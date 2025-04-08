package org.modelix.model

import kotlinx.datetime.Instant
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectHash
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.lazy.VersionBuilder
import kotlin.jvm.JvmInline

interface IVersion {
    fun getContentHash(): String

    @Deprecated("Use getModelTree()")
    fun getTree(): ITree

    fun getTrees(): Map<TreeType, ITree>

    fun getModelTree(): IGenericModelTree<INodeReference>

    fun getAttributes(): Map<String, String>

    fun asObject(): Object<*>

    fun getObjectHash(): ObjectHash = asObject().getHash()

    fun getParentVersions(): List<IVersion>

    fun getTimestamp(): Instant?

    companion object {
        fun builder(): VersionBuilder = VersionBuilder()
    }
}

@JvmInline
value class TreeType(val name: String) {
    init {
        for (c in name) {
            if (!('a' <= c && c <= 'z' || 'A' <= c && c <= 'Z' || '0' <= c && c <= '9')) {
                throw IllegalArgumentException("Name contains illegal characters: $name")
            }
        }
    }

    override fun toString(): String = name.ifEmpty { "<main>" }

    companion object {
        val MAIN = TreeType("")
        val META = TreeType("meta")
    }
}

/**
 * Sorts commits as described at https://git-scm.com/docs/http-protocol#_the_negotiation_algorithm
 *
 *     Start a queue, c_pending, ordered by commit time (popping newest first).
 *     Add all client refs.
 *     When a commit is popped from the queue its parents SHOULD be automatically inserted back.
 *     Commits MUST only enter the queue once.
 *
 * It's basically the order that you will see in a git-log output.
 */
fun Iterable<IVersion>.historyAsSequence(knownVersions: Iterable<ObjectHash>, sortByTime: Boolean = true): Sequence<IVersion> = sequence {
    val queue: MutableList<IVersion> = ArrayList()
    val wasInQueue = HashSet<ObjectHash>()
    wasInQueue.addAll(knownVersions)

    fun addToQueue(v: IVersion) {
        if (wasInQueue.contains(v.getObjectHash())) return
        wasInQueue.add(v.getObjectHash())
        queue.add(v)
        if (sortByTime) {
            queue.sortByDescending { it.getTimestamp() }
        }
    }

    this@historyAsSequence.forEach { addToQueue(it) }

    while (queue.isNotEmpty()) {
        val removed = queue.removeFirst()
        removed.getParentVersions().forEach { addToQueue(it) }
        yield(removed)
    }
}

fun IVersion.historyAsSequence(knownVersions: Iterable<ObjectHash> = emptyList(), sortByTime: Boolean = true): Sequence<IVersion> {
    return listOf(this).historyAsSequence(knownVersions, sortByTime)
}

fun IVersion.historyAsSequence(knownVersion: ObjectHash, sortByTime: Boolean = true): Sequence<IVersion> {
    return historyAsSequence(listOf(knownVersion), sortByTime)
}

/**
 * Returns all versions that are reachable from [this], but not from [knownVersions]
 */
fun IVersion.historyDiff(knownVersions: List<IVersion>): List<IVersion> {
    val commonVersions = HashSet<ObjectHash>()

    /**
     * We first assume that the [commonVersions] set is complete, meaning it will cut off the [historyAsSequence]
     * traversal so that it doesn't return any versions that are reachable from [knownVersions].
     * We traverse the history of [knownVersions] at the same time until we find prove that the initial assumption is
     * wrong. Then we update [commonVersions] and restart the process with the same assumption.
     */

    mainLoop@while (true) {
        val unknownHistoryItr = this.historyAsSequence(commonVersions, sortByTime = false).iterator()
        val knownHistoryItr = knownVersions.historyAsSequence(commonVersions, sortByTime = false).iterator()

        var unknownHistory = LinkedHashMap<ObjectHash, IVersion>()
        val knownHistory = HashSet<ObjectHash>()

        while (unknownHistoryItr.hasNext() || knownHistoryItr.hasNext()) {
            val unknownVersion = unknownHistoryItr.nextOrNull()?.also { unknownHistory.put(it.getObjectHash(), it) }?.getObjectHash()
            val knownVersion = knownHistoryItr.nextOrNull()?.getObjectHash()?.also { knownHistory.add(it) }

            if (unknownVersion != null && knownHistory.contains(unknownVersion)) {
                commonVersions.add(unknownVersion)
                continue@mainLoop
            }

            if (knownVersion != null && unknownHistory.contains(knownVersion)) {
                commonVersions.add(knownVersion)
                continue@mainLoop
            }
        }

        // assumption was correct
        return unknownHistory.values.toList()
    }
}

private fun <T> Iterator<T>.nextOrNull() = if (hasNext()) next() else null

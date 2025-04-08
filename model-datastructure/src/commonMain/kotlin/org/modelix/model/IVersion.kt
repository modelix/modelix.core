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
fun IVersion.historyAsSequence(): Sequence<IVersion> = sequence {
    val queue: MutableList<IVersion> = ArrayList()
    val visited = HashSet<ObjectHash>()

    fun addToQueue(v: IVersion) {
        if (visited.contains(v.getObjectHash())) return
        visited.add(v.getObjectHash())
        queue.add(v)
        queue.sortByDescending { it.getTimestamp() }
    }

    addToQueue(this@historyAsSequence)

    while (queue.isNotEmpty()) {
        val removed = queue.removeFirst()
        removed.getParentVersions().forEach { addToQueue(it) }
        yield(removed)
    }
}

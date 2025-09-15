package org.modelix.datastructures.model

import kotlinx.datetime.Instant
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.asObject
import org.modelix.datastructures.objects.getHashString
import org.modelix.datastructures.objects.requestBoth
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.kotlin.utils.urlDecode
import org.modelix.kotlin.utils.urlEncode
import org.modelix.model.lazy.CLVersion
import org.modelix.model.persistent.CPVersion
import org.modelix.model.persistent.Separators
import org.modelix.streams.IStream
import org.modelix.streams.flatten
import org.modelix.streams.plus
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration

sealed class HistoryIndexNode : IObjectData {
    abstract val firstVersion: ObjectReference<CPVersion>
    abstract val lastVersion: ObjectReference<CPVersion>
    abstract val authors: Set<String>
    abstract val size: Long
    abstract val height: Long
    abstract val minTime: Instant
    abstract val maxTime: Instant
    abstract val timeRange: ClosedRange<Instant>

    abstract fun getAllVersions(): IStream.Many<ObjectReference<CPVersion>>
    abstract fun getAllVersionsReversed(): IStream.Many<ObjectReference<CPVersion>>
    abstract fun getRange(indexRange: LongRange): IStream.Many<HistoryIndexNode>
    fun getRange(indexRange: IntRange) = getRange(indexRange.first.toLong()..indexRange.last.toLong())
    abstract fun merge(self: Object<HistoryIndexNode>, otherObj: Object<HistoryIndexNode>): Object<HistoryIndexNode>

    /**
     * Each returned node spans at most the duration specified in [interval].
     * For the same interval multiple nodes may be returned.
     * Latest entry is returned first.
     */
    abstract fun splitAtInterval(interval: Duration): IStream.Many<HistoryIndexNode>

    fun concat(
        self: Object<HistoryIndexNode>,
        otherObj: Object<HistoryIndexNode>,
    ): Object<HistoryIndexNode> {
        require(self.data === this)
        val other = otherObj.data
        require(maxTime < other.minTime)
        require(abs(height - other.height) <= 2)
        return HistoryIndexRangeNode(
            firstVersion = firstVersion,
            lastVersion = other.lastVersion,
            authors = authors + other.authors,
            size = size + other.size,
            height = max(height, other.height) + 1,
            minTime = minTime,
            maxTime = other.maxTime,
            child1 = self.ref,
            child2 = otherObj.ref,
        ).let {
            @OptIn(DelicateModelixApi::class)
            it.asObject(self.graph)
        }
    }

    override fun getDeserializer(): IObjectDeserializer<*> {
        return HistoryIndexNode
    }

    companion object : IObjectDeserializer<HistoryIndexNode> {
        override fun deserialize(
            serialized: String,
            referenceFactory: IObjectReferenceFactory,
        ): HistoryIndexNode {
            val parts = serialized.split(Separators.LEVEL1)
            return when (parts[0]) {
                "R" -> {
                    val versionRefs = parts[1].split(Separators.LEVEL2)
                        .map { referenceFactory.fromHashString(it, CPVersion) }
                    val times = parts[5].split(Separators.LEVEL2).map { Instant.fromEpochSeconds(it.toLong()) }
                    return HistoryIndexRangeNode(
                        firstVersion = versionRefs[0],
                        lastVersion = versionRefs.getOrElse(1) { versionRefs[0] },
                        authors = parts[2].split(Separators.LEVEL2).mapNotNull { it.urlDecode() }.toSet(),
                        size = parts[3].toLong(),
                        height = parts[4].toLong(),
                        minTime = times[0],
                        maxTime = times.getOrElse(1) { times[0] },
                        child1 = referenceFactory.fromHashString(parts[6], HistoryIndexNode),
                        child2 = referenceFactory.fromHashString(parts[7], HistoryIndexNode),
                    )
                }
                "L" -> {
                    HistoryIndexLeafNode(
                        versions = parts[1].split(Separators.LEVEL2)
                            .map { referenceFactory.fromHashString(it, CPVersion) },
                        authors = parts[2].split(Separators.LEVEL2).mapNotNull { it.urlDecode() }.toSet(),
                        time = Instant.fromEpochSeconds(parts[3].toLong()),
                    )
                }
                else -> error("Unknown type: " + parts[0])
            }
        }

        fun of(version: Object<CPVersion>): HistoryIndexNode {
            val time = CLVersion(version).getTimestamp() ?: Instant.Companion.fromEpochMilliseconds(0L)
            return HistoryIndexLeafNode(
                versions = listOf(version.ref),
                authors = setOfNotNull(version.data.author),
                time = time,
            )
        }

        fun of(version1: Object<CPVersion>, version2: Object<CPVersion>): HistoryIndexNode {
            return of(version1).asObject(version1.graph).merge(of(version2).asObject(version2.graph)).data
        }
    }
}

fun Object<HistoryIndexNode>.merge(otherObj: Object<HistoryIndexNode>): Object<HistoryIndexNode> = data.merge(this, otherObj)
fun Object<HistoryIndexNode>.concatUnbalanced(otherObj: Object<HistoryIndexNode>): Object<HistoryIndexNode> = data.concat(this, otherObj)
val Object<HistoryIndexLeafNode>.time get() = data.time

data class HistoryIndexLeafNode(
    val versions: List<ObjectReference<CPVersion>>,
    override val authors: Set<String>,
    val time: Instant,
) : HistoryIndexNode() {
    override val size: Long get() = versions.size.toLong()
    override val height: Long get() = 1
    override val minTime: Instant get() = time
    override val maxTime: Instant get() = time
    override val timeRange: ClosedRange<Instant> get() = minTime..maxTime
    override val firstVersion: ObjectReference<CPVersion> get() = versions.first()
    override val lastVersion: ObjectReference<CPVersion> get() = versions.last()

    override fun getAllVersions(): IStream.Many<ObjectReference<CPVersion>> {
        return IStream.many(versions)
    }

    override fun getAllVersionsReversed(): IStream.Many<ObjectReference<CPVersion>> {
        return IStream.many(versions.asReversed())
    }

    override fun getRange(indexRange: LongRange): IStream.Many<HistoryIndexNode> {
        if (indexRange.first == 0L && indexRange.size() == versions.size.toLong()) {
            return IStream.of(this)
        } else {
            return IStream.many(
                versions.asReversed()
                    .drop(indexRange.first.toInt())
                    .take(indexRange.size().toInt()),
            )
                .flatMapOrdered { it.resolve() }
                .map {
                    HistoryIndexLeafNode(
                        versions = listOf(it.ref),
                        authors = setOfNotNull(it.data.author),
                        time = time,
                    )
                }
        }
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return versions
    }

    override fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
    ): IStream.Many<Object<*>> {
        TODO("Not yet implemented")
    }

    override fun serialize(): String {
        return "L" +
            Separators.LEVEL1 + versions.joinToString(Separators.LEVEL2) { it.getHashString() } +
            Separators.LEVEL1 + authors.joinToString(Separators.LEVEL2) { it.urlEncode() } +
            Separators.LEVEL1 + time.epochSeconds.toString()
    }

    override fun merge(
        self: Object<HistoryIndexNode>,
        otherObj: Object<HistoryIndexNode>,
    ): Object<HistoryIndexNode> {
        val other = otherObj.data
        return when (other) {
            is HistoryIndexLeafNode -> {
                when {
                    other.time < time -> otherObj.concatBalanced(self)
                    other.time > time -> self.concatBalanced(otherObj)
                    else -> HistoryIndexLeafNode(
                        versions = (versions.associateBy { it.getHash() } + other.versions.associateBy { it.getHash() }).values.toList(),
                        authors = authors + other.authors,
                        time = time,
                    ).asObject(self.graph)
                }
            }
            is HistoryIndexRangeNode -> {
                otherObj.merge(self)
            }
        }
    }

    override fun splitAtInterval(interval: Duration): IStream.Many<HistoryIndexNode> {
        TODO("Not yet implemented")
    }
}

/**
 * The subranges can overlap.
 */
data class HistoryIndexRangeNode(
    /**
     * if subrange1 is set, then it's the same as subrange1.firstVersion
     */
    override val firstVersion: ObjectReference<CPVersion>,
    /**
     * if subrange2 is set, then it's the same as subrange2.lastVersion
     */
    override val lastVersion: ObjectReference<CPVersion>,

    /**
     * All authors in this subtree.
     */
    override val authors: Set<String>,

    /**
     * Number of versions in this subtree.
     */
    override val size: Long,

    override val height: Long,

    override val minTime: Instant,

    override val maxTime: Instant,

    val child1: ObjectReference<HistoryIndexNode>,
    val child2: ObjectReference<HistoryIndexNode>,
) : HistoryIndexNode() {

    init {
        require(firstVersion.getHash() != lastVersion.getHash())
        require(minTime < maxTime)
        require(height > 1L)
        require(size > 1L)
        require(child1.getHash() != child2.getHash())
    }

    private val graph: IObjectGraph get() = firstVersion.graph
    override val timeRange: ClosedRange<Instant> get() = minTime..maxTime

    override fun getDeserializer(): IObjectDeserializer<*> {
        return HistoryIndexNode
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return listOf(child1, child2)
    }

    override fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
    ): IStream.Many<Object<*>> {
        TODO("Not yet implemented")
    }

    override fun merge(self: Object<HistoryIndexNode>, otherObj: Object<HistoryIndexNode>): Object<HistoryIndexNode> {
        val self = self as Object<HistoryIndexRangeNode>
        val other = otherObj.data
        val resolvedChild1 = child1.resolveNow()
        val resolvedChild2 = child2.resolveNow()
        when (other) {
            is HistoryIndexLeafNode -> {
                val range1 = resolvedChild1.data.timeRange
                val range2 = resolvedChild2.data.timeRange
                return when {
                    other.time < range1.start -> otherObj.concatBalanced(resolvedChild1).concatBalanced(resolvedChild2)
                    other.time <= range1.endInclusive -> resolvedChild1.merge(otherObj).concatBalanced(resolvedChild2)
                    other.time < range2.start -> if (resolvedChild1.size <= resolvedChild2.size) {
                        resolvedChild1.concatBalanced(otherObj).concatBalanced(resolvedChild2)
                    } else {
                        resolvedChild1.concatBalanced(otherObj.concatBalanced(resolvedChild2))
                    }
                    other.time <= range2.endInclusive -> resolvedChild1.concatBalanced(resolvedChild2.merge(otherObj))
                    else -> resolvedChild1.concatBalanced(resolvedChild2.concatBalanced(otherObj))
                }
            }
            is HistoryIndexRangeNode -> {
                val range1 = resolvedChild1.data.timeRange
                val range2 = resolvedChild2.data.timeRange
                val intersects1 = other.timeRange.intersects(range1)
                val intersects2 = other.timeRange.intersects(range2)
                return when {
                    intersects1 && intersects2 -> {
                        resolvedChild1.merge(otherObj).merge(resolvedChild2)
                    }
                    intersects1 -> resolvedChild1.merge(otherObj).concatBalanced(resolvedChild2)
                    intersects2 -> resolvedChild1.concatBalanced(resolvedChild2.merge(otherObj))
                    other.maxTime < range1.start -> {
                        if (other.size < resolvedChild2.size) {
                            otherObj.concatBalanced(resolvedChild1).concatBalanced(resolvedChild2)
                        } else {
                            otherObj.concatBalanced(self)
                        }
                    }
                    other.maxTime < range2.start -> {
                        if (resolvedChild2.size < resolvedChild1.size) {
                            resolvedChild1.concatBalanced(otherObj.concatBalanced(resolvedChild2))
                        } else {
                            resolvedChild1.concatBalanced(otherObj).concatBalanced(resolvedChild2)
                        }
                    }
                    else -> {
                        if (other.size < resolvedChild1.size) {
                            resolvedChild1.concatBalanced(resolvedChild2.concatBalanced(otherObj))
                        } else {
                            self.concatBalanced(otherObj)
                        }
                    }
                }
            }
        }
    }

    override fun splitAtInterval(interval: Duration): IStream.Many<HistoryIndexNode> {
        TODO("Not yet implemented")
    }

    /**
     * Oldest version first
     */
    override fun getAllVersions(): IStream.Many<ObjectReference<CPVersion>> {
        return IStream.of(child1, child2)
            .flatMapOrdered { it.resolve() }
            .flatMapOrdered { it.data.getAllVersions() }
    }

    /**
     * Latest version first
     */
    override fun getAllVersionsReversed(): IStream.Many<ObjectReference<CPVersion>> {
        return IStream.of(child2, child1)
            .flatMapOrdered { it.resolve() }
            .flatMapOrdered { it.data.getAllVersionsReversed() }
    }

    /**
     * Latest element has index 0
     */
    override fun getRange(indexRange: LongRange): IStream.Many<HistoryIndexNode> {
        if (indexRange.isEmpty()) return IStream.empty()
        if (!indexRange.intersects(0L until size)) return IStream.empty()
        if (indexRange.contains(0L) && indexRange.contains(size - 1L)) return IStream.of(this)
        return child1.requestBoth(child2) { resolvedChild1, resolvedChild2 ->
            val validRange2 = 0L.rangeOfSize(resolvedChild2.size)
            val validRange1 = (validRange2.last + 1L).rangeOfSize(resolvedChild1.size)
            val range2 = indexRange.intersect(validRange2)
            val range1 = indexRange.intersect(validRange1).shift(-resolvedChild2.size)
            resolvedChild2.data.getRange(range2) + resolvedChild1.data.getRange(range1)
        }.flatten()
    }

    override fun serialize(): String {
        return "R" +
            Separators.LEVEL1 + firstVersion.getHashString() + Separators.LEVEL2 + lastVersion.getHashString() +
            Separators.LEVEL1 + authors.joinToString(Separators.LEVEL2) { it.urlEncode() } +
            Separators.LEVEL1 + size +
            Separators.LEVEL1 + height +
            Separators.LEVEL1 + minTime.epochSeconds.toString() + Separators.LEVEL2 + maxTime.epochSeconds +
            Separators.LEVEL1 + child1.getHashString() +
            Separators.LEVEL1 + child2.getHashString()
    }
}

private fun LongRange.shift(amount: Long) = first.plus(amount)..last.plus(amount)
private fun <T : Comparable<T>> ClosedRange<T>.intersects(other: ClosedRange<T>): Boolean {
    return this.contains(other.start) || this.contains(other.endInclusive) ||
        other.contains(this.start) || other.contains(this.endInclusive)
}

val Object<HistoryIndexNode>.size get() = data.size
val Object<HistoryIndexNode>.height get() = data.height
fun LongRange.size() = (last - first + 1).coerceAtLeast(0)
fun Long.rangeOfSize(size: Long) = this until (this + size)
fun LongRange.intersect(other: LongRange): LongRange {
    return if (this.first > other.first) other.intersect(this) else other.first..min(this.last, other.last)
}

fun Object<HistoryIndexNode>.rebalance(otherObj: Object<HistoryIndexNode>): Pair<Object<HistoryIndexNode>, Object<HistoryIndexNode>> {
    if (otherObj.height > height + 1) {
        val split1 = (otherObj.data as HistoryIndexRangeNode).child1.resolveNow()
        val split2 = (otherObj.data as HistoryIndexRangeNode).child2.resolveNow()
        val rebalanced = this.rebalance(split1)
        if (rebalanced.first.height <= split2.height) {
            return rebalanced.first.concatUnbalanced(rebalanced.second) to split2
        } else {
            return rebalanced.first to rebalanced.second.concatUnbalanced(split2)
        }
    } else if (height > otherObj.height + 1) {
        val split1 = (this.data as HistoryIndexRangeNode).child1.resolveNow()
        val split2 = (this.data as HistoryIndexRangeNode).child2.resolveNow()
        val rebalanced = split2.rebalance(otherObj)
        if (rebalanced.second.height > split1.height) {
            return split1.concatUnbalanced(rebalanced.first) to rebalanced.second
        } else {
            return split1 to rebalanced.first.concatUnbalanced(rebalanced.second)
        }
    } else {
        return this to otherObj
    }
}

fun Object<HistoryIndexNode>.concatBalanced(otherObj: Object<HistoryIndexNode>): Object<HistoryIndexNode> {
    val rebalanced = this.rebalance(otherObj)
    return rebalanced.first.concatUnbalanced(rebalanced.second)
}

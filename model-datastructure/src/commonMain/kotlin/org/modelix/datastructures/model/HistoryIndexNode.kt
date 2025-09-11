package org.modelix.datastructures.model

import kotlinx.datetime.Instant
import org.modelix.datastructures.model.HistoryIndexNode.Companion.merge
import org.modelix.datastructures.model.HistoryIndexNode.Companion.of
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.asObject
import org.modelix.datastructures.objects.getHashString
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.kotlin.utils.urlDecode
import org.modelix.kotlin.utils.urlEncode
import org.modelix.model.lazy.CLVersion
import org.modelix.model.persistent.CPVersion
import org.modelix.model.persistent.Separators
import org.modelix.streams.IStream
import org.modelix.streams.plus
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration

/**
 * The subranges can overlap.
 */
data class HistoryIndexNode(
    /**
     * if subrange1 is set, then it's the same as subrange1.firstVersion
     */
    val firstVersion: ObjectReference<CPVersion>,
    /**
     * if subrange2 is set, then it's the same as subrange2.lastVersion
     */
    val lastVersion: ObjectReference<CPVersion>,

    /**
     * All authors in this subtree.
     */
    val authors: Set<String>,

    /**
     * Number if versions in this subtree.
     */
    val size: Long,

    val height: Long,

    val minTime: Instant,

    val maxTime: Instant,

    val subranges: Pair<ObjectReference<HistoryIndexNode>, ObjectReference<HistoryIndexNode>>?,
) : IObjectData {

    init {
        when (size) {
            0L -> error("empty node not expected")
            1L -> {
                require(subranges == null)
                require(firstVersion.getHash() == lastVersion.getHash())
                require(minTime == maxTime)
                require(height == 1L)
            }
            2L -> {
                require(subranges == null)
                require(firstVersion.getHash() != lastVersion.getHash())
                require(minTime <= maxTime)
                require(height == 1L)
            }
            else -> {
                require(firstVersion.getHash() != lastVersion.getHash())
                require(subranges != null)
                require(subranges.first.getHash() != subranges.second.getHash())
                require(minTime <= maxTime)
                require(height > 1L)
            }
        }
    }

    private val graph: IObjectGraph get() = firstVersion.graph
    val time: ClosedRange<Instant> get() = minTime..maxTime

    override fun getDeserializer(): IObjectDeserializer<*> {
        return HistoryIndexNode
    }

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return subranges?.toList() ?: emptyList()
    }

    override fun objectDiff(
        self: Object<*>,
        oldObject: Object<*>?,
    ): IStream.Many<Object<*>> {
        TODO("Not yet implemented")
    }

    /**
     * Oldest version first
     */
    fun getAllVersions(): IStream.Many<ObjectReference<CPVersion>> {
        return when (size) {
            1L -> IStream.of(firstVersion)
            2L -> IStream.many(arrayOf(firstVersion, lastVersion))
            else -> IStream.many(subranges!!.toList())
                .flatMapOrdered { it.resolve() }
                .flatMapOrdered { it.data.getAllVersions() }
        }
    }

    /**
     * Latest version first
     */
    fun getAllVersionsReversed(): IStream.Many<ObjectReference<CPVersion>> {
        return when (size) {
            1L -> IStream.of(firstVersion)
            2L -> IStream.many(arrayOf(lastVersion, firstVersion))
            else -> IStream.many(subranges!!.toList().asReversed())
                .flatMapOrdered { it.resolve() }
                .flatMapOrdered { it.data.getAllVersionsReversed() }
        }
    }

    /**
     * Each returned node spans at most the duration specified in [interval].
     * For the same interval multiple nodes may be returned.
     * Latest entry is returned first.
     */
    fun splitAtInterval(interval: Duration): IStream.Many<HistoryIndexNode> {
        if (size == 1L) return IStream.of(this)
        val intervalId1 = minTime.toEpochMilliseconds() / interval.inWholeMilliseconds
        val intervalId2 = maxTime.toEpochMilliseconds() / interval.inWholeMilliseconds
        if (intervalId1 == intervalId2) return IStream.of(this)
        return splitReversed().flatMapOrdered { it.splitAtInterval(interval) }
    }

    fun split(): IStream.Many<HistoryIndexNode> {
        return when (size) {
            1L -> IStream.of(this)
            2L -> IStream.many(listOf(firstVersion, lastVersion))
                .flatMapOrdered { it.resolve() }
                .map { of(it) }
            else -> IStream.many(subranges!!.toList()).flatMapOrdered { it.resolve() }.map { it.data }
        }
    }

    fun splitReversed(): IStream.Many<HistoryIndexNode> {
        return when (size) {
            1L -> IStream.of(this)
            2L -> IStream.many(listOf(lastVersion, firstVersion))
                .flatMapOrdered { it.resolve() }
                .map { of(it) }
            else -> IStream.many(subranges!!.let { listOf(it.second, it.first) })
                .flatMapOrdered { it.resolve() }
                .map { it.data }
        }
    }

    /**
     * Latest element has index 0
     */
    fun getRange(indexRange: LongRange): IStream.Many<HistoryIndexNode> {
        if (indexRange.isEmpty()) return IStream.empty()
        if (!indexRange.intersects(0L until size)) return IStream.empty()
        if (indexRange.contains(0L) && indexRange.contains(size - 1L)) return IStream.of(this)
        return splitReversed().toList().flatMapOrdered { list ->
            when (list.size) {
                1 -> getRange(indexRange)
                2 -> {
                    val validRange1 = 0L.rangeOfSize(list[0].size)
                    val validRange2 = (validRange1.last + 1L).rangeOfSize(list[1].size)
                    val range1 = indexRange.intersect(validRange1)
                    val range2 = indexRange.intersect(validRange2).shift(-list[0].size)
                    list[0].getRange(range1) + list[1].getRange(range2)
                }
                else -> error("impossible")
            }
        }
    }
    fun getRange(indexRange: IntRange) = getRange(indexRange.first.toLong()..indexRange.last.toLong())

    override fun serialize(): String {
        val firstAndLast = if (firstVersion.getHash() == lastVersion.getHash()) {
            firstVersion.getHashString()
        } else {
            firstVersion.getHashString() + Separators.LEVEL2 + lastVersion.getHashString()
        }
        val times = if (minTime == maxTime) {
            minTime.epochSeconds.toString()
        } else {
            "${minTime.epochSeconds}${Separators.LEVEL2}${maxTime.epochSeconds}"
        }
        return firstAndLast +
            Separators.LEVEL1 + authors.joinToString(Separators.LEVEL2) { it.urlEncode() } +
            Separators.LEVEL1 + size +
            Separators.LEVEL1 + height +
            Separators.LEVEL1 + times +
            (subranges?.let { Separators.LEVEL1 + it.first.getHashString() + Separators.LEVEL2 + it.second.getHashString() } ?: "")
    }

    companion object : IObjectDeserializer<HistoryIndexNode> {

        override fun deserialize(
            serialized: String,
            referenceFactory: IObjectReferenceFactory,
        ): HistoryIndexNode {
            val parts = serialized.split(Separators.LEVEL1)
            val versionRefs = parts[0].split(Separators.LEVEL2)
                .map { referenceFactory.fromHashString(it, CPVersion) }
            val times = parts[4].split(Separators.LEVEL2).map { Instant.fromEpochSeconds(it.toLong()) }
            return HistoryIndexNode(
                firstVersion = versionRefs[0],
                lastVersion = versionRefs.getOrElse(1) { versionRefs[0] },
                authors = parts[1].split(Separators.LEVEL2).mapNotNull { it.urlDecode() }.toSet(),
                size = parts[2].toLong(),
                height = parts[3].toLong(),
                minTime = times[0],
                maxTime = times.getOrElse(1) { times[0] },
                subranges = parts.getOrNull(5)?.split(Separators.LEVEL2)
                    ?.map { referenceFactory.fromHashString(it, HistoryIndexNode) }
                    ?.let { it[0] to it[1] },
            )
        }

        fun of(version: Object<CPVersion>): HistoryIndexNode {
            val time = CLVersion(version).getTimestamp() ?: Instant.Companion.fromEpochMilliseconds(0L)
            return HistoryIndexNode(
                firstVersion = version.ref,
                lastVersion = version.ref,
                authors = setOfNotNull(version.data.author),
                size = 1,
                height = 1L,
                minTime = time,
                maxTime = time,
                subranges = null,
            )
        }

        fun of(version1: Object<CPVersion>, version2: Object<CPVersion>): HistoryIndexNode {
            val time1 = CLVersion(version1).getTimestamp() ?: Instant.Companion.fromEpochMilliseconds(0L)
            val time2 = CLVersion(version2).getTimestamp() ?: Instant.Companion.fromEpochMilliseconds(0L)
            return if (time1 <= time2) {
                HistoryIndexNode(
                    firstVersion = version1.ref,
                    lastVersion = version2.ref,
                    authors = setOfNotNull(version1.data.author, version2.data.author),
                    size = 2,
                    height = 1L,
                    minTime = time1,
                    maxTime = time2,
                    subranges = null,
                )
            } else {
                of(version2, version1)
            }
        }

        fun merge(range1: Object<HistoryIndexNode>?, range2: Object<HistoryIndexNode>?): Object<HistoryIndexNode> {
            if (range1 == null) return requireNotNull(range2)
            if (range2 == null) return range1
            if (range1.getHash() == range2.getHash()) return range1
            if (range2.data.maxTime < range1.data.minTime) return merge(range2, range1)

            val totalSize = range1.data.size + range2.data.size

            if (totalSize <= 2) return concat(range1, range2)

            if (range1.data.time.intersects(range2.data.time)) {
                val split1 = range1.splitNow()
                val split2 = range2.splitNow()

                if (split1.size == 1) {
                    if (split2.size == 1) {
                        TODO()
                    } else {
                        return merge(merge(split1[0], split2[0]), split2[1])
                    }
                } else {
                    if (split2.size == 1) {
                        return merge(split1[0], merge(split1[1], split2[0]))
                    } else {
                        return merge(split1[0], merge(merge(split1[1], split2[0]), split2[1]))
                    }
                }
            }

            // In a balanced tree, one subtree shouldn't be more than twice as big as the other.
            val minSubtreeSize = totalSize / 3L
            val maxSubtreeSize = totalSize - minSubtreeSize
            val allowedSubtreeSizeRange = minSubtreeSize..maxSubtreeSize

            if (range1.data.size > maxSubtreeSize) {
                val (range1A, range1B) = range1.splitLeft(allowedSubtreeSizeRange)
                return merge(range1A, merge(range1B, range2))
            }

            if (range2.data.size > maxSubtreeSize) {
                val (range2A, range2B) = range2.splitRight(allowedSubtreeSizeRange)
                return merge(merge(range1, range2A), range2B)
            }

            if (range1.data.size < minSubtreeSize) {
                val (range2A, range2B) = range2.splitLeft(allowedSubtreeSizeRange.shift(-range1.data.size))
                return merge(merge(range1, range2A), range2B)
            }

            if (range2.data.size < minSubtreeSize) {
                val (range1A, range1B) = range1.splitRight(allowedSubtreeSizeRange.shift(-range2.data.size))
                return merge(range1A, merge(range1B, range2))
            }

            return concat(range1, range2)
        }

        /**
         * Just merges the two subtrees without any guarantees about the shape of the resulting tree.
         */
        private fun concat(range1: Object<HistoryIndexNode>, range2: Object<HistoryIndexNode>): Object<HistoryIndexNode> {
            val subranges = if (range1.data.size == 1L && range2.data.size == 1L) {
                null
            } else {
                range1.ref to range2.ref
            }
            require(range1.data.maxTime <= range2.data.minTime) {
                "${range1.data.time} overlaps with ${range2.data.time}"
            }
            return HistoryIndexNode(
                firstVersion = range1.data.firstVersion,
                lastVersion = range2.data.lastVersion,
                authors = range1.data.authors + range2.data.authors,
                size = range1.data.size + range2.data.size,
                height = if (subranges == null) 1L else max(range1.data.height, range2.data.height) + 1,
                minTime = range1.data.minTime,
                maxTime = range2.data.maxTime,
                subranges = subranges,
            ).let {
                @OptIn(DelicateModelixApi::class)
                it.asObject(range1.graph)
            }
        }
    }
}

private fun Object<HistoryIndexNode>.splitNow(): List<Object<HistoryIndexNode>> {
    return when (data.size) {
        1L -> listOf(this)
        2L -> listOf(of(this.firstVersion.resolveNow()).asObject(graph), of(this.lastVersion.resolveNow()).asObject(graph))
        else -> data.subranges!!.toList().map { it.resolveNow() }
    }
}

private fun LongRange.coerceAtLeast(limit: Long) = first.coerceAtLeast(limit)..last.coerceAtLeast(limit)
private fun LongRange.coerceAtMost(limit: Long) = first.coerceAtMost(limit)..last.coerceAtMost(limit)
private fun LongRange.shift(amount: Long) = first.plus(amount)..last.plus(amount)
private fun <T : Comparable<T>> ClosedRange<T>.intersects(other: ClosedRange<T>): Boolean {
    return this.contains(other.start) || this.contains(other.endInclusive) ||
        other.contains(this.start) || other.contains(this.endInclusive)
}

val Object<HistoryIndexNode>.size get() = data.size
val Object<HistoryIndexNode>.height get() = data.height
val Object<HistoryIndexNode>.firstVersion get() = data.firstVersion
val Object<HistoryIndexNode>.lastVersion get() = data.lastVersion
val Object<HistoryIndexNode>.subranges get() = data.subranges
private fun Object<HistoryIndexNode>.split(leftSize: LongRange, rightSize: LongRange): Pair<Object<HistoryIndexNode>?, Object<HistoryIndexNode>?> {
    require(leftSize.first + rightSize.last == size)
    require(leftSize.last + rightSize.first == size)
    if (size == 1L) {
        if (leftSize.contains(1L)) return this to null
        if (rightSize.contains(1L)) return null to this
        error("Invalid constraints for size 1: $leftSize and $rightSize")
    }
    if (size == 2L) {
        if (leftSize.contains(2L) && rightSize.contains(0L)) return this to null
        if (leftSize.contains(0L) && rightSize.contains(2L)) return null to this
        if (leftSize.contains(1L) && rightSize.contains(1L)) {
            return of(firstVersion.resolveNow()).asObject(graph) to of(lastVersion.resolveNow()).asObject(graph)
        }
        error("Invalid constraints for size 2: $leftSize and $rightSize")
    }
    val subranges = this.subranges
    requireNotNull(subranges)
    val range1 = subranges.first.resolveNow()
    val range2 = subranges.second.resolveNow()
    if (leftSize.contains(range1.data.size) && rightSize.contains(range2.data.size)) {
        return range1 to range2
    }
    if (!leftSize.contains(range1.size)) {
        val (range1A, range1B) = range1.splitRight(rightSize.shift(-range2.size).coerceAtLeast(0))
        return range1A to merge(range1B, range2)
    } else {
        val (range2A, range2B) = range2.splitLeft(leftSize.shift(-range1.size).coerceAtLeast(0))
        return merge(range1, range2A) to range2B
    }
}
fun Object<HistoryIndexNode>.splitRight(rightSize: LongRange) = split((size - rightSize.last)..(size - rightSize.first), rightSize)
fun Object<HistoryIndexNode>.splitLeft(leftSize: LongRange) = split(leftSize, (size - leftSize.last)..(size - leftSize.first))
fun Object<HistoryIndexNode>?.merge(other: Object<HistoryIndexNode>?) = merge(this, other)
fun LongRange.size() = (last - first + 1).coerceAtLeast(0)
fun LongRange.withSize(newSize: Long) = first..(last.coerceAtMost(newSize - first - 1))
fun Long.rangeOfSize(size: Long) = this until (this + size)
fun LongRange.intersect(other: LongRange): LongRange {
    return if (this.first > other.first) other.intersect(this) else other.first..min(this.last, other.last)
}
fun LongRange.shiftFirstTo(newFirst: Long) = newFirst..(last + newFirst - first)

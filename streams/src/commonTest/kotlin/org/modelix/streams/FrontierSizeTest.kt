package org.modelix.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A synthetic content-addressed tree exposed as an [IBulkExecutor]. The node id is the key; the value is the
 * comma-separated list of child ids. Nodes are laid out as a complete [branching]-ary tree in BFS order
 * (root = 0, children of i are i*branching+1 .. i*branching+branching), so children can be computed on the fly
 * without materializing the whole tree.
 *
 * Crucially it records, per bulk round, how many keys were requested together. With a [batchSize] larger than any
 * level, each [execute] call corresponds to exactly one engine round, so [maxRoundSize] is the peak fetch frontier —
 * the quantity the old `BulkRequestStreamExecutor.sendNextBatch` depth-first traversal kept bounded.
 */
private class TreeSource(val branching: Int, val depth: Int) : IBulkExecutor<Int, String> {
    val nodeCount: Int = ((pow(branching, depth + 1) - 1) / (branching - 1))
    val widestLevel: Int = pow(branching, depth)

    var maxRoundSize: Int = 0
        private set
    var roundCount: Int = 0
        private set
    var totalFetched: Int = 0
        private set

    override fun execute(keys: List<Int>): Map<Int, String> {
        roundCount++
        totalFetched += keys.size
        if (keys.size > maxRoundSize) maxRoundSize = keys.size
        return keys.associateWith { id ->
            (1..branching).map { id * branching + it }.filter { it < nodeCount }.joinToString(",")
        }
    }

    override suspend fun executeSuspending(keys: List<Int>): Map<Int, String> = execute(keys)

    companion object {
        private fun pow(base: Int, exp: Int): Int {
            var r = 1
            repeat(exp) { r *= base }
            return r
        }
    }
}

class FrontierSizeTest {

    /** Builds `getDescendants(id)` the same shape the real tree uses: of(id) + children.flatMap { descendants }. */
    private fun descendants(executor: BulkRequestStreamExecutor<Int, String>, id: Int): IStream.Many<Int> {
        val childIds: IStream.Many<Int> = executor.enqueue(id).orNull().flatMapOrdered { csv ->
            if (csv.isNullOrEmpty()) IStream.many(emptyList()) else IStream.many(csv.split(",").map { it.toInt() })
        }
        return IStream.of(id).concat(childIds.flatMapOrdered { descendants(executor, it) })
    }

    @Test
    fun frontier_is_bounded_by_batch_size_on_wide_deep_tree() {
        val source = TreeSource(branching = 4, depth = 7) // 21845 nodes, widest level 16384
        val batchSize = 1000
        val executor = BulkRequestStreamExecutor(source, batchSize = batchSize)

        val count = executor.query { descendants(executor, 0).count() }

        // The traversal is correct and does no redundant work...
        assertEquals(source.nodeCount, count)
        assertEquals(source.nodeCount, source.totalFetched)
        // ...and the peak request frontier stays within the batch size, even though the widest level is far larger.
        // This is the depth-first bound the old sendNextBatch provided; the new round-based engine now preserves it.
        assertTrue(source.widestLevel > batchSize, "test should exercise a level wider than the cap")
        assertTrue(
            source.maxRoundSize <= batchSize,
            "peak frontier ${source.maxRoundSize} must stay within batchSize $batchSize (widest level ${source.widestLevel})",
        )
    }

    @Test
    fun unbounded_batch_size_batches_each_level_into_one_round() {
        // With a batch size above any level, no frontier is trimmed: each tree level is fetched in a single round, so
        // applicative batching is fully preserved when memory is not a constraint. (depth + 1 = 8 rounds.)
        val source = TreeSource(branching = 4, depth = 7)
        val executor = BulkRequestStreamExecutor(source, batchSize = Int.MAX_VALUE)

        val count = executor.query { descendants(executor, 0).count() }

        assertEquals(source.nodeCount, count)
        assertEquals(8, source.roundCount)
        assertEquals(source.widestLevel, source.maxRoundSize)
    }
}

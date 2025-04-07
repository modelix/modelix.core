package org.modelix.model

import org.modelix.model.lazy.CLVersion

class LinearHistory(private val baseVersionHash: String?) {
    /**
     * Children indexed by their parent versions.
     * A version is a parent of a child,
     * if the [CLVersion.baseVersion], the [CLVersion.getMergedVersion1] or [CLVersion.getMergedVersion2]
     */
    private val byVersionChildren = mutableMapOf<CLVersion, MutableSet<CLVersion>>()

    /**
     * Global roots are versions without parents.
     * It may be only the version denoted [baseVersionHash]
     * or many versions, if no base version was specified and versions without a common global root are ordered.
     */
    private val globalRoot = mutableSetOf<CLVersion>()

    /**
     * The distance of a version from its root.
     * Aka how many children a between the root and a version.
     */
    private val byVersionDistanceFromGlobalRoot = mutableMapOf<CLVersion, Int>()

    /**
     * Returns all versions between the [fromVersions] (inclusive) and a common version (exclusive).
     * The common version may be identified by [baseVersionHash].
     * If no [baseVersionHash] is given, the common version will be the first version
     * aka the version without a [CLVersion.baseVersion].
     *
     * The order also ensures three properties:
     * 1. The versions are ordered topologically starting with the versions without parents.
     * 2. The order is also "monotonic".
     *    This means adding a version to the set of all versions will never change
     *    the order of versions that were previously in the history.
     *    For example, given versions 1, 2 and 3:
     *      If 1 and 2 are ordered as (1, 2), ordering 1, 2 and x will never produce (2, x, 1).
     *      x can come anywhere (respecting the topological ordering), but 2 has to come after 1.
     * 3. "Close versions are kept together"
     *    Formally: A version that has only one child should always come before the child.
     *      Example: 1 <- 2 <- 3 and 1 <- x, then [1, 2, x, 3] is not allowed,
     *      because 3 is the only child of 2.
     *      Valid orders would be (1, 2, 3, x) and (1, x, 2, 3)
     *    This is relevant for UndoOp and RedoOp.
     *    See UndoTest.
     */
    fun load(vararg fromVersions: CLVersion): List<CLVersion> {
        // Traverse the versions once to index need data:
        // * Collect all relevant versions.
        // * Collect the distance to the base for each version.
        // * Collect the roots of relevant versions.
        // * Collect the children of each version.
        indexData(*fromVersions)

        // The following algorithm orders the version by
        // 1. Finding out the roots of so-called subtrees.
        //    A subtree is a tree of all versions that have the same version as root ancestor.
        //    A root ancestor of a version is the first ancestor in the chain of ancestors
        //    that is either a merge or a global root.
        //    Each version belongs to exactly one root ancestor, and it will be the same (especially future merges).
        // 2. Sort the roots of subtrees according to their distance (primary) and id (secondary)
        // 3. Order topologically inside each subtree.

        // Ordering the subtree root first, ensures the order is also "monotonic".
        // Then ordering the inside subtree ensures "close versions are kept together" without breaking "monotonicity".
        // Ordering inside a subtree ensures "monotonicity", because a subtree has no merges.
        // Only a subtrees root can be a merge.

        // Sorting the subtree roots by distance from base ensures topological order.
        val comparator = compareBy(byVersionDistanceFromGlobalRoot::getValue)
            // Sorting the subtree roots by distance from base and then by id ensures "monotonic" order.
            .thenBy(CLVersion::getObjectHash)
        val rootsOfSubtreesToVisit = globalRoot + byVersionDistanceFromGlobalRoot.keys.filter(CLVersion::isMerge)
        val orderedRootsOfSubtree = rootsOfSubtreesToVisit.distinct().sortedWith(comparator)

        val history = orderedRootsOfSubtree.flatMap { rootOfSubtree ->
            val historyOfSubtree = mutableListOf<CLVersion>()
            val stack = ArrayDeque<CLVersion>()
            stack.add(rootOfSubtree)
            while (stack.isNotEmpty()) {
                val version = stack.removeLast()
                historyOfSubtree.add(version)
                val children = byVersionChildren.getOrElse(version, ::emptyList)
                val childrenWithoutMerges = children.filterNot(CLVersion::isMerge)
                // Order so that child with the lowest id is processed first
                // and comes first in the history.
                stack.addAll(childrenWithoutMerges.sortedByDescending(CLVersion::getObjectHash))
            }
            historyOfSubtree
        }
        return history.filterNot(CLVersion::isMerge)
    }

    private fun indexData(vararg fromVersions: CLVersion): MutableMap<CLVersion, Int> {
        val stack = ArrayDeque<CLVersion>()
        fromVersions.forEach { fromVersion ->
            if (byVersionDistanceFromGlobalRoot.contains(fromVersion)) {
                return@forEach
            }
            stack.addLast(fromVersion)
            while (stack.isNotEmpty()) {
                val version = stack.last()
                val parents = version.getParents()
                // Version is the base version or the first version and therefore a root.
                if (parents.isEmpty()) {
                    stack.removeLast()
                    globalRoot.add(version)
                    byVersionDistanceFromGlobalRoot[version] = if (version.getObjectHash().toString() == baseVersionHash) 0 else 1
                } else {
                    parents.forEach { parent ->
                        byVersionChildren.getOrPut(parent, ::mutableSetOf).add(version)
                    }
                    val (visitedParents, notVisitedParents) = parents.partition(byVersionDistanceFromGlobalRoot::contains)
                    // All children where already visited and have their distance known.
                    if (notVisitedParents.isEmpty()) {
                        stack.removeLast()
                        val depth = visitedParents.maxOf { byVersionDistanceFromGlobalRoot[it]!! } + 1
                        byVersionDistanceFromGlobalRoot[version] = depth
                        // Children need to be visited
                    } else {
                        stack.addAll(notVisitedParents)
                    }
                }
            }
        }
        return byVersionDistanceFromGlobalRoot
    }

    private fun CLVersion.getParents(): List<CLVersion> {
        if (this.getContentHash() == baseVersionHash) {
            return emptyList()
        }
        val ancestors = if (isMerge()) {
            listOf(getMergedVersion1()!!, getMergedVersion2()!!)
        } else {
            listOfNotNull(baseVersion)
        }
        return ancestors.filter { it.getContentHash() != baseVersionHash }
    }
}

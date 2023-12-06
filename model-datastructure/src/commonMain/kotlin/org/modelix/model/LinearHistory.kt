package org.modelix.model

import org.modelix.model.lazy.CLVersion

class LinearHistory(val baseVersionHash: String?) {

    /**
     * Order all versions descending from any versions in [[fromVersions]] topologically.
     * This means that a version must come after all its descendants.
     * Returns the ordered versions starting with the earliest version.
     */
    fun loadLazy(vararg fromVersions: CLVersion) = sequence {
        // The algorithm sorts the versions topologically.
        // It performs a depth-first search.
        // It is implemented as an iterative algorithm with a stack.

        val stack = ArrayDeque<CLVersion>()
        val visited = mutableSetOf<CLVersion>()

        // Ensure deterministic merging,
        // by putting versions with lower id before versions with higher id.
        fromVersions.sortedBy { it.id }.forEach { fromVersion ->
            // Not putting fromVersions directly on the stack and checking visited.contains(fromVersion) ,
            // ensures the algorithm terminates if one version in `fromVersion`
            // is a descendant of another version in `fromVersion`
            if (!visited.contains(fromVersion)) {
                stack.addLast(fromVersion)
            }
            while (stack.isNotEmpty()) {
                val version = stack.last()
                val versionWasVisited = !visited.add(version)
                if (versionWasVisited) {
                    stack.removeLast()
                    if (!version.isMerge()) {
                        yield(version)
                    }
                }
                val descendants = if (version.isMerge()) {
                    // Put version 1 last, so that is processed first.
                    // We are using a stack and the last version is viewed first.
                    listOf(version.getMergedVersion2()!!, version.getMergedVersion1()!!)
                } else {
                    listOfNotNull(version.baseVersion)
                }
                // Ignore descendant, if it is a base version.
                val relevantDescendants = descendants.filter { it.getContentHash() != baseVersionHash }
                // Ignore already visited descendants.
                val nonCheckedDescendants = relevantDescendants.filterNot { visited.contains(it) }
                nonCheckedDescendants.forEach { stack.addLast(it) }
            }
        }
    }

    /**
     * Same as [[loadLazy]], but returning as a list instead of a lazy sequence.
     */
    fun load(vararg fromVersions: CLVersion): List<CLVersion> {
        return loadLazy(*fromVersions).toList()
    }
}

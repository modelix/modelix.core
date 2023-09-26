package org.modelix.model

import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.CPVersion

/**
 * Was introduced in https://github.com/modelix/modelix/commit/19c74bed5921028af3ac3ee9d997fc1c4203ad44
 * together with the UndoOp. The idea is that an undo should only revert changes if there is no other change that relies
 * on it. In that case the undo should do nothing, to not indirectly undo newer changes.
 * For example, if you added a node and someone else started changing properties on the that node, your undo should not
 * remove the node to not lose the property changes.
 * This requires the versions to be ordered in a way that the undo appears later.
 */
class LinearHistory(val baseVersionHash: String?) {

    val version2directDescendants: MutableMap<Long, Set<Long>> = HashMap()
    val versions: MutableMap<Long, CLVersion> = LinkedHashMap()

    /**
     * @param fromVersions it is assumed that the versions are sorted by the oldest version first. When merging a new
     *        version into an existing one the new version should appear after the existing one. The resulting order
     *        will prefer existing versions to new ones, meaning during the conflict resolution the existing changes
     *        have a higher probability of surviving.
     * @returns oldest version first
     */
    fun load(vararg fromVersions: CLVersion): List<CLVersion> {
        for (fromVersion in fromVersions) {
            collect(fromVersion)
        }

        var result: List<Long> = emptyList()

        for (version in versions.values.filter { !it.isMerge() }.sortedBy { it.id }) {
            val descendantIds = collectAllDescendants(version.id).filter { !versions[it]!!.isMerge() }.sorted().toSet()
            val idsInResult = result.toHashSet()
            if (idsInResult.contains(version.id)) {
                result =
                    result +
                    descendantIds.filter { !idsInResult.contains(it) }
            } else {
                result =
                    result.filter { !descendantIds.contains(it) } +
                    version.id +
                    result.filter { descendantIds.contains(it) } +
                    descendantIds.filter { !idsInResult.contains(it) }
            }
        }
        return result.map { versions[it]!! }
    }

    private fun collectAllDescendants(root: Long): Set<Long> {
        val result = LinkedHashSet<Long>()
        var previousSize = 0
        result += root

        while (previousSize != result.size) {
            val nextElements = result.asSequence().drop(previousSize).toList()
            previousSize = result.size
            for (ancestor in nextElements) {
                version2directDescendants[ancestor]?.let { result += it }
            }
        }

        return result.drop(1).toSet()
    }

    private fun collect(root: CLVersion) {
        if (root.getContentHash() == baseVersionHash) return

        var previousSize = versions.size
        versions[root.id] = root

        while (previousSize != versions.size) {
            val nextElements = versions.asSequence().drop(previousSize).map { it.value }.toList()
            previousSize = versions.size

            for (descendant in nextElements) {
                val ancestors = if (descendant.isMerge()) {
                    sequenceOf(
                        getVersion(descendant.data!!.mergedVersion1!!, descendant.store),
                        getVersion(descendant.data!!.mergedVersion2!!, descendant.store),
                    )
                } else {
                    sequenceOf(descendant.baseVersion)
                }.filterNotNull().filter { it.getContentHash() != baseVersionHash }.toList()
                for (ancestor in ancestors) {
                    versions[ancestor.id] = ancestor
                    version2directDescendants[ancestor.id] = (version2directDescendants[ancestor.id] ?: emptySet()) + setOf(descendant.id)
                }
            }
        }
    }

    private fun getVersion(hash: KVEntryReference<CPVersion>, store: IDeserializingKeyValueStore): CLVersion {
        return CLVersion(hash.getValue(store), store)
    }
}

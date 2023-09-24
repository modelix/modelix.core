package org.modelix.model

import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.CPVersion

/**
 * Was introduced in https://github.com/modelix/modelix/commit/19c74bed5921028af3ac3ee9d997fc1c4203ad44
 * together with the UndoOp. The idea is that an undo should only revert changes if there is no other change that relies
 * on it. In that case the undo should do nothing to not indirectly undo newer changes.
 * For example, if you added a node and someone else started changing properties on the that node, your undo should not
 * remove the node to not lose the property changes.
 */
class SlowLinearHistory(val baseVersionHash: String?) {

    val version2descendants: MutableMap<Long, MutableSet<Long>> = HashMap()
    val versions: MutableMap<Long, CLVersion> = HashMap()

    /**
     * Oldest version first
     */
    fun load(vararg fromVersions: CLVersion): List<CLVersion> {
        for (fromVersion in fromVersions) {
            collect(fromVersion, emptyList())
        }

        var result: List<Long> = ArrayList()

        for (version in versions.values.filter { !it.isMerge() }.sortedBy { it.id }) {
            val descendantIds = version2descendants[version.id]!!.sorted().toSet()
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

    private fun collect(version: CLVersion, path: List<CLVersion>) {
        if (version.hash == baseVersionHash) return

        if (!versions.containsKey(version.id)) versions[version.id] = version
        version2descendants.getOrPut(version.id) { HashSet() }.addAll(path.asSequence().map { it.id })

        if (version.isMerge()) {
            val version1 = getVersion(version.data!!.mergedVersion1!!, version.store)
            val version2 = getVersion(version.data!!.mergedVersion2!!, version.store)
            collect(version1, path)
            collect(version2, path)
        } else {
            val previous = version.baseVersion
            if (previous != null) {
                collect(previous, path + version)
            }
        }
    }

    private fun getVersion(hash: KVEntryReference<CPVersion>, store: IDeserializingKeyValueStore): CLVersion {
        return CLVersion(hash.getValue(store), store)
    }
}

class LinearHistory(val baseVersionHash: String?) {

    val version2directDescendants: MutableMap<Long, Set<Long>> = HashMap()
    val versions: MutableMap<Long, CLVersion> = HashMap()

    /**
     * Oldest version first
     */
    fun load(vararg fromVersions: CLVersion): List<CLVersion> {
        for (fromVersion in fromVersions) {
            collect(fromVersion, null)
        }

        var result: List<Long> = ArrayList()

        for (version in versions.values.filter { !it.isMerge() }.sortedBy { it.id }) {
            val descendantIds = HashSet<Long>().also { collectAllDescendants(version.id, it) }.filter { !versions[it]!!.isMerge() }.sorted().toSet()
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

    private fun collectAllDescendants(ancestor: Long, result: MutableSet<Long>, visited: MutableSet<Long> = HashSet()) {
        if (!visited.add(ancestor)) return
        val descendants = version2directDescendants[ancestor] ?: return

        result += descendants
        descendants.forEach {
            collectAllDescendants(it, result, visited)
        }
    }

    private fun collect(version: CLVersion, descendant: CLVersion?) {
        if (version.getContentHash() == baseVersionHash) return
        if (descendant != null) {
            version2directDescendants[version.id] = (version2directDescendants[version.id] ?: emptySet()) + setOf(descendant.id)
        }
        if (versions.containsKey(version.id)) return

        versions[version.id] = version

        if (version.isMerge()) {
            val version1 = getVersion(version.data!!.mergedVersion1!!, version.store)
            val version2 = getVersion(version.data!!.mergedVersion2!!, version.store)
            collect(version1, version)
            collect(version2, version)
        } else {
            val previous = version.baseVersion
            if (previous != null) {
                collect(previous, version)
            }
        }
    }

    private fun getVersion(hash: KVEntryReference<CPVersion>, store: IDeserializingKeyValueStore): CLVersion {
        return CLVersion(hash.getValue(store), store)
    }
}

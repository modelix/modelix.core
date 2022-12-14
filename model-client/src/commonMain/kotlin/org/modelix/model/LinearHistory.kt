package org.modelix.model

import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.KVEntryReference
import org.modelix.model.persistent.CPVersion

class LinearHistory(val baseVersionHash: String?) {

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
            val descendantIds = version2descendants[version.id]!!.sorted()
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

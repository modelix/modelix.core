package org.modelix.model.client2

import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.api.v2.ObjectHash
import org.modelix.model.server.api.v2.ObjectHashAndSerializedObject
import org.modelix.model.server.api.v2.SerializedObject

/**
 * Should only be used by Modelix components.
 */
interface IModelClientV2Internal : IModelClientV2 {
    /**
     * Required for lazy loading.
     * Use [IModelClientV2.lazyLoadVersion]
     */
    suspend fun getObjects(repository: RepositoryId, keys: Sequence<ObjectHash>): Map<ObjectHash, SerializedObject>

    /**
     * Required for lazy loading.
     * Use [IModelClientV2.lazyLoadVersion]
     */
    suspend fun pushObjects(repository: RepositoryId, objects: Sequence<ObjectHashAndSerializedObject>)
}

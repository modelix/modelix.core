/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

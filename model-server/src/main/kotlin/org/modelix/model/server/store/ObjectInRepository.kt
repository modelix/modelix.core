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

package org.modelix.model.server.store

/**
 * A composed key used in the key-value stores to persist model data.
 * The key is composed of a [key] and an optional [repositoryId].
 * The [repositoryId] is set when an entry is scoped to the repository.
 *
 * This class is directly used with the Ignite cache (see [IgniteStoreClient]).
 * Therefore, the order of fields must be consistent with the configuration of `JdbcType` in ignite.xml.
 */
data class ObjectInRepository(private val repositoryId: String, val key: String) : Comparable<ObjectInRepository> {
    fun isGlobal() = repositoryId == ""
    fun getRepositoryId() = repositoryId.takeIf { it != "" }

    override fun compareTo(other: ObjectInRepository): Int {
        repositoryId.compareTo(other.repositoryId).let { if (it != 0) return it }
        return key.compareTo(other.key)
    }

    companion object {
        fun global(key: String) = ObjectInRepository("", key)
        fun create(repositoryId: String?, key: String) = ObjectInRepository(repositoryId ?: "", key)
    }
}

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

package org.modelix.authorization.permissions

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AccessControlData(
    val grantedPermissions: Map<UserId, Set<PermissionId>> = emptyMap(),
) {
    fun withGrantedPermission(user: UserId, permission: PermissionId): AccessControlData = copy(
        grantedPermissions = grantedPermissions + (user to grantedPermissions[user].orEmpty() + permission),
    )

    fun withRemovedPermission(user: UserId, permission: PermissionId): AccessControlData {
        val usersPermissions = grantedPermissions[user] ?: return this
        if (!usersPermissions.contains(permission)) return this
        val newUsersPermissions = usersPermissions - permission
        return copy(
            grantedPermissions = grantedPermissions + (user to newUsersPermissions),
        )
    }

    fun toJson(): String = Json.encodeToString(this)

    companion object {
        fun fromJson(json: String): AccessControlData = Json.decodeFromString(json)
    }
}

interface IAccessControlPersistence {
    fun read(): AccessControlData?
    fun write(data: AccessControlData?)
}

class InMemoryAccessControlPersistence : IAccessControlPersistence {
    private var data: AccessControlData? = null
    override fun read(): AccessControlData? = data

    override fun write(data: AccessControlData?) {
        this.data = data
    }
}

typealias UserId = String
typealias PermissionId = String

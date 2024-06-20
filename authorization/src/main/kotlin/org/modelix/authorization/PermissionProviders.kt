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

package org.modelix.authorization

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.uri
import io.ktor.util.AttributeKey
import org.modelix.authorization.permissions.PermissionParts

object PermissionProviders {
    val KEY = AttributeKey<List<IPermissionProvider>>(PermissionProviders::class.qualifiedName ?: "PermissionProviders")

    fun addProvider(call: ApplicationCall, provider: IPermissionProvider) {
        call.attributes.put(KEY, getProviders(call) + provider)
    }

    fun getProviders(call: ApplicationCall): List<IPermissionProvider> {
        return call.attributes.getOrNull(KEY) ?: emptyList()
    }

    fun checkPermissions(call: ApplicationCall) {
        val providers = getProviders(call)
        if (providers.isEmpty()) {
            throw NoPermissionException("No permissions specified for ${call.request.uri}. Denying by default.")
        }
        for (permission in providers.flatMap { it.getRequiredPermissions() }) {
            call.checkPermission(permission)
        }
    }
}

interface IPermissionProvider {
    fun getRequiredPermissions(): List<PermissionParts>
}

class StaticPermissionProvider(val permissions: List<PermissionParts>) : IPermissionProvider {
    constructor(vararg permissions: PermissionParts) : this(permissions.toList())
    override fun getRequiredPermissions(): List<PermissionParts> = permissions
}

/*
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

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.interfaces.DecodedJWT
import com.google.common.cache.CacheBuilder
import org.keycloak.authorization.client.AuthorizationDeniedException
import org.keycloak.authorization.client.AuthzClient
import org.keycloak.authorization.client.Configuration
import org.keycloak.authorization.client.resource.ProtectedResource
import org.keycloak.representations.idm.authorization.AuthorizationRequest
import org.keycloak.representations.idm.authorization.Permission
import org.keycloak.representations.idm.authorization.PermissionRequest
import org.keycloak.representations.idm.authorization.ResourceRepresentation
import org.keycloak.representations.idm.authorization.ScopeRepresentation
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit

object KeycloakUtils {
    val BASE_URL = System.getenv("KEYCLOAK_BASE_URL")
    val REALM = System.getenv("KEYCLOAK_REALM")
    val CLIENT_ID = System.getenv("KEYCLOAK_CLIENT_ID")
    val CLIENT_SECRET = System.getenv("KEYCLOAK_CLIENT_SECRET")

    fun isEnabled() = BASE_URL != null

    val authzClient: AuthzClient by lazy {
        require(isEnabled()) { "Keycloak is not enabled" }
        patchUrls(AuthzClient.create(Configuration(
            BASE_URL,
            REALM,
            CLIENT_ID,
            mapOf("secret" to CLIENT_SECRET),
            null
        )))
    }

    val jwkProvider: JwkProvider by lazy {
        require(isEnabled()) { "Keycloak is not enabled" }
        JwkProviderBuilder(URL("${BASE_URL}realms/$REALM/protocol/openid-connect/certs")).build()
    }

    private val permissionCache = CacheBuilder.newBuilder()
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .build<Pair<Pair<DecodedJWT, KeycloakResource>, KeycloakScope>, Boolean>()
    private val existingResources = CacheBuilder.newBuilder()
        .expireAfterWrite(3, TimeUnit.MINUTES)
        .build<String, ResourceRepresentation>()

    private fun patchUrls(c: AuthzClient): AuthzClient {
        patchObject(c.serverConfiguration)
        patchObject(c.configuration)
        return c
    }

    private fun patchObject(obj: Any) {
        obj.javaClass.superclass
        var cls: Class<Any>? = obj.javaClass
        while (cls != null) {
            for (field in cls.declaredFields) {
                field.trySetAccessible()
                val value = field.get(obj)
                if (value is String && value.contains("://")) {
                    field.set(obj, patchUrl(value))
                }
            }
            cls = cls.superclass
        }
    }

    private fun patchUrl(url: String): String {
        return if (url.contains("/realms/")) {
            BASE_URL + "realms/" + url.substringAfter("/realms/")
        } else {
            url
        }
    }

    fun getServiceAccountToken(): DecodedJWT {
        return JWT.decode(authzClient.obtainAccessToken().token)
    }

    private fun isAccessToken(token: DecodedJWT): Boolean {
        val authClaim = token.getClaim("authorization")
        return !(authClaim.isNull || authClaim.isMissing)
    }

    private fun readPermissions(token: DecodedJWT): List<Permission> {
        require(isAccessToken(token)) { "Not an access token: ${token.token}" }
        try {
            val rpt = token.token
            val introspect = authzClient.protection().introspectRequestingPartyToken(rpt)
            return introspect.permissions ?: emptyList()
        } catch (e: Exception) {
            throw RuntimeException("Can't get permissions for token: ${token.token}", e)
        }
    }

    private fun createAccessToken(identityToken: DecodedJWT, permissions: List<Pair<String, List<String>>>): DecodedJWT {
        return JWT.decode(authzClient.authorization(identityToken.token).authorize(AuthorizationRequest().also {
            for (permission in permissions) {
                it.addPermission(permission.first, permission.second)
            }
        }).token)
    }

    @Synchronized
    fun hasPermission(identityOrAccessToken: DecodedJWT, resourceSpec: KeycloakResource, scope: KeycloakScope): Boolean {
        val key = identityOrAccessToken to resourceSpec to scope
        return permissionCache.get(key) { checkPermission(identityOrAccessToken, resourceSpec, scope) }
    }

    private fun checkPermission(identityOrAccessToken: DecodedJWT, resourceSpec: KeycloakResource, scope: KeycloakScope): Boolean {
        ensureResourcesExists(resourceSpec, identityOrAccessToken)

        if (isAccessToken(identityOrAccessToken)) {
            val grantedPermissions = readPermissions(identityOrAccessToken)
            val forResource = grantedPermissions.filter { it.resourceName == resourceSpec.name }
            if (forResource.isEmpty()) return false
            val scopes: Set<String> = forResource.mapNotNull { it.scopes }.flatten().toSet()
            if (scopes.isEmpty()) {
                // If the permissions are not restricted to any scope we assume they are valid for all scopes.
                return true
            }
            return scopes.contains(scope.name)
        } else {
            return try {
                createAccessToken(identityOrAccessToken, listOf(resourceSpec.name to listOf(scope.name)))
                true
            } catch (_: AuthorizationDeniedException) {
                false
            }
        }
    }

    @Synchronized
    fun createToken(permissions: List<Pair<KeycloakResource, Set<KeycloakScope>>>): DecodedJWT {
        val requests = permissions.map {
            PermissionRequest(
                ensureResourcesExists(it.first, null).id,
                *it.second.map { it.name }.toTypedArray()
            )
        }
        val ticketResponse = authzClient.protection().permission().create(requests)
        val authResponse = authzClient.authorization(/* service account */).authorize(AuthorizationRequest(ticketResponse.ticket))
        return JWT.decode(authResponse.token)
    }

    @Synchronized
    fun ensureResourcesExists(
        resourceSpec: KeycloakResource,
        owner: DecodedJWT? = null
    ): ResourceRepresentation {
        return existingResources.get(resourceSpec.name) {
            var resource = authzClient.protection().resource().findByNameAnyOwner(resourceSpec.name)
            if (resource != null) return@get resource
//            val protection = owner?.let { authzClient.protection(owner.token) }
//                ?.takeIf { resourceSpec.type.createByUser }
//                ?: authzClient.protection()
            val protection = authzClient.protection()
            resource = ResourceRepresentation().apply {
                name = resourceSpec.name
                scopes = resourceSpec.type.scopes.map { ScopeRepresentation(it.name) }.toSet()
                type = resourceSpec.type.name
//                if (resourceSpec.type.createByUser) ownerManagedAccess = true
                if (resourceSpec.type.createByUser) {
                    attributes = mapOf(
                        "created-by" to listOfNotNull(owner?.subject, owner?.getClaim("email")?.asString()),
                        "creation-timestamp" to listOf(Instant.now().epochSecond.toString())
                    )
                }
            }
            resource = protection.resource().create(resource)
            permissionCache.invalidateAll()
            return@get resource
        }

    }
}

data class KeycloakScope(val name: String) {
    operator fun plus(other: KeycloakScope): Set<KeycloakScope> = setOf(this, other)
    fun toSet() = setOf(this)

    companion object {
        val ADD = KeycloakScope("add") // the user can add a new item, but not remove other items in a list
        val LIST = KeycloakScope("list") // the user can see that an item exists, but not read the contents
        val READ = KeycloakScope("read")
        val WRITE = KeycloakScope("write")
        val DELETE = KeycloakScope("delete")
        val READ_WRITE_DELETE = setOf(READ, WRITE, DELETE)
        val READ_WRITE_DELETE_LIST = setOf(READ, WRITE, DELETE, LIST)
        val READ_WRITE = setOf(READ, WRITE)
        val READ_WRITE_LIST = setOf(READ, WRITE, LIST)
        val READ_ONLY = setOf(READ)
        val READ_LIST = setOf(READ, LIST)
        val ALL_SCOPES = READ_WRITE_DELETE_LIST
    }
}
fun EPermissionType.toKeycloakScope(): KeycloakScope = when (this) {
    EPermissionType.READ -> KeycloakScope.READ
    EPermissionType.WRITE -> KeycloakScope.WRITE
}

data class KeycloakResource(val name: String, val type: KeycloakResourceType) {

}

data class KeycloakResourceType(val name: String, val scopes: Set<KeycloakScope>, val createByUser: Boolean = false) {
    fun createInstance(resourceName: String) = KeycloakResource(this.name + "/" + resourceName, this)

    companion object {
        val DEFAULT_TYPE = KeycloakResourceType("default", KeycloakScope.READ_WRITE)
        val MODEL_SERVER_ENTRY = KeycloakResourceType("model-server-entry", KeycloakScope.READ_WRITE_DELETE)
        val REPOSITORY = KeycloakResourceType("repository", KeycloakScope.READ_WRITE_DELETE_LIST)
    }
}

fun String.asResource() = KeycloakResourceType.DEFAULT_TYPE.createInstance(this)

private fun ProtectedResource.findByNameAnyOwner(name: String): ResourceRepresentation? {
    val resources: List<ResourceRepresentation> = org.modelix.authorization.KeycloakUtils.authzClient.protection().resource()
        .find(
            null,
            name,
            null,
            null,
            null,
            null,
            false,
            true,
            true,
            null,
            null
        )
    return resources.firstOrNull()
}
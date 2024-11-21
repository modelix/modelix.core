package org.modelix.authorization.permissions

import com.auth0.jwt.interfaces.DecodedJWT
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.authorization.IAccessControlDataProvider
import org.modelix.authorization.ModelixJWTUtil
import java.io.File
import kotlin.collections.get

@Serializable
data class AccessControlData(
    /**
     * User ID to granted permission IDs
     */
    val grantsToUsers: Map<String, Set<String>> = emptyMap(),

    /**
     * Grants based on user roles extracted from the JWT token.
     */
    val grantsToRoles: Map<String, Set<String>> = emptyMap(),
) {

    /**
     * Load permissions for an identity token.
     * Identity tokens, unlike access tokens, don't have any permissions encoded in the token.
     * The resource server then is expected to know which permissions the user has.
     */
    fun load(jwt: DecodedJWT, permissionEvaluator: PermissionEvaluator) {
        val util = ModelixJWTUtil()
        if (util.isAccessToken(jwt)) {
            // The purpose of an access token is to restrict the permissions to the ones specified in the token.
            return
        }
        val userId = util.extractUserId(jwt)
        for (permissionId in (grantsToUsers[userId] ?: emptyList())) {
            permissionEvaluator.grantPermission(permissionId)
        }
        val roles = util.extractUserRoles(jwt)
        for (role in roles) {
            for (permissionId in (grantsToRoles[role] ?: emptyList())) {
                permissionEvaluator.grantPermission(permissionId)
            }
        }
    }

    fun withLegacyRoles(): AccessControlData {
        return this
            .withGrantToRole("modelix-user", PermissionSchemaBase.cluster.user.fullId)
            .withGrantToRole("modelix-admin", PermissionSchemaBase.cluster.admin.fullId)
    }

    fun withGrantToRole(role: String, permissionId: String): AccessControlData {
        return copy(grantsToRoles = grantsToRoles + (role to (grantsToRoles[role] ?: emptySet()) + permissionId))
    }

    fun withGrantToUser(user: String, permissionId: String): AccessControlData {
        return copy(grantsToUsers = grantsToUsers + (user to (grantsToUsers[user] ?: emptySet()) + permissionId))
    }

    fun withoutGrantToUser(user: String, permissionId: String): AccessControlData {
        val newGrants = (grantsToUsers[user] ?: emptySet()) - permissionId
        return if (newGrants.isEmpty()) {
            copy(grantsToUsers = grantsToUsers - user)
        } else {
            copy(grantsToUsers = grantsToUsers + (user to newGrants))
        }
    }

    fun withoutGrantToRole(role: String, permissionId: String): AccessControlData {
        val newGrants = (grantsToRoles[role] ?: emptySet()) - permissionId
        return if (newGrants.isEmpty()) {
            copy(grantsToRoles = grantsToRoles - role)
        } else {
            copy(grantsToRoles = grantsToRoles + (role to newGrants))
        }
    }
}

interface IAccessControlPersistence : IAccessControlDataProvider {
    fun read(): AccessControlData
    fun update(updater: (AccessControlData) -> AccessControlData)
    override fun getGrantedPermissionsForUser(userId: String): Set<PermissionParts> =
        read().grantsToUsers[userId]?.map { PermissionParts.fromString(it) }?.toSet() ?: emptySet()
    override fun getGrantedPermissionsForRole(role: String): Set<PermissionParts> =
        read().grantsToRoles[role]?.map { PermissionParts.fromString(it) }?.toSet() ?: emptySet()
}

class FileSystemAccessControlPersistence(val file: File) : IAccessControlPersistence {

    private var data: AccessControlData = if (file.exists()) {
        Json.decodeFromString<AccessControlData>(file.readText())
    } else {
        AccessControlData()
    }.withLegacyRoles()

    override fun read(): AccessControlData {
        return data
    }

    @Synchronized
    override fun update(updater: (AccessControlData) -> AccessControlData) {
        data = updater(data)
        writeFile()
    }

    private fun writeFile() {
        file.writeText(Json.encodeToString(data))
    }
}

class InMemoryAccessControlPersistence : IAccessControlPersistence {

    private var data: AccessControlData = AccessControlData().withLegacyRoles()

    override fun read(): AccessControlData {
        return data
    }

    @Synchronized
    override fun update(updater: (AccessControlData) -> AccessControlData) {
        data = updater(data)
    }
}

package org.modelix.model.server

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.authorization.permissions.AccessControlData
import org.modelix.authorization.permissions.IAccessControlPersistence
import org.modelix.model.server.store.IGenericStoreClient
import org.modelix.model.server.store.RequiresTransaction

class DBAccessControlPersistence<E>(val store: IGenericStoreClient<E>, val key: E) : IAccessControlPersistence {
    private val json = Json { ignoreUnknownKeys = true }
    override fun read(): AccessControlData {
        @OptIn(RequiresTransaction::class)
        return store.runReadTransaction {
            (store.get(key)?.let { json.decodeFromString(it) } ?: AccessControlData()).withLegacyRoles()
        }
    }

    override fun update(updater: (AccessControlData) -> AccessControlData) {
        @OptIn(RequiresTransaction::class)
        return store.runWriteTransaction {
            val oldData = read()
            val newData = updater(oldData)
            if (oldData == newData) return@runWriteTransaction
            store.put(key, json.encodeToString(newData))
        }
    }
}

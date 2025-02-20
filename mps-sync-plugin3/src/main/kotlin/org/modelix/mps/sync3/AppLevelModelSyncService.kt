package org.modelix.mps.sync3

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2

@Service(Service.Level.APP)
class AppLevelModelSyncService() : Disposable {

    companion object {
        fun getInstance(): AppLevelModelSyncService {
            return ApplicationManager.getApplication().getService(AppLevelModelSyncService::class.java)
        }
    }

    private val connections = LinkedHashMap<String, ServerConnection>()
    private val coroutinesScope = CoroutineScope(Dispatchers.Default)
    private val connectionCheckingJob = coroutinesScope.launchLoop(
        BackoffStrategy(
            initialDelay = 3_000,
            maxDelay = 10_000,
            factor = 1.2,
        ),
    ) {
        for (connection in synchronized(connections) { connections.values.toList() }) {
            connection.checkConnection()
        }
    }

    @Synchronized
    fun getConnections() = synchronized(connections) { connections.values.toList() }

    @Synchronized
    fun addConnection(url: String): ServerConnection {
        return synchronized(connections) { connections.getOrPut(url) { ServerConnection(url) } }
    }

    override fun dispose() {
        coroutinesScope.cancel("disposed")
    }

    class ServerConnection(val url: String) {
        private var client: ValueWithMutex<IModelClientV2?> = ValueWithMutex(null)
        private var connected: Boolean = false

        suspend fun getClient(): IModelClientV2 {
            return client.getValue() ?: client.updateValue {
                it ?: ModelClientV2.Companion.builder().url(url).build().also { it.init() }
            }
        }

        suspend fun checkConnection() {
            try {
                getClient().getServerId()
                connected = true
            } catch (ex: Throwable) {
                connected = false
            }
        }
    }
}

package org.modelix.mps.sync3

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.oauth.IAuthConfig
import org.modelix.model.oauth.IAuthRequestHandler
import org.modelix.model.oauth.OAuthConfig
import org.modelix.model.oauth.OAuthConfigBuilder

@Service(Service.Level.APP)
class AppLevelModelSyncService() : Disposable {

    companion object {
        fun getInstance(): AppLevelModelSyncService {
            return ApplicationManager.getApplication().getService(AppLevelModelSyncService::class.java)
        }
    }

    private val connections = LinkedHashMap<ModelServerConnectionProperties, ServerConnection>()
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
    fun addConnection(properties: ModelServerConnectionProperties): ServerConnection {
        return synchronized(connections) { connections.getOrPut(properties) { ServerConnection(properties) } }
    }

    override fun dispose() {
        coroutinesScope.cancel("disposed")
    }

    class ServerConnection(val properties: ModelServerConnectionProperties) {
        private var client: ValueWithMutex<IModelClientV2?> = ValueWithMutex(null)
        private var connected: Boolean = false
        private val authRequestHandler = AsyncAuthRequestHandler()
        private var authConfig: IAuthConfig = IAuthConfig.oauth {
            clientId(properties.oauthClientId ?: "external-mps")
            properties.oauthClientSecret?.let { clientSecret(it) }
            authRequestHandler(authRequestHandler)
            properties.repositoryId?.let { repositoryId(it) }
        }

        suspend fun getClient(): IModelClientV2 {
            return client.getValue() ?: client.updateValue {
                it ?: ModelClientV2.builder()
                    .url(properties.url)
                    .authConfig(authConfig)
                    .lazyAndBlockingQueries()
                    .build()
                    .also { it.init() }
            }
        }

        suspend fun checkConnection() {
            try {
                getClient().getServerId()
                connected = true
                authRequestHandler.clear()
            } catch (ex: Throwable) {
                connected = false
            }
        }

        fun isConnected(): Boolean = connected

        fun setAuthorizationConfig(config: IAuthConfig) {
            this.authConfig = config
            runBlocking { client.updateValue { null } }
        }

        fun configureOAuth(body: OAuthConfigBuilder.() -> Unit) {
            this.authConfig = OAuthConfigBuilder(this.authConfig as? OAuthConfig).apply(body).build()
            runBlocking { client.updateValue { null } }
        }

        fun getPendingAuthRequest(): String? = authRequestHandler.getPendingRequest()
    }
}

private class AsyncAuthRequestHandler : IAuthRequestHandler {
    private var pendingUrl: String? = null

    override fun browse(url: String) {
        pendingUrl = url
    }

    fun getPendingRequest(): String? = pendingUrl

    fun clear() {
        pendingUrl = null
    }
}

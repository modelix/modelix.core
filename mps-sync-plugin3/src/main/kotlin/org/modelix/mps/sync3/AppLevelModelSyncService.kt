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

    @Synchronized
    fun getConnections() = synchronized(connections) { connections.values.toList() }

    @Synchronized
    fun getOrCreateConnection(properties: ModelServerConnectionProperties): ServerConnection {
        return synchronized(connections) { connections.getOrPut(properties) { ServerConnection(properties, coroutinesScope) } }
    }

    override fun dispose() {
        coroutinesScope.cancel("disposed")
        connections.values.forEach { it.dispose() }
    }

    class ServerConnection(val properties: ModelServerConnectionProperties, val coroutinesScope: CoroutineScope) {
        private var client: ValueWithMutex<ModelClientV2?> = ValueWithMutex(null)
        private var connected: Boolean = false
        private var enabled: Boolean = true
        private var disposed: Boolean = false
        private val authRequestHandler = AsyncAuthRequestHandler()
        private var authConfig: IAuthConfig = IAuthConfig.oauth {
            clientId(properties.oauthClientId ?: "external-mps")
            properties.oauthClientSecret?.let { clientSecret(it) }
            authRequestHandler(authRequestHandler)
            properties.repositoryId?.let { repositoryId(it) }
        }
        private val connectionCheckingJob = coroutinesScope.launchLoop(
            BackoffStrategy(
                initialDelay = 3_000,
                maxDelay = 10_000,
                factor = 1.2,
            ),
        ) { checkConnection() }
        suspend fun getClient(): IModelClientV2 {
            checkDisposed()
            check(enabled) { "disabled" }
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
            if (!enabled) return
            try {
                getClient().getServerId()
                connected = true
                authRequestHandler.clear()
            } catch (ex: Throwable) {
                connected = false
            }
        }

        fun isConnected(): Boolean = connected

        fun isEnabled() = enabled

        fun setEnabled(enabled: Boolean) = if (enabled) enable() else disable()

        fun enable() {
            checkDisposed()
            if (enabled) return
            enabled = true
        }

        fun disable() {
            if (!enabled) return
            enabled = false
            disconnect()
        }

        fun disconnect() {
            checkDisposed()
            authRequestHandler.clear()
            runBlocking {
                client.updateValue {
                    it?.close()
                    null
                }
            }
            connected = false
        }

        fun setAuthorizationConfig(config: IAuthConfig) {
            checkDisposed()
            this.authConfig = config
            runBlocking { client.updateValue { null } }
        }

        fun configureOAuth(body: OAuthConfigBuilder.() -> Unit) {
            checkDisposed()
            this.authConfig = OAuthConfigBuilder(this.authConfig as? OAuthConfig).apply(body).build()
            runBlocking { client.updateValue { null } }
        }

        fun getPendingAuthRequest(): String? {
            checkDisposed()
            return authRequestHandler.getPendingRequest()
        }

        fun dispose() {
            disable()
            disposed = true
        }

        private fun checkDisposed() = check(!disposed) { "disposed" }
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

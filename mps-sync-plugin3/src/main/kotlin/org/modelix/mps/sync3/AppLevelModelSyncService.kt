package org.modelix.mps.sync3

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.modelix.model.api.runSynchronized
import org.modelix.model.client2.IModelClientV2
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.oauth.IAuthConfig
import org.modelix.model.oauth.IAuthRequest
import org.modelix.model.oauth.IAuthRequestHandler
import org.modelix.model.oauth.OAuthConfig
import org.modelix.model.oauth.OAuthConfigBuilder
import org.modelix.model.oauth.TokenParameters
import java.util.Collections

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
            properties.branchRef?.let { tokenParameters(TokenParameters(it)) }
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
            authRequestHandler.cancelAll()
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

        fun getPendingAuthRequest(): List<IAuthRequest> {
            checkDisposed()
            return authRequestHandler.getPendingRequests()
        }

        fun dispose() {
            disable()
            disposed = true
        }

        private fun checkDisposed() = check(!disposed) { "disposed" }
    }
}

private class AsyncAuthRequestHandler : IAuthRequestHandler {
    private val pendingRequests = Collections.synchronizedCollection(LinkedHashSet<IAuthRequest>())

    private fun cleanup() {
        runSynchronized(pendingRequests) {
            pendingRequests.removeIf { !it.isActive() }
        }
    }

    override fun browse(request: IAuthRequest) {
        cleanup()
        pendingRequests.add(request)
    }

    fun getPendingRequests(): List<IAuthRequest> {
        cleanup()
        return pendingRequests.toList()
    }

    fun cancelAll() {
        pendingRequests.filter { it.isActive() }.forEach { it.cancel() }
    }
}

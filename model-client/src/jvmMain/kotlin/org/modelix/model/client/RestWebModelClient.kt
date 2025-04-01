package org.modelix.model.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.serialization.ContentConverter
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.core.Closeable
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore
import org.modelix.model.KeyValueStoreCache
import org.modelix.model.api.IIdGenerator
import org.modelix.model.lazy.IDeserializingKeyValueStore
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.oauth.ModelixAuthClient
import org.modelix.model.persistent.HashUtil
import org.modelix.model.sleep
import org.modelix.model.util.StreamUtils.toStream
import org.modelix.streams.FlowStreamBuilder
import org.modelix.streams.IStreamExecutor
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

val HttpResponse.successful: Boolean
    get() = this.status.value in 200..299

val HttpResponse.unsuccessful: Boolean
    get() = !successful

val HttpResponse.forbidden: Boolean
    get() = status == HttpStatusCode.Forbidden

interface ConnectionListener {
    fun receivedForbiddenResponse()
    fun receivedSuccessfulResponse()
}

typealias ConnectionStatusListener = (oldValue: RestWebModelClient.ConnectionStatus, newValue: RestWebModelClient.ConnectionStatus) -> Unit

/**
 * We need to specify the connection listeners right into the constructor because connection is started in the constructor.
 */
class RestWebModelClient @JvmOverloads constructor(
    var baseUrl: String = defaultUrl,
    private val authTokenProvider: (() -> String?)? = null,
    initialConnectionListeners: List<ConnectionListener> = emptyList(),
    providedClient: HttpClient? = null,
) : IModelClient, Closeable {

    companion object {
        private val LOG = mu.KotlinLogging.logger {}
        const val MODEL_URI_VAR_NAME = "MODEL_URI"
        val modelUrlFromEnv: String?
            get() {
                var url = System.getProperty(MODEL_URI_VAR_NAME)
                if (url.isNullOrEmpty()) {
                    url = System.getenv(MODEL_URI_VAR_NAME)
                }
                return url
            }

        val defaultUrl: String
            get() {
                val urlFromEnv = modelUrlFromEnv
                return if (urlFromEnv.isNullOrEmpty()) {
                    "http://localhost:28101/"
                } else {
                    urlFromEnv
                }
            }
    }

    @Deprecated("Replaced by connectionStatusListeners")
    private val connectionListeners = LinkedList(initialConnectionListeners)

    override val clientId: Int
        get() {
            if (clientIdInternal == 0) {
                throw IllegalStateException("Client ID is not initialized yet. Client state: $connectionStatus")
            }
            return clientIdInternal
        }
    private var clientIdInternal: Int = 0
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var watchdogJob: Job? = null
    private val client = (providedClient ?: HttpClient(CIO)).config {
        this.followRedirects = false
        install(HttpTimeout) {
            connectTimeoutMillis = 30.seconds.inWholeMilliseconds
            requestTimeoutMillis = 30.seconds.inWholeMilliseconds
        }
        install(ContentNegotiation) {
            this.register(
                ContentType.Application.Json,
                object : ContentConverter {
                    override suspend fun deserialize(charset: Charset, typeInfo: TypeInfo, content: ByteReadChannel): Any {
                        val str = content.readRemaining().readText(charset)
                        return if (typeInfo.type == JSONArray::class) JSONArray(str) else JSONObject(str)
                    }

                    override suspend fun serialize(
                        contentType: ContentType,
                        charset: Charset,
                        typeInfo: TypeInfo,
                        value: Any?,
                    ): OutgoingContent? {
                        return TextContent(value.toString(), contentType)
                    }
                },
            )
        }
        install(Auth) {
            bearer {
                loadTokens {
                    val tp = authTokenProvider
                    if (tp == null) {
                        ModelixAuthClient.getTokens()?.let { BearerTokens(it.accessToken, it.refreshToken) }
                    } else {
                        val token = tp()
                        if (token == null) {
                            connectionStatus = ConnectionStatus.WAITING_FOR_TOKEN
                            null
                        } else {
                            BearerTokens(token, "")
                        }
                    }
                }
                refreshTokens {
                    val tp = authTokenProvider
                    if (tp == null) {
                        null
                    } else {
                        val providedToken = tp()
                        if (providedToken != null && providedToken != this.oldTokens?.accessToken) {
                            BearerTokens(providedToken, "")
                        } else {
                            connectionStatus = ConnectionStatus.WAITING_FOR_TOKEN
                            null
                        }
                    }
                }
            }
        }
        install(HttpRequestRetry) {
            retryOnExceptionOrServerErrors(maxRetries = 3)
            exponentialDelay()
            modifyRequest {
                try {
//                    connectionStatus = ConnectionStatus.SERVER_ERROR
                    runBlocking {
                        response?.let { println(it.bodyAsText()) }
                    }
                } catch (e: Exception) {
                    LOG.debug("", e)
                }
            }
        }
    }.apply {
        plugin(HttpSend).intercept { request ->
            val call = execute(request)
            val response = call.response
            if (response.successful) {
                receivedSuccessfulResponse()
            } else {
                if (response.status == HttpStatusCode.Unauthorized) {
                    receivedForbiddenResponse()
                }
            }
            call
        }
    }
    private val listeners: MutableList<PollingListener> = ArrayList()
    override val asyncStore: IKeyValueStore = AsyncStore(this)
    private val cache = createObjectStoreCache(KeyValueStoreCache(asyncStore))
    private val pendingWrites = AtomicInteger(0)
    var connectionStatus: ConnectionStatus = ConnectionStatus.NEW
        private set(value) {
            val oldValue = field
            field = value
            if (oldValue == value) return

            for (listener in connectionStatusListeners) {
                try {
                    listener(oldValue, value)
                } catch (e: Exception) {
                    LOG.error("Exception in status listener", e)
                }
            }
        }
    private var connectionStatusListeners: Set<ConnectionStatusListener> = emptySet()

    override fun getStreamExecutor(): IStreamExecutor = FlowStreamBuilder.INSTANCE.getStreamExecutor()

    private fun startConnectionWatchdog() {
        watchdogJob = coroutineScope.launch {
            while (isActive) {
                try {
                    connectNow()
                    if (connectionStatus == ConnectionStatus.CONNECTED) {
                        delay(10.seconds)
                    } else {
                        delay(3.seconds)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    LOG.debug("", e)
                    if (connectionStatus == ConnectionStatus.CONNECTED) {
                        connectionStatus = ConnectionStatus.DISCONNECTED
                    }
                    delay(3.seconds)
                }
            }
        }
    }

    fun reconnect() {
        runBlocking { connectNow() }
    }

    private suspend fun connectNow() {
        val response = client.get(baseUrl + "getEmail")
        if (response.successful || response.status == HttpStatusCode.NotFound) {
            if (clientIdInternal == 0) {
                loadClientId()
            }
            if (clientIdInternal != 0 && idGeneratorInternal == null) {
                idGeneratorInternal = IdGenerator.getInstance(clientIdInternal)
            }
            connectionStatus = ConnectionStatus.CONNECTED
        } else if (response.status == HttpStatusCode.Unauthorized) {
            connectionStatus = ConnectionStatus.WAITING_FOR_TOKEN
        }
    }

    fun addStatusListener(listener: ConnectionStatusListener) {
        connectionStatusListeners += listener
    }
    fun removeStatusListener(listener: ConnectionStatusListener) {
        connectionStatusListeners -= listener
    }

    private suspend fun loadClientId() {
        val targetUri = baseUrl + "counter/clientId"
        try {
            val response = client.post(targetUri)
            val body = response.bodyAsText()
            if (response.unsuccessful) {
                throw RuntimeException("Unable to get the clientId by querying $targetUri: ${response.status}\n$body")
            }
            clientIdInternal = body.toInt()
            LOG.info("Client ID received: $clientIdInternal")
        } catch (e: Exception) {
            throw RuntimeException("Unable to get the clientId by querying $targetUri", e)
        }
    }

    override val idGenerator: IIdGenerator = object : IIdGenerator {
        override fun generate(): Long {
            if (idGeneratorInternal == null) {
                runBlocking {
                    connectNow()
                }
            }
            val gen = idGeneratorInternal ?: throw IllegalStateException("Not connected yet")
            return gen.generate()
        }
    }

    private var idGeneratorInternal: IIdGenerator? = null

    override fun toString() = "RestWebModelClient($baseUrl)"

    private fun receivedForbiddenResponse() {
        connectionListeners.forEach { it.receivedForbiddenResponse() }
    }

    private fun receivedSuccessfulResponse() {
        connectionListeners.forEach { it.receivedSuccessfulResponse() }
    }

    fun addForbiddenListener(listener: ConnectionListener) {
        connectionListeners.add(listener)
    }

    fun dispose() {
        synchronized(listeners) {
            listeners.forEach { it.dispose() }
            listeners.clear()
        }
        coroutineScope.cancel("model client disposed")
        watchdogJob?.cancel("model client disposed")
    }

    override fun close() {
        dispose()
    }

    override fun getPendingSize(): Int = pendingWrites.get()

    override fun get(key: String): String? {
        return runBlocking { getA(key) }
    }

    override fun getIfCached(key: String): String? {
        return null // doesn't contain any caches
    }

    override suspend fun getA(key: String): String? {
        val isHash = HashUtil.isSha256(key)
        if (isHash) {
            if (LOG.isDebugEnabled) {
                LOG.debug("GET $key")
            }
        }
        val start = System.currentTimeMillis()
        val uri = baseUrl + "get/" + URLEncoder.encode(key, StandardCharsets.UTF_8)
        try {
            val response = client.get(uri)
            return when (response.status) {
                HttpStatusCode.OK -> {
                    val value = response.bodyAsText()
                    val end = System.currentTimeMillis()
                    if (isHash) {
                        if (LOG.isDebugEnabled) {
                            LOG.debug("GET " + key + " took " + (end - start) + " ms: " + value)
                        }
                    }
                    value
                }
                HttpStatusCode.NotFound -> {
                    null
                }
                else -> {
                    throw RuntimeException("Request for key '" + key + "' failed: " + response.status)
                }
            }
        } catch (e: Exception) {
            throw RuntimeException("Unable to connect to '$uri' to get key $key", e)
        }
    }

    override fun getAll(keys: Iterable<String>): Map<String, String?> {
        return runBlocking { getAllA(keys) }
    }

    suspend fun getAllA(keys: Iterable<String>): Map<String, String?> {
        if (!keys.iterator().hasNext()) {
            return HashMap()
        }

        val result: MutableMap<String, String?> = LinkedHashMap(16, 0.75.toFloat(), false)
        var json = JSONArray()
        val batch = suspend {
            val response = client.put(baseUrl + "getAll") {
                setBody(json)
                contentType(ContentType.Application.Json)
            }
            if (response.status == HttpStatusCode.OK) {
                val jsonStr = response.bodyAsText()
                val responseJson = JSONArray(jsonStr)
                for (entry_: Any in responseJson) {
                    val entry = entry_ as JSONObject
                    result[entry.getString("key")] = entry.optString("value", null)
                }
                json = JSONArray()
            } else {
                throw RuntimeException(
                    String.format(
                        "Request for %d keys failed (%s, ...): %s",
                        keys.spliterator().exactSizeIfKnown,
                        toStream(keys).findFirst().orElse(null),
                        response.status,
                    ),
                )
            }
        }

        for (key in keys) {
            json.put(key)
            if (json.length() >= 5000) batch()
        }

        if (json.length() > 0) batch()

        return result
    }

    fun getEmail(): String {
        return runBlocking { getEmailA() }
    }

    suspend fun getEmailA(): String {
        val response = client.get(baseUrl + "getEmail")
        if (response.successful) {
            return response.bodyAsText()
        } else {
            throw RuntimeException("Request for e-mail address failed: " + response.status)
        }
    }

    override fun listen(key: String, listener: IKeyListener) {
        val pollingListener = PollingListener(key, listener)
        synchronized(listeners) {
            listeners.add(pollingListener)
            pollingListener.start()
        }
    }

    override fun removeListener(key: String, listener: IKeyListener) {
        synchronized(listeners) {
            val toRemove = listeners.filter { it.key == key && it.keyListener === listener }
            listeners.removeAll(toRemove)
            toRemove.forEach { it.dispose() }
        }
    }

    override fun put(key: String, value: String?) {
        runBlocking { putA(key, value) }
    }

    suspend fun putA(key: String, value: String?) {
        if (!key.matches(HashUtil.HASH_PATTERN)) {
            if (LOG.isDebugEnabled) {
                LOG.debug("PUT $key = $value")
            }
        }
        let {
            val url = baseUrl + "put/" + URLEncoder.encode(key, StandardCharsets.UTF_8)
            try {
                val response = client.put(url) {
                    setBody(value)
                }
                if (response.unsuccessful) {
                    throw RuntimeException("Failed to store entry (${response.status}) $key = $value. " + response.bodyAsText())
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed executing a put to $url", e)
            }
        }
    }

    fun sortEntriesByDependency(unsorted: Map<String, String?>): Map<String, String?> {
        val sorted = LinkedHashMap<String, String?>()
        object {
            fun putEntry(key: String, value: String?) {
                if (sorted.containsKey(key)) return
                for (depKey in HashUtil.extractSha256(value)) {
                    if (sorted.containsKey(depKey)) continue
                    if (unsorted.containsKey(depKey)) {
                        val depValue = unsorted[depKey]
                        putEntry(depKey, depValue)
                    }
                }
                sorted[key] = value
            }

            fun putAll() {
                for (entry in unsorted) {
                    putEntry(entry.key, entry.value)
                }
            }
        }.putAll()

        return sorted
    }

    override fun putAll(entries: Map<String, String?>) {
        runBlocking { putAllA(entries) }
    }

    suspend fun putAllA(entries_: Map<String, String?>) {
        val entries = sortEntriesByDependency(entries_)
        val sendBatch: suspend (JSONArray, Int) -> Unit = sendBatch@{ json: JSONArray, remaining: Int ->
            for (attempt in 1..3) {
                if (LOG.isDebugEnabled) {
                    LOG.debug("PUT batch of ${json.length()} entries, $remaining remaining")
                }
                val response = client.put(baseUrl + "putAll") {
                    setBody(json)
                    contentType(ContentType.Application.Json)
                }
                if (response.successful) {
                    return@sendBatch
                }
                val message = String.format(
                    "Failed to store %d entries (%s) %s: %s (attempt %d)",
                    entries.size,
                    response.status,
                    entries.entries.stream().map { e: Map.Entry<String?, String?> -> e.key.toString() + " = " + e.value + ", ..." }.findFirst().orElse(""),
                    response.bodyAsText(),
                    attempt,
                )
                if (attempt == 3) throw RuntimeException(message) else LOG.warn(message)
                sleep(1000)
            }
        }
        if (LOG.isDebugEnabled) {
            LOG.debug("PUT " + entries.size + " entries")
        }

        let {
            var remainingEntries = entries.size
            try {
                pendingWrites.addAndGet(remainingEntries)
                var json = JSONArray()
                var approxSize = 0
                for ((key, value) in entries) {
                    val jsonEntry = JSONObject()
                    jsonEntry.put("key", key)
                    jsonEntry.put("value", value)
                    approxSize += key.length
                    approxSize += value?.length ?: 4
                    json.put(jsonEntry)
                    if (!key.matches(HashUtil.HASH_PATTERN)) {
                        if (LOG.isDebugEnabled) {
                            LOG.debug("PUT $key = $value")
                        }
                    }
                    if (json.length() >= 5000 || approxSize > 10000000) {
                        sendBatch(json, remainingEntries)
                        remainingEntries -= json.length()
                        pendingWrites.addAndGet(-json.length())
                        json = JSONArray()
                        approxSize = 0
                    }
                }
                if (json.length() > 0) {
                    sendBatch(json, remainingEntries)
                }
            } finally {
                pendingWrites.addAndGet(-remainingEntries)
            }
        }
    }

    override fun prefetch(key: String) {}

    override val storeCache: IDeserializingKeyValueStore
        get() = cache.getLegacyObjectStore()

    inner class PollingListener(val key: String, val keyListener: IKeyListener) {
        private var lastValue: String? = null
        private var job: Job? = null
        var nextDelay: Duration = 1.seconds
        fun dispose() {
            job?.cancel("listener disposed")
        }
        fun start() {
            job = coroutineScope.launch {
                while (isActive) {
                    try {
                        if (connectionStatus == ConnectionStatus.CONNECTED) {
                            run()
                        } else {
                            delay(1.seconds)
                        }
                        nextDelay = 1.seconds
                    } catch (e: CancellationException) {
                        break
                    } catch (e: Exception) {
                        LOG.error("Polling for '$key' failed", e)
                        delay(nextDelay)
                        nextDelay = (nextDelay * 1.2).coerceAtMost(2.minutes)
                    }
                }
            }
        }
        private suspend fun run() {
            var url = baseUrl + "poll/" + URLEncoder.encode(key, StandardCharsets.UTF_8)
            if (lastValue != null) {
                url += "?lastKnownValue=" + URLEncoder.encode(lastValue, StandardCharsets.UTF_8)
            }

            val value: String?
            val response = client.get(url) {
                timeout {
                    requestTimeoutMillis = 60.seconds.inWholeMilliseconds // long polling
                }
            }
            if (response.status == HttpStatusCode.NotFound) {
                delay(1.seconds)
            } else {
                if (response.unsuccessful) {
                    throw RuntimeException("Request for key '" + key + "' failed: " + response.status)
                }
                value = response.bodyAsText()
                if (value != lastValue) {
                    lastValue = value
                    keyListener.changed(key, value)
                }
            }
        }
    }

    init {
        if (baseUrl.isEmpty()) {
            baseUrl = defaultUrl
        }
        if (!(baseUrl.endsWith("/"))) {
            baseUrl += "/"
        }
        startConnectionWatchdog()
    }

    enum class ConnectionStatus {
        NEW,
        WAITING_FOR_TOKEN,
        DISCONNECTED,

//        SERVER_ERROR,
        CONNECTED,
    }
}

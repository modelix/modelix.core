package org.modelix.model.server.store

import mu.KotlinLogging
import org.apache.ignite.Ignite
import org.apache.ignite.IgniteCache
import org.apache.ignite.IgniteSemaphore
import org.apache.ignite.Ignition
import org.apache.ignite.cache.CachePeekMode
import org.apache.ignite.cache.query.ScanQuery
import org.apache.ignite.internal.IgnitionEx
import org.apache.ignite.lang.IgniteBiPredicate
import org.apache.ignite.lang.IgniteClosure
import org.apache.ignite.transactions.TransactionConcurrency
import org.apache.ignite.transactions.TransactionIsolation
import org.modelix.kotlin.utils.ContextValue
import org.modelix.model.IGenericKeyListener
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.HashUtil
import org.modelix.model.server.SqlUtils
import java.sql.SQLException
import java.util.*
import javax.cache.Cache
import javax.sql.DataSource

private val LOG = KotlinLogging.logger { }

/**
 * Store client implementation with an ignite cache.
 * If [inmemory] is true, the data is not persisted in a database.
 */
class IgniteStoreClient(jdbcProperties: Properties? = null, private val inmemory: Boolean = false) :
    IsolatingStore,
    ITransactionManager,
    IRepositoryAwareStore,
    AutoCloseable {

    companion object {
        private const val ENTRY_CHANGED_TOPIC = "entryChanged"
    }

    private lateinit var ignite: Ignite
    private val cache: IgniteCache<ObjectInRepository, String?>
    private val changeNotifier = ChangeNotifier(this)
    private val pendingChangeMessages = PendingChangeMessages {
        ignite.message().send(ENTRY_CHANGED_TOPIC, it)
    }

    private val igniteConfigName: String = if (inmemory) "ignite-inmemory.xml" else "ignite.xml"
    private val dataSource: DataSource by lazy {
        Ignition.loadSpringBean(
            IgniteStoreClient::class.java.getResource(igniteConfigName),
            "dataSource",
        )
    }

    private val localLocks = TransactionLocks()
    private val maxReadTransactions = 1_000
    private val globalReadTransactions: IgniteSemaphore

    /**
     * Instantiate an IgniteStoreClient
     *
     * @param jdbcConfFile adopt the configuration specified. If it is not specified, configuration
     * from ignite.xml is used
     */
    init {
        if (jdbcProperties != null) {
            // Given that systemPropertiesMode is set to 2 (SYSTEM_PROPERTIES_MODE_OVERRIDE) in
            // ignite.xml, we can override the properties through system properties
            for (propertyName in jdbcProperties.stringPropertyNames()) {
                require(propertyName.startsWith("jdbc.")) {
                    "Property `$propertyName` is invalid. Only properties starting with `jdbc.` are permitted."
                }
                System.setProperty(propertyName, jdbcProperties.getProperty(propertyName))
            }
        }

        listOf(
            "jdbc.url" to "MODELIX_JDBC_URL",
            "jdbc.user" to "MODELIX_JDBC_USER",
            "jdbc.pw" to "MODELIX_JDBC_PW",
            "jdbc.schema" to "MODELIX_JDBC_SCHEMA",
        ).forEach { (propName, envName) ->
            if (System.getProperty(propName).isNullOrEmpty()) {
                System.getenv(envName)?.let { System.setProperty(propName, it) }
            }
        }

        if (!inmemory) updateDatabaseSchema()

        // When running tests, Ignite complains about an already running instance, if we don't provide a unique name.
        val instanceName = if (inmemory) UUID.randomUUID().toString() else null
        ignite = IgnitionEx.start(javaClass.getResource(igniteConfigName), instanceName, null, null)
        cache = ignite.getOrCreateCache("model")
        ignite.message().localListen(ENTRY_CHANGED_TOPIC) { _: UUID?, key: Any? ->
            if (key is ObjectInRepository) {
                changeNotifier.notifyListeners(key)
            }
            true
        }

        globalReadTransactions = ignite.semaphore("global-reads", maxReadTransactions, true, true)
    }

    private fun updateDatabaseSchema() {
        SqlUtils(dataSource.connection).ensureSchemaInitialization()
    }

    @RequiresTransaction
    override fun getIfCached(key: ObjectInRepository): String? {
        localLocks.assertRead()
        return cache.localPeek(key, CachePeekMode.ONHEAP, CachePeekMode.OFFHEAP)
    }

    @RequiresTransaction
    override fun getAll(keys: Set<ObjectInRepository>): Map<ObjectInRepository, String?> {
        localLocks.assertRead()
        return cache.getAll(keys)
    }

    @RequiresTransaction
    override fun getAll(): Map<ObjectInRepository, String?> {
        localLocks.assertRead()
        return cache.associate { it.key to it.value }
    }

    @RequiresTransaction
    override fun removeRepositoryObjects(repositoryId: RepositoryId) {
        localLocks.assertWrite()
        if (!inmemory) {
            // Not all entries are in the cache. We delete them directly instead of loading them into the cache first.
            // This should be safe as the repository has already been removed from the list of available ones.
            removeRepositoryObjectsFromDatabase(repositoryId)
        }

        val filter = IgniteBiPredicate<ObjectInRepository, String?> { key, _ ->
            key.getRepositoryId() == repositoryId.id
        }
        val transformer = IgniteClosure<Cache.Entry<ObjectInRepository, String?>, ObjectInRepository?> { entry ->
            entry.key
        }
        val query = ScanQuery(filter)

        // sorting is necessary to avoid deadlocks, see documentation of IgniteCache::removeAllAsync
        val toDelete = cache.query(query, transformer).all.asSequence().filterNotNull().toSortedSet()
        LOG.info { "Deleting cache entries asynchronously. [numberOfEntries=${toDelete.size}]" }
        cache.removeAllAsync(toDelete).listen { LOG.info { "Cache entries deleted." } }
    }

    @RequiresTransaction
    override fun copyRepositoryObjects(
        source: RepositoryId,
        target: RepositoryId,
    ) {
        localLocks.assertWrite()
        require(!inmemory) { "Cannot copy in in-memory mode." }
        LOG.info { "Copying repository objects in database. [source=$source, target=$target]" }

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "INSERT INTO model (repository, key, value)" +
                    " SELECT ? AS repository, key, value" +
                    " FROM model" +
                    " WHERE repository = ?",
            ).use { stmt ->
                stmt.setString(1, target.id)
                stmt.setString(2, source.id)
                try {
                    val copiedRows = stmt.executeUpdate()
                    LOG.info { "Copied rows inside database. [copiedRows=$copiedRows]" }
                } catch (e: SQLException) {
                    LOG.error { e }
                }
            }
        }
    }

    private fun removeRepositoryObjectsFromDatabase(repositoryId: RepositoryId) {
        require(!inmemory) { "Cannot remove from database in in-memory mode." }
        LOG.info { "Removing repository objects from database." }

        dataSource.connection.use { connection ->
            connection.prepareStatement("DELETE from model WHERE repository = ?").use { stmt ->
                stmt.setString(1, repositoryId.id)
                try {
                    val deletedRows = stmt.executeUpdate()
                    LOG.info { "Deleted rows from database. [deletedRows=$deletedRows]" }
                } catch (e: SQLException) {
                    LOG.error { e }
                }
            }
        }
    }

    @RequiresTransaction
    override fun putAll(entries: Map<ObjectInRepository, String?>, silent: Boolean) {
        localLocks.assertWrite()

        // Sorting is important to avoid deadlocks (lock ordering).
        // The documentation of IgniteCache.putAll also states that this a requirement.
        val sortedEntries = entries.toSortedMap()
        val deletes = sortedEntries.asSequence().filter { it.value == null }.map { it.key }.toSet()
        val puts = sortedEntries.filterValues { it != null }
        if (deletes.isNotEmpty()) cache.removeAll(deletes)
        if (puts.isNotEmpty()) cache.putAll(puts)
        if (!silent) {
            for (key in sortedEntries.keys) {
                if (HashUtil.isSha256(key.key)) continue
                pendingChangeMessages.entryChanged(key)
            }
        }
    }

    override fun listen(key: ObjectInRepository, listener: IGenericKeyListener<ObjectInRepository>) {
        // Entries where the key is the SHA hash over the value are not expected to change and listening is unnecessary.
        require(!HashUtil.isSha256(key.key)) { "Listener for $key will never get notified." }

        changeNotifier.addListener(key, listener)
    }

    override fun removeListener(key: ObjectInRepository, listener: IGenericKeyListener<ObjectInRepository>) {
        changeNotifier.removeListener(key, listener)
    }

    override fun generateId(key: ObjectInRepository): Long {
        return cache.invoke(key, ClientIdProcessor())
    }

    override fun <T> runWrite(body: () -> T): T {
        return if (localLocks.canWrite()) {
            body()
        } else {
            localLocks.runWrite {
                // Acquiring `maxReadTransactions` number of permits, ensures that a:
                //  * write transaction is only started when no read transaction is currently running
                //  * no read transaction starts while a write transaction is running
                globalReadTransactions.acquireAndRun(maxReadTransactions) {
                    val transactions = ignite.transactions()
                    check(transactions.tx() == null) { "Already inside a transaction" }
                    // `OPTIMISTIC` and `READ_COMMITTED`
                    // are used instead of the default `PESSIMISTIC` and `REPEATABLE_READ`
                    // because they acquire fewer locks when writing.
                    // It is ok to acquire fewer locks,
                    // because the `globalReadTransactions`
                    // semaphore ensures no writes can happen in parallel.
                    transactions.txStart(TransactionConcurrency.OPTIMISTIC, TransactionIsolation.READ_COMMITTED).use { tx ->
                        pendingChangeMessages.runAndFlush {
                            val result = body()
                            tx.commit()
                            result
                        }
                    }
                }
            }
        }
    }

    override fun <T> runRead(body: () -> T): T {
        return if (localLocks.canRead()) {
            if (localLocks.canWrite()) {
                // downgrade
                localLocks.runRead(body)
            } else {
                body()
            }
        } else {
            localLocks.runRead {
                globalReadTransactions.acquireAndRun(1) {
                    // No ignite transaction necessary for read-only access
                    body()
                }
            }
        }
    }

    override fun canRead(): Boolean {
        return localLocks.canRead()
    }

    override fun canWrite(): Boolean {
        return localLocks.canWrite()
    }

    override fun getTransactionManager(): ITransactionManager {
        return this
    }

    override fun getImmutableStore(): IImmutableStore<ObjectInRepository> {
        return object : IImmutableStore<ObjectInRepository> {
            override fun getAll(keys: Set<ObjectInRepository>): Map<ObjectInRepository, String?> {
                keys.forEach { require(HashUtil.isSha256(it.key)) { "Not an immutable object: $it" } }
                return cache.getAll(keys)
            }

            override fun addAll(entries: Map<ObjectInRepository, String>) {
                entries.forEach {
                    require(HashUtil.isSha256(it.key.key)) { "Not an immutable object: $it" }
                    HashUtil.checkObjectHash(it.key.key, it.value)
                }
                cache.putAll(entries)
            }

            override fun getIfCached(key: ObjectInRepository): String? {
                require(HashUtil.isSha256(key.key)) { "Not an immutable object: $key" }
                return cache.localPeek(key, CachePeekMode.ONHEAP, CachePeekMode.OFFHEAP)
            }
        }
    }

    fun dispose() {
        ignite.close()
    }

    override fun close() {
        dispose()
    }
}

class PendingChangeMessages(private val notifier: (ObjectInRepository) -> Unit) {
    private val pendingChangeMessages = ContextValue<MutableSet<ObjectInRepository>>()

    fun <R> runAndFlush(body: () -> R): R {
        val messages = HashSet<ObjectInRepository>()
        return pendingChangeMessages.computeWith(messages) {
            val result = body()
            messages.forEach { notifier(it) }
            result
        }
    }

    fun entryChanged(key: ObjectInRepository) {
        val messages =
            checkNotNull(pendingChangeMessages.getValueOrNull()) { "Only allowed inside PendingChangeMessages.runAndFlush" }
        messages.add(key)
    }
}

class ChangeNotifier(val store: IsolatingStore) {
    private val changeNotifiers = HashMap<ObjectInRepository, EntryChangeNotifier>()

    @Synchronized
    fun notifyListeners(key: ObjectInRepository) {
        changeNotifiers[key]?.notifyIfChanged()
    }

    @Synchronized
    fun addListener(key: ObjectInRepository, listener: IGenericKeyListener<ObjectInRepository>) {
        changeNotifiers.getOrPut(key) { EntryChangeNotifier(key) }.listeners.add(listener)
    }

    @Synchronized
    fun removeListener(key: ObjectInRepository, listener: IGenericKeyListener<ObjectInRepository>) {
        val notifier = changeNotifiers[key] ?: return
        notifier.listeners.remove(listener)
        if (notifier.listeners.isEmpty()) {
            changeNotifiers.remove(key)
        }
    }

    private inner class EntryChangeNotifier(val key: ObjectInRepository) {
        val listeners = HashSet<IGenericKeyListener<ObjectInRepository>>()
        private var lastNotifiedValue: String? = null

        fun notifyIfChanged() {
            @OptIn(RequiresTransaction::class)
            val value = store.runReadTransaction { store.get(key) }
            if (value == lastNotifiedValue) return
            lastNotifiedValue = value

            for (listener in listeners) {
                try {
                    listener.changed(key, value)
                } catch (ex: Exception) {
                    LOG.error("Exception in listener of $key", ex)
                }
            }
        }
    }
}

private fun <R> IgniteSemaphore.acquireAndRun(permits: Int, body: () -> R): R {
    this.acquire(permits)
    try {
        return body()
    } finally {
        this.release(permits)
    }
}

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.modelix.model.server.store

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.ignite.Ignite
import org.apache.ignite.IgniteCache
import org.apache.ignite.Ignition
import org.apache.ignite.cache.query.ScanQuery
import org.apache.ignite.lang.IgniteBiPredicate
import org.apache.ignite.lang.IgniteClosure
import org.modelix.kotlin.utils.ContextValue
import org.modelix.model.IGenericKeyListener
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.persistent.HashUtil
import org.modelix.model.server.SqlUtils
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.sql.SQLException
import java.util.*
import javax.cache.Cache
import javax.sql.DataSource

private val LOG = KotlinLogging.logger { }

/**
 * Store client implementation with an ignite cache.
 * If [inmemory] is true, the data is not persisted in a database.
 */
class IgniteStoreClient(jdbcConfFile: File? = null, private val inmemory: Boolean = false) : IsolatingStore, AutoCloseable {

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

    /**
     * Instantiate an IgniteStoreClient
     *
     * @param jdbcConfFile adopt the configuration specified. If it is not specified, configuration
     * from ignite.xml is used
     */
    init {
        if (jdbcConfFile != null) {
            // Given that systemPropertiesMode is set to 2 (SYSTEM_PROPERTIES_MODE_OVERRIDE) in
            // ignite.xml, we can override the properties through system properties
            try {
                val properties = Properties()
                properties.load(FileReader(jdbcConfFile))
                for (pn in properties.stringPropertyNames()) {
                    if (pn.startsWith("jdbc.")) {
                        System.setProperty(pn, properties.getProperty(pn))
                    } else {
                        throw RuntimeException(
                            "Properties not related to jdbc are not permitted. Check file " +
                                jdbcConfFile.absolutePath,
                        )
                    }
                }
            } catch (e: IOException) {
                throw RuntimeException(
                    "We are unable to load the JDBC configuration from " +
                        jdbcConfFile.absolutePath,
                    e,
                )
            }
        }
        if (!inmemory) updateDatabaseSchema()
        ignite = Ignition.start(javaClass.getResource(igniteConfigName))
        cache = ignite.getOrCreateCache("model")

        ignite.message().localListen(ENTRY_CHANGED_TOPIC) { _: UUID?, key: Any? ->
            if (key is ObjectInRepository) {
                changeNotifier.notifyListeners(key)
            }
            true
        }
    }

    private fun updateDatabaseSchema() {
        SqlUtils(dataSource.connection).ensureSchemaInitialization()
    }

    override fun getAll(keys: Set<ObjectInRepository>): Map<ObjectInRepository, String?> {
        return cache.getAll(keys)
    }

    override fun getAll(): Map<ObjectInRepository, String?> {
        return cache.associate { it.key to it.value }
    }

    override fun removeRepositoryObjects(repositoryId: RepositoryId) {
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

    override fun putAll(entries: Map<ObjectInRepository, String?>, silent: Boolean) {
        // Sorting is important to avoid deadlocks (lock ordering).
        // The documentation of IgniteCache.putAll also states that this a requirement.
        val sortedEntries = entries.toSortedMap()
        val deletes = sortedEntries.asSequence().filter { it.value == null }.map { it.key }.toSet()
        val puts = sortedEntries.filterValues { it != null }
        runTransaction {
            if (deletes.isNotEmpty()) cache.removeAll(deletes)
            if (puts.isNotEmpty()) cache.putAll(puts)
            if (!silent) {
                for (key in sortedEntries.keys) {
                    if (HashUtil.isSha256(key.key)) continue
                    pendingChangeMessages.entryChanged(key)
                }
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

    override fun <T> runTransaction(body: () -> T): T {
        val transactions = ignite.transactions()
        if (transactions.tx() == null) {
            transactions.txStart().use { tx ->
                return pendingChangeMessages.runAndFlush {
                    val result = body()
                    tx.commit()
                    result
                }
            }
        } else {
            // already in a transaction
            return body()
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
            val value = store.get(key)
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

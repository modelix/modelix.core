package org.modelix.model.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.modelix.model.IKeyListener
import org.modelix.model.IKeyValueStore

abstract class VersionChangeDetector(
    private val store: IKeyValueStore,
    private val key: String,
    coroutineScope: CoroutineScope,
) {
    private val keyListener: IKeyListener
    var lastVersionHash: String? = null
        private set
    private var job: Job? = null

    @Synchronized
    private fun versionChanged(newVersion: String?) {
        if (newVersion == lastVersionHash) {
            return
        }
        try {
            processVersionChange(lastVersionHash, newVersion)
        } catch (ex: Exception) {
            LOG.error("", ex)
        }
        lastVersionHash = newVersion
    }

    protected abstract fun processVersionChange(oldVersion: String?, newVersion: String?)
    fun dispose() {
        job?.cancel("disposed")
        store.removeListener(key, keyListener)
    }

    companion object {
        private val LOG = mu.KotlinLogging.logger {}
    }

    init {
        keyListener = object : IKeyListener {
            override fun changed(key: String, value: String?) {
                LOG.debug { "Listener received new version $value" }
                versionChanged(value)
            }
        }

        job = coroutineScope.launch {
            store.listen(key, keyListener)
            while (isActive) {
                try {
                    val version = store.getA(key)
                    if (version != lastVersionHash) {
                        LOG.debug { "New version detected by polling: $version" }
                        versionChanged(version)
                    }
                } catch (e: Exception) {
                    LOG.warn("Failed to detect version change for $key: ${e.message}")
                }
                delay(3000)
            }
        }
    }
}

package org.modelix.model.client

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object SharedExecutors {
    private val LOG = mu.KotlinLogging.logger {}

    @JvmField
    val FIXED = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 1)
    val SCHEDULED = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() + 1)
    fun shutdownAll() {
        SCHEDULED.shutdown()
        FIXED.shutdown()
    }

    @JvmStatic
    fun fixDelay(periodMs: Long, r: Runnable): ScheduledFuture<*> {
        return fixDelay(periodMs, periodMs * 3, r)
    }

    @JvmStatic
    fun fixDelay(periodMs: Long, timeoutMs: Long, r: Runnable): ScheduledFuture<*> {
        val body = Runnable {
            try {
                r.run()
            } catch (ex: Exception) {
                LOG.error("", ex)
            }
        }

        var workerTask: Future<*>? = null
        return SCHEDULED.scheduleAtFixedRate(
            {
                if (workerTask == null || (workerTask?.isDone == true) || (workerTask?.isCancelled == true)) {
                    workerTask = FIXED.submit(body)
                    SCHEDULED.schedule(
                        {
                            workerTask?.cancel(true)
                        },
                        timeoutMs,
                        TimeUnit.MILLISECONDS,
                    )
                }
            },
            periodMs,
            periodMs,
            TimeUnit.MILLISECONDS,
        )
    }
}

package org.modelix.model.server.handlers

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.util.pipeline.PipelineContext
import org.modelix.model.server.handlers.KeyValueLikeModelServer.Companion.PROTECTED_PREFIX
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.StoreManager

class HealthApiImpl(
    private val repositoriesManager: RepositoriesManager,
) : HealthApi() {

    private val stores: StoreManager get() = repositoriesManager.stores

    override suspend fun PipelineContext<Unit, ApplicationCall>.getHealth() {
        if (isHealthy()) {
            call.respondText(text = "healthy", contentType = ContentType.Text.Plain, status = HttpStatusCode.OK)
        } else {
            throw HttpException(HttpStatusCode.InternalServerError, details = "not healthy")
        }
    }

    private fun isHealthy(): Boolean {
        val store = stores.getGlobalStoreClient()
        @OptIn(RequiresTransaction::class)
        return store.getTransactionManager().runWrite {
            val value = toLong(store[HEALTH_KEY]) + 1
            store.put(HEALTH_KEY, java.lang.Long.toString(value))
            toLong(store[HEALTH_KEY]) >= value
        }
    }

    private fun toLong(value: String?): Long {
        return if (value.isNullOrEmpty()) 0 else value.toLong()
    }

    companion object {
        internal const val HEALTH_KEY = PROTECTED_PREFIX + "health2"
    }
}

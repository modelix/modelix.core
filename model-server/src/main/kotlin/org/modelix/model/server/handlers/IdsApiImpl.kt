package org.modelix.model.server.handlers

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.origin
import io.ktor.server.response.respondText
import io.ktor.server.routing.routing
import io.ktor.util.pipeline.PipelineContext
import org.modelix.authorization.getUserName
import org.modelix.authorization.requiresLogin

/**
 * Implementation of the REST API that is responsible for handling client and server IDs.
 */
class IdsApiImpl(
    private val repositoriesManager: IRepositoriesManager,
) : IdsApi() {

    override suspend fun PipelineContext<Unit, ApplicationCall>.getServerId() {
        // Currently, the server ID is initialized in KeyValueLikeModelServer eagerly on startup.
        // Should KeyValueLikeModelServer be removed or change,
        // RepositoriesManager#maybeInitAndGetSeverId will initialize the server ID lazily on the first request.
        //
        // Functionally, it does not matter if the server ID is created eagerly or lazily,
        // as long as the same server ID is returned from the same server.
        val serverId = repositoriesManager.maybeInitAndGetSeverId()
        call.respondText(serverId)
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.getUserId() {
        call.respondText(call.getUserName() ?: call.request.origin.remoteHost)
    }

    override suspend fun PipelineContext<Unit, ApplicationCall>.generateClientId() {
        call.respondText(repositoriesManager.getStoreManager().getGlobalStoreClient().generateId("clientId").toString())
    }

    fun init(application: Application) {
        application.routing {
            requiresLogin {
                installRoutes(this)
            }
        }
    }
}

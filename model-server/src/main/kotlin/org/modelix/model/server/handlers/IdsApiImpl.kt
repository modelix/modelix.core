/*
 * Copyright (c) 2024.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import org.modelix.model.server.store.IStoreClient
import org.modelix.model.server.store.LocalModelClient

/**
 * Implementation of the REST API that is responsible for handling client and server IDs.
 */
class IdsApiImpl(
    private val repositoriesManager: IRepositoriesManager,
    private val modelClient: LocalModelClient,
) : IdsApi() {

    constructor(modelClient: LocalModelClient) : this(RepositoriesManager(modelClient), modelClient)
    constructor(storeClient: IStoreClient) : this(LocalModelClient(storeClient))
    constructor(repositoriesManager: RepositoriesManager) : this(repositoriesManager, repositoriesManager.client)

    private val storeClient: IStoreClient get() = modelClient.store

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
        call.respondText(storeClient.generateId("clientId").toString())
    }

    fun init(application: Application) {
        application.routing {
            requiresLogin {
                installRoutes(this)
            }
        }
    }
}

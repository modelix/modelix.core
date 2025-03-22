package org.modelix.model.server.handlers

import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.server.store.RequiresTransaction
import org.modelix.model.server.store.runReadIO
import org.modelix.model.server.store.runWriteIO

class LionwebApiImpl(val repoManager: IRepositoriesManager) : LionwebApi() {

    override suspend fun RoutingContext.bulkDelete(
        partition: String,
        nodes: List<String>,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.bulkRetrieve(
        partition: String,
        nodes: List<String>,
        depthLimit: Int?,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.bulkStore(
        partition: String,
        lionwebSerializationChunk: LionwebSerializationChunk,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.createPartitions(lionwebSerializationChunk: LionwebSerializationChunk) {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.deletePartitions(lionwebSerializationChunk: LionwebSerializationChunk) {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.getIds(count: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.listPartitions() {
        TODO("Not yet implemented")
    }

    override suspend fun RoutingContext.listRepositories() {
        @OptIn(RequiresTransaction::class)
        call.respond(
            runRead {
                ListRepositories200Response(
                    success = true,
                    messages = emptyList(),
                    repositories = repoManager.getRepositories().map { repo ->
                        LionwebRepositoryConfiguration(
                            name = repo.id,
                            lionwebVersion = "2024.1",
                            history = true,
                        )
                    },
                )
            },
        )
    }

    override suspend fun RoutingContext.createRepository(
        repository: String,
        history: Boolean?,
        lionWebVersion: String?,
    ) {
        @OptIn(RequiresTransaction::class)
        runWrite {
            repoManager.createRepository(
                RepositoryId(repository),
                userName = "lionweb@modelix.org",
            )
        }
        call.respond(LionwebResponse(success = true, messages = emptyList()))
    }

    private suspend fun <R> runRead(body: () -> R): R {
        return repoManager.getTransactionManager().runReadIO(body)
    }

    private suspend fun <R> runWrite(body: () -> R): R {
        return repoManager.getTransactionManager().runWriteIO(body)
    }
}

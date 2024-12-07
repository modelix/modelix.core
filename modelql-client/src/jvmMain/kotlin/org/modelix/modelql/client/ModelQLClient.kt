package org.modelix.modelql.client

import kotlinx.coroutines.runBlocking
import org.modelix.model.api.INode
import org.modelix.model.api.RoleAccessContext
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IUnboundQuery

actual fun <ResultT> ModelQLClient.blockingQuery(body: (IMonoStep<INode>) -> IMonoStep<ResultT>): ResultT {
    val query = RoleAccessContext.runWith(true) { IUnboundQuery.buildMono(body) }
    return runBlocking {
        runQuery(query)
    }
}

package org.modelix.modelql.client

import kotlinx.coroutines.runBlocking
import org.modelix.model.api.INode
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IUnboundQuery

actual fun <ResultT> ModelQLClient.blockingQuery(body: (IMonoStep<INode>) -> IMonoStep<ResultT>): ResultT {
    return runBlocking {
        runQuery(IUnboundQuery.buildMono(body))
    }
}

package org.modelix.modelql.client

import kotlinx.coroutines.runBlocking
import org.modelix.model.api.INode
import org.modelix.modelql.core.IMonoStep

actual fun <ResultT> ModelQLClient.blockingQuery(body: (IMonoStep<INode>) -> IMonoStep<ResultT>): ResultT {
    return runBlocking { buildQuery(body).execute() }
}

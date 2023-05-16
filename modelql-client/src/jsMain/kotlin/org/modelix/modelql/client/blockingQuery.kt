package org.modelix.modelql.client

import org.modelix.model.api.INode
import org.modelix.modelql.core.IMonoStep

actual fun <ResultT> ModelQLClient.blockingQuery(body: (IMonoStep<INode>) -> IMonoStep<ResultT>): ResultT {
    throw UnsupportedOperationException("Only supported on JVM")
}
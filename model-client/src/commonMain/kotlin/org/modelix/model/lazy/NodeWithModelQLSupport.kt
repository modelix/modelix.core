package org.modelix.model.lazy

import kotlinx.coroutines.flow.single
import org.modelix.model.api.*
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.Query
import org.modelix.modelql.untyped.ISupportsModelQL

class NodeWithModelQLSupport(val node: INode) : INode by node, ISupportsModelQL {
    override suspend fun <R> query(body: (IMonoStep<INode>) -> IMonoStep<R>): R {
        return (node.deepUnwrap() as PNodeAdapter).branch.query(body)
    }
}

suspend fun <R> IBranch.query(body: (IMonoStep<INode>) -> IMonoStep<R>): R {
    val tree = (this.deepUnwrap() as PBranch).computeReadT { t -> (t.tree.unwrap() as CLTree) }
    val rootNode = getRootNode()
    val result = IBulkQuery2.buildBulkFlow<R>(tree.store) {
        val query = Query.build(body)
        query.applyQuery(rootNode)
    }.single()
    return result
}

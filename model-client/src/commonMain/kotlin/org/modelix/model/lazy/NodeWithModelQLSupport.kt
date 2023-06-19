package org.modelix.model.lazy

import kotlinx.coroutines.flow.single
import org.modelix.model.api.*
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IQuery
import org.modelix.modelql.core.IUnboundQuery
import org.modelix.modelql.core.UnboundQuery
import org.modelix.modelql.core.map
import org.modelix.modelql.untyped.ISupportsModelQL

class NodeWithModelQLSupport(val node: INode) : INode by node, ISupportsModelQL {
    override fun <R> buildQuery(body: (IMonoStep<INode>) -> IMonoStep<R>): IQuery<R> {
        return (node.deepUnwrap() as PNodeAdapter).branch.buildQuery(body)
    }
}

fun <R> IBranch.buildQuery(body: (IMonoStep<INode>) -> IMonoStep<R>): IQuery<R> {
    return BranchQuery<R>(this, UnboundQuery.build(body))
}

private class BranchQuery<E>(val branch: IBranch, val query: IUnboundQuery<INode, E>) : IQuery<E> {
    override suspend fun execute(): E {
        val tree = (branch.deepUnwrap() as PBranch).computeReadT { t -> (t.tree.unwrap() as CLTree) }
        val rootNode = branch.getRootNode()
        val result = IBulkQuery2.buildBulkFlow<E>(tree.store) {
            query.applyQuery(rootNode)
        }.single()
        return result
    }

    override fun <T> map(body: (IMonoStep<E>) -> IMonoStep<T>): IQuery<T> {
        return BranchQuery(branch, UnboundQuery.build { it.map(query).map(body) })
    }
}

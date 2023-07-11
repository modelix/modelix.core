package org.modelix.model.lazy

import org.modelix.model.api.*
import org.modelix.modelql.core.IFluxQuery
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoQuery
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IQueryExecutor
import org.modelix.modelql.core.IUnboundQuery
import org.modelix.modelql.core.StepFlow
import org.modelix.modelql.core.asStepFlow
import org.modelix.modelql.core.value
import org.modelix.modelql.untyped.ISupportsModelQL

class NodeWithModelQLSupport(val node: INode) : INode by node, ISupportsModelQL {
    override fun createQueryExecutor(): IQueryExecutor<INode> {
        return (node.deepUnwrap() as PNodeAdapter).branch.createQueryExecutor()
    }
}

fun IBranch.usesRoleIds() = computeReadT { it.tree.usesRoleIds() }

fun <R> IBranch.buildQuery(body: (IMonoStep<INode>) -> IMonoStep<R>): IMonoQuery<R> {
    return RoleAccessContext.runWith(usesRoleIds()) {
        IUnboundQuery.buildMono(body).bind(BranchQueryExecutor(this))
    }
}

fun <R> IBranch.buildQuery(body: (IMonoStep<INode>) -> IFluxStep<R>): IFluxQuery<R> {
    return RoleAccessContext.runWith(usesRoleIds()) {
        IUnboundQuery.buildFlux(body).bind(BranchQueryExecutor(this))
    }
}

class BranchQueryExecutor(val branch: IBranch) : IQueryExecutor<INode> {
    override fun <Out> createFlow(query: IUnboundQuery<INode, *, Out>): StepFlow<Out> {
        val tree = (branch.deepUnwrap() as PBranch).computeReadT { t -> (t.tree.unwrap() as CLTree) }
        val rootNode = branch.getRootNode()
        return IBulkQuery2.buildBulkFlow<Out>(tree.store) {
            query.asFlow(rootNode).value
        }.asStepFlow()
    }
}

fun IBranch.createQueryExecutor() = BranchQueryExecutor(this)

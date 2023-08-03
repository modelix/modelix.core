/*
 * Copyright 2003-2023 JetBrains s.r.o.
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
/*
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
package org.modelix.model.lazy

import org.modelix.model.api.*
import org.modelix.modelql.core.IFluxQuery
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoQuery
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IQueryExecutor
import org.modelix.modelql.core.IUnboundQuery
import org.modelix.modelql.core.QueryEvaluationContext
import org.modelix.modelql.core.StepFlow
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
        return IBulkQuery2.buildBulkFlow(tree.store) {
            query.asFlow(QueryEvaluationContext.EMPTY, rootNode)
        }
    }
}

fun IBranch.createQueryExecutor() = BranchQueryExecutor(this)

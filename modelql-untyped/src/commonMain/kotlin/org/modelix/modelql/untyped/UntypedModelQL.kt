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
package org.modelix.modelql.untyped

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.modelix.model.api.INode
import org.modelix.model.api.RoleAccessContext
import org.modelix.modelql.core.IFluxQuery
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoQuery
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IQueryExecutor
import org.modelix.modelql.core.SimpleQueryExecutor
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.UnboundQuery
import org.modelix.modelql.core.buildMonoQuery

object UntypedModelQL {
    val serializersModule: SerializersModule = SerializersModule {
        include(UnboundQuery.serializersModule)
        polymorphic(StepDescriptor::class) {
            subclass(AddNewChildNodeStep.Descriptor::class)
            subclass(AllChildrenTraversalStep.AllChildrenStepDescriptor::class)
            subclass(AllReferencesTraversalStep.Descriptor::class)
            subclass(ChildrenTraversalStep.ChildrenStepDescriptor::class)
            subclass(ConceptReferenceTraversalStep.Descriptor::class)
            subclass(ConceptReferenceUIDTraversalStep.Descriptor::class)
            subclass(DescendantsTraversalStep.WithSelfDescriptor::class)
            subclass(DescendantsTraversalStep.WithoutSelfDescriptor::class)
            subclass(MoveNodeStep.Descriptor::class)
            subclass(NodeReferenceAsStringTraversalStep.Descriptor::class)
            subclass(NodeReferenceSourceStep.Descriptor::class)
            subclass(NodeReferenceTraversalStep.Descriptor::class)
            subclass(OfConceptStep.Descriptor::class)
            subclass(ParentTraversalStep.Descriptor::class)
            subclass(PropertyTraversalStep.PropertyStepDescriptor::class)
            subclass(ReferenceTraversalStep.Descriptor::class)
            subclass(RemoveNodeStep.Descriptor::class)
            subclass(ResolveNodeStep.Descriptor::class)
            subclass(RoleInParentTraversalStep.Descriptor::class)
            subclass(SetPropertyStep.Descriptor::class)
            subclass(SetReferenceStep.Descriptor::class)
        }
        polymorphicDefaultSerializer(INode::class) { NodeKSerializer() }
    }
    val json = Json {
        serializersModule = UntypedModelQL.serializersModule
    }
}

interface ISupportsModelQL : INode {
    fun createQueryExecutor(): IQueryExecutor<INode>
}

fun INode.createQueryExecutor(): IQueryExecutor<INode> {
    return when (this) {
        is ISupportsModelQL -> this.createQueryExecutor()
        else -> SimpleQueryExecutor(this)
    }
}

suspend fun <R> INode.query(body: (IMonoStep<INode>) -> IMonoStep<R>): R {
    return buildQuery(body).execute().value
}

suspend fun <R> INode.queryFlux(body: (IMonoStep<INode>) -> IFluxStep<R>): List<R> {
    return buildFluxQuery(body).execute().value.map { it.value }
}

fun <R> INode.buildQuery(body: (IMonoStep<INode>) -> IMonoStep<R>): IMonoQuery<R> {
    return RoleAccessContext.runWith(usesRoleIds()) { org.modelix.modelql.core.buildMonoQuery { body(it) }.bind(createQueryExecutor()) }
}

fun <R> INode.buildFluxQuery(body: (IMonoStep<INode>) -> IFluxStep<R>): IFluxQuery<R> {
    return RoleAccessContext.runWith(usesRoleIds()) { org.modelix.modelql.core.buildFluxQuery { body(it) }.bind(createQueryExecutor()) }
}

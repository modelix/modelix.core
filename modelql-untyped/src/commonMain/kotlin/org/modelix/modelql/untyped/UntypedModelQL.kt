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
import org.modelix.modelql.core.BoundQuery
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IQuery
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.UnboundQuery

object UntypedModelQL {
    val serializersModule: SerializersModule = SerializersModule {
        include(UnboundQuery.serializersModule)
        polymorphic(StepDescriptor::class) {
            subclass(AllChildrenTraversalStep.AllChildrenStepDescriptor::class)
            subclass(ChildrenTraversalStep.ChildrenStepDescriptor::class)
            subclass(ConceptReferenceTraversalStep.Descriptor::class)
            subclass(ConceptReferenceUIDTraversalStep.Descriptor::class)
            subclass(DescendantsTraversalStep.WithSelfDescriptor::class)
            subclass(DescendantsTraversalStep.WithoutSelfDescriptor::class)
            subclass(NodeReferenceAsStringTraversalStep.Descriptor::class)
            subclass(NodeReferenceSourceStep.Descriptor::class)
            subclass(NodeReferenceTraversalStep.Descriptor::class)
            subclass(ParentTraversalStep.Descriptor::class)
            subclass(PropertyTraversalStep.PropertyStepDescriptor::class)
            subclass(PropertyTraversalStep.PropertyStepDescriptor::class)
            subclass(ReferenceTraversalStep.Descriptor::class)
            subclass(ResolveNodeStep.Descriptor::class)
            subclass(RoleInParentTraversalStep.Descriptor::class)
            subclass(SetPropertyStep.Descriptor::class)
        }
        polymorphicDefaultSerializer(INode::class) { NodeKSerializer() }
    }
    val json = Json {
        serializersModule = UntypedModelQL.serializersModule
    }
}

interface ISupportsModelQL : INode {
    fun <R> buildQuery(body: (IMonoStep<INode>) -> IMonoStep<R>): IQuery<R>
}

suspend fun <R> INode.query(body: (IMonoStep<INode>) -> IMonoStep<R>): R {
    return buildQuery(body).execute()
}

fun <R> INode.buildQuery(body: (IMonoStep<INode>) -> IMonoStep<R>): IQuery<R> {
    return when (this) {
        is ISupportsModelQL -> this.buildQuery(body)
        else -> BoundQuery.build(this, body)
    }
}

package org.modelix.modelql.modelapi

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.modelix.model.api.INode
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.Query
import org.modelix.modelql.core.StepDescriptor

object UntypedModelQL {
    val serializersModule: SerializersModule = SerializersModule {
        include(Query.serializersModule)
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
        }
        polymorphicDefaultSerializer(INode::class) { NodeKSerializer() }
    }
    val json = Json {
        serializersModule = UntypedModelQL.serializersModule
    }
}

interface ISupportsModelQL : INode {
    suspend fun <R> query(body: (IMonoStep<INode>) -> IMonoStep<R>): R
}

suspend fun <R> INode.query(body: (IMonoStep<INode>) -> IMonoStep<R>): R {
    return when (this) {
        is ISupportsModelQL -> this.query(body)
        else -> Query.build(body).run(this)
    }
}

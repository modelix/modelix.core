package org.modelix.modelql.untyped

import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import org.modelix.model.api.INode
import org.modelix.model.api.remove
import org.modelix.modelql.core.AggregationStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IProducingStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.StepFlow
import org.modelix.modelql.core.asStepOutput
import org.modelix.modelql.core.connect
import org.modelix.modelql.core.stepOutputSerializer

class RemoveNodeStep() : AggregationStep<INode, Int>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Int>> {
        return serializersModule.serializer<Int>().stepOutputSerializer(this)
    }

    override suspend fun aggregate(input: StepFlow<INode>): IStepOutput<Int> {
        return input.map { it.value.remove() }.count().asStepOutput(this)
    }

    override fun aggregate(input: Sequence<IStepOutput<INode>>): IStepOutput<Int> {
        return input.map { it.value.remove() }.count().asStepOutput(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}.remove()"
    }

    @Serializable
    @SerialName("untyped.remove")
    class Descriptor() : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return RemoveNodeStep()
        }
    }
}

fun IProducingStep<INode>.remove(): IMonoStep<Int> {
    return RemoveNodeStep().also { connect(it) }
}

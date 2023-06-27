package org.modelix.modelql.untyped

import kotlinx.coroutines.flow.Flow
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
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.connect

class RemoveNodeStep() : AggregationStep<INode, Int>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out Int> {
        return serializersModule.serializer<Int>()
    }

    override suspend fun aggregate(input: Flow<INode>): Int {
        return input.map { it.remove() }.count()
    }

    override fun aggregate(input: Sequence<INode>): Int {
        return input.map { it.remove() }.count()
    }

    override fun createDescriptor(): StepDescriptor {
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
        override fun createStep(): IStep {
            return RemoveNodeStep()
        }
    }
}

fun IProducingStep<INode>.remove(): IMonoStep<Int> {
    return RemoveNodeStep().also { connect(it) }
}

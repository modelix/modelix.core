package org.modelix.modelql.untyped

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.key
import org.modelix.model.api.resolvePropertyOrFallback
import org.modelix.modelql.core.IFlowInstantiationContext
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.MonoTransformingStep
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.connect

class SetPropertyStep(val role: String, val value: String?) : MonoTransformingStep<INode, INode>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out INode> {
        return getProducer().getOutputSerializer(serializersModule)
    }

    override fun createFlow(input: Flow<INode>, context: IFlowInstantiationContext): Flow<INode> {
        return input.map {
            it.setPropertyValue(it.resolvePropertyOrFallback(role), value)
            it
        }
    }

    override fun createDescriptor(): StepDescriptor {
        return Descriptor(role, value)
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}.setProperty($role, $value)"
    }

    @Serializable
    class Descriptor(val role: String, val value: String?) : StepDescriptor() {
        override fun createStep(): IStep {
            return SetPropertyStep(role, value)
        }
    }
}

fun IMonoStep<INode>.setProperty(role: String, value: String?): IMonoStep<INode> {
    return SetPropertyStep(role, value).also { connect(it) }
}
fun IMonoStep<INode>.setProperty(role: IProperty, value: String?): IMonoStep<INode> {
    return SetPropertyStep(role.key(), value).also { connect(it) }
}

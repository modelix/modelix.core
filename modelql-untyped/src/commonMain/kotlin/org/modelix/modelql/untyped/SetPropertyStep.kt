package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.modelix.model.api.INode
import org.modelix.model.api.IProperty
import org.modelix.model.api.key
import org.modelix.model.api.resolvePropertyOrFallback
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.asMono
import org.modelix.modelql.core.connect

class SetPropertyStep(val role: String) :
    TransformingStepWithParameter<INode, String?, Any?, INode>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<INode>> {
        return getInputProducer().getOutputSerializer(serializersModule)
    }

    override fun transformElement(input: INode, parameter: String?): INode {
        input.setPropertyValue(input.resolvePropertyOrFallback(role), parameter)
        return input
    }

    override fun createDescriptor(): StepDescriptor {
        return Descriptor(role)
    }

    override fun requiresWriteAccess(): Boolean {
        return true
    }

    override fun toString(): String {
        return "${getProducer()}.setProperty($role, ${getParameterProducer()})"
    }

    @Serializable
    @SerialName("untyped.setProperty")
    class Descriptor(val role: String) : StepDescriptor() {
        override fun createStep(): IStep {
            return SetPropertyStep(role)
        }
    }
}

fun IMonoStep<INode>.setProperty(role: String, value: String?): IMonoStep<INode> {
    return setProperty(role, value.asMono())
}
fun IMonoStep<INode>.setProperty(role: String, value: IMonoStep<String?>): IMonoStep<INode> {
    return SetPropertyStep(role).also {
        connect(it)
        value.connect(it)
    }
}
fun IMonoStep<INode>.setProperty(role: IProperty, value: String?): IMonoStep<INode> {
    return setProperty(role, value.asMono())
}
fun IMonoStep<INode>.setProperty(role: IProperty, value: IMonoStep<String?>): IMonoStep<INode> {
    return setProperty(role.key(), value)
}

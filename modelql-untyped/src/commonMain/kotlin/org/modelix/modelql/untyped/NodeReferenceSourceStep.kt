package org.modelix.modelql.untyped

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.nullable
import org.modelix.model.api.INodeReference
import org.modelix.model.api.SerializedNodeReference
import org.modelix.modelql.core.ConstantSourceStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.IdReassignments
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.SerializationContext
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.stepOutputSerializer
import kotlin.jvm.JvmName
import kotlin.reflect.typeOf

class NodeReferenceSourceStep(element: INodeReference?) : ConstantSourceStep<INodeReference?>(element, typeOf<INodeReference?>()) {
    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<INodeReference?>> {
        return serializationContext.serializer<INodeReference>().nullable.stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(element)

    @Serializable
    @SerialName("nodeReferenceMonoSource")
    data class Descriptor(val element: INodeReference?) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return NodeReferenceSourceStep(element)
        }

        override fun doNormalize(idReassignments: IdReassignments): StepDescriptor = Descriptor(element)
    }

    override fun toString(): String {
        return "<${element?.serialize()}>"
    }

    override fun canEvaluateStatically(): Boolean {
        return true
    }

    override fun evaluateStatically(): INodeReference? {
        return element
    }
}

@JvmName("asMonoNullable")
fun INodeReference?.asMono(): IMonoStep<INodeReference?> {
    return NodeReferenceSourceStep(this?.serialize()?.let { SerializedNodeReference(it) })
}
fun INodeReference.asMono(): IMonoStep<INodeReference> {
    return NodeReferenceSourceStep(SerializedNodeReference(serialize())) as IMonoStep<INodeReference>
}

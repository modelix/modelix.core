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

import kotlinx.coroutines.flow.filter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.getAllSubConcepts
import org.modelix.modelql.core.IFlowInstantiationContext
import org.modelix.modelql.core.IFluxStep
import org.modelix.modelql.core.IMonoStep
import org.modelix.modelql.core.IStep
import org.modelix.modelql.core.IStepOutput
import org.modelix.modelql.core.MonoTransformingStep
import org.modelix.modelql.core.QueryDeserializationContext
import org.modelix.modelql.core.QueryEvaluationContext
import org.modelix.modelql.core.QueryGraphDescriptorBuilder
import org.modelix.modelql.core.StepDescriptor
import org.modelix.modelql.core.StepFlow

class OfConceptStep(val conceptUIDs: Set<String>) : MonoTransformingStep<INode?, INode>() {
    override fun createFlow(input: StepFlow<INode?>, context: IFlowInstantiationContext): StepFlow<INode> {
        return input.filter {
            val value = it.value
            value != null && conceptUIDs.contains(value.concept?.getUID())
        } as StepFlow<INode>
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<INode>> {
        return getProducer().getOutputSerializer(serializersModule) as KSerializer<out IStepOutput<INode>>
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: INode?): INode {
        require(input != null) { "node is null" }
        require(conceptUIDs.contains(input.concept?.getUID())) { "$input is not an instance of $conceptUIDs" }
        return input
    }

    override fun createTransformingSequence(evaluationContext: QueryEvaluationContext, input: Sequence<INode?>): Sequence<INode> {
        return input.filterNotNull().filter { conceptUIDs.contains(it.concept?.getUID()) }
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(conceptUIDs)

    @Serializable
    @SerialName("untyped.ofConcept")
    class Descriptor(val conceptUIDs: Set<String>) : StepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return OfConceptStep(conceptUIDs)
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.ofConcept($conceptUIDs)"""
    }
}

fun IMonoStep<INode?>.ofConcept(concept: IConcept): IMonoStep<INode> {
    val subconceptUIDs = concept.getAllSubConcepts(true).map { it.getReference().getUID() }.toSet()
    return OfConceptStep(subconceptUIDs).connectAndDowncast(this)
}
fun IFluxStep<INode?>.ofConcept(concept: IConcept): IFluxStep<INode> {
    val subconceptUIDs = concept.getAllSubConcepts(true).map { it.getReference().getUID() }.toSet()
    return OfConceptStep(subconceptUIDs).connectAndDowncast(this)
}

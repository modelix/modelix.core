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
package org.modelix.modelql.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

class IsNullPredicateStep<In>() : SimpleMonoTransformingStep<In, Boolean>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: In): Boolean {
        return input == null
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Boolean>> {
        return serializationContext.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("isNull")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return IsNullPredicateStep<Any?>()
        }
    }

    override fun toString(): String {
        return """${getProducers().single()}.isNull()"""
    }
}

fun <T> IMonoStep<T>.isNull(): IMonoStep<Boolean> = IsNullPredicateStep<T>().also { connect(it) }
fun <T : Any> IMonoStep<T?>.filterNotNull(): IMonoStep<T> = filter { !it.isNull() } as IMonoStep<T>
fun <T : Any> IFluxStep<T?>.filterNotNull(): IFluxStep<T> = filter { !it.isNull() } as IFluxStep<T>

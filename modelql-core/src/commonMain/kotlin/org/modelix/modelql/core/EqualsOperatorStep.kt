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

class EqualsOperatorStep<E>() : TransformingStepWithParameter<E, E, E, Boolean>() {

    override fun transformElement(input: IStepOutput<E>, parameter: IStepOutput<E>?): IStepOutput<Boolean> {
        return (input.value == parameter?.value).asStepOutput(this)
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Boolean>> {
        return serializationContext.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun toString(): String {
        return "${getInputProducer()}.equalTo(${getParameterProducer()})"
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor()

    @Serializable
    @SerialName("equalTo")
    class Descriptor() : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return EqualsOperatorStep<Any?>()
        }
    }
}

fun <T> IMonoStep<T>.notEqualTo(operand: IMonoStep<T>): IMonoStep<Boolean> = !equalTo(operand)
fun <T> IMonoStep<T>.equalTo(operand: IMonoStep<T>): IMonoStep<Boolean> = EqualsOperatorStep<T>().also {
    connect(it)
    operand.connect(it)
}

fun IMonoStep<Boolean?>.equalTo(operand: Boolean?) = equalTo(operand.asMono())
fun IMonoStep<Boolean?>.notEqualTo(operand: Boolean?) = !equalTo(operand)

fun IMonoStep<Byte?>.equalTo(operand: Byte?) = equalTo(operand.asMono())
fun IMonoStep<Byte?>.notEqualTo(operand: Byte?) = !equalTo(operand)

fun IMonoStep<Char?>.equalTo(operand: Char?) = equalTo(operand.asMono())
fun IMonoStep<Char?>.notEqualTo(operand: Char?) = !equalTo(operand)

fun IMonoStep<Short?>.equalTo(operand: Short?) = equalTo(operand.asMono())
fun IMonoStep<Short?>.notEqualTo(operand: Short?) = !equalTo(operand)

fun IMonoStep<Int?>.equalTo(operand: Int?) = equalTo(operand.asMono())
fun IMonoStep<Int?>.notEqualTo(operand: Int?) = !equalTo(operand)

fun IMonoStep<Long?>.equalTo(operand: Long?) = equalTo(operand.asMono())
fun IMonoStep<Long?>.notEqualTo(operand: Long?) = !equalTo(operand)

fun IMonoStep<Float?>.equalTo(operand: Float?) = equalTo(operand.asMono())
fun IMonoStep<Float?>.notEqualTo(operand: Float?) = !equalTo(operand)

fun IMonoStep<Double?>.equalTo(operand: Double?) = equalTo(operand.asMono())
fun IMonoStep<Double?>.notEqualTo(operand: Double?) = !equalTo(operand)

fun IMonoStep<String?>.equalTo(operand: String) = equalTo(operand.asMono())
fun IMonoStep<String?>.notEqualTo(operand: String) = !equalTo(operand)

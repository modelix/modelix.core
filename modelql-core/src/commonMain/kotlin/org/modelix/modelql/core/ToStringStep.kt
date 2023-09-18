/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName

class ToStringStep : MonoTransformingStep<Any?, String?>() {
    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<String?>> {
        return serializersModule.serializer<String>().nullable.stepOutputSerializer(this)
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: Any?): String? {
        return input?.toString()
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor()
    }

    @Serializable
    @SerialName("toString")
    class Descriptor : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ToStringStep()
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.asString()"
    }
}

@JvmName("asStringNullable")
fun IMonoStep<Any?>.asString(): IMonoStep<String?> = ToStringStep().connectAndDowncast(this)
fun IMonoStep<Any>.asString(): IMonoStep<String> = ToStringStep().connectAndDowncast(this) as IMonoStep<String>

@JvmName("asStringNullable")
fun IFluxStep<Any?>.asString(): IFluxStep<String?> = ToStringStep().connectAndDowncast(this)
fun IFluxStep<Any>.asString(): IFluxStep<String> = ToStringStep().connectAndDowncast(this) as IFluxStep<String>

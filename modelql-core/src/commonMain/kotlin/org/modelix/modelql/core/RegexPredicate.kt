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
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer

class RegexPredicate(val regex: Regex) : MonoTransformingStep<String?, Boolean>() {

    override fun transform(evaluationContext: QueryEvaluationContext, input: String?): Boolean {
        return input?.matches(regex) ?: false
    }

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Boolean>> {
        return serializersModule.serializer<Boolean>().stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder) = Descriptor(regex.pattern)

    @Serializable
    @SerialName("regex")
    class Descriptor(val pattern: String) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return RegexPredicate(Regex(pattern))
        }
    }

    override fun toString(): String {
        return "${getProducers().single()}.matches(/${regex.pattern}/)"
    }
}

fun IMonoStep<String?>.matches(regex: Regex): IMonoStep<Boolean> = RegexPredicate(regex).also { connect(it) }

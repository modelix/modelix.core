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
import kotlinx.serialization.modules.SerializersModule

class ZipElementAccessStep<Out>(val index: Int) : MonoTransformingStep<IZipOutput<Any?>, Out>() {

    override fun getOutputSerializer(serializersModule: SerializersModule): KSerializer<out IStepOutput<Out>> {
        val zipSerializer = getProducers().single().getOutputSerializer(serializersModule) as ZipOutputSerializer<Out, *>
        return zipSerializer.elementSerializers[index]
    }

    override fun transform(
        evaluationContext: QueryEvaluationContext,
        input: IStepOutput<IZipOutput<Any?>>,
    ): IStepOutput<Out> {
        return (input as ZipStepOutput<*, *>).values[index] as IStepOutput<Out>
    }

    override fun transform(evaluationContext: QueryEvaluationContext, input: IZipOutput<Any?>): Out {
        return input.values[index] as Out
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor {
        return Descriptor(index)
    }

    @Serializable
    @SerialName("zip.output.access")
    class Descriptor(val index: Int) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ZipElementAccessStep<Any?>(index)
        }
    }

    override fun toString(): String {
        return """${getProducers().firstOrNull()}[$index]"""
    }
}

val <T> IMonoStep<IZip1Output<*, T>>.first: IMonoStep<T>
    get() = ZipElementAccessStep<T>(0).also { connect(it) }
val <T> IMonoStep<IZip2Output<*, *, T>>.second: IMonoStep<T>
    get() = ZipElementAccessStep<T>(1).also { connect(it) }
val <T> IMonoStep<IZip3Output<*, *, *, T>>.third: IMonoStep<T>
    get() = ZipElementAccessStep<T>(2).also { connect(it) }
val <T> IMonoStep<IZip4Output<*, *, *, *, T>>.forth: IMonoStep<T>
    get() = ZipElementAccessStep<T>(3).also { connect(it) }
val <T> IMonoStep<IZip5Output<*, *, *, *, *, T>>.fifth: IMonoStep<T>
    get() = ZipElementAccessStep<T>(4).also { connect(it) }
val <T> IMonoStep<IZip6Output<*, *, *, *, *, *, T>>.sixth: IMonoStep<T>
    get() = ZipElementAccessStep<T>(5).also { connect(it) }
val <T> IMonoStep<IZip7Output<*, *, *, *, *, *, *, T>>.seventh: IMonoStep<T>
    get() = ZipElementAccessStep<T>(6).also { connect(it) }
val <T> IMonoStep<IZip8Output<*, *, *, *, *, *, *, *, T>>.eighth: IMonoStep<T>
    get() = ZipElementAccessStep<T>(7).also { connect(it) }
val <T> IMonoStep<IZip9Output<*, *, *, *, *, *, *, *, *, T>>.ninth: IMonoStep<T>
    get() = ZipElementAccessStep<T>(8).also { connect(it) }

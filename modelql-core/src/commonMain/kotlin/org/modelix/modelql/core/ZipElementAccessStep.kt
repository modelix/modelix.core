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

import com.badoo.reaktive.observable.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class ZipElementAccessStep<Out>(val index: Int) : MonoTransformingStep<IZipOutput<Any?>, Out>() {

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<Out>> {
        val zipSerializer = getProducers().single().getOutputSerializer(serializationContext) as ZipOutputSerializer<Out, *>
        return zipSerializer.elementSerializers[index]
    }

    override fun createFlow(input: StepFlow<IZipOutput<Any?>>, context: IFlowInstantiationContext): StepFlow<Out> {
        return input.map { (it as ZipStepOutput<*, *>).values[index].upcast() }
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

@Deprecated("Use fourth, the version without typo", ReplaceWith("fourth"))
val <T> IMonoStep<IZip4Output<*, *, *, *, T>>.forth: IMonoStep<T>
    get() = ZipElementAccessStep<T>(3).also { connect(it) }
val <T> IMonoStep<IZip4Output<*, *, *, *, T>>.fourth: IMonoStep<T>
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

operator fun <T> IMonoStep<IZip1Output<*, T>>.component1() = first
operator fun <T> IMonoStep<IZip2Output<*, *, T>>.component2() = second
operator fun <T> IMonoStep<IZip3Output<*, *, *, T>>.component3() = third
operator fun <T> IMonoStep<IZip4Output<*, *, *, *, T>>.component4() = fourth
operator fun <T> IMonoStep<IZip5Output<*, *, *, *, *, T>>.component5() = fifth
operator fun <T> IMonoStep<IZip6Output<*, *, *, *, *, *, T>>.component6() = sixth
operator fun <T> IMonoStep<IZip7Output<*, *, *, *, *, *, *, T>>.component7() = seventh
operator fun <T> IMonoStep<IZip8Output<*, *, *, *, *, *, *, *, T>>.component8() = eighth
operator fun <T> IMonoStep<IZip9Output<*, *, *, *, *, *, *, *, *, T>>.component9() = ninth

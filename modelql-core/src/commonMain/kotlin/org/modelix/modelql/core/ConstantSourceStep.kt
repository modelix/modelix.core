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

import com.badoo.reaktive.observable.observableOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.NothingSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer
import kotlin.jvm.JvmName
import kotlin.reflect.KType
import kotlin.reflect.typeOf

private val supportedTypes = setOf(
    typeOf<Boolean>(), typeOf<Boolean?>(),
    typeOf<Byte>(), typeOf<Byte?>(),
    typeOf<Char>(), typeOf<Char?>(),
    typeOf<Short>(), typeOf<Short?>(),
    typeOf<Int>(), typeOf<Int?>(),
    typeOf<Long>(), typeOf<Long?>(),
    typeOf<Float>(), typeOf<Float?>(),
    typeOf<Double>(), typeOf<Double?>(),
    typeOf<String>(), typeOf<String?>(),
    typeOf<Set<String>>(), typeOf<Set<String?>>(),
)
private val string2type: Map<String, KType> = supportedTypes.associateBy { it.toString() }

open class ConstantSourceStep<E>(val element: E, val type: KType) : ProducingStep<E>(), IMonoStep<E> {
    override fun canBeEmpty(): Boolean = false
    override fun canBeMultiple(): Boolean = false
    override fun canEvaluateStatically(): Boolean = true
    override fun requiresSingularQueryInput(): Boolean = false
    override fun hasSideEffect(): Boolean = false
    override fun requiresWriteAccess(): Boolean = false
    override fun needsCoroutineScope(): Boolean = false

    override fun evaluateStatically(): E {
        return element
    }

    override fun createFlow(context: IFlowInstantiationContext): StepFlow<E> {
        return observableOf(element.asStepOutput(this))
    }

    override fun toString(): String {
        return """Mono($element)"""
    }

    override fun getOutputSerializer(serializationContext: SerializationContext): KSerializer<out IStepOutput<E>> {
        return (serializationContext.serializer(type) as KSerializer<E>).stepOutputSerializer(this)
    }

    override fun createDescriptor(context: QueryGraphDescriptorBuilder): StepDescriptor = Descriptor(element, type.toString())

    @Serializable(with = Descriptor.Serializer::class)
    @SerialName("monoSource")
    class Descriptor(val element: Any?, val elementType: String) : CoreStepDescriptor() {
        override fun createStep(context: QueryDeserializationContext): IStep {
            return ConstantSourceStep<Any?>(
                element,
                string2type[elementType] ?: throw IllegalArgumentException("Unsupported type: $elementType"),
            )
        }

        class Serializer : KSerializer<Descriptor> {
            override fun deserialize(decoder: Decoder): Descriptor {
                var type: String? = null
                var value: Any? = null
                var owner: Long? = null
                decoder.decodeStructure(descriptor) {
                    while (true) {
                        when (decodeElementIndex(descriptor)) {
                            CompositeDecoder.DECODE_DONE -> break
                            0 -> type = decodeStringElement(descriptor, 0)
                            1 -> value = decodeSerializableElement(descriptor, 1, decoder.serializersModule.serializer(string2type[type!!]!!))
                            2 -> owner = decodeLongElement(descriptor, 2)
                            else -> throw IllegalArgumentException()
                        }
                    }
                }
                return Descriptor(value, type!!).also { it.owner = owner }
            }

            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("monoSource") {
                element("elementType", String.serializer().descriptor)
                element("value", NothingSerializer().descriptor)
                element("owner", Long.serializer().nullable.descriptor, isOptional = true)
            }

            override fun serialize(encoder: Encoder, value: Descriptor) {
                encoder.encodeStructure(descriptor) {
                    encodeStringElement(descriptor, 0, value.elementType)
                    encodeSerializableElement(descriptor, 1, encoder.serializersModule.serializer(string2type[value.elementType]!!), value.element)
                    if (value.owner != null) {
                        encodeLongElement(descriptor, 2, value.owner!!)
                    }
                }
            }
        }
    }
}

private inline fun <reified T> createConstantSourceStep(value: T): IMonoStep<T> = ConstantSourceStep<T>(value, typeOf<T>())

fun Boolean.asMono() = createConstantSourceStep(this)
fun Byte.asMono() = createConstantSourceStep(this)
fun Char.asMono() = createConstantSourceStep(this)
fun Short.asMono() = createConstantSourceStep(this)
fun Int.asMono() = createConstantSourceStep(this)
fun Long.asMono() = createConstantSourceStep(this)
fun Float.asMono() = createConstantSourceStep(this)
fun Double.asMono() = createConstantSourceStep(this)
fun String.asMono() = createConstantSourceStep(this)
fun Set<String>.asMono() = createConstantSourceStep(this)

@JvmName("asMono_nullable")
fun Boolean?.asMono() = createConstantSourceStep(this)

@JvmName("asMono_nullable")
fun Byte?.asMono() = createConstantSourceStep(this)

@JvmName("asMono_nullable")
fun Char?.asMono() = createConstantSourceStep(this)

@JvmName("asMono_nullable")
fun Short?.asMono() = createConstantSourceStep(this)

@JvmName("asMono_nullable")
fun Int?.asMono() = createConstantSourceStep(this)

@JvmName("asMono_nullable")
fun Long?.asMono() = createConstantSourceStep(this)

@JvmName("asMono_nullable")
fun Float?.asMono() = createConstantSourceStep(this)

@JvmName("asMono_nullable")
fun Double?.asMono() = createConstantSourceStep(this)

@JvmName("asMono_nullable")
fun String?.asMono() = createConstantSourceStep(this)

@JvmName("asMono_nullable")
fun Set<String?>.asMono() = createConstantSourceStep(this)

inline fun <reified T> nullMono(): IMonoStep<T?> = ConstantSourceStep<T?>(null, typeOf<T?>())

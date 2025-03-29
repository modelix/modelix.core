package org.modelix.datastructures.serialization

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import org.modelix.kotlin.utils.urlDecode
import org.modelix.kotlin.utils.urlEncode

class SplitJoinFormat(override val serializersModule: SerializersModule) : StringFormat {
    override fun <T> decodeFromString(
        deserializer: DeserializationStrategy<T>,
        string: String,
    ): T {
        return deserializer.deserialize(SplitJoinValueDecoder(serializersModule, string, 0, false))
    }

    override fun <T> encodeToString(
        serializer: SerializationStrategy<T>,
        value: T,
    ): String {
        val encoder = SplitJoinSerializer(serializersModule)
        serializer.serialize(encoder.ForValue(-1, false), value)
        return encoder.toString()
    }
}

class SplitJoinSerializer(val serializersModule: SerializersModule) {
    private val sb = StringBuilder()

    private fun appendRaw(encodedItem: String) {
        sb.append(encodedItem)
    }

    private fun appendString(item: String?) {
        appendRaw(item.urlEncode())
    }

    override fun toString(): String {
        return sb.toString()
    }

    inner class ForMap(separatorIndex: Int) : ForComposite(separatorIndex, true) {
        override fun getSeparator(elementIndex: Int): Char {
            return if (elementIndex % 2 == 0) super.getSeparator(elementIndex) else MAPPING
        }
    }

    open inner class ForComposite(val separatorIndex: Int, val alreadyInsideMap: Boolean) : CompositeEncoder {
        private var nextElementIndex = 0

        protected open fun getSeparator(elementIndex: Int): Char {
            return SEPARATORS[separatorIndex]
        }

        private fun prepareNext(elementIndex: Int): ForValue {
            check(elementIndex == nextElementIndex) {
                "Element with index $nextElementIndex expected, but was $elementIndex"
            }
            nextElementIndex++
            if (elementIndex != 0) {
                sb.append(getSeparator(elementIndex))
            }
            return ForValue(separatorIndex, alreadyInsideMap)
        }

        override fun encodeBooleanElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Boolean,
        ) {
            prepareNext(index).encodeBoolean(value)
        }

        override fun encodeByteElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Byte,
        ) {
            prepareNext(index).encodeByte(value)
        }

        override fun encodeCharElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Char,
        ) {
            prepareNext(index).encodeChar(value)
        }

        override fun encodeDoubleElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Double,
        ) {
            prepareNext(index).encodeDouble(value)
        }

        override fun encodeFloatElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Float,
        ) {
            prepareNext(index).encodeFloat(value)
        }

        override fun encodeInlineElement(
            descriptor: SerialDescriptor,
            index: Int,
        ): Encoder {
            return prepareNext(index)
        }

        override fun encodeIntElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Int,
        ) {
            prepareNext(index).encodeInt(value)
        }

        override fun encodeLongElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Long,
        ) {
            prepareNext(index).encodeLong(value)
        }

        @ExperimentalSerializationApi
        override fun <T : Any> encodeNullableSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            serializer: SerializationStrategy<T>,
            value: T?,
        ) {
            if (value == null) {
                prepareNext(index)
                appendRaw("%00")
            } else {
                encodeSerializableElement(descriptor, index, serializer, value)
            }
        }

        override fun <T> encodeSerializableElement(
            descriptor: SerialDescriptor,
            index: Int,
            serializer: SerializationStrategy<T>,
            value: T,
        ) {
            serializer.serialize(prepareNext(index), value)
        }

        override fun encodeShortElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: Short,
        ) {
            prepareNext(index).encodeShort(value)
        }

        override fun encodeStringElement(
            descriptor: SerialDescriptor,
            index: Int,
            value: String,
        ) {
            prepareNext(index).encodeString(value)
        }

        override fun endStructure(descriptor: SerialDescriptor) {}

        override val serializersModule: SerializersModule
            get() = this@SplitJoinSerializer.serializersModule
    }

    inner class ForValue(val separatorIndex: Int, val alreadyInsideMap: Boolean) : Encoder {
        override fun beginStructure(structureDescriptor: SerialDescriptor): CompositeEncoder {
            return if (!alreadyInsideMap && structureDescriptor.kind == StructureKind.MAP) {
                ForMap(separatorIndex + 1)
            } else {
                ForComposite(separatorIndex + 1, alreadyInsideMap)
            }
        }
        override fun encodeBoolean(value: Boolean) = appendRaw(value.toString())
        override fun encodeByte(value: Byte) = appendRaw(value.toUInt().toString(16))
        override fun encodeChar(value: Char) = appendString(value.toString())
        override fun encodeDouble(value: Double) = appendRaw(value.toString())
        override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int): Unit = appendRaw(index.toString())
        override fun encodeFloat(value: Float) = appendRaw(value.toString())
        override fun encodeInline(descriptor: SerialDescriptor): Encoder = this
        override fun encodeInt(value: Int) = appendRaw(value.toUInt().toString(16))
        override fun encodeLong(value: Long) = appendRaw(value.toULong().toString(16))

        @ExperimentalSerializationApi
        override fun encodeNull() = appendRaw("%00")
        override fun encodeShort(value: Short) = appendRaw(value.toUShort().toString(16))
        override fun encodeString(value: String) = appendString(value)

        override val serializersModule: SerializersModule
            get() = this@SplitJoinSerializer.serializersModule
    }

    companion object {
        val SEPARATORS = charArrayOf(
            '/', ',', ';', ':', // legacy separators
            '|', '\\', '!', '#', '~', '^', // additional good candidates
            '@', '&', '?', // usually have a special meaning and are not recognized as separators
            '(', ')', '<', '>', '[', ']', '{', '}', // brackets usually appear in pairs around lists
            '\'', '`', '"', // usually used for string literals and appear in pairs
            '$', // problematic in Kotlin strings
            ' ', // good separator, but whitespace characters are more suitable to separate entire objects
            // '*', '-', '.', '_', still part of a string after url encoding
        )
        val MAPPING = '='

        private val DEFAULT_SERIALIZERS_MODULE = SerializersModule {}

        fun <T> serialize(strategy: SerializationStrategy<T>, data: T): String {
            val encoder = SplitJoinSerializer(DEFAULT_SERIALIZERS_MODULE)
            strategy.serialize(encoder.ForValue(-1, false), data)
            return encoder.toString()
        }

        fun <T> deserialize(strategy: DeserializationStrategy<T>, serialized: String): T {
            return strategy.deserialize(SplitJoinValueDecoder(DEFAULT_SERIALIZERS_MODULE, serialized, 0, false))
        }
    }
}

class SplitJoinMapDecoder(
    serializersModule: SerializersModule,
    descriptor: SerialDescriptor,
    serialized: List<String>,
    separatorIndex: Int,
) : SplitJoinStructureDecoder(serializersModule, descriptor, serialized, separatorIndex, true) {
    override fun forIndex(index: Int): SplitJoinValueDecoder {
        check(nextElementIndex == index) {
            "Element with index $nextElementIndex expected, but was $index"
        }
        nextElementIndex++
        return SplitJoinValueDecoder(
            serializersModule = serializersModule,
            serialized = serialized[index / 2].let {
                if (index % 2 == 0) {
                    it.substringBefore(SplitJoinSerializer.MAPPING)
                } else {
                    it.substringAfter(SplitJoinSerializer.MAPPING)
                }
            },
            separatorIndex = separatorIndex,
            alreadyInsideMap = true,
        )
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return if (nextElementIndex < serialized.size * 2) nextElementIndex else CompositeDecoder.DECODE_DONE
    }
}

open class SplitJoinStructureDecoder(
    override val serializersModule: SerializersModule,
    val descriptor: SerialDescriptor,
    val serialized: List<String>,
    val separatorIndex: Int,
    val alreadyInsideMap: Boolean,
) : CompositeDecoder {
    protected var nextElementIndex = 0

    protected open fun forIndex(index: Int): SplitJoinValueDecoder {
        check(nextElementIndex == index) {
            "Element with index $nextElementIndex expected, but was $index"
        }
        nextElementIndex++
        return SplitJoinValueDecoder(serializersModule, serialized[index], separatorIndex, alreadyInsideMap)
    }

    override fun decodeBooleanElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Boolean {
        return forIndex(index).decodeBoolean()
    }

    override fun decodeByteElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Byte {
        return forIndex(index).decodeByte()
    }

    override fun decodeCharElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Char {
        return forIndex(index).decodeChar()
    }

    override fun decodeDoubleElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Double {
        return forIndex(index).decodeDouble()
    }

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        return if (nextElementIndex < serialized.size) nextElementIndex else CompositeDecoder.DECODE_DONE
    }

    override fun decodeFloatElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Float {
        return forIndex(index).decodeFloat()
    }

    override fun decodeInlineElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Decoder {
        return forIndex(index)
    }

    override fun decodeIntElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Int {
        return forIndex(index).decodeInt()
    }

    override fun decodeLongElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Long {
        return forIndex(index).decodeLong()
    }

    @ExperimentalSerializationApi
    override fun <T : Any> decodeNullableSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T?>,
        previousValue: T?,
    ): T? {
        val valueDecoder = forIndex(index)
        if (valueDecoder.serialized == "%00") return null
        val elementDescriptor = descriptor.getElementDescriptor(index)
        if (valueDecoder.serialized == "" && elementDescriptor.kind != PrimitiveKind.STRING) return null
        return valueDecoder.decodeNullableSerializableValue(deserializer)
    }

    override fun <T> decodeSerializableElement(
        descriptor: SerialDescriptor,
        index: Int,
        deserializer: DeserializationStrategy<T>,
        previousValue: T?,
    ): T {
        return forIndex(index).decodeSerializableValue(deserializer)
    }

    override fun decodeShortElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): Short {
        return forIndex(index).decodeShort()
    }

    override fun decodeStringElement(
        descriptor: SerialDescriptor,
        index: Int,
    ): String {
        return forIndex(index).decodeString()
    }

    override fun endStructure(descriptor: SerialDescriptor) {}
}

class SplitJoinValueDecoder(
    override val serializersModule: SerializersModule,
    val serialized: String,
    val separatorIndex: Int,
    val alreadyInsideMap: Boolean,
) : Decoder {

    override fun beginStructure(descriptor: SerialDescriptor): CompositeDecoder {
        val parts = if (serialized.isEmpty()) {
            emptyList()
        } else {
            serialized.split(SplitJoinSerializer.SEPARATORS[separatorIndex])
        }

        if (!alreadyInsideMap && descriptor.kind == StructureKind.MAP) {
            return SplitJoinMapDecoder(serializersModule, descriptor, parts, separatorIndex + 1)
        } else {
            return SplitJoinStructureDecoder(serializersModule, descriptor, parts, separatorIndex + 1, alreadyInsideMap)
        }
    }

    override fun decodeBoolean(): Boolean {
        return serialized.toBooleanStrict()
    }

    override fun decodeByte(): Byte {
        return serialized.toUByte(16).toByte()
    }

    override fun decodeChar(): Char {
        return (serialized.urlDecode() ?: return Char(0)).single()
    }

    override fun decodeDouble(): Double {
        return serialized.toDouble()
    }

    override fun decodeEnum(enumDescriptor: SerialDescriptor): Int {
        return serialized.toInt()
    }

    override fun decodeFloat(): Float {
        return serialized.toFloat()
    }

    override fun decodeInline(descriptor: SerialDescriptor): Decoder {
        return this
    }

    override fun decodeInt(): Int {
        return serialized.toUInt(16).toInt()
    }

    override fun decodeLong(): Long {
        return serialized.toULong(16).toLong()
    }

    @ExperimentalSerializationApi
    override fun decodeNotNullMark(): Boolean {
        return serialized != "%00"
    }

    @ExperimentalSerializationApi
    override fun decodeNull(): Nothing? {
        check(serialized == "%00")
        return null
    }

    override fun decodeShort(): Short {
        return serialized.toUShort(16).toShort()
    }

    override fun decodeString(): String {
        return checkNotNull(serialized.urlDecode())
    }
}

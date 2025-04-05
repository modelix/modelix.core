package org.modelix.datastructures.list

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.modelix.datastructures.objects.IDataTypeConfiguration
import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.IObjectDeserializer
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.IObjectReferenceFactory
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectHash
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.getDescendantsAndSelf
import org.modelix.datastructures.objects.getHashString
import org.modelix.datastructures.serialization.SerializationSeparators
import org.modelix.kotlin.utils.urlDecode
import org.modelix.kotlin.utils.urlEncode
import org.modelix.streams.IStream
import kotlin.collections.chunked

class LargeListConfig<E>(
    val graph: IObjectGraph,
    val elementType: IDataTypeConfiguration<E>,
    val maxNodeSize: Int = 20,
) : IObjectDeserializer<LargeList<E>> {
    override fun deserialize(input: String, referenceFactory: IObjectReferenceFactory): LargeList<E> {
        val data = if (input.startsWith(LargeList.LARGE_LIST_PREFIX)) {
            val subLists = input.substring(LargeList.LARGE_LIST_PREFIX.length)
                .split(SerializationSeparators.LEVEL2)
                .map { referenceFactory(it, this) }
            LargeListInternalNode(this, subLists)
        } else {
            LargeListLeafNode(
                this,
                input.split(SerializationSeparators.LEVEL2)
                    .filter { it.isNotEmpty() }
                    .map { elementType.deserialize(it.urlDecode()!!) },
            )
        }
        return data
    }

    fun createEmptyList(): LargeList<E> = LargeListLeafNode(this, emptyList())

    fun createList(elements: List<E>): LargeList<E> {
        return if (elements.size <= maxNodeSize) {
            LargeListLeafNode(this, elements)
        } else {
            // split the elements into at most maxNodeSize sub lists, but also minimize the number of objects
            val sublistSizes = ((elements.size + maxNodeSize - 1) / maxNodeSize).coerceAtLeast(maxNodeSize)
            LargeListInternalNode(this, elements.chunked(sublistSizes) { graph.fromCreated(createList(it.toList())) }.toList())
        }
    }
}

class LargeListKSerializer<E>(val config: LargeListConfig<E>) : KSerializer<LargeList<E>> {
    private val listSerializer = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = listSerializer.descriptor

    override fun serialize(encoder: Encoder, value: LargeList<E>) {
        when (value) {
            is LargeListInternalNode<E> -> listSerializer.serialize(encoder, value.subLists.map { it.getHashString() })
            is LargeListLeafNode<E> -> listSerializer.serialize(encoder, value.elements.map { config.elementType.serialize(it) })
        }
    }

    override fun deserialize(decoder: Decoder): LargeList<E> {
        val strings = listSerializer.deserialize(decoder)
        return if (strings.isNotEmpty() && ObjectHash.isValidHashString(strings.first())) {
            LargeListInternalNode(
                config,
                strings.map { config.graph.fromHashString(it, config) },
            )
        } else {
            LargeListLeafNode(config, strings.map { config.elementType.deserialize(it) })
        }
    }
}

sealed class LargeList<E>() : IObjectData {
    companion object {
        const val LARGE_LIST_PREFIX = "OL" + SerializationSeparators.LEVEL1
    }

    abstract fun getElements(): IStream.Many<E>

    override fun objectDiff(self: Object<*>, oldObject: Object<*>?): IStream.Many<Object<*>> {
        return self.getDescendantsAndSelf()
    }
}

class LargeListInternalNode<E>(val config: LargeListConfig<E>, val subLists: List<ObjectReference<LargeList<E>>>) : LargeList<E>() {
    override fun serialize(): String {
        return LARGE_LIST_PREFIX + subLists.joinToString(SerializationSeparators.LEVEL2) { it.getHashString() }
    }

    override fun getDeserializer(): IObjectDeserializer<LargeList<E>> = config

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return subLists.toList()
    }

    override fun getElements(): IStream.Many<E> {
        return IStream.many(subLists).flatMap {
            it.resolveData().flatMap { it.getElements() }
        }
    }
}

class LargeListLeafNode<E>(val config: LargeListConfig<E>, val elements: List<E>) : LargeList<E>() {
    override fun serialize(): String {
        return if (elements.isEmpty()) {
            ""
        } else {
            elements.joinToString(SerializationSeparators.LEVEL2) { config.elementType.serialize(it).urlEncode() }
        }
    }

    override fun getDeserializer(): IObjectDeserializer<LargeList<E>> = config

    override fun getContainmentReferences(): List<ObjectReference<IObjectData>> {
        return elements.flatMap { config.elementType.getContainmentReferences(it) }
    }

    override fun getElements(): IStream.Many<E> {
        return IStream.many(elements)
    }
}

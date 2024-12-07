package org.modelix.metamodel

interface IPropertyValueSerializer<ValueT> {
    fun serialize(value: ValueT): String?
    fun deserialize(serialized: String?): ValueT
}

object MandatoryStringPropertySerializer : IPropertyValueSerializer<String> {
    override fun serialize(value: String): String? {
        return value
    }

    override fun deserialize(serialized: String?): String {
        return serialized ?: ""
    }
}

object OptionalStringPropertySerializer : IPropertyValueSerializer<String?> {
    override fun serialize(value: String?): String? {
        return value
    }

    override fun deserialize(serialized: String?): String? {
        return serialized
    }
}

object MandatoryBooleanPropertySerializer : IPropertyValueSerializer<Boolean> {
    override fun serialize(value: Boolean): String? {
        return if (value) "true" else "false"
    }

    override fun deserialize(serialized: String?): Boolean {
        return serialized == "true"
    }
}

object OptionalBooleanPropertySerializer : IPropertyValueSerializer<Boolean?> {
    override fun serialize(value: Boolean?): String? {
        return value?.let { if (it) "true" else "false" }
    }

    override fun deserialize(serialized: String?): Boolean? {
        return serialized?.let { it == "true" }
    }
}

object MandatoryIntPropertySerializer : IPropertyValueSerializer<Int> {
    override fun serialize(value: Int): String? {
        return value.toString()
    }

    override fun deserialize(serialized: String?): Int {
        return serialized?.toInt() ?: 0
    }
}

object OptionalIntPropertySerializer : IPropertyValueSerializer<Int?> {
    override fun serialize(value: Int?): String? {
        return value?.toString()
    }

    override fun deserialize(serialized: String?): Int? {
        return serialized?.toInt()
    }
}

abstract class EnumSerializer {
    protected fun serializeEnumMember(id: String, name: String) = "$id/$name"
    protected fun deserializeEnumMemberId(serialized: String?) = serialized?.substringBefore('/')
}

class MandatoryEnumSerializer<E : Enum<*>>(
    private val memberIdOf: (E) -> String,
    private val fromMemberId: (String?) -> E,
) : EnumSerializer(), IPropertyValueSerializer<E> {

    override fun serialize(value: E): String {
        return serializeEnumMember(memberIdOf(value), value.name)
    }

    override fun deserialize(serialized: String?): E {
        return fromMemberId(deserializeEnumMemberId(serialized))
    }
}

class OptionalEnumSerializer<E : Enum<*>>(
    private val memberIdOf: (E) -> String,
    private val fromMemberId: (String) -> E,
) : EnumSerializer(), IPropertyValueSerializer<E?> {

    override fun serialize(value: E?): String? {
        return value?.let { serializeEnumMember(memberIdOf(it), it.name) }
    }

    override fun deserialize(serialized: String?): E? {
        return deserializeEnumMemberId(serialized)?.let { fromMemberId(it) }
    }
}

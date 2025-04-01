package org.modelix.model.api

import kotlinx.serialization.Serializable
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.kotlin.utils.urlDecode
import org.modelix.kotlin.utils.urlEncode

/**
 * An [IRole] is a structural feature of a concept.
 * It can either be an [IProperty] or an [ILink].
 */
@Deprecated("Use IRoleReference or IRoleDefinition")
interface IRole : IRoleDefinition {
    /**
     * Returns the concept this role belongs to.
     *
     * @return concept
     */
    override fun getConcept(): IConcept

    /**
     * Returns the uid of this role.
     *
     * @return uid
     */
    override fun getUID(): String

    /**
     * Returns the unqualified name of this role.
     *
     * @return simple name
     */
    override fun getSimpleName(): String

    @Deprecated("Use getSimpleName() when showing it to the user or when accessing the model use the INode functions that accept an IRole or use IRole.key(...)")
    val name: String get() = toReference().stringForLegacyApi() ?: "null"

    /**
     * Returns whether this role's value has to be set or not.
     *
     * @return true if this role's value is optional, false otherwise
     */
    override val isOptional: Boolean

    override fun toReference(): IRoleReference
}

sealed interface IRoleDefinition {
    fun getConcept(): IConcept
    fun getUID(): String
    fun getSimpleName(): String
    val isOptional: Boolean
    fun toReference(): IRoleReference

    @Deprecated("use IRoleReference or IRoleDefinition instead of IRole")
    fun toLegacy(): IRole
}

@Serializable
sealed interface IRoleReference {
    fun getSimpleName(): String?
    fun getUID(): String?

    /**
     * Get whichever is available, but prefer the name.
     *
     * Should only be use in the same cases as [getIdOrName], but when a legacy name based persistence is used.
     */
    @DelicateModelixApi
    @Deprecated("Name based persistence is legacy and IDs should be used")
    fun getNameOrId(): String

    /**
     * Get whichever is available, but prefer the UID.
     *
     * Use it only when persisting model data to produce a stable ObjectHash.
     * When passing a string value to some legacy API use [stringForLegacyApi] or [toString].
     */
    @DelicateModelixApi
    fun getIdOrName(): String

    /**
     * Use this for APIs that still work with strings, but have some implementation that uses [IRoleReference].
     */
    fun stringForLegacyApi(): String?

    fun matches(unclassified: String?): Boolean

    @Deprecated("use IRoleReference or IRoleDefinition instead of IRole")
    fun toLegacy(): IRole

    companion object {
        fun <T : IRoleReference> decodeStringFromLegacyApi(value: String?, factory: IRoleReferenceFactory<T>): T {
            if (value == null || value == "null") return factory.fromNull()
            val parts = value.split(":")
            if (parts.size != 3 || parts[0] != "") return factory.fromUnclassifiedString(value)
            val id = parts[1].takeIf { it.isNotEmpty() }?.urlDecode()
            val name = parts[2].takeIf { it.isNotEmpty() }?.urlDecode()
            return if (id != null) {
                if (name != null) {
                    factory.fromIdAndName(id, name)
                } else {
                    factory.fromId(id)
                }
            } else {
                if (name != null) {
                    factory.fromName(name)
                } else {
                    throw IllegalArgumentException("No ID and no name provided: $value")
                }
            }
        }

        fun encodeStringForLegacyApi(id: String?, name: String?): String {
            return ":" + (id?.urlEncode() ?: "") + ":" + (name?.urlEncode() ?: "")
        }

        fun requireNotForLegacyApi(value: String?) {
            if (value == null) return
            require(value.count { it == ':' } != 3) {
                "Use .fromLegacyApi() for this string: $value"
            }
        }
    }
}

interface IRoleReferenceFactory<E : IRoleReference> {
    fun fromNull(): E = throw IllegalArgumentException("Null values not allowed")

    /**
     * Use [fromString] instead, unless you are sure you want to create an instance of an Unclassified...Reference.
     */
    @DelicateModelixApi
    fun fromUnclassifiedString(value: String): E

    fun fromName(value: String): E
    fun fromId(value: String): E
    fun fromIdAndName(id: String?, name: String?): E
    fun fromLegacyApi(value: String?): E = IRoleReference.decodeStringFromLegacyApi(value, this)
    fun fromString(value: String?): E = fromLegacyApi(value)
}

fun IRoleReference.matches(other: IRoleReference): Boolean {
    return when (this) {
        is IPropertyReference -> when (other) {
            is IPropertyReference -> this.matches(other)
            else -> false
        }
        is IReferenceLinkReference -> when (other) {
            is IReferenceLinkReference -> this.matches(other)
            else -> false
        }
        is IChildLinkReference -> when (other) {
            is IChildLinkReference -> this.matches(other)
            else -> false
        }
    }
}

@Serializable
sealed interface IUnclassifiedRoleReference : IRoleReference {
    fun getStringValue(): String
}

@Serializable
sealed interface IRoleReferenceByName : IRoleReference {
    override fun getSimpleName(): String
}

@Serializable
sealed interface IRoleReferenceByUID : IRoleReference {
    override fun getUID(): String
}

@Serializable
sealed class AbstractRoleReference : IRoleReference {
    final override fun toString(): String = stringForLegacyApi() ?: "null"
    override fun getUID(): String = throw UnsupportedOperationException()
    override fun getSimpleName(): String = throw UnsupportedOperationException()
    final override fun equals(other: Any?): Boolean {
        if (other !is IRoleReference) return false

        /**
         Using [matches] would violate this requirement:

         It is transitive: for any non-null reference values x, y, and z, if x.equals(y) returns true and
         y.equals(z) returns true, then x.equals(z) should return true.

         For the three reference
         - x of type ByIdAndName with the ID '123' and the name 'abc'
         - y of type ByUID with the ID '123'
         - z of type ByName with the name 'abc'
         x.matches(y) is true, x.matches(z) is true, but y.matches(z) is false.

         By comparing the values of [getIdOrName] x.equals(y) is true, x.equals(z) is false and y.equals(z) is false.

         References should be compared using [matches] and not be used as a key in a map.
         This implementation exists to support some like `List<IRoleReference>.distinct()`.

         This implementation is only problematic when name based references are used, which are considered legacy.
         */

        return getIdOrName() == other.getIdOrName()
    }

    final override fun hashCode(): Int {
        return getIdOrName().hashCode()
    }
}

abstract class RoleFromName() : IRole {
    override fun getConcept(): IConcept {
        throw UnsupportedOperationException()
    }

    override fun getUID(): String {
        return name
    }

    override fun getSimpleName(): String {
        return name
    }

    override val isOptional: Boolean
        get() = throw UnsupportedOperationException()
}

fun <T : IRoleReference> Sequence<T>.mergeWith(others: Sequence<T>): List<T> {
    val remaining = others.toMutableList()
    val merged = ArrayList<T>()
    outer@for (left in this) {
        for (i in remaining.indices) {
            val right = remaining[i]
            if (right.matches(left)) {
                val mostSpecific = left.merge(right)
                remaining.removeAt(i)
                merged.add(mostSpecific)
                continue@outer
            }
        }
        merged.add(left)
    }
    merged.addAll(remaining)
    return merged
}

/**
 * Choose the more specific one of two matching references.
 */
fun <T : IRoleReference> T.merge(other: T): T {
    return when (this) {
        is IRoleReferenceByUID -> when (other) {
            is IRoleReferenceByUID -> if (other is IRoleReferenceByName) other else this
            else -> this
        }
        is IRoleReferenceByName -> when (other) {
            is IRoleReferenceByUID -> other
            else -> this
        }
        is IUnclassifiedRoleReference -> when (other) {
            is IUnclassifiedRoleReference -> this
            else -> other
        }
        else -> this
    }
}

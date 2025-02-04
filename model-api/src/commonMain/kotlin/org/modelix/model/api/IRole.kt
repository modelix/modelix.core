package org.modelix.model.api

import kotlinx.serialization.Serializable
import org.modelix.kotlin.utils.ContextValue

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
    val name: String get() = RoleAccessContext.getKey(this)

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
     */
    fun getNameOrId(): String

    /**
     * Get whichever is available, but prefer the UID.
     */
    fun getIdOrName(): String

    @Deprecated("use IRoleReference or IRoleDefinition instead of IRole")
    fun toLegacy(): IRole
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
    override fun getUID(): String = throw UnsupportedOperationException()
    override fun getSimpleName(): String = throw UnsupportedOperationException()
}

@Deprecated("Will be removed after all usages of IRole.name are migrated.")
object RoleAccessContext {
    private val value = ContextValue<Boolean>(false)

    fun <T> runWith(useRoleIds: Boolean, body: () -> T): T {
        return value.computeWith(useRoleIds, body)
    }

    /**
     * Depending on the context returns IRole.getSimpleName() or IRole.getUID()
     */
    fun getKey(role: IRole): String {
        return if (isUsingRoleIds()) {
            // Some implementations use the name to construct a UID. Avoid endless recursions.
            runWith(false) { role.toReference().getIdOrName() }
        } else {
            role.toReference().getNameOrId()
        }
    }

    fun isUsingRoleIds(): Boolean {
        return value.getValueOrNull() ?: false
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

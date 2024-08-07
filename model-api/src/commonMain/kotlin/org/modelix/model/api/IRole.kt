package org.modelix.model.api

import kotlinx.serialization.Serializable
import org.modelix.kotlin.utils.ContextValue

/**
 * An [IRole] is a structural feature of a concept.
 * It can either be an [IProperty] or an [ILink].
 */
interface IRole {
    /**
     * Returns the concept this role belongs to.
     *
     * @return concept
     */
    fun getConcept(): IConcept

    /**
     * Returns the uid of this role.
     *
     * @return uid
     */
    fun getUID(): String

    /**
     * Returns the unqualified name of this role.
     *
     * @return simple name
     */
    fun getSimpleName(): String

    @Deprecated("Use getSimpleName() when showing it to the user or when accessing the model use the INode functions that accept an IRole or use IRole.key(...)")
    val name: String get() = RoleAccessContext.getKey(this)

    /**
     * Returns whether this role's value has to be set or not.
     *
     * @return true if this role's value is optional, false otherwise
     */
    val isOptional: Boolean
}

@Serializable
sealed interface IRoleReference {
    fun getSimpleName(): String?
    fun getUID(): String?
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
abstract class AbstractRoleReference : IRoleReference, IRole {
    override fun getConcept(): IConcept = throw UnsupportedOperationException()
    override fun getUID(): String = throw UnsupportedOperationException()
    override fun getSimpleName(): String = throw UnsupportedOperationException()
    override val isOptional: Boolean get() = throw UnsupportedOperationException()
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
            runWith(false) { role.getUID() }
        } else {
            role.getSimpleName()
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

package org.modelix.model.api

interface IRole {
    fun getConcept(): IConcept
    fun getUID(): String
    fun getSimpleName(): String
    @Deprecated("Use getSimpleName() when showing it to the user or when accessing the model use the INode functions that accept an IRole or use IRole.key(...)")
    val name: String get() = RoleAccessContext.getKey(this)
    val isOptional: Boolean
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
        return value.getValue() ?: false
    }
}

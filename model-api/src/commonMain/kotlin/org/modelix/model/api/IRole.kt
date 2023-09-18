/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model.api

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

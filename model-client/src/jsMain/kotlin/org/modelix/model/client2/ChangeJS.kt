/*
 * Copyright (c) 2023.
 *
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

package org.modelix.model.client2

import INodeJS

/**
 * Represents a change in branch that can be handled by a [ChangeHandler].
 * See [BranchJS.addListener]
 */
@JsExport
sealed interface ChangeJS {
    /**
     * The node on which the change occurred.
     */
    val node: INodeJS
}

/**
 * Represents a changed property value.
 */
@JsExport
data class PropertyChanged(
    override val node: INodeJS,
    /**
     * Role of the changed property.
     */
    val role: String,
) : ChangeJS

/**
 * Represents moved, added or removed children.
 */
@JsExport
data class ChildrenChanged(
    override val node: INodeJS,
    /**
     * Role of the changed children in [node].
     */
    val role: String?,
) : ChangeJS

/**
 * Represents a changed reference target.
 */
@JsExport
data class ReferenceChanged(
    override val node: INodeJS,
    /**
     * Role of the changed reference.
     */
    val role: String,
) : ChangeJS

/**
 * Represents the change of the parent of [node] changed.
 */
@JsExport
data class ContainmentChanged(override val node: INodeJS) : ChangeJS

/**
 * Represents the change of the concept of a [node].
 */
@JsExport
data class ConceptChanged(override val node: INodeJS) : ChangeJS

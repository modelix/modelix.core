package org.modelix.model.client2

import INodeJS

/**
 * Represents a change in branch that can be handled by a [ChangeHandler].
 * See [MutableModelTreeJs.addListener]
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
     * Role of the changed property in the [org.modelix.model.api.IRoleReference.stringForLegacyApi] format.
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
     * Role of the changed children in [node] in the [org.modelix.model.api.IRoleReference.stringForLegacyApi] format.
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
     * Role of the changed reference  in the [org.modelix.model.api.IRoleReference.stringForLegacyApi] format.
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

/**
 * Represents an added [node].
 * Every node of an added subtree is reported, not only the root of the subtree.
 * Only reported to handlers added with [MutableModelTreeJs.addListenerIncludingAddedAndRemovedNodes].
 */
@JsExport
data class NodeAdded(override val node: INodeJS) : ChangeJS

/**
 * Represents a removed [node].
 * Every node of a removed subtree is reported, not only the root of the subtree.
 * Only reported to handlers added with [MutableModelTreeJs.addListenerIncludingAddedAndRemovedNodes].
 * Only the reference of [node] can be read, because it is no longer part of the model.
 */
@JsExport
data class NodeRemoved(override val node: INodeJS) : ChangeJS

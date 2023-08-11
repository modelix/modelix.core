package org.modelix.model.sync

import org.modelix.model.api.INode

actual class ModelExporter actual constructor(private val root: INode) {

    /**
     * Triggers a bulk export of this ModelExporter's root node and its (in-)direct children.
     *
     * @return exported node
     */
    fun export() = root.asExported()
}

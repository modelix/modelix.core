package org.modelix.model.sync

import org.modelix.model.api.INode

actual class ModelExporter actual constructor(private val root: INode) {
    fun export() = root.asExported()
}
package org.modelix.model.sync

import org.modelix.model.api.INode
import org.modelix.model.data.ModelData
import java.io.File

actual class ModelExporter actual constructor(private val root: INode) {
    fun export(outputFile: File) {
        val modelData = ModelData(root = root.asExported())
        outputFile.parentFile.mkdirs()
        outputFile.writeText(modelData.toJson())
    }
}
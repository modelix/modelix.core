package org.modelix.model.sync

import org.modelix.model.api.INode
import org.modelix.model.data.ModelData
import java.io.File

actual class ModelExporter actual constructor(private val root: INode) {

    /**
     * Triggers a bulk export of this ModelExporter's root node and its (in-)direct children into the specified file.
     *
     * @param outputFile target file of the export
     */
    fun export(outputFile: File) {
        val modelData = ModelData(root = root.asExported())
        outputFile.parentFile.mkdirs()
        outputFile.writeText(modelData.toJson())
    }
}

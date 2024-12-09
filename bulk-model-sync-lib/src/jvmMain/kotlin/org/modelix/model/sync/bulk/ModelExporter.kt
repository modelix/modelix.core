package org.modelix.model.sync.bulk

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import org.modelix.model.api.INode
import org.modelix.model.data.ModelData
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.outputStream

actual class ModelExporter actual constructor(private val root: INode) {

    /**
     * Triggers a bulk export of this ModelExporter's root node and its (in-)direct children into the specified file.
     *
     * @param outputFile target file of the export
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun export(outputFile: Path) {
        Files.createDirectories(outputFile.parent)

        val modelData = ModelData(root = root.asExported())
        outputFile.outputStream().use { outputStream ->
            Json.encodeToStream(modelData, outputStream)
        }
    }
}

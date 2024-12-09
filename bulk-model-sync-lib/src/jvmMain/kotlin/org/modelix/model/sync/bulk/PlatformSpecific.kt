package org.modelix.model.sync.bulk

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.modelix.model.data.ModelData
import java.io.File

/**
 * Incrementally updates the root of the receiver [ModelImporter]
 * based on the [ModelData] specification contained in the given file.
 *
 * @param jsonFile json file containing the model specification
 *
 * @throws IllegalArgumentException if the file is not a json file or the file does not exist.
 */
@JvmName("importFile")
fun ModelImporter.import(jsonFile: File) {
    require(jsonFile.exists())

    val data = ModelData.fromJson(jsonFile.readText())
    import(data)
}

@OptIn(ExperimentalSerializationApi::class)
fun ModelImporter.importFilesAsRootChildren(files: Collection<File>) {
    val models: List<ModelData> = files.map { file ->
        file.inputStream().use(Json::decodeFromStream)
    }
    import(mergeModelData(models))
}

@Deprecated("use collection parameter for better performance")
fun ModelImporter.importFilesAsRootChildren(vararg files: File) = importFilesAsRootChildren(files.asList())

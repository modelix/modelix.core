package org.modelix.model.sync

import org.modelix.model.data.ModelData
import java.io.File

// import is a reserved keyword in java
@Deprecated("Use importFile instead", ReplaceWith("importFile(jsonFile)"))
fun ModelImporter.import(jsonFile: File) {
    this.importFile(jsonFile)
}

/**
 * Incrementally updates the root of the receiver [ModelImporter]
 * based on the [ModelData] specification contained in the given file.
 *
 * @param jsonFile json file containing the model specification
 *
 * @throws IllegalArgumentException if the file is not a json file or the file does not exist.
 */
fun ModelImporter.importFile(jsonFile: File) {
    require(jsonFile.exists())
    require(jsonFile.extension == "json")

    val data = ModelData.fromJson(jsonFile.readText())
    import(data)
}

fun ModelImporter.importFilesAsRootChildren(vararg files: File) {
    val models = files.map { ModelData.fromJson(it.readText()) }
    import(mergeModelData(*models.toTypedArray()))
}

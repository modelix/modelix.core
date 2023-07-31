package org.modelix.model.sync

import org.modelix.model.data.ModelData
import java.io.File

fun ModelImporter.import(jsonFile: File) {
    require(jsonFile.exists())
    require(jsonFile.extension == "json")

    val data = ModelData.fromJson(jsonFile.readText())
    import(data)
}
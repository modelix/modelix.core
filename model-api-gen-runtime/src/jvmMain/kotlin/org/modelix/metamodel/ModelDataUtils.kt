package org.modelix.metamodel

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.modelix.model.data.ModelData
import java.io.File

fun modelDataFromFile(file: File): ModelData {
    return when (file.extension.lowercase()) {
        "yaml" -> modelDataFromYaml(file.readText())
        "json" -> ModelData.fromJson(file.readText())
        else -> throw IllegalArgumentException("Unsupported file extension: $file")
    }
}

fun ModelData.toYaml(): String = Yaml.default.encodeToString(this)

fun modelDataFromYaml(serialized: String): ModelData = Yaml.default.decodeFromString(serialized)
fun modelDataFromJson(serialized: String): ModelData = ModelData.fromJson(serialized)

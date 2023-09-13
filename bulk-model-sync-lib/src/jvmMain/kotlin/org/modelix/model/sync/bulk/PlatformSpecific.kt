/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modelix.model.sync.bulk

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

fun ModelImporter.importFilesAsRootChildren(vararg files: File) {
    val models = files.map { ModelData.fromJson(it.readText()) }
    import(mergeModelData(*models.toTypedArray()))
}

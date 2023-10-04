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

import mu.KLogger
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.SimpleConcept
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData

fun mergeModelData(vararg models: ModelData): ModelData {
    return ModelData(root = NodeData(children = models.map { it.root }))
}

internal fun logImportSize(nodeData: NodeData, logger: KLogger) {
    logger.debug { "Number of modules: ${countMpsModules(nodeData)}" }
    logger.debug { "Number of models: ${countMpsModels(nodeData)}" }
    logger.debug { "Number of concepts: ${countConcepts(nodeData)}" }
    logger.debug { "Number of properties: ${countProperties(nodeData)}" }
    logger.debug { "Number of references: ${countReferences(nodeData)}" }
}

private fun countProperties(data: NodeData): Int =
    data.properties.size + data.children.sumOf { countProperties(it) }

private fun countReferences(data: NodeData): Int =
    data.references.size + data.children.sumOf { countReferences(it) }

private fun countConcepts(data: NodeData): Int {
    val set = mutableSetOf<String>()
    countConceptsRec(data, set)
    return set.size
}

private fun countConceptsRec(data: NodeData, set: MutableSet<String>) {
    data.concept?.let { set.add(it) }
    data.children.forEach { countConceptsRec(it, set) }
}

private fun countMpsModels(data: NodeData) =
    countSpecificConcept(data, BuiltinLanguages.MPSRepositoryConcepts.Model)

private fun countMpsModules(data: NodeData) =
    countSpecificConcept(data, BuiltinLanguages.MPSRepositoryConcepts.Module)

private fun countSpecificConcept(data: NodeData, concept: SimpleConcept): Int {
    var count = if (data.concept == concept.getUID()) 1 else 0
    count += data.children.sumOf { countSpecificConcept(it, concept) }
    return count
}

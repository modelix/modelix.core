/*
 * Copyright (c) 2023-2024.
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

package org.modelix.mps.sync.transformation.cache

import com.intellij.util.containers.stream
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.util.synchronizedLinkedHashSet
import org.modelix.mps.sync.util.synchronizedMap

/**
 * WARNING:
 * - use with caution, otherwise this cache may cause memory leaks
 * - if you add a new Map as a field in the class, then please also add it to the `remove` and `isMappedToMps` methods below
 */
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
object MpsToModelixMap {

    private val nodeToModelixId = synchronizedMap<SNode, Long>()
    private val modelixIdToNode = synchronizedMap<Long, SNode>()

    private val modelToModelixId = synchronizedMap<SModel, Long>()
    private val modelixIdToModel = synchronizedMap<Long, SModel>()

    private val moduleToModelixId = synchronizedMap<SModule, Long>()
    private val modelixIdToModule = synchronizedMap<Long, SModule>()

    private val moduleWithOutgoingModuleReferenceToModelixId = synchronizedMap<ModuleWithModuleReference, Long>()
    private val modelixIdToModuleWithOutgoingModuleReference = synchronizedMap<Long, ModuleWithModuleReference>()

    private val modelWithOutgoingModuleReferenceToModelixId = synchronizedMap<ModelWithModuleReference, Long>()
    private val modelixIdToModelWithOutgoingModuleReference = synchronizedMap<Long, ModelWithModuleReference>()

    private val modelWithOutgoingModelReferenceToModelixId = synchronizedMap<ModelWithModelReference, Long>()
    private val modelixIdToModelWithOutgoingModelReference = synchronizedMap<Long, ModelWithModelReference>()

    private val objectsRelatedToAModel = synchronizedMap<SModel, MutableSet<Any>>()
    private val objectsRelatedToAModule = synchronizedMap<SModule, MutableSet<Any>>()

    fun put(node: SNode, modelixId: Long) {
        nodeToModelixId[node] = modelixId
        modelixIdToNode[modelixId] = node

        node.model?.let { putObjRelatedToAModel(it, node) }
    }

    fun put(model: SModel, modelixId: Long) {
        modelToModelixId[model] = modelixId
        modelixIdToModel[modelixId] = model

        putObjRelatedToAModel(model, model)
    }

    fun put(module: SModule, modelixId: Long) {
        moduleToModelixId[module] = modelixId
        modelixIdToModule[modelixId] = module

        putObjRelatedToAModule(module, module)
    }

    fun put(sourceModule: SModule, moduleReference: SModuleReference, modelixId: Long) {
        val moduleWithOutgoingModuleReference = ModuleWithModuleReference(sourceModule, moduleReference)
        moduleWithOutgoingModuleReferenceToModelixId[moduleWithOutgoingModuleReference] = modelixId
        modelixIdToModuleWithOutgoingModuleReference[modelixId] = moduleWithOutgoingModuleReference

        putObjRelatedToAModule(sourceModule, moduleReference)
    }

    fun put(sourceModel: SModel, moduleReference: SModuleReference, modelixId: Long) {
        val modelWithOutgoingModuleReference = ModelWithModuleReference(sourceModel, moduleReference)
        modelWithOutgoingModuleReferenceToModelixId[modelWithOutgoingModuleReference] = modelixId
        modelixIdToModelWithOutgoingModuleReference[modelixId] = modelWithOutgoingModuleReference

        putObjRelatedToAModel(sourceModel, moduleReference)
    }

    fun put(sourceModel: SModel, modelReference: SModelReference, modelixId: Long) {
        val modelWithOutgoingModelReference = ModelWithModelReference(sourceModel, modelReference)
        modelWithOutgoingModelReferenceToModelixId[modelWithOutgoingModelReference] = modelixId
        modelixIdToModelWithOutgoingModelReference[modelixId] = modelWithOutgoingModelReference

        putObjRelatedToAModel(sourceModel, modelReference)
    }

    private fun putObjRelatedToAModel(model: SModel, obj: Any?) {
        objectsRelatedToAModel.computeIfAbsent(model) { synchronizedLinkedHashSet() }.add(obj!!)
        // just in case, the model has not been tracked yet. E.g. @descriptor models that are created locally but were not synchronized to the model server.
        putObjRelatedToAModule(model.module, model)
    }

    private fun putObjRelatedToAModule(module: SModule, obj: Any?) =
        objectsRelatedToAModule.computeIfAbsent(module) { synchronizedLinkedHashSet() }.add(obj!!)

    operator fun get(node: SNode?) = nodeToModelixId[node]

    operator fun get(model: SModel?) = modelToModelixId[model]

    operator fun get(modelId: SModelId?) =
        modelToModelixId.filter { it.key.modelId == modelId }.map { it.value }.firstOrNull()

    operator fun get(module: SModule?) = moduleToModelixId[module]

    operator fun get(moduleId: SModuleId?) =
        moduleToModelixId.filter { it.key.moduleId == moduleId }.map { it.value }.firstOrNull()

    operator fun get(sourceModel: SModel, moduleReference: SModuleReference) =
        modelWithOutgoingModuleReferenceToModelixId[ModelWithModuleReference(sourceModel, moduleReference)]

    operator fun get(sourceModule: SModule, moduleReference: SModuleReference) =
        moduleWithOutgoingModuleReferenceToModelixId[ModuleWithModuleReference(sourceModule, moduleReference)]

    operator fun get(sourceModel: SModel, modelReference: SModelReference) =
        modelWithOutgoingModelReferenceToModelixId[ModelWithModelReference(sourceModel, modelReference)]

    fun getNode(modelixId: Long?) = modelixIdToNode[modelixId]

    fun getModel(modelixId: Long?) = modelixIdToModel[modelixId]

    fun getModule(modelixId: Long?) = modelixIdToModule[modelixId]

    fun getModule(moduleId: SModuleId) = objectsRelatedToAModule.keys.firstOrNull { it.moduleId == moduleId }

    fun getOutgoingModelReference(modelixId: Long?) = modelixIdToModelWithOutgoingModelReference[modelixId]

    fun getOutgoingModuleReferenceFromModel(modelixId: Long?) = modelixIdToModelWithOutgoingModuleReference[modelixId]

    fun getOutgoingModuleReferenceFromModule(modelixId: Long?) = modelixIdToModuleWithOutgoingModuleReference[modelixId]

    fun remove(modelixId: Long) {
        // is related to node
        modelixIdToNode.remove(modelixId)?.let { nodeToModelixId.remove(it) }

        // is related to model
        modelixIdToModel.remove(modelixId)?.let {
            modelToModelixId.remove(it)
            remove(it)
        }
        modelixIdToModelWithOutgoingModelReference.remove(modelixId)
            ?.let { modelWithOutgoingModelReferenceToModelixId.remove(it) }
        modelixIdToModelWithOutgoingModuleReference.remove(modelixId)
            ?.let { modelWithOutgoingModuleReferenceToModelixId.remove(it) }

        // is related to module
        modelixIdToModule.remove(modelixId)?.let { remove(it) }
        modelixIdToModuleWithOutgoingModuleReference.remove(modelixId)
            ?.let { moduleWithOutgoingModuleReferenceToModelixId.remove(it) }
    }

    fun remove(model: SModel) {
        modelToModelixId.remove(model)?.let { modelixIdToModel.remove(it) }
        objectsRelatedToAModel.remove(model)?.forEach {
            when (it) {
                is SModuleReference -> {
                    val target = ModelWithModuleReference(model, it)
                    modelWithOutgoingModuleReferenceToModelixId.remove(target)
                        ?.let { id -> modelixIdToModelWithOutgoingModuleReference.remove(id) }
                }

                is SModelReference -> {
                    val target = ModelWithModelReference(model, it)
                    modelWithOutgoingModelReferenceToModelixId.remove(target)
                        ?.let { id -> modelixIdToModelWithOutgoingModelReference.remove(id) }
                }

                is SNode -> {
                    nodeToModelixId.remove(it)?.let { modelixId -> modelixIdToNode.remove(modelixId) }
                }
            }
        }
    }

    fun remove(module: SModule) {
        moduleToModelixId.remove(module)?.let { modelixIdToModule.remove(it) }
        objectsRelatedToAModule.remove(module)?.forEach {
            if (it is SModuleReference) {
                val target = ModuleWithModuleReference(module, it)
                moduleWithOutgoingModuleReferenceToModelixId.remove(target)
                    ?.let { id -> modelixIdToModuleWithOutgoingModuleReference.remove(id) }
            } else if (it is SModel) {
                remove(it)
            }
        }
    }

    fun isMappedToMps(modelixId: Long?): Boolean {
        val idMaps = arrayOf(
            modelixIdToNode,
            modelixIdToModel,
            modelixIdToModule,
            modelixIdToModuleWithOutgoingModuleReference,
            modelixIdToModelWithOutgoingModuleReference,
            modelixIdToModelWithOutgoingModelReference,
        )
        return modelixId != null && idMaps.stream().anyMatch { it.contains(modelixId) }
    }
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class ModelWithModelReference(val source: SModel, val modelReference: SModelReference)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class ModelWithModuleReference(val source: SModel, val moduleReference: SModuleReference)

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
data class ModuleWithModuleReference(val source: SModule, val moduleReference: SModuleReference)

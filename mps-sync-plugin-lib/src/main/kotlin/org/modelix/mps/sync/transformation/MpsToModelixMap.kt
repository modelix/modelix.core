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

package org.modelix.mps.sync.transformation

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.kotlin.utils.UnstableModelixFeature

// use with caution, otherwise this cache may cause memory leaks
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class MpsToModelixMap {

    companion object {
        val instance = MpsToModelixMap()
    }

    // WARNING: if you add a new Map here, please also add it to the `remove` and `isMappedToMps` methods below

    private val sNodeToModelixId = mutableMapOf<SNode, Long>()
    private val modelixIdToSNode = mutableMapOf<Long, SNode>()

    private val sModelToModelixId = mutableMapOf<SModel, Long>()
    private val modelixIdToSModel = mutableMapOf<Long, SModel>()

    private val sModelIdToModelixId = mutableMapOf<SModelId, Long>()
    private val modelixIdToSModelId = mutableMapOf<Long, SModelId>()

    private val sModuleToModelixId = mutableMapOf<SModule, Long>()
    private val modelixIdToSModule = mutableMapOf<Long, SModule>()

    private val sModuleReferenceToModelixId = mutableMapOf<SModuleReference, Long>()
    private val modelixIdToSModuleReference = mutableMapOf<Long, SModuleReference>()

    private val sModelReferenceToModelixId = mutableMapOf<SModelReference, Long>()
    private val modelixIdToSModelReference = mutableMapOf<Long, SModelReference>()

    private val objectsRelatedToAModel = mutableMapOf<SModel, MutableSet<Any>>()
    private val objectsRelatedToAModule = mutableMapOf<SModule, MutableSet<Any>>()

    val models = modelixIdToSModel.values
    val modules = modelixIdToSModule.values

    fun put(node: SNode, modelixId: Long) {
        sNodeToModelixId[node] = modelixId
        modelixIdToSNode[modelixId] = node
        node.model?.let { putObjRelatedToAModel(it, node) }
    }

    fun put(model: SModel, modelixId: Long) {
        sModelToModelixId[model] = modelixId
        modelixIdToSModel[modelixId] = model

        val modelId = model.modelId
        sModelIdToModelixId[modelId] = modelixId
        modelixIdToSModelId[modelixId] = modelId

        putObjRelatedToAModel(model, model)
    }

    fun put(module: SModule, modelixId: Long) {
        sModuleToModelixId[module] = modelixId
        modelixIdToSModule[modelixId] = module

        putObjRelatedToAModule(module, module)
    }

    fun put(moduleReference: SModuleReference, modelixId: Long, sourceModule: SModule) {
        sModuleReferenceToModelixId[moduleReference] = modelixId
        modelixIdToSModuleReference[modelixId] = moduleReference

        putObjRelatedToAModule(sourceModule, moduleReference)
    }

    fun put(moduleReference: SModuleReference, modelixId: Long, sourceModel: SModel) {
        sModuleReferenceToModelixId[moduleReference] = modelixId
        modelixIdToSModuleReference[modelixId] = moduleReference

        putObjRelatedToAModel(sourceModel, moduleReference)
    }

    fun put(modelReference: SModelReference, modelixId: Long, sourceModel: SModel) {
        sModelReferenceToModelixId[modelReference] = modelixId
        modelixIdToSModelReference[modelixId] = modelReference

        putObjRelatedToAModel(sourceModel, modelReference)
    }

    private fun putObjRelatedToAModel(model: SModel, obj: Any?) =
        objectsRelatedToAModel.computeIfAbsent(model) { mutableSetOf() }.add(obj!!)

    private fun putObjRelatedToAModule(module: SModule, obj: Any?) =
        objectsRelatedToAModule.computeIfAbsent(module) { mutableSetOf() }.add(obj!!)

    operator fun get(node: SNode?) = sNodeToModelixId[node]

    operator fun get(model: SModel?) = sModelToModelixId[model]

    operator fun get(modelId: SModelId?) = sModelIdToModelixId[modelId]

    operator fun get(module: SModule?) = sModuleToModelixId[module]

    operator fun get(moduleReference: SModuleReference?) = sModuleReferenceToModelixId[moduleReference]

    operator fun get(modelReference: SModelReference?) = sModelReferenceToModelixId[modelReference]

    fun getNode(modelixId: Long?) = modelixIdToSNode[modelixId]

    fun getModel(modelixId: Long?) = modelixIdToSModel[modelixId]

    fun getModule(modelixId: Long?) = modelixIdToSModule[modelixId]

    fun getModuleReference(modelixId: Long?) = modelixIdToSModuleReference[modelixId]

    fun getModelReference(modelixId: Long?) = modelixIdToSModelReference[modelixId]

    fun remove(modelixId: Long) {
        modelixIdToSNode.remove(modelixId)?.let { sNodeToModelixId.remove(it) }
        modelixIdToSModel.remove(modelixId)?.let {
            sModelToModelixId.remove(it)
            remove(it)
        }
        modelixIdToSModelId.remove(modelixId)?.let { sModelIdToModelixId.remove(it) }
        modelixIdToSModelReference.remove(modelixId)?.let { sModelReferenceToModelixId.remove(it) }
        modelixIdToSModule.remove(modelixId)?.let {
            sModuleToModelixId.remove(it)
            remove(it)
        }
        modelixIdToSModuleReference.remove(modelixId)?.let { sModuleReferenceToModelixId.remove(it) }
    }

    fun remove(model: SModel) {
        sModelToModelixId.remove(model)?.let { modelixIdToSModel.remove(it) }
        sModelIdToModelixId.remove(model.modelId)?.let { modelixIdToSModelId.remove(it) }
        objectsRelatedToAModel.remove(model)?.forEach {
            if (it is SModuleReference) {
                sModuleReferenceToModelixId.remove(it)?.let { id -> modelixIdToSModuleReference.remove(id) }
            } else if (it is SModelReference) {
                sModelReferenceToModelixId.remove(it)?.let { modelixId -> modelixIdToSModelReference.remove(modelixId) }
            } else if (it is SNode) {
                sNodeToModelixId.remove(it)?.let { modelixId -> modelixIdToSNode.remove(modelixId) }
            }
        }
    }

    fun remove(module: SModule) {
        sModuleToModelixId.remove(module)?.let { modelixIdToSModule.remove(it) }
        objectsRelatedToAModule.remove(module)?.forEach {
            if (it is SModuleReference) {
                sModuleReferenceToModelixId.remove(it)?.let { id -> modelixIdToSModuleReference.remove(id) }
            } else if (it is SModel) {
                remove(it)
            }
        }
    }

    fun isMappedToMps(modelixId: Long?) =
        modelixId != null && (
            modelixIdToSNode.contains(modelixId) || modelixIdToSModel.contains(modelixId) ||
                modelixIdToSModelId.contains(modelixId) || modelixIdToSModule.contains(modelixId) ||
                modelixIdToSModuleReference.contains(modelixId) || modelixIdToSModelReference.contains(modelixId)
            )
}

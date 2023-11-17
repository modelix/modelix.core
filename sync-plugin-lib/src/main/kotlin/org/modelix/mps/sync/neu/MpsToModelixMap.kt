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

package org.modelix.mps.sync.neu

import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelId
import org.jetbrains.mps.openapi.model.SModelReference
import org.jetbrains.mps.openapi.model.SNode
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SModuleReference

// use with caution, otherwise this cache may cause memory leaks
class MpsToModelixMap {

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

    val models = modelixIdToSModel.values
    val modules = modelixIdToSModule.values

    fun put(node: SNode, modelixId: Long) {
        sNodeToModelixId[node] = modelixId
        modelixIdToSNode[modelixId] = node
    }

    fun put(model: SModel, modelixId: Long) {
        sModelToModelixId[model] = modelixId
        modelixIdToSModel[modelixId] = model

        val modelId = model.modelId
        sModelIdToModelixId[modelId] = modelixId
        modelixIdToSModelId[modelixId] = modelId
    }

    fun put(module: SModule, modelixId: Long) {
        sModuleToModelixId[module] = modelixId
        modelixIdToSModule[modelixId] = module
    }

    fun put(moduleReference: SModuleReference, modelixId: Long) {
        sModuleReferenceToModelixId[moduleReference] = modelixId
        modelixIdToSModuleReference[modelixId] = moduleReference
    }

    fun put(modelReference: SModelReference, modelixId: Long) {
        sModelReferenceToModelixId[modelReference] = modelixId
        modelixIdToSModelReference[modelixId] = modelReference
    }

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
}

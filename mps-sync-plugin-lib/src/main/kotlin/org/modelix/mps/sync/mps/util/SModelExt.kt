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

package org.modelix.mps.sync.mps.util

import jetbrains.mps.extapi.model.SModelDescriptorStub
import org.jetbrains.mps.openapi.language.SLanguage
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.module.SModuleReference
import org.modelix.kotlin.utils.UnstableModelixFeature

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun SModel.addDevKit(devKitModuleReference: SModuleReference) {
    require(this is SModelDescriptorStub) { "Model ${this.modelId} must be an SModelDescriptorStub" }
    this.addDevKit(devKitModuleReference)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun SModel.deleteDevKit(devKitModuleReference: SModuleReference) {
    require(this is SModelDescriptorStub) { "Model ${this.modelId} must be an SModelDescriptorStub" }
    this.deleteDevKit(devKitModuleReference)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun SModel.addLanguageImport(sLanguage: SLanguage, version: Int) {
    require(this is SModelDescriptorStub) { "Model ${this.modelId} must be an SModelDescriptorStub" }
    this.addLanguage(sLanguage)
    this.setLanguageImportVersion(sLanguage, version)
}

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
fun SModel.deleteLanguage(sLanguage: SLanguage) {
    require(this is SModelDescriptorStub) { "Model ${this.modelId} must be an SModelDescriptorStub" }
    this.deleteLanguageId(sLanguage)
}

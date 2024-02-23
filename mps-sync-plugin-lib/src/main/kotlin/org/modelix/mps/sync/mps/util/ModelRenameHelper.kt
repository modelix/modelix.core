/*
 * Copyright (c) 2024.
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

import jetbrains.mps.extapi.model.EditableSModelBase
import jetbrains.mps.extapi.module.SModuleBase
import jetbrains.mps.extapi.persistence.FileDataSource
import jetbrains.mps.persistence.DataSourceFactoryNotFoundException
import jetbrains.mps.persistence.DefaultModelRoot
import jetbrains.mps.persistence.NoSourceRootsInModelRootException
import jetbrains.mps.persistence.SourceRootDoesNotExistException
import jetbrains.mps.refactoring.Renamer
import jetbrains.mps.smodel.SModelReference
import jetbrains.mps.smodel.event.SModelFileChangedEvent
import jetbrains.mps.smodel.event.SModelRenamedEvent
import jetbrains.mps.vfs.IFile
import mu.KotlinLogging
import org.jetbrains.mps.openapi.model.SModelName
import org.modelix.kotlin.utils.UnstableModelixFeature

// TODO retest this class in and above MPS 2021.1, because some of its methods might have been removed from their original classes (see origin comments above the methods)
@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelRenameHelper(private val model: EditableSModelBase) {

    val logger = KotlinLogging.logger {}

    fun changeStereotype(stereotype: String?) {
        beforeRename()

        val oldName = model.reference.name.withoutStereotype()
        val newName = SModelName(oldName.value, stereotype)
        rename(newName, false)

        afterRename()
    }

    // adopted from jetbrains.mps.ide.refactoring.RenameModelDialog.renameModel(model, newName)
    fun renameModel(modelName: String) {
        beforeRename()
        rename(SModelName(modelName), model.source is FileDataSource)
        afterRename()
    }

    private fun beforeRename() = model.repository.saveAll()

    private fun afterRename() {
        val modelRepository = model.repository
        Renamer.updateModelAndModuleReferences(modelRepository)
        modelRepository.saveAll()
    }

    // adopted from EditableSModelBase.rename(newModelName, changeFile)
    private fun rename(newModelName: SModelName, changeFile: Boolean) {
        val oldName = model.reference
        fireBeforeModelRenamed(SModelRenamedEvent(model, oldName.modelName, newModelName.value))

        val newModelReference = SModelReference(model.reference.moduleReference, model.reference.modelId, newModelName)
        fireBeforeModelRenamed(newModelReference)
        model.changeModelReference(newModelReference)
        model.isChanged = true

        try {
            if (changeFile) {
                if (model.source !is FileDataSource) {
                    throw UnsupportedOperationException("cannot change model file on non-file data source")
                }

                val oldFile = (model.getSource() as FileDataSource).file
                fireBeforeModelFileChanged(SModelFileChangedEvent(model, oldFile, null as IFile?))
                val root = model.modelRoot
                if (root is DefaultModelRoot) {
                    root.rename(model.source as FileDataSource?, newModelName.value)
                    model.updateTimestamp()
                }

                val newFile = (model.source as FileDataSource).file
                if (oldFile.path != newFile.path) {
                    fireModelFileChanged(SModelFileChangedEvent(model, oldFile, newFile))
                }
            }
        } catch (var8: NoSourceRootsInModelRootException) {
            logger.error(var8) {}
        } catch (var8: SourceRootDoesNotExistException) {
            logger.error(var8) {}
        } catch (var8: DataSourceFactoryNotFoundException) {
            logger.error(var8) {}
        }

        model.save()
        fireModelRenamed(SModelRenamedEvent(model, oldName.modelName, newModelName.value))
        fireModelRenamed(oldName)
    }

    // adopted from EditableSModelBase.fireBeforeModelRenamed(event)
    private fun fireBeforeModelRenamed(event: SModelRenamedEvent) {
        val iterator = model.modelListeners.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().beforeModelRenamed(event)
            } catch (var5: Throwable) {
                logger.error(var5) {}
            }
        }
    }

    // adopted from SModelBase.fireBeforeModelRenamed(newName)
    private fun fireBeforeModelRenamed(newName: SModelReference) {
        val module = model.module
        if (module is SModuleBase) {
            module.fireBeforeModelRenamed(model, newName)
        }
    }

    // adopted from SModelDescriptorStub.fireBeforeModelFileChanged(event)
    private fun fireBeforeModelFileChanged(event: SModelFileChangedEvent) {
        val iterator = model.modelListeners.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().beforeModelFileChanged(event)
            } catch (var5: Throwable) {
                logger.error(var5) {}
            }
        }
    }

    // adopted from SModelDescriptorStub.fireModelFileChanged(event)
    private fun fireModelFileChanged(event: SModelFileChangedEvent) {
        val iterator = model.modelListeners.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().modelFileChanged(event)
            } catch (var5: Throwable) {
                logger.error(var5) {}
            }
        }
    }

    // adopted from SModelDescriptorStub.fireModelFileChanged(event)
    private fun fireModelRenamed(event: SModelRenamedEvent) {
        val iterator = model.modelListeners.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().modelRenamed(event)
            } catch (var5: Throwable) {
                logger.error(var5) {}
            }
        }
    }

    // adopted from SModelBase.fireModelRenamed(newName)
    private fun fireModelRenamed(oldName: org.jetbrains.mps.openapi.model.SModelReference) {
        val module = model.module
        if (module is SModuleBase) {
            module.fireModelRenamed(model, oldName)
        }
    }
}

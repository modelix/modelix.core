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

package org.modelix.mps.sync.actions.node

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.progress.ProgressMonitorAdapter
import jetbrains.mps.smodel.MPSModuleRepository
import org.jetbrains.mps.openapi.module.SModule
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.PNodeAdapter
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.actions.ModelixAction
import org.modelix.mps.sync.actions.getMpsProject
import org.modelix.mps.sync.actions.getTreeNode
import org.modelix.mps.sync.actions.getTreeNodeAs
import org.modelix.mps.sync.actions.util.isProjectNode
import org.modelix.mps.sync.importToCloud.ModelCloudImportUtils
import org.modelix.mps.sync.tools.history.CloudNodeTreeNode
import org.modelix.mps.sync.tools.history.ModelServerTreeNode
import org.modelix.mps.sync.tools.history.RepositoryTreeNode
import javax.swing.Icon

class StoreAllModulesAction : ModelixAction {

    constructor() : super()

    constructor(text: String?, description: String?, icon: Icon?) : super(text, description, icon)

    override fun isApplicable(event: AnActionEvent) = event.getTreeNode()?.isProjectNode() == true

    override fun actionPerformed(event: AnActionEvent) {
        val treeNode = event.getTreeNodeAs<CloudNodeTreeNode>()
        val modelServer = treeNode.getAncestor(ModelServerTreeNode::class.java).modelServer
        val repositoryId = treeNode.getAncestor(RepositoryTreeNode::class.java).repositoryId
        val treeInRepository = CloudRepository(modelServer, repositoryId)
        val cloudProjectId = (treeNode.node as PNodeAdapter).nodeId
        val branch = treeInRepository.getActiveBranch().branch

        val project = event.getMpsProject()
        val projectHelper = ProjectHelper.toIdeaProject(project)
        val task = object : Task.Backgroundable(projectHelper, "Import MPS Repository", true) {

            override fun run(indicator: ProgressIndicator) {
                val progress = ProgressMonitorAdapter(indicator)

                val mpsRepo = MPSModuleRepository.getInstance()
                var mpsModules: MutableList<SModule> = mutableListOf()
                mpsRepo.modelAccess.runReadAction {
                    mpsModules = mpsRepo.modules.toMutableList()
                }

                val nameProperty = BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name
                val modules = BuiltinLanguages.MPSRepositoryConcepts.Project.modules
                val existingModules = branch.computeRead {
                    val transaction = branch.transaction
                    val existingModules =
                        transaction.getChildren(
                            cloudProjectId,
                            modules.getSimpleName(),
                        )
                    existingModules.map {
                        transaction.getProperty(
                            it,
                            nameProperty.getSimpleName(),
                        )
                    }
                }
                mpsModules = mpsModules.filter { !existingModules.contains(it.moduleName) }.toMutableList()
                mpsModules.shuffle()

                progress.start("Importing ${mpsModules.size} Modules", mpsModules.size)
                mpsModules.forEach { mpsModule ->
                    if (progress.isCanceled) {
                        return
                    }
                    while (modelServer.getClient().storeCache.keyValueStore.getPendingSize() > 10000) {
                        if (progress.isCanceled) {
                            break
                        }
                        try {
                            Thread.sleep(1000)
                        } catch (ex: InterruptedException) {
                            break
                        }
                    }
                    progress.step("Importing Module ${mpsModule.moduleName}")
                    branch.runWrite {
                        val transaction = branch.writeTransaction
                        val cloudModuleId = transaction.addNewChild(
                            cloudProjectId,
                            modules.getSimpleName(),
                            -1,
                            BuiltinLanguages.MPSRepositoryConcepts.Module,
                        )

                        transaction.setProperty(cloudModuleId, nameProperty.getSimpleName(), mpsModule.moduleName)
                        ModelCloudImportUtils.replicatePhysicalModule(
                            treeInRepository,
                            PNodeAdapter(
                                cloudModuleId,
                                branch,
                            ),
                            mpsModule,
                            null,
                            progress.subTask(1),
                        )
                    }
                }
            }
        }

        ProgressManager.getInstance().run(task)
    }
}

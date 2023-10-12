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

package org.modelix.mps.sync.exportFromCloud

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import jetbrains.mps.project.Project
import jetbrains.mps.project.Solution
import org.modelix.model.api.PNodeAdapter
import org.modelix.mps.sync.CloudRepository
import javax.swing.SwingUtilities

// status: ready to test
class ModuleCheckout(private val mpsProject: Project, private val treeInRepository: CloudRepository) {

    fun checkoutCloudModule(cloudModule: PNodeAdapter): Solution {
        val modelCloudExporter = ModelCloudExporter(treeInRepository)
        modelCloudExporter.setCheckoutMode()
        val exportPath = mpsProject.projectFile!!.path
        val moduleIds = hashSetOf(cloudModule.nodeId)
        var solutions = listOf<Solution>()

        val runnable = Runnable {
            mpsProject.repository.modelAccess.executeCommand {
                solutions = modelCloudExporter.export(exportPath, moduleIds, mpsProject)
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run()
        } else {
            ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.NON_MODAL)
        }
        check(solutions.size == 1) { "One solution expected. These found: ${solutions.size}" }
        return solutions[0]
    }
}

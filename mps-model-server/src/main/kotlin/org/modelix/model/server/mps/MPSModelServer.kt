/*
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
package org.modelix.model.server.mps

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import jetbrains.mps.project.ProjectBase
import jetbrains.mps.project.ProjectManager
import jetbrains.mps.smodel.MPSModuleRepository
import org.modelix.model.api.INode
import org.modelix.model.api.runSynchronized
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.server.light.LightModelServer

@Service(Service.Level.APP)
class MPSModelServer : Disposable {
    init {
        println("modelix server created")
    }

    private var server: LightModelServer? = null

    fun ensureStarted() {
        runSynchronized(this) {
            if (server != null) return

            println("starting modelix server")

            val rootNodeProvider: () -> INode? = { MPSModuleRepository.getInstance()?.let { MPSRepositoryAsNode(it) } }
            server = LightModelServer.builder()
                .port(48305)
                .rootNode(rootNodeProvider)
                .healthCheck(object : LightModelServer.IHealthCheck {
                    override val id: String
                        get() = "projects"
                    override val enabledByDefault: Boolean
                        get() = false

                    override fun run(output: StringBuilder): Boolean {
                        val projects = ProjectManager.getInstance().openedProjects
                        output.append("${projects.size} projects found")
                        projects.forEach { output.append("  ${it.name}") }
                        return ProjectManager.getInstance().openedProjects.isNotEmpty()
                    }
                })
                .healthCheck(object : LightModelServer.IHealthCheck {
                    override val id: String
                        get() = "virtualFolders"
                    override val enabledByDefault: Boolean
                        get() = false

                    override fun run(output: StringBuilder): Boolean {
                        val projects = ProjectManager.getInstance().openedProjects.filterIsInstance<ProjectBase>()
                        for (project in projects) {
                            val modules = project.projectModules
                            val virtualFolders = modules
                                .mapNotNull { project.getPath(it)?.virtualFolder }
                                .filter { it.isNotEmpty() }
                            output.append("project ${project.name} contains ${modules.size} modules with ${virtualFolders.size} virtual folders")
                            if (virtualFolders.isNotEmpty()) return true
                        }
                        return false
                    }
                })
                .build()
            server!!.start()
        }
    }

    fun ensureStopped() {
        runSynchronized(this) {
            if (server == null) return
            println("stopping modelix server")
            server?.stop()
            server = null
        }
    }

    override fun dispose() {
        ensureStopped()
    }
}

class MPSModelServerDynamicPluginListener : DynamicPluginListener {
    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        service<MPSModelServer>().ensureStarted()
    }
}

class MPSModelServerAppLifecycleListener : AppLifecycleListener {
    override fun appStarting(projectFromCommandLine: Project?) {
        service<MPSModelServer>().ensureStarted()
    }

    override fun appStarted() {
        service<MPSModelServer>().ensureStarted()
    }
}

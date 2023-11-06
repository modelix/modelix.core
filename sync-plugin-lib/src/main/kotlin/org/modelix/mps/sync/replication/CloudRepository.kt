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

package org.modelix.mps.sync.replication

import jetbrains.mps.project.MPSProject
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getConcept
import org.modelix.model.area.PArea
import org.modelix.model.client.ActiveBranch
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.mps.NodeAsMPSNode
import org.modelix.mps.sync.binding.Binding
import org.modelix.mps.sync.binding.ProjectBinding
import org.modelix.mps.sync.binding.RootBinding
import org.modelix.mps.sync.connection.ModelServerConnectionInterface
import org.modelix.mps.sync.connection.ModelServerConnections
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.transient.TransientModuleBinding
import org.modelix.mps.sync.util.createModuleInRepository
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.function.Consumer

// status: ready to test
class CloudRepository(public val modelServer: ModelServerConnectionInterface, private val repositoryId: RepositoryId) :
    ICloudRepository {

    companion object {
        fun fromPresentationString(presentation: String): CloudRepository {
            val lastSlash: Int = presentation.lastIndexOf("/")
            val url = presentation.substring(0, lastSlash)
            val repositoryId = RepositoryId(presentation.substring(lastSlash + 1))
            val modelServer = ModelServerConnections.instance.ensureModelServerIsPresent(url)
            return CloudRepository(modelServer, repositoryId)
        }
    }

    override fun getBranch() = getActiveBranch().branch

    override fun getActiveBranch(): ActiveBranch = modelServer.getActiveBranch(repositoryId)

    fun isConnected(): Boolean = modelServer.isConnected()

    override fun getRepositoryId(): RepositoryId = repositoryId

    override fun completeId(): String {
        return if (modelServer.baseUrl.endsWith("/")) {
            modelServer.baseUrl + repositoryId
        } else {
            modelServer.baseUrl + "/" + repositoryId
        }
    }

    fun <T> computeRead(producer: () -> T): T {
        return modelServer.getInfoBranch().computeRead {
            val activeBranch = modelServer.getActiveBranch(repositoryId)
            val branch = activeBranch.branch
            PArea(branch).executeRead { producer.invoke() }
        }
    }

    fun runRead(r: Runnable) {
        PArea(modelServer.getInfoBranch()).executeRead {
            val activeBranch = modelServer.getActiveBranch(repositoryId)
            val branch = activeBranch.branch
            PArea(branch).executeRead {
                r.run()
            }
        }
    }

    /**
     *  Consumer receives the root node
     */
    fun runRead(consumer: Consumer<PNodeAdapter>) {
        val activeBranch = modelServer.getActiveBranch(repositoryId)
        val branch = activeBranch.branch
        val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
        PArea(branch).executeRead {
            consumer.accept(rootNode)
        }
    }

    /**
     *  computer receives the root node
     */
    fun <T> computeWrite(computer: (PNodeAdapter) -> T): T {
        val activeBranch = modelServer.getActiveBranch(repositoryId)
        val branch = activeBranch.branch
        val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
        return PArea(branch).executeWrite { computer.invoke(rootNode) }
    }

    /**
     *  Consumer receives the root node
     */
    fun runWrite(consumer: Consumer<PNodeAdapter>) {
        val activeBranch = modelServer.getActiveBranch(repositoryId)
        val branch = activeBranch.branch
        val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
        PArea(branch).executeWrite {
            consumer.accept(rootNode)
        }
    }

    fun processProjects(consumer: Consumer<Any>) {
        processRepoRoots { iNode ->
            val sNode = NodeAsMPSNode.wrap(iNode)!!
            if (iNode.getConcept() is BuiltinLanguages.MPSRepositoryConcepts.Project) {
                consumer.accept(sNode)
            }
        }
    }

    fun repoRoots(): List<INode> {
        val roots = mutableListOf<INode>()
        processRepoRoots { roots.add(it) }
        return roots
    }

    fun processRepoRoots(consumer: Consumer<INode>) {
        PArea(modelServer.getInfoBranch()).executeRead {
            val activeBranch = modelServer.getActiveBranch(repositoryId)
            val branch = activeBranch.branch
            val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
            PArea(branch).executeRead {
                rootNode.allChildren.forEach { child ->
                    consumer.accept(child)
                }
            }
        }
    }

    fun getReadTransaction(): IReadTransaction = getActiveBranch().branch.readTransaction

    fun getRootBinding(): RootBinding = modelServer.getRootBinding(repositoryId)

    fun addProjectBinding(nodeId: Long, project: MPSProject, initialSyncDirection: SyncDirection): ProjectBinding {
        val binding = ProjectBinding(project, nodeId, initialSyncDirection)
        addBinding(binding)
        return binding
    }

    fun addTransientModuleBinding(node: INode) = addBinding(TransientModuleBinding((node as PNodeAdapter).nodeId))

    fun addBinding(binding: Binding) = modelServer.addBinding(repositoryId, binding)

    fun deleteRoot(root: INode) {
        PArea(modelServer.getInfoBranch()).executeWrite {
            val activeBranch = modelServer.getActiveBranch(repositoryId)
            val branch = activeBranch.branch
            val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
            PArea(branch).executeWrite {
                rootNode.removeChild(root)
            }
        }
    }

    fun createProject(name: String) =
        computeWrite { rootNode ->
            val newProject = rootNode.addNewChild(
                BuiltinLanguages.MPSRepositoryConcepts.Repository.projects,
                -1,
                BuiltinLanguages.MPSRepositoryConcepts.Project,
            )
            newProject.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, name)
            newProject
        }

    fun getProject(name: String) =
        computeRead {
            var project: INode? = null
            val activeBranch = modelServer.getActiveBranch(repositoryId)
            val branch = activeBranch.branch
            val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
            rootNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.projects).forEach { child ->
                val projectName = child.getPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name)
                if (projectName == name) {
                    project = child
                }
            }
            project
        }

    fun hasModuleUnderProject(projectNodeId: Long, moduleId: String) =
        computeRead {
            val activeBranch = modelServer.getActiveBranch(repositoryId)
            val branch = activeBranch.branch
            val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
            val projectNode = PNodeAdapter(projectNodeId, rootNode.branch)

            projectNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Project.modules)
                .any { it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) == moduleId }
        }

    fun hasModuleInRepository(moduleId: String) =
        computeRead {
            val activeBranch = modelServer.getActiveBranch(repositoryId)
            val branch = activeBranch.branch
            val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)

            rootNode.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Project.modules)
                .any { it.getPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id) == moduleId }
        }

    fun createModuleUnderProject(projectNodeId: Long, moduleId: String, moduleName: String) =
        computeWrite { rootNode ->
            val projectNode = PNodeAdapter(projectNodeId, rootNode.branch)
            val newModule = projectNode.addNewChild(
                BuiltinLanguages.MPSRepositoryConcepts.Project.modules,
                -1,
                BuiltinLanguages.MPSRepositoryConcepts.Module,
            )
            newModule.setPropertyValue(BuiltinLanguages.MPSRepositoryConcepts.Module.id, moduleId)
            newModule.setPropertyValue(BuiltinLanguages.jetbrains_mps_lang_core.INamedConcept.name, moduleName)
            newModule
        }

    fun createModuleUnderProject(cloudModule: INode, moduleId: String, moduleName: String) =
        createModuleUnderProject(cloudModule.nodeIdAsLong(), moduleId, moduleName)

    fun createNode(
        parent: INode,
        containmentLink: IChildLink,
        concept: IConcept,
        initializer: Consumer<INode>,
    ) = computeWrite {
        val newNode = parent.addNewChild(containmentLink, -1, concept)
        initializer.accept(newNode)
        newNode
    }

    fun createModule(moduleName: String) =
        this.computeWrite { rootNode -> rootNode.createModuleInRepository(moduleName) }

    override fun hashCode() = modelServer.hashCode() + 7 * repositoryId.hashCode()

    override fun equals(other: Any?) =
        if (other is CloudRepository) {
            this.modelServer == other.modelServer && this.repositoryId == other.repositoryId
        } else {
            false
        }
}

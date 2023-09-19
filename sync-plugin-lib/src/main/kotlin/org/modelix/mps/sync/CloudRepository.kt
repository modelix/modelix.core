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

package org.modelix.mps.sync

import jetbrains.mps.project.MPSProject
import org.jetbrains.mps.openapi.language.SConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PropertyFromName
import org.modelix.model.area.PArea
import org.modelix.model.client.ActiveBranch
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.sync.binding.Binding
import org.modelix.mps.sync.binding.ProjectBinding
import org.modelix.mps.sync.binding.RootBinding
import org.modelix.mps.sync.connection.ModelServerConnection
import org.modelix.mps.sync.connection.ModelServerConnections
import org.modelix.mps.sync.synchronization.SyncDirection
import org.modelix.mps.sync.transient.TransientModuleBinding
import org.modelix.mps.sync.util.createModuleInRepository
import org.modelix.mps.sync.util.nodeIdAsLong
import java.util.function.Consumer

// status: migrated, but needs some bugfixes
class CloudRepository(public val modelServer: ModelServerConnection, private val repositoryId: RepositoryId) :
    ICloudRepository {

    companion object {
        fun fromPresentationString(presentation: String): CloudRepository {
            val lastSlash: Int = presentation.lastIndexOf("/")
            val url = presentation.substring(0, lastSlash)
            val repositoryId = RepositoryId(presentation.substring(lastSlash + 1))
            val modelServer = ModelServerConnections.getInstance().ensureModelServerIsPresent(url)
            return CloudRepository(modelServer, repositoryId)
        }
    }

    override fun getBranch() = getActiveBranch().branch

    override fun getActiveBranch(): ActiveBranch = modelServer.getActiveBranch(repositoryId)

    fun isConnected(): Boolean = modelServer.isConnected()

    override fun getRepositoryId(): RepositoryId = repositoryId

    override fun completeId(): String {
        return if (modelServer.baseUrl.toString().endsWith("/")) {
            modelServer.baseUrl.toString() + repositoryId
        } else {
            modelServer.baseUrl.toString() + "/" + repositoryId
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

    // TODO fixme. Consumer's generics type must be org.modelix.model.repositoryconcepts.Project instead of Any
    fun processProjects(consumer: Consumer<Any>) {
        processRepoRoots { iNode ->
            // TODO fixme. org.modelix.model.mpsadapters.mps.NodeToSNodeAdapter is not found...
            // NodeToSNodeAdapter.wrap(iNode)
            val sNode: SNode = null!!

            // TODO fixme. org.modelix.model.repositoryconcepts.Project must be used instead of Any
            if (sNode is Any) {
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

    fun createProject(name: String): INode {
        return computeWrite { rootNode ->
            // TODO instead of "projects" it must be link/Repository : projects/.getName()
            // TODO fix parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
            // TODO  Project must be org.modelix.model.repositoryconcepts.Project
            // rootNode.addNewChild("projects", -1, SConceptAdapter.wrap(concept/Project/));
            val newProject: INode? = null!!

            // TODO instead of "name" it must be property/Project : name/.getName()
            val nameProperty = PropertyFromName("name")
            newProject!!.setPropertyValue(nameProperty, name)
            newProject
        }
    }

    fun getProject(name: String): INode? {
        return computeRead {
            var project: INode? = null
            val activeBranch = modelServer.getActiveBranch(repositoryId)
            val branch = activeBranch.branch
            val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
            // TODO instead of "projects" it must be link/Repository : projects/.getName()
            rootNode.getChildren("projects").forEach { child ->
                // TODO instead of "name" it must be property/Project : name/.getName()
                val nameProperty = PropertyFromName("name")
                val projectName = child.getPropertyValue(nameProperty)
                if (projectName == name) {
                    project = child
                }
            }
            project
        }
    }

    fun hasModuleUnderProject(projectNodeId: Long, moduleId: String): Boolean {
        return computeRead {
            val activeBranch = modelServer.getActiveBranch(repositoryId)
            val branch = activeBranch.branch
            val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)
            val projectNode = PNodeAdapter(projectNodeId, rootNode.branch)

            // TODO instead of "id" it must be property/Module : id/.getName()
            val idProperty = PropertyFromName("id")
            // TODO instead of "modules" it must be link/Project : modules/.getName()
            projectNode.getChildren("modules").any { it.getPropertyValue(idProperty) == moduleId }
        }
    }

    fun hasModuleInRepository(moduleId: String): Boolean {
        return computeRead {
            val activeBranch = modelServer.getActiveBranch(repositoryId)
            val branch = activeBranch.branch
            val rootNode = PNodeAdapter(ITree.ROOT_ID, branch)

            // TODO instead of "id" it must be property/Module : id/.getName()
            val idProperty = PropertyFromName("id")
            // TODO instead of "modules" it must be link/Repository : modules/.getName()
            rootNode.getChildren("modules").any { it.getPropertyValue(idProperty) == moduleId }
        }
    }

    fun createModuleUnderProject(projectNodeId: Long, moduleId: String, moduleName: String): INode {
        return computeWrite { rootNode ->
            val projectNode = PNodeAdapter(projectNodeId, rootNode.branch)
            // TODO instead of "modules" it must be link/Project : modules/.getName()
            // TODO fix parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
            // projectNode.addNewChild("modules", -1, SConceptAdapter.wrap(concept/Module/));
            val newModule: INode? = null!!
            // TODO instead of "id" it must be property/Module : id/.getName()
            val idProperty = PropertyFromName("id")
            newModule!!.setPropertyValue(idProperty, moduleId)
            // TODO instead of "name" it must be property/Module : name/.getName()
            val nameProperty = PropertyFromName("name")
            newModule.setPropertyValue(nameProperty, moduleName)
            newModule
        }
    }

    fun createModuleUnderProject(cloudModule: INode, moduleId: String, moduleName: String) =
        createModuleUnderProject(cloudModule.nodeIdAsLong(), moduleId, moduleName)

    fun createNode(
        parent: INode,
        containmentLink: SContainmentLink,
        concept: IConcept,
        initializer: Consumer<INode>,
    ): INode {
        return computeWrite {
            val newNode = parent.addNewChild(containmentLink.name, -1, concept)
            initializer.accept(newNode)
            newNode
        }
    }

    fun createNode(
        parent: INode,
        containmentLink: SContainmentLink,
        concept: SConcept,
        initializer: Consumer<INode>,
    ): INode {
        // TODO fix parameter. Problem SConceptAdapter.wrap does not exist anymore in modelix...
        // return createNode(parent, containmentLink, SConceptAdapter.wrap(concept), initializer)
        return null!!
    }

    fun createModule(moduleName: String) =
        this.computeWrite { rootNode -> rootNode.createModuleInRepository(moduleName) }

    override fun hashCode(): Int = modelServer.hashCode() + 7 * repositoryId.hashCode()

    override fun equals(other: Any?): Boolean {
        return if (other is CloudRepository) {
            this.modelServer == other.modelServer && this.repositoryId == other.repositoryId
        } else {
            false
        }
    }
}

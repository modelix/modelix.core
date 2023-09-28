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
import com.intellij.openapi.vfs.VirtualFileManager
import jetbrains.mps.extapi.model.SModelData
import jetbrains.mps.ide.MPSCoreComponents
import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.persistence.DefaultModelPersistence
import jetbrains.mps.persistence.DefaultModelRoot
import jetbrains.mps.persistence.ModelCannotBeCreatedException
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.project.ModuleId
import jetbrains.mps.project.Project
import jetbrains.mps.project.Solution
import jetbrains.mps.project.structure.modules.SolutionDescriptor
import jetbrains.mps.project.structure.modules.SolutionKind
import jetbrains.mps.smodel.DefaultSModel
import jetbrains.mps.smodel.DefaultSModelDescriptor
import jetbrains.mps.smodel.GeneralModuleFactory
import jetbrains.mps.smodel.SModelHeader
import jetbrains.mps.smodel.SModelId
import jetbrains.mps.smodel.persistence.def.ModelPersistence
import jetbrains.mps.vfs.IFile
import jetbrains.mps.vfs.VFSManager
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.model.SModel
import org.jetbrains.mps.openapi.model.SModelName
import org.jetbrains.mps.openapi.persistence.DataSource
import org.jetbrains.mps.openapi.persistence.ModelFactory
import org.jetbrains.mps.openapi.persistence.ModelLoadingOption
import org.jetbrains.mps.openapi.persistence.ModelRoot
import org.jetbrains.mps.openapi.persistence.ModelSaveException
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.jetbrains.mps.openapi.persistence.StreamDataSource
import org.jetbrains.mps.openapi.persistence.UnsupportedDataSourceException
import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.INode
import org.modelix.model.api.ITree
import org.modelix.model.api.IdGeneratorDummy
import org.modelix.model.api.PBranch
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.PropertyFromName
import org.modelix.model.area.PArea
import org.modelix.model.client.RestWebModelClient
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.PrefetchCache
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.metameta.MetaModelBranch
import org.modelix.mps.sync.CloudRepository
import org.modelix.mps.sync.ModelPersistenceFacility
import org.modelix.mps.sync.ModelPersistenceWithFixedId
import org.modelix.mps.sync.binding.ModelSynchronizer
import org.modelix.mps.sync.connection.ModelServerConnections
import org.modelix.mps.sync.exportFromCloud.ModelCloudExporter.PersistenceFacility
import org.modelix.mps.sync.util.ModelixNotifications.notifyError
import java.io.File
import java.io.IOException
import java.util.UUID

// status: ready to test
class ModelCloudExporter {

    companion object {
        private val DEFAULT_BRANCH_NAME = "master"
        private val DEFAULT_URL = "http://localhost:28101/"
        private val DEFAULT_TREE_ID = "default"
    }

    private val logger = mu.KotlinLogging.logger {}
    private var branchName = DEFAULT_BRANCH_NAME
    private var inCheckoutMode = false

    private lateinit var repositoryInModelServer: CloudRepository

    constructor(url: String, repositoryId: String, branchName: String) {
        var preparedUrl = url.ifEmpty { DEFAULT_URL }
        if (!preparedUrl.endsWith("/")) {
            preparedUrl += "/"
        }
        val preparedRepositoryId = repositoryId.ifEmpty { DEFAULT_TREE_ID }

        val modelServer = ModelServerConnections.instance.getModelServer(preparedUrl)
        check(modelServer != null) { "No ModelServer connection found for url $url" }

        init(CloudRepository(modelServer, RepositoryId(preparedRepositoryId)), branchName)
    }

    constructor(tree: CloudRepository, branchName: String = DEFAULT_BRANCH_NAME) {
        init(tree, branchName)
    }

    constructor(tree: CloudRepository) : this(tree, tree.getActiveBranch().branchName)

    private fun init(tree: CloudRepository, branchName: String) {
        this.repositoryInModelServer = tree
        this.branchName = branchName.ifEmpty {
            DEFAULT_BRANCH_NAME
        }
    }

    fun setCheckoutMode(): ModelCloudExporter {
        inCheckoutMode = true
        return this
    }

    fun export(exportPath: String, mpsProject: Project): List<Solution> = export(exportPath, null, mpsProject)

    /**
     * @param selectedMduleIds null indicates all modules
     */
    fun export(
        exportPath: String,
        selectedMduleIds: Set<Long>?,
        mpsProject: Project,
    ): List<Solution> {
        val coreComponents = ApplicationManager.getApplication().getComponent(
            MPSCoreComponents::class.java,
        )
        val vfsManager = coreComponents.platform.findComponent(
            VFSManager::class.java,
        )
        val fileSystem = vfsManager!!.getFileSystem(VFSManager.FILE_FS)
        val outputFolder: IFile = fileSystem.getFile(exportPath)
        return export(outputFolder, selectedMduleIds, mpsProject)
    }

    /**
     * This method is expected to be called when a user is present to see the notifications.
     *
     * @param selectedModuleIds null indicates all modules
     */
    fun export(outputFolder: IFile, selectedModuleIds: Set<Long>?, mpsProject: Project): List<Solution> {
        logger.info { "exporting to ${outputFolder.path}" }
        logger.info { "the output folder exists? ${outputFolder.exists()}" }

        if (!inCheckoutMode) {
            outputFolder.deleteIfExists()
        }

        val url = repositoryInModelServer.modelServer.baseUrl
        val client = RestWebModelClient(url)
        val repositoryId = repositoryInModelServer.getRepositoryId()
        val branchKey = repositoryId.getBranchReference(branchName).getKey()
        val versionHash = client[branchKey]
        check(!versionHash.isNullOrEmpty()) { "No version found at ${url}get/$branchKey" }
        val version = CLVersion.Companion.loadFromHash(versionHash, client.storeCache)
        return repositoryInModelServer.computeRead {
            val tree = version.getTree()
            val branch = MetaModelBranch(PBranch(tree, IdGeneratorDummy()))
            PArea(branch).executeRead {
                PrefetchCache.Companion.with(tree) {
                    val transaction = branch.transaction
                    var moduleIds = transaction.getChildren(
                        ITree.ROOT_ID,
                        BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.getSimpleName(),
                    )
                    if (selectedModuleIds != null) {
                        moduleIds = moduleIds.intersect(selectedModuleIds)
                    }

                    // prefetch module contents
                    tree.getDescendants(moduleIds, true)

                    val modules = moduleIds.map { PNodeAdapter(it, branch) }
                    createModules(modules, outputFolder, mpsProject)
                }
            }
        }
    }

    /**
     * This method is expected to be called when a user is present to see the notifications.
     */
    private fun createModules(modules: List<INode>, outputFolder: IFile, mpsProject: Project): List<Solution> {
        val solutions = mutableListOf<Solution>()
        modules.forEach { module ->
            val solution = createModule(module, outputFolder, mpsProject)
            if (solution != null) {
                solutions.add(solution)
            }
        }
        return solutions
    }

    /**
     * We experienced issues with physical and virtual files being out of sync.
     * This method ensure that files are deleted, recursively both on the virtual filesystem and the physical filesystem.
     */
    private fun ensureDeletion(virtualFile: IFile) {
        if (virtualFile.isDirectory) {
            virtualFile.children?.forEach { child ->
                ensureDeletion(child)
            }
        } else {
            if (virtualFile.exists()) {
                virtualFile.delete()
            }
            val physicalFile = File(virtualFile.path)
            physicalFile.delete()
        }
    }

    private fun ensureDirDeletionAndRecreation(virtualDir: IFile) {
        ensureDeletion(virtualDir)
        virtualDir.mkdirs()
    }

    /**
     * This method is expected to be called when a user is present to see the notification.
     */
    private fun createModule(module: INode, outputFolder: IFile, mpsProject: Project): Solution? {
        val nameProperty = PropertyFromName("name")
        val name = module.getPropertyValue(nameProperty)
        if (name == null) {
            notifyError(
                "Module without name",
                "Module's name is null. Please set the name and check it out again",
            )
            return null
        }

        val idProperty = PropertyFromName("id")
        val moduleIdAsString = module.getPropertyValue(idProperty)
        if (moduleIdAsString == null) {
            notifyError(
                "Module without ID",
                "Module $name has been stored without an ID. Please set the ID and check it out again",
            )
            return null
        }

        val coreComponents = ApplicationManager.getApplication().getComponent(
            MPSCoreComponents::class.java,
        )
        val vfsManager = coreComponents.platform.findComponent(
            VFSManager::class.java,
        )
        // why we need this call?
        vfsManager!!.getFileSystem(VFSManager.FILE_FS)
        if (!inCheckoutMode) {
            outputFolder.deleteIfExists()
        }
        val solutionFile = outputFolder.findChild(name).findChild("solution" + MPSExtentions.DOT_SOLUTION)
        val solutionDir = outputFolder.findChild(name)
        if (inCheckoutMode) {
            ApplicationManager.getApplication().invokeAndWait {
                VirtualFileManager.getInstance().syncRefresh()
                val modelsDirVirtual = solutionDir.findChild("models")
                ensureDirDeletionAndRecreation(modelsDirVirtual)
            }
        }
        val descriptor = SolutionDescriptor()
        descriptor.namespace = name
        val solutionId = ModuleId.regular(UUID.fromString(moduleIdAsString))
        descriptor.id = solutionId
        descriptor.modelRootDescriptors.add(
            DefaultModelRoot.createDescriptor(
                solutionFile.parent!!,
                solutionFile.parent!!
                    .findChild(Solution.SOLUTION_MODELS),
            ),
        )
        descriptor.setKind(SolutionKind.PLUGIN_OTHER)
        val solution = GeneralModuleFactory().instantiate(descriptor, solutionFile) as Solution
        mpsProject.addModule(solution)
        check(solution.repository != null) { "The solution should be in a repo, so also the model will be in a repo and syncReference will not crash" }
        for (model in module.getChildren("models")) {
            createModel(solution, model)
        }
        solution.save()

        return solution
    }

    private fun createModel(module: AbstractModule, model: INode) {
        val modelRootsIt: Iterator<ModelRoot> = module.getModelRoots().iterator()
        check(modelRootsIt.hasNext()) { "Module has not default model root: $module (${module.moduleName})" }
        val defaultModelRoot = modelRootsIt.next() as DefaultModelRoot

        val nameProperty = PropertyFromName("name")
        val sModelName = SModelName(model.getPropertyValue(nameProperty)!!)

        val idProperty = PropertyFromName("id")
        val imposedModelID = SModelId.fromString(model.getPropertyValue(idProperty))
        val modelFactory: ModelFactory = object : ModelPersistenceWithFixedId(module.moduleReference, imposedModelID) {

            @Throws(UnsupportedDataSourceException::class)
            override fun create(
                dataSource: DataSource,
                modelName: SModelName,
                vararg options: ModelLoadingOption,
            ): SModel {
                // COPIED FROM https://github.com/JetBrains/MPS/blob/14b86a2f987cdd3fbcc72b9262e8b388f7a5fae3/core/persistence/source/jetbrains/mps/persistence/DefaultModelPersistence.java#L115
                if (!supports(dataSource)) {
                    throw UnsupportedDataSourceException(dataSource)
                }
                val header = SModelHeader.create(ModelPersistence.LAST_VERSION)
                val modelReference =
                    PersistenceFacade.getInstance().createModelReference(null, imposedModelID, modelName.value)
                header.modelReference = modelReference
                val rv = DefaultSModelDescriptor(PersistenceFacility(this, dataSource as StreamDataSource), header)
                // Hack to ensure newly created model is indeed empty. Otherwise, with StreamDataSource pointing to existing model stream, an attempt to
                // do anything with the model triggers loading and the model get all the data. Two approaches deemed reasonable to tackle the issue:
                // (a) enforce clear empty model (why would anyone call #create() then)
                // (b) fail with error (too brutal?)
                // Another alternative considered is to tolerate any DataSource in DefaultSModelDescriptor (or its persistence counterpart), so that
                // one can create an empty model with NullDataSource, and later save with a proper DataSource (which yields more job to client and makes him
                // question why SModel.save() is there). This task is reasonable regardless of final approach taken, but would take more effort, hence the hack.
                if (dataSource.getTimestamp().toInt() != -1) {
                    // chances are there's something in the stream already
                    rv.replace(DefaultSModel(modelReference, header))
                    // model state is FULLY_LOADED, DataSource won't get read
                }
                return rv
            }
        }

        // We create models asynchronously, similarly to what is done in mpsutil.smodule
        // this helps avoid issues with VFS and physical FS being out of sync
        VirtualFileManager.getInstance().syncRefresh()
        val res = AsyncPromise<EditableSModel>()
        ThreadUtils.runInUIThreadNoWait {
            try {
                logger.info { "creating model $sModelName" }
                val smodel = defaultModelRoot.createModel(sModelName, null, null, modelFactory) as EditableSModel
                logger.info { "  model $sModelName created" }
                res.setResult(smodel)
            } catch (ex: ModelCannotBeCreatedException) {
                res.setResult(null)
                throw RuntimeException(ex)
            }
        }

        val smodel = res.get()
        if (smodel != null) {
            ModelSynchronizer(
                (model as PNodeAdapter).nodeId,
                smodel,
                repositoryInModelServer,
            ).syncModelToMPS(model.branch.transaction.tree, true)
            module.repository?.modelAccess?.runWriteAction {
                smodel.save()
                logger.info { "  model $sModelName saved" }
            }
        }
    }

    /**
     * We had to copy it from https://github.com/JetBrains/MPS/blob/14b86a2f987cdd3fbcc72b9262e8b388f7a5fae3/core/persistence/source/jetbrains/mps/persistence/DefaultModelPersistence.java#L115
     */
    private class PersistenceFacility(modelFactory: DefaultModelPersistence, dataSource: StreamDataSource) :
        ModelPersistenceFacility(modelFactory, dataSource) {

        @Throws(IOException::class)
        override fun saveModel(header: SModelHeader, modelData: SModelData) {
            val res = AsyncPromise<Boolean>()
            ThreadUtils.runInUIThreadNoWait {
                try {
                    ModelPersistence.saveModel(
                        modelData as jetbrains.mps.smodel.SModel,
                        source0,
                        header.persistenceVersion,
                    )
                    res.setResult(true)
                } catch (ex: ModelSaveException) {
                    ex.printStackTrace()
                    res.setResult(false)
                }
            }
            if (!res.get()!!) {
                throw RuntimeException("Unable to save model")
            }
        }
    }
}

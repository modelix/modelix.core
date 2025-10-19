package org.modelix.mps.gitimport

import jetbrains.mps.extapi.persistence.datasource.DataSourceFactoryRuleService
import jetbrains.mps.ide.MPSCoreComponents
import jetbrains.mps.library.ModulesMiner
import jetbrains.mps.project.AbstractModule
import jetbrains.mps.project.persistence.ProjectDescriptorPersistence
import jetbrains.mps.project.structure.project.ModulePath
import jetbrains.mps.project.structure.project.ProjectDescriptor
import jetbrains.mps.smodel.GeneralModuleFactory
import jetbrains.mps.smodel.Language
import jetbrains.mps.util.MacrosFactory
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.jetbrains.mps.openapi.model.EditableSModel
import org.modelix.model.IVersion
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.historyAsSequence
import org.modelix.model.lazy.BranchReference
import org.modelix.model.mpsadapters.MPSProjectAsNode
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.mutable.asReadOnlyModel
import org.modelix.model.sync.bulk.FullSyncFilter
import org.modelix.model.sync.bulk.IdentityPreservingNodeAssociation
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.model.sync.bulk.UnfilteredModelMask
import org.modelix.mps.api.ModelixMpsApi
import org.modelix.mps.gitimport.fs.MutableGitFS
import java.io.File
import kotlin.system.exitProcess

class GitExporter(
    val gitDir: File,
    val modelServerUrl: String,
    val modelixBranch: BranchReference,
    val modelixVersionHash: String?,
    val gitBranch: String? = null,
    val token: String? = null,
) {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun runSuspending() {
        val client = ModelClientV2.builder().url(modelServerUrl).lazyAndBlockingQueries().also {
            if (token != null) it.authToken { token }
        }.build()
        client.init()

        val modelixVersion = if (modelixVersionHash == null) {
            client.pull(modelixBranch, null, ObjectDeltaFilter(includeHistory = false, includeOperations = false))
        } else {
            client.loadVersion(modelixBranch.repositoryId, modelixVersionHash, null)
        }

        println("Modelix version: ${modelixVersion.getObjectHash()}")

        val gitBranch = gitBranch
            ?: "modelix-export/${modelixBranch.repositoryId}/${modelixBranch.branchName}/${modelixVersion.getObjectHash()}"

        println()
        println()
        println("Running Git export from $modelServerUrl into $gitDir, $gitBranch")

        val git: Git = Git.open(gitDir)

        println("Searching for last imported git commit")
        val lastImportedVersion = requireNotNull(modelixVersion.historyAsSequence().find { it.gitCommit != null }) {
            "No Git imports found"
        }
        val resolvedParentCommit = git.repository.parseCommit(git.repository.resolve(lastImportedVersion.gitCommit))

        println("Starting export")
        runExport(
            modelixVersion = modelixVersion,
            gitParentCommit = resolvedParentCommit,
            gitRepo = git.repository,
            gitBranchName = gitBranch,
            mpsRepo = DummyRepo(),
        )

        println("Export done")
    }

    private fun runExport(modelixVersion: IVersion, gitParentCommit: RevCommit, gitRepo: Repository, gitBranchName: String, mpsRepo: DummyRepo) {
        val gitFS = MutableGitFS(gitCommit = gitParentCommit, gitRepo = gitRepo)
        val rootDir = gitFS.root

        MPSCoreComponents.getInstance().platform.findComponent(DataSourceFactoryRuleService::class.java)!!
            .register(GitDataSourceFactoryRule(gitFS))

        val projectDirs = GitSyncUtils.collectProjectDirs(rootDir)
        println("Project directories: $projectDirs")

        val miner = ModulesMiner(MPSCoreComponents.getInstance().platform)
        miner.collectModules(rootDir)

        for (collectedModule in miner.collectedModules) {
            val id = collectedModule.descriptor?.id ?: continue
            if (mpsRepo.getModule(id) != null) continue
            val mf = GeneralModuleFactory()
            val module = mf.instantiate(collectedModule.descriptor ?: continue, collectedModule.file)
            mpsRepo.register(module)
        }

        val targetRoot = MPSRepositoryAsNode(mpsRepo)
        val sourceRoot = modelixVersion.getModelTree().asReadOnlyModel().getRootNode()

        val mpsProjects = projectDirs.map { DummyMPSProject(mpsRepo, it) }
        MPSProjectAsNode.runWithProjects(mpsProjects) {
            ModelSynchronizer(
                filter = FullSyncFilter(),
                sourceRoot = sourceRoot,
                targetRoot = targetRoot,
                nodeAssociation = IdentityPreservingNodeAssociation(
                    targetRoot.getModel(),
                    mapOf(sourceRoot.getNodeReference() to targetRoot.getNodeReference()),
                ),
                sourceMask = UnfilteredModelMask(),
                targetMask = UnfilteredModelMask(),
                onException = {
                    it.printStackTrace()
                    exitProcess(1)
                },
            ).synchronize()
        }

        saveAll(mpsRepo, mpsProjects)

        val commitId = gitFS.createCommit(
            message = "modelix export ${modelixVersion.getObjectHash()}",
            author = modelixVersion.getAuthor(),
        )
        println("Git commit created: ${commitId.name}")
        gitRepo.refDatabase.newUpdate("refs/heads/$gitBranchName", false).also { it.setNewObjectId(commitId) }.update()

        println("Imported ${modelixVersion.getObjectHash()} into $gitBranchName")
    }

    private fun saveAll(mpsRepo: DummyRepo, mpsProjects: List<DummyMPSProject>) {
        val allModules = mpsRepo.modules.flatMap {
            listOf(it) + ((it as? Language)?.generators ?: emptyList())
        }.distinct()
        for (module in allModules) {
            module as AbstractModule
            module.save()
            for (model in module.models.filterIsInstance<EditableSModel>()) {
                if (model.isReadOnly) continue
                ModelixMpsApi.forceSave(model)
            }
        }

        for (mpsProject in mpsProjects) {
            val descriptor = ProjectDescriptor("unknown")
            for (module in mpsProject.getModules()) {
                val modulePath = checkNotNull((module as AbstractModule).descriptorFile) {
                    "$module has no path"
                }
                descriptor.addModulePath(ModulePath(modulePath, mpsProject.getVirtualFolder(module)))
            }
            ProjectDescriptorPersistence(
                mpsProject.projectDir,
                MacrosFactory.forProjectFile(mpsProject.projectDir),
            ).save(descriptor)
        }
    }
}

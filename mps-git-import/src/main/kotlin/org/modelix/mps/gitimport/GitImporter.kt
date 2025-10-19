package org.modelix.mps.gitimport

import jetbrains.mps.extapi.persistence.datasource.DataSourceFactoryRuleService
import jetbrains.mps.ide.MPSCoreComponents
import jetbrains.mps.library.ModulesMiner
import jetbrains.mps.project.MPSExtentions
import jetbrains.mps.project.io.DescriptorIO
import jetbrains.mps.project.io.DescriptorIOFacade
import jetbrains.mps.project.structure.modules.LanguageDescriptor
import jetbrains.mps.project.structure.modules.ModuleDescriptor
import jetbrains.mps.smodel.GeneralModuleFactory
import jetbrains.mps.vfs.IFile
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toKotlinInstant
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.treewalk.TreeWalk
import org.modelix.datastructures.objects.ObjectHash
import org.modelix.model.IVersion
import org.modelix.model.api.INodeReference
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.historyAsSequence
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.VersionBuilder
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.mpsadapters.toModelix
import org.modelix.model.mutable.DummyIdGenerator
import org.modelix.model.mutable.VersionedModelTree
import org.modelix.model.mutable.getRootNode
import org.modelix.model.sync.bulk.DefaultInvalidationTree
import org.modelix.model.sync.bulk.FullSyncFilter
import org.modelix.model.sync.bulk.IdentityPreservingNodeAssociation
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.model.sync.bulk.UnfilteredModelMask
import org.modelix.mps.api.ModelixMpsApi
import org.modelix.mps.gitimport.fs.GitFS
import org.modelix.mps.gitimport.fs.GitObjectAsVirtualFile
import java.io.File
import kotlin.system.exitProcess

class GitImporter(
    val gitDir: File,
    val modelServerUrl: String,
    val baseBranch: BranchReference,
    val targetBranchName: String?,
    val gitRevision: String,
    val token: String? = null,
) {
    @OptIn(ExperimentalStdlibApi::class)
    suspend fun runSuspending() {
        val repositoryId = baseBranch.repositoryId

        println()
        println()
        println("Running Git import from $gitDir into $modelServerUrl")
        // Thread.sleep(20_000)

        val git: Git = Git.open(gitDir)
        val resolvedCommit = git.repository.parseCommit(git.repository.resolve(gitRevision))
        val targetBranch = repositoryId.getBranchReference(targetBranchName ?: "git-import-${resolvedCommit.name}")

        val client = ModelClientV2.builder().url(modelServerUrl).lazyAndBlockingQueries().also {
            if (token != null) it.authToken { token }
        }.build()
        client.init()

        println("Finding existing imports")
        val initialRemoteVersion = if (client.listRepositories().contains(repositoryId)) {
            if (client.listBranches(repositoryId).contains(baseBranch)) {
                client.lazyLoadVersion(baseBranch)
            } else {
                client.lazyLoadVersion(repositoryId.getBranchReference())
            }
        } else {
            client.initRepository(repositoryId)
        }
        val versionIndex = VersionIndex(initialRemoteVersion)
        var lastPushedVersion = initialRemoteVersion

        val importQueue = LinkedHashMap<String, PendingImport>()
        val fillQueue = DeepRecursiveFunction<ObjectId, PendingImport> { gitCommitId ->
            val gitCommit = git.repository.parseCommit(gitCommitId)
            importQueue[gitCommit.name]?.let { return@DeepRecursiveFunction it }

            val existingVersionHash = versionIndex.findByGitCommitId(gitCommitId.name)
            val queueElement = if (existingVersionHash != null) {
                PendingImport(
                    gitCommit = gitCommit,
                    parentImports = null,
                    importedVersion = null,
                    existingVersionHash = existingVersionHash,
                )
            } else {
                PendingImport(
                    gitCommit = gitCommit,
                    parentImports = gitCommit.parents?.map { callRecursive(it) }.orEmpty(),
                    importedVersion = null,
                    existingVersionHash = null,
                )
            }
            importQueue[gitCommit.name] = queueElement
            queueElement
        }
        fillQueue(resolvedCommit)

        println("Starting import")
        for (commitId in importQueue.keys.toList()) {
            val queueElement = importQueue.remove(commitId)!!
            try {
                when {
                    queueElement.importedVersion != null -> continue
                    queueElement.existingVersionHash != null -> {
                        val version = client.loadVersion(repositoryId, queueElement.existingVersionHash!!.toString(), null)
                        queueElement.success(version)
                    }
                    else -> {
                        val parentImports = queueElement.parentImports.orEmpty().map { it.importedVersion!! }
                        DummyRepo().use { mpsRepo ->
                            val gitCommit = queueElement.gitCommit
                            val importedVersion = ModelixMpsApi.runWithRepository(mpsRepo) {
                                when (parentImports.size) {
                                    0 -> runImport(listOf(versionIndex.getInitialVersion()), gitCommit, git.repository, mpsRepo)
                                    1, 2 -> runImport(parentImports, gitCommit, git.repository, mpsRepo)
                                    else -> TODO()
                                }
                            }

                            runBlocking {
                                println("Pushing $importedVersion")
                                lastPushedVersion = client.push(targetBranch, importedVersion, importedVersion.getParentVersions(), force = true)
                            }

                            queueElement.success(importedVersion)
                        }
                    }
                }
            } catch (ex: Exception) {
                println("Import failed for ${queueElement.gitCommit.name}")
                ex.printStackTrace()
                throw ex
            }
        }

        println("Import done")
    }

    /**
     * To keep the number of change operations low, for merge commits the import is executed with both parent versions
     * as the base version and the one that is already closer to the result and needs fewer operations is chosen.
     */
    private fun runImport(parentModelixVersions: List<IVersion>, gitCommit: RevCommit, gitRepo: Repository, mpsRepo: DummyRepo): IVersion {
        val alternatives = parentModelixVersions.map {
            runImport(parentModelixVersions, it, gitCommit, gitRepo, mpsRepo)
        }.sortedBy { (it as CLVersion).numberOfOperations }
        val first = alternatives.first()
        if (alternatives.size > 1) {
            println("For merge commit ${gitCommit.name} using ${(first as CLVersion).baseVersion?.gitCommit} (${first.numberOfOperations}) as the base instead of " + alternatives.drop(1).joinToString(", ") { "${(it as CLVersion).baseVersion?.gitCommit} (${it.numberOfOperations})" })
        }
        return first
    }

    private fun runImport(parentModelixVersions: List<IVersion>, baseModelixVersion: IVersion, gitCommit: RevCommit, gitRepo: Repository, mpsRepo: DummyRepo): IVersion {
        val baseGitCommitId = baseModelixVersion.gitCommit
        val syncFilter = if (baseGitCommitId != null) {
            DefaultInvalidationTree(MPSRepositoryAsNode(mpsRepo).getNodeReference()).also {
                loadInvalidations(gitCommit, gitRepo.parseCommit(ObjectId.fromString(baseGitCommitId)), gitRepo, it)
            }
        } else {
            FullSyncFilter()
        }

        val rootDir = GitObjectAsVirtualFile(
            parent = null,
            name = gitCommit.name,
            isDirectory = true,
            objectId = gitCommit.tree,
            repository = gitRepo,
        )

        MPSCoreComponents.getInstance().platform.findComponent(DataSourceFactoryRuleService::class.java)!!
            .register(GitDataSourceFactoryRule(GitFS(rootDir)))

        val miner = ModulesMiner(MPSCoreComponents.getInstance().platform)
        miner.collectModules(rootDir)

        for (collectedModule in miner.collectedModules) {
            val id = collectedModule.descriptor?.id ?: continue
            if (mpsRepo.getModule(id) != null) continue
            val mf = GeneralModuleFactory()
            val module = mf.instantiate(collectedModule.descriptor ?: continue, collectedModule.file)
            mpsRepo.register(module)
        }

        val sourceRoot = MPSRepositoryAsNode(mpsRepo)

        val mutableTree = VersionedModelTree(baseModelixVersion, DummyIdGenerator<INodeReference>())
        mutableTree.runWrite {
            val targetRoot = mutableTree.getRootNode()
            GitSyncUtils.runWithProjects(rootDir, mpsRepo) {
                ModelSynchronizer(
                    filter = syncFilter,
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
        }

        val (ops, newTree) = mutableTree.getPendingChanges()
        val newVersion = CLVersion.builder()
            .tree(newTree)
            .let { builder ->
                when (parentModelixVersions.size) {
                    1 -> builder.regularUpdate(baseModelixVersion)
                    2 -> builder.autoMerge(baseModelixVersion, parentModelixVersions[0], parentModelixVersions[1])
                    else -> TODO()
                }
            }
            .operations(ops.map { it.getOriginalOp() })
            .author("${gitCommit.authorIdent.name} <${gitCommit.authorIdent.emailAddress}>")
            .time(gitCommit.authorIdent.whenAsInstant.toKotlinInstant())
            .gitCommit(gitCommit.name)
            .attribute("git-message", gitCommit.fullMessage)
            .attribute("git-parents", gitCommit.parents.joinToString(",") { it.name })
            .attribute("git-committer", gitCommit.committerIdent.toExternalString())
            .attribute("git-author", gitCommit.authorIdent.toExternalString())
            .build()

        println("Imported ${gitCommit.name} into ${newVersion.getObjectHash()}")

        return newVersion
    }

    private fun loadInvalidations(newGitCommit: RevCommit, oldGitCommit: RevCommit, gitRepo: Repository, invalidations: DefaultInvalidationTree) {
        val walk = TreeWalk(gitRepo)
        walk.addTree(newGitCommit.tree)
        walk.addTree(oldGitCommit.tree)
        walk.isRecursive = true
        while (walk.next()) {
            if (walk.idEqual(0, 1)) continue

            val newId: ObjectId? = walk.getObjectId(0)?.takeIf { it != ObjectId.zeroId() }
            val oldId: ObjectId? = walk.getObjectId(1)?.takeIf { it != ObjectId.zeroId() }

            val changedFiles = listOfNotNull(
                newId?.let { GitFS(newGitCommit, gitRepo).getFile(walk.pathString) },
                oldId?.let { GitFS(oldGitCommit, gitRepo).getFile(walk.pathString) },
            )

            for (file in changedFiles) {
                when (file.extension) {
                    "xml" -> {
                        if (file.name == "modules.xml") {
                            invalidations.invalidate(listOf(invalidations.root), includingDescendants = true)
                        }
                    }
                    MPSExtentions.SOLUTION, MPSExtentions.LANGUAGE, MPSExtentions.GENERATOR, MPSExtentions.DEVKIT -> {
                        file.readModuleDescriptor().let { descriptor ->
                            invalidations.invalidate(listOf(invalidations.root))
                            for (moduleRef in descriptor.getAllModuleReferences()) {
                                invalidations.invalidate(listOf(invalidations.root, moduleRef), includingDescendants = true)
                            }
                        }
                    }
                    MPSExtentions.MODEL, MPSExtentions.MODEL_BINARY, MPSExtentions.MODEL_ROOT -> {
                        val moduleDescriptor = file.findModuleFile()?.readModuleDescriptor()
                        for (moduleRef in moduleDescriptor.getAllModuleReferences()) {
                            invalidations.invalidate(listOfNotNull(invalidations.root, moduleRef), includingDescendants = true)
                        }

//                        val modelRef = file.openInputStream().bufferedReader().lineSequence().mapNotNull {
//                            JDOMUtil.loadDocument(file.openInputStream())
//                                .rootElement
//                                .getAttribute("ref")?.value
//                                ?.let { MPSModelReference.parseSModelReference(it) }
//                        }.firstOrNull()
//                        if (modelRef != null) {
//                            invalidations.invalidate(
//                                listOfNotNull(
//                                    invalidations.root,
//                                    moduleRef,
//                                    modelRef,
//                                ),
//                                includingDescendants = true,
//                            )
//                        }
                    }
                }
            }
        }
    }

    /**
     * The purpose of this class is to build a queue of to be imported versions and keep the result only as long as
     * necessary in memory. As soon as the result is consumed by all child imports, the result can be garbage collected.
     */
    private class PendingImport(
        val gitCommit: RevCommit,
        var parentImports: List<PendingImport>?,
        var importedVersion: IVersion? = null,
        var existingVersionHash: ObjectHash? = null,
    ) {
        fun success(version: IVersion) {
            importedVersion = version
            parentImports = null
        }
    }
}

private fun IFile.findModuleFile(): IFile? {
    if (this.isDirectory) {
        for (child in this.children.orEmpty()) {
            if (moduleDescriptorExtensions.contains(child.extension)) return child
        }
    }
    return parent?.findModuleFile()
}

private fun IFile.readModuleDescriptor(): ModuleDescriptor? {
    val descriptorIOFacade = MPSCoreComponents.getInstance().platform.findComponent(DescriptorIOFacade::class.java)!!
    val descriptorIO: DescriptorIO<out ModuleDescriptor?>? = descriptorIOFacade.fromFileType(this)
    try {
        return descriptorIO?.readFromFile(this)
    } catch (ex: Exception) {
        ex.printStackTrace()
        return null
    }
}

private fun ModuleDescriptor?.getAllModuleReferences(): List<INodeReference> {
    return listOfNotNull(this?.moduleReference?.toModelix()) +
        (this as? LanguageDescriptor)?.generators.orEmpty().mapNotNull { it.moduleReference?.toModelix() }
}

private val moduleDescriptorExtensions = setOf(
    MPSExtentions.SOLUTION,
    MPSExtentions.LANGUAGE,
    MPSExtentions.GENERATOR,
    MPSExtentions.DEVKIT,
)

val IVersion.gitCommit: String? get() = getAttributes()["git-commit"]
fun VersionBuilder.gitCommit(id: String) = attribute("git-commit", id)

class VersionIndex(rootVersion: IVersion) {
    private val historyIterator = rootVersion.historyAsSequence().iterator()
    private val indexedVersions = HashMap<String, ObjectHash>()
    private var initialVersion: IVersion? = null

    fun getInitialVersion(): IVersion {
        findByGitCommitId("just load the remaining history")
        return checkNotNull(initialVersion) { "Initial version not found in the history" }
    }

    fun findByGitCommitId(gitCommitIdToFind: String): ObjectHash? {
        indexedVersions[gitCommitIdToFind]?.let { return it }
        while (historyIterator.hasNext()) {
            val nextVersion = historyIterator.next()
            if (nextVersion.getParentVersions().isEmpty()) {
                initialVersion = nextVersion
            }
            val gitCommitId = nextVersion.gitCommit
            if (gitCommitId != null) {
                indexedVersions[gitCommitId] = nextVersion.getObjectHash()
                if (gitCommitId == gitCommitIdToFind) return nextVersion.getObjectHash()
            }
        }
        return null
    }
}

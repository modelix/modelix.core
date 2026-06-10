package org.modelix.mps.model.sync.bulk

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.project.ProjectManager
import jetbrains.mps.ide.ThreadUtils
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.smodel.SNodeUtil
import jetbrains.mps.smodel.StaticReference
import jetbrains.mps.smodel.adapter.ids.MetaIdHelper
import jetbrains.mps.smodel.adapter.ids.SConceptId
import jetbrains.mps.smodel.adapter.ids.SLanguageId
import jetbrains.mps.smodel.adapter.ids.SPropertyId
import jetbrains.mps.smodel.adapter.structure.MetaAdapterFactory
import jetbrains.mps.smodel.adapter.structure.concept.SConceptAdapterById
import jetbrains.mps.smodel.language.ConceptRegistry
import jetbrains.mps.smodel.language.StructureRegistry
import jetbrains.mps.smodel.runtime.ConceptDescriptor
import jetbrains.mps.smodel.runtime.illegal.IllegalConceptDescriptor
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.mps.openapi.model.EditableSModel
import org.jetbrains.mps.openapi.module.SModule
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.INode
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.client2.ModelClientV2
import org.modelix.model.data.ModelData
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSArea
import org.modelix.model.mpsadapters.MPSModuleAsNode
import org.modelix.model.mpsadapters.asLegacyNode
import org.modelix.model.mpsadapters.asWritableNode
import org.modelix.model.sync.bulk.ExistingAndExpectedNode
import org.modelix.model.sync.bulk.InvalidatingVisitor
import org.modelix.model.sync.bulk.InvalidationTree
import org.modelix.model.sync.bulk.ModelExporter
import org.modelix.model.sync.bulk.ModelImporter
import org.modelix.model.sync.bulk.ModelSynchronizer
import org.modelix.model.sync.bulk.NodeAssociationFromModelServer
import org.modelix.model.sync.bulk.isModuleIncluded
import java.io.File
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

@Suppress("MagicNumber")
private val conceptIdOfINamedConcept = SConceptId(SLanguageId(-0x3154ae6ada15b0deL, -0x646defc46a3573f4L), 0x110396eaaa4L)

@Suppress("MagicNumber")
private val conceptIdOfIResolveInfo = SConceptId(SLanguageId(-0x3154ae6ada15b0deL, -0x646defc46a3573f4L), 0x116b17c6e46L)

/**
 * Identifier of the `name` property in the `INamedConcept` concept.
 * See https://github.com/JetBrains/MPS/blob/5bb20b8a104c08206490e0f3fad70304fa0e0151/core/kernel/kernelSolution/source_gen/jetbrains/mps/util/SNodeOperations.java#L355
 */
@Suppress("MagicNumber")
private val namePropertyOfINamedConceptConcept = MetaAdapterFactory.getProperty(
    SPropertyId(conceptIdOfINamedConcept, 0x110396ec041L),
    "name",
)

/**
 * Identifier of the `resolveInfo` property in the `IResolveInfo` concept.
 * See https://github.com/JetBrains/MPS/blob/5bb20b8a104c08206490e0f3fad70304fa0e0151/core/kernel/kernelSolution/source_gen/jetbrains/mps/util/SNodeOperations.java#L354
 */
@Suppress("MagicNumber")
private val resolveInfoPropertyOfIResolveInfoConcept = MetaAdapterFactory.getProperty(
    SPropertyId(conceptIdOfIResolveInfo, 0x116b17cd415L),
    "resolveInfo",
)

object MPSBulkSynchronizer {

    private const val MPS_2025_1_BASELINE_VERSION = 251

    @JvmStatic
    fun exportRepository() {
        val repository = getRepository()
        val includedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.output.modules"))
        val includedModulePrefixes =
            parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.output.modules.prefixes"))
        val excludedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.output.modules.excluded"))
        val excludedModulePrefixes = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.output.modules.prefixes.excluded"))
        val outputPathName = System.getProperty("modelix.mps.model.sync.bulk.output.path")
        val outputPath = Path.of(outputPathName)
        exportModulesFromRepository(
            repository = repository,
            includedModuleNames = includedModuleNames,
            includedModulePrefixes = includedModulePrefixes,
            outputPath = outputPath,
            excludedModuleNames = excludedModuleNames,
            excludedModulePrefixes = excludedModulePrefixes,
        )
    }

    /**
     * Export modules that are included either by [includedModuleNames] or [includedModulePrefixes]
     * as files into [outputPath].
     */
    @JvmStatic
    @VisibleForTesting
    fun exportModulesFromRepository(
        repository: SRepository,
        includedModuleNames: Set<String>,
        includedModulePrefixes: Set<String>,
        outputPath: Path,
        excludedModuleNames: Set<String> = emptySet(),
        excludedModulePrefixes: Set<String> = emptySet(),
    ) {
        repository.modelAccess.runReadAction {
            val allModules = repository.modules
            val includedModules = allModules.filter {
                isModuleIncluded(
                    moduleName = requireNotNull(it.moduleName) { "module name not found" },
                    includedModules = includedModuleNames,
                    includedPrefixes = includedModulePrefixes,
                    excludedModules = excludedModuleNames,
                    excludedPrefixes = excludedModulePrefixes,
                )
            }

            require(includedModules.isNotEmpty()) {
                """
                    No module matched the inclusion criteria.
                    [includedModules] = $includedModuleNames
                    [includedModulePrefixes] = $includedModulePrefixes
                """.trimIndent()
            }

            val numIncludedModules = includedModules.count()
            val counter = AtomicInteger()

            includedModules.parallelStream().forEach { module ->
                val pos = counter.incrementAndGet()

                repository.modelAccess.runReadAction {
                    println("Exporting module $pos of $numIncludedModules: '${module.moduleName}'")
                    val exporter = ModelExporter(MPSModuleAsNode(module).asLegacyNode())
                    val outputFile = outputPath.resolve(module.moduleName + ".json")
                    exporter.export(outputFile)
                    println("Exported module $pos of $numIncludedModules: '${module.moduleName}'")
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @JvmStatic
    fun importRepository() {
        val repository = getRepository()
        val includedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules"))
        val includedModulePrefixes = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules.prefixes"))
        val excludedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules.excluded"))
        val excludedModulePrefixes = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules.prefixes.excluded"))
        val inputPath = System.getProperty("modelix.mps.model.sync.bulk.input.path")
        val continueOnError = System.getProperty("modelix.mps.model.sync.bulk.input.continueOnError", "false").toBoolean()
        val jsonFiles = File(inputPath).listFiles()?.filter {
            it.extension == "json" && isModuleIncluded(
                moduleName = it.nameWithoutExtension,
                includedModules = includedModuleNames,
                includedPrefixes = includedModulePrefixes,
                excludedModules = excludedModuleNames,
                excludedPrefixes = excludedModulePrefixes,
            )
        }

        if (jsonFiles.isNullOrEmpty()) error("no json files found for included modules")

        println("Found ${jsonFiles.size} modules to be imported")
        val getModulesToImport = {
            val allModules = repository.modules
            val includedModules: Iterable<SModule> = allModules.filter {
                isModuleIncluded(
                    moduleName = requireNotNull(it.moduleName) { "module name not found" },
                    includedModules = includedModuleNames,
                    includedPrefixes = includedModulePrefixes,
                    excludedModules = excludedModuleNames,
                    excludedPrefixes = excludedModulePrefixes,
                )
            }
            val numIncludedModules = includedModules.count()
            val modulesToImport = includedModules.asSequence().flatMapIndexed { index, module ->
                println("Importing module ${index + 1} of $numIncludedModules: '${module.moduleName}'")
                val fileName = inputPath + File.separator + module.moduleName + ".json"
                val moduleFile = File(fileName)
                if (moduleFile.exists()) {
                    val expectedData: ModelData = moduleFile.inputStream().use(Json::decodeFromStream)
                    sequenceOf(ExistingAndExpectedNode(MPSModuleAsNode(module).asLegacyNode(), expectedData))
                } else {
                    println("Skip importing ${module.moduleName}} because $fileName does not exist.")
                    sequenceOf()
                }
            }
            modulesToImport
        }
        importModelsIntoRepository(repository, repository.asLegacyNode(), continueOnError, getModulesToImport)
    }

    /**
     * Import specified models into the repository.
     * [getModulesToImport] is a lambda to be executed with read access in MPS.
     */
    @JvmStatic
    @VisibleForTesting
    fun importModelsIntoRepository(
        repository: SRepository,
        rootOfImport: INode,
        continueOnError: Boolean,
        getModulesToImport: () -> Sequence<ExistingAndExpectedNode>,
    ) {
        executeCommandWithExceptionHandling(repository) {
            println("Importing modules...")
            // `modulesToImport` lazily produces modules to import
            // so that loaded model data can be garbage collected.
            val modulesToImport = getModulesToImport()
            ModelImporter(rootOfImport, continueOnError).importIntoNodes(modulesToImport)
            println("Import finished.")
        }

        persistChanges(repository)
    }

    private fun persistChanges(repository: SRepository) = executeCommandWithExceptionHandling(repository) {
        println("Persisting changes...")
        enableWorkaroundForUnloadedConcepts(repository)
        updateUnsetResolveInfo(repository)
        repository.saveAll()
        println("Changes persisted.")
    }

    private fun executeCommandWithExceptionHandling(repository: SRepository, block: () -> Unit) {
        val exception = ThreadUtils.runInUIThreadAndWait {
            repository.modelAccess.executeCommand {
                block()
            }
        }
        if (exception != null) {
            throw exception
        }
    }

    /**
     * Import from model-server via [INode]-based [ModelSynchronizer].
     * Requires a specified baseVersion.
     */
    @JvmStatic
    fun importRepositoryFromModelServer() {
        val repository = getRepository()

        val includedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules"))
        val includedModulePrefixes = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules.prefixes"))
        val excludedModuleNames = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules.excluded"))
        val excludedModulePrefixes = parseRawPropertySet(System.getProperty("modelix.mps.model.sync.bulk.input.modules.prefixes.excluded"))

        val includedModulesFilter = IncludedModulesFilter(
            includedModules = includedModuleNames,
            includedModulePrefixes = includedModulePrefixes,
            excludedModules = excludedModuleNames,
            excludedModulesPrefixes = excludedModulePrefixes,
        )

        val modelServerUrl = System.getProperty("modelix.mps.model.sync.bulk.server.url")
        val repositoryId = RepositoryId(requireNotNull(System.getProperty("modelix.mps.model.sync.bulk.server.repository")) { "modelix.mps.model.sync.bulk.server.repository not specified" })
        val branchName = System.getProperty("modelix.mps.model.sync.bulk.server.branch")
        val branchRef = repositoryId.getBranchReference(branchName)
        val versionHash: String? = System.getProperty("modelix.mps.model.sync.bulk.server.version.hash")
        val baseVersionHash = requireNotNull(System.getProperty("modelix.mps.model.sync.bulk.server.version.base.hash")) { "modelix.mps.model.sync.bulk.server.version.base.hash not specified" }
        val client = ModelClientV2.builder().url(modelServerUrl).lazyAndBlockingQueries().build()
        val version = runBlocking {
            if (versionHash == null) client.lazyLoadVersion(branchRef) else client.lazyLoadVersion(repositoryId, versionHash)
        }
        val baseVersion = runBlocking {
            client.lazyLoadVersion(repositoryId, baseVersionHash)
        }
        println("Loading version ${version.getContentHash()}")

        executeCommandWithExceptionHandling(repository) {
            val newTree = version.getTree()
            val invalidationTree = InvalidationTree()
            newTree.visitChanges(
                baseVersion.getTree(),
                InvalidatingVisitor(newTree, invalidationTree),
            )
            val treePointer = TreePointer(newTree)
            treePointer.runRead {
                val synchronizer = ModelSynchronizer(
                    CompositeFilter(listOf(invalidationTree, includedModulesFilter)),
                    treePointer.getRootNode().asReadableNode(),
                    repository.asWritableNode(),
                    NodeAssociationFromModelServer(treePointer, MPSArea(repository).asModel()),
                )
                synchronizer.synchronize()
            }
        }

        // TODO call NodeAssociationFromModelServer.writeAssociations and write the changes to the model server

        println("Import finished.")
        persistChanges(repository)
    }

    /**
     * Workaround for MPS not being able to set the `resolveInfo` property on a reference.
     * This is the case when the concept of the target node cannot be loaded/is not valid.
     * Without this workaround, the `resolve` attribute in serialized references
     * (e.g. <ref role="3SLt5I" node="3vHUMVfa0RY" resolve="referencedNodeA" />)
     * will not be set, updated or even removed.
     *
     * The `resolve` is for example removed when the node that contains the reference is moved.
     *
     * The workaround follows the logic of MPS but without relying on the concept being loaded/valid.
     * Without this workaround a bulk sync can remove the `resolve` info unintentionally
     * and produce unwanted file changes.
     */
    private fun updateUnsetResolveInfo(repository: SRepository) {
        val changedModels = filterChangedModels(repository)
        val references = changedModels
            .flatMap { org.jetbrains.mps.openapi.model.SNodeUtil.getDescendants(it) }
            .flatMap { it.references }
            .mapNotNull { it as? StaticReference }

        references.forEach { reference ->
            val target = reference.targetNode ?: return@forEach
            // A concept is not valid, for example, when the language could not be loaded.
            if (target.concept.isValid) {
                return@forEach
            }
            // Try guessing the resolve info following the logic of MPS.
            // Use the logic like in MPS but without relying on the concept being loaded.
            // https://github.com/JetBrains/MPS/blob/5bb20b8a104c08206490e0f3fad70304fa0e0151/core/kernel/kernelSolution/source_gen/jetbrains/mps/util/SNodeOperations.java#L230
            val newResolveInfo = target.getProperty(resolveInfoPropertyOfIResolveInfoConcept)
                ?: target.getProperty(namePropertyOfINamedConceptConcept)
            if (newResolveInfo != reference.resolveInfo) {
                // This workaround works with different persistence because it sets the `resolveInfo`
                // using `jetbrains.mps.smodel.SReference` which is not specific to any persistence.
                reference.resolveInfo = newResolveInfo
            }
        }
    }

    /**
     * Registers a dummy [INamedConcept] descriptor for concepts that aren't loaded, so MPS can read a node's name
     * during save even though the concept's language isn't available. MPS checks for a subconcept of INamedConcept
     * in two places that matter here:
     *
     *  - file-per-root persistence uses the root node's name for the file name (otherwise the node id is used, renaming
     *    files);
     *
     *  - since MPS 2025.1, [org.jetbrains.mps.openapi.model.SModel] save rebuilds the resolve info of every reference
     *    from its target node (see SaveOptions and ResolveInfoUpdater); for an unloaded target concept this would
     *    otherwise produce no name and drop the `resolve` attribute.
     *
     * The descriptor reports the concept as both INamedConcept and IResolveInfo: the `.mps` file records only which
     * property a node uses, not the concept's super-concepts, so MPS cannot tell that an unloaded concept implements
     * either. Reporting both lets the save-time computation read whichever of `name`/`resolveInfo` the node actually
     * has (the other simply yields nothing).
     */
    @JvmStatic
    private fun enableWorkaroundForUnloadedConcepts(repository: SRepository) {
        val structureRegistry: StructureRegistry = ConceptRegistry.getInstance().readField("myStructureRegistry")
        val myConceptDescriptorsById: MutableMap<SConceptId, ConceptDescriptor> =
            structureRegistry.readField("myConceptDescriptorsById")

        val changedModels = filterChangedModels(repository)

        val conceptsToRegister = mutableSetOf<SConceptAdapterById>()

        // MPS 2025.1+ rebuilds reference resolve info during save, so there the reference targets' (possibly unloaded)
        // concepts need a descriptor. On older versions updateUnsetResolveInfo alone sets the resolve info, and
        // registering target descriptors would make their concepts appear valid, causing updateUnsetResolveInfo to skip
        // them.
        val shouldAddReferenceTargets = ApplicationInfo.getInstance().build.baselineVersion >= MPS_2025_1_BASELINE_VERSION

        for (model in changedModels) {
            for (root in model.rootNodes) {
                (root.concept as? SConceptAdapterById)?.let { conceptsToRegister.add(it) }

                if (shouldAddReferenceTargets) {
                    org.jetbrains.mps.openapi.model.SNodeUtil.getDescendants(root).asSequence()
                        .flatMap { it.references }
                        .mapNotNullTo(conceptsToRegister) { it.targetNode?.concept as? SConceptAdapterById }
                }
            }
        }

        conceptsToRegister.forEach {
            myConceptDescriptorsById.putIfAbsent(it.id, DummyNamedConceptDescriptor(it))
        }
    }

    private fun filterChangedModels(repository: SRepository): Sequence<EditableSModel> = repository.modules.asSequence()
        .flatMap { it.models }
        .filterIsInstance<EditableSModel>()
        .filter { it.isChanged }

    @Suppress("UNCHECKED_CAST")
    private fun <R> Any.readField(name: String): R {
        return this::class.java.getDeclaredField(name).also { it.isAccessible = true }.get(this) as R
    }

    private class DummyNamedConceptDescriptor(concept: SConceptAdapterById) :
        ConceptDescriptor by IllegalConceptDescriptor(concept.id, concept.qualifiedName) {

        override fun isAssignableTo(other: SConceptId?): Boolean {
            return other == conceptIdOfINamedConcept || other == conceptIdOfIResolveInfo
        }

        override fun getSuperConceptId(): SConceptId {
            return MetaIdHelper.getConcept(SNodeUtil.concept_BaseConcept)
        }

        override fun getAncestorsIds(): MutableSet<SConceptId> {
            return mutableSetOf(conceptIdOfINamedConcept, conceptIdOfIResolveInfo)
        }

        override fun getParentsIds(): MutableList<SConceptId> {
            return mutableListOf(conceptIdOfINamedConcept, conceptIdOfIResolveInfo)
        }
    }

    @JvmStatic
    private fun parseRawPropertySet(rawProperty: String): Set<String> {
        return if (rawProperty.isEmpty()) {
            emptySet()
        } else {
            rawProperty.split(",")
                .dropLastWhile { it.isEmpty() }
                .toSet()
        }
    }

    @JvmStatic
    private fun getRepository(): SRepository {
        val repoPath = System.getProperty("modelix.mps.model.sync.bulk.repo.path")
        val project = ProjectManager.getInstance().loadAndOpenProject(repoPath)
        val repo = ProjectHelper.getProjectRepository(project)

        return checkNotNull(repo) { "project repository not found" }
    }
}

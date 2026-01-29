package org.modelix.mps.sync3

import org.modelix.model.api.BuiltinLanguages
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IConcept
import org.modelix.model.api.IModel
import org.modelix.model.api.IMutableModel
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.ISyncTargetNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.NewNodeSpec
import org.modelix.model.api.NodeReference
import org.modelix.model.api.NullChildLinkReference
import org.modelix.model.api.remove
import org.modelix.model.api.syncNewChild
import org.modelix.model.api.syncNewChildren
import org.modelix.model.mpsadapters.MPSProjectAsNode
import org.modelix.mps.multiplatform.model.MPSModuleReference
import org.modelix.mps.multiplatform.model.MPSProjectModuleReference
import org.modelix.mps.multiplatform.model.MPSProjectReference

data class SyncTargetConfig(
    val model: IMutableModel,
    val readonly: Boolean,

    /**
     * @see BindingState.projectId
     */
    val projectId: MPSProjectReference,
)

class SyncTargetModel(
    val project: MPSProjectAsNode,
    val targetConfigs: List<SyncTargetConfig>,
) : IMutableModel {
    private val rootRef = NodeReference("sync-root")
    private val models: List<IMutableModel> get() = targetConfigs.map { it.model }
    private val repositoryNode: RepositoryWrapper = RepositoryWrapper()

    override fun getRootNode(): IWritableNode = repositoryNode

    override fun getRootNodes(): List<IWritableNode> = listOf(getRootNode())

    override fun tryResolveNode(ref: INodeReference): IWritableNode? {
        if (ref == rootRef) return repositoryNode
        if (models.any { it.getRootNode().getNodeReference() == ref }) return repositoryNode
        return models.firstNotNullOfOrNull { m ->
            m.tryResolveNode(ref)?.let { node ->
                if (node.getContainmentLink().matches(BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference())) {
                    return ProjectWrapper(project.getNodeReference())
                }
                NodeWrapper(m, node)
            }
        }
    }

    override fun <R> executeRead(body: () -> R): R {
        return executeRead(models, body)
    }

    override fun <R> executeWrite(body: () -> R): R {
        return executeWrite(models, body)
    }

    private fun <R> executeRead(remainingModels: List<IModel>, body: () -> R): R {
        if (remainingModels.isEmpty()) return body()
        return remainingModels.first().executeRead {
            executeRead(remainingModels.subList(1, remainingModels.lastIndex), body)
        }
    }

    private fun <R> executeWrite(remainingModels: List<IModel>, body: () -> R): R {
        if (remainingModels.isEmpty()) return body()
        return remainingModels.first().executeWrite {
            executeWrite(remainingModels.subList(1, remainingModels.size), body)
        }
    }

    override fun canRead(): Boolean {
        return models.all { it.canRead() }
    }

    override fun canWrite(): Boolean {
        return models.all { it.canWrite() }
    }

    inner class RepositoryWrapper : IWritableNode, ISyncTargetNode {
        private val modulesRole: IChildLinkReference get() = BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference()
        private val projectsRole: IChildLinkReference get() = BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference()

        private val delegate: IWritableNode get() = models.first().getRootNode()

        override fun getModel(): IMutableModel = this@SyncTargetModel

        fun getRepositories(): List<IWritableNode> {
            return models.map { NodeWrapper(it, it.getRootNode()) }
        }

        fun getMPSModules(): List<IWritableNode> {
            return targetConfigs.flatMap { config ->
                config.model.getRootNodes().flatMap { repositoryNode ->
                    repositoryNode.getChildren(modulesRole).map { ModuleWrapper(it, config) }
                }
            }.distinctBy { it.getNodeReference() }
        }

        fun getMPSProjects(): List<IWritableNode> {
            return listOf(ProjectWrapper(project.getNodeReference()))
        }

        override fun getAllChildren(): List<IWritableNode> {
            return getMPSModules() + getMPSProjects()
        }

        override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
            return when {
                role.matches(modulesRole) -> getMPSModules()
                role.matches(projectsRole) -> getMPSProjects()
                else -> emptyList()
            }
        }

        override fun getLocalReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
            return null
        }

        override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
            return emptyList()
        }

        override fun getParent(): IWritableNode? {
            return null
        }

        override fun changeConcept(newConcept: ConceptReference): IWritableNode {
            val updatedNode = delegate.changeConcept(newConcept)
            require(updatedNode == delegate) {
                "Only in-place concept changes are supported"
            }
            return this
        }

        override fun setPropertyValue(property: IPropertyReference, value: String?) {
            delegate.setPropertyValue(property, value)
        }

        override fun moveChild(
            role: IChildLinkReference,
            index: Int,
            child: IWritableNode,
        ) {
            throw UnsupportedOperationException()
        }

        override fun removeChild(child: IWritableNode) {
            child as NodeWrapper
            child.node.remove()
        }

        override fun addNewChild(
            role: IChildLinkReference,
            index: Int,
            concept: ConceptReference,
        ): IWritableNode {
            return delegate.addNewChild(role, index, concept).let { NodeWrapper(models.first(), it) }
        }

        override fun addNewChildren(
            role: IChildLinkReference,
            index: Int,
            concepts: List<ConceptReference>,
        ): List<IWritableNode> {
            return delegate.addNewChildren(role, index, concepts).map { NodeWrapper(models.first(), it) }
        }

        override fun setReferenceTarget(
            role: IReferenceLinkReference,
            target: IWritableNode?,
        ) {
            delegate.setReferenceTarget(role, target?.unwrap())
        }

        override fun setReferenceTargetRef(
            role: IReferenceLinkReference,
            target: INodeReference?,
        ) {
            delegate.setReferenceTargetRef(role, target)
        }

        override fun isValid(): Boolean {
            return getRepositories().all { it.isValid() }
        }

        override fun getNodeReference(): INodeReference {
            return rootRef
        }

        override fun getConcept(): IConcept {
            return delegate.getConcept()
        }

        override fun getConceptReference(): ConceptReference {
            return delegate.getConceptReference()
        }

        override fun getContainmentLink(): IChildLinkReference {
            return NullChildLinkReference
        }

        override fun getPropertyValue(property: IPropertyReference): String? {
            return delegate.getPropertyValue(property)
        }

        override fun getPropertyLinks(): List<IPropertyReference> {
            return delegate.getPropertyLinks()
        }

        override fun getAllProperties(): List<Pair<IPropertyReference, String>> {
            return delegate.getAllProperties()
        }

        override fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference? {
            return delegate.getReferenceTargetRef(role)
        }

        override fun getReferenceLinks(): List<IReferenceLinkReference> {
            return delegate.getReferenceLinks()
        }

        override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> {
            return delegate.getAllReferenceTargetRefs()
        }

        override fun syncNewChildren(
            role: IChildLinkReference,
            index: Int,
            specs: List<NewNodeSpec>,
        ): List<IWritableNode> {
            return delegate.syncNewChildren(role, index, specs).map { NodeWrapper(models.first(), it) }
        }
    }

    /**
     * One local MPS project can have bindings to multiple server side projects in different repositories.
     * This class provides a merged representation of all server side projects as a single project so that it can be
     * more easily synchronized with the single local MPS project.
     */
    inner class ProjectWrapper(val localId: MPSProjectReference) : WrapperBase() {
        override fun delegates(): Sequence<IWritableNode> = targetConfigs.asSequence().mapNotNull {
            it.model.tryResolveNode(it.projectId)
        }

        override fun getOrCreateDelegate(): IWritableNode = delegates().firstOrNull()
            ?: targetConfigs.filterNot { it.readonly }.first().let { config ->
                config.model.getRootNode().syncNewChild(
                    role = BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference(),
                    index = 0,
                    sourceNode = NewNodeSpec(
                        conceptRef = BuiltinLanguages.MPSRepositoryConcepts.Project.getReference(),
                        preferredNodeReference = config.projectId,
                    ),
                )
            }

        override fun getModel() = this@SyncTargetModel

        private fun getProjectModules(): List<List<IWritableNode>> {
            return targetConfigs.map { targetConfig ->
                val serverSideProjectId = targetConfig.projectId
                if (serverSideProjectId == null) {
                    // If the binding doesn't specify a project ID, project nodes are ignored for that repository and
                    // all modules are considered being part of the binding.
                    targetConfig.model.getRootNode()
                        .getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference())
                        .map { it.getNodeReference() }
                } else {
                    val projectNode = targetConfig.model.tryResolveNode(serverSideProjectId)
                    if (projectNode == null) {
                        emptyList()
                    } else {
                        projectNode
                            .getChildren(BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference())
                            .mapNotNull {
                                it.getReferenceTargetRef(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference())
                            }
                    }
                }.map {
                    ProjectModuleWrapper(this, MPSProjectModuleReference(MPSModuleReference.convert(it), localId))
                }
            }
        }

        private fun getAllModuleIds(): List<Set<INodeReference>> {
            return targetConfigs.map { targetConfig ->
                (
                    targetConfig.model.getRootNodes()
                        .flatMap { it.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.modules.toReference()) }
                        .map { it.getNodeReference() } +
                        targetConfig.model.getRootNodes()
                            .flatMap { it.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference()) }
                            .flatMap { it.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference()) }
                            .mapNotNull { it.getReferenceTargetRef(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference()) }
                    ).toSet()
            }
        }

        override fun getAllChildren(): List<IWritableNode> {
            return getProjectModules().flatten()
        }

        override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
            if (role.matches(BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference())) {
                return getProjectModules().flatten()
            }
            return emptyList()
        }

        override fun getLocalReferenceTarget(role: IReferenceLinkReference): IWritableNode? = null

        override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
            return emptyList()
        }

        override fun getParent(): IWritableNode? {
            return repositoryNode
        }

        override fun changeConcept(newConcept: ConceptReference): IWritableNode {
            require(newConcept == BuiltinLanguages.MPSRepositoryConcepts.Project.getReference()) {
                "Unexpected concept change: $newConcept"
            }
            return this
        }

        override fun moveChild(
            role: IChildLinkReference,
            index: Int,
            child: IWritableNode,
        ) {
            TODO("Not yet implemented")
        }

        override fun removeChild(child: IWritableNode) {
            when (child) {
                is ProjectModuleWrapper -> {
                    child.delegates().toList().forEach { it.remove() }
                }
                else -> TODO()
            }
        }

        override fun addNewChild(
            role: IChildLinkReference,
            index: Int,
            concept: ConceptReference,
        ): IWritableNode {
            TODO("Not yet implemented")
        }

        override fun addNewChildren(
            role: IChildLinkReference,
            index: Int,
            concepts: List<ConceptReference>,
        ): List<IWritableNode> {
            TODO("Not yet implemented")
        }

        override fun isValid(): Boolean {
            return true
        }

        override fun getNodeReference(): INodeReference {
            return localId
        }

        override fun getConcept(): IConcept {
            return BuiltinLanguages.MPSRepositoryConcepts.Project
        }

        override fun getConceptReference(): ConceptReference {
            return BuiltinLanguages.MPSRepositoryConcepts.Project.getReference()
        }

        override fun getContainmentLink(): IChildLinkReference {
            return BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference()
        }

        override fun syncNewChildren(
            role: IChildLinkReference,
            index: Int,
            specs: List<NewNodeSpec>,
        ): List<IWritableNode> {
            when {
                role.matches(BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference()) -> {
                    val moduleOwners = getAllModuleIds().withIndex().flatMap { (configIndex, modules) ->
                        modules.map { it to configIndex }
                    }.toMap()

                    return specs.groupBy { moduleOwners[it.preferredOrCurrentRef] ?: 0 }.flatMap { (ownerIndex, specs) ->
                        val targetConfig = targetConfigs[ownerIndex]
                        val projectNode = targetConfig.model.tryResolveNode(targetConfig.projectId)
                            ?: targetConfig.model.getRootNode().addNewChild(
                                BuiltinLanguages.MPSRepositoryConcepts.Repository.projects.toReference(), -1,
                                BuiltinLanguages.MPSRepositoryConcepts.Project.getReference(),
                            )
                        projectNode.syncNewChildren(role, -1, specs).map {
                            val id = MPSProjectModuleReference.convert(it.getNodeReference())
                            it.setReferenceTargetRef(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference(), id.moduleRef)
                            ProjectModuleWrapper(this, MPSProjectModuleReference(id.moduleRef, localId))
                        }
                    }
                }
                else -> throw UnsupportedOperationException("role = $role")
            }
        }
    }

    inner class ProjectModuleWrapper(val project: ProjectWrapper, val id: MPSProjectModuleReference) : WrapperBase() {
        override fun delegates(): Sequence<IWritableNode> = project.delegates()
            .flatMap { it.getChildren(BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference()) }
            .filter { it.getReferenceTargetRef(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference()) == id.moduleRef }

        override fun getOrCreateDelegate(): IWritableNode {
            return delegates().firstOrNull()
                ?: project.delegates().first().syncNewChild(getContainmentLink(), -1, NewNodeSpec(this))
        }

        private fun targetModule() = tryResolveNode(id.moduleRef)

        override fun getModel(): IMutableModel {
            return this@SyncTargetModel
        }

        override fun getAllChildren(): List<IWritableNode> {
            return emptyList()
        }

        override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
            return emptyList()
        }

        override fun getLocalReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
            if (role.matches(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference())) {
                return targetModule()
            }
            return null
        }

        override fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference? {
            if (role.matches(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference())) {
                return id.moduleRef
            }
            return null
        }

        override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
            return listOfNotNull(targetModule()?.let { BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference() to it })
        }

        override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> {
            return listOfNotNull(BuiltinLanguages.MPSRepositoryConcepts.ModuleReference.module.toReference() to id.moduleRef)
        }

        override fun getParent(): IWritableNode? {
            return project
        }

        override fun changeConcept(newConcept: ConceptReference): IWritableNode {
            TODO("Not yet implemented")
        }

        override fun setPropertyValue(property: IPropertyReference, value: String?) {
            if (property.matches(BuiltinLanguages.MPSRepositoryConcepts.ProjectModule.virtualFolder.toReference())) {
                return getOrCreateDelegate().setPropertyValue(property, value)
            }
            throw UnsupportedOperationException("$property = $value")
        }

        override fun moveChild(
            role: IChildLinkReference,
            index: Int,
            child: IWritableNode,
        ) {
            TODO("Not yet implemented")
        }

        override fun removeChild(child: IWritableNode) {
            TODO("Not yet implemented")
        }

        override fun addNewChild(
            role: IChildLinkReference,
            index: Int,
            concept: ConceptReference,
        ): IWritableNode {
            TODO("Not yet implemented")
        }

        override fun addNewChildren(
            role: IChildLinkReference,
            index: Int,
            concepts: List<ConceptReference>,
        ): List<IWritableNode> {
            TODO("Not yet implemented")
        }

        override fun isValid(): Boolean {
            return delegates().any()
        }

        override fun getNodeReference(): INodeReference {
            return id
        }

        override fun getConcept(): IConcept {
            return BuiltinLanguages.MPSRepositoryConcepts.ProjectModule
        }

        override fun getConceptReference(): ConceptReference {
            return BuiltinLanguages.MPSRepositoryConcepts.ProjectModule.getReference()
        }

        override fun getContainmentLink(): IChildLinkReference {
            return BuiltinLanguages.MPSRepositoryConcepts.Project.projectModules.toReference()
        }

        override fun syncNewChildren(
            role: IChildLinkReference,
            index: Int,
            specs: List<NewNodeSpec>,
        ): List<IWritableNode> {
            TODO("Not yet implemented")
        }
    }

    private fun IWritableNode.unwrap() = if (this is NodeWrapper) this.node else this

    open inner class NodeWrapper(private val model: IMutableModel, val node: IWritableNode) : IWritableNode by node, ISyncTargetNode {
        init {
            require(node !is NodeWrapper)
            require(node.getNodeReference() != model.getRootNode().getNodeReference()) {
                "${RepositoryWrapper::javaClass.name} should be used for $node"
            }
        }

        private fun IWritableNode.wrap() = NodeWrapper(model, this)
        private fun Iterable<IWritableNode>.wrap() = map { it.wrap() }

        override fun getModel(): IMutableModel {
            return this@SyncTargetModel
        }

        override fun getAllChildren(): List<IWritableNode> {
            return node.getAllChildren().wrap()
        }

        override fun getChildren(role: IChildLinkReference): List<IWritableNode> {
            return node.getChildren(role).wrap()
        }

        override fun getLocalReferenceTarget(role: IReferenceLinkReference): IWritableNode? {
            return node.getLocalReferenceTarget(role)?.wrap()
        }

        override fun getAllReferenceTargets(): List<Pair<IReferenceLinkReference, IWritableNode>> {
            return node.getAllReferenceTargets().map { it.first to it.second.wrap() }
        }

        override fun getParent(): IWritableNode? {
            val parent = node.getParent() ?: return null
            val role = node.getContainmentLink()
            return when {
                BuiltinLanguages.MPSRepositoryConcepts.Repository.childLinks.any { it.toReference().matches(role) } -> {
                    repositoryNode
                }
                BuiltinLanguages.MPSRepositoryConcepts.Project.childLinks.any { it.toReference().matches(role) } -> {
                    ProjectWrapper(MPSProjectReference.convert(parent.getNodeReference()))
                }
                else -> parent.wrap()
            }
        }

        override fun changeConcept(newConcept: ConceptReference): IWritableNode {
            return node.changeConcept(newConcept).wrap()
        }

        override fun moveChild(
            role: IChildLinkReference,
            index: Int,
            child: IWritableNode,
        ) {
            child as NodeWrapper
            require(child.model == model) {
                "Cannot move a node between models. [child=${child.node}, child.model=${child.model}, target=$node, target.model=$model, source=${child.node.getParent()}, role=$role, index=$index]"
            }
            node.moveChild(role, index, child.node)
        }

        override fun removeChild(child: IWritableNode) {
            child as NodeWrapper
            node.removeChild(child.node)
        }

        override fun addNewChild(
            role: IChildLinkReference,
            index: Int,
            concept: ConceptReference,
        ): IWritableNode {
            return node.addNewChild(role, index, concept).wrap()
        }

        override fun addNewChildren(
            role: IChildLinkReference,
            index: Int,
            concepts: List<ConceptReference>,
        ): List<IWritableNode> {
            return node.addNewChildren(role, index, concepts).wrap()
        }

        override fun setReferenceTarget(
            role: IReferenceLinkReference,
            target: IWritableNode?,
        ) {
            target as NodeWrapper?
            node.setReferenceTarget(role, target?.node)
        }

        override fun syncNewChildren(
            role: IChildLinkReference,
            index: Int,
            specs: List<NewNodeSpec>,
        ): List<IWritableNode> {
            return node.syncNewChildren(role, index, specs).wrap()
        }
    }

    inner class ModuleWrapper(node: IWritableNode, val owner: SyncTargetConfig) : NodeWrapper(owner.model, node) {
        override fun isReadOnly(): Boolean {
            return owner.readonly || node.isReadOnly()
        }

        override fun getPropertyValue(property: IPropertyReference): String? {
            if (property.matches(BuiltinLanguages.MPSRepositoryConcepts.Module.isReadOnly.toReference()) && owner.readonly) {
                return true.toString()
            }
            return super.getPropertyValue(property)
        }

        override fun setPropertyValue(property: IPropertyReference, value: String?) {
            if (property.matches(BuiltinLanguages.MPSRepositoryConcepts.Module.isReadOnly.toReference())) {
                return // changes not supported
            }
            return super.setPropertyValue(property, value)
        }
    }

    abstract inner class WrapperBase : IWritableNode, ISyncTargetNode {
        abstract fun delegates(): Sequence<IWritableNode>
        open fun getOrCreateDelegate(): IWritableNode = delegates().first()

        override fun getPropertyValue(property: IPropertyReference): String? {
            return delegates().firstNotNullOfOrNull { it.getPropertyValue(property) }
        }

        override fun getPropertyLinks(): List<IPropertyReference> {
            return delegates().flatMap { it.getPropertyLinks() }.distinct().toList()
        }

        override fun getAllProperties(): List<Pair<IPropertyReference, String>> {
            return delegates().flatMap { it.getAllProperties() }.distinctBy { it.first }.toList()
        }

        override fun getReferenceTargetRef(role: IReferenceLinkReference): INodeReference? {
            return delegates().firstNotNullOfOrNull { it.getReferenceTargetRef(role) }
        }

        override fun getReferenceLinks(): List<IReferenceLinkReference> {
            return delegates().flatMap { it.getReferenceLinks() }.distinct().toList()
        }

        override fun getAllReferenceTargetRefs(): List<Pair<IReferenceLinkReference, INodeReference>> {
            return delegates().flatMap { it.getAllReferenceTargetRefs() }.distinctBy { it.first }.toList()
        }

        override fun setPropertyValue(property: IPropertyReference, value: String?) {
            getOrCreateDelegate().setPropertyValue(property, value)
        }

        override fun setReferenceTarget(
            role: IReferenceLinkReference,
            target: IWritableNode?,
        ) {
            getOrCreateDelegate().setReferenceTarget(role, target?.unwrap())
        }

        override fun setReferenceTargetRef(
            role: IReferenceLinkReference,
            target: INodeReference?,
        ) {
            getOrCreateDelegate().setReferenceTargetRef(role, target)
        }
    }
}

package org.modelix.mps.sync3

import org.jdom.Element
import org.jetbrains.mps.openapi.module.SModuleId
import org.jetbrains.mps.openapi.persistence.PersistenceFacade
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.mps.multiplatform.model.MPSProjectReference

data class SyncServiceState(
    val bindings: Map<BindingId, BindingState> = emptyMap(),
    /**
     * Modules that exist locally, but aren't synchronized to any repository.
     */
    val localModules: Set<SModuleId> = emptySet(),
) : IModuleMappings {
    override fun getAllModuleOwners(): Map<SModuleId, IModuleOwnerId> {
        return (
            bindings.flatMap { binding ->
                binding.value.ownedModules.map { it to binding.key }
            } + localModules.map { it to LocalOnlyModuleOwner }
            ).toMap()
    }

    override fun getModuleOwner(moduleId: SModuleId): IModuleOwnerId? {
        return getAllModuleOwners()[moduleId]
    }

    fun assignModuleOwner(moduleId: SModuleId, owner: IModuleOwnerId?): SyncServiceState {
        return copy(
            bindings = bindings.mapValues {
                if (it.key == owner) {
                    it.value.copy(ownedModules = it.value.ownedModules + moduleId)
                } else {
                    it.value.copy(ownedModules = it.value.ownedModules - moduleId)
                }
            },
            localModules = if (owner == LocalOnlyModuleOwner) localModules + moduleId else localModules - moduleId,
        )
    }

    fun toXml() = Element("model-sync").also {
        it.children.addAll(
            bindings.map { bindingEntry ->
                Element("binding").also { bindingElement ->
                    bindingElement.children.add(Element("enabled").also { it.text = bindingEntry.value.enabled.toString() })
                    bindingElement.children.add(
                        Element("url").also {
                            it.text = bindingEntry.key.connectionProperties.url
                            it.setAttribute("repositoryScoped", "${bindingEntry.key.connectionProperties.repositoryId != null}")
                        },
                    )
                    bindingEntry.key.connectionProperties.oauthClientId?.let { oauthClientId ->
                        bindingElement.children.add(Element("oauthClientId").also { it.text = oauthClientId })
                    }
                    bindingEntry.key.connectionProperties.oauthClientSecret?.let { oauthClientSecret ->
                        bindingElement.children.add(Element("oauthClientSecret").also { it.text = oauthClientSecret })
                    }
                    bindingElement.children.add(Element("repository").also { it.text = bindingEntry.key.branchRef.repositoryId.id })
                    bindingElement.children.add(Element("branch").also { it.text = bindingEntry.key.branchRef.branchName })
                    bindingElement.children.add(Element("versionHash").also { it.text = bindingEntry.value.versionHash })
                    bindingElement.children.add(Element("readonly").also { it.text = bindingEntry.value.readonly.toString() })
                    bindingEntry.value.projectId?.projectName?.takeIf { it.isNotEmpty() }?.let { projectId ->
                        bindingElement.children.add(Element("projectId").also { it.text = projectId })
                    }
                    bindingEntry.value.ownedModules.forEach { moduleId ->
                        bindingElement.children.add(Element("module").also { it.text = moduleId.toString() })
                    }
                }
            } + localModules.map { moduleId ->
                Element("module").also { it.text = moduleId.toString() }
            },
        )
    }
    companion object {
        fun fromXml(element: Element): SyncServiceState {
            return SyncServiceState(
                bindings = element.getChildren("binding").mapNotNull<Element, Pair<BindingId, BindingState>> { element ->
                    val repositoryId = RepositoryId(element.getChild("repository")?.text ?: return@mapNotNull null)
                    BindingId(
                        connectionProperties = ModelServerConnectionProperties(
                            url = element.getChild("url")?.text ?: return@mapNotNull null,
                            repositoryId = repositoryId.takeIf { element.getChild("url")?.getAttribute("repositoryScoped")?.value != "false" },
                            oauthClientId = element.getChild("oauthClientId")?.text,
                            oauthClientSecret = element.getChild("oauthClientSecret")?.text,
                        ),
                        branchRef = BranchReference(
                            repositoryId,
                            element.getChild("branch")?.text ?: return@mapNotNull null,
                        ),
                    ) to BindingState(
                        versionHash = element.getChild("versionHash")?.text,
                        enabled = element.getChild("enabled")?.text.toBoolean(),
                        readonly = element.getChild("readonly")?.text.toBoolean(),
                        projectId = element.getChild("projectId")?.text?.takeIf { it.isNotEmpty() }?.let { MPSProjectReference(it) },
                        ownedModules = element.getChildren("module").mapNotNull {
                            runCatching { PersistenceFacade.getInstance().createModuleId(it.text) }.getOrNull()
                        }.toSet(),
                    )
                }.toMap(),
                localModules = element.getChildren("module").mapNotNull { element ->
                    runCatching { PersistenceFacade.getInstance().createModuleId(element.text) }.getOrNull()
                }.toSet(),
            )
        }
    }
}

data class BindingState(
    val versionHash: String? = null,
    val enabled: Boolean = false,
    val readonly: Boolean = false,

    /**
     * If null, the first found project is used.
     */
    val projectId: MPSProjectReference? = null,
    val ownedModules: Set<SModuleId> = emptySet(),
    val ignoredModules: Set<SModuleId> = emptySet(),
)

data class BindingId(val connectionProperties: ModelServerConnectionProperties, val branchRef: BranchReference) : IModuleOwnerId {
    override fun toString(): String {
        return "BindingId($connectionProperties, ${branchRef.repositoryId}, ${branchRef.branchName})"
    }
}

sealed interface IModuleOwnerId

object LocalOnlyModuleOwner : IModuleOwnerId

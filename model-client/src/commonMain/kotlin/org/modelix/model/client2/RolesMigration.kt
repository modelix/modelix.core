package org.modelix.model.client2

import org.modelix.datastructures.model.DefaultModelTree
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.model.Int64ModelTree
import org.modelix.datastructures.model.getDescendants
import org.modelix.datastructures.objects.asObject
import org.modelix.kotlin.utils.DelicateModelixApi
import org.modelix.model.IVersion
import org.modelix.model.ObjectDeltaFilter
import org.modelix.model.TreeType
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference
import org.modelix.model.api.NullChildLinkReference
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.runWriteOnTree
import org.modelix.model.mutable.DummyIdGenerator
import org.modelix.model.mutable.VersionedModelTree
import org.modelix.model.mutable.moveChildren
import org.modelix.model.mutable.setProperty
import org.modelix.model.mutable.setReferenceTarget
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.CPVersion
import org.modelix.streams.IStream
import org.modelix.streams.executeBlocking
import org.modelix.streams.forEach
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.collections.plus

suspend fun IModelClientV2.migrateRoles(
    branch: BranchReference,
    roleReplacement: (IRoleReference) -> IRoleReference,
): IVersion = migrateRoles(
    branch,
    object : IRoleReplacement {
        override fun replaceRole(concept: ConceptReference, role: IPropertyReference): IPropertyReference {
            return roleReplacement(role) as IPropertyReference
        }

        override fun replaceRole(concept: ConceptReference, role: IReferenceLinkReference): IReferenceLinkReference {
            return roleReplacement(role) as IReferenceLinkReference
        }

        override fun replaceRole(concept: ConceptReference, role: IChildLinkReference): IChildLinkReference {
            return roleReplacement(role) as IChildLinkReference
        }
    },
)

/**
 * Ensure this executed without concurrently active clients so that no merges can happen.
 */
@OptIn(DelicateModelixApi::class)
suspend fun IModelClientV2.migrateRoles(
    branch: BranchReference,
    roleReplacement: IRoleReplacement,
): IVersion {
    val oldVersion = pull(
        branch,
        lastKnownVersion = null,
        filter = ObjectDeltaFilter(
            knownVersions = emptySet(),
            includeOperations = false,
            includeHistory = false,
            includeTrees = true,
        ),
    )

    val newVersion = oldVersion
        .runWriteOnTree(nodeIdGenerator = DummyIdGenerator<INodeReference>(), getUserId()) { newTree ->
            val oldTree = newTree.getTransaction().tree

            (newTree.getWriteTransaction() as VersionedModelTree.VersionedWriteTransaction).unsafeSetTree(newTree.getTransaction().tree.withUseRoleIds(true))

            oldTree.getDescendants(oldTree.getRootNodeId(), true)
                .flatMap { IStream.of(it).zipWith(oldTree.getConceptReference(it)) { nodeId, concept -> nodeId to concept } }
                .flatMap { (nodeId, concept) ->
                    oldTree.getChildren(nodeId).flatMap { childId ->
                        IStream.of(childId)
                            .zipWith(oldTree.getRoleInParent(childId).firstOrDefault { NullChildLinkReference }) { id, role -> id to role }
                    }.toList().forEach { children ->
                        val childrenByRole = children.groupBy { it.second }
                        for ((oldRole, childrenInOldRole) in childrenByRole) {
                            val newRole = if (oldRole.matches(NullChildLinkReference)) {
                                NullChildLinkReference
                            } else {
                                roleReplacement.replaceRole(concept, oldRole)
                            }
                            if (oldRole == newRole) continue
                            newTree.getWriteTransaction().moveChildren(nodeId, newRole, -1, childrenInOldRole.map { it.first })
                        }
                    }.andThen(
                        oldTree.getProperties(nodeId).forEach { (oldRole, value) ->
                            val newRole = roleReplacement.replaceRole(concept, oldRole)
                            if (oldRole == newRole) return@forEach
                            newTree.setProperty(nodeId, oldRole, null)
                            newTree.setProperty(nodeId, newRole, value)
                        }.andThen(
                            oldTree.getReferenceTargets(nodeId).forEach { (oldRole, value) ->
                                val newRole = roleReplacement.replaceRole(concept, oldRole)
                                newTree.setReferenceTarget(nodeId, oldRole, null)
                                newTree.setReferenceTarget(nodeId, newRole, value)
                            },
                        ),
                    ).andThenUnit()
                }.drainAll().executeBlocking(oldTree)
        }

    push(branch, newVersion, oldVersion)
    return newVersion
}

interface IRoleReplacement {
    fun replaceRole(concept: ConceptReference, role: IPropertyReference): IPropertyReference
    fun replaceRole(concept: ConceptReference, role: IReferenceLinkReference): IReferenceLinkReference
    fun replaceRole(concept: ConceptReference, role: IChildLinkReference): IChildLinkReference
}

private fun IVersion.replaceMainTree(modifier: (CPTree) -> CPTree): CLVersion {
    this as CLVersion
    @OptIn(DelicateModelixApi::class)
    return CLVersion(data.replaceMainTree(modifier).asObject(obj.graph))
}

private fun CPVersion.replaceMainTree(modifier: (CPTree) -> CPTree): CPVersion {
    val mainTreeRef = treeRefs[TreeType.MAIN]!!.resolveNow()
    val newData = modifier(mainTreeRef.data)
    val newRef = mainTreeRef.graph.fromCreated(newData)
    return copy(treeRefs = treeRefs + (TreeType.MAIN to newRef))
}

fun <NodeId> IGenericModelTree<*>.withUseRoleIds(useRoleIds: Boolean): IGenericModelTree<NodeId> {
    return when (this) {
        is Int64ModelTree -> Int64ModelTree(this.nodesMap, this.getId(), useRoleIds)
        is DefaultModelTree -> DefaultModelTree(this.nodesMap, this.getId(), useRoleIds)
        else -> throw IllegalArgumentException("unknown tree type: $this")
    } as IGenericModelTree<NodeId>
}

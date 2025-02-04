package org.modelix.model.sync.bulk

import mu.KotlinLogging
import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.INode
import org.modelix.model.api.IReadableNode
import org.modelix.model.api.IWritableNode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.data.ModelData
import org.modelix.model.data.NodeData
import org.modelix.model.data.NodeDataAsNode
import org.modelix.model.data.ensureHasId
import org.modelix.model.sync.bulk.ModelSynchronizer.IFilter
import kotlin.jvm.JvmName

private val LOG = KotlinLogging.logger { }

/**
 * A ModelImporter updates an existing [INode] and its subtree based on a [ModelData] specification.
 *
 * The import is incremental.
 * Instead of simply overwriting the existing model, only a minimal amount of operations is used.
 *
 * Properties, references, and child links are synchronized for this node and all of its (in-)direct children.
 *
 * Changes to the behaviour of this class should also reflected in [ModelImporter].
 *
 * @param root the root node to be updated
 * @param continueOnError if true, ignore exceptions and continue.
 *        Enabling this might lead to inconsistent models.
 * @param childFilter filter that is applied to all children of a parent.
 *        If the filter evaluates to true, the node is included.
 */
class ModelImporter(
    private val root: INode,
    private val continueOnError: Boolean,
    private val childFilter: (INode) -> Boolean = { true },
    private val writeOriginalIds: Boolean = root is PNodeAdapter,
    private val nodeAssociation: INodeAssociation = TransientNodeAssociation(writeOriginalIds, root.getArea().asModel()),
) {
    // For MPS / Java compatibility, where a default value does not work. Can be dropped once the MPS solution is
    // updated to the constructor with two arguments.
    constructor(root: INode) : this(root, false)

    /**
     * Incrementally updates this importers root based on the provided [ModelData] specification.
     *
     * @param data the model specification
     */
    @JvmName("importData")
    fun import(data: ModelData) {
        importIntoNodes(sequenceOf(ExistingAndExpectedNode(root, data)))
    }

    /**
     * Incrementally updates existing children of the given with specified data.
     *
     * @param nodeCombinationsToImport Combinations of an old existing child and the new expected data.
     * The combinations are consumed lazily.
     * Callers can use this to load expected data on demand.
     */
    fun importIntoNodes(nodeCombinationsToImport: Sequence<ExistingAndExpectedNode>) {
        val sourceAndTargetNodes = nodeCombinationsToImport.toList()
        val sourceNodes = sourceAndTargetNodes.map { NodeDataAsNode(it.expectedNodeData.root.ensureHasId(), null) }
        val targetNodes = sourceAndTargetNodes.map { it.existingNode.asWritableNode() }

        ModelSynchronizer(
            filter = object : IFilter {
                override fun needsDescentIntoSubtree(subtreeRoot: IReadableNode): Boolean {
                    return true
                }

                override fun needsSynchronization(node: IReadableNode): Boolean {
                    return true
                }

                override fun filterTargetChildren(
                    parent: IWritableNode,
                    role: IChildLinkReference,
                    children: List<IWritableNode>,
                ): List<IWritableNode> {
                    return children.filter { childFilter(it.asLegacyNode()) }
                }
            },
            sourceRoot = sourceNodes.first(),
            targetRoot = targetNodes.first(),
            nodeAssociation = nodeAssociation,
        ).synchronize(sourceNodes, targetNodes)
    }
}

internal fun NodeData.originalId(): String? {
    return properties[NodeData.ID_PROPERTY_KEY] ?: id
}

data class ExistingAndExpectedNode(
    val existingNode: INode,
    val expectedNodeData: ModelData,
)

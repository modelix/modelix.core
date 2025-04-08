package org.modelix.model.lazy

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.datastructures.objects.IObjectGraph
import org.modelix.datastructures.objects.Object
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.datastructures.objects.asObject
import org.modelix.model.IVersion
import org.modelix.model.TreeType
import org.modelix.model.api.INodeReference
import org.modelix.model.api.ITree
import org.modelix.model.api.PNodeReference
import org.modelix.model.operations.IOperation
import org.modelix.model.operations.SetReferenceOp
import org.modelix.model.persistent.CPTree
import org.modelix.model.persistent.CPVersion
import org.modelix.model.persistent.OperationsList

class VersionBuilder {
    private var id: Long? = null
    private var author: String? = null
    private var time: String? = null
    private var treeRefs: MutableMap<TreeType, Object<CPTree>> = mutableMapOf()
    private var baseVersion: ObjectReference<CPVersion>? = null
    private var mergedVersion1: ObjectReference<CPVersion>? = null
    private var mergedVersion2: ObjectReference<CPVersion>? = null
    private var operations: List<IOperation>? = null
    private var operationsHash: ObjectReference<OperationsList>? = null
    private var numberOfOperations: Int = 0
    private var graph: IObjectGraph? = null
    private var attributes: Map<String, String> = emptyMap()

    @Deprecated("Not mandatory anymore. Usages of the ID should be replaced by the ObjectHash.")
    fun id(value: Long) = also { this.id = value }

    fun author(value: String?) = also { this.author = value }
    fun time(value: String?) = also { this.time = value }
    fun time(value: Instant) = time(value.epochSeconds.toString())
    fun currentTime() = time(Clock.System.now())
    fun tree(type: TreeType, value: Object<CPTree>) = also {
        this.treeRefs[type] = value
        if (it.graph == null) it.graph = value.graph
    }
    fun tree(value: Object<CPTree>) = tree(TreeType.Companion.MAIN, value)
    fun tree(value: ITree) = tree(value.asObject() as Object<CPTree>)
    fun tree(value: IGenericModelTree<*>) = tree(value.asObject() as Object<CPTree>)
    fun graph(value: IObjectGraph?) = also { it.graph = value }

    fun regularUpdate(baseVersion: IVersion) = regularUpdate((baseVersion as CLVersion).obj.ref)
    fun baseVersion(baseVersion: IVersion?) = regularUpdate((baseVersion as CLVersion?)?.obj?.ref)

    fun regularUpdate(baseVersion: ObjectReference<CPVersion>?) = also {
        it.baseVersion = baseVersion
        it.mergedVersion1 = null
        it.mergedVersion2 = null
        if (baseVersion != null && it.graph == null) it.graph = baseVersion.graph
    }

    fun autoMerge(base: IVersion, version1: IVersion, version2: IVersion) = also {
        autoMerge((base as CLVersion).obj.ref, (version1 as CLVersion).obj.ref, (version2 as CLVersion).obj.ref)
    }

    fun autoMerge(base: ObjectReference<CPVersion>, version1: ObjectReference<CPVersion>, version2: ObjectReference<CPVersion>) = also {
        it.baseVersion = base
        it.mergedVersion1 = version1
        it.mergedVersion2 = version2
        if (it.graph == null) it.graph = base.graph
    }

    fun operations(operations: List<IOperation>) = operations(operations.toList().toTypedArray())

    fun operations(operations: Array<IOperation>) = also {
        val tree = checkNotNull(treeRefs[TreeType.Companion.MAIN]) { "Specify the tree data first" }
        val compressedOps = OperationsCompressor(tree)
            .compressOperations(operations)
        val localizedOps = localizeOps(compressedOps.toList(), tree)
        val graph = tree.graph
        if (localizedOps.size <= CLVersion.Companion.INLINED_OPS_LIMIT) {
            this.operations = localizedOps
            this.operationsHash = null
            this.numberOfOperations = localizedOps.size
        } else {
            val opsList = OperationsList.Companion.of(localizedOps, graph)
            this.operations = null
            this.operationsHash = graph.fromCreated(opsList)
            this.numberOfOperations = localizedOps.size
        }
    }

    fun attribute(key: String, value: String) = also {
        attributes += key to value
    }

    fun buildData() = CPVersion(
        id = id ?: 0,
        time = time,
        author = author,
        treeRefs = treeRefs.also {
            checkNotNull(it[TreeType.Companion.MAIN]) { "tree data not specified" }
        }.mapValues { it.value.ref },
        previousVersion = null,
        originalVersion = null,
        baseVersion = baseVersion,
        mergedVersion1 = mergedVersion1,
        mergedVersion2 = mergedVersion2,
        operations = operations,
        operationsHash = operationsHash,
        numberOfOperations = numberOfOperations,
        attributes = attributes,
    )

    fun build(): IVersion {
        return buildLegacy()
    }

    fun buildLegacy(): CLVersion {
        return CLVersion(buildData().asObject(checkNotNull(graph) { "object graph not specified" }))
    }

    private fun localizeNodeRef(ref: INodeReference?, tree: Object<CPTree>): INodeReference? {
        return if (ref is PNodeReference && ref.treeId == tree.data.id.id) ref.toLocal() else ref
    }

    private fun localizeOps(ops: List<IOperation>, tree: Object<CPTree>): List<IOperation> {
        return ops.map {
            when (it) {
                is SetReferenceOp -> it.withTarget(localizeNodeRef(it.target, tree))
                else -> it
            }
        }
    }
}

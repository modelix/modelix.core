package org.modelix.model.server.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.modelix.model.api.IConceptReference

typealias NodeId = String
typealias ChangeSetId = Int

@Serializable
data class NodeData(
    val nodeId: NodeId,
    val concept: String? = null,
    val parent: NodeId? = null,
    val role: String? = null,
    val properties: Map<String, String> = emptyMap(),
    val references: Map<String, String> = emptyMap(),
    val children: Map<String?, List<NodeId>> = emptyMap()
)

@Serializable
data class NodeUpdateData(
    val nodeId: NodeId?,
    val temporaryNodeId: NodeId? = null,
    //val parent: NodeId? = null,
    //val role: String? = null,
    //val index: Int? = null, // when a new node is created, this is the position in the parent
    val concept: String? = null,
    val references: Map<String, String?>? = null,
    val properties: Map<String, String?>? = null,
    val children: Map<String?, List<NodeId>>? = null
) {
    fun replaceIds(replacer: (String)->String?): NodeUpdateData {
        val replaceOrKeep: (String)->String = { replacer(it) ?: it }

        val newNodeId = nodeId ?: temporaryNodeId?.let(replacer)
        return NodeUpdateData(
            nodeId = newNodeId,
            temporaryNodeId = temporaryNodeId,
//            parent = parent?.let(replaceOrKeep),
//            role = role,
//            index = index,
            concept = concept,
            references = references?.mapValues { it.value?.let(replaceOrKeep) },
            properties = properties,
            children = children?.mapValues { it.value.map(replaceOrKeep) }
        )
    }

    fun withReference(referenceRole: String, target: NodeId?) = NodeUpdateData(
        nodeId = nodeId,
        temporaryNodeId = temporaryNodeId,
//        parent = parent,
//        role = role,
//        index = index,
        concept = concept,
        references = (references ?: emptyMap()) + (referenceRole to target),
        properties = properties,
        children = children
    )
    fun withProperty(propertyRole: String, value: String?) = NodeUpdateData(
        nodeId = nodeId,
        temporaryNodeId = temporaryNodeId,
//        parent = parent,
//        role = role,
//        index = index,
        concept = concept,
        references = references,
        properties = (properties ?: emptyMap()) + (propertyRole to value),
        children = children
    )
    fun withChildren(childrenRole: String?, newChildren: List<NodeId>?) = NodeUpdateData(
        nodeId = nodeId,
        temporaryNodeId = temporaryNodeId,
//        parent = parent,
//        role = role,
//        index = index,
        concept = concept,
        references = references,
        properties = properties,
        children = (children ?: emptyMap()) + (childrenRole to (newChildren ?: emptyList()))
    )
    fun withConcept(newConcept: IConceptReference?): NodeUpdateData {
        return NodeUpdateData(
            nodeId = nodeId,
            temporaryNodeId = temporaryNodeId,
//        parent = parent,
//        role = role,
//        index = index,
            concept = newConcept?.getUID(),
            references = references,
            properties = properties,
            children = children
        )
    }

//    fun withContainment(newParent: NodeId, newRole: String?, newIndex: Int) = NodeUpdateData(
//        nodeId = nodeId,
//        temporaryNodeId = temporaryNodeId,
////        parent = newParent,
////        role = newRole,
////        index = newIndex,
//        concept = concept,
//        references = references,
//        properties = properties,
//        children = children
//    )

    companion object {
        fun newNode(tempId: NodeId, /*parent: NodeId, role: String?, index: Int,*/ concept: String?) = NodeUpdateData(
            nodeId = null,
            temporaryNodeId = tempId,
//            parent = parent,
//            role = role,
//            index = index,
            concept = concept,
            references = null,
            properties = null,
            children = null
        )

        fun nothing(nodeId: NodeId) = NodeUpdateData(nodeId = nodeId)
    }
}

@Serializable
data class VersionData(
    val repositoryId: String? = null,
    val versionHash: String? = null,
    val rootNodeId: String? = null,
    val nodes: List<NodeData>,
)

fun VersionData.merge(older: VersionData): VersionData {
    val mergedNodes: Map<NodeId, NodeData> = older.nodes.associateBy { it.nodeId } + nodes.associateBy { it.nodeId }
    val deletedNodes: List<NodeData> = mergedNodes.values.filter { node ->
        val parent = node.parent?.let { mergedNodes[it] } ?: return@filter true
        !parent.children.values.flatten().contains(node.nodeId)
    }
    val deletedAndDescendants = deletedNodes.asSequence()
        .flatMap { mergedNodes.descendants(it.nodeId, true) }.toSet()
    val filteredMergedNodes = mergedNodes - deletedAndDescendants
    return VersionData(
        repositoryId = repositoryId ?: older.repositoryId,
        versionHash = versionHash,
        rootNodeId = rootNodeId ?: older.rootNodeId,
        nodes = filteredMergedNodes.values.toList()
    )
}

private fun Map<NodeId, NodeData>.descendants(nodeId: NodeId, includeSelf: Boolean): Sequence<NodeId> {
    return if (includeSelf) {
        sequenceOf(nodeId) + descendants(nodeId, false)
    } else {
        val data = this[nodeId] ?: return emptySequence()
        data.children.values.asSequence().flatten().flatMap { descendants(it, true) }
    }
}

@Serializable
data class MessageFromServer(
    val version: VersionData? = null,
    val replacedIds: Map<String, String>? = null,
    val includedChangeSets: List<ChangeSetId> = emptyList(),
    val exception: ExceptionData? = null
) {
    fun toJson() = Json.encodeToString(this)
    companion object {
        fun fromJson(json: String) = Json.decodeFromString<MessageFromServer>(json)
    }
}

@Serializable
data class MessageFromClient(
    val changeSetId: ChangeSetId,
    val changedNodes: List<NodeUpdateData>?,
) {
    fun toJson() = Json.encodeToString(this)
    companion object {
        fun fromJson(json: String) = Json.decodeFromString<MessageFromClient>(json)
    }
}

@Serializable
data class ExceptionData(
    val message: String,
    val stacktrace: List<String>,
    val cause: ExceptionData? = null
) {
    constructor(exception: Throwable) : this(
        exception.message ?: "",
        exception.stackTraceToString().lines(),
        if (exception.cause == exception) null else exception.cause?.let { ExceptionData(it) }
    )

    fun allMessages() = generateSequence(this) { it.cause }.map { it.message }

    override fun toString(): String {
        return stacktrace.joinToString("\n")
    }
}

fun NodeData.replaceReferences(f: (Map<String, String>)->Map<String, String>): NodeData {
    return NodeData(
        nodeId = nodeId,
        concept = concept,
        parent = parent,
        role = role,
        properties = properties,
        references = f(references),
        children = children
    )
}

fun NodeData.replaceChildren(f: (Map<String?, List<String>>)->Map<String?, List<String>>): NodeData {
    return NodeData(
        nodeId = nodeId,
        concept = concept,
        parent = parent,
        role = role,
        properties = properties,
        references = references,
        children = f(children)
    )
}

fun NodeData.replaceChildren(role: String?, f: (List<String>)->List<String>): NodeData {
    return replaceChildren { allOldChildren ->
        val oldChildren = (allOldChildren[role] ?: emptyList()).toMutableList()
        val newChildren = f(oldChildren)
        allOldChildren + (role to newChildren)
    }
}

fun NodeData.replaceContainment(newParent: NodeId?, newRole: String?): NodeData {
    return NodeData(
        nodeId = nodeId,
        concept = concept,
        parent = newParent,
        role = newRole,
        properties = properties,
        references = references,
        children = children
    )
}

fun NodeData.replaceId(newId: String): NodeData {
    return NodeData(
        nodeId = newId,
        concept = concept,
        parent = parent,
        role = role,
        properties = properties,
        references = references,
        children = children
    )
}

fun NodeData.allReferencedIds(): List<NodeId> {
    return children.values.flatten() + references.values + listOfNotNull(parent)
}

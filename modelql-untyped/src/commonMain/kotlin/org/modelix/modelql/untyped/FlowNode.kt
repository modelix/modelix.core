package org.modelix.modelql.untyped

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import org.modelix.model.api.*

interface IFlowNode : INode {
    fun getParentAsFlow(): Flow<INode> = flowOf(parent).filterNotNull()
    fun getPropertyValueAsFlow(role: IProperty): Flow<String?> = flowOf(getPropertyValue(role))
    fun getAllChildrenAsFlow(): Flow<INode> = allChildren.asFlow()
    fun getChildrenAsFlow(role: IChildLink): Flow<INode> = getChildren(role).asFlow()
    fun getReferenceTargetAsFlow(role: IReferenceLink): Flow<INode> = flowOf(getReferenceTarget(role)).filterNotNull()
    fun getReferenceTargetRefAsFlow(role: IReferenceLink): Flow<INodeReference> = flowOf(getReferenceTargetRef(role)).filterNotNull()

    @OptIn(FlowPreview::class)
    fun getDescendantsAsFlow(includeSelf: Boolean = false): Flow<INode> {
        return if (includeSelf) {
            flowOf(flowOf(this), getDescendantsAsFlow(false)).flattenConcat()
        } else {
            getAllChildrenAsFlow().flatMapConcat { it.asFlowNode().getDescendantsAsFlow(true) }
        }
    }
}

class FlowNodeAdapter(val node: INode) : IFlowNode, INode by node

fun INode.asFlowNode(): IFlowNode = if (this is IFlowNode) this else FlowNodeAdapter(this)

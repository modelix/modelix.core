package org.modelix.model.client2

import INodeJS
import INodeReferenceJS
import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchListener
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.ITree
import org.modelix.model.api.ITreeChangeVisitor
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.getRootNode
import org.modelix.model.area.getArea

internal class BranchJSImpl(
    private val branch: IBranch,
) : BranchJS {

    private val changeHandlers = mutableSetOf<ChangeHandler>()

    private val jsRootNode = toNodeJs(branch.getRootNode())
    private val changeListener = ChangeListener(branch) { change ->
        changeHandlers.forEach {
                changeHandler ->
            changeHandler(change)
        }
    }

    init {
        branch.addListener(changeListener)
    }

    override val rootNode: INodeJS
        get() {
            return jsRootNode
        }

    override fun resolveNode(reference: INodeReferenceJS): INodeJS? {
        val referenceObject = INodeReferenceSerializer.deserialize(reference as String)
        return branch.getArea().resolveNode(referenceObject)?.let(::toNodeJs)
    }

    override fun addListener(handler: ChangeHandler) {
        changeHandlers.add(handler)
    }
    override fun removeListener(handler: ChangeHandler) {
        changeHandlers.remove(handler)
    }
}

internal class ChangeListener(private val branch: IBranch, private val changeCallback: (ChangeJS) -> Unit) : IBranchListener {

    fun nodeIdToInode(nodeId: Long): INodeJS {
        return toNodeJs(PNodeAdapter(nodeId, branch))
    }

    override fun treeChanged(oldTree: ITree?, newTree: ITree) {
        if (oldTree == null) {
            return
        }
        newTree.visitChanges(
            oldTree,
            object : ITreeChangeVisitor {
                override fun containmentChanged(nodeId: Long) {
                    changeCallback(ContainmentChanged(nodeIdToInode(nodeId)))
                }

                override fun conceptChanged(nodeId: Long) {
                    changeCallback(ConceptChanged(nodeIdToInode(nodeId)))
                }

                override fun childrenChanged(nodeId: Long, role: String?) {
                    changeCallback(ChildrenChanged(nodeIdToInode(nodeId), role))
                }

                override fun referenceChanged(nodeId: Long, role: String) {
                    changeCallback(ReferenceChanged(nodeIdToInode(nodeId), role))
                }

                override fun propertyChanged(nodeId: Long, role: String) {
                    changeCallback(PropertyChanged(nodeIdToInode(nodeId), role))
                }
            },
        )
    }
}

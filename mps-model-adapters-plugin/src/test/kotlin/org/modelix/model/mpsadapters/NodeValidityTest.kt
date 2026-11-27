package org.modelix.model.mpsadapters

import org.jetbrains.mps.openapi.model.SNode
import org.modelix.incremental.DependencyTracking
import org.modelix.incremental.IDependencyListener
import org.modelix.incremental.IStateVariableGroup
import org.modelix.incremental.IStateVariableReference

/**
 * An [MPSWritableNode] is valid while its MPS node is part of a model.
 */
class NodeValidityTest : MpsAdaptersTestBase("SimpleProject") {

    private fun rootNode(): SNode = mpsProject.projectModules
        .single { it.moduleName == "Solution1" }
        .models.single { it.name.longName == "Solution1.model1" }
        .rootNodes.single()

    /**
     * A child of the root node that has children itself.
     */
    private fun childWithChildren(): SNode = rootNode().children.first { it.children.any() }

    fun `test a node in a model is valid`() = readAction {
        val child = childWithChildren()
        assertTrue(MPSWritableNode(rootNode()).isValid())
        assertTrue(MPSWritableNode(child).isValid())
        assertTrue(MPSWritableNode(child.children.first()).isValid())
    }

    fun `test a node that was never added to a model is invalid`() = readAction {
        assertFalse(MPSWritableNode(jetbrains.mps.smodel.SNode(rootNode().concept)).isValid())
    }

    fun `test a removed node and its descendants are invalid`() = runCommandOnEDT {
        val child = childWithChildren()
        val grandChild = child.children.first()
        rootNode().removeChild(child)
        assertFalse(MPSWritableNode(child).isValid())
        assertFalse(MPSWritableNode(grandChild).isValid())
    }

    fun `test a removed root node is invalid`() = runCommandOnEDT {
        val root = rootNode()
        root.model!!.removeRootNode(root)
        assertFalse(MPSWritableNode(root).isValid())
    }

    /**
     * Removing a node notifies the containment of that node only, which a descendant doesn't have to access
     * for anything else.
     */
    fun `test the validity of a node depends on the containment of its ancestors`() = readAction {
        val child = childWithChildren()
        val accessed = ArrayList<IStateVariableReference<*>>()
        val listener = object : IDependencyListener {
            override fun accessed(key: IStateVariableReference<*>) {
                accessed.add(key)
            }
            override fun modified(key: IStateVariableReference<*>) {}
            override fun parentGroupChanged(childGroup: IStateVariableGroup) {}
        }
        DependencyTracking.registerListener(listener)
        try {
            MPSWritableNode(child.children.first()).isValid()
        } finally {
            DependencyTracking.removeListener(listener)
        }
        assertTrue(accessed.toString(), accessed.contains(MPSContainmentDependency(child)))
        assertTrue(accessed.toString(), accessed.contains(MPSContainmentDependency(rootNode())))
    }
}

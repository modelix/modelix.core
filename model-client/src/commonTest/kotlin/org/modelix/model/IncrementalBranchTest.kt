package org.modelix.model

import org.modelix.incremental.IncrementalEngine
import org.modelix.incremental.incrementalFunction
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.IProperty
import org.modelix.model.api.IReplaceableNode
import org.modelix.model.api.ITree
import org.modelix.model.api.PBranch
import org.modelix.model.api.getRootNode
import org.modelix.model.client.IdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals

class IncrementalBranchTest {

    @Test
    fun propertyChange() {
        val incrementalBranch = initializeIncrementalBranch()
        val rootNode = incrementalBranch.getRootNode()
        incrementalBranch.runWrite { rootNode.setPropertyValue(IProperty.fromName("name"), "abc") }
        val engine = IncrementalEngine()
        try {
            var callCount = 0
            val nameWithSuffix = engine.incrementalFunction("f") { _ ->
                callCount++
                val name = rootNode.getPropertyValue(IProperty.fromName("name"))
                name + "Suffix"
            }
            assertEquals(callCount, 0)
            assertEquals("abcSuffix", incrementalBranch.computeRead { nameWithSuffix() })
            assertEquals(callCount, 1)
            assertEquals("abcSuffix", incrementalBranch.computeRead { nameWithSuffix() })
            assertEquals(callCount, 1)
            incrementalBranch.runWrite { rootNode.setPropertyValue(IProperty.fromName("name"), "xxx") }
            assertEquals(callCount, 1)
            assertEquals("xxxSuffix", incrementalBranch.computeRead { nameWithSuffix() })
            assertEquals(callCount, 2)
        } finally {
            engine.dispose()
        }
    }

    @Test
    fun conceptChange() {
        val incrementalBranch = initializeIncrementalBranch()
        val root = incrementalBranch.getRootNode() as IReplaceableNode
        incrementalBranch.runWrite { root.replaceNode(ConceptReference("myConcept")) }
        val engine = IncrementalEngine()

        try {
            var callCount = 0
            val conceptUidWithSuffix = engine.incrementalFunction("f") { _ ->
                callCount++
                val conceptRef = incrementalBranch.getRootNode().getConceptReference()?.getUID()
                conceptRef + "Suffix"
            }
            assertEquals(callCount, 0)
            assertEquals("myConceptSuffix", incrementalBranch.computeRead { conceptUidWithSuffix() })
            assertEquals(callCount, 1)
            assertEquals("myConceptSuffix", incrementalBranch.computeRead { conceptUidWithSuffix() })
            assertEquals(callCount, 1)
            incrementalBranch.runWrite {
                (incrementalBranch.getRootNode() as IReplaceableNode).replaceNode(
                    ConceptReference("myConcept2"),
                )
            }
            assertEquals(callCount, 1)
            assertEquals("myConcept2Suffix", incrementalBranch.computeRead { conceptUidWithSuffix() })
            assertEquals(callCount, 2)
        } finally {
            engine.dispose()
        }
    }

    @Test
    fun addNewChild_modifies_containsNode() {
        val childId = 12345L // 0x3039
        val branch = PBranch(ModelFacade.newLocalTree(), IdGenerator.getInstance(17865))
        val incrementalBranch = IncrementalBranch(branch)
        val engine = IncrementalEngine()
        try {
            val containsChild = engine.incrementalFunction("f") { _ ->
                incrementalBranch.transaction.tree.containsNode(childId)
            }
            assertEquals(false, branch.computeReadT { it.tree.containsNode(childId) })
            assertEquals(false, incrementalBranch.computeRead { containsChild() })
            incrementalBranch.runWriteT { it.addNewChild(ITree.ROOT_ID, "role1", -1, childId, null as IConceptReference?) }
            assertEquals(true, branch.computeReadT { it.tree.containsNode(childId) })
            assertEquals(true, incrementalBranch.computeRead { containsChild() })
        } finally {
            engine.dispose()
        }
    }

    private fun initializeIncrementalBranch(): IncrementalBranch {
        val branch = PBranch(ModelFacade.newLocalTree(), IdGenerator.getInstance(17865))
        val incrementalBranch = IncrementalBranch(branch)
        return incrementalBranch
    }
}

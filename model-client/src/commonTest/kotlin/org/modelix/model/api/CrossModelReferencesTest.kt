package org.modelix.model.api

import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.model.mutable.asModel
import org.modelix.model.mutable.asMutableThreadSafe
import kotlin.test.Test
import kotlin.test.assertEquals

class CrossModelReferencesTest {

    @Test
    fun `cross model reference resolution`() {
        val modelA = IGenericModelTree.builder().withNodeReferenceIds().build().asMutableThreadSafe().asModel()
        val modelB = IGenericModelTree.builder().withNodeReferenceIds().build().asMutableThreadSafe().asModel()

        val nameRole = IPropertyReference.fromName("name")
        val refRole = IReferenceLinkReference.fromName("crossModelRef")
        modelA.executeWrite {
            modelA.getRootNode().setPropertyValue(nameRole, "nodeA")
            modelA.getRootNode().setReferenceTarget(refRole, modelB.getRootNode())
        }
        modelB.executeWrite {
            modelB.getRootNode().setPropertyValue(nameRole, "nodeB")
        }

        val model = CompositeModel(modelA, modelB)

        model.executeRead {
            assertEquals(2, model.getRootNodes().size)
            assertEquals("nodeA", model.getRootNodes()[0].getPropertyValue(nameRole))
            assertEquals("nodeB", model.getRootNodes()[1].getPropertyValue(nameRole))
            assertEquals(model.getRootNodes()[1], model.getRootNodes()[0].getReferenceTarget(refRole))
            assertEquals(model.getRootNodes()[1].getNodeReference(), model.getRootNodes()[0].getReferenceTarget(refRole)?.getNodeReference())
            assertEquals("nodeB", model.getRootNodes()[0].getReferenceTarget(refRole)?.getPropertyValue(nameRole))
        }

        model.executeWrite {
            assertEquals(model.getRootNodes()[1], model.getRootNodes()[0].getReferenceTarget(refRole))
        }
    }

    @Test
    fun `cross model reference resolution with auto transactions`() {
        val modelA = IGenericModelTree.builder().withNodeReferenceIds().build().asMutableThreadSafe().asModel()
        val modelB = IGenericModelTree.builder().withNodeReferenceIds().build().asMutableThreadSafe().asModel()

        val nameRole = IPropertyReference.fromName("name")
        val refRole = IReferenceLinkReference.fromName("crossModelRef")
        modelA.executeWrite {
            modelA.getRootNode().setPropertyValue(nameRole, "nodeA")
            modelA.getRootNode().setReferenceTarget(refRole, modelB.getRootNode())
        }
        modelB.executeWrite {
            modelB.getRootNode().setPropertyValue(nameRole, "nodeB")
        }

        val model = CompositeModel(modelA, modelB).withAutoTransactions()

        assertEquals(2, model.getRootNodes().size)
        assertEquals("nodeA", model.getRootNodes()[0].getPropertyValue(nameRole))
        assertEquals("nodeB", model.getRootNodes()[1].getPropertyValue(nameRole))
        assertEquals(model.getRootNodes()[1], model.getRootNodes()[0].getReferenceTarget(refRole))
        assertEquals(model.getRootNodes()[1].getNodeReference(), model.getRootNodes()[0].getReferenceTarget(refRole)?.getNodeReference())
        assertEquals("nodeB", model.getRootNodes()[0].getReferenceTarget(refRole)?.getPropertyValue(nameRole))
    }
}

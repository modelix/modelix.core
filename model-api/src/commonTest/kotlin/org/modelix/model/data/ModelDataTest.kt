package org.modelix.model.data

import org.modelix.model.api.ChildLinkFromName
import org.modelix.model.api.ConceptReference
import org.modelix.model.api.INode
import org.modelix.model.api.TreePointer
import org.modelix.model.api.getRootNode
import org.modelix.model.async.LegacyKeyValueStoreAsAsyncStore
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.persistent.MapBasedStore
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelDataTest {

    @Test
    fun nodeWithConceptReferenceButWithoutRegisteredConceptCanSerialized() {
        val aChildLink = ChildLinkFromName("aChildLink")
        val aConceptReference = ConceptReference("aConceptReference")
        val rootNode = createEmptyRootNode()
        rootNode.addNewChild(aChildLink, -1, aConceptReference)

        val nodeData = rootNode.asData()

        val child = nodeData.children.single { child -> child.role == aChildLink.getUID() }
        assertEquals(aConceptReference.getUID(), child.concept)
    }
}

internal fun createEmptyRootNode(): INode {
    val tree = CLTree.builder(LegacyKeyValueStoreAsAsyncStore(MapBasedStore())).build()
    val branch = TreePointer(tree, IdGenerator.getInstance(1))
    return branch.getRootNode()
}

package org.modelix.model.client2

import org.modelix.datastructures.model.IGenericModelTree
import org.modelix.model.api.IPropertyReference
import org.modelix.model.lazy.createObjectStoreCache
import org.modelix.model.mutable.asMutableThreadSafe
import org.modelix.model.persistent.MapBasedStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoTransactionsInMutableModelTreeJsTest {

    @Test
    fun `auto transactions in listener on MutableModelTreeJs `() {
        val modelTreeJs = IGenericModelTree.builder()
            .graph(createObjectStoreCache(MapBasedStore()).asObjectGraph())
            .withNodeReferenceIds()
            .build()
            .asMutableThreadSafe()
            .let { MutableModelTreeJsImpl(it) }
        var listenerSuccessful = false
        val listener: (ChangeJS) -> Unit = { change ->
            assertEquals("abc", change.node.getPropertyValue(IPropertyReference.fromName("name")))
            listenerSuccessful = true
        }
        modelTreeJs.addListener(listener)
        modelTreeJs.rootNode.setPropertyValue(IPropertyReference.fromName("name"), "abc")
        assertTrue(listenerSuccessful)
    }
}

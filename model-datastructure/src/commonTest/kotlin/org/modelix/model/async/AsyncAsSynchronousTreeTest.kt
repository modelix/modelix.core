package org.modelix.model.async

import org.modelix.model.api.ITree
import org.modelix.model.api.NullChildLinkReference
import org.modelix.model.api.meta.NullConcept
import org.modelix.model.lazy.NonCachingObjectStore
import org.modelix.model.lazy.createNewTreeData
import org.modelix.model.persistent.MapBasedStore
import org.modelix.streams.StreamAssertionError
import org.modelix.streams.getSynchronous
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AsyncAsSynchronousTreeTest {

    private val asyncStore = NonCachingObjectStore(MapBasedStore()).getAsyncStore()
    private val cpTree = createNewTreeData(asyncStore)
    val tree = AsyncTree(cpTree, asyncStore)

    @Test
    fun failWhenDuplicateIDsAreSpecified() {
        val concepts = arrayOf(NullConcept.getReference(), NullConcept.getReference())
        val ids = longArrayOf(1L, 1L)

        val exception = assertFailsWith<IllegalArgumentException> {
            tree.addNewChildren(ITree.ROOT_ID, NullChildLinkReference, 0, ids, concepts).getSynchronous()
        }

        assertEquals("The specified IDs are not unique.", exception.message)
    }

    @Test
    fun failWhenNotSameNumberOfIDsAndConceptsIsSpecified() {
        val concepts = arrayOf(NullConcept.getReference(), NullConcept.getReference())
        val ids: LongArray = longArrayOf(1L)

        val exception = assertFailsWith<IllegalArgumentException> {
            tree.addNewChildren(ITree.ROOT_ID, NullChildLinkReference, 0, ids, concepts).getSynchronous()
        }

        assertEquals("The number of IDs and concepts should be the same. 1 IDs and 2 concepts provided.", exception.message)
    }

    @Test
    fun failWhenIdAlreadyExists() {
        val concepts = arrayOf(NullConcept.getReference())
        val ids: LongArray = longArrayOf(ITree.ROOT_ID)

        val exception = assertFailsWith<StreamAssertionError> {
            tree.addNewChildren(ITree.ROOT_ID, NullChildLinkReference, 0, ids, concepts).getSynchronous()
        }

        assertEquals("Node with ID 1 already exists.", exception.message)
    }
}

/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.modelix.model.DummyKeyValueStore
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.client.IdGenerator
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.CLVersion
import org.modelix.model.lazy.NonCachingObjectStore
import org.modelix.model.lazy.ObjectNotLoadedException
import org.modelix.model.lazy.load
import org.modelix.model.lazy.runWrite
import org.modelix.model.lazy.writeToMap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PartialLoadingTest {

    @Test
    fun can_load_version_without_store_access() {
        val idGenerator = IdGenerator.newInstance(101)
        val initialTree = CLTree.builder(NonCachingObjectStore(DummyKeyValueStore())).build()

        val v0 = CLVersion.createRegularVersion(
            id = idGenerator.generate(),
            tree = initialTree,
            author = null,
            baseVersion = null,
            operations = emptyArray(),
        )
        val v0objects = v0.dataRef.writeToMap()
        assertEquals(5, v0objects.size) // CPVersion, CPTree, CPHamtSingle, CPHamtLeaf, CPNode

        val v1a = v0.runWrite(idGenerator, null) { t ->
            val gen = RandomTreeChangeGenerator(idGenerator, Random(87545))
                .growingOperationsOnly()
            repeat(10) { gen.applyRandomChange(t, null) }
            t.addNewChild(ITree.ROOT_ID, "children", -1, null as IConceptReference?)
        }

        val v1objects = v1a.dataRef.writeToMap()
        assertEquals(18, v1objects.size)

        // Only new objects should be written. Objects that existed during an earlier write are already
        // known to the server.
        assertEquals(emptySet(), v1objects.keys.intersect(v0objects.keys))

        // Recreate the version from serialized objects. This simulates the replication on a second client.
        val v1b = CLVersion.loadFromHash(v1a.getContentHash(), NonCachingObjectStore(DummyKeyValueStore()))

        // The version was created, but none of its data is loaded, and it also can't lazy load it from the dummy store.
        assertFailsWith(ObjectNotLoadedException::class) { v1b.getTree() }
        assertFailsWith(ObjectNotLoadedException::class) { v1b.getTree().getChildren(ITree.ROOT_ID, "children") }

        // Now load the whole object graph.
        v1b.load(v1objects + v0objects)

        // The original version v1a and the restored version v1b should be same.
        assertEquals(
            v1a.getTree().getChildren(ITree.ROOT_ID, "children").last(),
            v1b.getTree().getChildren(ITree.ROOT_ID, "children").last(),
        )

        // Iterate the whole tree to check that all objects are loaded.
        v1b.getTree().getDescendants(ITree.ROOT_ID, true).count()
    }
}

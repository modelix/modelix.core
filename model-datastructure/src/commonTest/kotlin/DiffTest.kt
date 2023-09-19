import org.modelix.model.api.ConceptReference
import org.modelix.model.api.ITree
import org.modelix.model.api.NodeReference
import org.modelix.model.lazy.CLTree
import org.modelix.model.lazy.ObjectStoreCache
import org.modelix.model.persistent.MapBaseStore
import kotlin.test.Test
import kotlin.test.assertEquals

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

class DiffTest {

    @Test
    fun changeProperty() {
        runTest(
            setOf(
                TreeChangeCollector.PropertyChangedEvent(ITree.ROOT_ID, "name"),
            ),
        ) {
            it.setProperty(ITree.ROOT_ID, "name", "a")
        }
    }

    @Test
    fun changeReference() {
        runTest(
            setOf(
                TreeChangeCollector.ReferenceChangedEvent(ITree.ROOT_ID, "ref1"),
            ),
        ) {
            it.setReferenceTarget(ITree.ROOT_ID, "ref1", NodeReference("abc"))
        }
    }

    @Test
    fun changeReferenceId() {
        runTest(
            setOf(
                TreeChangeCollector.ReferenceChangedEvent(ITree.ROOT_ID, "ref1"),
            ),
        ) {
            it.setReferenceTarget(ITree.ROOT_ID, "ref1", 123)
        }
    }

    @Test
    fun addChild() {
        runTest(
            setOf(
                TreeChangeCollector.ChildrenChangedEvent(ITree.ROOT_ID, "children1"),
                TreeChangeCollector.NodeAddedEvent(100),
            ),
        ) {
            it.addNewChild(ITree.ROOT_ID, "children1", -1, 100, null as ConceptReference?)
        }
    }

    @Test
    fun removeChild() {
        runTest(
            setOf(
                TreeChangeCollector.ChildrenChangedEvent(ITree.ROOT_ID, "children1"),
                TreeChangeCollector.NodeRemovedEvent(100),
            ),
            {
                it.addNewChild(ITree.ROOT_ID, "children1", -1, 100, null as ConceptReference?)
            },
            {
                it.deleteNode(100)
            },
        )
    }

    private fun runTest(expectedEvents: Set<TreeChangeCollector.ChangeEvent>, mutator: (ITree) -> ITree) {
        runTest(expectedEvents, { it }, mutator)
    }

    private fun runTest(expectedEvents: Set<TreeChangeCollector.ChangeEvent>, initialMutator: (ITree) -> ITree, mutator: (ITree) -> ITree) {
        val tree1 = initialMutator(CLTree.builder(ObjectStoreCache(MapBaseStore())).build())
        val tree2 = mutator(tree1)
        val collector = TreeChangeCollector()
        tree2.visitChanges(tree1, collector)

        assertEquals(
            expectedEvents,
            collector.events.toSet(),
        )
    }
}

/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model

import org.modelix.model.api.ITree
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ExpectedTreeData {
    var expectedChildren: MutableMap<Pair<Long, String?>, MutableList<Long>> = HashMap()
    var expectedParents: MutableMap<Long, Long> = HashMap()
    var expectedRoles: MutableMap<Long, String> = HashMap()
    var expectedDeletes: MutableSet<Long> = HashSet()

    fun clone(): ExpectedTreeData {
        val clone = ExpectedTreeData()
        clone.expectedChildren.putAll(this.expectedChildren)
        clone.expectedChildren.keys.forEach { clone.expectedChildren[it] = ArrayList(clone.expectedChildren[it]!!) }
        clone.expectedParents.putAll(this.expectedParents)
        clone.expectedRoles.putAll(this.expectedRoles)
        clone.expectedDeletes.addAll(this.expectedDeletes)
        return clone
    }

    fun insertChild(parent: Long, role: String?, index: Int, child: Long) {
        val list = expectedChildren.getOrPut(Pair(parent, role), { ArrayList() })
        if (list.contains(child)) throw RuntimeException("Attempt to add a child twice")
        if (index == -1) {
            list.add(child)
        } else {
            list.add(index, child)
        }
    }

    fun removeChild(parent: Long, role: String?, child: Long) {
        val list = expectedChildren.getOrPut(Pair(parent, role), { ArrayList() })
        list.remove(child)
    }

    fun assertTree(tree: ITree) {
        for ((key, expectedParent) in this.expectedParents) {
            if (expectedParent == 0L) {
                assertFalse(tree.containsNode(key))
            } else {
                val actualParent = tree.getParent(key)
                assertEquals(expectedParent, actualParent)
            }
        }
        for ((key, value) in this.expectedChildren) {
            if (this.expectedDeletes.contains(key.first)) {
                continue
            }
            val expected = value.toList()
            val actual = tree.getChildren(key.first, key.second).toList()
            assertEquals(expected, actual)
        }
        for ((key, value) in this.expectedRoles) {
            assertEquals(value, tree.getRole(key))
        }
        for (node in this.expectedDeletes) {
            assertFalse(tree.containsNode(node))
        }
    }
}

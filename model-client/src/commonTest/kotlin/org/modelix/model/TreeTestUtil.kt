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
import kotlin.random.Random

class TreeTestUtil(private val tree: ITree, private val rand: Random) {
    fun getAncestors(descendant: Long, includeSelf: Boolean): Iterable<Long> {
        if (descendant == 0L) {
            return emptyList()
        }
        return if (includeSelf) {
            (sequenceOf(descendant) + getAncestors(descendant, false)).asIterable()
        } else {
            val parent = tree.getParent(descendant)
            getAncestors(parent, true)
        }
    }

    val allNodes: Iterable<Long>
        get() = getDescendants(ITree.ROOT_ID, true)

    val allNodesWithoutRoot: Iterable<Long>
        get() = getDescendants(ITree.ROOT_ID, false)

    fun getDescendants(parent: Long, includeSelf: Boolean): Iterable<Long> {
        return if (includeSelf) {
            sequenceOf(parent).plus(getDescendants(parent, false)).asIterable()
        } else {
            tree.getAllChildren(parent).flatMap { it: Long -> getDescendants(it, true) }
        }
    }

    val randomNodeWithoutRoot: Long
        get() = getRandomNode(allNodesWithoutRoot)

    val randomNodeWithRoot: Long
        get() = getRandomNode(allNodes)

    fun getRandomNode(nodes: Iterable<Long>): Long {
        return if (nodes.count() == 0) {
            0L
        } else {
            nodes.drop(rand.nextInt(nodes.count())).first()
        }
    }

    val randomLeafNode: Long
        get() = getRandomNode(allNodes.filter { tree.getAllChildren(it).count() == 0 })
}

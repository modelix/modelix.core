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

package org.modelix.mps.sync.neu

import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.NodeAsMPSNode
import java.util.concurrent.atomic.AtomicReference

class ITreeToSTreeTransformer(private val replicatedModel: ReplicatedModel) {

    fun transform(): SNode {
        val newTreeHolder = AtomicReference<SNode>()
        try {
            // TODO for each getChild level spawn new cooperating coroutines?
            // TODO transform each node to an SNode/SModule/SProject, etc. depending on which level we are
            // TODO first build this model tree only in memory, but do not connect to the (physical) model/project in MPS
            replicatedModel.getBranch().runReadT { transaction ->
                val allChildren = transaction.tree.getAllChildren(1L)
                // println("Number of children of root: ${allChildren.count()}")
                // val childrenOfRoot = allChildren.joinToString(", ") { it.toString() }
                // println("All children of root: $childrenOfRoot")

                allChildren.forEach { id ->
                    val iNode = PNodeAdapter.wrap(id, replicatedModel.getBranch())
                    val newTree = NodeAsMPSNode.wrap(iNode)!!
                    newTreeHolder.set(newTree)

                    println()
                    println("New SNode's name: ${newTree.name}")
                    println("New SNode's SModel: ${newTree.model}")
                    // println("New SNode's concept: ${newTree.concept}")
                    println()

                    println("Properties:")
                    newTree.properties.forEach { println("${it.name}: $it") }
                    println()

                    println("References:")
                    newTree.references.forEach { println("${it.link.name}: ${it.targetNodeId}") }
                    println()

                    println("Children:")
                    newTree.children.forEach { println("${it.name}: ${it.nodeId}") }
                    println("-----------------")
                }
            }
        } catch (ex: Exception) {
            println("${this.javaClass} exploded")
            ex.printStackTrace()
        }

        return newTreeHolder.get()
    }
}

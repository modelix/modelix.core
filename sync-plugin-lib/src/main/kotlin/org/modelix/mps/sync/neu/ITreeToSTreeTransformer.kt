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
import org.jetbrains.mps.openapi.module.SRepository
import org.modelix.model.api.ILanguageRepository
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.client2.ReplicatedModel
import org.modelix.model.mpsadapters.MPSLanguageRepository
import org.modelix.model.mpsadapters.NodeAsMPSNode
import java.util.concurrent.atomic.AtomicReference

class ITreeToSTreeTransformer(private val replicatedModel: ReplicatedModel, private val repository: SRepository) {

    fun transform(): SNode {
        val newTreeHolder = AtomicReference<SNode>()
        try {
            // 1. Register the language concepts so they are ready for lookup
            val mpsLanguageRepo = MPSLanguageRepository(repository)
            ILanguageRepository.register(mpsLanguageRepo)

            // 2. Traverse and transform the tree
            // TODO use coroutines instead of big-bang eager loading?
            replicatedModel.getBranch().runReadT { transaction ->
                val allChildren = transaction.tree.getAllChildren(1L)

                // TODO make sure that each INode is transformed to the correct SNode, SModule, SProject etc. class

                println("Level: 1")
                allChildren.forEach { id ->
                    val iNode = PNodeAdapter.wrap(id, replicatedModel.getBranch())!!
                    val newTree = NodeAsMPSNode.wrap(iNode, repository)!!
                    newTreeHolder.set(newTree)

                    printNode(newTree)
                    println("Parent was: ${newTree.name}")
                    traverse(iNode, 1)
                }
            }
        } catch (ex: Exception) {
            println("${this.javaClass} exploded")
            ex.printStackTrace()
        }

        return newTreeHolder.get()
    }

    fun traverse(parent: INode, level: Int) {
        println("Level: $level")
        parent.allChildren.forEach {
            val sNode = NodeAsMPSNode.wrap(parent)!!
            printNode(sNode)
            traverse(it, level + 1)
        }
    }

    private fun printNode(node: SNode) {
        println()
        println("New SNode's name: ${node.name}")
        println("New SNode's SModel: ${node.model}")
        // println("New SNode's concept: ${node.concept}")
        println()

        println("Properties:")
        node.properties.forEach { println("${it.name}: $it") }
        println()

        println("References:")
        node.references.forEach { println("${it.link.name}: ${it.targetNodeId}") }
        println()

        println("Children:")
        node.children.forEach { println("${it.name}: ${it.nodeId}") }
        println("-----------------")
    }
}

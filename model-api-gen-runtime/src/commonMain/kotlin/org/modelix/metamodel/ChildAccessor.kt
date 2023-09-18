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
package org.modelix.metamodel

import org.modelix.model.api.IChildLink
import org.modelix.model.api.IConcept
import org.modelix.model.api.INode
import kotlin.reflect.KClass

abstract class ChildAccessor<ChildT : ITypedNode>(
    protected val parent: INode,
    protected val role: IChildLink,
    protected val childConcept: IConcept,
    val childType: KClass<ChildT>,
) : Iterable<ChildT> {
    fun isEmpty(): Boolean = !iterator().hasNext()

    fun getSize(): Int {
        return this.count()
    }

    fun untypedNodes(): Iterable<INode> = parent.getChildren(role)

    override fun iterator(): Iterator<ChildT> {
        return untypedNodes().map {
            when (childConcept) {
                is GeneratedConcept<*, *> -> it.typed(childType)
                else -> throw RuntimeException("Unsupported concept type: ${childConcept::class} (${childConcept.getLongName()})")
            }
        }.iterator()
    }

    fun addNew(index: Int = -1): ChildT {
        return parent.addNewChild(role, index, childConcept).typed(childType)
    }

    fun <NewNodeT : ChildT> addNew(index: Int = -1, concept: INonAbstractConcept<NewNodeT>): NewNodeT {
        return parent.addNewChild(role, index, concept.untyped()).typed(concept.getInstanceClass())
    }

    fun <NewNodeT : ChildT> addNew(concept: INonAbstractConcept<NewNodeT>): NewNodeT {
        return addNew(-1, concept)
    }

    fun removeUnwrapped(child: INode) {
        parent.removeChild(child)
    }

    fun remove(child: TypedNodeImpl) {
        removeUnwrapped(child.unwrap())
    }
}

fun <ChildT : ITypedNode> ChildAccessor<ChildT>.filterLoaded(): List<ChildT> {
    return untypedNodes().asSequence().filter { it.isValid }.map { it.typed(childType) }.toList()
}

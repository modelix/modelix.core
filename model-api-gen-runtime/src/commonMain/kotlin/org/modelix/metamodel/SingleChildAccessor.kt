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

class SingleChildAccessor<ChildT : ITypedNode>(
    parent: INode,
    role: IChildLink,
    childConcept: IConcept,
    childType: KClass<ChildT>,
) : ChildAccessor<ChildT>(parent, role, childConcept, childType) {
    fun isSet(): Boolean = !isEmpty()
    fun get(): ChildT? = iterator().let { if (it.hasNext()) it.next() else null }
    fun <T> read(receiver: (ChildT?) -> T): T = receiver(get())
    fun setNew(): ChildT {
        get()?.let { parent.removeChild(it.unwrap()) }
        return addNew()
    }
    fun <NewChildT : ChildT> setNew(concept: INonAbstractConcept<NewChildT>): NewChildT {
        require(concept.untyped().isSubConceptOf(childConcept)) {
            "$concept is not a sub concept of $childConcept"
        }
        get()?.let { parent.removeChild(it.unwrap()) }
        return addNew(concept = concept)
    }
}

fun <NewChildT : ChildT, ChildT : ITypedNode> SingleChildAccessor<ChildT>.setNew(concept: INonAbstractConcept<NewChildT>, initializer: NewChildT.() -> Unit): NewChildT {
    return setNew(concept).apply(initializer)
}
fun <ChildT : ITypedNode> SingleChildAccessor<ChildT>.setNew(initializer: ChildT.() -> Unit): ChildT {
    return setNew().apply(initializer)
}

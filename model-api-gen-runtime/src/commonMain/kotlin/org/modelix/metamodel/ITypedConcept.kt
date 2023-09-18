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

import org.modelix.model.api.IConcept
import kotlin.reflect.KClass

interface ITypedConcept {
    fun untyped(): IConcept
}

@Deprecated("use .untyped()")
val ITypedConcept._concept: IConcept get() = untyped()

class FallbackTypedConcept(private val untypedConcept: IConcept) : ITypedConcept {
    override fun untyped(): IConcept = _concept
}

fun IConcept.typed(): ITypedConcept = when (this) {
    is GeneratedConcept<*, *> -> this.typed()
    else -> FallbackTypedConcept(this)
}

interface IConceptOfTypedNode<out NodeT : ITypedNode> : ITypedConcept {
    fun getInstanceInterface(): KClass<out NodeT>
}

@Deprecated("use .getInstanceInterface()")
fun <NodeT : ITypedNode> IConceptOfTypedNode<NodeT>.getInstanceClass(): KClass<out NodeT> {
    return getInstanceInterface()
}

interface INonAbstractConcept<out NodeT : ITypedNode> : IConceptOfTypedNode<NodeT>

interface ITypedConceptFeature

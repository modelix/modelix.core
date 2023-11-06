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

package org.modelix.mps.sync.util

import org.jetbrains.mps.openapi.language.SAbstractConcept
import org.jetbrains.mps.openapi.language.SContainmentLink
import org.jetbrains.mps.openapi.model.SNode
import org.modelix.model.api.index
import org.modelix.model.mpsadapters.MPSConcept
import org.modelix.model.mpsadapters.MPSNode
import org.modelix.model.mpsadapters.mps.NodeAsMPSNode

// status: ready to test

fun SNode.index() = MPSNode(this).index()

fun SNode.addNewChild(role: SContainmentLink) = this.addNewChild(role, -1, role.targetConcept)

fun SNode.addNewChild(role: SContainmentLink, index: Int, childConcept: SAbstractConcept?): SNode {
    val newChild = MPSNode.wrap(this)?.addNewChild(role.name, index, MPSConcept.wrap(childConcept))
        ?: throw RuntimeException("addNewChild has to return the created child node")
    return NodeAsMPSNode.wrap(newChild)!!
}

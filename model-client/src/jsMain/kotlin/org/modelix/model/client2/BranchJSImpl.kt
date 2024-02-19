/*
 * Copyright (c) 2024.
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

package org.modelix.model.client2

import INodeJS
import INodeReferenceJS
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.model.api.IBranch
import org.modelix.model.api.INodeReferenceSerializer
import org.modelix.model.api.getRootNode
import org.modelix.model.area.getArea

fun interface DisposeFunction {
    fun invoke()
}

@OptIn(UnstableModelixFeature::class)
class BranchJSImpl(
    private val dispose: DisposeFunction,
    private val branch: IBranch,
) : BranchJS {

    private val jsRootNode = toNodeJs(branch.getRootNode())

    override val rootNode: INodeJS
        get() {
            return jsRootNode
        }

    override fun resolveNode(reference: INodeReferenceJS): INodeJS? {
        val referenceObject = INodeReferenceSerializer.deserialize(reference as String)
        return branch.getArea().resolveNode(referenceObject)?.let(::toNodeJs)
    }

    override fun dispose() {
        dispose.invoke()
    }
}

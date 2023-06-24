/*
 * Copyright 2003-2023 JetBrains s.r.o.
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
package org.modelix.model.mpsadapters

import org.jetbrains.mps.openapi.model.SNodeReference
import org.modelix.model.api.INode
import org.modelix.model.api.INodeReference
import org.modelix.model.api.INodeResolutionScope
import org.modelix.model.area.IArea

data class MPSNodeReference(val ref: SNodeReference) : INodeReference {
    override fun resolveNode(area: IArea?): INode? {
        return super.resolveNode(area)
    }

    override fun resolveIn(scope: INodeResolutionScope): INode? {
        return super.resolveIn(scope)
    }
}
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

package org.modelix.model.async

import org.modelix.model.api.IBranch
import org.modelix.model.api.INode
import org.modelix.model.api.PNodeAdapter
import org.modelix.model.api.async.IAsyncNode
import org.modelix.model.api.async.INodeWithAsyncSupport
import org.modelix.model.lazy.CLTree

class PNodeAdapterWithAsyncSupport(nodeId: Long, branch: IBranch) : PNodeAdapter(nodeId, branch), INodeWithAsyncSupport {
    override fun getAsyncNode(): IAsyncNode {
        return AsyncNode(nodeId, (branch.transaction.tree as CLTree).asAsyncTree())
    }

    override fun createAdapter(id: Long): INode {
        return PNodeAdapterWithAsyncSupport(id, branch)
    }

    override fun createAdapter(node: INode): INode {
        return if (node is PNodeAdapter && node !is PNodeAdapterWithAsyncSupport && node.branch == this.branch) {
            PNodeAdapterWithAsyncSupport(nodeId, branch)
        } else {
            node
        }
    }
}

fun INode.withAsyncSupport(): INodeWithAsyncSupport {
    return when (this) {
        is INodeWithAsyncSupport -> this
        is PNodeAdapter -> PNodeAdapterWithAsyncSupport(this.nodeId, this.branch)
        else -> throw IllegalArgumentException("Not possible for this node type: $this")
    }
}
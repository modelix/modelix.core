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

package org.modelix.model.sync.bulk

import org.modelix.model.api.INode
import org.modelix.model.data.NodeData
import org.modelix.model.data.associateWithNotNull

/**
 * A ModelExporter exports a node and its subtree in bulk.
 */
expect class ModelExporter(root: INode)

/**
 * Returns a [NodeData] representation of the receiver node as it would be exported by a [ModelExporter].
 * This function is recursively called on the node's children.
 */
fun INode.asExported(): NodeData {
    val idKey = NodeData.idPropertyKey
    return NodeData(
        id = getPropertyValue(idKey) ?: reference.serialize(),
        concept = getConceptReference()?.getUID(),
        role = roleInParent,
        properties = getPropertyRoles().associateWithNotNull { getPropertyValue(it) }.filterKeys { it != idKey },
        references = getReferenceRoles().associateWithNotNull {
            getReferenceTarget(it)?.getPropertyValue(idKey) ?: getReferenceTargetRef(it)?.serialize()
        },
        children = allChildren.map { it.asExported() },
    )
}

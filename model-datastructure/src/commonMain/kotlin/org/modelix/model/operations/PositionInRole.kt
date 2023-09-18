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
package org.modelix.model.operations

data class PositionInRole(val roleInNode: RoleInNode, val index: Int) {
    constructor(nodeId: Long, role: String?, index: Int) : this(RoleInNode(nodeId, role), index)
    val nodeId: Long
        get() = roleInNode.nodeId
    val role: String?
        get() = roleInNode.role
    override fun toString() = "$roleInNode[$index]"
    fun withIndex(newIndex: Int) = PositionInRole(roleInNode, newIndex)
}

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

package org.modelix.model.api.async

import org.modelix.model.api.IChildLinkReference
import org.modelix.model.api.IPropertyReference
import org.modelix.model.api.IReferenceLinkReference
import org.modelix.model.api.IRoleReference

sealed class TreeChangeEvent
sealed class NodeChangeEvent : TreeChangeEvent() {
    abstract val nodeId: Long
}
sealed class RoleChangeEvent : TreeChangeEvent() {
    abstract val nodeId: Long
    abstract val role: IRoleReference
}
data class ContainmentChangedEvent(override val nodeId: Long) : NodeChangeEvent()
data class ConceptChangedEvent(override val nodeId: Long) : NodeChangeEvent()

data class ChildrenChangedEvent(override val nodeId: Long, override val role: IChildLinkReference) : RoleChangeEvent()
data class ReferenceChangedEvent(override val nodeId: Long, override val role: IReferenceLinkReference) : RoleChangeEvent()
data class PropertyChangedEvent(override val nodeId: Long, override val role: IPropertyReference) : RoleChangeEvent()
data class NodeRemovedEvent(override val nodeId: Long) : NodeChangeEvent()
data class NodeAddedEvent(override val nodeId: Long) : NodeChangeEvent()

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

import org.modelix.model.api.ITreeChangeVisitorEx

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

class TreeChangeCollector : ITreeChangeVisitorEx {
    val events: MutableList<ChangeEvent> = ArrayList()

    override fun containmentChanged(nodeId: Long) {
        events += ContainmentChangedEvent(nodeId)
    }

    override fun childrenChanged(nodeId: Long, role: String?) {
        events += ChildrenChangedEvent(nodeId, role)
    }

    override fun referenceChanged(nodeId: Long, role: String) {
        events += ReferenceChangedEvent(nodeId, role)
    }

    override fun propertyChanged(nodeId: Long, role: String) {
        events += PropertyChangedEvent(nodeId, role)
    }

    override fun nodeRemoved(nodeId: Long) {
        events += NodeRemovedEvent(nodeId)
    }

    override fun nodeAdded(nodeId: Long) {
        events += NodeAddedEvent(nodeId)
    }

    abstract class ChangeEvent
    data class ContainmentChangedEvent(val nodeId: Long) : ChangeEvent()
    data class ChildrenChangedEvent(val nodeId: Long, val role: String?) : ChangeEvent()
    data class ReferenceChangedEvent(val nodeId: Long, val role: String?) : ChangeEvent()
    data class PropertyChangedEvent(val nodeId: Long, val role: String?) : ChangeEvent()
    data class NodeRemovedEvent(val nodeId: Long) : ChangeEvent()
    data class NodeAddedEvent(val nodeId: Long) : ChangeEvent()
}

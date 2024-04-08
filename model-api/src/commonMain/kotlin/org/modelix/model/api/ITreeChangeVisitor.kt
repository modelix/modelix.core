/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.modelix.model.api

/**
 * Interface to handle changes within an [ITree].
 */
interface ITreeChangeVisitor {
    /**
     * Called when the containment of a node has changed.
     *
     * @param nodeId id of the affected node
     */
    fun containmentChanged(nodeId: Long)

    /**
     * Called when the concept of a node has changed.
     *
     * @param nodeId id of the affected node
     */
    fun conceptChanged(nodeId: Long) {} // Noop default implementation to avoid breaking change

    /**
     * Called when the children of a node have changed.
     *
     * @param nodeId id of the affected parent
     * @param role the affected child link role
     */
    fun childrenChanged(nodeId: Long, role: String?)

    /**
     * Called when a reference of a node has changed.
     *
     * @param nodeId id of the affected node
     * @param role the affected reference role
     */
    fun referenceChanged(nodeId: Long, role: String)

    /**
     * Called when a property of a node has changed.
     *
     * @param nodeId id of the affected node
     * @param role the affected property role
     */
    fun propertyChanged(nodeId: Long, role: String)
}

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
 * Listener, that listens for changes on a branch.
 */
interface IBranchListener {
    /**
     * Informs the branch listener about tree changes.
     *
     * @param oldTree the original tree state
     * @param newTree the new tree state
     */
    fun treeChanged(oldTree: ITree?, newTree: ITree)
}

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

package org.modelix.mps.sync.bindings

import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.IBinding

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class BindingSortComparator : Comparator<IBinding> {
    /**
     * ModelBindings should come first, then ModuleBindings. If both bindings have the same type, then they are sorted lexicographically.
     */
    override fun compare(left: IBinding, right: IBinding): Int {
        val leftName = left.name()
        val rightName = right.name()

        if (left is ModelBinding) {
            if (right is ModelBinding) {
                return leftName.compareTo(rightName)
            } else if (right is ModuleBinding) {
                return -1
            }
        } else if (left is ModuleBinding) {
            if (right is ModelBinding) {
                return 1
            } else if (right is ModuleBinding) {
                return leftName.compareTo(rightName)
            }
        }
        return 0
    }
}

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

class CapturedInsertPosition(val siblingsBefore: LongArray, val siblingsAfter: LongArray) {
    constructor(index: Int, children: LongArray) : this(
        children.take(index).toLongArray(),
        children.drop(index).toLongArray(),
    )

    fun findIndex(children: LongArray): Int {
        if (children.contentEquals(siblingsBefore + siblingsAfter)) return siblingsBefore.size

        var leftIndex = 0
        var rightIndex = children.size

        for (sibling in siblingsBefore.reversed()) {
            val index = children.indexOf(sibling)
            if (index != -1) {
                leftIndex = index + 1
                break
            }
        }

        for (sibling in siblingsAfter) {
            val index = children.indexOf(sibling)
            if (index != -1) {
                rightIndex = index
                break
            }
        }

        return if (leftIndex < rightIndex) rightIndex else leftIndex
    }
}

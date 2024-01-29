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

package org.modelix.mps.sync.gui

import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.IBinding
import org.modelix.mps.sync.bindings.BindingSortComparator
import org.modelix.mps.sync.bindings.BindingsRegistry

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class BindingsComboBoxRefresher(private val gui: ModelSyncGuiFactory.ModelSyncGui) : Runnable {

    private val bindingsComparator = BindingSortComparator()
    private var existingBindings = listOf<IBinding>()

    override fun run() {
        // TODO test how much this class slows down the execution
        // TODO test if the comparison returns false only if there is a difference in the list/set of bindings
        val latestBindings = BindingsRegistry.getAllBindings()
        if (existingBindings != latestBindings) {
            existingBindings = latestBindings
            val sorted = existingBindings.sortedWith(bindingsComparator)
            gui.populateBindingCB(sorted)
        }
    }
}

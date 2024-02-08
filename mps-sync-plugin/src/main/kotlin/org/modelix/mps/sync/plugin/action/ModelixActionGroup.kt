/*
 * Copyright (c) 2023-2024.
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

package org.modelix.mps.sync.plugin.action

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.modelix.kotlin.utils.UnstableModelixFeature
import org.modelix.mps.sync.plugin.icons.CloudIcons

@UnstableModelixFeature(reason = "The new modelix MPS plugin is under construction", intendedFinalization = "2024.1")
class ModelixActionGroup : ActionGroup("Modelix Actions", "", CloudIcons.PLUGIN_ICON) {

    init {
        isPopup = true
    }

    override fun getChildren(event: AnActionEvent?): Array<AnAction> {
        val model = event?.getData(ModelSyncAction.CONTEXT_MODEL)
        if (model != null) {
            return arrayOf(ModelSyncAction.create(), UnbindModelAction.create())
        }

        val module = event?.getData(ModuleSyncAction.CONTEXT_MODULE)
        if (module != null) {
            return arrayOf(ModuleSyncAction.create(), UnbindModuleAction.create())
        }

        return emptyArray()
    }
}

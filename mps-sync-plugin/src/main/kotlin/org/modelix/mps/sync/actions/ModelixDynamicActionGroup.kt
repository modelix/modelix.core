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

package org.modelix.mps.sync.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.modelix.mps.sync.icons.CloudIcons

class ModelixDynamicActionGroup : ActionGroup() {
    /**
     * Returns an array of menu actions for the group.
     *
     * @param e Event received when the associated group-id menu is chosen.
     * @return AnAction[] An instance of [AnAction], in this case containing a single instance of the
     * [PopupDialogAction] class.
     */
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf<AnAction>(
            PopupDialogAction("Action Added at Runtime", "Dynamic Action Demo", null),
            AddModelServerAction("Add Modelserver...", "Add a model-server URL", CloudIcons.ROOT_ICON),
        )
    }
}

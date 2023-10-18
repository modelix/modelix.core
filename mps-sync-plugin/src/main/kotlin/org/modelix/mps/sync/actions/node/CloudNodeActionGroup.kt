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

package org.modelix.mps.sync.actions.node

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class CloudNodeActionGroup : ActionGroup() {

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.AddProjectNodeAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.AddModuleNodeAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.AddModelNodeAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.AddProjectBindingAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.StoreAllModulesAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.DeleteProjectAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.DeleteModuleAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.AddTransientModuleBindingAction"),
            ActionManager.getInstance()
                .getAction("org.modelix.mps.sync.actions.node.RemoveTransientModuleBindingAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.CheckoutModuleAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.CheckoutAndSyncModuleAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.ShowPropertiesAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.ShowReferencesAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.NavigateToMpsNodeAction"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.AddChildActionGroup"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.SetPropertyActionGroup"),
        )
    }
}

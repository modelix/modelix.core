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
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.AddProjectNode"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.AddModuleNode"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.AddModelNode"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.AddProjectBinding"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.StoreAllModules"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.DeleteProject"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.DeleteModule"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.AddTransientModuleBinding"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.RemoveTransientModuleBinding"),
            ActionManager.getInstance().getAction("org.modelix.mps.sync.actions.node.CheckoutModule"),
        )
    }
}

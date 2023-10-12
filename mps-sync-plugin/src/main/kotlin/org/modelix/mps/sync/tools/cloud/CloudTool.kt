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

package org.modelix.mps.sync.tools.cloud

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import jetbrains.mps.ide.tools.BaseTool
import org.modelix.mps.sync.tools.ModelSyncGuiFactory
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.KeyStroke

class CloudToolFactory : ToolWindowFactory, Disposable {

    private var log: Logger = logger<ModelSyncGuiFactory>()
    private lateinit var content: Content

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        log.info("-------------------------------------------- create CloudTool")
        content = ContentFactory.SERVICE.getInstance().createContent(CloudView(), "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun dispose() {
        log.info("-------------------------------------------- disposing CloudTool")
        content.dispose()
    }
}

class CloudTool(
    project: Project?,
    id: String?,
    shortcutsByKeymap: MutableMap<String, KeyStroke>?,
    icon: Icon?,
    anchor: ToolWindowAnchor?,
    sideTool: Boolean,
    canCloseContent: Boolean,
) : BaseTool(project, id, shortcutsByKeymap, icon, anchor, sideTool, canCloseContent) {

    private var component: CloudView? = null
    override fun init(project: Project) {
        super.init(project)
        makeAvailableLater()
    }

    override fun getComponent(): JComponent {
        if (component == null) {
            component = CloudView()
        }
        return component!!
    }
}

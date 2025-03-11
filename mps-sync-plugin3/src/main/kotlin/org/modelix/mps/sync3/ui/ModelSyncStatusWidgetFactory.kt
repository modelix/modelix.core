package org.modelix.mps.sync3.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

class ModelSyncStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = ModelSyncStatusWidget.ID

    override fun getDisplayName(): String = "Model Synchronization Status"

    override fun createWidget(project: Project): StatusBarWidget {
        return ModelSyncStatusWidget(project)
    }

    override fun isAvailable(project: Project): Boolean = true

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

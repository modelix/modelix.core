package org.modelix.mps.sync3.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.jetbrains.annotations.NonNls

class ModelSyncStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): @NonNls String = ModelSyncStatusWidget.ID

    override fun getDisplayName(): @NlsContexts.ConfigurableName String = "Model Synchronization Status"

    override fun createWidget(project: Project): StatusBarWidget {
        return ModelSyncStatusWidget(project)
    }
}

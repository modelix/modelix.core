package org.modelix.mps.sync3.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.modelix.mps.sync3.IModelSyncService

class LogoutAction() : AnAction() {

    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        presentation.setText("Logout")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        IModelSyncService.getInstance(project).getServerConnections()
            .forEach { it.deactivate() }
    }

}

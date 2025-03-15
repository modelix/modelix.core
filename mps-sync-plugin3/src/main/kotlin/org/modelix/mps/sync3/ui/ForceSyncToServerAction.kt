package org.modelix.mps.sync3.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.modelix.mps.sync3.IModelSyncService

abstract class ForceSyncAction(private val push: Boolean) : AnAction() {

    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        presentation.setText("Force Synchronization (${if (push) "Push" else "Pull"})")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        for (binding in IModelSyncService.getInstance(project).getBindings()) {
            if (!binding.isEnabled()) continue
            binding.forceSync(push)
        }
    }
}

class ForceSyncToServerAction : ForceSyncAction(push = true)

class ForceSyncToMPSAction : ForceSyncAction(push = false)

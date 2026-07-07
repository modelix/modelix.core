package org.modelix.mps.sync3.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.modelix.model.client2.ModelClientV2
import org.modelix.mps.sync3.IModelSyncService

class OpenFrontendAction() : AnAction("Open in Browser") {

    fun getFrontendUrls(project: Project): List<String> {
        val service = IModelSyncService.getInstance(project)

        // Order the bindings so the primary (writable) repository comes first, ahead of any
        // read-only dependencies (e.g. a writable TARA followed by the read-only catalogs it
        // depends on). "Open in Browser" then opens the first entry. This mirrors
        // ModelSyncStatusWidget, which likewise orders by read-only state to single out the
        // modifiable binding.
        return service.getBindings()
            .filter { it.isEnabled() }
            .sortedBy { it.isReadonly() }
            .map {
                ModelClientV2.getFrontendUrl(it.getConnection().getUrl(), it.getBranchRef())
            }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.let { getFrontendUrls(it).isNotEmpty() } ?: false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val url = getFrontendUrls(e.project ?: return).firstOrNull() ?: return
        BrowserUtil.open(url)
    }
}

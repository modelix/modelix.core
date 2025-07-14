package org.modelix.mps.sync3.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.modelix.model.client2.ModelClientV2
import org.modelix.mps.sync3.IModelSyncService

class OpenFrontendAction() : AnAction("Open Frontend in Browser") {

    fun getFrontendUrls(project: Project): List<String> {
        val service = IModelSyncService.getInstance(project)

        return service.getBindings().filter { it.isEnabled() }.map {
            ModelClientV2.builder().url(it.getConnection().getUrl()).build()
                .getFrontendUrl(it.getBranchRef()).toString()
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.let { getFrontendUrls(it).isNotEmpty() } ?: false
    }

    override fun actionPerformed(e: AnActionEvent) {
        for (url in getFrontendUrls(e.project ?: return)) {
            BrowserUtil.open(url)
        }
    }
}

package org.modelix.mps.sync3

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class ModelSyncStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        project.service<ModelSyncService>() // just ensure it's initialized
    }
}

package org.modelix.mps.sync

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

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import jetbrains.mps.ide.project.ProjectHelper
import jetbrains.mps.smodel.MPSModuleRepository
import org.modelix.model.api.INode
import org.modelix.model.api.runSynchronized
import org.modelix.model.lazy.BranchReference
import org.modelix.model.lazy.RepositoryId
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import java.net.URL

@Service(Service.Level.APP)
class ModelSyncService : Disposable {

    private var log: Logger = Logger.getInstance(this.javaClass)

    init {
        println("============================================ ModelSyncService init")

        val syncService = SyncServiceImpl().bindRepository(
            URL("http://127.0.0.1"),
            BranchReference(RepositoryId("0"), "name"),
            "JWT",
            ProjectHelper.fromIdeaProject(com.intellij.openapi.project.ProjectManager.getInstance().openProjects.first())!!,
            this::afterActivate,
        )

        log.info("modelix sync plugin initialized $syncService")
    }
    fun afterActivate() {
        println("afterActivate")
    }

    private var server: String? = null

    fun ensureStarted() {
        println("============================================  ensureStarted")

        runSynchronized(this) {
            log.info("starting modelix synchronization plugin")
            if (server != null) return

            val rootNodeProvider: () -> INode? = { MPSModuleRepository.getInstance()?.let { MPSRepositoryAsNode(it) } }
            log.info("rootNodeProvider: $rootNodeProvider")
        }
    }

    fun ensureStopped() {
        println("============================================  ensureStopped")
        runSynchronized(this) {
            if (server == null) return
            println("stopping modelix server")
            server = null
        }
    }

    override fun dispose() {
        println("============================================  dispose")

        ensureStopped()
    }
}

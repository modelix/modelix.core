/*
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
package org.modelix.model.server.mps

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import org.modelix.model.server.light.LightModelServer
import jetbrains.mps.smodel.MPSModuleRepository
import org.modelix.model.mpsadapters.MPSRepositoryAsNode
import org.modelix.model.api.INode
import org.modelix.model.api.runSynchronized

class MPSModelServer : DynamicPluginListener, AppLifecycleListener {
    private var server: LightModelServer? = null

    fun ensureStarted() {
        runSynchronized(this) {
            val rootNodeProvider: () -> INode? = { MPSModuleRepository.getInstance()?.let { MPSRepositoryAsNode(it) } }
            server = LightModelServer.builder().port(48305).rootNode(rootNodeProvider).build()
            server!!.start()
        }
    }

    fun ensureStopped() {
        runSynchronized(this) {
            server?.stop()
            server = null
        }
    }

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        ensureStarted()
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        ensureStopped()
    }

    override fun appStarted() {
        ensureStarted()
    }

    override fun appClosing() {
        ensureStopped()
    }
}
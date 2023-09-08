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

package org.modelix.mps.sync.listeners

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.components.service
import org.modelix.mps.sync.ModelSyncService

class MPSSynchronizationDynamicPluginListener : DynamicPluginListener {
    override fun beforePluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        println("============================================ before load")
        super.beforePluginLoaded(pluginDescriptor)
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        println("============================================ before unload")

        super.beforePluginUnload(pluginDescriptor, isUpdate)
    }

    override fun checkUnloadPlugin(pluginDescriptor: IdeaPluginDescriptor) {
        println("============================================ check unload")
        super.checkUnloadPlugin(pluginDescriptor)
    }

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        println("============================================ loaded")
        super.pluginLoaded(pluginDescriptor)
        service<ModelSyncService>().ensureStarted()
    }

    override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        println("============================================ unloaded")
        super.pluginUnloaded(pluginDescriptor, isUpdate)
    }
}

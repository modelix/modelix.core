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

package org.modelix.mps.sync.tools.history

import jetbrains.mps.ide.ui.tree.TextTreeNode
import org.modelix.mps.sync.connection.IRepositoriesChangedListener
import org.modelix.mps.sync.connection.ModelServerConnections
import org.modelix.mps.sync.icons.CloudIcons

// status: ready to test
class CloudRootTreeNode : TextTreeNode(CloudIcons.ROOT_ICON, "Cloud") {

    private var myInitialized = false
    private val repositoriesListener = object : IRepositoriesChangedListener {
        override fun repositoriesChanged() {
            update()
            init()
        }
    }

    init {
        setAllowsChildren(true)
        init()
    }

    override fun isInitialized() = myInitialized

    override fun doInit() {
        myInitialized = true
        populate()
    }

    override fun doUpdate() {
        removeAllChildren()
        myInitialized = false
    }

    private fun populate() {
        ModelServerConnections.instance.modelServers.forEach { add(ModelServerTreeNode(it)) }
    }

    override fun onAdd() {
        super.onAdd()
        ModelServerConnections.instance.addListener(repositoriesListener)
    }

    override fun onRemove() {
        super.onRemove()
        ModelServerConnections.instance.removeListener(repositoriesListener)
    }
}

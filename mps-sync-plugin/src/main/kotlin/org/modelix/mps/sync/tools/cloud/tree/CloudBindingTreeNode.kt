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

package org.modelix.mps.sync.tools.cloud.tree

import jetbrains.mps.ide.ui.tree.TextTreeNode
import org.modelix.mps.sync.binding.Binding
import org.modelix.mps.sync.binding.IBinding
import org.modelix.mps.sync.replication.CloudRepository
import javax.swing.SwingUtilities

// status: ready to test
class CloudBindingTreeNode(val binding: Binding, val cloudRepository: CloudRepository) :
    TextTreeNode(binding.toString()) {

    val modelServer = cloudRepository.modelServer
    val repositoryInModelServer = cloudRepository

    private val bindingListener = object : Binding.IListener {
        override fun bindingAdded(binding: IBinding) {
            updateBindingsLater()
        }

        override fun bindingRemoved(binding: IBinding) {
            updateBindingsLater()
        }

        override fun ownerChanged(newOwner: IBinding) {
            updateBindingsLater()
        }

        override fun bindingActivated() {
            updateText()
            updateBindingsLater()
        }

        override fun bindingDeactivated() {
            updateText()
            updateBindingsLater()
        }
    }

    init {
        updateBindings()
    }

    override fun onAdd() {
        super.onAdd()
        binding.addListener(bindingListener)
    }

    override fun onRemove() {
        super.onRemove()
        binding.removeListener(bindingListener)
    }

    fun updateBindingsLater() = SwingUtilities.invokeLater { updateBindings() }

    fun updateText() {
        text = binding.toString() + if (binding.isActive) "" else " [disabled]"
    }

    fun updateBindings() {
        val existing = mutableMapOf<Binding, CloudBindingTreeNode>()
        TreeModelUtil.getChildren(this).filterIsInstance<CloudBindingTreeNode>().forEach { existing[it.binding] = it }
        TreeModelUtil.setChildren(
            this,
            binding.ownedBindings.map {
                if (existing.containsKey(it)) {
                    existing[it]!!
                } else {
                    CloudBindingTreeNode(it, cloudRepository)
                }
            },
        )
    }
}

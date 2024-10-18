/*
 * Copyright (c) 2024.
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

package org.modelix.model.api

data class TreeAsBranch(override val tree: ITree) : IBranch, IReadTransaction {
    override fun getUserObject(key: Any) = null

    override fun putUserObject(key: Any, value: Any?) {
        throw UnsupportedOperationException("read-only")
    }

    override fun addListener(l: IBranchListener) {
        // branch will never change
    }

    override fun getId(): String {
        return tree.getId() ?: throw UnsupportedOperationException()
    }

    override fun runRead(runnable: () -> Unit) {
        computeRead(runnable)
    }

    override fun <T> computeRead(computable: () -> T): T {
        return RoleAccessContext.runWith(tree.usesRoleIds()) { computable() }
    }

    override fun runWrite(runnable: () -> Unit) {
        throw UnsupportedOperationException("read-only")
    }

    override fun <T> computeWrite(computable: () -> T): T {
        throw UnsupportedOperationException("read-only")
    }

    override fun canRead(): Boolean {
        return true
    }

    override fun canWrite(): Boolean {
        return false
    }

    override val transaction: ITransaction
        get() = this
    override val readTransaction: IReadTransaction
        get() = this
    override val writeTransaction: IWriteTransaction
        get() = throw UnsupportedOperationException("read-only")

    override fun removeListener(l: IBranchListener) {
        // branch will never change
    }

    override val branch: IBranch
        get() = this

    override fun containsNode(nodeId: Long) = tree.containsNode(nodeId)

    override fun getConcept(nodeId: Long) = tree.getConcept(nodeId)

    override fun getConceptReference(nodeId: Long): IConceptReference? = tree.getConceptReference(nodeId)

    override fun getParent(nodeId: Long) = tree.getParent(nodeId)

    override fun getRole(nodeId: Long) = tree.getRole(nodeId)

    override fun getProperty(nodeId: Long, role: String) = tree.getProperty(nodeId, role)

    override fun getReferenceTarget(sourceId: Long, role: String) = tree.getReferenceTarget(sourceId, role)

    override fun getChildren(parentId: Long, role: String?) = tree.getChildren(parentId, role)

    override fun getAllChildren(parentId: Long) = tree.getAllChildren(parentId)

    override fun getReferenceRoles(sourceId: Long) = tree.getReferenceRoles(sourceId)

    override fun getPropertyRoles(sourceId: Long) = tree.getPropertyRoles(sourceId)
}

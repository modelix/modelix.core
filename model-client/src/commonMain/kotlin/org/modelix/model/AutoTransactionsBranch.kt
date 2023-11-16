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
package org.modelix.model

import org.modelix.model.api.IBranch
import org.modelix.model.api.IBranchWrapper
import org.modelix.model.api.IConcept
import org.modelix.model.api.IConceptReference
import org.modelix.model.api.INodeReference
import org.modelix.model.api.IReadTransaction
import org.modelix.model.api.ITransaction
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction

class AutoTransactionsBranch(private val branch: IBranch) : IBranch by branch, IBranchWrapper {
    override val transaction: ITransaction
        get() = if (isInTransaction()) branch.transaction else AutoReadTransaction(branch)
    override val readTransaction: IReadTransaction
        get() = if (isInTransaction()) branch.readTransaction else AutoReadTransaction(branch)
    override val writeTransaction: IWriteTransaction
        get() = if (isInTransaction()) branch.writeTransaction else AutoWriteTransaction(branch)

    private fun isInTransaction() = branch.canRead()
    override fun unwrapBranch(): IBranch = branch
}

open class AutoTransaction(override val branch: IBranch) : ITransaction {
    override val tree: ITree
        get() = branch.computeReadT { it.tree }

    override fun containsNode(nodeId: Long): Boolean = branch.computeReadT { it.containsNode(nodeId) }
    override fun getConcept(nodeId: Long): IConcept? = branch.computeReadT { it.getConcept(nodeId) }
    override fun getConceptReference(nodeId: Long): IConceptReference? = branch.computeReadT { it.getConceptReference(nodeId) }
    override fun getParent(nodeId: Long): Long = branch.computeReadT { it.getParent(nodeId) }
    override fun getRole(nodeId: Long): String? = branch.computeReadT { it.getRole(nodeId) }
    override fun getProperty(nodeId: Long, role: String): String? = branch.computeReadT { it.getProperty(nodeId, role) }
    override fun getReferenceTarget(sourceId: Long, role: String): INodeReference? = branch.computeReadT { it.getReferenceTarget(sourceId, role) }
    override fun getChildren(parentId: Long, role: String?): Iterable<Long> = branch.computeReadT { it.getChildren(parentId, role) }
    override fun getAllChildren(parentId: Long): Iterable<Long> = branch.computeReadT { it.getAllChildren(parentId) }
    override fun getReferenceRoles(sourceId: Long): Iterable<String> = branch.computeReadT { it.getReferenceRoles(sourceId) }
    override fun getPropertyRoles(sourceId: Long): Iterable<String> = branch.computeReadT { it.getPropertyRoles(sourceId) }
    override fun getUserObject(key: Any): Any? = null
    override fun putUserObject(key: Any, value: Any?) {}
}

class AutoReadTransaction(branch: IBranch) : AutoTransaction(branch), IReadTransaction

class AutoWriteTransaction(branch: IBranch) : AutoTransaction(branch), IWriteTransaction {
    override fun setProperty(nodeId: Long, role: String, value: String?) =
        branch.computeWriteT { it.setProperty(nodeId, role, value) }
    override fun setReferenceTarget(sourceId: Long, role: String, target: INodeReference?) =
        branch.computeWriteT { it.setReferenceTarget(sourceId, role, target) }
    override fun moveChild(newParentId: Long, newRole: String?, newIndex: Int, childId: Long) =
        branch.computeWriteT { it.moveChild(newParentId, newRole, newIndex, childId) }
    override fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConcept?): Long =
        branch.computeWriteT { it.addNewChild(parentId, role, index, concept) }
    override fun addNewChild(parentId: Long, role: String?, index: Int, concept: IConceptReference?): Long =
        branch.computeWriteT { it.addNewChild(parentId, role, index, concept) }
    override fun addNewChild(parentId: Long, role: String?, index: Int, childId: Long, concept: IConcept?) =
        branch.computeWriteT { it.addNewChild(parentId, role, index, childId, concept) }
    override fun addNewChild(
        parentId: Long,
        role: String?,
        index: Int,
        childId: Long,
        concept: IConceptReference?,
    ) = branch.computeWriteT { it.addNewChild(parentId, role, index, childId, concept) }
    override fun deleteNode(nodeId: Long) = branch.computeWriteT { it.deleteNode(nodeId) }

    override var tree: ITree
        get() = branch.computeReadT { it.tree }
        set(value) {
            branch.computeWriteT { it.tree = value }
        }
}

fun IBranch.withAutoTransactions() = AutoTransactionsBranch(this)

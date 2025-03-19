package org.modelix.model.operations

import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction
import org.modelix.model.objects.IObjectData
import org.modelix.model.objects.ObjectReference

sealed interface IOperation {
    fun apply(transaction: IWriteTransaction): IAppliedOperation
    fun captureIntend(tree: ITree): IOperationIntend
    fun getObjectReferences(): List<ObjectReference<IObjectData>>
}

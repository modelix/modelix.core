package org.modelix.model.operations

import org.modelix.datastructures.objects.IObjectData
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.api.ITree
import org.modelix.model.api.IWriteTransaction

sealed interface IOperation {
    fun apply(transaction: IWriteTransaction): IAppliedOperation
    fun captureIntend(tree: ITree): IOperationIntend
    fun getObjectReferences(): List<ObjectReference<IObjectData>>
}

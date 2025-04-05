package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree
import org.modelix.datastructures.objects.ObjectReference
import org.modelix.model.mutable.IMutableModelTree

sealed interface IOperation {
    fun apply(tree: IMutableModelTree): IAppliedOperation
    fun captureIntend(tree: IModelTree): IOperationIntend
    fun getObjectReferences(): List<ObjectReference<*>>
}

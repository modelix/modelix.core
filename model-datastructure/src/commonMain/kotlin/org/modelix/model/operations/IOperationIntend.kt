package org.modelix.model.operations

import org.modelix.datastructures.model.IModelTree

interface IOperationIntend {
    fun getOriginalOp(): IOperation
    fun restoreIntend(tree: IModelTree): List<IOperation>
}

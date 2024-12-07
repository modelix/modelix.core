package org.modelix.model.operations

interface IAppliedOperation {
    fun getOriginalOp(): IOperation
    fun invert(): List<IOperation>
}

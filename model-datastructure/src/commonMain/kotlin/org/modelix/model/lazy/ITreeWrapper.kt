package org.modelix.model.lazy

import org.modelix.model.api.ITree

interface ITreeWrapper : ITree {
    override fun asObject() = getWrappedTree().asObject()
    fun getWrappedTree(): ITree
}

fun ITree.unwrap(): ITree = if (this is ITreeWrapper) this.getWrappedTree().unwrap() else this

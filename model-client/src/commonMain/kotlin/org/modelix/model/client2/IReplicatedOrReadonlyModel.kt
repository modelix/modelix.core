package org.modelix.model.client2

import org.modelix.model.IVersion
import org.modelix.model.mutable.IMutableModelTree
import org.modelix.model.mutable.asMutableReadonly

interface IReplicatedOrReadonlyModel {

    fun getMutableModelTree(): IMutableModelTree
    fun getCurrentVersion(): IVersion
    fun dispose()

    class Replicated(private val model: ReplicatedModel) : IReplicatedOrReadonlyModel {
        override fun getMutableModelTree(): IMutableModelTree = model.getVersionedModelTree()
        override fun getCurrentVersion(): IVersion = model.getCurrentVersion()
        override fun dispose() {
            model.dispose()
        }
    }

    class Readonly(private val version: IVersion) : IReplicatedOrReadonlyModel {
        override fun getMutableModelTree(): IMutableModelTree = version.getModelTree().asMutableReadonly()
        override fun getCurrentVersion(): IVersion = version
        override fun dispose() {}
    }
}

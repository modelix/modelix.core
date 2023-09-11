package org.modelix.mps.sync.binding

interface IBinding {

    fun activate(callback: Runnable? = null)
    fun deactivate(callback: Runnable? = null)
}

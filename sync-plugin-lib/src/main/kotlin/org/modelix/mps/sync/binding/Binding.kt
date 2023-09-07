package org.modelix.mps.sync.binding

interface Binding {

    fun activate(callback: Runnable? = null)
    fun deactivate(callback: Runnable? = null)
}

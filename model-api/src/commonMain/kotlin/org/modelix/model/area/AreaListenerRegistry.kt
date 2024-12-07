package org.modelix.model.area

object AreaListenerRegistry {
    private var listeners: List<Entry> = ArrayList()

    fun getListeners(area: IArea) = listeners.filter { it.area == area }

    fun unregisterArea(area: IArea) {
        listeners = listeners.filter { it.area == area }
    }

    fun registerListener(area: IArea, listener: IAreaListener) {
        listeners = listeners + Entry(area, listener)
    }

    fun unregisterListener(area: IArea, listener: IAreaListener) {
        listeners = listeners.filter { !(it.area == area && it.listener == listener) }
    }

    fun unregisterWrappedListener(area: IArea, wrappedListener: IAreaListener) {
        listeners = listeners.filter { !(it.area == area && unwrap(it.listener) == unwrap(wrappedListener)) }
    }

    private fun unwrap(l: IAreaListener): IAreaListener = if (l is AreaListenerWrapper) unwrap(l.wrappedListener) else l

    class Entry(val area: IArea, val listener: IAreaListener)
}

package org.modelix.datastructures.objects

interface IObjectWriter {
    fun write(obj: Object<*>)
}

/**
 * The /dev/null equivalent of an IObjectWriter
 */
class NullObjectWriter : IObjectWriter {
    override fun write(obj: Object<*>) {}
}

package org.modelix.model.server.store

import javax.cache.processor.EntryProcessor
import javax.cache.processor.EntryProcessorException
import javax.cache.processor.MutableEntry

class ClientIdProcessor : EntryProcessor<ObjectInRepository?, String?, Long> {
    @Throws(EntryProcessorException::class)
    override fun process(mutableEntry: MutableEntry<ObjectInRepository?, String?>, vararg objects: Any): Long {
        val id = generateId(mutableEntry.value)
        mutableEntry.value = id.toString()
        return id
    }
}

package org.modelix.model.objects

interface IObjectWriter {
    fun write(hash: ObjectHash, obj: IObjectData)
}

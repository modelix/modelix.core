package org.modelix.model

interface IKeyValueStoreWrapper : IKeyValueStore {
    fun getWrapped(): IKeyValueStore

    companion object {
        fun IKeyValueStore.getAllStores(): List<IKeyValueStore> {
            return if (this is IKeyValueStoreWrapper) listOf(this) + this.getWrapped().getAllStores() else listOf(this)
        }
    }
}

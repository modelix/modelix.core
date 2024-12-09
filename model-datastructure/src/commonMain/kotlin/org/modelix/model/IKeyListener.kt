package org.modelix.model

interface IKeyListener : IGenericKeyListener<String>

interface IGenericKeyListener<KeyT> {
    companion object {
        const val NULL_VALUE = "Null-nULL-NulL-nULl"
    }
    fun changed(key: KeyT, value: String?)
}

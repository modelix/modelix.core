package org.modelix.model.api

@Deprecated("use org.modelix.kotlin.utils.ContextValue from org.modelix:kotlin-utils")
expect class ContextValue<E> {

    constructor()
    constructor(defaultValue: E)

    fun getValue(): E?
    fun <T> computeWith(newValue: E, r: () -> T): T
}

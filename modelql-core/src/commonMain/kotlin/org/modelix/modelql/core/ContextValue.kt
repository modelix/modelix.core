package org.modelix.modelql.core

@Deprecated("use org.modelix.kotlin.utils.ContextValue from org.modelix:kotlin-utils")
expect class ContextValue<E : Any>() {
    fun getStack(): List<E>
    fun getValue(): E
    fun tryGetValue(): E?
    fun <T> computeWith(newValue: E, r: () -> T): T
}

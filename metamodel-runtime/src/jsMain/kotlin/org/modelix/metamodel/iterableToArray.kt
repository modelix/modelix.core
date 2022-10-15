package org.modelix.metamodel

import kotlin.reflect.KClass

actual fun <T : Any> iterableToArray(
    elementsType: KClass<T>,
    elements: Iterable<T>
): Array<out T> {
    return elements.toList().toTypedArray()
}

@JsExport
fun <T> iterableToArray(elements: Iterable<T>): Array<out T> {
    return elements.toList().toTypedArray()
}
package org.modelix.metamodel

import kotlin.reflect.KClass

actual fun <T : Any> iterableToArray(elementsType: KClass<T>, elements: Iterable<T>): Array<out T> {
    val list = elements.toList()
    val array: Array<T> = java.lang.reflect.Array.newInstance(elementsType.java, list.size) as Array<T>
    list.forEachIndexed { index, t -> array[index] = t }
    return array
}
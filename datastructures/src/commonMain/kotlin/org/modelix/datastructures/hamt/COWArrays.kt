package org.modelix.datastructures.hamt

internal object COWArrays {

    fun <T> arraycopy(array: Array<out T>, srcPos: Int, dest: Array<in T>, destPos: Int, length: Int) {
        array.copyInto(dest, destPos, srcPos, srcPos + length)
    }

    fun arraycopy(array: LongArray, srcPos: Int, dest: LongArray, destPos: Int, length: Int) {
        array.copyInto(dest, destPos, srcPos, srcPos + length)
    }

    inline fun <reified T> insert(array: Array<out T>, index: Int, element: T): Array<out T> {
        val newArray = arrayOfNulls<T>(array.size + 1)
        arraycopy(array, 0, newArray, 0, index)
        newArray[index] = element
        arraycopy(array, index, newArray, index + 1, array.size - index)
        return newArray as Array<T>
    }

    fun insert(array: LongArray, index: Int, element: Long): LongArray {
        val newArray = LongArray(array.size + 1)
        arraycopy(array, 0, newArray, 0, index)
        newArray[index] = element
        arraycopy(array, index, newArray, index + 1, array.size - index)
        return newArray
    }

    fun insert(array: LongArray, index: Int, elements: LongArray): LongArray {
        val newArray = LongArray(array.size + elements.size)
        arraycopy(array, 0, newArray, 0, index)
        arraycopy(elements, 0, newArray, index, elements.size)
        arraycopy(array, index, newArray, index + elements.size, array.size - index)
        return newArray
    }

    inline fun <reified T> removeAt(array: Array<out T>, index: Int): Array<out T> {
        val newArray = arrayOfNulls<T>(array.size - 1)
        arraycopy(array, 0, newArray, 0, index)
        arraycopy(array, index + 1, newArray, index, array.size - index - 1)
        return newArray as Array<T>
    }

    fun removeAt(array: LongArray, index: Int): LongArray {
        val newArray = LongArray(array.size - 1)
        arraycopy(array, 0, newArray, 0, index)
        arraycopy(array, index + 1, newArray, index, array.size - index - 1)
        return newArray
    }

    inline fun <reified T> remove(array: Array<out T>, value: T): Array<out T> {
        val index = indexOf(array, value)
        return if (index != -1) removeAt(array, index) else array
    }

    fun remove(array: LongArray, value: Long): LongArray {
        val index = indexOf(array, value)
        return if (index != -1) removeAt(array, index) else array
    }

    fun removeAll(array: LongArray, valuesToRemove_: LongArray): LongArray {
        val valuesToRemove = valuesToRemove_.toHashSet()
        val filtered = LongArray(array.size)
        var cursor = 0
        for (l in array) {
            if (!valuesToRemove.contains(l)) {
                filtered[cursor++] = l
            }
        }
        return filtered.copyOf(cursor)
    }

    operator fun <T> set(array: Array<out T>, index: Int, value: T): Array<out T> {
        val newArray = array.copyOf() as Array<T>
        newArray[index] = value
        return newArray
    }

    operator fun set(array: LongArray, index: Int, value: Long): LongArray {
        val newArray = array.copyOf()
        newArray[index] = value
        return newArray
    }

    fun <T> add(array: Array<T>, value: T): Array<T> {
        val newArray = array.copyOf(array.size + 1)
        newArray[newArray.size - 1] = value
        return newArray as Array<T>
    }

    fun add(array: LongArray, value: Long): LongArray {
        val newArray = array.copyOf(array.size + 1)
        newArray[newArray.size - 1] = value
        return newArray
    }

    fun add(array: LongArray, values: LongArray): LongArray {
        val newArray = array.copyOf(array.size + values.size)
        arraycopy(values, 0, newArray, array.size, values.size)
        return newArray
    }

    fun <T> copy(array: Array<T>): Array<T> {
        return array.copyOf()
    }

    fun copy(array: LongArray): LongArray {
        return array.copyOf()
    }

    fun <T> addIfAbsent(array: Array<T>, value: T): Array<T> {
        return if (indexOf(array, value) == -1) add(array, value) else array
    }

    fun <T> indexOf(array: Array<out T>, value: T): Int {
        for (i in array.indices) {
            if (array[i] == value) {
                return i
            }
        }
        return -1
    }

    fun indexOf(array: LongArray, value: Long): Int {
        for (i in array.indices) {
            if (array[i] == value) {
                return i
            }
        }
        return -1
    }

    fun <T> concat(array1: Array<T>, array2: Array<T>): Array<T> {
        val newArray = array1.copyOf(array1.size + array2.size)
        arraycopy(array2, 0, newArray, array1.size, array2.size)
        return newArray as Array<T>
    }
}

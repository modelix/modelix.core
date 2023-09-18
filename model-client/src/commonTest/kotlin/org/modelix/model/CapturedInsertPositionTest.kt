/*
 * Copyright (c) 2023.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modelix.model

import org.modelix.model.operations.CapturedInsertPosition
import kotlin.test.Test
import kotlin.test.assertEquals

class CapturedInsertPositionTest {

    @Test
    fun noChange() {
        val list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(0L, 1L, -2L, 2L, 3L, 4L, 5L), list)
    }

    @Test
    fun insertBefore_1() {
        val list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list.add(1, -1L)
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(0L, -1L, 1L, -2L, 2L, 3L, 4L, 5L), list)
    }

    @Test
    fun insertAfter_3() {
        val list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list.add(3, -1L)
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(0L, 1L, -2L, 2L, -1L, 3L, 4L, 5L), list)
    }

    @Test
    fun insertAtSamePosition() {
        val list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list.add(2, -1L)
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(0L, 1L, -1L, -2L, 2L, 3L, 4L, 5L), list)
        // also valid: 0L, 1L, -2L, -1L, 2L, 3L, 4L, 5L
    }

    @Test
    fun moveAfter_1_3() {
        val list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list.add(3 - 1, list.removeAt(1))
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(0L, 2L, 1L, -2L, 3L, 4L, 5L), list)
    }

    @Test
    fun moveAfter_0_4() {
        val list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list.add(4 - 1, list.removeAt(0))
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(1L, -2L, 2L, 3L, 0L, 4L, 5L), list)
    }

    @Test
    fun moveBefore_2_1() {
        val list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list.add(1, list.removeAt(2))
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(0L, 2L, 1L, -2L, 3L, 4L, 5L), list)
    }

    @Test
    fun moveBefore_3_2() {
        val list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list.add(2, list.removeAt(3))
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(0L, 1L, 3L, -2L, 2L, 4L, 5L), list)
        // also valid: 0L, 1L, -2L, 3L, 2L, 4L, 5L
    }

    @Test
    fun moveBefore_3_1() {
        val list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list.add(1, list.removeAt(3))
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(0L, 3L, 1L, -2L, 2L, 4L, 5L), list)
    }

    @Test
    fun moveBefore_4_0() {
        val list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list.add(0, list.removeAt(4))
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(4L, 0L, 1L, -2L, 2L, 3L, 5L), list)
    }

    @Test
    fun moveMultiple_2_5() {
        var list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list = mutableListOf(2L, 3L, 4L, 5L, 0L, 1L)
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(2L, 3L, 4L, 5L, 0L, 1L, -2L), list)
        // also valid: -2L, 2L, 3L, 4L, 5L, 0L, 1L
    }

    @Test
    fun moveMultiple_3_5() {
        var list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list = mutableListOf(3L, 4L, 5L, 0L, 1L, 2L)
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(3L, 4L, 5L, 0L, 1L, -2L, 2L), list)
    }

    @Test
    fun moveMultiple_4_5() {
        var list = (0L..5L).toMutableList()
        val captured = CapturedInsertPosition(2, list.toLongArray())
        list = mutableListOf(4L, 5L, 0L, 1L, 2L, 3L)
        list.add(captured.findIndex(list.toLongArray()), -2L)
        assertEquals(listOf(4L, 5L, 0L, 1L, -2L, 2L, 3L), list)
    }
}

package org.modelix.streams

import com.badoo.reaktive.observable.observableOf
import com.badoo.reaktive.observable.toList
import com.badoo.reaktive.single.blockingGet
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamExtensionsTests {

    @Test
    fun `distinct removes duplicates`() {
        assertEquals(
            listOf("g", "a", "d", "h", "z"),
            observableOf("g", "g", "a", "d", "h", "z", "g", "h").distinct().toList().blockingGet(),
        )
    }
}

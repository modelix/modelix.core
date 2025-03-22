package org.modelix.streams

import com.badoo.reaktive.observable.observableOf
import com.badoo.reaktive.observable.toList
import org.modelix.kotlin.utils.UnstableModelixFeature
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamExtensionsTests {

    @OptIn(UnstableModelixFeature::class)
    @Test
    fun `distinct removes duplicates`() {
        assertEquals(
            listOf("g", "a", "d", "h", "z"),
            observableOf("g", "g", "a", "d", "h", "z", "g", "h").distinct().toList().getSynchronous(),
        )
    }
}

package org.modelix.model

import org.modelix.model.persistent.emptyStringAsNull
import org.modelix.model.persistent.nullAsEmptyString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class SerializationUtilNullEmptyStringTest {

    @Test
    fun nullAsEmptyString_null() {
        assertEquals("", nullAsEmptyString(null))
    }

    @Test
    fun nullAsEmptyString_emptyString() {
        assertFails { nullAsEmptyString("") }
    }

    @Test
    fun emptyStringAsNull_emptyString() {
        assertEquals(null, emptyStringAsNull(""))
    }
}

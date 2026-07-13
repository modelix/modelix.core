package org.modelix.mps.sync3.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSyncStatusWidgetTest {
    @Test
    fun `syncing text omits null progress`() {
        assertEquals("Synchronizing", formatSyncingStatusText(null))
    }

    @Test
    fun `syncing text keeps non-null progress`() {
        assertEquals("Synchronizing: abcde", formatSyncingStatusText("abcde"))
    }
}

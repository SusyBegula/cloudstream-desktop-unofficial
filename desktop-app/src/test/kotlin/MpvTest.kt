package com.lagradost.cloudstream3.desktop.player

import kotlin.test.Test
import kotlin.test.assertNotNull

class MpvTest {
    @Test
    fun testMpvCreate() {
        val lib = MpvLibrary.INSTANCE
        val handle = lib.mpv_create()
        assertNotNull(handle, "mpv_create handle should not be null")
        lib.mpv_terminate_destroy(handle)
    }
}

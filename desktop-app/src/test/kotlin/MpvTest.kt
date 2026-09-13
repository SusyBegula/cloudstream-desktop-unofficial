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

    @Test
    fun testMpvFullscreenOption() {
        val lib = MpvLibrary.INSTANCE
        val handle = lib.mpv_create()
        assertNotNull(handle, "mpv_create handle should not be null")
        lib.mpv_set_option_string(handle, "fs", "yes")
        lib.mpv_initialize(handle)
        val fs = lib.getProperty(handle, "fullscreen")
        kotlin.test.assertEquals("yes", fs, "fullscreen property should be 'yes'")
        lib.mpv_terminate_destroy(handle)
    }
}

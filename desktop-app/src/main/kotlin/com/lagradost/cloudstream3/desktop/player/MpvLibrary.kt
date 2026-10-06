package com.lagradost.cloudstream3.desktop.player

import com.lagradost.common.logging.AppLogger
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

interface CLibrary : Library {
    fun setlocale(category: Int, locale: String?): String?

    companion object {
        val INSTANCE: CLibrary? by lazy {
            try {
                val isWin = System.getProperty("os.name").lowercase().contains("win")
                Native.load(if (isWin) "msvcrt" else "c", CLibrary::class.java) as CLibrary
            } catch (_: Throwable) {
                null
            }
        }
        const val LC_ALL = 0
        const val LC_NUMERIC = 1

        fun initLocale() {
            try {
                INSTANCE?.setlocale(LC_ALL, "C")
                INSTANCE?.setlocale(LC_NUMERIC, "C")
            } catch (e: Throwable) {
                AppLogger.w("Failed to set C locale for MPV: ${e.message}")
            }
        }
    }
}

interface X11Library : Library {
    interface XErrorHandler : com.sun.jna.Callback {
        fun callback(display: Pointer?, errorEvent: Pointer?): Int
    }

    fun XSetErrorHandler(handler: XErrorHandler?): Pointer?

    companion object {
        private var handlerInstalled = false
        private var ignoreHandlerRef: XErrorHandler? = null

        fun installDummyErrorHandler() {
            if (handlerInstalled) return
            val isLinux = System.getProperty("os.name").lowercase().let { it.contains("nix") || it.contains("nux") }
            if (!isLinux) return

            try {
                val x11 = Native.load("X11", X11Library::class.java) as X11Library
                ignoreHandlerRef = object : XErrorHandler {
                    override fun callback(display: Pointer?, errorEvent: Pointer?): Int {
                        // Suppress non-fatal X11 errors (like BadWindow when mpv window surface is detached)
                        return 0
                    }
                }
                x11.XSetErrorHandler(ignoreHandlerRef)
                handlerInstalled = true
                AppLogger.i("Installed custom X11 error handler to prevent BadWindow crashes")
            } catch (e: Throwable) {
                AppLogger.w("Failed to install X11 error handler: ${e.message}")
            }
        }
    }
}

interface User32Library : Library {
    fun FindWindowExA(hwndParent: Pointer?, hwndChildAfter: Pointer?, lpszClass: String?, lpszWindow: String?): Pointer?
    fun GetWindowLongA(hWnd: Pointer?, nIndex: Int): Int
    fun SetWindowLongA(hWnd: Pointer?, nIndex: Int, dwNewLong: Int): Int

    companion object {
        const val GWL_EXSTYLE = -20
        const val WS_EX_TRANSPARENT = 0x00000020

        val INSTANCE: User32Library? by lazy {
            val isWin = System.getProperty("os.name").lowercase().contains("win")
            if (isWin) {
                try {
                    Native.load("user32", User32Library::class.java) as User32Library
                } catch (e: Throwable) {
                    AppLogger.w("Failed to load user32 library: ${e.message}")
                    null
                }
            } else {
                null
            }
        }

        fun makeTransparent(parentHwndPtr: Pointer?): Boolean {
            val lib = INSTANCE ?: return false
            if (parentHwndPtr == null) return false
            return try {
                var child = lib.FindWindowExA(parentHwndPtr, null, null, null)
                var count = 0
                while (child != null) {
                    val current = lib.GetWindowLongA(child, GWL_EXSTYLE)
                    if ((current and WS_EX_TRANSPARENT) == 0) {
                        lib.SetWindowLongA(child, GWL_EXSTYLE, current or WS_EX_TRANSPARENT)
                        AppLogger.i("Applied WS_EX_TRANSPARENT to child HWND: $child")
                    }

                    var grandChild = lib.FindWindowExA(child, null, null, null)
                    while (grandChild != null) {
                        val gcCurrent = lib.GetWindowLongA(grandChild, GWL_EXSTYLE)
                        if ((gcCurrent and WS_EX_TRANSPARENT) == 0) {
                            lib.SetWindowLongA(grandChild, GWL_EXSTYLE, gcCurrent or WS_EX_TRANSPARENT)
                            AppLogger.i("Applied WS_EX_TRANSPARENT to grandchild HWND: $grandChild")
                        }
                        grandChild = lib.FindWindowExA(child, grandChild, null, null)
                    }

                    count++
                    child = lib.FindWindowExA(parentHwndPtr, child, null, null)
                }
                count > 0
            } catch (e: Throwable) {
                AppLogger.w("Failed to apply WS_EX_TRANSPARENT: ${e.message}")
                false
            }
        }
    }
}

interface MpvLibrary : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(handle: Pointer): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_command_string(ctx: Pointer, args: String): Int
    fun mpv_free(data: Pointer)
    fun mpv_terminate_destroy(handle: Pointer)

    fun getProperty(ctx: Pointer, name: String): String? {
        val ptr = mpv_get_property_string(ctx, name) ?: return null
        return try {
            ptr.getString(0, "UTF-8")
        } catch (_: Throwable) {
            null
        } finally {
            mpv_free(ptr)
        }
    }

    companion object {
        val INSTANCE: MpvLibrary by lazy {
            CLibrary.initLocale()
            X11Library.installDummyErrorHandler()
            val targets = listOf("mpv", "libmpv.so.2", "libmpv.so.1", "libmpv.so", "libmpv-2", "mpv-2", "mpv-1", "libmpv", "mpv-3.dll")
            var loaded: MpvLibrary? = null
            for (target in targets) {
                try {
                    loaded = Native.load(target, MpvLibrary::class.java) as MpvLibrary
                    AppLogger.i("Successfully loaded native mpv library: $target")
                    break
                } catch (e: UnsatisfiedLinkError) {
                    // Try next
                } catch (e: IllegalArgumentException) {
                    // Try next
                }
            }
            loaded ?: throw RuntimeException("Failed to load native MPV library. Please ensure libmpv (e.g. libmpv2 on Debian/Ubuntu, mpv on Arch) is installed.")
        }
    }
}

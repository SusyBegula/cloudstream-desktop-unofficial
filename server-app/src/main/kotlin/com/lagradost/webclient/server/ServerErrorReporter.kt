package com.lagradost.webclient.server

import com.lagradost.common.logging.AppLogger

/** Server-side counterpart of desktop-app's DesktopErrorReporter (no UI badge/counter needed here). */
object ServerErrorReporter {
    fun report(title: String, throwable: Throwable? = null) {
        AppLogger.i(
            buildString {
                append("[")
                append(System.currentTimeMillis())
                append("] ")
                append(title)
                if (throwable != null) {
                    append("\n")
                    append(throwable.stackTraceToString())
                }
            },
        )
    }
}

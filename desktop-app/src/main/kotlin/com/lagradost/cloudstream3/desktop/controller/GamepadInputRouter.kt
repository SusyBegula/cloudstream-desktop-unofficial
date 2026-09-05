package com.lagradost.cloudstream3.desktop.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

object GamepadInputRouter {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var routeJob: Job? = null
    private var uiHandler: UiGamepadHandler? = null
    private var isPlayerActive = false

    fun start(uiGamepadHandler: UiGamepadHandler) {
        uiHandler = uiGamepadHandler
        GamepadManager.init()

        routeJob?.cancel()
        routeJob = scope.launch {
            GamepadManager.events.collect { event ->
                if (isPlayerActive) {
                    PlayerGamepadHandler.handleEvent(event)
                } else {
                    uiHandler?.handleEvent(event)
                }
            }
        }
    }

    fun setPlayerActive(active: Boolean) {
        isPlayerActive = active
    }

    fun stop() {
        routeJob?.cancel()
        uiHandler?.dispose()
        uiHandler = null
    }
}

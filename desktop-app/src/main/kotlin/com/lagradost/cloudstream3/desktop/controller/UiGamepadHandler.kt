package com.lagradost.cloudstream3.desktop.controller

import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.*
import java.awt.Robot
import java.awt.event.KeyEvent

class UiGamepadHandler(
    private val navController: NavController,
    private val focusManagerProvider: () -> FocusManager?,
    private val onSearchFocus: () -> Unit = {},
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var repeatJob: Job? = null
    private var activeDirection: FocusDirection? = null
    private var robot: Robot? = try { Robot() } catch (_: Throwable) { null }

    private val dockScreens = listOf(
        Screen.Home,
        Screen.Library,
        Screen.Downloads,
        Screen.Extensions,
        Screen.Settings,
    )

    fun handleEvent(event: GamepadEvent) {
        val config = GamepadManager.uiConfig.value
        if (!config.enabled) return

        when (event) {
            is GamepadEvent.ButtonDown -> handleButtonDown(event.button, config)
            is GamepadEvent.ButtonUp -> handleButtonUp(event.button)
            is GamepadEvent.AxisMove -> handleAxisMove(event.axis, event.value, config)
            is GamepadEvent.TriggerMove -> handleTriggerMove(event.isLeft, event.value)
        }
    }

    private fun handleButtonDown(button: GamepadButton, config: UiControllerConfig) {
        val focusManager = focusManagerProvider()

        when (button) {
            GamepadButton.DPAD_UP -> startDirectionalRepeat(FocusDirection.Up, config)
            GamepadButton.DPAD_DOWN -> startDirectionalRepeat(FocusDirection.Down, config)
            GamepadButton.DPAD_LEFT -> startDirectionalRepeat(FocusDirection.Left, config)
            GamepadButton.DPAD_RIGHT -> startDirectionalRepeat(FocusDirection.Right, config)

            GamepadButton.A -> {
                // Direct Compose trigger for all focused cards/buttons
                GamepadActionBridge.triggerClick()

                // Also attempt AWT Robot Enter keypress as a fallback
                try {
                    robot?.let { r ->
                        r.keyPress(KeyEvent.VK_ENTER)
                        r.keyRelease(KeyEvent.VK_ENTER)
                    }
                } catch (_: Throwable) {}
            }

            GamepadButton.B -> {
                // Back navigation
                if (navController.canGoBack()) {
                    navController.goBack()
                } else if (navController.currentScreen !is Screen.Home) {
                    navController.navigate(Screen.Home)
                }
            }

            GamepadButton.LB -> cycleDockTab(forward = false)
            GamepadButton.RB -> cycleDockTab(forward = true)

            GamepadButton.Y -> {
                GamepadActionBridge.triggerSearchFocus()
                onSearchFocus()
            }

            GamepadButton.START -> {
                if (navController.currentScreen !is Screen.Settings) {
                    navController.navigate(Screen.Settings)
                }
            }

            else -> {}
        }
    }

    private fun handleButtonUp(button: GamepadButton) {
        when (button) {
            GamepadButton.DPAD_UP, GamepadButton.DPAD_DOWN, GamepadButton.DPAD_LEFT, GamepadButton.DPAD_RIGHT -> {
                stopDirectionalRepeat()
            }
            else -> {}
        }
    }

    private fun handleAxisMove(axis: GamepadAxis, value: Float, config: UiControllerConfig) {
        if (axis == GamepadAxis.LEFT_Y) {
            if (value < -config.deadzone) {
                startDirectionalRepeat(FocusDirection.Up, config)
            } else if (value > config.deadzone) {
                startDirectionalRepeat(FocusDirection.Down, config)
            } else if (activeDirection == FocusDirection.Up || activeDirection == FocusDirection.Down) {
                stopDirectionalRepeat()
            }
        } else if (axis == GamepadAxis.LEFT_X) {
            if (value < -config.deadzone) {
                startDirectionalRepeat(FocusDirection.Left, config)
            } else if (value > config.deadzone) {
                startDirectionalRepeat(FocusDirection.Right, config)
            } else if (activeDirection == FocusDirection.Left || activeDirection == FocusDirection.Right) {
                stopDirectionalRepeat()
            }
        } else if (axis == GamepadAxis.RIGHT_Y) {
            val normalized = if (kotlin.math.abs(value) > config.deadzone) value else 0f
            GamepadActionBridge.updateRightStickY(normalized)
        }
    }

    private fun handleTriggerMove(isLeft: Boolean, value: Float) {
        if (value > 0.7f) {
            try {
                val key = if (isLeft) KeyEvent.VK_PAGE_UP else KeyEvent.VK_PAGE_DOWN
                robot?.let { r ->
                    r.keyPress(key)
                    r.keyRelease(key)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun startDirectionalRepeat(direction: FocusDirection, config: UiControllerConfig) {
        if (activeDirection == direction && repeatJob?.isActive == true) return
        activeDirection = direction
        repeatJob?.cancel()

        // Immediate first step
        val focusManager = focusManagerProvider()
        focusManager?.moveFocus(direction)

        repeatJob = scope.launch {
            delay(config.dpadRepeatDelayMs)
            while (isActive && activeDirection == direction) {
                focusManagerProvider()?.moveFocus(direction)
                delay(config.dpadRepeatIntervalMs)
            }
        }
    }

    private fun stopDirectionalRepeat() {
        activeDirection = null
        repeatJob?.cancel()
        repeatJob = null
    }

    private fun cycleDockTab(forward: Boolean) {
        stopDirectionalRepeat()
        val current = navController.currentScreen
        val currentIndex = dockScreens.indexOfFirst { it::class == current::class }
        val nextIndex = if (currentIndex == -1) {
            0
        } else if (forward) {
            (currentIndex + 1) % dockScreens.size
        } else {
            (currentIndex - 1 + dockScreens.size) % dockScreens.size
        }
        navController.navigate(dockScreens[nextIndex])
    }

    fun dispose() {
        stopDirectionalRepeat()
        scope.cancel()
    }
}

package com.lagradost.cloudstream3.desktop.controller

import com.lagradost.common.logging.AppLogger

interface PlayerControllerCallback {
    fun onPlayPause()
    fun onSeekRelative(seconds: Int)
    fun onVolumeChange(deltaPercent: Int)
    fun onCycleSubtitles()
    fun onCycleAudio()
    fun onToggleOsd()
    fun onToggleFullscreen()
    fun onCycleSpeed()
    fun onSeekStart()
    fun onSeekEnd()
    fun onCycleAspectRatio()
    fun onExitPlayer()
    fun onSetSpeed(speed: Float)
}

object PlayerGamepadHandler {
    private var callback: PlayerControllerCallback? = null
    private var isFastForwarding = false

    fun registerCallback(cb: PlayerControllerCallback) {
        callback = cb
    }

    fun unregisterCallback(cb: PlayerControllerCallback) {
        if (callback == cb) {
            callback = null
        }
    }

    fun handleEvent(event: GamepadEvent) {
        val config = GamepadManager.playerConfig.value
        if (!config.enabled) return
        val cb = callback ?: return

        when (event) {
            is GamepadEvent.ButtonDown -> {
                when (event.button) {
                    GamepadButton.A -> cb.onPlayPause()
                    GamepadButton.B -> cb.onExitPlayer()
                    GamepadButton.X -> cb.onToggleFullscreen()
                    GamepadButton.Y -> cb.onCycleSpeed()
                    GamepadButton.DPAD_LEFT -> cb.onSeekRelative(-config.shortSeekSeconds)
                    GamepadButton.DPAD_RIGHT -> cb.onSeekRelative(config.shortSeekSeconds)
                    GamepadButton.DPAD_UP -> cb.onVolumeChange(config.volumeStepPercent)
                    GamepadButton.DPAD_DOWN -> cb.onVolumeChange(-config.volumeStepPercent)
                    GamepadButton.LB -> cb.onSeekStart()
                    GamepadButton.RB -> cb.onSeekEnd()
                    GamepadButton.L3 -> cb.onCycleAudio()
                    GamepadButton.R3 -> cb.onCycleSubtitles()
                    GamepadButton.BACK -> cb.onCycleAspectRatio()
                    GamepadButton.START -> cb.onToggleOsd()
                    else -> {}
                }
            }

            is GamepadEvent.TriggerMove -> {
                if (config.triggerFastForward) {
                    if (!event.isLeft) { // Right trigger (RT)
                        if (event.value > 0.6f && !isFastForwarding) {
                            isFastForwarding = true
                            cb.onSetSpeed(2.5f)
                        } else if (event.value < 0.2f && isFastForwarding) {
                            isFastForwarding = false
                            cb.onSetSpeed(1.0f)
                        }
                    }
                }
            }

            else -> {}
        }
    }
}

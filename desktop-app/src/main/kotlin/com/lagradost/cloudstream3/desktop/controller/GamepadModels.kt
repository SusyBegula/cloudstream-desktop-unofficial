package com.lagradost.cloudstream3.desktop.controller

import com.lagradost.common.storage.DesktopDataStore

enum class GamepadButton(val displayName: String) {
    A("A (Select)"),
    B("B (Back)"),
    X("X (Action)"),
    Y("Y (Search)"),
    DPAD_UP("D-Pad Up"),
    DPAD_DOWN("D-Pad Down"),
    DPAD_LEFT("D-Pad Left"),
    DPAD_RIGHT("D-Pad Right"),
    LB("LB (Left Bumper)"),
    RB("RB (Right Bumper)"),
    START("Start / Menu"),
    BACK("Back / View"),
    GUIDE("Xbox / Guide"),
    L3("L3 (Left Stick Click)"),
    R3("R3 (Right Stick Click)"),
}

enum class GamepadAxis {
    LEFT_X,
    LEFT_Y,
    RIGHT_X,
    RIGHT_Y,
    LEFT_TRIGGER,
    RIGHT_TRIGGER,
}

data class ControllerInfo(
    val index: Int,
    val name: String,
    val isConnected: Boolean,
    val buttonStates: Map<GamepadButton, Boolean> = emptyMap(),
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
)

sealed class GamepadEvent {
    data class ButtonDown(val controllerIndex: Int, val button: GamepadButton) : GamepadEvent()
    data class ButtonUp(val controllerIndex: Int, val button: GamepadButton) : GamepadEvent()
    data class AxisMove(val controllerIndex: Int, val axis: GamepadAxis, val value: Float) : GamepadEvent()
    data class TriggerMove(val controllerIndex: Int, val isLeft: Boolean, val value: Float) : GamepadEvent()
}

/**
 * Action bridge to trigger direct clicks on focused composables when controller action buttons are pressed
 */
object GamepadActionBridge {
    val clickTrigger = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val searchTrigger = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val rightStickY = kotlinx.coroutines.flow.MutableStateFlow(0f)

    fun triggerClick() {
        clickTrigger.value += 1
    }

    fun triggerSearchFocus() {
        searchTrigger.value += 1
    }

    fun updateRightStickY(y: Float) {
        rightStickY.value = y
    }
}

/**
 * Configuration for Gamepad interaction within the Desktop Application UI (Home, Library, Settings, etc.)
 */
data class UiControllerConfig(
    val enabled: Boolean = true,
    val deadzone: Float = 0.22f,
    val dpadRepeatDelayMs: Long = 250L,
    val dpadRepeatIntervalMs: Long = 120L,
    val scrollSpeed: Float = 1.0f,
    val tvFocusEffect: Boolean = true,
) {
    companion object {
        const val PREF_KEY = "gamepad_ui_config"

        fun load(): UiControllerConfig {
            return DesktopDataStore.getKey<UiControllerConfig>(PREF_KEY) ?: UiControllerConfig()
        }

        fun save(config: UiControllerConfig) {
            DesktopDataStore.setKey(PREF_KEY, config)
        }
    }
}

/**
 * Configuration for Gamepad interaction within the Embedded MPV Video Player
 */
data class PlayerControllerConfig(
    val enabled: Boolean = true,
    val shortSeekSeconds: Int = 10,
    val longSeekSeconds: Int = 60,
    val volumeStepPercent: Int = 5,
    val triggerFastForward: Boolean = true,
    val rumbleOnFinish: Boolean = true,
    val rumbleOnSeek: Boolean = false,
) {
    companion object {
        const val PREF_KEY = "gamepad_player_config"

        fun load(): PlayerControllerConfig {
            return DesktopDataStore.getKey<PlayerControllerConfig>(PREF_KEY) ?: PlayerControllerConfig()
        }

        fun save(config: PlayerControllerConfig) {
            DesktopDataStore.setKey(PREF_KEY, config)
        }
    }
}

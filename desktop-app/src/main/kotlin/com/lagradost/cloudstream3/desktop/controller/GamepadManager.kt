package com.lagradost.cloudstream3.desktop.controller

import com.lagradost.common.logging.AppLogger
import com.studiohartman.jamepad.ControllerAxis
import com.studiohartman.jamepad.ControllerButton
import com.studiohartman.jamepad.ControllerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

object GamepadManager {
    private var manager: ControllerManager? = null
    private val isInitialized = AtomicBoolean(false)
    private var pollJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _controllers = MutableStateFlow<List<ControllerInfo>>(emptyList())
    val controllers = _controllers.asStateFlow()

    private val _events = MutableSharedFlow<GamepadEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    // Config flows
    val uiConfig = MutableStateFlow(UiControllerConfig.load())
    val playerConfig = MutableStateFlow(PlayerControllerConfig.load())

    private val prevButtonStates = mutableMapOf<Int, MutableMap<GamepadButton, Boolean>>()
    private val prevTriggerStates = mutableMapOf<Int, Pair<Float, Float>>()
    private val prevAxisStates = mutableMapOf<Int, MutableMap<GamepadAxis, Float>>()

    fun init() {
        if (isInitialized.getAndSet(true)) return

        scope.launch {
            try {
                val mgr = ControllerManager()
                mgr.initSDLGamepad()
                manager = mgr
                AppLogger.i("Jamepad ControllerManager initialized successfully with SDL2")
                startPolling()
            } catch (e: Throwable) {
                AppLogger.e("Failed to initialize Gamepad ControllerManager", e)
            }
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val mgr = manager ?: break
                try {
                    mgr.update()
                    val numControllers = mgr.numControllers
                    val activeList = mutableListOf<ControllerInfo>()

                    for (i in 0 until numControllers) {
                        val state = mgr.getState(i)
                        if (!state.isConnected) continue

                        val controllerIndex = mgr.getControllerIndex(i)
                        val name = try {
                            controllerIndex.name ?: "Gamepad #$i"
                        } catch (_: Throwable) {
                            "Gamepad #$i"
                        }

                        val currentButtons = mutableMapOf(
                            GamepadButton.A to state.a,
                            GamepadButton.B to state.b,
                            GamepadButton.X to state.y,
                            GamepadButton.Y to state.x,
                            GamepadButton.DPAD_UP to state.dpadUp,
                            GamepadButton.DPAD_DOWN to state.dpadDown,
                            GamepadButton.DPAD_LEFT to state.dpadLeft,
                            GamepadButton.DPAD_RIGHT to state.dpadRight,
                            GamepadButton.LB to state.lb,
                            GamepadButton.RB to state.rb,
                            GamepadButton.START to state.start,
                            GamepadButton.BACK to state.back,
                            GamepadButton.GUIDE to state.guide,
                            GamepadButton.L3 to state.leftStickClick,
                            GamepadButton.R3 to state.rightStickClick,
                        )

                        val prevButtons = prevButtonStates.getOrPut(i) { mutableMapOf() }
                        for ((btn, isDown) in currentButtons) {
                            val wasDown = prevButtons[btn] ?: false
                            if (isDown && !wasDown) {
                                _events.tryEmit(GamepadEvent.ButtonDown(i, btn))
                            } else if (!isDown && wasDown) {
                                _events.tryEmit(GamepadEvent.ButtonUp(i, btn))
                            }
                            prevButtons[btn] = isDown
                        }

                        // Analog stick deadzone filtering
                        val deadzone = uiConfig.value.deadzone
                        val lx = if (kotlin.math.abs(state.leftStickX) > deadzone) state.leftStickX else 0f
                        val ly = if (kotlin.math.abs(state.leftStickY) > deadzone) state.leftStickY else 0f
                        val rx = if (kotlin.math.abs(state.rightStickX) > deadzone) state.rightStickX else 0f
                        val ry = if (kotlin.math.abs(state.rightStickY) > deadzone) state.rightStickY else 0f

                        val prevAxes = prevAxisStates.getOrPut(i) { mutableMapOf() }
                        val axes = mapOf(
                            GamepadAxis.LEFT_X to lx,
                            GamepadAxis.LEFT_Y to ly,
                            GamepadAxis.RIGHT_X to rx,
                            GamepadAxis.RIGHT_Y to ry,
                        )
                        for ((axis, valNow) in axes) {
                            val valPrev = prevAxes[axis] ?: 0f
                            if (kotlin.math.abs(valNow - valPrev) > 0.02f || (valNow == 0f && valPrev != 0f)) {
                                _events.tryEmit(GamepadEvent.AxisMove(i, axis, valNow))
                                prevAxes[axis] = valNow
                            }
                        }

                        val lt = state.leftTrigger
                        val rt = state.rightTrigger

                        val prevTrig = prevTriggerStates.getOrDefault(i, Pair(0f, 0f))
                        if (kotlin.math.abs(lt - prevTrig.first) > 0.05f) {
                            _events.tryEmit(GamepadEvent.TriggerMove(i, isLeft = true, lt))
                        }
                        if (kotlin.math.abs(rt - prevTrig.second) > 0.05f) {
                            _events.tryEmit(GamepadEvent.TriggerMove(i, isLeft = false, rt))
                        }
                        prevTriggerStates[i] = Pair(lt, rt)

                        activeList.add(
                            ControllerInfo(
                                index = i,
                                name = name,
                                isConnected = true,
                                buttonStates = currentButtons,
                                leftStickX = lx,
                                leftStickY = ly,
                                rightStickX = rx,
                                rightStickY = ry,
                                leftTrigger = lt,
                                rightTrigger = rt,
                            ),
                        )
                    }

                    _controllers.value = activeList
                } catch (e: Throwable) {
                    AppLogger.e("Error during Gamepad polling", e)
                }

                delay(16) // ~60 Hz polling
            }
        }
    }

    fun vibrate(controllerIndex: Int = 0, leftMotor: Float = 0.5f, rightMotor: Float = 0.5f, durationMs: Int = 200) {
        val mgr = manager ?: return
        try {
            if (controllerIndex < mgr.numControllers) {
                val index = mgr.getControllerIndex(controllerIndex)
                if (index.isConnected) {
                    index.doVibration(leftMotor, rightMotor, durationMs)
                }
            }
        } catch (e: Throwable) {
            AppLogger.e("Failed to trigger controller vibration", e)
        }
    }

    fun updateUiConfig(newConfig: UiControllerConfig) {
        uiConfig.value = newConfig
        UiControllerConfig.save(newConfig)
    }

    fun updatePlayerConfig(newConfig: PlayerControllerConfig) {
        playerConfig.value = newConfig
        PlayerControllerConfig.save(newConfig)
    }

    fun shutdown() {
        pollJob?.cancel()
        manager?.quitSDLGamepad()
        manager = null
        isInitialized.set(false)
    }
}

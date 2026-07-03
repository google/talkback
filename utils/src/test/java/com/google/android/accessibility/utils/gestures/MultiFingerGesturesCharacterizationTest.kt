/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Ported from Java to Kotlin.
 */

package com.google.android.accessibility.utils.gestures

import android.os.Looper
import android.view.MotionEvent
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO dos matchers multi-dedo:
 *
 * MultiFingerMultiTap (ex.: toque duplo de 2 dedos):
 *  - um "tap" conta quando TODOS os dedos desceram e subiram dentro das
 *    janelas (tapTimeout escala com o nº de dedos: N × 100 ms);
 *  - o gesto COMEÇA no 1º tap completo e COMPLETA após o último tap +
 *    doubleTapTimeout (completeAfterDoubleTapTimeout);
 *  - dedos demais cancelam.
 *
 * MultiFingerMultiTapAndHold: SEGURAR no início do último tap completa após o
 * longPressTimeout; SOLTAR no último tap cancela (não é hold).
 *
 * MultiFingerSwipe: todos os dedos na MESMA direção completam no UP; um dedo
 * na direção errada cancela na hora.
 *
 * TwoFingerSecondFingerMultiTap (rotor): 2 dedos descem juntos, UM segura e o
 * OUTRO tapeia N vezes; completa no UP final do dedo-âncora.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiFingerGesturesCharacterizationTest {
    private val states = mutableListOf<Int>()
    private val listener = GestureMatcher.StateChangeListener { _, state, _ -> states += state }
    private val context = RuntimeEnvironment.getApplication()

    private val oneX = floatArrayOf(X)
    private val oneY = floatArrayOf(Y)
    private val twoXs = floatArrayOf(X, X + 100)
    private val twoYs = floatArrayOf(Y, Y)

    // ----- MultiFingerMultiTap -----

    @Test
    fun `toque duplo de 2 dedos completa apos a janela do ultimo tap`() {
        val matcher = MultiFingerMultiTap(context, 2, 2, GESTURE_ID, listener) {}
        twoFingerTap(matcher, 0)
        twoFingerTap(matcher, 150)
        assertFalse(states.contains(GestureMatcher.STATE_GESTURE_COMPLETED))
        idleMainLooper(GestureConfiguration.DOUBLE_TAP_TIMEOUT_MS + 50L)
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, states.last())
    }

    @Test
    fun `um so toque de 2 dedos comeca o gesto mas nao completa`() {
        val matcher = MultiFingerMultiTap(context, 2, 2, GESTURE_ID, listener) {}
        twoFingerTap(matcher, 0)
        assertTrue(states.contains(GestureMatcher.STATE_GESTURE_STARTED))
        assertFalse(states.contains(GestureMatcher.STATE_GESTURE_COMPLETED))
    }

    @Test
    fun `terceiro dedo cancela o matcher de 2 dedos`() {
        val matcher = MultiFingerMultiTap(context, 2, 2, GESTURE_ID, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(10, twoXs, twoYs))
        val threeXs = floatArrayOf(X, X + 100, X + 200)
        val threeYs = floatArrayOf(Y, Y, Y)
        val state = matcher.onMotionEvent(null, pointerDown(20, threeXs, threeYs))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    // ----- MultiFingerMultiTapAndHold -----

    @Test
    fun `2 dedos - tap e SEGURAR no 2o tap completa apos o longPressTimeout`() {
        val matcher = MultiFingerMultiTapAndHold(context, 2, 2, GESTURE_HOLD_ID, listener) {}
        twoFingerTap(matcher, 0)
        // 2º tap desce e SEGURA.
        matcher.onMotionEvent(null, down(150, X, Y))
        matcher.onMotionEvent(null, pointerDown(160, twoXs, twoYs))
        assertFalse(states.contains(GestureMatcher.STATE_GESTURE_COMPLETED))
        idleMainLooper(600)
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, states.last())
    }

    @Test
    fun `soltar no ultimo tap cancela o and-hold (nao e hold)`() {
        val matcher = MultiFingerMultiTapAndHold(context, 2, 2, GESTURE_HOLD_ID, listener) {}
        twoFingerTap(matcher, 0)
        matcher.onMotionEvent(null, down(150, X, Y))
        matcher.onMotionEvent(null, pointerDown(160, twoXs, twoYs))
        matcher.onMotionEvent(null, pointerUp(200, twoXs, twoYs, 1))
        val state = matcher.onMotionEvent(null, up(220, X, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    // ----- MultiFingerSwipe -----

    @Test
    fun `swipe de 2 dedos para cima completa quando os dois dedos sobem`() {
        val matcher = MultiFingerSwipe(context, 2, MultiFingerSwipe.UP, GESTURE_SWIPE_ID, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(10, twoXs, twoYs))
        matcher.onMotionEvent(null, moveMulti(30, floatArrayOf(X, X + 100), floatArrayOf(Y - 100, Y - 100)))
        matcher.onMotionEvent(null, moveMulti(50, floatArrayOf(X, X + 100), floatArrayOf(Y - 200, Y - 200)))
        matcher.onMotionEvent(
            null,
            pointerUp(70, floatArrayOf(X, X + 100), floatArrayOf(Y - 200, Y - 200), 1),
        )
        val state = matcher.onMotionEvent(null, up(90, X, Y - 200))
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, state)
    }

    @Test
    fun `dedo na direcao errada cancela o swipe de 2 dedos`() {
        val matcher = MultiFingerSwipe(context, 2, MultiFingerSwipe.UP, GESTURE_SWIPE_ID, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(10, twoXs, twoYs))
        val state =
            matcher.onMotionEvent(
                null,
                moveMulti(30, floatArrayOf(X + 100, X + 200), floatArrayOf(Y, Y)), // direita
            )
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    // ----- TwoFingerSecondFingerMultiTap (rotor) -----

    @Test
    fun `ancora segura e o 2o dedo tapeia 2x - completa no UP final da ancora`() {
        val matcher =
            TwoFingerSecondFingerMultiTap(
                context, 2, TwoFingerSecondFingerMultiTap.ROTATE_DIRECTION_DONT_CARE,
                GESTURE_ROTOR_ID, listener,
            ) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(10, twoXs, twoYs)) // tap 1 desce
        matcher.onMotionEvent(null, pointerUp(50, twoXs, twoYs, 1)) // tap 1 sobe
        matcher.onMotionEvent(null, pointerDown(100, twoXs, twoYs)) // tap 2 desce
        matcher.onMotionEvent(null, pointerUp(150, twoXs, twoYs, 1)) // tap 2 sobe
        val state = matcher.onMotionEvent(null, up(200, X, Y)) // âncora levanta
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, state)
    }

    @Test
    fun `tap do 2o dedo segurado alem do tapTimeout cancela o rotor`() {
        val matcher =
            TwoFingerSecondFingerMultiTap(
                context, 2, TwoFingerSecondFingerMultiTap.ROTATE_DIRECTION_DONT_CARE,
                GESTURE_ROTOR_ID, listener,
            ) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(10, twoXs, twoYs))
        // 2 dedos → tapTimeout = 2×100 = 200 ms; segurou 250 ms → cancela.
        val state = matcher.onMotionEvent(null, pointerUp(260, twoXs, twoYs, 1))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    /** Um toque completo de 2 dedos (desce 1→2, sobe 2→1), sem movimento. */
    private fun twoFingerTap(matcher: GestureMatcher, startTime: Long) {
        matcher.onMotionEvent(null, down(startTime, X, Y))
        matcher.onMotionEvent(null, pointerDown(startTime + 10, twoXs, twoYs))
        matcher.onMotionEvent(null, pointerUp(startTime + 40, twoXs, twoYs, 1))
        matcher.onMotionEvent(null, up(startTime + 60, X, Y))
    }

    private fun idleMainLooper(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    private fun down(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)

    private fun up(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_UP, x, y, 0)

    private fun pointerDown(t: Long, xs: FloatArray, ys: FloatArray) =
        multi(t, MotionEvent.ACTION_POINTER_DOWN, xs.size - 1, xs, ys)

    private fun pointerUp(t: Long, xs: FloatArray, ys: FloatArray, index: Int) =
        multi(t, MotionEvent.ACTION_POINTER_UP, index, xs, ys)

    private fun moveMulti(t: Long, xs: FloatArray, ys: FloatArray) =
        multi(t, MotionEvent.ACTION_MOVE, 0, xs, ys)

    private fun multi(t: Long, action: Int, actionIndex: Int, xs: FloatArray, ys: FloatArray): MotionEvent {
        val count = xs.size
        val props =
            Array(count) {
                MotionEvent.PointerProperties().apply {
                    id = it
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
        val coords =
            Array(count) {
                MotionEvent.PointerCoords().apply {
                    x = xs[it]
                    y = ys[it]
                    pressure = 1f
                    size = 1f
                }
            }
        val masked = action or (actionIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        return MotionEvent.obtain(0, t, masked, count, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private companion object {
        const val GESTURE_ID = 20 // GESTURE_2_FINGER_DOUBLE_TAP
        const val GESTURE_HOLD_ID = 40 // GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD
        const val GESTURE_SWIPE_ID = 26 // GESTURE_2_FINGER_SWIPE_UP
        const val GESTURE_ROTOR_ID = -4 // GESTURE_TAP_HOLD_AND_2ND_FINGER_FORWARD_DOUBLE_TAP
        const val X = 200f
        const val Y = 400f
    }
}

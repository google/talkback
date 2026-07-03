/*
 * Copyright (C) The Android Open Source Project
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO da família do SPLIT-TAP (1º dedo segura, 2º toca):
 *
 * SecondFingerTap (split typing):
 *  - o 2º dedo só VALE se descer DEPOIS de doubleTapTimeout (300 ms) do 1º
 *    DOWN — antes disso é candidato a gesto de 2 dedos, e o matcher CANCELA;
 *  - ao completar, o matcher NOTIFICA COMPLETED e em seguida REINICIA
 *    (restart + startGesture) para aceitar o próximo split-tap na MESMA
 *    interação (digitação contínua);
 *  - levantar o 1º dedo (ACTION_UP) sempre cancela.
 *
 * SecondFingerTapAndHold: 2º dedo desce (após a janela) e SEGURA → completa
 * após o longPressTimeout; levantar o 2º dedo antes cancela.
 *
 * SecondFingerMultiTap (variante antiga, rotor): 2 toques do 2º dedo dentro
 * das janelas (tap ≤ 100 ms; entre toques ≤ 250 ms) completam.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SecondFingerGesturesCharacterizationTest {
    private val states = mutableListOf<Int>()
    private val listener = GestureMatcher.StateChangeListener { _, state, _ -> states += state }
    private val context = RuntimeEnvironment.getApplication()

    // ----- SecondFingerTap (split typing) -----

    @Test
    fun `split-tap valido - 2o dedo desce apos 300ms, toca e completa, e o matcher reinicia`() {
        val matcher = SecondFingerTap(context, 1, GESTURE_SPLIT, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(350, twoXs, twoYs))
        val state = matcher.onMotionEvent(null, pointerUp(400, twoXs, twoYs, 1))
        assertTrue(states.contains(GestureMatcher.STATE_GESTURE_COMPLETED))
        // Reinicia para o próximo split-tap na mesma interação (digitação contínua).
        assertEquals(GestureMatcher.STATE_GESTURE_STARTED, state)
    }

    @Test
    fun `2o dedo cedo demais (antes de 300ms do 1o DOWN) cancela o split-tap`() {
        val matcher = SecondFingerTap(context, 1, GESTURE_SPLIT, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, pointerDown(100, twoXs, twoYs))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `levantar o 1o dedo cancela o split-tap`() {
        val matcher = SecondFingerTap(context, 1, GESTURE_SPLIT, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, up(400, X, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    // ----- SecondFingerTapAndHold -----

    @Test
    fun `segurar o 2o dedo completa apos o longPressTimeout`() {
        val matcher = SecondFingerTapAndHold(context, 1, GESTURE_SPLIT_HOLD, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(350, twoXs, twoYs))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, states.last())
    }

    @Test
    fun `levantar o 2o dedo antes do hold cancela`() {
        val matcher = SecondFingerTapAndHold(context, 1, GESTURE_SPLIT_HOLD, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(350, twoXs, twoYs))
        val state = matcher.onMotionEvent(null, pointerUp(400, twoXs, twoYs, 1))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    // ----- SecondFingerMultiTap (variante antiga) -----

    @Test
    fun `dois toques do 2o dedo dentro das janelas completam`() {
        val matcher = SecondFingerMultiTap(context, 2, GESTURE_ROTATE, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(10, twoXs, twoYs))
        matcher.onMotionEvent(null, pointerUp(50, twoXs, twoYs, 1)) // tap 1 (40ms)
        matcher.onMotionEvent(null, pointerDown(150, twoXs, twoYs)) // 100ms depois (≤250)
        val state = matcher.onMotionEvent(null, pointerUp(200, twoXs, twoYs, 1)) // tap 2
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, state)
    }

    @Test
    fun `toque do 2o dedo mais longo que o tapTimeout cancela`() {
        val matcher = SecondFingerMultiTap(context, 2, GESTURE_ROTATE, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(10, twoXs, twoYs))
        val state = matcher.onMotionEvent(null, pointerUp(200, twoXs, twoYs, 1)) // 190ms > 100
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    private val twoXs = floatArrayOf(X, X + 120)
    private val twoYs = floatArrayOf(Y, Y)

    private fun down(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)

    private fun up(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_UP, x, y, 0)

    private fun pointerDown(t: Long, xs: FloatArray, ys: FloatArray) =
        multi(t, MotionEvent.ACTION_POINTER_DOWN, xs.size - 1, xs, ys)

    private fun pointerUp(t: Long, xs: FloatArray, ys: FloatArray, index: Int) =
        multi(t, MotionEvent.ACTION_POINTER_UP, index, xs, ys)

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
        const val GESTURE_SPLIT = -3 // GESTURE_FAKED_SPLIT_TYPING
        const val GESTURE_SPLIT_HOLD = -8 // GESTURE_FAKED_SPLIT_TYPING_AND_HOLD
        const val GESTURE_ROTATE = -4
        const val X = 200f
        const val Y = 400f
    }
}

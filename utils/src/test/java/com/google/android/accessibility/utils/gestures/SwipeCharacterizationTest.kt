/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO do Swipe de 1 dedo:
 *  - confirma como GESTO ao percorrer 1 cm dentro de 150 ms (config_*);
 *  - lento demais para COMEÇAR (150 ms sem 1 cm) cancela — vira exploração;
 *  - a PRIMEIRA direção errada cancela imediatamente;
 *  - swipe em ÂNGULO (2 direções) é segmentado por mudança de ~90°;
 *  - 2º dedo cancela;
 *  - UP com o matcher em CLEAR limpa SILENCIOSAMENTE (sem notificar cancel —
 *    comentário do onUp: não travar a próxima detecção).
 *
 * Métricas do Robolectric (mdpi): 1 cm ≈ 63 px; amostra 0,25 cm ≈ 15,7 px.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SwipeCharacterizationTest {
    private val states = mutableListOf<Int>()

    private fun swipe(vararg directions: Int): Swipe {
        val context = RuntimeEnvironment.getApplication()
        val listener = GestureMatcher.StateChangeListener { _, state, _ -> states += state }
        val provider = object : GestureManifold.GestureConfigProvider {}
        return if (directions.size == 1) {
            Swipe(context, directions[0], GESTURE_ID, listener, provider) {}
        } else {
            Swipe(context, directions[0], directions[1], GESTURE_ID, listener, provider) {}
        }
    }

    @Test
    fun `swipe reto rapido (1cm em menos de 150ms) completa`() {
        val matcher = swipe(Swipe.RIGHT)
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, move(50, X + 40, Y))
        matcher.onMotionEvent(null, move(100, X + 70, Y)) // cruzou 1cm (~63px) em 100ms
        val state = matcher.onMotionEvent(null, up(120, X + 80, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, state)
    }

    @Test
    fun `comecar devagar demais cancela (vira exploracao)`() {
        val matcher = swipe(Swipe.RIGHT)
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, move(60, X + 30, Y))
        // 200ms sem completar 1cm → maxStartThreshold (150ms) estourou.
        val state = matcher.onMotionEvent(null, move(200, X + 50, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `primeira direcao errada cancela imediatamente`() {
        val matcher = swipe(Swipe.RIGHT)
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, move(50, X - 40, Y)) // esquerda
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `swipe em angulo direita-baixo e segmentado e completa`() {
        val matcher = swipe(Swipe.RIGHT, Swipe.DOWN)
        matcher.onMotionEvent(null, down(0, X, Y))
        // Perna direita, amostrada a cada 20px.
        matcher.onMotionEvent(null, move(10, X + 20, Y))
        matcher.onMotionEvent(null, move(20, X + 40, Y))
        matcher.onMotionEvent(null, move(30, X + 60, Y))
        matcher.onMotionEvent(null, move(40, X + 80, Y))
        // Perna para baixo.
        matcher.onMotionEvent(null, move(55, X + 80, Y + 50))
        matcher.onMotionEvent(null, move(70, X + 80, Y + 100))
        matcher.onMotionEvent(null, move(85, X + 80, Y + 150))
        matcher.onMotionEvent(null, move(100, X + 80, Y + 200))
        val state = matcher.onMotionEvent(null, up(110, X + 80, Y + 205))
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, state)
    }

    @Test
    fun `segundo dedo cancela o swipe de 1 dedo`() {
        val matcher = swipe(Swipe.RIGHT)
        matcher.onMotionEvent(null, down(0, X, Y))
        val state =
            matcher.onMotionEvent(null, pointerDown(10, floatArrayOf(X, X + 80), floatArrayOf(Y, Y)))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `up com o matcher em CLEAR limpa sem notificar cancelamento`() {
        val matcher = swipe(Swipe.RIGHT)
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, up(30, X, Y)) // toque parado: nada de swipe
        assertEquals(GestureMatcher.STATE_CLEAR, state)
        assertFalse(states.contains(GestureMatcher.STATE_GESTURE_CANCELED))
    }

    private fun down(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)

    private fun up(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_UP, x, y, 0)

    private fun move(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_MOVE, x, y, 0)

    private fun pointerDown(t: Long, xs: FloatArray, ys: FloatArray): MotionEvent {
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
        val masked =
            MotionEvent.ACTION_POINTER_DOWN or
                ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        return MotionEvent.obtain(0, t, masked, count, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
    }

    private companion object {
        const val GESTURE_ID = 4 // GESTURE_SWIPE_RIGHT
        const val X = 200f
        const val Y = 400f
    }
}

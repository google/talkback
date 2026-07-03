/*
 * Copyright (C) 2019 The Android Open Source Project
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO do MultiTap: fixa o comportamento observável do
 * detector de duplo-toque ANTES da conversão para Kotlin. Este teste roda verde
 * contra o MultiTap.java original e deve rodar verde, SEM ALTERAÇÃO, contra o
 * MultiTap.kt convertido.
 *
 * Comportamentos fixados (MultiTap.java):
 *  - a janela entre toques é UP(n) → DOWN(n+1) ≤ doubleTapTimeout (onDown:100-105)
 *    — a duração do toque seguinte NÃO conta;
 *  - cada toque individual dura ≤ tapTimeout (isValidUpEvent:231-237);
 *  - o toque seguinte precisa cair dentro do doubleTapSlop do anterior
 *    (onDown:111-115);
 *  - mover além do touchSlop dentro de um toque cancela (onMove:150-156);
 *  - o gesto COMEÇA no DOWN do toque final (onDown:118-124) e COMPLETA no UP.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiTapCharacterizationTest {
    private val states = mutableListOf<Int>()
    private lateinit var matcher: MultiTap

    @Before
    fun setUp() {
        matcher =
            MultiTap(
                RuntimeEnvironment.getApplication(),
                /* taps= */ 2,
                /* gesture= */ GESTURE_ID,
                /* listener= */ { _, state, _ -> states += state },
                /* configProvider= */ object : GestureManifold.GestureConfigProvider {},
                /* logger= */ {},
            )
    }

    @Test
    fun `duplo-toque completa quando o DOWN do 2o toque cai na janela apos o UP do 1o`() {
        // UP1→DOWN2 = 240ms (≤ doubleTapTimeout=250) mas UP1→UP2 = 290ms: a
        // duração do 2º toque NÃO conta — só o intervalo até o DOWN.
        val up1 = 50L
        val down2 = up1 + matcher.doubleTapTimeout - 10L
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, up(up1, X, Y))
        matcher.onMotionEvent(null, down(down2, X, Y))
        val final = matcher.onMotionEvent(null, up(down2 + 50, X, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, final)
    }

    @Test
    fun `DOWN do 2o toque depois da janela cancela o duplo-toque`() {
        val up1 = 50L
        val down2 = up1 + matcher.doubleTapTimeout + 10L
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, up(up1, X, Y))
        val state = matcher.onMotionEvent(null, down(down2, X, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `um toque so nao completa - notifica PROCESSING mas o estado interno fica CLEAR`() {
        // GestureMatcher.setState: PROCESSING é notificado ao listener SEM virar
        // estado interno (só estende o timer de exploração no monitor).
        matcher.onMotionEvent(null, down(0, X, Y))
        val returned = matcher.onMotionEvent(null, up(50, X, Y))
        assertEquals(GestureMatcher.STATE_CLEAR, returned)
        assertEquals(GestureMatcher.STATE_GESTURE_PROCESSING, states.last())
    }

    @Test
    fun `toque individual mais longo que tapTimeout e invalido`() {
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, up(matcher.tapTimeout + 50L, X, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `2o toque fora do doubleTapSlop cancela`() {
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, up(50, X, Y))
        val farX = X + matcher.doubleTapSlop + 10f
        val state = matcher.onMotionEvent(null, down(150, farX, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `mover alem do touchSlop dentro do toque cancela`() {
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, move(20, X + matcher.touchSlop + 5f, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `o gesto COMECA no DOWN do toque final e o id do gesto e propagado`() {
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, up(50, X, Y))
        val state = matcher.onMotionEvent(null, down(150, X, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_STARTED, state)
        assertEquals(GESTURE_ID, matcher.gestureId)
    }

    private fun down(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)

    private fun up(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_UP, x, y, 0)

    private fun move(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_MOVE, x, y, 0)

    private companion object {
        const val GESTURE_ID = 17 // AccessibilityService.GESTURE_DOUBLE_TAP
        const val X = 200f
        const val Y = 400f
    }
}

/*
 * Copyright (C) 2024 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 * Ported from Java to Kotlin.
 */

package com.google.android.accessibility.utils.gestures

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO dos pseudo-gestos que ACELERAM a entrada em
 * exploração por toque:
 *
 * TapToTouchExplore — completa no UP quando o toque foi LENTO DEMAIS para ser
 * tap (durou mais que o tapTimeout): nenhum outro matcher de 1 dedo pode mais
 * casar, então é seguro explorar já. Um tap RÁPIDO cancela (pode ser
 * duplo-toque).
 *
 * TapUpToTouchExplore — completa no MOVE quando o dedo ficou PARADO (dentro de
 * 1 cm) por mais que o maxStartThreshold (150 ms): não há mais swipe possível.
 * Mover além de 1 cm cancela; levantar o dedo cancela.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TouchExploreMatchersCharacterizationTest {
    private val states = mutableListOf<Int>()
    private val listener = GestureMatcher.StateChangeListener { _, state, _ -> states += state }
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `toque mais lento que o tapTimeout completa o TapToTouchExplore no UP`() {
        val matcher = TapToTouchExplore(context, GESTURE_TAP_EXPLORE, listener, provider) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, up(150, X, Y)) // 150ms > tapTimeout(100)
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, state)
    }

    @Test
    fun `tap rapido cancela o TapToTouchExplore (pode ser duplo-toque)`() {
        val matcher = TapToTouchExplore(context, GESTURE_TAP_EXPLORE, listener, provider) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, up(50, X, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `mover alem do touchSlop cancela o TapToTouchExplore`() {
        val matcher = TapToTouchExplore(context, GESTURE_TAP_EXPLORE, listener, provider) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, move(50, X + 100, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `dedo parado por mais de 150ms completa o TapUpToTouchExplore no MOVE`() {
        val matcher = TapUpToTouchExplore(context, GESTURE_TAP_UP_EXPLORE, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, move(200, X + 5, Y)) // parado (<<1cm), 200ms
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, state)
    }

    @Test
    fun `mover mais de 1cm cancela o TapUpToTouchExplore (pode ser swipe)`() {
        val matcher = TapUpToTouchExplore(context, GESTURE_TAP_UP_EXPLORE, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, move(50, X + 100, Y)) // 100px > ~63px (1cm)
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `levantar o dedo cancela o TapUpToTouchExplore`() {
        val matcher = TapUpToTouchExplore(context, GESTURE_TAP_UP_EXPLORE, listener) {}
        matcher.onMotionEvent(null, down(0, X, Y))
        val state = matcher.onMotionEvent(null, up(50, X, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    private val provider = object : GestureManifold.GestureConfigProvider {}

    private fun down(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)

    private fun up(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_UP, x, y, 0)

    private fun move(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_MOVE, x, y, 0)

    private companion object {
        const val GESTURE_TAP_EXPLORE = -6 // GESTURE_TOUCH_EXPLORE
        const val GESTURE_TAP_UP_EXPLORE = -7 // GESTURE_TAP_UP_TOUCH_EXPLORE
        const val X = 200f
        const val Y = 400f
    }
}

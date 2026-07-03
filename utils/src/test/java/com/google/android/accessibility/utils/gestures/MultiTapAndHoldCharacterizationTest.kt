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

import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO do MultiTapAndHold (duplo-toque-e-segura):
 *  - o gesto COMPLETA sozinho após o longPressTimeout com o dedo parado no
 *    toque final (completeAfterLongPressTimeout no DOWN final);
 *  - LEVANTAR no toque final antes do long-press CANCELA (não é hold);
 *  - um toque só, sem o seguinte, expira após o doubleTapTimeout.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiTapAndHoldCharacterizationTest {
    private val states = mutableListOf<Int>()
    private lateinit var matcher: MultiTapAndHold

    @Before
    fun setUp() {
        matcher =
            MultiTapAndHold(
                RuntimeEnvironment.getApplication(),
                /* taps= */ 2,
                /* gesture= */ GESTURE_ID,
                /* listener= */ { _, state, _ -> states += state },
                /* configProvider= */ object : GestureManifold.GestureConfigProvider {},
                /* logger= */ {},
            )
    }

    @Test
    fun `segurar no toque final completa apos o longPressTimeout`() {
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, up(50, X, Y))
        matcher.onMotionEvent(null, down(200, X, Y)) // 2º toque desce e SEGURA
        assertFalse(states.contains(GestureMatcher.STATE_GESTURE_COMPLETED))
        idleMainLooper(ViewConfiguration.getLongPressTimeout().toLong() + 50)
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, states.last())
    }

    @Test
    fun `levantar no toque final antes do long-press cancela (nao e hold)`() {
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, up(50, X, Y))
        matcher.onMotionEvent(null, down(200, X, Y))
        val state = matcher.onMotionEvent(null, up(250, X, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    @Test
    fun `um toque so expira depois da janela de duplo-toque`() {
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, up(50, X, Y))
        idleMainLooper(GestureConfiguration.DOUBLE_TAP_TIMEOUT_MS + 50L)
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, states.last())
    }

    private fun idleMainLooper(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    private fun down(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)

    private fun up(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_UP, x, y, 0)

    private companion object {
        const val GESTURE_ID = 18 // AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD
        const val X = 200f
        const val Y = 400f
    }
}

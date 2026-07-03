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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO do TwoFingerSingleTapAndLongHold (recuperação de
 * mau-disparo): 2 dedos descem e SEGURAM → completa após 5000 ms (LONG_HOLD,
 * maior que o long-press normal); levantar antes cancela (a variante "tap"
 * deste padrão pertence ao MultiFingerMultiTap, não a esta classe).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwoFingerSingleTapAndLongHoldCharacterizationTest {
    private val states = mutableListOf<Int>()
    private lateinit var matcher: TwoFingerSingleTapAndLongHold

    @Before
    fun setUp() {
        matcher =
            TwoFingerSingleTapAndLongHold(
                RuntimeEnvironment.getApplication(),
                /* gestureId= */ GESTURE_ID,
                /* listener= */ { _, state, _ -> states += state },
                /* logger= */ {},
            )
    }

    @Test
    fun `dois dedos segurados completam apos os 5 segundos do LONG_HOLD`() {
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(10, floatArrayOf(X, X + 80), floatArrayOf(Y, Y)))
        idleMainLooper(4000)
        assertFalse(
            "não pode completar antes dos 5s",
            states.contains(GestureMatcher.STATE_GESTURE_COMPLETED),
        )
        idleMainLooper(1100)
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, states.last())
    }

    @Test
    fun `levantar os dedos antes do hold cancela`() {
        matcher.onMotionEvent(null, down(0, X, Y))
        matcher.onMotionEvent(null, pointerDown(10, floatArrayOf(X, X + 80), floatArrayOf(Y, Y)))
        matcher.onMotionEvent(null, pointerUp(100, floatArrayOf(X, X + 80), floatArrayOf(Y, Y), 1))
        val state = matcher.onMotionEvent(null, up(120, X, Y))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
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
        const val GESTURE_ID = 63 // GESTURE_2_FINGER_SINGLE_TAP_AND_HOLD
        const val X = 200f
        const val Y = 400f
    }
}

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

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_DOUBLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT
import android.os.Looper
import android.view.MotionEvent
import com.google.common.collect.ImmutableList
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO do GestureManifold + GestureMatcherFactory:
 *  - a fábrica instancia os matchers pelo NOME do enum na lista de suporte
 *    (nomes desconhecidos são ignorados);
 *  - o manifold despacha onGestureCompleted UMA vez e CANCELA os demais
 *    matchers SEM notificar (sem cascata de onGestureCancelled);
 *  - onMotionEvent devolve true no evento que completa um gesto;
 *  - gestos multi-dedo só participam depois de setMultiFingerGesturesEnabled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GestureManifoldCharacterizationTest {
    private val started = mutableListOf<Int>()
    private val completed = mutableListOf<Int>()
    private val cancelled = mutableListOf<Int>()

    private val manifoldListener =
        object : GestureManifold.Listener {
            override fun onGestureStarted(gestureId: Int) {
                started += gestureId
            }

            override fun onGestureCompleted(gestureEvent: AccessibilityGestureEvent) {
                completed += gestureEvent.gestureId
            }

            override fun onGestureCancelled(gestureId: Int) {
                cancelled += gestureId
            }
        }

    private lateinit var manifold: GestureManifold

    @Before
    fun setUp() {
        manifold =
            GestureManifold(
                RuntimeEnvironment.getApplication(),
                manifoldListener,
                object : GestureManifold.GestureConfigProvider {},
                {},
                DISPLAY_ID,
                ImmutableList.of(
                    "MAPPER_GESTURE_DOUBLE_TAP",
                    "MAPPER_GESTURE_SWIPE_RIGHT",
                    "MAPPER_GESTURE_2_FINGER_SINGLE_TAP",
                    "NOME_QUE_NAO_EXISTE",
                ),
            )
    }

    @Test
    fun `duplo-toque completa via manifold e o evento final devolve true`() {
        manifold.onMotionEvent(null, down(0))
        manifold.onMotionEvent(null, up(50))
        manifold.onMotionEvent(null, down(200))
        val handled = manifold.onMotionEvent(null, up(250))
        assertTrue("o evento que completa deve ser consumido", handled)
        assertEquals(listOf(GESTURE_DOUBLE_TAP), completed)
    }

    @Test
    fun `ao completar, os outros matchers sao cancelados SEM notificar`() {
        manifold.onMotionEvent(null, down(0))
        manifold.onMotionEvent(null, up(50))
        manifold.onMotionEvent(null, down(200))
        manifold.onMotionEvent(null, up(250))
        assertFalse(
            "cancelamento em cascata não deve notificar",
            cancelled.contains(GESTURE_SWIPE_RIGHT),
        )
    }

    @Test
    fun `swipe direita completa via manifold`() {
        manifold.onMotionEvent(null, down(0))
        manifold.onMotionEvent(null, move(40, X + 40, Y))
        manifold.onMotionEvent(null, move(80, X + 80, Y))
        manifold.onMotionEvent(null, up(100, X + 90, Y))
        assertEquals(listOf(GESTURE_SWIPE_RIGHT), completed)
    }

    @Test
    fun `gesto multi-dedo so participa depois de habilitado`() {
        // Desabilitado (default): toque de 2 dedos não completa nada.
        twoFingerTap(0)
        idle(400)
        assertEquals(emptyList<Int>(), completed)

        manifold.clear()
        cancelled.clear()
        manifold.setMultiFingerGesturesEnabled(true)
        twoFingerTap(1000)
        idle(400)
        assertEquals(listOf(GESTURE_2_FINGER_SINGLE_TAP), completed)
    }

    private fun twoFingerTap(t: Long) {
        manifold.onMotionEvent(null, down(t))
        manifold.onMotionEvent(
            null,
            multi(t + 10, MotionEvent.ACTION_POINTER_DOWN, 1, floatArrayOf(X, X + 100), floatArrayOf(Y, Y)),
        )
        manifold.onMotionEvent(
            null,
            multi(t + 40, MotionEvent.ACTION_POINTER_UP, 1, floatArrayOf(X, X + 100), floatArrayOf(Y, Y)),
        )
        manifold.onMotionEvent(null, up(t + 60))
    }

    private fun idle(millis: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
    }

    private fun down(t: Long) = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, X, Y, 0)

    private fun up(t: Long, x: Float = X, y: Float = Y) =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_UP, x, y, 0)

    private fun move(t: Long, x: Float, y: Float) =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_MOVE, x, y, 0)

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
        const val DISPLAY_ID = 0
        const val X = 200f
        const val Y = 400f
    }
}

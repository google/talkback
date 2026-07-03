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

import android.graphics.PointF
import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO dos utilitários de gesto: distâncias, janelas de
 * tempo (borda INCLUSIVA no timeout, EXCLUSIVA na distância) e a decisão de
 * drag por cosseno do ângulo entre os deslocamentos dos dois dedos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GestureUtilsCharacterizationTest {
    @Test
    fun `constantes de conversao metrica`() {
        assertEquals(10, GestureUtils.MM_PER_CM)
        assertEquals(2.54f, GestureUtils.CM_PER_INCH)
    }

    @Test
    fun `isTimedOut e verdadeiro quando deltaTime atinge o timeout (inclusivo)`() {
        assertTrue(GestureUtils.isTimedOut(up(0), up(250), 250))
        assertFalse(GestureUtils.isTimedOut(up(0), up(249), 250))
    }

    @Test
    fun `isMultiTap exige dentro do tempo E dentro da distancia (exclusivos)`() {
        // Dentro do tempo e da distância → multi-tap.
        assertTrue(GestureUtils.isMultiTap(up(0, 100f, 100f), up(200, 110f, 100f), 250, 20))
        // Na borda da distância (>=) já NÃO é.
        assertFalse(GestureUtils.isMultiTap(up(0, 100f, 100f), up(200, 120f, 100f), 250, 20))
        // Fora do tempo não é.
        assertFalse(GestureUtils.isMultiTap(up(0, 100f, 100f), up(250, 100f, 100f), 250, 20))
        // Nulos não são.
        assertFalse(GestureUtils.isMultiTap(null, up(0, 100f, 100f), 250, 20))
        assertFalse(GestureUtils.isMultiTap(up(0, 100f, 100f), null, 250, 20))
    }

    @Test
    fun `distance e dist medem euclidiana`() {
        assertEquals(5.0, GestureUtils.distance(up(0, 0f, 0f), up(10, 3f, 4f)), 1e-6)
        assertEquals(5f, GestureUtils.dist(0f, 0f, 3f, 4f))
    }

    @Test
    fun `distanceClosestPointerToPoint usa o ponteiro mais proximo`() {
        val move = multiMove(longArrayOf(0), floatArrayOf(0f, 30f), floatArrayOf(0f, 40f))
        // Ponto (3,4): dedo 1 está a 5; dedo 2 a ~45 → vale 5.
        assertEquals(5.0, GestureUtils.distanceClosestPointerToPoint(PointF(3f, 4f), move), 1e-4)
    }

    @Test
    fun `dedos paralelos sao drag - pinca nao e - dedo parado conta como drag`() {
        val cos45 = 0.525321989f
        // Paralelos (ambos para baixo) → drag.
        assertTrue(GestureUtils.isDraggingGesture(0f, 0f, 50f, 0f, 0f, 100f, 50f, 100f, cos45))
        // Direções opostas (pinça) → não é drag.
        assertFalse(GestureUtils.isDraggingGesture(0f, 0f, 50f, 0f, -100f, 0f, 150f, 0f, cos45))
        // Um dedo parado → drag (vetor nulo conta como paralelo).
        assertTrue(GestureUtils.isDraggingGesture(0f, 0f, 50f, 0f, 0f, 0f, 50f, 100f, cos45))
        // Perpendiculares (cos 0 < 0,5253) → não é drag.
        assertFalse(GestureUtils.isDraggingGesture(0f, 0f, 50f, 0f, 100f, 0f, 50f, 100f, cos45))
    }

    @Test
    fun `getActionIndex extrai o indice do ponteiro da acao`() {
        val event = multiMove(longArrayOf(0), floatArrayOf(0f, 10f), floatArrayOf(0f, 10f))
        val withIndex1 =
            MotionEvent.obtain(event).apply {
                action =
                    MotionEvent.ACTION_POINTER_UP or
                        (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
            }
        assertEquals(0, GestureUtils.getActionIndex(event))
        assertEquals(1, GestureUtils.getActionIndex(withIndex1))
    }

    private fun up(t: Long, x: Float = 0f, y: Float = 0f): MotionEvent =
        MotionEvent.obtain(0, t, MotionEvent.ACTION_UP, x, y, 0)

    private fun multiMove(times: LongArray, xs: FloatArray, ys: FloatArray): MotionEvent {
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
        return MotionEvent.obtain(
            0, times[0], MotionEvent.ACTION_MOVE, count, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0,
        )
    }
}

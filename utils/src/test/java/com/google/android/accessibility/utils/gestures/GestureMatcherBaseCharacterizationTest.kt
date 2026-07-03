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

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * CARACTERIZAÇÃO da classe-base GestureMatcher:
 *  - após CANCELED ou COMPLETED, novos eventos são IGNORADOS (retornam o
 *    estado sem chamar handlers nem notificar de novo);
 *  - transições ADIADAS (cancelAfterX e completeAfterX) disparam pelo Handler
 *    e notificam o listener;
 *  - clear() remove transições pendentes e volta a CLEAR;
 *  - tipo de ação desconhecido (ex.: HOVER_MOVE) CANCELA por evento inválido.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GestureMatcherBaseCharacterizationTest {
    private val states = mutableListOf<Int>()
    private val listener = GestureMatcher.StateChangeListener { _, state, _ -> states += state }

    /** Matcher de laboratório: o comportamento do onDown é injetável. */
    private class TestMatcher(
        listener: StateChangeListener,
        private val onDownAction: TestMatcher.(MotionEvent) -> Unit,
    ) : GestureMatcher(
            GESTURE_ID,
            Handler(RuntimeEnvironment.getApplication().mainLooper),
            listener,
            GestureMatcher.AnalyticsEventLogger {},
        ) {
        var downs = 0

        override fun getGestureName(): String = "Test"

        override fun onDown(eventId: com.google.android.accessibility.utils.Performance.EventId?, event: MotionEvent) {
            downs++
            onDownAction(event)
        }

        fun cancelNow(event: MotionEvent) = cancelGesture(event)

        fun completeNow(event: MotionEvent) = completeGesture(null, event)

        fun cancelLater(event: MotionEvent) = cancelAfterDoubleTapTimeout(event)

        private companion object {
            const val GESTURE_ID = 99
        }
    }

    @Test
    fun `eventos apos CANCELED sao ignorados sem re-notificar`() {
        val matcher = TestMatcher(listener) { cancelNow(it) }
        matcher.onMotionEvent(null, down(0))
        val notifications = states.size
        val state = matcher.onMotionEvent(null, down(100))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
        assertEquals("não deve tratar nem notificar de novo", 1, matcher.downs)
        assertEquals(notifications, states.size)
    }

    @Test
    fun `eventos apos COMPLETED sao ignorados`() {
        val matcher = TestMatcher(listener) { completeNow(it) }
        matcher.onMotionEvent(null, down(0))
        val state = matcher.onMotionEvent(null, down(100))
        assertEquals(GestureMatcher.STATE_GESTURE_COMPLETED, state)
        assertEquals(1, matcher.downs)
    }

    @Test
    fun `transicao adiada dispara pelo handler e notifica`() {
        val matcher = TestMatcher(listener) { cancelLater(it) }
        matcher.onMotionEvent(null, down(0))
        assertEquals(GestureMatcher.STATE_CLEAR, matcher.state)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, matcher.state)
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, states.last())
    }

    @Test
    fun `clear remove a transicao pendente`() {
        val matcher = TestMatcher(listener) { cancelLater(it) }
        matcher.onMotionEvent(null, down(0))
        matcher.clear()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
        assertEquals(GestureMatcher.STATE_CLEAR, matcher.state)
        assertFalse(states.contains(GestureMatcher.STATE_GESTURE_CANCELED))
    }

    @Test
    fun `tipo de acao desconhecido cancela por evento invalido`() {
        val matcher = TestMatcher(listener) {}
        val hover = MotionEvent.obtain(0, 0, MotionEvent.ACTION_HOVER_MOVE, X, Y, 0)
        val state = matcher.onMotionEvent(null, hover)
        assertEquals(GestureMatcher.STATE_GESTURE_CANCELED, state)
    }

    private fun down(t: Long) = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, X, Y, 0)

    private companion object {
        const val X = 200f
        const val Y = 400f
    }
}

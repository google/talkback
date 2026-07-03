/*
 * Copyright (C) 2024 Google Open Source Project
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

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * CARACTERIZAÇÃO: valores dos eventos/extras de analytics — são
 * persistidos/reportados, então os números são contrato.
 */
class GestureAnalyticsEventCharacterizationTest {
    @Test
    fun `ids dos eventos sao estaveis`() {
        assertEquals(0, GestureAnalyticsEvent.EVENT_DOUBLE_TAP_SLOP_OVER_RANGE)
        assertEquals(1, GestureAnalyticsEvent.EVENT_TAP_TO_TOUCH_EXPLORE)
    }

    @Test
    fun `extras de slop excedido sao estaveis`() {
        assertEquals(0, GestureAnalyticsEvent.EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_10_PERCENT)
        assertEquals(1, GestureAnalyticsEvent.EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_20_PERCENT)
        assertEquals(2, GestureAnalyticsEvent.EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_50_PERCENT)
        assertEquals(3, GestureAnalyticsEvent.EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_100_PERCENT)
        assertEquals(4, GestureAnalyticsEvent.EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_MORE_THAN_100_PERCENT)
        assertEquals(0, GestureAnalyticsEvent.EXTRA_DEBUG_TAP_TO_TOUCH_EXPLORE_TOTAL_SAVED_TIME)
        assertEquals(1, GestureAnalyticsEvent.EXTRA_DEBUG_TAP_TO_TOUCH_EXPLORE_HIT_COUNT)
    }

    @Test
    fun `construtor guarda evento e id do gesto`() {
        val e = GestureAnalyticsEvent(GestureAnalyticsEvent.EVENT_TAP_TO_TOUCH_EXPLORE, 17)
        assertEquals(GestureAnalyticsEvent.EVENT_TAP_TO_TOUCH_EXPLORE, e.event)
        assertEquals(17, e.gestureId)
    }
}

/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.view.ViewConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** CARACTERIZAÇÃO: o timeout de multi-tap é getDoubleTapTimeout() − 50 ms (IPC). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GestureConfigurationCharacterizationTest {
    @Test
    fun `janela de multi-tap e o doubleTapTimeout do sistema menos 50ms`() {
        assertEquals(
            ViewConfiguration.getDoubleTapTimeout() - 50,
            GestureConfiguration.DOUBLE_TAP_TIMEOUT_MS,
        )
    }
}

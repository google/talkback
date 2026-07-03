/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.content.Context
import android.os.Build
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import com.google.android.accessibility.utils.Performance.EventId

/**
 * This class matches second-finger multi-tap-and-hold gestures. A second-finger multi-tap gesture
 * is where one finger is held down and a second finger executes the taps then hold. The number of
 * taps for each instance is specified in the constructor.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class SecondFingerTapAndHold(
  context: Context,
  taps: Int,
  gesture: Int,
  listener: GestureMatcher.StateChangeListener,
  logger: GestureMatcher.AnalyticsEventLogger,
) : SecondFingerTap(context, taps, gesture, listener, logger) {

  override fun onPointerDown(eventId: EventId?, event: MotionEvent) {
    super.onPointerDown(eventId, event)
    if (state != STATE_GESTURE_CANCELED) {
      // We should check the detector state in advance because it may enter Cancel state in base
      // class (MultiTap).
      completeAfterLongPressTimeout(eventId, event)
    }
  }

  override fun onPointerUp(eventId: EventId?, event: MotionEvent) {
    if (pendingRestart) {
      pendingRestart = false
      return
    }
    if (state == STATE_GESTURE_STARTED || state == STATE_CLEAR) {
      currentTaps++
      if (currentTaps == targetTaps) {
        cancelGesture(event)
        return
      }
      // Needs more taps.
    } else {
      // Either too many taps or nonsensical event stream.
      cancelGesture(event)
      return
    }
    cancelAfterDoubleTapTimeout(event)
  }

  override fun onUp(eventId: EventId?, event: MotionEvent) {
    cancelGesture(event)
  }

  override fun getGestureName(): String = "Second Finger Tap and Hold"
}

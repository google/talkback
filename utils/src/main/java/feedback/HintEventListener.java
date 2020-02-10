/*
 * Copyright (C) 2017 Google Inc.
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
 */

package com.google.android.accessibility.utils.feedback;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.output.FeedbackItem;

/** Posts an accessibility hint for an {@link AccessibilityNodeInfoCompat}. */
public interface HintEventListener {
  /**
   * Post an accessibility hint for an {@link AccessibilityNodeInfoCompat}.
   *
   * @param eventType Type of the accessibility event for which the hint will be spoken.
   * @param compat The {@link AccessibilityNodeInfoCompat} based on which the accessibility hint
   *     will be generated.
   * @param hintForcedFeedbackAudioPlaybackActive Whether the hint is a forced feedback when audio
   *     playback is active. If the hint source is an accessibility focus results from unknown
   *     source, it is set to false. Otherwise it is set to true.
   * @see FeedbackItem#FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
   * @param hintForcedFeedbackMicrophoneActive Whether the hint is a forced feedback when microphone
   *     is active. If the hint source is an accessibility focus results from unknown source, it is
   *     set to false. Otherwise it is set to true.
   * @see FeedbackItem#FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
   */
  void onFocusHint(
      int eventType,
      AccessibilityNodeInfoCompat compat,
      boolean hintForcedFeedbackAudioPlaybackActive,
      boolean hintForcedFeedbackMicrophoneActive);

  /**
   * Handle a delayed window-change hint from AccessibilityHintsManager, passing hint text to
   * ProcessorAccessibilityHints and then to Compositor.
   */
  void onScreenHint(CharSequence text);
}

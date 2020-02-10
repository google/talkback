/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.switchaccess.feedback;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.EventFilter;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.feedback.AccessibilityHintsManager;
import com.google.android.accessibility.utils.feedback.ScrollFeedbackManager;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Controller used to provide spoken feedback when an {@link AccessibilityEvent} happened. */
public class SwitchAccessAccessibilityEventFeedbackController {

  private final EventFilter eventFilter;
  private final ScrollFeedbackManager scrollFeedbackManager;
  private final SwitchAccessScreenFeedbackManager switchAccessScreenFeedbackManager;

  public SwitchAccessAccessibilityEventFeedbackController(
      AccessibilityService service,
      Compositor compositor,
      GlobalVariables globalVariables,
      SpeechControllerImpl speechController,
      FeedbackController feedbackController,
      AccessibilityHintsManager hintsManager) {
    eventFilter =
        new EventFilter(
            compositor, service, null, null, new InputModeManager(), null, globalVariables);
    scrollFeedbackManager = new ScrollFeedbackManager(speechController, service);
    switchAccessScreenFeedbackManager =
        new SwitchAccessScreenFeedbackManager(
            service, hintsManager, speechController, feedbackController);
  }

  void processAccessibilityEvent(AccessibilityEvent event) {
    eventFilter.sendEvent(event, null /* eventId */);
  }

  void onWindowChangedAndIsNowStable(AccessibilityEvent event, @Nullable EventId eventId) {
    switchAccessScreenFeedbackManager.onAccessibilityEvent(event, eventId);
    // Provide scroll feedback after the window state stabilized and the title of the window was
    // announced.
    scrollFeedbackManager.onAccessibilityEvent(event, eventId);
  }
}

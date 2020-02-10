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
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.WindowEventInterpreter;
import com.google.android.accessibility.utils.feedback.AccessibilityHintsManager;
import com.google.android.accessibility.utils.feedback.ScreenFeedbackManager;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Customizes {@link ScreenFeedbackManager} for SwitchAccess. Further interprets window events to
 * determine type of SwitchAccess menu windows. Provides extra feedback for SwitchAccess menu
 * window.
 */
public class SwitchAccessScreenFeedbackManager extends ScreenFeedbackManager {

  SwitchAccessScreenFeedbackManager(
      AccessibilityService service,
      AccessibilityHintsManager accessibilityHintsManager,
      SpeechControllerImpl speechController,
      FeedbackController feedbackController) {
    super(service, accessibilityHintsManager, speechController, feedbackController, false);
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, @Nullable EventId eventId) {
    // Check whether the window event is triggered by the Switch Access Menu button overlay showing
    // up. If so, ignore this event. This is because when the Switch Access Menu button and the
    // underlying overlay show up on the screen, the button is highlighted at the same time. If we
    // do not ignore this event, "Switch Access Menu" will be announced twice. First for the Switch
    // Access Menu button overlay showing up, then for the highlighted Switch Access Menu button.
    //
    // We use two criteria to check if the window is the Switch Access Menu button overlay:
    // 1. The package of the event is the same as the Switch Access Accessibility Service.
    // 2. The text of the event is the same as the text of the menu button.
    CharSequence packageName = event.getPackageName();
    if (service.getPackageName().equals(packageName)) {
      CharSequence text = StringBuilderUtils.getAggregateText(event.getText());
      if (text != null
          && text.toString()
              .equals(service.getResources().getString(R.string.option_scanning_menu_button))) {
        return;
      }
    }

    interpreter.interpret(event, eventId);
  }

  @Override
  protected boolean customHandle(
      WindowEventInterpreter.EventInterpretation interpretation, @Nullable EventId eventId) {
    if (interpretation == null) {
      return false;
    }

    // Only speak if windows are stable.
    return interpretation.areWindowsStable();
  }
}

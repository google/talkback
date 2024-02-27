/*
 * Copyright 2021 Google Inc.
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

package com.google.android.accessibility.talkback;

import android.app.Activity;
import android.view.KeyEvent;

/** The requester of the survey for user feedback. */
public class HatsSurveyRequester {

  /** Callback to notify the client that a survey is available. */
  public interface OnSurveyAvailableListener {
    void onSurveyAvailable();
  }

  public HatsSurveyRequester(Activity activity) {
    // Do nothing.
  }

  /** Requests Hats survey. */
  public void requestSurvey() {
    // Do nothing.
  }

  /**
   * Presents the cached survey if available.
   *
   * @return True if survey is available, otherwise false
   */
  public boolean presentCachedSurvey() {
    return false;
  }

  public boolean isSurveyAvailable() {
    return false;
  }

  public void setOnSurveyAvailableListener(OnSurveyAvailableListener listener) {
    // Do nothing.
  }

  /** Dismisses Hats survey. */
  public void dismissSurvey() {
    // Do nothing.
  }

  /**
   * Callback for back key being released ({@link KeyEvent#ACTION_UP}).
   *
   * @return {@code true} if the back key has been handled (stops propagation)
   */
  public boolean handleBackKeyPress() {
    return false; // Do nothing.
  }
}

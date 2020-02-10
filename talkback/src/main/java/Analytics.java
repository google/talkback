/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import androidx.annotation.IntDef;
import com.google.android.accessibility.utils.input.CursorGranularity;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Analytics that tracks talkback usage */
public abstract class Analytics implements OnSharedPreferenceChangeListener {
  public static final int TYPE_UNKNOWN = 0;
  public static final int TYPE_PREFERENCE_SETTING = 1;
  public static final int TYPE_GESTURE = 2;
  public static final int TYPE_CONTEXT_MENU = 3;
  public static final int TYPE_SELECTOR = 4;

  /** Defines types of user actions to change granularity, setting. */
  @IntDef({TYPE_UNKNOWN, TYPE_PREFERENCE_SETTING, TYPE_GESTURE, TYPE_CONTEXT_MENU, TYPE_SELECTOR})
  @Retention(RetentionPolicy.SOURCE)
  public @interface UserActionType {}

  /** The {@link com.google.android.accessibility.talkback.TalkBackService} instance. */
  protected TalkBackService service;

  public Analytics(TalkBackService service) {
    this.service = service;
  }

  /** To be called when TalkBack service has started. */
  public abstract void onTalkBackServiceStarted();

  /** To be called when TalkBack service has stopped. */
  public abstract void onTalkBackServiceStopped();

  /**
   * To be called when TalkBack processes a gesture.
   *
   * @param gestureId The ID of the processed gesture.
   */
  public abstract void onGesture(int gestureId);

  /** To be called when the user edits text. */
  public abstract void onTextEdited();

  /**
   * Called when the user changes granularity.
   *
   * <p><strong>Note:</strong> When changing granularity linearly with gesture or selector, we're
   * notified with intermediate changes. (e.g. When changing granularity from default to line by
   * gestures, the change is default->character->word->Sentence->line). For these changes, set the
   * flag {@code isPending} to {@code true} to avoid logging it immediately.
   *
   * @param newGranularity The new granularity value.
   * @param userActionType The source action of the change.
   * @param isPending Whether it is a pending change. Pending change will not be logged until {@link
   *     #logPendingChanges()} is called.
   */
  public abstract void onGranularityChanged(
      CursorGranularity newGranularity, @UserActionType int userActionType, boolean isPending);

  /**
   * Logs the last pending granularity change action, and the last pending preference change action.
   */
  public abstract void logPendingChanges();

  /** Clears the pending granularity change. */
  public abstract void clearPendingGranularityChange();

  /**
   * Called when a setting is changed from non-preference-activity user action.
   *
   * <p><strong>Note:</strong> When changing verbosity with selector, we're notified with
   * intermediate changes. (e.g. When changing verbosity from low to high, the change is
   * low->custom->high). For these changes, set the flag {@code isPending} to {@code true} to avoid
   * logging it immediately.
   *
   * @param prefKey The key of the preference that was changed.
   * @param userActionType Source user action leads to the change.
   * @param isPending Whether it is a pending change. Pending change will not be logged until {@link
   *     #logPendingChanges()} is called.
   */
  public abstract void onManuallyChangeSetting(
      String prefKey, @UserActionType int userActionType, boolean isPending);

  // Used for debug only
  public static String userActionTypeToString(@UserActionType int userActionType) {
    switch (userActionType) {
      case TYPE_PREFERENCE_SETTING:
        return "TYPE_PREFERENCE_SETTING";
      case TYPE_GESTURE:
        return "TYPE_GESTURE";
      case TYPE_CONTEXT_MENU:
        return "TYPE_CONTEXT_MENU";
      case TYPE_SELECTOR:
        return "TYPE_SELECTOR";
      default:
        return "TYPE_UNKNOWN";
    }
  }
}

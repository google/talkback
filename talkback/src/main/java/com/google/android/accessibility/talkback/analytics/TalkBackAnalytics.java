/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.analytics;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import androidx.annotation.IntDef;
import com.google.android.accessibility.talkback.controller.SelectorController;
import com.google.android.accessibility.utils.input.CursorGranularity;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** An base class to declare constants and callback functions defined for Analytics. */
public class TalkBackAnalytics implements OnSharedPreferenceChangeListener {
  /** These constants of user action type. */
  public static final int TYPE_UNKNOWN = 0;

  public static final int TYPE_PREFERENCE_SETTING = 1;
  public static final int TYPE_GESTURE = 2;
  public static final int TYPE_CONTEXT_MENU = 3;
  public static final int TYPE_SELECTOR = 4;

  public static final int MENU_ITEM_UNKNOWN = -1;

  /** Defines types of user actions to change granularity, setting. */
  @IntDef({TYPE_UNKNOWN, TYPE_PREFERENCE_SETTING, TYPE_GESTURE, TYPE_CONTEXT_MENU, TYPE_SELECTOR})
  @Retention(RetentionPolicy.SOURCE)
  public @interface UserActionType {}

  /** These constants of local context menu type. */
  public static final int MENU_TYPE_UNKNOWN = 0;

  public static final int MENU_TYPE_GRANULARITY = 1;
  public static final int MENU_TYPE_EDIT_OPTIONS = 2;
  public static final int MENU_TYPE_VIEW_PAGER = 3;
  public static final int MENU_TYPE_LABELING = 4;
  public static final int MENU_TYPE_CUSTOM_ACTION = 5;
  public static final int MENU_TYPE_SEEK_BAR = 6;
  public static final int MENU_TYPE_SPANNABLES = 7;
  /** Defines types of local context menu. */
  @IntDef({
    MENU_TYPE_UNKNOWN,
    MENU_TYPE_GRANULARITY,
    MENU_TYPE_EDIT_OPTIONS,
    MENU_TYPE_VIEW_PAGER,
    MENU_TYPE_LABELING,
    MENU_TYPE_CUSTOM_ACTION,
    MENU_TYPE_SEEK_BAR,
    MENU_TYPE_SPANNABLES
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface LocalContextMenuType {}

  /** These constants of recognized gesture id. */
  public static final int GESTURE_UNKNOWN = 0;

  public static final int GESTURE_1FINGER_UP = 1;
  public static final int GESTURE_1FINGER_DOWN = 2;
  public static final int GESTURE_1FINGER_LEFT = 3;
  public static final int GESTURE_1FINGER_RIGHT = 4;
  public static final int GESTURE_1FINGER_LEFT_RIGHT = 5;
  public static final int GESTURE_1FINGER_RIGHT_LEFT = 6;
  public static final int GESTURE_1FINGER_UP_DOWN = 7;
  public static final int GESTURE_1FINGER_DOWN_UP = 8;
  public static final int GESTURE_1FINGER_LEFT_UP = 9;
  public static final int GESTURE_1FINGER_LEFT_DOWN = 10;
  public static final int GESTURE_1FINGER_RIGHT_UP = 11;
  public static final int GESTURE_1FINGER_RIGHT_DOWN = 12;
  public static final int GESTURE_1FINGER_UP_LEFT = 13;
  public static final int GESTURE_1FINGER_UP_RIGHT = 14;
  public static final int GESTURE_1FINGER_DOWN_LEFT = 15;
  public static final int GESTURE_1FINGER_DOWN_RIGHT = 16;
  //  public static final int GESTURE_1FINGER_2TAP = 17;
  //  public static final int GESTURE_1FINGER_2TAP_HOLD = 18;
  public static final int GESTURE_2FINGER_1TAP = 19;
  public static final int GESTURE_2FINGER_2TAP = 20;
  public static final int GESTURE_2FINGER_3TAP = 21;
  public static final int GESTURE_3FINGER_1TAP = 22;
  public static final int GESTURE_3FINGER_2TAP = 23;
  public static final int GESTURE_3FINGER_3TAP = 24;
  public static final int GESTURE_2FINGER_UP = 25;
  public static final int GESTURE_2FINGER_DOWN = 26;
  public static final int GESTURE_2FINGER_LEFT = 27;
  public static final int GESTURE_2FINGER_RIGHT = 28;
  public static final int GESTURE_3FINGER_UP = 29;
  public static final int GESTURE_3FINGER_DOWN = 30;
  public static final int GESTURE_3FINGER_LEFT = 31;
  public static final int GESTURE_3FINGER_RIGHT = 32;
  public static final int GESTURE_4FINGER_UP = 33;
  public static final int GESTURE_4FINGER_DOWN = 34;
  public static final int GESTURE_4FINGER_LEFT = 35;
  public static final int GESTURE_4FINGER_RIGHT = 36;
  public static final int GESTURE_4FINGER_1TAP = 37;
  public static final int GESTURE_4FINGER_2TAP = 38;
  public static final int GESTURE_4FINGER_3TAP = 39;
  public static final int GESTURE_2FINGER_2TAP_HOLD = 40;
  public static final int GESTURE_3FINGER_2TAP_HOLD = 41;
  public static final int GESTURE_4FINGER_2TAP_HOLD = 42;
  /** Defines the recognized gesture id. */
  @IntDef({
    GESTURE_UNKNOWN,
    GESTURE_1FINGER_UP,
    GESTURE_1FINGER_DOWN,
    GESTURE_1FINGER_LEFT,
    GESTURE_1FINGER_RIGHT,
    GESTURE_1FINGER_LEFT_RIGHT,
    GESTURE_1FINGER_RIGHT_LEFT,
    GESTURE_1FINGER_UP_DOWN,
    GESTURE_1FINGER_DOWN_UP,
    GESTURE_1FINGER_LEFT_UP,
    GESTURE_1FINGER_LEFT_DOWN,
    GESTURE_1FINGER_RIGHT_UP,
    GESTURE_1FINGER_RIGHT_DOWN,
    GESTURE_1FINGER_UP_LEFT,
    GESTURE_1FINGER_UP_RIGHT,
    GESTURE_1FINGER_DOWN_LEFT,
    GESTURE_1FINGER_DOWN_RIGHT,
    GESTURE_2FINGER_1TAP,
    GESTURE_2FINGER_2TAP,
    GESTURE_2FINGER_3TAP,
    GESTURE_3FINGER_1TAP,
    GESTURE_3FINGER_2TAP,
    GESTURE_3FINGER_3TAP,
    GESTURE_2FINGER_UP,
    GESTURE_2FINGER_DOWN,
    GESTURE_2FINGER_LEFT,
    GESTURE_2FINGER_RIGHT,
    GESTURE_3FINGER_UP,
    GESTURE_3FINGER_DOWN,
    GESTURE_3FINGER_LEFT,
    GESTURE_3FINGER_RIGHT,
    GESTURE_4FINGER_UP,
    GESTURE_4FINGER_DOWN,
    GESTURE_4FINGER_LEFT,
    GESTURE_4FINGER_RIGHT,
    GESTURE_4FINGER_1TAP,
    GESTURE_4FINGER_2TAP,
    GESTURE_4FINGER_3TAP,
    GESTURE_2FINGER_2TAP_HOLD,
    GESTURE_3FINGER_2TAP_HOLD,
    GESTURE_4FINGER_2TAP_HOLD,
  })
  @Retention(RetentionPolicy.SOURCE)
  @interface GestureId {}

  /** These constants of voice command events. */
  public static final int VOICE_COMMAND_ATTEMPT = 1;

  public static final int VOICE_COMMAND_TIMEOUT = 2;
  public static final int VOICE_COMMAND_RECOGNIZED = 3;
  public static final int VOICE_COMMAND_UNRECOGNIZED = 4;
  public static final int VOICE_COMMAND_ENGINE_ERROR = 5;

  /** Defines events of voice command. */
  @IntDef({
    VOICE_COMMAND_ATTEMPT,
    VOICE_COMMAND_TIMEOUT,
    VOICE_COMMAND_RECOGNIZED,
    VOICE_COMMAND_UNRECOGNIZED,
    VOICE_COMMAND_ENGINE_ERROR
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface VoiceCommandEventId {}

  public void onTalkBackServiceStarted() {}

  public void onTalkBackServiceStopped() {}

  public void onGesture(int gestureId) {}

  public void onTextEdited() {}

  public void onMoveWithGranularity(CursorGranularity newGranularity) {}

  public void logPendingChanges() {}

  public void onManuallyChangeSetting(
      String prefKey, @UserActionType int userActionType, boolean isPending) {}

  public void onGlobalContextMenuOpen(boolean isListStyle) {}

  public void onGlobalContextMenuAction(int menuItemId) {}

  public void onLocalContextMenuAction(@LocalContextMenuType int lcmType, int menuItemId) {}

  public void onLocalContextMenuOpen(boolean isListStyle) {}

  public void onVoiceCommandEvent(@VoiceCommandEventId int event) {}

  /** For select previous/next setting purpose in selector. */
  public void onSelectorEvent() {}

  /** For selected setting previous/next action purpose in selector. */
  public void onSelectorActionEvent(SelectorController.Setting setting) {}

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {}
}

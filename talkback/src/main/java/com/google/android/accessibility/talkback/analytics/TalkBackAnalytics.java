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
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping.TalkbackAction;
import com.google.android.accessibility.talkback.keyboard.KeyComboModel;
import com.google.android.accessibility.talkback.keyboard.TalkBackPhysicalKeyboardShortcut;
import com.google.android.accessibility.talkback.selector.SelectorController;
import com.google.android.accessibility.utils.input.CursorGranularity;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** A base class to declare constants and callback functions defined for Analytics. */
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
  public static final int GESTURE_2FINGER_3TAP_HOLD = 43;
  public static final int GESTURE_3FINGER_1TAP_HOLD = 44;
  public static final int GESTURE_3FINGER_3TAP_HOLD = 45;

  public static final int GESTURE_SPLIT_TAP = 61;
  public static final int GESTURE_LIFT_TO_TYPE = 62;

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
    GESTURE_2FINGER_3TAP_HOLD,
    GESTURE_3FINGER_1TAP_HOLD,
    GESTURE_3FINGER_3TAP_HOLD,
    GESTURE_SPLIT_TAP,
    GESTURE_LIFT_TO_TYPE,
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

  // LINT.IfChange(voice_command_type)
  // These types should be maintained with the same order as the VoiceCommandType defined in the
  // proto , and the value must be continuous.
  public static final int VOICE_COMMAND_TYPE_SELECT_ALL = 1;
  public static final int VOICE_COMMAND_TYPE_HIDE_SCREEN = 2;
  public static final int VOICE_COMMAND_TYPE_SCREEN_SEARCH = 3;
  public static final int VOICE_COMMAND_TYPE_END_SELECT = 4;
  public static final int VOICE_COMMAND_TYPE_START_SELECT = 5;
  public static final int VOICE_COMMAND_TYPE_CUSTOM_ACTION = 6;
  public static final int VOICE_COMMAND_TYPE_NEXT_HEADING = 7;
  public static final int VOICE_COMMAND_TYPE_NEXT_CONTROL = 8;
  public static final int VOICE_COMMAND_TYPE_NEXT_LINK = 9;
  public static final int VOICE_COMMAND_TYPE_NEXT_LANDMARK = 10;
  public static final int VOICE_COMMAND_TYPE_VERBOSITY = 11;
  public static final int VOICE_COMMAND_TYPE_GRANULARITY = 12;
  public static final int VOICE_COMMAND_TYPE_SHOW_SCREEN = 13;
  public static final int VOICE_COMMAND_TYPE_BACK = 14;
  public static final int VOICE_COMMAND_TYPE_SPEECH_RATE_INCREASE = 15;
  public static final int VOICE_COMMAND_TYPE_SPEECH_RATE_DECREASE = 16;
  public static final int VOICE_COMMAND_TYPE_FIND = 17;
  public static final int VOICE_COMMAND_TYPE_INSERT = 18;
  public static final int VOICE_COMMAND_TYPE_LABEL = 19;
  public static final int VOICE_COMMAND_TYPE_READ_FROM_CURSOR = 20;
  public static final int VOICE_COMMAND_TYPE_READ_FROM_TOP = 21;
  public static final int VOICE_COMMAND_TYPE_COPY_LAST_UTTERANCE = 22;
  public static final int VOICE_COMMAND_TYPE_QUICK_SETTING = 23;
  public static final int VOICE_COMMAND_TYPE_TALKBACK_SETTING = 24;
  public static final int VOICE_COMMAND_TYPE_COPY = 25;
  public static final int VOICE_COMMAND_TYPE_PASTE = 26;
  public static final int VOICE_COMMAND_TYPE_CUT = 27;
  public static final int VOICE_COMMAND_TYPE_DELETE = 28;
  public static final int VOICE_COMMAND_TYPE_FIRST = 29;
  public static final int VOICE_COMMAND_TYPE_LAST = 30;
  public static final int VOICE_COMMAND_TYPE_LANGUAGE = 31;
  public static final int VOICE_COMMAND_TYPE_NOTIFICATION = 32;
  public static final int VOICE_COMMAND_TYPE_RECENT_APPS = 33;
  public static final int VOICE_COMMAND_TYPE_ALL_APPS = 34;
  public static final int VOICE_COMMAND_TYPE_HOME = 35;
  public static final int VOICE_COMMAND_TYPE_QUIT = 36;
  public static final int VOICE_COMMAND_TYPE_ASSISTANT = 37;
  public static final int VOICE_COMMAND_TYPE_HELP = 38;
  public static final int VOICE_COMMAND_TYPE_GEMINI = 39;

  /** Defines types of voice command. */
  @IntDef({
    VOICE_COMMAND_TYPE_SELECT_ALL,
    VOICE_COMMAND_TYPE_HIDE_SCREEN,
    VOICE_COMMAND_TYPE_SCREEN_SEARCH,
    VOICE_COMMAND_TYPE_END_SELECT,
    VOICE_COMMAND_TYPE_START_SELECT,
    VOICE_COMMAND_TYPE_CUSTOM_ACTION,
    VOICE_COMMAND_TYPE_NEXT_HEADING,
    VOICE_COMMAND_TYPE_NEXT_CONTROL,
    VOICE_COMMAND_TYPE_NEXT_LINK,
    VOICE_COMMAND_TYPE_NEXT_LANDMARK,
    VOICE_COMMAND_TYPE_VERBOSITY,
    VOICE_COMMAND_TYPE_GRANULARITY,
    VOICE_COMMAND_TYPE_SHOW_SCREEN,
    VOICE_COMMAND_TYPE_BACK,
    VOICE_COMMAND_TYPE_SPEECH_RATE_INCREASE,
    VOICE_COMMAND_TYPE_SPEECH_RATE_DECREASE,
    VOICE_COMMAND_TYPE_FIND,
    VOICE_COMMAND_TYPE_INSERT,
    VOICE_COMMAND_TYPE_LABEL,
    VOICE_COMMAND_TYPE_READ_FROM_CURSOR,
    VOICE_COMMAND_TYPE_READ_FROM_TOP,
    VOICE_COMMAND_TYPE_COPY_LAST_UTTERANCE,
    VOICE_COMMAND_TYPE_QUICK_SETTING,
    VOICE_COMMAND_TYPE_TALKBACK_SETTING,
    VOICE_COMMAND_TYPE_COPY,
    VOICE_COMMAND_TYPE_PASTE,
    VOICE_COMMAND_TYPE_CUT,
    VOICE_COMMAND_TYPE_DELETE,
    VOICE_COMMAND_TYPE_FIRST,
    VOICE_COMMAND_TYPE_LAST,
    VOICE_COMMAND_TYPE_LANGUAGE,
    VOICE_COMMAND_TYPE_NOTIFICATION,
    VOICE_COMMAND_TYPE_RECENT_APPS,
    VOICE_COMMAND_TYPE_ALL_APPS,
    VOICE_COMMAND_TYPE_HOME,
    VOICE_COMMAND_TYPE_QUIT,
    VOICE_COMMAND_TYPE_ASSISTANT,
    VOICE_COMMAND_TYPE_HELP,
    VOICE_COMMAND_TYPE_GEMINI
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface VoiceCommandTypeId {}

  // LINT.ThenChange(//depot/google3/java/com/google/android/accessibility/talkback/overlay/google/analytics/proto/voice_command_enums.proto:voice_command_type)

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

  public void onVoiceCommandType(@VoiceCommandTypeId int type) {}

  /** For select previous/next setting purpose in selector. */
  public void onSelectorEvent() {}

  /** For selected setting previous/next action purpose in selector. */
  public void onSelectorActionEvent(SelectorController.Setting setting) {}

  public void onMagnificationUsed(int mode) {}

  public void onKeyboardShortcutUsed(
      TalkBackPhysicalKeyboardShortcut keyboardShortcut, int triggerModifier, long keyComboCode) {}

  public void onKeymapTypeUsed(KeyComboModel keyComboModel) {}

  public void onModifierKeyUsed(int modifierKey) {}

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {}

  public void sendLogImmediately(int recoveryType) {}

  public static final int IMAGE_CAPTION_EVENT_CAPTION_REQUEST = 1;
  public static final int IMAGE_CAPTION_EVENT_CAPTION_REQUEST_MANUAL = 2;
  public static final int IMAGE_CAPTION_EVENT_SCREENSHOT_FAILED = 3;
  public static final int IMAGE_CAPTION_EVENT_IMAGE_CAPTION_CACHE_HIT = 4;
  public static final int IMAGE_CAPTION_EVENT_ICON_DETECT_PERFORM = 5;
  public static final int IMAGE_CAPTION_EVENT_ICON_DETECT_SUCCEED = 6;
  public static final int IMAGE_CAPTION_EVENT_ICON_DETECT_NO_RESULT = 7;
  public static final int IMAGE_CAPTION_EVENT_ICON_DETECT_FAIL = 8;
  public static final int IMAGE_CAPTION_EVENT_OCR_PERFORM = 9;
  public static final int IMAGE_CAPTION_EVENT_OCR_PERFORM_SUCCEED = 10;
  public static final int IMAGE_CAPTION_EVENT_OCR_PERFORM_SUCCEED_EMPTY = 11;
  public static final int IMAGE_CAPTION_EVENT_OCR_PERFORM_FAIL = 12;
  public static final int IMAGE_CAPTION_EVENT_ICON_DETECT_ABORT = 13;
  public static final int IMAGE_CAPTION_EVENT_OCR_ABORT = 14;
  public static final int IMAGE_CAPTION_EVENT_INSTALL_LIB_REQUEST = 15;
  public static final int IMAGE_CAPTION_EVENT_INSTALL_LIB_DENY = 16;
  public static final int IMAGE_CAPTION_EVENT_INSTALL_LIB_SUCCESS = 17;
  public static final int IMAGE_CAPTION_EVENT_INSTALL_LIB_FAIL = 18;
  public static final int IMAGE_CAPTION_EVENT_UNINSTALL_LIB_REQUEST = 19;
  public static final int IMAGE_CAPTION_EVENT_UNINSTALL_LIB_DENY = 20;
  public static final int IMAGE_CAPTION_EVENT_IMAGE_DESCRIBE_PERFORM = 21;
  public static final int IMAGE_CAPTION_EVENT_IMAGE_DESCRIBE_SUCCEED = 22;
  public static final int IMAGE_CAPTION_EVENT_IMAGE_DESCRIBE_NO_RESULT = 23;
  public static final int IMAGE_CAPTION_EVENT_IMAGE_DESCRIBE_FAIL = 24;
  public static final int IMAGE_CAPTION_EVENT_IMAGE_DESCRIBE_ABORT = 25;
  public static final int IMAGE_DESCRIBE_EVENT_INSTALL_LIB_REQUEST = 26;
  public static final int IMAGE_DESCRIBE_EVENT_INSTALL_LIB_DENY = 27;
  public static final int IMAGE_DESCRIBE_EVENT_INSTALL_LIB_SUCCESS = 28;
  public static final int IMAGE_DESCRIBE_EVENT_INSTALL_LIB_FAIL = 29;
  public static final int IMAGE_DESCRIBE_EVENT_UNINSTALL_LIB_REQUEST = 30;
  public static final int IMAGE_DESCRIBE_EVENT_UNINSTALL_LIB_DENY = 31;
  public static final int IMAGE_DESCRIBE_EVENT_QUALITY_LEVEL_HIGH = 32;
  public static final int IMAGE_DESCRIBE_EVENT_QUALITY_LEVEL_MIDDLE = 33;
  public static final int IMAGE_DESCRIBE_EVENT_QUALITY_LEVEL_LOW = 34;
  public static final int IMAGE_CAPTION_EVENT_CANNOT_PERFORM_WHEN_SCREEN_HIDDEN = 35;
  public static final int IMAGE_CAPTION_EVENT_SCHEDULE_SCREENSHOT_CAPTURE_FAILURE = 36;

  /** Defines events of image description. */
  @IntDef({
    IMAGE_CAPTION_EVENT_CAPTION_REQUEST,
    IMAGE_CAPTION_EVENT_CAPTION_REQUEST_MANUAL,
    IMAGE_CAPTION_EVENT_SCREENSHOT_FAILED,
    IMAGE_CAPTION_EVENT_IMAGE_CAPTION_CACHE_HIT,
    IMAGE_CAPTION_EVENT_ICON_DETECT_PERFORM,
    IMAGE_CAPTION_EVENT_ICON_DETECT_SUCCEED,
    IMAGE_CAPTION_EVENT_ICON_DETECT_NO_RESULT,
    IMAGE_CAPTION_EVENT_ICON_DETECT_FAIL,
    IMAGE_CAPTION_EVENT_OCR_PERFORM,
    IMAGE_CAPTION_EVENT_OCR_PERFORM_SUCCEED,
    IMAGE_CAPTION_EVENT_OCR_PERFORM_SUCCEED_EMPTY,
    IMAGE_CAPTION_EVENT_OCR_PERFORM_FAIL,
    IMAGE_CAPTION_EVENT_ICON_DETECT_ABORT,
    IMAGE_CAPTION_EVENT_OCR_ABORT,
    IMAGE_CAPTION_EVENT_INSTALL_LIB_REQUEST,
    IMAGE_CAPTION_EVENT_INSTALL_LIB_DENY,
    IMAGE_CAPTION_EVENT_INSTALL_LIB_SUCCESS,
    IMAGE_CAPTION_EVENT_INSTALL_LIB_FAIL,
    IMAGE_CAPTION_EVENT_UNINSTALL_LIB_REQUEST,
    IMAGE_CAPTION_EVENT_UNINSTALL_LIB_DENY,
    IMAGE_CAPTION_EVENT_IMAGE_DESCRIBE_PERFORM,
    IMAGE_CAPTION_EVENT_IMAGE_DESCRIBE_SUCCEED,
    IMAGE_CAPTION_EVENT_IMAGE_DESCRIBE_NO_RESULT,
    IMAGE_CAPTION_EVENT_IMAGE_DESCRIBE_FAIL,
    IMAGE_CAPTION_EVENT_IMAGE_DESCRIBE_ABORT,
    IMAGE_CAPTION_EVENT_CANNOT_PERFORM_WHEN_SCREEN_HIDDEN,
    IMAGE_CAPTION_EVENT_SCHEDULE_SCREENSHOT_CAPTURE_FAILURE,
    IMAGE_DESCRIBE_EVENT_INSTALL_LIB_REQUEST,
    IMAGE_DESCRIBE_EVENT_INSTALL_LIB_DENY,
    IMAGE_DESCRIBE_EVENT_INSTALL_LIB_SUCCESS,
    IMAGE_DESCRIBE_EVENT_INSTALL_LIB_FAIL,
    IMAGE_DESCRIBE_EVENT_UNINSTALL_LIB_REQUEST,
    IMAGE_DESCRIBE_EVENT_UNINSTALL_LIB_DENY,
    IMAGE_DESCRIBE_EVENT_QUALITY_LEVEL_HIGH,
    IMAGE_DESCRIBE_EVENT_QUALITY_LEVEL_MIDDLE,
    IMAGE_DESCRIBE_EVENT_QUALITY_LEVEL_LOW
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface ImageCaptionEventId {}

  public void onImageCaptionEvent(@ImageCaptionEventId int event) {}

  /** Defines statistics entries for multiple downloaders (from TalkBack menu) */
  public enum ImageCaptionLogKeys {
    ICON_DETECTION(
        IMAGE_CAPTION_EVENT_INSTALL_LIB_SUCCESS,
        IMAGE_CAPTION_EVENT_INSTALL_LIB_FAIL,
        IMAGE_CAPTION_EVENT_INSTALL_LIB_REQUEST,
        IMAGE_CAPTION_EVENT_INSTALL_LIB_DENY,
        IMAGE_CAPTION_EVENT_UNINSTALL_LIB_REQUEST,
        IMAGE_CAPTION_EVENT_UNINSTALL_LIB_DENY),
    IMAGE_DESCRIPTION(
        IMAGE_DESCRIBE_EVENT_INSTALL_LIB_SUCCESS,
        IMAGE_DESCRIBE_EVENT_INSTALL_LIB_FAIL,
        IMAGE_DESCRIBE_EVENT_INSTALL_LIB_REQUEST,
        IMAGE_DESCRIBE_EVENT_INSTALL_LIB_DENY,
        IMAGE_DESCRIBE_EVENT_UNINSTALL_LIB_REQUEST,
        IMAGE_DESCRIBE_EVENT_UNINSTALL_LIB_DENY);

    public final int installSuccess;
    public final int installFail;
    public final int installRequest;
    public final int installDeny;
    public final int uninstallRequest;
    public final int uninstallDeny;

    ImageCaptionLogKeys(
        int installSuccess,
        int installFail,
        int installRequest,
        int installDeny,
        int uninstallRequest,
        int uninstallDeny) {
      this.installSuccess = installSuccess;
      this.installFail = installFail;
      this.installRequest = installRequest;
      this.installDeny = installDeny;
      this.uninstallRequest = uninstallRequest;
      this.uninstallDeny = uninstallDeny;
    }
  }

  public void onShortcutActionEvent(TalkbackAction shortcut) {}

  public void onTalkBackActivitiesEvent(int event) {}

  public static final int TRAINING_SECTION_ONBOARDING = 1;
  public static final int TRAINING_SECTION_TUTORIAL = 2;
  public static final int TRAINING_SECTION_TUTORIAL_BASIC_NAVIGATION = 3;
  public static final int TRAINING_SECTION_TUTORIAL_TEXT_EDITING = 4;
  public static final int TRAINING_SECTION_TUTORIAL_READING_NAVIGATION = 5;
  public static final int TRAINING_SECTION_TUTORIAL_VOICE_COMMAND = 6;
  public static final int TRAINING_SECTION_TUTORIAL_EVERYDAY_TASKS = 7;
  public static final int TRAINING_SECTION_TUTORIAL_PRACTICE_GESTURES = 8;
  public static final int TRAINING_BUTTON_NEXT = 9;
  public static final int TRAINING_BUTTON_PREVIOUS = 10;
  public static final int TRAINING_BUTTON_CLOSE = 11;
  public static final int TRAINING_BUTTON_TURN_OFF_TALKBACK = 12;

  /** Defines events of training sections. */
  @IntDef({
    TRAINING_SECTION_ONBOARDING,
    TRAINING_SECTION_TUTORIAL,
    TRAINING_SECTION_TUTORIAL_BASIC_NAVIGATION,
    TRAINING_SECTION_TUTORIAL_TEXT_EDITING,
    TRAINING_SECTION_TUTORIAL_READING_NAVIGATION,
    TRAINING_SECTION_TUTORIAL_VOICE_COMMAND,
    TRAINING_SECTION_TUTORIAL_EVERYDAY_TASKS,
    TRAINING_SECTION_TUTORIAL_PRACTICE_GESTURES,
    TRAINING_BUTTON_NEXT,
    TRAINING_BUTTON_PREVIOUS,
    TRAINING_BUTTON_CLOSE,
    TRAINING_BUTTON_TURN_OFF_TALKBACK,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface TrainingSectionId {}

  public void onTrainingSectionEntered(@TrainingSectionId int section) {}

  public static final int GEMINI_REQUEST = 1;
  public static final int GEMINI_SUCCESS = 2;

  /** Defines events of Gemini request & successful response. */
  @IntDef({
    GEMINI_REQUEST,
    GEMINI_SUCCESS,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface GeminiEventId {}

  public void onGeminiEvent(@GeminiEventId int eventId) {}

  public static final int GEMINI_FAIL_APIKEY_NOT_AVAILABLE = 1;
  public static final int GEMINI_FAIL_USER_NOT_OPT_IN = 2;
  public static final int GEMINI_FAIL_NETWORK_UNAVAILABLE = 3;
  public static final int GEMINI_FAIL_NO_SCREENSHOT_PROVIDED = 4;
  public static final int GEMINI_FAIL_COMMAND_NOT_PROVIDED = 5;
  public static final int GEMINI_FAIL_FAIL_TO_ENCODE_PICTURE = 6;
  public static final int GEMINI_FAIL_FAIL_TO_PARSE_RESPONSE = 7;
  public static final int GEMINI_FAIL_CONTENT_BLOCKED = 8;
  public static final int GEMINI_FAIL_PROTOCOL_ERROR = 9;

  /** Defines events of fail cases of Gemini request. */
  @IntDef({
    GEMINI_FAIL_APIKEY_NOT_AVAILABLE,
    GEMINI_FAIL_USER_NOT_OPT_IN,
    GEMINI_FAIL_NETWORK_UNAVAILABLE,
    GEMINI_FAIL_NO_SCREENSHOT_PROVIDED,
    GEMINI_FAIL_COMMAND_NOT_PROVIDED,
    GEMINI_FAIL_FAIL_TO_ENCODE_PICTURE,
    GEMINI_FAIL_FAIL_TO_PARSE_RESPONSE,
    GEMINI_FAIL_CONTENT_BLOCKED,
    GEMINI_FAIL_PROTOCOL_ERROR,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface GeminiFailId {}

  public void onGeminiFailEvent(@GeminiFailId int failId) {}

  // Count the times dialog popped up.
  public static final int GEMINI_OPT_IN_SHOW_DIALOG = 1;
  // Count the times dialog dismissed by positive ack.
  public static final int GEMINI_OPT_IN_CONSENT = 2;
  // Count the times dialog dismissed by negative ack.
  public static final int GEMINI_OPT_IN_DISSENT = 3;

  /** Defines user selection of Opt-in Dialog. */
  @IntDef({
    GEMINI_OPT_IN_SHOW_DIALOG,
    GEMINI_OPT_IN_CONSENT,
    GEMINI_OPT_IN_DISSENT,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface GeminiOptInId {}

  public void onGeminiOptInEvent(@GeminiOptInId int optInId) {}
}

/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.android.accessibility.talkback.gesture;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.FingerprintGestureController;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;
import androidx.annotation.StringRes;
import com.google.android.accessibility.compositor.GestureShortcutProvider;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WindowUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * The class provides gesture and action mappings in TalkBack for quick access. It updates cache
 * mappings whenever preference or screen layout changed.
 */
public class GestureShortcutMapping implements GestureShortcutProvider {
  private static final String TAG = GestureShortcutMapping.class.getSimpleName();

  /**
   * Type of gestures. The value is also used to prioritize gestures if multi-gestures are assigned
   * to one action. Smaller value has higher priority. For example, when creating the usage hint, we
   * prefer to use multi-finger gestures(value 0), then use single finger gestures(value 1) if no
   * multi-finger gestures are found.
   */
  @IntDef({MULTI_FINGER, SINGLE_FINGER, FINGERPRINT})
  @Retention(RetentionPolicy.SOURCE)
  private @interface GestureType {}

  private static final int MULTI_FINGER = 0;
  private static final int SINGLE_FINGER = 1;
  private static final int FINGERPRINT = 2;

  /** Defines how the gesture works with right-to-left screen. */
  @IntDef({RTL_UNRELATED, LTR_GESTURE, RTL_GESTURE})
  @Retention(RetentionPolicy.SOURCE)
  private @interface RTLType {}

  private static final int RTL_UNRELATED = 0;
  private static final int LTR_GESTURE = 1;
  private static final int RTL_GESTURE = 2;

  /** List all of supported gestures. */
  private enum TalkBackGesture {
    SWIPE_UP(
        AccessibilityService.GESTURE_SWIPE_UP,
        SINGLE_FINGER,
        R.string.pref_shortcut_up_key,
        R.string.pref_shortcut_up_default),
    SWIPE_DOWN(
        AccessibilityService.GESTURE_SWIPE_DOWN,
        SINGLE_FINGER,
        R.string.pref_shortcut_down_key,
        R.string.pref_shortcut_down_default),
    SWIPE_LEFT(
        AccessibilityService.GESTURE_SWIPE_LEFT,
        SINGLE_FINGER,
        LTR_GESTURE,
        R.string.pref_shortcut_left_key,
        R.string.pref_shortcut_left_default),
    SWIPE_LEFT_RTL(
        AccessibilityService.GESTURE_SWIPE_LEFT,
        SINGLE_FINGER,
        RTL_GESTURE,
        R.string.pref_shortcut_right_key,
        R.string.pref_shortcut_right_default),
    SWIPE_RIGHT(
        AccessibilityService.GESTURE_SWIPE_RIGHT,
        SINGLE_FINGER,
        LTR_GESTURE,
        R.string.pref_shortcut_right_key,
        R.string.pref_shortcut_right_default),
    SWIPE_RIGHT_RTL(
        AccessibilityService.GESTURE_SWIPE_RIGHT,
        SINGLE_FINGER,
        RTL_GESTURE,
        R.string.pref_shortcut_left_key,
        R.string.pref_shortcut_left_default),
    SWIPE_UP_AND_DOWN(
        AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN,
        SINGLE_FINGER,
        R.string.pref_shortcut_up_and_down_key,
        R.string.pref_shortcut_up_and_down_default),
    SWIPE_DOWN_AND_UP(
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP,
        SINGLE_FINGER,
        R.string.pref_shortcut_down_and_up_key,
        R.string.pref_shortcut_down_and_up_default),
    SWIPE_LEFT_AND_RIGHT(
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT,
        SINGLE_FINGER,
        LTR_GESTURE,
        R.string.pref_shortcut_left_and_right_key,
        R.string.pref_shortcut_left_and_right_default),
    SWIPE_LEFT_AND_RIGHT_RTL(
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT,
        SINGLE_FINGER,
        RTL_GESTURE,
        R.string.pref_shortcut_right_and_left_key,
        R.string.pref_shortcut_right_and_left_default),
    SWIPE_RIGHT_AND_LEFT(
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT,
        SINGLE_FINGER,
        LTR_GESTURE,
        R.string.pref_shortcut_right_and_left_key,
        R.string.pref_shortcut_right_and_left_default),
    SWIPE_RIGHT_AND_LEFT_RTL(
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT,
        SINGLE_FINGER,
        RTL_GESTURE,
        R.string.pref_shortcut_left_and_right_key,
        R.string.pref_shortcut_left_and_right_default),
    SWIPE_UP_AND_LEFT(
        AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT,
        SINGLE_FINGER,
        R.string.pref_shortcut_up_and_left_key,
        R.string.pref_shortcut_up_and_left_default),
    SWIPE_UP_AND_RIGHT(
        AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT,
        SINGLE_FINGER,
        R.string.pref_shortcut_up_and_right_key,
        R.string.pref_shortcut_up_and_right_default),
    SWIPE_DOWN_AND_LEFT(
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT,
        SINGLE_FINGER,
        R.string.pref_shortcut_down_and_left_key,
        R.string.pref_shortcut_down_and_left_default),
    SWIPE_DOWN_AND_RIGHT(
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT,
        SINGLE_FINGER,
        R.string.pref_shortcut_down_and_right_key,
        R.string.pref_shortcut_down_and_right_default),
    SWIPE_RIGHT_AND_DOWN(
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN,
        SINGLE_FINGER,
        R.string.pref_shortcut_right_and_down_key,
        R.string.pref_shortcut_right_and_down_default),
    SWIPE_RIGHT_AND_UP(
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP,
        SINGLE_FINGER,
        R.string.pref_shortcut_right_and_up_key,
        R.string.pref_shortcut_right_and_up_default),
    SWIPE_LEFT_AND_DOWN(
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN,
        SINGLE_FINGER,
        R.string.pref_shortcut_left_and_down_key,
        R.string.pref_shortcut_left_and_down_default),
    SWIPE_LEFT_AND_UP(
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP,
        SINGLE_FINGER,
        R.string.pref_shortcut_left_and_up_key,
        R.string.pref_shortcut_left_and_up_default),
    // Multi-finger Gestures
    TWO_FINGER_SINGLE_TAP(
        AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP,
        MULTI_FINGER,
        R.string.pref_shortcut_2finger_1tap_key,
        R.string.pref_shortcut_2finger_1tap_default),
    TWO_FINGER_DOUBLE_TAP(
        AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP,
        MULTI_FINGER,
        R.string.pref_shortcut_2finger_2tap_key,
        R.string.pref_shortcut_2finger_2tap_default),
    TWO_FINGER_TRIPLE_TAP(
        AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP,
        MULTI_FINGER,
        R.string.pref_shortcut_2finger_3tap_key,
        R.string.pref_shortcut_2finger_3tap_default),
    THREE_FINGER_SINGLE_TAP(
        AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP,
        MULTI_FINGER,
        R.string.pref_shortcut_3finger_1tap_key,
        R.string.pref_shortcut_3finger_1tap_default),
    THREE_FINGER_DOUBLE_TAP(
        AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP,
        MULTI_FINGER,
        R.string.pref_shortcut_3finger_2tap_key,
        R.string.pref_shortcut_3finger_2tap_default),
    THREE_FINGER_TRIPLE_TAP(
        AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP,
        MULTI_FINGER,
        R.string.pref_shortcut_3finger_3tap_key,
        R.string.pref_shortcut_3finger_3tap_default),
    FOUR_FINGER_SINGLE_TAP(
        AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP,
        MULTI_FINGER,
        R.string.pref_shortcut_4finger_1tap_key,
        R.string.pref_shortcut_4finger_1tap_default),
    FOUR_FINGER_DOUBLE_TAP(
        AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP,
        MULTI_FINGER,
        R.string.pref_shortcut_4finger_2tap_key,
        R.string.pref_shortcut_4finger_2tap_default),
    FOUR_FINGER_TRIPLE_TAP(
        AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP,
        MULTI_FINGER,
        R.string.pref_shortcut_4finger_3tap_key,
        R.string.pref_shortcut_4finger_3tap_default),
    TWO_FINGER_SWIPE_UP(
        AccessibilityService.GESTURE_2_FINGER_SWIPE_UP,
        MULTI_FINGER,
        R.string.pref_shortcut_2finger_swipe_up_key,
        R.string.pref_shortcut_2finger_swipe_up_default),
    TWO_FINGER_SWIPE_DOWN(
        AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN,
        MULTI_FINGER,
        R.string.pref_shortcut_2finger_swipe_down_key,
        R.string.pref_shortcut_2finger_swipe_down_default),
    TWO_FINGER_SWIPE_LEFT(
        AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT,
        MULTI_FINGER,
        R.string.pref_shortcut_2finger_swipe_left_key,
        R.string.pref_shortcut_2finger_swipe_left_default),
    TWO_FINGER_SWIPE_RIGHT(
        AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT,
        MULTI_FINGER,
        R.string.pref_shortcut_2finger_swipe_right_key,
        R.string.pref_shortcut_2finger_swipe_right_default),
    THREE_FINGER_SWIPE_UP(
        AccessibilityService.GESTURE_3_FINGER_SWIPE_UP,
        MULTI_FINGER,
        R.string.pref_shortcut_3finger_swipe_up_key,
        R.string.pref_shortcut_3finger_swipe_up_default),
    THREE_FINGER_SWIPE_DOWN(
        AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN,
        MULTI_FINGER,
        R.string.pref_shortcut_3finger_swipe_down_key,
        R.string.pref_shortcut_3finger_swipe_down_default),
    THREE_FINGER_SWIPE_LEFT(
        AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT,
        MULTI_FINGER,
        R.string.pref_shortcut_3finger_swipe_left_key,
        R.string.pref_shortcut_3finger_swipe_left_default),
    THREE_FINGER_SWIPE_RIGHT(
        AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT,
        MULTI_FINGER,
        R.string.pref_shortcut_3finger_swipe_right_key,
        R.string.pref_shortcut_3finger_swipe_right_default),
    FOUR_FINGER_SWIPE_UP(
        AccessibilityService.GESTURE_4_FINGER_SWIPE_UP,
        MULTI_FINGER,
        R.string.pref_shortcut_4finger_swipe_up_key,
        R.string.pref_shortcut_4finger_swipe_up_default),
    FOUR_FINGER_SWIPE_DOWN(
        AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN,
        MULTI_FINGER,
        R.string.pref_shortcut_4finger_swipe_down_key,
        R.string.pref_shortcut_4finger_swipe_down_default),
    FOUR_FINGER_SWIPE_LEFT(
        AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT,
        MULTI_FINGER,
        R.string.pref_shortcut_4finger_swipe_left_key,
        R.string.pref_shortcut_4finger_swipe_left_default),
    FOUR_FINGER_SWIPE_RIGHT(
        AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT,
        MULTI_FINGER,
        R.string.pref_shortcut_4finger_swipe_right_key,
        R.string.pref_shortcut_4finger_swipe_right_default),
    TWO_FINGER_DOUBLE_TAP_AND_HOLD(
        AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD,
        MULTI_FINGER,
        R.string.pref_shortcut_2finger_2tap_hold_key,
        R.string.pref_shortcut_2finger_2tap_hold_default),
    THREE_FINGER_DOUBLE_TAP_AND_HOLD(
        AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD,
        MULTI_FINGER,
        R.string.pref_shortcut_3finger_2tap_hold_key,
        R.string.pref_shortcut_3finger_2tap_hold_default),
    FOUR_FINGER_DOUBLE_TAP_AND_HOLD(
        AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD,
        MULTI_FINGER,
        R.string.pref_shortcut_4finger_2tap_hold_key,
        R.string.pref_shortcut_4finger_2tap_hold_default),
    // Fingerprint.
    FINGERPRINT_SWIPE_UP(
        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP,
        FINGERPRINT,
        R.string.pref_shortcut_fingerprint_up_key,
        R.string.pref_shortcut_fingerprint_up_default),
    FINGERPRINT_SWIPE_DOWN(
        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN,
        FINGERPRINT,
        R.string.pref_shortcut_fingerprint_down_key,
        R.string.pref_shortcut_fingerprint_down_default),
    FINGERPRINT_SWIPE_LEFT(
        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_LEFT,
        FINGERPRINT,
        R.string.pref_shortcut_fingerprint_left_key,
        R.string.pref_shortcut_fingerprint_left_default),
    FINGERPRINT_SWIPE_RIGHT(
        FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_RIGHT,
        FINGERPRINT,
        R.string.pref_shortcut_fingerprint_right_key,
        R.string.pref_shortcut_fingerprint_right_default);

    TalkBackGesture(int gestureId, @GestureType int gestureType, int keyId, int defaultActionId) {
      this(gestureId, gestureType, RTL_UNRELATED, keyId, defaultActionId);
    }

    TalkBackGesture(
        int gestureId,
        @GestureType int gestureType,
        @RTLType int rtlType,
        int keyId,
        int defaultActionId) {
      this.gestureId = gestureId;
      this.gestureType = gestureType;
      this.rtlType = rtlType;
      this.keyId = keyId;
      this.defaultActionId = defaultActionId;
    }

    final int gestureId;
    @GestureType final int gestureType;
    @RTLType final int rtlType;
    final int keyId;
    final int defaultActionId;
  }

  /** All supported actions. */
  private enum TalkbackAction {
    // Basic navigation.
    PERFORM_CLICK(
        R.string.shortcut_value_perform_click_action, R.string.shortcut_perform_click_action),
    PERFORM_LONG_CLICK(
        R.string.shortcut_value_perform_click_action, R.string.shortcut_perform_long_click_action),
    PREVIOUS(R.string.shortcut_value_previous, R.string.shortcut_previous),
    NEXT(R.string.shortcut_value_next, R.string.shortcut_next),
    FIRST_IN_SCREEN(R.string.shortcut_value_first_in_screen, R.string.shortcut_first_in_screen),
    LAST_IN_SCREEN(R.string.shortcut_value_last_in_screen, R.string.shortcut_last_in_screen),
    SCROLL_BACK(R.string.shortcut_value_scroll_back, R.string.shortcut_scroll_back),
    SCROLL_FORWARD(R.string.shortcut_value_scroll_forward, R.string.shortcut_scroll_forward),

    // System action.
    HOME(R.string.shortcut_value_home, R.string.shortcut_home),
    BACK(R.string.shortcut_value_back, R.string.shortcut_back),
    OVERVIEW(R.string.shortcut_value_overview, R.string.shortcut_overview),
    NOTIFICATIONS(R.string.shortcut_value_notifications, R.string.shortcut_notifications),
    QUICK_SETTINGS(R.string.shortcut_value_quick_settings, R.string.shortcut_quick_settings),
    ALL_APPS(R.string.shortcut_value_all_apps, R.string.shortcut_all_apps),
    A11Y_BUTTON(R.string.shortcut_value_a11y_button, R.string.shortcut_a11y_button),
    A11Y_BUTTON_LONG_PRESS(
        R.string.shortcut_value_a11y_button_long_press, R.string.shortcut_a11y_button_long_press),

    // Reading control.
    READ_FROM_TOP(R.string.shortcut_value_read_from_top, R.string.shortcut_read_from_top),
    READ_FROM_CURRENT(
        R.string.shortcut_value_read_from_current, R.string.shortcut_read_from_current),
    PAUSE_OR_RESUME_FEEDBACK(
        R.string.shortcut_value_pause_or_resume_feedback,
        R.string.shortcut_pause_or_resume_feedback),
    TOGGLE_VOICE_FEEDBACK(
        R.string.shortcut_value_toggle_voice_feedback, R.string.shortcut_toggle_voice_feedback),
    SHOW_LANGUAGE_OPTIONS(
        R.string.shortcut_value_show_language_options, R.string.shortcut_show_language_options),

    // Menu control.
    TALKBACK_BREAKOUT(
        R.string.shortcut_value_talkback_breakout, R.string.shortcut_talkback_breakout),
    SELECT_PREVIOUS_SETTING(
        R.string.shortcut_value_select_previous_setting, R.string.shortcut_select_previous_setting),
    SELECT_NEXT_SETTING(
        R.string.shortcut_value_select_next_setting, R.string.shortcut_select_next_setting),
    SELECTED_SETTING_PREVIOUS_ACTION(
        R.string.shortcut_value_selected_setting_previous_action,
        R.string.shortcut_selected_setting_previous_action),
    SELECTED_SETTING_NEXT_ACTION(
        R.string.shortcut_value_selected_setting_next_action,
        R.string.shortcut_selected_setting_next_action),

    // Text editing.
    START_SELECTION_MODE(
        R.string.shortcut_value_start_selection_mode,
        R.string.title_edittext_breakout_start_selection_mode),
    COPY(R.string.shortcut_value_copy, android.R.string.copy),
    CUT(R.string.shortcut_value_cut, android.R.string.cut),
    PASTE(R.string.shortcut_value_paste, android.R.string.paste),
    EDITING(R.string.shortcut_value_editing, R.string.shortcut_editing),
    BRAILLE_KEYBOARD(R.string.shortcut_value_braille_keyboard, R.string.shortcut_braille_keyboard),

    // Special features.
    MEDIA_CONTROL(R.string.shortcut_value_media_control, R.string.shortcut_media_control),
    VOICE_COMMANDS(R.string.shortcut_value_voice_commands, R.string.shortcut_voice_commands),
    SCREEN_SEARCH(R.string.shortcut_value_screen_search, R.string.title_show_screen_search),
    PASS_THROUGH_NEXT_GESTURE(
        R.string.shortcut_value_pass_through_next_gesture, R.string.shortcut_pass_through_next),
    PRINT_NODE_TREE(R.string.shortcut_value_print_node_tree, R.string.shortcut_print_node_tree),
    PRINT_PERFORMANCE_STATS(
        R.string.shortcut_value_print_performance_stats, R.string.shortcut_print_performance_stats),
    SHOW_CUSTOM_ACTIONS(
        R.string.shortcut_value_show_custom_actions, R.string.shortcut_show_custom_actions),
    TUTORIAL(R.string.shortcut_value_tutorial, R.string.shortcut_tutorial),
    PRACTICE_GESTURE(
        R.string.shortcut_value_practice_gestures, R.string.shortcut_practice_gestures),
    REPORT_GESTURE(R.string.shortcut_value_report_gesture, R.string.shortcut_report_gesture);

    final @StringRes int actionKeyResId;
    final @StringRes int actionNameResId;

    TalkbackAction(@StringRes int actionKeyResId, @StringRes int actionNameResId) {
      this.actionKeyResId = actionKeyResId;
      this.actionNameResId = actionNameResId;
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  // Constants

  // TODO  : Remove constants copied from framework since R-QPR.
  /**
   * The user has performed a touch-exploration gesture on the touch screen without ever triggering
   * gesture detection. This gesture is only dispatched when {@link
   * FeatureSupport#FLAG_SEND_MOTION_EVENTS} is set.
   */
  public static final int GESTURE_TOUCH_EXPLORATION = -2;

  /**
   * The user has performed a passthrough gesture on the touch screen without ever triggering
   * gesture detection. This gesture is only dispatched when {@link
   * FeatureSupport#FLAG_SEND_MOTION_EVENTS} is set.
   */
  public static final int GESTURE_PASSTHROUGH = -1;

  /**
   * The user has performed an unrecognized gesture on the touch screen. This gesture is only
   * dispatched when {@link FeatureSupport#FLAG_SEND_MOTION_EVENTS} is set.
   */
  public static final int GESTURE_UNKNOWN = 0;

  /** The user has performed a double tap gesture on the touch screen. */
  public static final int GESTURE_DOUBLE_TAP = 17;

  /** The user has performed a double tap and hold gesture on the touch screen. */
  public static final int GESTURE_DOUBLE_TAP_AND_HOLD = 18;

  /** The user has performed a two-finger single-tap and hold gesture on the touch screen. */
  public static final int GESTURE_2_FINGER_SINGLE_TAP_AND_HOLD = 43;

  /** The user has performed a three-finger single-tap and hold gesture on the touch screen. */
  public static final int GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD = 44;

  /** The user has performed a three-finger triple-tap and hold gesture on the touch screen. */
  public static final int GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD = 45;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  protected String actionUnassigned;
  protected String actionTalkbackContextMenu;

  private Context context;
  private SharedPreferences prefs;
  private int previousScreenLayout = 0;
  private HashMap<String, GestureCollector> actionToGesture = new HashMap<>();
  private HashMap<Integer, String> gestureIdToActionKey = new HashMap<>();

  /** Reloads preferences whenever their values change. */
  private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
      (prefs, key) -> loadGestureIdToActionKeyMap();

  public GestureShortcutMapping(Context context) {
    this.context = context;
    actionUnassigned = context.getString(R.string.shortcut_value_unassigned);
    actionTalkbackContextMenu = context.getString(R.string.shortcut_value_talkback_breakout);
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    loadGestureIdToActionKeyMap();
  }

  public void onConfigurationChanged(Configuration newConfig) {
    if (newConfig != null && newConfig.screenLayout != previousScreenLayout) {
      loadGestureIdToActionKeyMap();
      previousScreenLayout = newConfig.screenLayout;
    }
  }

  public void onUnbind() {
    prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
  }

  /** Returns gesture shortcut name for talkback context menu. */
  @Override
  public @Nullable CharSequence nodeMenuShortcut() {
    return getGestureFromActionKey(actionTalkbackContextMenu);
  }

  /**
   * Gets corresponding action from gesture-action mappings.
   *
   * @param gestureId The gesture id corresponds to the action
   * @return action key string
   */
  public String getActionKeyFromGestureId(int gestureId) {
    String action = gestureIdToActionKey.get(gestureId);
    return action == null ? actionUnassigned : action;
  }

  /**
   * Gets the highest priority gesture text for given action, including fingerprint gestures.
   *
   * <p><b>Priority:</b> 1. Default multi-finger gesture. 2. Default single-finger gesture. 3. User
   * customized multi-finger gesture. 4. User customized single-finger gesture. 5. Fingerprint
   * gesture.
   *
   * @param action The corresponding action assigned to the gesture
   * @return gesture text, or null if no gesture assigned to the action
   */
  public @Nullable String getGestureFromActionKey(String action) {
    if (TextUtils.isEmpty(action) || !actionToGesture.containsKey(action)) {
      return null;
    }

    GestureCollector gestureCollector = actionToGesture.get(action);
    TalkBackGesture gesture = gestureCollector.getPrioritizedGesture();
    if (gesture == null) {
      LogUtils.w(
          TAG, "The Action is loaded in the mapping table, but no suitable gesture be found.");
      return null;
    }

    if (gesture.gestureType == FINGERPRINT) {
      return getFingerprintGestureString(context, gesture.gestureId);
    }

    return getGestureString(context, gesture.gestureId);
  }

  /**
   * Gets the gesture text from {@link #getGestureFromActionKey(String)} for each action.
   *
   * @param actions The corresponding actions assigned to the gesture
   * @return a list of gesture texts
   */
  public @NonNull List<String> getGestureTextsFromActionKeys(String... actions) {
    List<String> matchedGestures = new ArrayList<>();

    for (String action : actions) {
      String gestureString = getGestureFromActionKey(action);
      if (!TextUtils.isEmpty(gestureString)) {
        matchedGestures.add(gestureString);
      }
    }
    return matchedGestures;
  }

  /** Loads gesture-action mappings from shared preference. */
  private void loadGestureIdToActionKeyMap() {
    loadGestureIdToActionKeyMap(
        FeatureSupport.isMultiFingerGestureSupported(),
        FeatureSupport.isFingerprintSupported(context));
  }

  @VisibleForTesting
  /** Loads gesture-action mappings with multi-finger gesture on. It's only for testing purpose. */
  protected void loadGestureIdToActionKeyMapWithMultiFingerGesture() {
    loadGestureIdToActionKeyMap(
        /* isMultiFingerOn= */ true, FeatureSupport.isFingerprintSupported(context));
  }

  private void loadGestureIdToActionKeyMap(boolean isMultiFingerOn, boolean isFingerprintOn) {
    LogUtils.d(
        TAG,
        "loadActionToGestureIdMap - isMultiFingerOn : "
            + isMultiFingerOn
            + " isFingerprintOn : "
            + isFingerprintOn);
    actionToGesture.clear();
    gestureIdToActionKey.clear();

    // Load TalkBack gestures.
    for (TalkBackGesture gesture : TalkBackGesture.values()) {
      // For some gestures, we have different behavior if the device is RTL. Skip the value of
      // non-RTL if it's RTL, and vice versa.
      if (skipGestureForRTL(gesture)) {
        continue;
      }

      // Skip multi-finger gestures when isMultiFingerOn = false.
      if (!isMultiFingerOn && gesture.gestureType == MULTI_FINGER) {
        continue;
      }

      // Skip fingerprint gestures when isFingerprintOn = false.
      if (!isFingerprintOn && gesture.gestureType == FINGERPRINT) {
        continue;
      }

      String action =
          prefs.getString(
              context.getString(gesture.keyId), context.getString(gesture.defaultActionId));
      GestureCollector gestureCollector;
      if (actionToGesture.containsKey(action)) {
        gestureCollector = actionToGesture.get(action);
      } else {
        gestureCollector = new GestureCollector();
      }

      // Check the action is default or customized action.
      if (TextUtils.equals(action, context.getString(gesture.defaultActionId))) {
        gestureCollector.addDefaultGesture(gesture);
      } else {
        gestureCollector.addCustomizedGesture(gesture);
      }

      actionToGesture.put(action, gestureCollector);

      // Load the mapping table of the gesture id to the action.
      if (gesture.gestureType == FINGERPRINT) {
        // Fingerprint gestures use another gesture id system, so skip fingerprint gestures in this
        // table.
        continue;
      }
      gestureIdToActionKey.put(
          gesture.gestureId,
          prefs.getString(
              context.getString(gesture.keyId), context.getString(gesture.defaultActionId)));
    }

    // Don't need to keep unassigned action in the map.
    actionToGesture.remove(actionUnassigned);
  }

  private boolean skipGestureForRTL(TalkBackGesture gesture) {
    if (gesture.rtlType == RTL_UNRELATED) {
      return false;
    }

    if (WindowUtils.isScreenLayoutRTL(context)) {
      // Skip LTR gestures.
      if (gesture.rtlType == LTR_GESTURE) {
        return true;
      }
    } else {
      // Skip RTL gestures.
      if (gesture.rtlType == RTL_GESTURE) {
        return true;
      }
    }

    return false;
  }

  /** Returns the corresponding action resource Id of action key. */
  public static String getActionString(Context context, String actionKeyString) {
    for (TalkbackAction action : TalkbackAction.values()) {
      if (context.getString(action.actionKeyResId).equals(actionKeyString)) {
        return context.getString(action.actionNameResId);
      }
    }
    return context.getString(R.string.shortcut_unassigned);
  }

  /** Returns the corresponding gesture string of gesture id. */
  public static String getGestureString(Context context, int gestureId) {
    switch (gestureId) {
      case AccessibilityService.GESTURE_SWIPE_UP:
        return context.getString(R.string.title_pref_shortcut_up);
      case AccessibilityService.GESTURE_SWIPE_DOWN:
        return context.getString(R.string.title_pref_shortcut_down);
      case AccessibilityService.GESTURE_SWIPE_LEFT:
        return context.getString(R.string.title_pref_shortcut_left);
      case AccessibilityService.GESTURE_SWIPE_RIGHT:
        return context.getString(R.string.title_pref_shortcut_right);
      case AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN:
        return context.getString(R.string.title_pref_shortcut_up_and_down);
      case AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP:
        return context.getString(R.string.title_pref_shortcut_down_and_up);
      case AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT:
        return context.getString(R.string.title_pref_shortcut_left_and_right);
      case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT:
        return context.getString(R.string.title_pref_shortcut_right_and_left);
      case AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT:
        return context.getString(R.string.title_pref_shortcut_up_and_right);
      case AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT:
        return context.getString(R.string.title_pref_shortcut_up_and_left);
      case AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT:
        return context.getString(R.string.title_pref_shortcut_down_and_right);
      case AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT:
        return context.getString(R.string.title_pref_shortcut_down_and_left);
      case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN:
        return context.getString(R.string.title_pref_shortcut_right_and_down);
      case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP:
        return context.getString(R.string.title_pref_shortcut_right_and_up);
      case AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN:
        return context.getString(R.string.title_pref_shortcut_left_and_down);
      case AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP:
        return context.getString(R.string.title_pref_shortcut_left_and_up);
      case AccessibilityService.GESTURE_2_FINGER_SWIPE_UP:
        return context.getString(R.string.title_pref_shortcut_2finger_swipe_up);
      case AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN:
        return context.getString(R.string.title_pref_shortcut_2finger_swipe_down);
      case AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT:
        return context.getString(R.string.title_pref_shortcut_2finger_swipe_left);
      case AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT:
        return context.getString(R.string.title_pref_shortcut_2finger_swipe_right);
      case AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP:
        return context.getString(R.string.title_pref_shortcut_2finger_1tap);
      case AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP:
        return context.getString(R.string.title_pref_shortcut_2finger_2tap);
      case AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP:
        return context.getString(R.string.title_pref_shortcut_2finger_3tap);
      case AccessibilityService.GESTURE_3_FINGER_SWIPE_UP:
        return context.getString(R.string.title_pref_shortcut_3finger_swipe_up);
      case AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN:
        return context.getString(R.string.title_pref_shortcut_3finger_swipe_down);
      case AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT:
        return context.getString(R.string.title_pref_shortcut_3finger_swipe_left);
      case AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT:
        return context.getString(R.string.title_pref_shortcut_3finger_swipe_right);
      case AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP:
        return context.getString(R.string.title_pref_shortcut_3finger_1tap);
      case AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP:
        return context.getString(R.string.title_pref_shortcut_3finger_2tap);
      case AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP:
        return context.getString(R.string.title_pref_shortcut_3finger_3tap);
      case AccessibilityService.GESTURE_4_FINGER_SWIPE_UP:
        return context.getString(R.string.title_pref_shortcut_4finger_swipe_up);
      case AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN:
        return context.getString(R.string.title_pref_shortcut_4finger_swipe_down);
      case AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT:
        return context.getString(R.string.title_pref_shortcut_4finger_swipe_left);
      case AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT:
        return context.getString(R.string.title_pref_shortcut_4finger_swipe_right);
      case AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP:
        return context.getString(R.string.title_pref_shortcut_4finger_1tap);
      case AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP:
        return context.getString(R.string.title_pref_shortcut_4finger_2tap);
      case AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP:
        return context.getString(R.string.title_pref_shortcut_4finger_3tap);
      case AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD:
        return context.getString(R.string.title_pref_shortcut_2finger_2tap_hold);
      case AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD:
        return context.getString(R.string.title_pref_shortcut_3finger_2tap_hold);
      case AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD:
        return context.getString(R.string.title_pref_shortcut_4finger_2tap_hold);
      case GESTURE_TOUCH_EXPLORATION:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_touch_explore)
            : null;
      case GESTURE_PASSTHROUGH:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_pass_through)
            : null;
      case GESTURE_UNKNOWN:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_unknown)
            : null;
      case GESTURE_DOUBLE_TAP:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_double_tap)
            : null;
      case GESTURE_DOUBLE_TAP_AND_HOLD:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_double_tap_and_hold)
            : null;
      case GESTURE_2_FINGER_SINGLE_TAP_AND_HOLD:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_2finger_tap_hold)
            : null;
      case GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_3finger_tap_hold)
            : null;
      case GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_3finger_3tap_hold)
            : null;
      default:
        return null;
    }
  }

  private static String getFingerprintGestureString(Context context, int fingerprintGestureId) {
    switch (fingerprintGestureId) {
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP:
        return context.getString(R.string.title_pref_shortcut_fingerprint_up);
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN:
        return context.getString(R.string.title_pref_shortcut_fingerprint_down);
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_RIGHT:
        return context.getString(R.string.title_pref_shortcut_fingerprint_right);
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_LEFT:
        return context.getString(R.string.title_pref_shortcut_fingerprint_left);
      default:
        return null;
    }
  }

  /** Keeps different kind of gestures for an TalkBack action, and prioritizes gestures. */
  private static class GestureCollector {
    List<TalkBackGesture> defaultGestures = new ArrayList<>();
    List<TalkBackGesture> customizedGestures = new ArrayList<>();

    void addDefaultGesture(TalkBackGesture gesture) {
      defaultGestures.add(gesture);
    }

    void addCustomizedGesture(TalkBackGesture gesture) {
      customizedGestures.add(gesture);
    }

    /**
     * Returns the gesture id with the highest priority, it will return null if no suitable gesture
     * in the collector.
     */
    @Nullable
    TalkBackGesture getPrioritizedGesture() {
      // Priority : default multi-finger gesture -> default single-finger gesture -> customized
      // multi-finger gesture -> customized single-finger gesture -> fingerprint.
      if (!defaultGestures.isEmpty()) {
        Collections.sort(defaultGestures, new GestureComparator());
        return defaultGestures.get(0);
      }

      if (!customizedGestures.isEmpty()) {
        Collections.sort(customizedGestures, new GestureComparator());
        return customizedGestures.get(0);
      }

      return null;
    }
  }

  /**
   * Comparator for {@link TalkBackGesture}. Will sort gestures by following order: Multi-finger
   * gesture > Single-finger gesture > Fingerprint.
   */
  private static class GestureComparator implements Comparator<TalkBackGesture> {
    @Override
    public int compare(TalkBackGesture g1, TalkBackGesture g2) {
      return Integer.compare(g1.gestureType, g2.gestureType);
    }
  }
}

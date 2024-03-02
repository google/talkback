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

import static com.google.android.accessibility.utils.gestures.GestureManifold.GESTURE_FAKED_SPLIT_TYPING;
import static com.google.android.accessibility.utils.gestures.GestureManifold.GESTURE_TAP_HOLD_AND_2ND_FINGER_BACKWARD_DOUBLE_TAP;
import static com.google.android.accessibility.utils.gestures.GestureManifold.GESTURE_TAP_HOLD_AND_2ND_FINGER_FORWARD_DOUBLE_TAP;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.FingerprintGestureController;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.FeatureFlagReader;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.GestureShortcutProvider;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Logger;
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
import java.util.Map;

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

  // The number of gesture sets to be provided.
  private static final int NUMBER_OF_GESTURE_SET = 2;

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
    // One-finger Tap
    ONE_FINGER_DOUBLE_TAP(
        AccessibilityService.GESTURE_DOUBLE_TAP,
        MULTI_FINGER,
        R.string.pref_shortcut_1finger_2tap_key,
        R.string.pref_shortcut_1finger_2tap_default),
    ONE_FINGER_DOUBLE_TAP_AND_HOLD(
        AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD,
        MULTI_FINGER,
        R.string.pref_shortcut_1finger_2tap_hold_key,
        R.string.pref_shortcut_1finger_2tap_hold_default),
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
    THREE_FINGER_TRIPLE_TAP_AND_HOLD(
        AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD,
        MULTI_FINGER,
        R.string.pref_shortcut_3finger_3tap_hold_key,
        R.string.pref_shortcut_3finger_3tap_hold_default),
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
    THREE_FINGER_TAP_AND_HOLD(
        AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD,
        MULTI_FINGER,
        R.string.pref_shortcut_3finger_1tap_hold_key,
        R.string.pref_shortcut_3finger_1tap_hold_default),
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
    TWO_FINGER_TRIPLE_TAP_AND_HOLD(
        AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD,
        MULTI_FINGER,
        R.string.pref_shortcut_2finger_3tap_hold_key,
        R.string.pref_shortcut_2finger_3tap_hold_default),

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
    /**
     * For mapping the gesture id to action, we need to consider the variance when gesture set is
     * introduced. When gesture set 0 (default set) is activated, the mapping key is the same as
     * this keyId; otherwise, it needs to append with a '-' and gesture set to resolve the authentic
     * mapped action.
     */
    final int keyId;

    final int defaultActionId;
  }

  /** All supported actions. */
  public enum TalkbackAction {
    UNASSIGNED_ACTION(-1, -1),
    // Basic navigation.
    PERFORM_CLICK(
        R.string.shortcut_value_perform_click_action, R.string.shortcut_perform_click_action),
    PERFORM_LONG_CLICK(
        R.string.shortcut_value_perform_click_action, R.string.shortcut_perform_long_click_action),
    PREVIOUS(R.string.shortcut_value_previous, R.string.shortcut_previous),
    NEXT(R.string.shortcut_value_next, R.string.shortcut_next),
    FIRST_IN_SCREEN(R.string.shortcut_value_first_in_screen, R.string.shortcut_first_in_screen),
    LAST_IN_SCREEN(R.string.shortcut_value_last_in_screen, R.string.shortcut_last_in_screen),
    PREV_CONTAINER(R.string.shortcut_value_prev_container, R.string.shortcut_prev_container),
    NEXT_CONTAINER(R.string.shortcut_value_next_container, R.string.shortcut_next_container),
    PREVIOUS_WINDOW(R.string.shortcut_value_previous_window, R.string.shortcut_previous_window),
    NEXT_WINDOW(R.string.shortcut_value_next_window, R.string.shortcut_next_window),
    SCROLL_BACK(R.string.shortcut_value_scroll_back, R.string.shortcut_scroll_back),
    SCROLL_FORWARD(R.string.shortcut_value_scroll_forward, R.string.shortcut_scroll_forward),
    SCROLL_UP(R.string.shortcut_value_scroll_up, R.string.shortcut_scroll_up),
    SCROLL_DOWN(R.string.shortcut_value_scroll_down, R.string.shortcut_scroll_down),
    SCROLL_LEFT(R.string.shortcut_value_scroll_left, R.string.shortcut_scroll_left),
    SCROLL_RIGHT(R.string.shortcut_value_scroll_right, R.string.shortcut_scroll_right),

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
    MOVE_CURSOR_TO_BEGINNING(
        R.string.shortcut_value_move_cursor_to_beginning,
        R.string.title_edittext_breakout_move_to_beginning),
    MOVE_CURSOR_TO_END(
        R.string.shortcut_value_move_cursor_to_end, R.string.title_edittext_breakout_move_to_end),
    SELECT_ALL(R.string.shortcut_value_select_all, android.R.string.selectAll),
    COPY(R.string.shortcut_value_copy, android.R.string.copy),
    CUT(R.string.shortcut_value_cut, android.R.string.cut),
    PASTE(R.string.shortcut_value_paste, android.R.string.paste),
    COPY_LAST_SPOKEN_UTTERANCE(
        R.string.shortcut_value_copy_last_spoken_phrase, R.string.title_copy_last_spoken_phrase),
    BRAILLE_KEYBOARD(R.string.shortcut_value_braille_keyboard, R.string.shortcut_braille_keyboard),

    // Special features.
    MEDIA_CONTROL(R.string.shortcut_value_media_control, R.string.shortcut_media_control),
    INCREASE_VOLUME(R.string.shortcut_value_increase_volume, R.string.shortcut_increase_volume),
    DECREASE_VOLUME(R.string.shortcut_value_decrease_volume, R.string.shortcut_decrease_volume),
    VOICE_COMMANDS(R.string.shortcut_value_voice_commands, R.string.shortcut_voice_commands),
    SCREEN_SEARCH(R.string.shortcut_value_screen_search, R.string.title_show_screen_search),
    SHOW_HIDE_SCREEN(R.string.shortcut_value_show_hide_screen, R.string.title_show_hide_screen),
    PASS_THROUGH_NEXT_GESTURE(
        R.string.shortcut_value_pass_through_next_gesture, R.string.shortcut_pass_through_next),
    PRINT_NODE_TREE(R.string.shortcut_value_print_node_tree, R.string.shortcut_print_node_tree),
    PRINT_PERFORMANCE_STATS(
        R.string.shortcut_value_print_performance_stats, R.string.shortcut_print_performance_stats),
    SHOW_CUSTOM_ACTIONS(
        R.string.shortcut_value_show_custom_actions, R.string.shortcut_show_custom_actions),
    NAVIGATE_BRAILLE_SETTINGS(
        R.string.shortcut_value_braille_display_settings,
        R.string.shortcut_braille_display_settings),
    TUTORIAL(R.string.shortcut_value_tutorial, R.string.shortcut_tutorial),
    PRACTICE_GESTURE(
        R.string.shortcut_value_practice_gestures, R.string.shortcut_practice_gestures),
    REPORT_GESTURE(R.string.shortcut_value_report_gesture, R.string.shortcut_report_gesture),
    TOGGLE_BRAILLE_DISPLAY_ON_OFF(
        R.string.shortcut_value_toggle_braille_display, R.string.shortcut_toggle_braille_display),
    DESCRIBE_IMAGE(R.string.shortcut_value_describe_image, R.string.title_image_caption);

    @StringRes final int actionKeyResId;
    @StringRes final int actionNameResId;

    TalkbackAction(@StringRes int actionKeyResId, @StringRes int actionNameResId) {
      this.actionKeyResId = actionKeyResId;
      this.actionNameResId = actionNameResId;
    }
  }

  ///////////////////////////////////////////////////////////////////////////
  // Constants

  // Copies hidden constants from framework.
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

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  protected String actionUnassigned;
  protected String actionTalkbackContextMenu;
  protected String actionNextReadingMenuSetting;
  protected String actionReadingMenuUp;
  protected String actionReadingMenuDown;
  protected String actionShortcut;

  private final String actionGestureUnsupported;

  private Context context;
  private boolean gestureSetEnabled;
  // Specify which gesture set (0/1) is activated. Default value is 0.
  private int currentGestureSet;
  private final SharedPreferences prefs;
  private int previousScreenLayout = 0;
  private final List<HashMap<String, GestureCollector>> actionToGesture =
      new ArrayList<HashMap<String, GestureCollector>>();
  private final List<HashMap<Integer, String>> gestureIdToActionKey =
      new ArrayList<HashMap<Integer, String>>();
  private final HashMap<Integer, String> fingerprintGestureIdToActionKey = new HashMap<>();

  /** Reloads preferences whenever their values change. */
  private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
      (prefs, key) -> {
        loadGestureIdToActionKeyMap();
        if (context.getResources().getString(R.string.pref_gesture_set_key).equals(key)) {
          currentGestureSet =
              SharedPreferencesUtils.getIntFromStringPref(
                  prefs,
                  context.getResources(),
                  R.string.pref_gesture_set_key,
                  R.string.pref_gesture_set_value_default);
        } else if (context
            .getResources()
            .getString(R.string.pref_multiple_gesture_set_key)
            .equals(key)) {
          gestureSetEnabled =
              isGestureSetEnabled(
                  context,
                  prefs,
                  R.string.pref_multiple_gesture_set_key,
                  R.bool.pref_multiple_gesture_set_default);
        }
      };

  public GestureShortcutMapping(Context context) {
    this.context = context;
    actionGestureUnsupported = context.getString(R.string.shortcut_value_unsupported);
    actionUnassigned = context.getString(R.string.shortcut_value_unassigned);
    actionTalkbackContextMenu = context.getString(R.string.shortcut_value_talkback_breakout);
    actionNextReadingMenuSetting = context.getString(R.string.shortcut_value_select_next_setting);
    actionReadingMenuUp =
        context.getString(R.string.shortcut_value_selected_setting_previous_action);
    actionReadingMenuDown = context.getString(R.string.shortcut_value_selected_setting_next_action);
    actionShortcut = context.getString(R.string.shortcut_value_show_custom_actions);
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    loadGestureIdToActionKeyMap();
    gestureSetEnabled =
        isGestureSetEnabled(
            context,
            prefs,
            R.string.pref_multiple_gesture_set_key,
            R.bool.pref_multiple_gesture_set_default);
    currentGestureSet =
        gestureSetEnabled
            ? SharedPreferencesUtils.getIntFromStringPref(
                prefs,
                context.getResources(),
                R.string.pref_gesture_set_key,
                R.string.pref_gesture_set_value_default)
            : 0;
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

  public int switchGestureSet(boolean isNext) {
    gestureSetEnabled =
        isGestureSetEnabled(
            context,
            prefs,
            R.string.pref_multiple_gesture_set_key,
            R.bool.pref_multiple_gesture_set_default);

    if (!gestureSetEnabled) {
      currentGestureSet = 0;
      return 0;
    }
    int gestureSet =
        SharedPreferencesUtils.getIntFromStringPref(
            prefs,
            context.getResources(),
            R.string.pref_gesture_set_key,
            R.string.pref_gesture_set_value_default);
    gestureSet =
        isNext
            ? (gestureSet + 1) % NUMBER_OF_GESTURE_SET
            : (gestureSet == 0) ? (NUMBER_OF_GESTURE_SET - 1) : (gestureSet - 1);
    SharedPreferencesUtils.putStringPref(
        prefs, context.getResources(), R.string.pref_gesture_set_key, String.valueOf(gestureSet));
    return gestureSet;
  }

  /** Returns gesture shortcut name for talkback context menu. */
  @Override
  @Nullable
  public CharSequence nodeMenuShortcut() {
    return getGestureFromActionKey(actionTalkbackContextMenu);
  }

  @Override
  @Nullable
  public CharSequence readingMenuNextSettingShortcut() {
    return getGestureFromActionKey(actionNextReadingMenuSetting);
  }

  @Override
  @Nullable
  public CharSequence readingMenuDownShortcut() {
    return getGestureFromActionKey(actionReadingMenuDown);
  }

  @Override
  @Nullable
  public CharSequence readingMenuUpShortcut() {
    return getGestureFromActionKey(actionReadingMenuUp);
  }

  @Override
  @Nullable
  public CharSequence actionsShortcut() {
    return getGestureFromActionKey(actionShortcut);
  }

  /**
   * Gets corresponding action from gesture-action mappings.
   *
   * @param gestureId The gesture id corresponds to the action
   * @return action key string
   */
  public String getActionKeyFromGestureId(int gestureId) {
    return getActionKeyFromGestureId(currentGestureSet, gestureId);
  }

  private String getActionKeyFromGestureId(int index, int gestureId) {
    if (index < 0 || index >= NUMBER_OF_GESTURE_SET) {
      // Uses index 0 as a fallback.
      LogUtils.w(TAG, "Gesture set is not allowed; fallback to 0.");
      index = 0;
    }
    if (gestureId == GESTURE_TAP_HOLD_AND_2ND_FINGER_FORWARD_DOUBLE_TAP
        || gestureId == GESTURE_TAP_HOLD_AND_2ND_FINGER_BACKWARD_DOUBLE_TAP) {
      // These 2 gestures are dedicated for switching gesture set.
      return gestureSetEnabled ? context.getString(R.string.switch_gesture_set) : actionUnassigned;
    }
    String action = gestureIdToActionKey.get(index).get(gestureId);
    return action == null ? actionUnassigned : action;
  }

  /** Returns {@code true} if this gesture is supported. */
  public boolean isSupportedGesture(int gestureId) {
    String action = gestureIdToActionKey.get(0).get(gestureId);
    return action != null && !TextUtils.equals(action, actionGestureUnsupported);
  }

  /**
   * Gets corresponding action from fingerprint gesture-action mappings.
   *
   * @param fingerprintGestureId The fingerprint gesture id corresponds to the action
   * @return action key string
   */
  public String getActionKeyFromFingerprintGestureId(int fingerprintGestureId) {
    String action = fingerprintGestureIdToActionKey.get(fingerprintGestureId);
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
  @Nullable
  public String getGestureFromActionKey(String action) {
    if (TextUtils.isEmpty(action) || !actionToGesture.get(currentGestureSet).containsKey(action)) {
      return null;
    }

    GestureCollector gestureCollector = actionToGesture.get(currentGestureSet).get(action);
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
  @NonNull
  public List<String> getGestureTextsFromActionKeys(String... actions) {
    List<String> matchedGestures = new ArrayList<>();

    for (String action : actions) {
      String gestureString = getGestureFromActionKey(action);
      if (!TextUtils.isEmpty(gestureString)) {
        matchedGestures.add(gestureString);
      }
    }
    return matchedGestures;
  }

  /**
   * Returns an action-gesture mapping including all actions. The map key is an action key. The map
   * value is the text of the gesture which is assigned to the action.
   */
  public HashMap<String, String> getAllGestureTexts() {
    final HashMap<String, String> actionKeyToGestureText = new HashMap<>();
    actionToGesture
        .get(currentGestureSet)
        .forEach(
            (action, gestureCollector) -> {
              TalkBackGesture gesture = gestureCollector.getPrioritizedGesture();
              if (gesture == null) {
                return;
              }

              if (gesture.gestureType == FINGERPRINT) {
                actionKeyToGestureText.put(
                    action, getFingerprintGestureString(context, gesture.gestureId));
              } else {
                actionKeyToGestureText.put(action, getGestureString(context, gesture.gestureId));
              }
            });
    return actionKeyToGestureText;
  }

  /** Loads gesture-action mappings from shared preference. */
  private void loadGestureIdToActionKeyMap() {
    loadGestureIdToActionKeyMap(
        FeatureSupport.isMultiFingerGestureSupported(),
        FeatureSupport.isFingerprintGestureSupported(context));
  }

  /** Loads gesture-action mappings with multi-finger gesture on. It's only for testing purpose. */
  @VisibleForTesting
  protected void loadGestureIdToActionKeyMapWithMultiFingerGesture() {
    loadGestureIdToActionKeyMap(
        /* isMultiFingerOn= */ true, FeatureSupport.isFingerprintGestureSupported(context));
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
    fingerprintGestureIdToActionKey.clear();

    for (int index = 0; index < NUMBER_OF_GESTURE_SET; index++) {
      HashMap<Integer, String> gestureIdToActionKeyMap = new HashMap<>();
      HashMap<String, GestureCollector> actionToGestureMap = new HashMap<>();
      gestureIdToActionKey.add(gestureIdToActionKeyMap);
      actionToGesture.add(actionToGestureMap);
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

        String keyId = getPrefKeyWithGestureSet(context.getString(gesture.keyId), index);
        String action = prefs.getString(keyId, context.getString(gesture.defaultActionId));
        // When diagnosis-mode is on, override a gesture to dump node-tree to logs.
        if ((gesture == TalkBackGesture.FOUR_FINGER_SINGLE_TAP)
            && PreferencesActivityUtils.isDiagnosisModeOn(prefs, context.getResources())) {
          action = context.getString(R.string.shortcut_value_print_node_tree);
        }

        GestureCollector gestureCollector;
        if (actionToGestureMap.containsKey(action)) {
          gestureCollector = actionToGestureMap.get(action);
        } else {
          gestureCollector = new GestureCollector();
        }

        // Check the action is default or customized action.
        if (TextUtils.equals(action, context.getString(gesture.defaultActionId))) {
          gestureCollector.addDefaultGesture(gesture);
        } else {
          gestureCollector.addCustomizedGesture(gesture);
        }

        actionToGestureMap.put(action, gestureCollector);

        // Load the mapping table of the gesture id to the action.
        if (gesture.gestureType == FINGERPRINT) {
          // Fingerprint gestures use another gesture id system.
          fingerprintGestureIdToActionKey.put(gesture.gestureId, action);
        } else {
          gestureIdToActionKeyMap.put(gesture.gestureId, action);
        }
      }

      // Non-customizable shortcut for SPLIT_TYPE
      gestureIdToActionKeyMap.put(
          GESTURE_FAKED_SPLIT_TYPING, context.getString(R.string.shortcut_value_split_typing));
      // Don't need to keep unassigned action in the map.
      actionToGestureMap.remove(actionUnassigned);
    }
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

  public void dump(Logger dumpLogger) {
    dumpLogger.log("Gesture mapping");
    for (Map.Entry<Integer, String> entry : gestureIdToActionKey.get(0).entrySet()) {
      dumpLogger.log(
          "Gesture = %s, action = %s", getGestureString(context, entry.getKey()), entry.getValue());
    }
  }

  /** Returns the corresponding action resource Id of action key. */
  public static String getActionString(Context context, String actionKeyString) {
    for (TalkbackAction action : TalkbackAction.values()) {
      if (action.actionKeyResId != -1
          && TextUtils.equals(context.getString(action.actionKeyResId), actionKeyString)) {
        return context.getString(action.actionNameResId);
      }
    }
    return context.getString(R.string.shortcut_unassigned);
  }

  /** Returns if the device supports multiple gesture set. */
  public static boolean isGestureSetEnabled(
      Context context, SharedPreferences prefs, int resKeyId, int defaultValue) {
    return FeatureSupport.supportMultipleGestureSet()
        && FeatureFlagReader.useMultipleGestureSet(context)
        && SharedPreferencesUtils.getBooleanPref(
            prefs, context.getResources(), resKeyId, defaultValue);
  }

  /** Returns derived preference key which is affixed with gesture set. */
  public static String getPrefKeyWithGestureSet(String key, int gestureSet) {
    if (gestureSet < 0 || gestureSet >= NUMBER_OF_GESTURE_SET) {
      gestureSet = 0;
    }
    String derivedKey = key;
    int splitIndex = key.indexOf("-");
    if (gestureSet == 0) {
      if (splitIndex != -1) {
        derivedKey = key.substring(0, splitIndex);
      }
    } else {
      if (splitIndex == -1) {
        derivedKey = key + "-" + gestureSet;
      } else {
        derivedKey = key.substring(0, splitIndex + 1) + gestureSet;
      }
    }
    return derivedKey;
  }

  /** Returns the corresponding TalkBack action null when undefined. */
  @Nullable
  public TalkbackAction getActionEvent(String actionKeyString) {
    for (TalkbackAction action : TalkbackAction.values()) {
      if (action.actionKeyResId != -1
          && TextUtils.equals(context.getString(action.actionKeyResId), actionKeyString)) {
        return action;
      }
    }
    return null;
  }

  /** Returns the corresponding gesture string of gesture id. */
  @Nullable
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
      case AccessibilityService.GESTURE_UNKNOWN:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_unknown)
            : null;
      case AccessibilityService.GESTURE_DOUBLE_TAP:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_double_tap)
            : null;
      case AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_double_tap_and_hold)
            : null;
      case AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_2finger_3tap_hold)
            : null;
      case AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_3finger_tap_hold)
            : null;
      case AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD:
        return FeatureSupport.supportGestureMotionEvents()
            ? context.getString(R.string.gesture_name_3finger_3tap_hold)
            : null;
      case GESTURE_FAKED_SPLIT_TYPING:
        return context.getString(R.string.shortcut_value_split_typing);
      default:
        return null;
    }
  }

  @Nullable
  public static String getFingerprintGestureString(Context context, int fingerprintGestureId) {
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

  /** Keeps different kind of gestures for a TalkBack action, and prioritizes gestures. */
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

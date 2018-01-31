/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.talkback.controller;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.FingerprintGestureController;
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.talkback.Analytics;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.MenuManager;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.TreeDebug;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.input.CursorController;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.output.FeedbackController;

/**
 * Class to handle incoming gestures to TalkBack. TODO: Remove Shortcut gesture action TODO:
 * Make sure tutorial still works TODO: Map actions to ints, and store in a map that changes
 * with prefs
 */
public class GestureControllerApp implements GestureController {
  private final TalkBackService mService;
  private final CursorController mCursorController;
  private final FeedbackController mFeedbackController;
  private final FullScreenReadController mFullScreenReadController;
  private final MenuManager mMenuManager;
  private final SelectorController mSelectorController;

  public GestureControllerApp(
      TalkBackService service,
      CursorController cursorController,
      FeedbackController feedbackController,
      FullScreenReadController fullScreenReadController,
      MenuManager menuManager,
      SelectorController selectorController) {
    if (cursorController == null) {
      throw new IllegalStateException();
    }
    if (feedbackController == null) {
      throw new IllegalStateException();
    }
    if (fullScreenReadController == null) {
      throw new IllegalStateException();
    }
    if (menuManager == null) {
      throw new IllegalStateException();
    }
    if (selectorController == null) {
      throw new IllegalStateException();
    }

    mCursorController = cursorController;
    mFeedbackController = feedbackController;
    mFullScreenReadController = fullScreenReadController;
    mMenuManager = menuManager;
    mService = service;
    mSelectorController = selectorController;
  }

  private String actionFromGesture(int gesture) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mService);
    switch (gesture) {
      case AccessibilityService.GESTURE_SWIPE_UP:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_up_key),
            mService.getString(R.string.pref_shortcut_up_default));
      case AccessibilityService.GESTURE_SWIPE_DOWN:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_down_key),
            mService.getString(R.string.pref_shortcut_down_default));
      case AccessibilityService.GESTURE_SWIPE_LEFT:
        if (WindowManager.isScreenLayoutRTL(mService)) {
          return prefs.getString(
              mService.getString(R.string.pref_shortcut_right_key),
              mService.getString(R.string.pref_shortcut_right_default));
        } else {
          return prefs.getString(
              mService.getString(R.string.pref_shortcut_left_key),
              mService.getString(R.string.pref_shortcut_left_default));
        }

      case AccessibilityService.GESTURE_SWIPE_RIGHT:
        if (WindowManager.isScreenLayoutRTL(mService)) {
          return prefs.getString(
              mService.getString(R.string.pref_shortcut_left_key),
              mService.getString(R.string.pref_shortcut_left_default));
        } else {
          return prefs.getString(
              mService.getString(R.string.pref_shortcut_right_key),
              mService.getString(R.string.pref_shortcut_right_default));
        }

      case AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN:
        {
          if (prefs.contains(mService.getString(R.string.pref_shortcut_up_and_down_key))) {
            return prefs.getString(
                mService.getString(R.string.pref_shortcut_up_and_down_key),
                mService.getString(R.string.pref_shortcut_up_and_down_default));
          }
          if (prefs.contains(mService.getString(R.string.pref_two_part_vertical_gestures_key))) {
            String pref =
                prefs.getString(
                    mService.getString(R.string.pref_two_part_vertical_gestures_key),
                    mService.getString(R.string.value_two_part_vertical_gestures_jump));
            if (pref.equals(mService.getString(R.string.value_two_part_vertical_gestures_jump))) {
              return mService.getString(R.string.shortcut_value_first_in_screen);
            }
            if (pref.equals(mService.getString(R.string.value_two_part_vertical_gestures_cycle))) {
              return mService.getString(R.string.shortcut_value_previous_granularity);
            }
          }
          return mService.getString(R.string.pref_shortcut_up_and_down_default);
        }
      case AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP:
        {
          if (prefs.contains(mService.getString(R.string.pref_shortcut_down_and_up_key))) {
            return prefs.getString(
                mService.getString(R.string.pref_shortcut_down_and_up_key),
                mService.getString(R.string.pref_shortcut_down_and_up_default));
          }
          if (prefs.contains(mService.getString(R.string.pref_two_part_vertical_gestures_key))) {
            String pref =
                prefs.getString(
                    mService.getString(R.string.pref_two_part_vertical_gestures_key),
                    mService.getString(R.string.value_two_part_vertical_gestures_jump));
            if (pref.equals(mService.getString(R.string.value_two_part_vertical_gestures_jump))) {
              return mService.getString(R.string.shortcut_value_last_in_screen);
            }
            if (pref.equals(mService.getString(R.string.value_two_part_vertical_gestures_cycle))) {
              return mService.getString(R.string.shortcut_value_next_granularity);
            }
          }
          return mService.getString(R.string.pref_shortcut_down_and_up_default);
        }
      case AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_left_and_right_key),
            mService.getString(R.string.pref_shortcut_left_and_right_default));
      case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_right_and_left_key),
            mService.getString(R.string.pref_shortcut_right_and_left_default));

      case AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_up_and_left_key),
            mService.getString(R.string.pref_shortcut_up_and_left_default));
      case AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_up_and_right_key),
            mService.getString(R.string.pref_shortcut_up_and_right_default));
      case AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_down_and_left_key),
            mService.getString(R.string.pref_shortcut_down_and_left_default));
      case AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_down_and_right_key),
            mService.getString(R.string.pref_shortcut_down_and_right_default));

      case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_right_and_down_key),
            mService.getString(R.string.pref_shortcut_right_and_down_default));
      case AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_right_and_up_key),
            mService.getString(R.string.pref_shortcut_right_and_up_default));
      case AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_left_and_down_key),
            mService.getString(R.string.pref_shortcut_left_and_down_default));
      case AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_left_and_up_key),
            mService.getString(R.string.pref_shortcut_left_and_up_default));

      default:
        return mService.getString(R.string.shortcut_value_unassigned);
    }
  }

  /**
   * Maps fingerprint gesture Id to TalkBack action.
   *
   * @param gesture Fingerprint gesture Id
   * @return Mapped action shortcut
   */
  @TargetApi(Build.VERSION_CODES.O)
  private String actionFromFingerprintGesture(int gesture) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mService);
    switch (gesture) {
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_fingerprint_up_key),
            mService.getString(R.string.pref_shortcut_fingerprint_up_default));
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_fingerprint_down_key),
            mService.getString(R.string.pref_shortcut_fingerprint_down_default));
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_LEFT:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_fingerprint_left_key),
            mService.getString(R.string.pref_shortcut_fingerprint_left_default));
      case FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_RIGHT:
        return prefs.getString(
            mService.getString(R.string.pref_shortcut_fingerprint_right_key),
            mService.getString(R.string.pref_shortcut_fingerprint_right_default));
      default:
        return mService.getString(R.string.shortcut_value_unassigned);
    }
  }

  @Override
  public void performAction(String action, EventId eventId) {
    if (action.equals(mService.getString(R.string.shortcut_value_unassigned))) {
      // Do Nothing
    } else if (action.equals(mService.getString(R.string.shortcut_value_previous))) {
      boolean result =
          mCursorController.previous(
              true /* shouldWrap */,
              true /* shouldScroll */,
              true /*useInputFocusAsPivotIfEmpty*/,
              InputModeManager.INPUT_MODE_TOUCH,
              eventId);
      if (!result) mFeedbackController.playAuditory(R.raw.complete);
    } else if (action.equals(mService.getString(R.string.shortcut_value_next))) {
      boolean result =
          mCursorController.next(
              true /* shouldWrap */,
              true /* shouldScroll */,
              true /*useInputFocusAsPivotIfEmpty*/,
              InputModeManager.INPUT_MODE_TOUCH,
              eventId);
      if (!result) mFeedbackController.playAuditory(R.raw.complete);
    } else if (action.equals(mService.getString(R.string.shortcut_value_scroll_back))) {
      boolean result = mCursorController.less(eventId);
      if (!result) mFeedbackController.playAuditory(R.raw.complete);
    } else if (action.equals(mService.getString(R.string.shortcut_value_scroll_forward))) {
      boolean result = mCursorController.more(eventId);
      if (!result) mFeedbackController.playAuditory(R.raw.complete);
    } else if (action.equals(mService.getString(R.string.shortcut_value_first_in_screen))) {
      boolean result = mCursorController.jumpToTop(InputModeManager.INPUT_MODE_TOUCH, eventId);
      if (!result) mFeedbackController.playAuditory(R.raw.complete);
    } else if (action.equals(mService.getString(R.string.shortcut_value_last_in_screen))) {
      boolean result = mCursorController.jumpToBottom(InputModeManager.INPUT_MODE_TOUCH, eventId);
      if (!result) mFeedbackController.playAuditory(R.raw.complete);
    } else if (action.equals(mService.getString(R.string.shortcut_value_back))) {
      mService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
    } else if (action.equals(mService.getString(R.string.shortcut_value_home))) {
      mService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
    } else if (action.equals(mService.getString(R.string.shortcut_value_overview))) {
      mService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
    } else if (action.equals(mService.getString(R.string.shortcut_value_notifications))) {
      mService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
    } else if (action.equals(mService.getString(R.string.shortcut_value_quick_settings))) {
      mService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS);
    } else if (action.equals(mService.getString(R.string.shortcut_value_talkback_breakout))) {
      mMenuManager.showMenu(R.menu.global_context_menu, eventId);
    } else if (action.equals(mService.getString(R.string.shortcut_value_local_breakout))) {
      mMenuManager.showMenu(R.menu.local_context_menu, eventId);
    } else if (action.equals(mService.getString(R.string.shortcut_value_show_custom_actions))) {
      mMenuManager.showMenu(R.id.custom_action_menu, eventId);
    } else if (action.equals(mService.getString(R.string.shortcut_value_editing))) {
      mMenuManager.showMenu(R.id.editing_menu, eventId);
    } else if (action.equals(mService.getString(R.string.shortcut_value_show_language_options))) {
      mMenuManager.showMenu(R.menu.language_menu, eventId);
    } else if (action.equals(mService.getString(R.string.shortcut_value_previous_granularity))) {
      boolean result = mCursorController.previousGranularity(eventId);
      if (result) {
        mService
            .getAnalytics()
            .onGranularityChanged(
                mCursorController.getCurrentGranularity(),
                Analytics.TYPE_GESTURE,
                /* isPending= */ true);
      } else {
        mFeedbackController.playAuditory(R.raw.complete);
      }
    } else if (action.equals(mService.getString(R.string.shortcut_value_next_granularity))) {
      boolean result = mCursorController.nextGranularity(eventId);
      if (result) {
        mService
            .getAnalytics()
            .onGranularityChanged(
                mCursorController.getCurrentGranularity(),
                Analytics.TYPE_GESTURE,
                /* isPending= */ true);
      } else {
        mFeedbackController.playAuditory(R.raw.complete);
      }
    } else if (action.equals(mService.getString(R.string.shortcut_value_read_from_top))) {
      mFullScreenReadController.startReadingFromBeginning(eventId);
    } else if (action.equals(mService.getString(R.string.shortcut_value_read_from_current))) {
      mFullScreenReadController.startReadingFromNextNode(eventId);
    } else if (action.equals(mService.getString(R.string.shortcut_value_print_node_tree))) {
      TreeDebug.logNodeTrees(mService.getWindows());
    } else if (action.equals(mService.getString(R.string.shortcut_value_print_performance_stats))) {
      Performance.getInstance().displayLabelToStats();
      Performance.getInstance().displayStatToLabelCompare();
      Performance.getInstance().displayAllEventStats();
    } else if (action.equals(mService.getString(R.string.shortcut_value_perform_click_action))) {
      AccessibilityNodeInfoCompat cursor = mCursorController.getCursor();
      PerformActionUtils.performAction(
          mCursorController.getCursor(), AccessibilityNodeInfo.ACTION_CLICK, eventId);
      AccessibilityNodeInfoUtils.recycleNodes(cursor);
    } else if (action.equals(mService.getString(R.string.shortcut_value_select_previous_setting))) {
      mSelectorController.selectPreviousOrNextSetting(eventId, false);
    } else if (action.equals(mService.getString(R.string.shortcut_value_select_next_setting))) {
      mSelectorController.selectPreviousOrNextSetting(eventId, true);
    } else if (action.equals(
        mService.getString(R.string.shortcut_value_selected_setting_previous_action))) {
      mSelectorController.performSelectedSettingAction(eventId, false);
    } else if (action.equals(
        mService.getString(R.string.shortcut_value_selected_setting_next_action))) {
      mSelectorController.performSelectedSettingAction(eventId, true);
    }
    Intent intent = new Intent(GestureActionMonitor.ACTION_GESTURE_ACTION_PERFORMED);
    intent.putExtra(GestureActionMonitor.EXTRA_SHORTCUT_GESTURE_ACTION, action);
    LocalBroadcastManager.getInstance(mService).sendBroadcast(intent);
  }

  @Override
  public boolean isFingerprintGestureAssigned(int fingerprintGestureId) {
    return !TextUtils.equals(
        mService.getString(R.string.shortcut_value_unassigned),
        actionFromFingerprintGesture(fingerprintGestureId));
  }

  @Override
  public void onGesture(int gestureId, EventId eventId) {
    String action = actionFromGesture(gestureId);
    performAction(action, eventId);
  }

  @Override
  public void onFingerprintGesture(int fingerprintGestureId, EventId eventId) {
    String action = actionFromFingerprintGesture(fingerprintGestureId);
    performAction(action, eventId);
  }


}




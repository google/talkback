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

package com.android.talkback.controller;

import com.android.utils.SharedPreferencesUtils;
import com.google.android.marvin.talkback.TalkBackService;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;

import com.android.talkback.InputModeManager;
import com.android.talkback.R;
import com.android.talkback.contextmenu.MenuManager;
import com.android.utils.TreeDebug;
import com.android.utils.compat.accessibilityservice.AccessibilityServiceCompatUtils;

/**
 * Class to handle incoming gestures to TalkBack.
 * TODO: Remove Shortcut gesture action
 * TODO: Make sure tutorial still works
 * TODO: Map action to string description of gesture
 * TODO: Map actions to ints, and store in a map that changes with prefs
 */
public class GestureControllerApp implements GestureController {
    private final TalkBackService mService;
    private final CursorController mCursorController;
    private final FeedbackController mFeedbackController;
    private final FullScreenReadController mFullScreenReadController;
    private final MenuManager mMenuManager;

    public GestureControllerApp(TalkBackService service,
                                CursorController cursorController,
                                FeedbackController feedbackController,
                                FullScreenReadController fullScreenReadController,
                                MenuManager menuManager) {
        if (cursorController == null) throw new IllegalStateException();
        if (feedbackController == null) throw new IllegalStateException();
        if (fullScreenReadController == null) throw new IllegalStateException();
        if (menuManager == null) throw new IllegalStateException();

        mCursorController = cursorController;
        mFeedbackController = feedbackController;
        mFullScreenReadController = fullScreenReadController;
        mMenuManager = menuManager;
        mService = service;
    }

    @Override
    public String gestureDescriptionFromAction(String action) {
        if (action == null) return null;

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_UP))) {
            return mService.getString(R.string.value_direction_up);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_DOWN))) {
            return mService.getString(R.string.value_direction_down);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_LEFT))) {
            return mService.getString(R.string.value_direction_left);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_RIGHT))) {
            return mService.getString(R.string.value_direction_right);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN))) {
            return mService.getString(R.string.value_direction_up_and_down);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP))) {
            return mService.getString(R.string.value_direction_down_and_up);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT))) {
            return mService.getString(R.string.value_direction_down_and_up);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT))) {
            return mService.getString(R.string.value_direction_right_and_left);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT))) {
            return mService.getString(R.string.value_direction_up_and_left);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT))) {
            return mService.getString(R.string.value_direction_up_and_right);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT))) {
            return mService.getString(R.string.value_direction_down_and_left);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT))) {
            return mService.getString(R.string.value_direction_down_and_right);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN))) {
            return mService.getString(R.string.value_direction_right_and_down);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP))) {
            return mService.getString(R.string.value_direction_right_and_up);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN))) {
            return mService.getString(R.string.value_direction_left_and_down);
        }

        if (action.equals(actionFromGesture(AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP))) {
            return mService.getString(R.string.value_direction_left_and_up);
        }

        return null;
    }

    @Override
    public String gestureFromAction(String action) {
        if (action == null) return null;

        if (action.equals(mService.getString(R.string.shortcut_value_unassigned)))
            return mService.getString(R.string.shortcut_unassigned);
        if (action.equals(mService.getString(R.string.shortcut_value_back)))
            return mService.getString(R.string.shortcut_back);
        if (action.equals(mService.getString(R.string.shortcut_value_home)))
            return mService.getString(R.string.shortcut_home);
        if (action.equals(mService.getString(R.string.shortcut_value_overview)))
            return mService.getString(R.string.shortcut_overview);
        if (action.equals(mService.getString(R.string.shortcut_value_notifications)))
            return mService.getString(R.string.shortcut_notifications);
        if (action.equals(mService.getString(R.string.shortcut_value_talkback_breakout)))
            return mService.getString(R.string.shortcut_talkback_breakout);
        if (action.equals(mService.getString(R.string.shortcut_value_local_breakout)))
            return mService.getString(R.string.shortcut_local_breakout);
        if (action.equals(mService.getString(R.string.shortcut_value_read_from_top)))
            return mService.getString(R.string.shortcut_read_from_top);
        if (action.equals(mService.getString(R.string.shortcut_value_read_from_current)))
            return mService.getString(R.string.shortcut_read_from_current);
        if (action.equals(mService.getString(R.string.shortcut_value_print_node_tree)))
            return mService.getString(R.string.shortcut_print_node_tree);

        return null;
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
                if (mService.isScreenLayoutRTL()) {
                    return prefs.getString(
                            mService.getString(R.string.pref_shortcut_right_key),
                            mService.getString(R.string.pref_shortcut_right_default));
                } else {
                    return prefs.getString(
                            mService.getString(R.string.pref_shortcut_left_key),
                            mService.getString(R.string.pref_shortcut_left_default));
                }

            case AccessibilityService.GESTURE_SWIPE_RIGHT:
                if (mService.isScreenLayoutRTL()) {
                    return prefs.getString(
                            mService.getString(R.string.pref_shortcut_left_key),
                            mService.getString(R.string.pref_shortcut_left_default));
                } else {
                    return prefs.getString(
                            mService.getString(R.string.pref_shortcut_right_key),
                            mService.getString(R.string.pref_shortcut_right_default));
                }

            case AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN: {
                if (prefs.contains(mService.getString(R.string.pref_shortcut_up_and_down_key))) {
                    return prefs.getString(
                            mService.getString(R.string.pref_shortcut_up_and_down_key),
                            mService.getString(R.string.pref_shortcut_up_and_down_default));
                }
                if (prefs.contains(mService.getString(
                        R.string.pref_two_part_vertical_gestures_key))) {
                    String pref = prefs.getString(
                            mService.getString(R.string.pref_two_part_vertical_gestures_key),
                            mService.getString(R.string.value_two_part_vertical_gestures_jump));
                    if (pref.equals(mService.getString(
                            R.string.value_two_part_vertical_gestures_jump))) {
                        return mService.getString(R.string.shortcut_value_first_in_screen);
                    }
                    if (pref.equals(mService.getString(
                            R.string.value_two_part_vertical_gestures_cycle))) {
                        return mService.getString(R.string.shortcut_value_previous_granularity);
                    }
                }
                return mService.getString(R.string.pref_shortcut_up_and_down_default);
            }
            case AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP: {
                if (prefs.contains(mService.getString(R.string.pref_shortcut_down_and_up_key))) {
                    return prefs.getString(
                            mService.getString(R.string.pref_shortcut_down_and_up_key),
                            mService.getString(R.string.pref_shortcut_down_and_up_default));
                }
                if (prefs.contains(mService.getString(
                        R.string.pref_two_part_vertical_gestures_key))) {
                    String pref = prefs.getString(
                            mService.getString(R.string.pref_two_part_vertical_gestures_key),
                            mService.getString(R.string.value_two_part_vertical_gestures_jump));
                    if (pref.equals(mService.getString(
                            R.string.value_two_part_vertical_gestures_jump))) {
                        return mService.getString(R.string.shortcut_value_last_in_screen);
                    }
                    if (pref.equals(mService.getString(
                            R.string.value_two_part_vertical_gestures_cycle))) {
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

    @Override
    public void performAction(String action) {
        if (action.equals(mService.getString(R.string.shortcut_value_unassigned))) {
          // Do Nothing
        } else if (action.equals(mService.getString(R.string.shortcut_value_previous))) {
            boolean result = mCursorController.previous(true /* shouldWrap */,
                    true /* shouldScroll */,
                    true /*useInputFocusAsPivotIfEmpty*/,
                    InputModeManager.INPUT_MODE_TOUCH);
            if (!result) mFeedbackController.playAuditory(R.raw.complete);
        } else if (action.equals(mService.getString(R.string.shortcut_value_next))) {
            boolean result = mCursorController.next(true /* shouldWrap */,
                    true /* shouldScroll */,
                    true /*useInputFocusAsPivotIfEmpty*/,
                    InputModeManager.INPUT_MODE_TOUCH);
            if (!result) mFeedbackController.playAuditory(R.raw.complete);
        } else if (action.equals(mService.getString(R.string.shortcut_value_scroll_back))) {
            boolean result = mCursorController.less();
            if (!result) mFeedbackController.playAuditory(R.raw.complete);
        } else if (action.equals(mService.getString(R.string.shortcut_value_scroll_forward))) {
            boolean result = mCursorController.more();
            if (!result) mFeedbackController.playAuditory(R.raw.complete);
        } else if (action.equals(mService.getString(R.string.shortcut_value_first_in_screen))) {
            boolean result = mCursorController.jumpToTop(InputModeManager.INPUT_MODE_TOUCH);
            if (!result) mFeedbackController.playAuditory(R.raw.complete);
        } else if (action.equals(mService.getString(R.string.shortcut_value_last_in_screen))) {
            boolean result = mCursorController.jumpToBottom(InputModeManager.INPUT_MODE_TOUCH);
            if (!result) mFeedbackController.playAuditory(R.raw.complete);
        } else if (action.equals(mService.getString(R.string.shortcut_value_back))) {
            mService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        } else if (action.equals(mService.getString(R.string.shortcut_value_home))) {
            mService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
        } else if (action.equals(mService.getString(R.string.shortcut_value_overview))) {
            mService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS);
        } else if (action.equals(mService.getString(R.string.shortcut_value_notifications))) {
            mService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS);
        } else if (action.equals(mService.getString(R.string.shortcut_value_talkback_breakout))) {
            mMenuManager.showMenu(R.menu.global_context_menu);
        } else if (action.equals(mService.getString(R.string.shortcut_value_local_breakout))) {
            mMenuManager.showMenu(R.menu.local_context_menu);
        } else if (action.equals(mService.getString(R.string.shortcut_value_show_custom_actions))) {
            mMenuManager.showMenu(R.id.custom_action_menu);
        } else if (action.equals(mService.getString(R.string.shortcut_value_previous_granularity))) {
            boolean result = mCursorController.previousGranularity();
            if (!result) mFeedbackController.playAuditory(R.raw.complete);
        } else if (action.equals(mService.getString(R.string.shortcut_value_next_granularity))) {
            boolean result = mCursorController.nextGranularity();
            if (!result) mFeedbackController.playAuditory(R.raw.complete);
        } else if (action.equals(mService.getString(R.string.shortcut_value_read_from_top))) {
            mFullScreenReadController.startReadingFromBeginning();
        } else if (action.equals(mService.getString(R.string.shortcut_value_read_from_current))) {
            mFullScreenReadController.startReadingFromNextNode();
        } else if (action.equals(mService.getString(R.string.shortcut_value_print_node_tree))) {
            TreeDebug.logNodeTree(AccessibilityServiceCompatUtils.getRootInActiveWindow(mService));
        }

        Intent intent = new Intent(GestureActionMonitor.ACTION_GESTURE_ACTION_PERFORMED);
        intent.putExtra(GestureActionMonitor.EXTRA_SHORTCUT_GESTURE_ACTION, action);
        LocalBroadcastManager.getInstance(mService).sendBroadcast(intent);
    }

    @Override
    public void onGesture(int gestureId) {
        String action = actionFromGesture(gestureId);
        performAction(action);
    }
}

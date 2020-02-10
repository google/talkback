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

package com.google.android.accessibility.talkback;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import com.google.android.accessibility.compositor.GestureShortcutProvider;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashMap;
import java.util.Iterator;

/**
 * The class provides gesture and action mappings in TalkBack for quick access. It updates cache
 * mappings whenever preference or screen layout changed.
 */
public class GestureShortcutMapping implements GestureShortcutProvider {
  private static final String TAG = GestureShortcutMapping.class.getSimpleName();
  protected static final int GESTURE_UNKNOWN = 0;
  protected static String actionUnassigned;
  protected static String actionLocalContextMenu;

  private Context context;
  private SharedPreferences prefs;
  private int previousScreenLayout = 0;
  private HashMap<Integer, String> gestureIdToActionKey = new HashMap<>();

  /** Reloads preferences whenever their values change. */
  private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
          loadGestureIdToActionKeyMap();
        }
      };

  public GestureShortcutMapping(Context context) {
    this.context = context;
    actionUnassigned = context.getString(R.string.shortcut_value_unassigned);
    actionLocalContextMenu = context.getString(R.string.shortcut_value_local_breakout);
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

  /** Returns gesture shortcut name for local context menu. */
  @Override
  public @Nullable CharSequence nodeMenuShortcut() {
    return getGestureString(getGestureIdFromActionKey(actionLocalContextMenu));
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
   * Gets first corresponding gesture id from gesture-action mappings.
   *
   * @param action The corresponding action assigned to the gesture
   * @return gesture id
   */
  public int getGestureIdFromActionKey(String action) {
    if (TextUtils.isEmpty(action)) {
      return GESTURE_UNKNOWN;
    }
    Iterator<Integer> gestureIds = gestureIdToActionKey.keySet().iterator();
    while (gestureIds.hasNext()) {
      int gestureId = gestureIds.next();
      String cachedAction = gestureIdToActionKey.get(gestureId);
      if (action.equals(cachedAction)) {
        return gestureId;
      }
    }
    return GESTURE_UNKNOWN;
  }

  /** Loads gesture-action mappings from shared preference. */
  private void loadGestureIdToActionKeyMap() {
    LogUtils.d(TAG, "loadGestureIdToActionKeyMap");
    gestureIdToActionKey.clear();

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_UP,
        prefs.getString(
            context.getString(R.string.pref_shortcut_up_key),
            context.getString(R.string.pref_shortcut_up_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_DOWN,
        prefs.getString(
            context.getString(R.string.pref_shortcut_down_key),
            context.getString(R.string.pref_shortcut_down_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_LEFT,
        (WindowManager.isScreenLayoutRTL(context))
            ? prefs.getString(
                context.getString(R.string.pref_shortcut_right_key),
                context.getString(R.string.pref_shortcut_right_default))
            : prefs.getString(
                context.getString(R.string.pref_shortcut_left_key),
                context.getString(R.string.pref_shortcut_left_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_RIGHT,
        (WindowManager.isScreenLayoutRTL(context))
            ? prefs.getString(
                context.getString(R.string.pref_shortcut_left_key),
                context.getString(R.string.pref_shortcut_left_default))
            : prefs.getString(
                context.getString(R.string.pref_shortcut_right_key),
                context.getString(R.string.pref_shortcut_right_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN,
        prefs.getString(
            context.getString(R.string.pref_shortcut_up_and_down_key),
            context.getString(R.string.pref_shortcut_up_and_down_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP,
        prefs.getString(
            context.getString(R.string.pref_shortcut_down_and_up_key),
            context.getString(R.string.pref_shortcut_down_and_up_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT,
        (WindowManager.isScreenLayoutRTL(context))
            ? prefs.getString(
                context.getString(R.string.pref_shortcut_right_and_left_key),
                context.getString(R.string.pref_shortcut_right_and_left_default))
            : prefs.getString(
                context.getString(R.string.pref_shortcut_left_and_right_key),
                context.getString(R.string.pref_shortcut_left_and_right_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT,
        (WindowManager.isScreenLayoutRTL(context))
            ? prefs.getString(
                context.getString(R.string.pref_shortcut_left_and_right_key),
                context.getString(R.string.pref_shortcut_left_and_right_default))
            : prefs.getString(
                context.getString(R.string.pref_shortcut_right_and_left_key),
                context.getString(R.string.pref_shortcut_right_and_left_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT,
        prefs.getString(
            context.getString(R.string.pref_shortcut_up_and_left_key),
            context.getString(R.string.pref_shortcut_up_and_left_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT,
        prefs.getString(
            context.getString(R.string.pref_shortcut_up_and_right_key),
            context.getString(R.string.pref_shortcut_up_and_right_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT,
        prefs.getString(
            context.getString(R.string.pref_shortcut_down_and_left_key),
            context.getString(R.string.pref_shortcut_down_and_left_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT,
        prefs.getString(
            context.getString(R.string.pref_shortcut_down_and_right_key),
            context.getString(R.string.pref_shortcut_down_and_right_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN,
        prefs.getString(
            context.getString(R.string.pref_shortcut_right_and_down_key),
            context.getString(R.string.pref_shortcut_right_and_down_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP,
        prefs.getString(
            context.getString(R.string.pref_shortcut_right_and_up_key),
            context.getString(R.string.pref_shortcut_right_and_up_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN,
        prefs.getString(
            context.getString(R.string.pref_shortcut_left_and_down_key),
            context.getString(R.string.pref_shortcut_left_and_down_default)));

    gestureIdToActionKey.put(
        AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP,
        prefs.getString(
            context.getString(R.string.pref_shortcut_left_and_up_key),
            context.getString(R.string.pref_shortcut_left_and_up_default)));
  }

  private String getGestureString(int gestureId) {
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
      default:
        return null;
    }
  }
}

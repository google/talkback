/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.google.android.accessibility.talkback.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.FullScreenReadActor;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.selector.SelectorController;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.ServiceStateListener;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manages state related to detecting key combinations.
 *
 * <p>TODO: move KeyComboManager under package talkback.keyboard.
 */
public class KeyComboManager implements ServiceKeyEventListener, ServiceStateListener {

  public static final int NO_MATCH = -1;
  public static final int PARTIAL_MATCH = 1;
  public static final int EXACT_MATCH = 2;

  public static final int ACTION_UNKNOWN = -1;
  public static final int ACTION_NAVIGATE_NEXT = 1;
  public static final int ACTION_NAVIGATE_PREVIOUS = 2;
  public static final int ACTION_NAVIGATE_FIRST = 3;
  public static final int ACTION_NAVIGATE_LAST = 4;
  public static final int ACTION_PERFORM_CLICK = 5;
  public static final int ACTION_BACK = 6;
  public static final int ACTION_HOME = 7;
  public static final int ACTION_RECENTS = 8;
  public static final int ACTION_NOTIFICATION = 9;
  public static final int ACTION_GRANULARITY_INCREASE = 11;
  public static final int ACTION_GRANULARITY_DECREASE = 12;
  public static final int ACTION_READ_FROM_TOP = 13;
  public static final int ACTION_READ_FROM_NEXT_ITEM = 14;
  public static final int ACTION_TOGGLE_SEARCH = 15;

  public static final int ACTION_TALKBACK_CONTEXT_MENU = 17;
  public static final int ACTION_NAVIGATE_UP = 18;
  public static final int ACTION_NAVIGATE_DOWN = 19;
  public static final int ACTION_NAVIGATE_NEXT_WORD = 20;
  public static final int ACTION_NAVIGATE_PREVIOUS_WORD = 21;
  public static final int ACTION_NAVIGATE_NEXT_CHARACTER = 22;
  public static final int ACTION_NAVIGATE_PREVIOUS_CHARACTER = 23;
  public static final int ACTION_PERFORM_LONG_CLICK = 24;
  public static final int ACTION_NAVIGATE_NEXT_HEADING = 25;
  public static final int ACTION_NAVIGATE_PREVIOUS_HEADING = 26;
  public static final int ACTION_NAVIGATE_NEXT_BUTTON = 27;
  public static final int ACTION_NAVIGATE_PREVIOUS_BUTTON = 28;
  public static final int ACTION_NAVIGATE_NEXT_CHECKBOX = 29;
  public static final int ACTION_NAVIGATE_PREVIOUS_CHECKBOX = 30;
  public static final int ACTION_NAVIGATE_NEXT_ARIA_LANDMARK = 31;
  public static final int ACTION_NAVIGATE_PREVIOUS_ARIA_LANDMARK = 32;
  public static final int ACTION_NAVIGATE_NEXT_EDIT_FIELD = 33;
  public static final int ACTION_NAVIGATE_PREVIOUS_EDIT_FIELD = 34;
  public static final int ACTION_NAVIGATE_NEXT_FOCUSABLE_ITEM = 35;
  public static final int ACTION_NAVIGATE_PREVIOUS_FOCUSABLE_ITEM = 36;
  public static final int ACTION_NAVIGATE_NEXT_HEADING_1 = 37;
  public static final int ACTION_NAVIGATE_PREVIOUS_HEADING_1 = 38;
  public static final int ACTION_NAVIGATE_NEXT_HEADING_2 = 39;
  public static final int ACTION_NAVIGATE_PREVIOUS_HEADING_2 = 40;
  public static final int ACTION_NAVIGATE_NEXT_HEADING_3 = 41;
  public static final int ACTION_NAVIGATE_PREVIOUS_HEADING_3 = 42;
  public static final int ACTION_NAVIGATE_NEXT_HEADING_4 = 43;
  public static final int ACTION_NAVIGATE_PREVIOUS_HEADING_4 = 44;
  public static final int ACTION_NAVIGATE_NEXT_HEADING_5 = 45;
  public static final int ACTION_NAVIGATE_PREVIOUS_HEADING_5 = 46;
  public static final int ACTION_NAVIGATE_NEXT_HEADING_6 = 47;
  public static final int ACTION_NAVIGATE_PREVIOUS_HEADING_6 = 48;
  public static final int ACTION_NAVIGATE_NEXT_LINK = 49;
  public static final int ACTION_NAVIGATE_PREVIOUS_LINK = 50;
  public static final int ACTION_NAVIGATE_NEXT_CONTROL = 51;
  public static final int ACTION_NAVIGATE_PREVIOUS_CONTROL = 52;
  public static final int ACTION_NAVIGATE_NEXT_GRAPHIC = 53;
  public static final int ACTION_NAVIGATE_PREVIOUS_GRAPHIC = 54;
  public static final int ACTION_NAVIGATE_NEXT_LIST_ITEM = 55;
  public static final int ACTION_NAVIGATE_PREVIOUS_LIST_ITEM = 56;
  public static final int ACTION_NAVIGATE_NEXT_LIST = 57;
  public static final int ACTION_NAVIGATE_PREVIOUS_LIST = 58;
  public static final int ACTION_NAVIGATE_NEXT_TABLE = 59;
  public static final int ACTION_NAVIGATE_PREVIOUS_TABLE = 60;
  public static final int ACTION_NAVIGATE_NEXT_COMBOBOX = 61;
  public static final int ACTION_NAVIGATE_PREVIOUS_COMBOBOX = 62;
  public static final int ACTION_NAVIGATE_NEXT_WINDOW = 63;
  public static final int ACTION_NAVIGATE_PREVIOUS_WINDOW = 64;
  public static final int ACTION_OPEN_MANAGE_KEYBOARD_SHORTCUTS = 65;
  public static final int ACTION_OPEN_TALKBACK_SETTINGS = 66;
  public static final int ACTION_CUSTOM_ACTIONS = 67;
  public static final int ACTION_NAVIGATE_NEXT_DEFAULT = 68;
  public static final int ACTION_NAVIGATE_PREVIOUS_DEFAULT = 69;
  public static final int ACTION_LANGUAGE_OPTIONS = 70;
  public static final int ACTION_PLAY_PAUSE_MEDIA = 71;
  public static final int ACTION_SCROLL_FORWARD_READING_MENU = 72;
  public static final int ACTION_SCROLL_BACKWARD_READING_MENU = 73;
  public static final int ACTION_ADJUST_READING_SETTING_PREVIOUS = 74;
  public static final int ACTION_ADJUST_READING_SETTING_NEXT = 75;

  private static final int KEY_EVENT_MODIFIER_MASK =
      KeyEvent.META_SHIFT_ON | KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | KeyEvent.META_META_ON;

  public static final String CONCATENATION_STR = " + ";
  private static final String KEYCODE_PREFIX = "KEYCODE_";

  /** When user has pressed same key twice less than this interval, we handle them as double tap. */
  private static final long TIME_TO_DETECT_DOUBLE_TAP = 1000; // ms

  private static final int DEFAULT_KEYMAP = R.string.default_keymap_entry_value;

  /** Returns keyComboCode that represent keyEvent. */
  public static long getKeyComboCode(KeyEvent keyEvent) {
    if (keyEvent == null) {
      return KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
    }

    int modifier = keyEvent.getModifiers() & KEY_EVENT_MODIFIER_MASK;
    return getKeyComboCode(modifier, getConvertedKeyCode(keyEvent));
  }

  /** Returns key combo code which is combination of modifier and keycode. */
  public static long getKeyComboCode(int modifier, int keycode) {
    return (((long) modifier) << 32) + keycode;
  }

  /** Returns modifier part of key combo code. */
  public static int getModifier(long keyComboCode) {
    return (int) (keyComboCode >> 32);
  }

  /** Returns key code part of key combo code. */
  public static int getKeyCode(long keyComboCode) {
    return (int) (keyComboCode);
  }

  /**
   * Returns converted key code. This method converts the following key events. - Convert
   * KEYCODE_HOME with meta to KEYCODE_ENTER. - Convert KEYCODE_BACK with meta to KEYCODE_DEL.
   *
   * @param event Key event to be converted.
   * @return Converted key code.
   */
  static int getConvertedKeyCode(KeyEvent event) {
    // We care only when meta key is pressed with.
    if ((event.getModifiers() & KeyEvent.META_META_ON) == 0) {
      return event.getKeyCode();
    }

    if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
      return KeyEvent.KEYCODE_ENTER;
    } else if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
      return KeyEvent.KEYCODE_DEL;
    } else {
      return event.getKeyCode();
    }
  }

  private final boolean isArc;

  /** Whether the user performed a combo during the current interaction. */
  private boolean performedCombo;

  /** Whether the user may be performing a combo and we should intercept keys. */
  private boolean hasPartialMatch;

  private Set<Integer> currentKeysDown = new HashSet<>();
  private Set<Integer> passedKeys = new HashSet<>();

  private long currentKeyComboCode = KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
  private long currentKeyComboTime = 0;
  private long previousKeyComboCode = KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
  private long previousKeyComboTime = 0;

  private Context context;
  private boolean matchKeyCombo = true;
  private KeyComboModel keyComboModel;
  private int serviceState = SERVICE_STATE_INACTIVE;
  private ServiceKeyEventListener keyEventDelegate;
  private KeyComboMapper keyComboMapper;

  public static KeyComboManager create(Context context) {
    return new KeyComboManager(context);
  }

  private KeyComboManager(Context context) {
    this.context = context;
    isArc = FeatureSupport.isArc();
    initializeDefaultPreferenceValues();
    keyComboModel = createKeyComboModel();
  }

  // TODO: KeyComboManager would be separated into KeyComboManager
  // and KeyComboModelManager
  public KeyComboManager(
      Context context,
      Pipeline.FeedbackReturner pipeline,
      SelectorController selectorController,
      ListMenuManager menuManager,
      FullScreenReadActor fullScreenReadActor) {
    this(context);
    keyComboMapper =
        new KeyComboMapper(context, pipeline, selectorController, menuManager, fullScreenReadActor);
  }

  /** Store default values in preferences to show them in preferences UI. */
  private void initializeDefaultPreferenceValues() {
    SharedPreferences preferences = SharedPreferencesUtils.getSharedPreferences(context);
    if (preferences.contains(context.getString(R.string.pref_select_keymap_key))) {
      return;
    }

    preferences
        .edit()
        .putString(
            context.getString(R.string.pref_select_keymap_key), context.getString(DEFAULT_KEYMAP))
        .apply();
  }

  /**
   * Sets delegate for key events. If it's set, it can listen and consume key events before
   * KeyComboManager does. Sets null to remove current one.
   */
  public void setKeyEventDelegate(ServiceKeyEventListener delegate) {
    keyEventDelegate = delegate;
  }

  /**
   * Returns keymap by reading preference.
   *
   * @return key map. Returns default key map if can not find key.
   */
  public String getKeymap() {
    SharedPreferences preferences = SharedPreferencesUtils.getSharedPreferences(context);
    return preferences.getString(
        context.getString(R.string.pref_select_keymap_key), context.getString(DEFAULT_KEYMAP));
  }

  /**
   * Creates key combo model by keymap key.
   *
   * @return Key combo model. null will be returned if keymap is invalid.
   */
  @Nullable
  private KeyComboModel createKeyComboModel() {
    String keymap = getKeymap();
    if (keymap.equals(context.getString(R.string.classic_keymap_entry_value))) {
      return new KeyComboModelApp(context);
    } else if (keymap.equals(context.getString(R.string.default_keymap_entry_value))) {
      return new DefaultKeyComboModel(context);
    }
    return null;
  }

  /** Refreshes key combo model after key map changes. */
  public void refreshKeyComboModel() {
    keyComboModel = createKeyComboModel();
  }

  /** Returns modifier part of key combo code. */
  public KeyComboModel getKeyComboModel() {
    return keyComboModel;
  }

  /**
   * Returns corresponding action id to key. If invalid value is passed as key, ACTION_UNKNOWN will
   * be returned.
   */
  private int getActionIdFromKey(String key) {
    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next))) {
      return ACTION_NAVIGATE_NEXT;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous))) {
      return ACTION_NAVIGATE_PREVIOUS;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_default))) {
      return ACTION_NAVIGATE_NEXT_DEFAULT;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_default))) {
      return ACTION_NAVIGATE_PREVIOUS_DEFAULT;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_first))) {
      return ACTION_NAVIGATE_FIRST;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_last))) {
      return ACTION_NAVIGATE_LAST;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_perform_click))) {
      return ACTION_PERFORM_CLICK;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_back))) {
      return ACTION_BACK;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_home))) {
      return ACTION_HOME;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_recents))) {
      return ACTION_RECENTS;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_notifications))) {
      return ACTION_NOTIFICATION;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_global_play_pause_media))) {
      return ACTION_PLAY_PAUSE_MEDIA;
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_scroll_forward_reading_menu))) {
      return ACTION_SCROLL_FORWARD_READING_MENU;
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_scroll_backward_reading_menu))) {
      return ACTION_SCROLL_BACKWARD_READING_MENU;
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_adjust_reading_settings_previous))) {
      return ACTION_ADJUST_READING_SETTING_PREVIOUS;
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_global_adjust_reading_setting_next))) {
      return ACTION_ADJUST_READING_SETTING_NEXT;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_granularity_increase))) {
      return ACTION_GRANULARITY_INCREASE;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_granularity_decrease))) {
      return ACTION_GRANULARITY_DECREASE;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_read_from_top))) {
      return ACTION_READ_FROM_TOP;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_read_from_next_item))) {
      return ACTION_READ_FROM_NEXT_ITEM;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_toggle_search))) {
      return ACTION_TOGGLE_SEARCH;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_talkback_context_menu))) {
      return ACTION_TALKBACK_CONTEXT_MENU;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_custom_actions))) {
      return ACTION_CUSTOM_ACTIONS;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_other_language_options))) {
      return ACTION_LANGUAGE_OPTIONS;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_up))) {
      return ACTION_NAVIGATE_UP;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_down))) {
      return ACTION_NAVIGATE_DOWN;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_word))) {
      return ACTION_NAVIGATE_NEXT_WORD;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_word))) {
      return ACTION_NAVIGATE_PREVIOUS_WORD;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_character))) {
      return ACTION_NAVIGATE_NEXT_CHARACTER;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_character))) {
      return ACTION_NAVIGATE_PREVIOUS_CHARACTER;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_perform_long_click))) {
      return ACTION_PERFORM_LONG_CLICK;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading))) {
      return ACTION_NAVIGATE_NEXT_HEADING;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading))) {
      return ACTION_NAVIGATE_PREVIOUS_HEADING;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_button))) {
      return ACTION_NAVIGATE_NEXT_BUTTON;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_button))) {
      return ACTION_NAVIGATE_PREVIOUS_BUTTON;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_checkbox))) {
      return ACTION_NAVIGATE_NEXT_CHECKBOX;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_checkbox))) {
      return ACTION_NAVIGATE_PREVIOUS_CHECKBOX;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_aria_landmark))) {
      return ACTION_NAVIGATE_NEXT_ARIA_LANDMARK;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_aria_landmark))) {
      return ACTION_NAVIGATE_PREVIOUS_ARIA_LANDMARK;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_edit_field))) {
      return ACTION_NAVIGATE_NEXT_EDIT_FIELD;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_edit_field))) {
      return ACTION_NAVIGATE_PREVIOUS_EDIT_FIELD;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_focusable_item))) {
      return ACTION_NAVIGATE_NEXT_FOCUSABLE_ITEM;
    }

    if (key.equals(
        context.getString(R.string.keycombo_shortcut_navigate_previous_focusable_item))) {
      return ACTION_NAVIGATE_PREVIOUS_FOCUSABLE_ITEM;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_1))) {
      return ACTION_NAVIGATE_NEXT_HEADING_1;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_1))) {
      return ACTION_NAVIGATE_PREVIOUS_HEADING_1;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_2))) {
      return ACTION_NAVIGATE_NEXT_HEADING_2;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_2))) {
      return ACTION_NAVIGATE_PREVIOUS_HEADING_2;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_3))) {
      return ACTION_NAVIGATE_NEXT_HEADING_3;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_3))) {
      return ACTION_NAVIGATE_PREVIOUS_HEADING_3;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_4))) {
      return ACTION_NAVIGATE_NEXT_HEADING_4;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_4))) {
      return ACTION_NAVIGATE_PREVIOUS_HEADING_4;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_5))) {
      return ACTION_NAVIGATE_NEXT_HEADING_5;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_5))) {
      return ACTION_NAVIGATE_PREVIOUS_HEADING_5;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_heading_6))) {
      return ACTION_NAVIGATE_NEXT_HEADING_6;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_heading_6))) {
      return ACTION_NAVIGATE_PREVIOUS_HEADING_6;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_link))) {
      return ACTION_NAVIGATE_NEXT_LINK;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_link))) {
      return ACTION_NAVIGATE_PREVIOUS_LINK;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_control))) {
      return ACTION_NAVIGATE_NEXT_CONTROL;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_control))) {
      return ACTION_NAVIGATE_PREVIOUS_CONTROL;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_graphic))) {
      return ACTION_NAVIGATE_NEXT_GRAPHIC;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_graphic))) {
      return ACTION_NAVIGATE_PREVIOUS_GRAPHIC;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_list_item))) {
      return ACTION_NAVIGATE_NEXT_LIST_ITEM;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_list_item))) {
      return ACTION_NAVIGATE_PREVIOUS_LIST_ITEM;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_list))) {
      return ACTION_NAVIGATE_NEXT_LIST;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_list))) {
      return ACTION_NAVIGATE_PREVIOUS_LIST;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_table))) {
      return ACTION_NAVIGATE_NEXT_TABLE;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_table))) {
      return ACTION_NAVIGATE_PREVIOUS_TABLE;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_combobox))) {
      return ACTION_NAVIGATE_NEXT_COMBOBOX;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_combobox))) {
      return ACTION_NAVIGATE_PREVIOUS_COMBOBOX;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_next_window))) {
      return ACTION_NAVIGATE_NEXT_WINDOW;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_navigate_previous_window))) {
      return ACTION_NAVIGATE_PREVIOUS_WINDOW;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_open_manage_keyboard_shortcuts))) {
      return ACTION_OPEN_MANAGE_KEYBOARD_SHORTCUTS;
    }

    if (key.equals(context.getString(R.string.keycombo_shortcut_open_talkback_settings))) {
      return ACTION_OPEN_TALKBACK_SETTINGS;
    }

    return ACTION_UNKNOWN;
  }

  /** Set whether to process keycombo */
  public void setMatchKeyCombo(boolean value) {
    matchKeyCombo = value;
  }

  /** Returns user friendly string representations of key combo code */
  public String getKeyComboStringRepresentation(long keyComboCode) {
    if (keyComboCode == KeyComboModel.KEY_COMBO_CODE_UNASSIGNED) {
      return context.getString(R.string.keycombo_unassigned);
    }

    int triggerModifier = keyComboModel.getTriggerModifier();
    int modifier = getModifier(keyComboCode);
    int modifierWithoutTriggerModifier = modifier & ~triggerModifier;
    int keyCode = getKeyCode(keyComboCode);

    StringBuilder sb = new StringBuilder();

    // Append trigger modifier if key combo code contains it.
    if ((triggerModifier & modifier) != 0) {
      appendModifiers(triggerModifier, sb);
    }

    // Append modifier except trigger modifier.
    appendModifiers(modifierWithoutTriggerModifier, sb);

    // Append key code.
    if (keyCode > 0 && !KeyEvent.isModifierKey(keyCode)) {
      appendPlusSignIfNotEmpty(sb);

      switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          sb.append(context.getString(R.string.keycombo_key_arrow_right));
          break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
          sb.append(context.getString(R.string.keycombo_key_arrow_left));
          break;
        case KeyEvent.KEYCODE_DPAD_UP:
          sb.append(context.getString(R.string.keycombo_key_arrow_up));
          break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          sb.append(context.getString(R.string.keycombo_key_arrow_down));
          break;
        default:
          String keyCodeString = KeyEvent.keyCodeToString(keyCode);
          if (keyCodeString != null) {
            String keyCodeNoPrefix;
            if (keyCodeString.startsWith(KEYCODE_PREFIX)) {
              keyCodeNoPrefix = keyCodeString.substring(KEYCODE_PREFIX.length());
            } else {
              keyCodeNoPrefix = keyCodeString;
            }
            sb.append(keyCodeNoPrefix.replace('_', ' '));
          }
          break;
      }
    }

    return sb.toString();
  }

  /** Appends modifier. */
  private void appendModifiers(int modifier, StringBuilder sb) {
    appendModifier(
        modifier, KeyEvent.META_ALT_ON, context.getString(R.string.keycombo_key_modifier_alt), sb);
    appendModifier(
        modifier,
        KeyEvent.META_SHIFT_ON,
        context.getString(R.string.keycombo_key_modifier_shift),
        sb);
    appendModifier(
        modifier,
        KeyEvent.META_CTRL_ON,
        context.getString(R.string.keycombo_key_modifier_ctrl),
        sb);
    appendModifier(
        modifier,
        KeyEvent.META_META_ON,
        context.getString(R.string.keycombo_key_modifier_meta),
        sb);
  }

  /** Appends string representation of target modifier if modifier contains it. */
  private void appendModifier(
      int modifier, int targetModifier, String stringRepresentation, StringBuilder sb) {
    if ((modifier & targetModifier) != 0) {
      appendPlusSignIfNotEmpty(sb);
      sb.append(stringRepresentation);
    }
  }

  private void appendPlusSignIfNotEmpty(StringBuilder sb) {
    if (sb.length() > 0) {
      sb.append(CONCATENATION_STR);
    }
  }

  /**
   * Handles incoming key events. May intercept keys if the user seems to be performing a key combo.
   *
   * @param event The key event.
   * @return {@code true} if the key was intercepted.
   */
  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    if (keyEventDelegate != null) {
      if (keyEventDelegate.onKeyEvent(event, eventId)) {
        return true;
      }
    }

    if (!hasPartialMatch && !performedCombo && !matchKeyCombo) {
      return false;
    }

    switch (event.getAction()) {
      case KeyEvent.ACTION_DOWN:
        return onKeyDown(event);
      case KeyEvent.ACTION_MULTIPLE:
        return hasPartialMatch;
      case KeyEvent.ACTION_UP:
        return onKeyUp(event);
      default:
        return false;
    }
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return true;
  }

  private KeyEvent convertKeyEventInArc(KeyEvent event) {
    switch (event.getKeyCode()) {
      case KeyEvent.KEYCODE_HOME:
      case KeyEvent.KEYCODE_BACK:
        // In Arc, Search + X is sent as KEYCODE_X with META_META_ON in Android. Android
        // converts META_META_ON + KEYCODE_ENTER and META_META_ON + KEYCODE_DEL to
        // KEYCODE_HOME and KEYCODE_BACK without META_META_ON. We add META_META_ON to this
        // key event to satisfy trigger modifier condition. We don't need to do this in
        // non-Arc since Search + X is usually sent as KEYCODE_X with META_META_ON and
        // META_META_LEFT_ON or META_META_RIGHT_ON.
        return new KeyEvent(
            event.getDownTime(),
            event.getEventTime(),
            event.getAction(),
            event.getKeyCode(),
            event.getRepeatCount(),
            event.getMetaState() | KeyEvent.META_META_ON);
      default:
        return event;
    }
  }

  private boolean onKeyDown(KeyEvent event) {
    if (isArc) {
      event = convertKeyEventInArc(event);
    }

    currentKeysDown.add(event.getKeyCode());
    currentKeyComboCode = getKeyComboCode(event);
    currentKeyComboTime = event.getDownTime();

    // Check modifier.
    int triggerModifier = keyComboModel.getTriggerModifier();
    boolean hasModifier = triggerModifier != KeyComboModel.NO_MODIFIER;
    if (hasModifier && (triggerModifier & event.getModifiers()) != triggerModifier) {
      // Do nothing if condition of modifier is not met.
      passedKeys.addAll(currentKeysDown);
      return false;
    }

    boolean isServiceActive = (serviceState == SERVICE_STATE_ACTIVE);

    // If the current set of keys is a partial combo, consume the event.
    hasPartialMatch = false;

    for (Map.Entry<String, Long> entry : keyComboModel.getKeyComboCodeMap().entrySet()) {
      if (!isServiceActive) {
        continue;
      }

      final int match = matchKeyEventWith(event, triggerModifier, entry.getValue());
      if (match == EXACT_MATCH) {
        int comboId = getActionIdFromKey(entry.getKey());
        String comboName = getKeyComboStringRepresentation(comboId);
        EventId eventId = Performance.getInstance().onKeyComboEventReceived(comboId);
        // Checks interrupt events if matches key combos. To prevent interrupting actions generated
        // by key combos, we should send interrupt events
        // before performing key combos.
        interrupt(comboId);

        if (keyComboMapper.performKeyComboAction(comboId, comboName, eventId)) {
          performedCombo = true;
        }
        return true;
      }

      if (match == PARTIAL_MATCH) {
        hasPartialMatch = true;
      }
    }

    // Do not handle key event if user has pressed search key (meta key) twice to open search
    // app.
    if (hasModifier && triggerModifier == KeyEvent.META_META_ON) {
      if (previousKeyComboCode == currentKeyComboCode
          && currentKeyComboTime - previousKeyComboTime < TIME_TO_DETECT_DOUBLE_TAP
          && (currentKeyComboCode
                  == KeyComboManager.getKeyComboCode(
                      KeyEvent.META_META_ON, KeyEvent.KEYCODE_META_RIGHT)
              || currentKeyComboCode
                  == KeyComboManager.getKeyComboCode(
                      KeyEvent.META_META_ON, KeyEvent.KEYCODE_META_LEFT))) {
        // Set KEY_COMBO_CODE_UNASSIGNED not to open search app again with following search
        // key event.
        currentKeyComboCode = KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
        passedKeys.addAll(currentKeysDown);
        return false;
      }
    }

    if (!hasPartialMatch) {
      passedKeys.addAll(currentKeysDown);
    }
    return hasPartialMatch;
  }

  private int matchKeyEventWith(KeyEvent event, int triggerModifier, long keyComboCode) {
    int keyCode = getConvertedKeyCode(event);
    int metaState = event.getModifiers() & KEY_EVENT_MODIFIER_MASK;

    int targetKeyCode = getKeyCode(keyComboCode);
    int targetMetaState = getModifier(keyComboCode) | triggerModifier;

    // Handle exact matches first.
    if (metaState == targetMetaState && keyCode == targetKeyCode) {
      return EXACT_MATCH;
    }

    if (targetMetaState != 0 && metaState == 0) {
      return NO_MATCH;
    }

    // Otherwise, all modifiers must be down.
    if (KeyEvent.isModifierKey(keyCode)
        && targetMetaState != 0
        && (targetMetaState & metaState) != 0) {
      // Partial match.
      return PARTIAL_MATCH;
    }

    // No match.
    return NO_MATCH;
  }

  /**
   * Notifies the {@link KeyComboMapper} whether should interrupt or not by checking the ActionId.
   *
   * @param performedActionId the ActionId generating from key combos.
   */
  void interrupt(int performedActionId) {
    if (keyComboMapper != null) {
      keyComboMapper.interruptByKeyCombo(performedActionId);
    }
  }

  private boolean onKeyUp(KeyEvent event) {
    if (isArc) {
      event = convertKeyEventInArc(event);
    }

    currentKeysDown.remove(event.getKeyCode());
    boolean passed = passedKeys.remove(event.getKeyCode());

    if (currentKeysDown.isEmpty()) {
      // Checks interrupt events if no key combos performed in the interaction.
      if (!performedCombo) {
        interrupt(ACTION_UNKNOWN);
      }
      // The interaction is over, reset the state.
      performedCombo = false;
      hasPartialMatch = false;
      previousKeyComboCode = currentKeyComboCode;
      previousKeyComboTime = currentKeyComboTime;
      currentKeyComboCode = KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
      currentKeyComboTime = 0;
      passedKeys.clear();
    }

    return !passed;
  }

  @Override
  public void onServiceStateChanged(int newState) {
    // Unfortunately, key events are lost when the TalkBackService becomes active. If a key-down
    // occurs that triggers TalkBack to resume, the corresponding key-up event will not be
    // sent, causing the partially-matched key history to become inconsistent.
    // The following method will cause the key history to be reset.
    setMatchKeyCombo(matchKeyCombo);


    serviceState = newState;
  }
}

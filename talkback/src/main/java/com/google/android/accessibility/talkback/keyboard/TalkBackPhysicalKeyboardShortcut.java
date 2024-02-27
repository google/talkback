package com.google.android.accessibility.talkback.keyboard;

import android.content.res.Resources;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import java.util.HashMap;
import java.util.Map;

/** Keyboard shortcut wrapper wraps the key and KeyboardShortcut ordinal together. */
public enum TalkBackPhysicalKeyboardShortcut {
  ACTION_UNKNOWN(/* KeyboardShortcut.KEYBOARD_SHORTCUT_UNKNOWN */ 75, -1),
  NAVIGATE_NEXT(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_ITEM */ 0,
      R.string.keycombo_shortcut_navigate_next),
  NAVIGATE_PREVIOUS(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_ITEM */ 1,
      R.string.keycombo_shortcut_navigate_previous),
  NAVIGATE_ABOVE(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_ABOVE_ITEM */ 2,
      R.string.keycombo_shortcut_navigate_up),
  NAVIGATE_BELOW(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_BELOW_ITEM*/ 3,
      R.string.keycombo_shortcut_navigate_down),
  NAVIGATE_FIRST(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_FIRST_ITEM */ 4,
      R.string.keycombo_shortcut_navigate_first),
  NAVIGATE_LAST(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_LAST_ITEM */ 5,
      R.string.keycombo_shortcut_navigate_last),
  NAVIGATE_NEXT_WINDOW(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_WINDOW */ 6,
      R.string.keycombo_shortcut_navigate_next_window),
  NAVIGATE_PREVIOUS_WINDOW(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_WINDOW */ 7,
      R.string.keycombo_shortcut_navigate_previous_window),
  NAVIGATE_NEXT_WORD(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_WORD */ 8,
      R.string.keycombo_shortcut_navigate_next_word),
  NAVIGATE_PREVIOUS_WORD(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_WORD */ 9,
      R.string.keycombo_shortcut_navigate_previous_word),
  NAVIGATE_NEXT_CHARACTER(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_CHARACTER */ 10,
      R.string.keycombo_shortcut_navigate_next_character),
  NAVIGATE_PREVIOUS_CHARACTER(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_CHARACTER */ 11,
      R.string.keycombo_shortcut_navigate_previous_character),
  PERFORM_CLICK(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_PERFORM_CLICK */ 12,
      R.string.keycombo_shortcut_perform_click),
  PERFORM_LONG_CLICK(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_PERFORM_LONG_CLICK */ 13,
      R.string.keycombo_shortcut_perform_long_click),
  // Global actions
  BACK(/* KeyboardShortcut.KEYBOARD_SHORTCUT_BACK */ 14, R.string.keycombo_shortcut_global_back),
  HOME(/* KeyboardShortcut.KEYBOARD_SHORTCUT_HOME */ 15, R.string.keycombo_shortcut_global_home),
  RECENT_APPS(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_RECENT_APPS */ 16,
      R.string.keycombo_shortcut_global_recents),
  NOTIFICATIONS(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NOTIFICATIONS */ 17,
      R.string.keycombo_shortcut_global_notifications),
  PAUSE_OR_RESUME_TALKBACK(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_PAUSE_OR_RESUME_TALKBACK */ 18, -1 /* ?? */),
  // Other actions
  READ_FROM_TOP(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_READ_FROM_TOP */ 19,
      R.string.keycombo_shortcut_other_read_from_top),
  READ_FROM_NEXT_ITEM(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_READ_FROM_NEXT_ITEM */ 20,
      R.string.keycombo_shortcut_other_read_from_next_item),
  SHOW_GLOBAL_CONTEXT_MENU(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_SHOW_GLOBAL_CONTEXT_MENU */ 21,
      R.string.keycombo_shortcut_other_talkback_context_menu),
  SHOW_LOCAL_CONTEXT_MENU(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_SHOW_LOCAL_CONTEXT_MENU */ 22, -1 /* ?? */),
  SHOW_ACTIONS(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_SHOW_ACTIONS */ 23,
      R.string.keycombo_shortcut_other_custom_actions),
  SHOW_LANGUAGES_AVAILABLE(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_SHOW_LANGUAGES_AVAILABLE */ 24,
      R.string.keycombo_shortcut_other_language_options),
  SEARCH_SCREEN_FOR_ITEM(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_SEARCH_SCREEN_FOR_ITEM */ 25,
      R.string.keycombo_shortcut_other_toggle_search),
  // Web navigation actions
  NAVIGATE_NEXT_BUTTON(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_BUTTON */ 26,
      R.string.keycombo_shortcut_navigate_next_button),
  NAVIGATE_PREVIOUS_BUTTON(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_BUTTON */ 27,
      R.string.keycombo_shortcut_navigate_previous_button),
  NAVIGATE_NEXT_CONTROL(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_CONTROL */ 28,
      R.string.keycombo_shortcut_navigate_next_control),
  NAVIGATE_PREVIOUS_CONTROL(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_CONTROL */ 29,
      R.string.keycombo_shortcut_navigate_previous_control),
  NAVIGATE_NEXT_ARIA_LANDMARK(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_ARIA_LANDMARK */ 30,
      R.string.keycombo_shortcut_navigate_next_aria_landmark),
  NAVIGATE_PREVIOUS_ARIA_LANDMARK(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_ARIA_LANDMARK */ 31,
      R.string.keycombo_shortcut_navigate_previous_aria_landmark),
  NAVIGATE_NEXT_EDIT_FIELD(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_EDIT_FIELD */ 32,
      R.string.keycombo_shortcut_navigate_next_edit_field),
  NAVIGATE_PREVIOUS_EDIT_FIELD(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_EDIT_FIELD */ 33,
      R.string.keycombo_shortcut_navigate_previous_edit_field),
  NAVIGATE_NEXT_FOCUSABLE_ITEM(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_FOCUSABLE_ITEM */ 34,
      R.string.keycombo_shortcut_navigate_next_focusable_item),
  NAVIGATE_PREVIOUS_FOCUSABLE_ITEM(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_FOCUSABLE_ITEM */ 35,
      R.string.keycombo_shortcut_navigate_previous_focusable_item),
  NAVIGATE_NEXT_GRAPHIC(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_GRAPHIC */ 36,
      R.string.keycombo_shortcut_navigate_next_graphic),
  NAVIGATE_PREVIOUS_GRAPHIC(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_GRAPHIC */ 37,
      R.string.keycombo_shortcut_navigate_previous_graphic),
  NAVIGATE_NEXT_HEADING(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_HEADING */ 38,
      R.string.keycombo_shortcut_navigate_next_heading),
  NAVIGATE_PREVIOUS_HEADING(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_HEADING */ 39,
      R.string.keycombo_shortcut_navigate_previous_heading),
  NAVIGATE_NEXT_HEADING_1(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_HEADING_1 */ 40,
      R.string.keycombo_shortcut_navigate_next_heading_1),
  NAVIGATE_PREVIOUS_HEADING_1(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_HEADING_1 */ 41,
      R.string.keycombo_shortcut_navigate_previous_heading_1),
  NAVIGATE_NEXT_HEADING_2(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_HEADING_2 */ 42,
      R.string.keycombo_shortcut_navigate_next_heading_2),
  NAVIGATE_PREVIOUS_HEADING_2(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_HEADING_2 */ 43,
      R.string.keycombo_shortcut_navigate_previous_heading_2),
  NAVIGATE_NEXT_HEADING_3(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_HEADING_3 */ 44,
      R.string.keycombo_shortcut_navigate_next_heading_3),
  NAVIGATE_PREVIOUS_HEADING_3(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_HEADING_3 */ 45,
      R.string.keycombo_shortcut_navigate_previous_heading_3),
  NAVIGATE_NEXT_HEADING_4(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_HEADING_4 */ 46,
      R.string.keycombo_shortcut_navigate_next_heading_4),
  NAVIGATE_PREVIOUS_HEADING_4(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_HEADING_4 */ 47,
      R.string.keycombo_shortcut_navigate_previous_heading_4),
  NAVIGATE_NEXT_HEADING_5(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_HEADING_5 */ 48,
      R.string.keycombo_shortcut_navigate_next_heading_5),
  NAVIGATE_PREVIOUS_HEADING_5(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_HEADING_5 */ 49,
      R.string.keycombo_shortcut_navigate_previous_heading_5),
  NAVIGATE_NEXT_HEADING_6(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_HEADING_6 */ 50,
      R.string.keycombo_shortcut_navigate_next_heading_6),
  NAVIGATE_PREVIOUS_HEADING_6(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_HEADING_6 */ 51,
      R.string.keycombo_shortcut_navigate_previous_heading_6),
  NAVIGATE_NEXT_LIST_ITEM(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_LIST_ITEM */ 52,
      R.string.keycombo_shortcut_navigate_next_list_item),
  NAVIGATE_PREVIOUS_LIST_ITEM(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_LIST_ITEM */ 53,
      R.string.keycombo_shortcut_navigate_previous_list_item),
  NAVIGATE_NEXT_LINK(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_LINK */ 54,
      R.string.keycombo_shortcut_navigate_next_link),
  NAVIGATE_PREVIOUS_LINK(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_LINK */ 55,
      R.string.keycombo_shortcut_navigate_previous_link),
  NAVIGATE_NEXT_LIST(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_LIST */ 56,
      R.string.keycombo_shortcut_navigate_next_list),
  NAVIGATE_PREVIOUS_LIST(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_LIST */ 57,
      R.string.keycombo_shortcut_navigate_previous_list),
  NAVIGATE_NEXT_TABLE(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_TABLE */ 58,
      R.string.keycombo_shortcut_navigate_next_table),
  NAVIGATE_PREVIOUS_TABLE(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_TABLE */ 59,
      R.string.keycombo_shortcut_navigate_previous_table),
  NAVIGATE_NEXT_COMBOBOX(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_COMBOBOX */ 60,
      R.string.keycombo_shortcut_navigate_next_combobox),
  NAVIGATE_PREVIOUS_COMBOBOX(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_COMBOBOX */ 61,
      R.string.keycombo_shortcut_navigate_previous_combobox),
  NAVIGATE_NEXT_CHECKBOX(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_CHECKBOX */ 62,
      R.string.keycombo_shortcut_navigate_next_checkbox),
  NAVIGATE_PREVIOUS_CHECKBOX(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_CHECKBOX */ 63,
      R.string.keycombo_shortcut_navigate_previous_checkbox),
  // Navigation in classic keymap.
  NEXT_NAVIGATION_SETTING(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NEXT_NAVIGATION_SETTING */ 64,
      R.string.keycombo_shortcut_granularity_increase),
  PREVIOUS_NAVIGATION_SETTING(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_PREVIOUS_NAVIGATION_SETTING */ 65,
      R.string.keycombo_shortcut_granularity_decrease),
  // Shortcuts on Arc device only.
  OPEN_MANAGE_KEYBOARD_SHORTCUTS(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_OPEN_MANAGE_KEYBOARD_SHORTCUTS */ 66,
      -1 /* ?? */), // Deprecated
  OPEN_TALKBACK_SETTINGS(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_OPEN_TALKBACK_SETTINGS */ 67,
      -1 /* ?? */), // Deprecated
  // Navigation in default keymap.
  NAVIGATE_NEXT_DEFAULT(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_NEXT_ITEM_DEFAULT */ 68,
      R.string.keycombo_shortcut_navigate_next_default),
  NAVIGATE_PREVIOUS_DEFAULT(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_NAVIGATE_TO_PREVIOUS_ITEM_DEFAULT */ 69,
      R.string.keycombo_shortcut_navigate_previous_default),
  // Global shortcut
  PLAY_PAUSE_MEDIA(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_PLAY_PAUSE_MEDIA */ 70,
      R.string.keycombo_shortcut_global_play_pause_media),
  SELECT_NEXT_READING_MENU(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_SELECT_NEXT_READING_MENU */ 71,
      R.string.keycombo_shortcut_global_scroll_forward_reading_menu),
  SELECT_PREVIOUS_READING_MENU(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_SELECT_PREVIOUS_READING_MENU */ 72,
      R.string.keycombo_shortcut_global_scroll_backward_reading_menu),
  ADJUST_READING_MENU_UP(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_ADJUST_READING_MENU_UP */ 73,
      R.string.keycombo_shortcut_global_adjust_reading_settings_previous),
  ADJUST_READING_MENU_DOWN(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_ADJUST_READING_MENU_DOWN */ 74,
      R.string.keycombo_shortcut_global_adjust_reading_setting_next),
  COPY_LAST_SPOKEN_PHRASE(
      /* KeyboardShortcut.KEYBOARD_SHORTCUT_COPY_LAST_SPOKEN_PHRASE */ 76,
      R.string.keycombo_shortcut_other_copy_last_spoken_phrase),
  HIDE_OR_SHOW_SCREEN(
      /* KEYBOARD_SHORTCUT_HIDE_OR_SHOW_SCREEN */ 77,
      R.string.keycombo_shortcut_global_hide_or_show_screen),
  ;

  private static Map<String, TalkBackPhysicalKeyboardShortcut> keyShortcutMap;
  private static Map<Integer, TalkBackPhysicalKeyboardShortcut> ordinalShortcutMap;
  private final int keyboardShortcutOrdinal;
  @StringRes private final int keyStrRes;

  TalkBackPhysicalKeyboardShortcut(int keyboardShortcutOrdinal, @StringRes int descriptionStrRes) {
    this.keyboardShortcutOrdinal = keyboardShortcutOrdinal;
    this.keyStrRes = descriptionStrRes;
  }

  public int getKeyboardShortcutOrdinal() {
    return keyboardShortcutOrdinal;
  }

  public String getKey(Resources resources) {
    if (keyStrRes == -1) {
      return "";
    }
    return resources.getString(keyStrRes);
  }

  /** Obtains {@link TalkBackPhysicalKeyboardShortcut} from key. */
  public static TalkBackPhysicalKeyboardShortcut getActionFromKey(Resources resources, String key) {
    if (keyShortcutMap == null) {
      keyShortcutMap = new HashMap<>();
      for (TalkBackPhysicalKeyboardShortcut talkBackPhysicalKeyboardShortcut :
          TalkBackPhysicalKeyboardShortcut.values()) {
        keyShortcutMap.put(
            talkBackPhysicalKeyboardShortcut.getKey(resources), talkBackPhysicalKeyboardShortcut);
      }
    }
    return nullToUnknown(keyShortcutMap.get(key));
  }

  /** Obtains {@link TalkBackPhysicalKeyboardShortcut} from keyboard shortcut ordinal. */
  public static TalkBackPhysicalKeyboardShortcut getActionFromKeyboardShortcutOrdinal(
      int keyboardShortcutOrdinal) {
    if (ordinalShortcutMap == null) {
      ordinalShortcutMap = new HashMap<>();
      for (TalkBackPhysicalKeyboardShortcut talkBackPhysicalKeyboardShortcut :
          TalkBackPhysicalKeyboardShortcut.values()) {
        ordinalShortcutMap.put(
            talkBackPhysicalKeyboardShortcut.keyboardShortcutOrdinal,
            talkBackPhysicalKeyboardShortcut);
      }
    }
    return nullToUnknown(ordinalShortcutMap.get(keyboardShortcutOrdinal));
  }

  private static TalkBackPhysicalKeyboardShortcut nullToUnknown(
      TalkBackPhysicalKeyboardShortcut shortcut) {
    return shortcut == null ? ACTION_UNKNOWN : shortcut;
  }
}

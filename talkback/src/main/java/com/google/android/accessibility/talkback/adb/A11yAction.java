package com.google.android.accessibility.talkback.adb;

import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.selector.SelectorController;

enum A11yAction {
    UNASSIGNED(R.string.shortcut_value_unassigned),
    PREVIOUS(R.string.shortcut_value_previous),
    NEXT(R.string.shortcut_value_next),
    BACK(R.string.shortcut_value_back),
    FIRST_IN_SCREEN(R.string.shortcut_value_first_in_screen),
    SUMMARY(R.string.shortcut_value_summary),
    VOICE_COMMANDS(R.string.shortcut_value_voice_commands),
    LAST_IN_SCREEN(R.string.shortcut_value_last_in_screen),
    NEXT_GRANULARITY(R.string.shortcut_value_next_granularity),
    PREVIOUS_GRANULARITY(R.string.shortcut_value_previous_granularity),
    PREVIOUS_WINDOW(R.string.shortcut_value_previous_window),
    NEXT_WINDOW(R.string.shortcut_value_next_window),
    SCROLL_BACK(R.string.shortcut_value_scroll_back),
    SCROLL_FORWARD(R.string.shortcut_value_scroll_forward),
    SCROLL_UP(R.string.shortcut_value_scroll_up),
    SCROLL_DOWN(R.string.shortcut_value_scroll_down),
    SCROLL_LEFT(R.string.shortcut_value_scroll_left),
    SCROLL_RIGHT(R.string.shortcut_value_scroll_right),
    HOME(R.string.shortcut_value_home),
    OVERVIEW(R.string.shortcut_value_overview),
    NOTIFICATIONS(R.string.shortcut_value_notifications),
    QUICK_SETTINGS(R.string.shortcut_value_quick_settings),
    SHOW_CUSTOM_ACTIONS(R.string.shortcut_value_show_custom_actions),
    EDITING(R.string.shortcut_value_editing),
    TALKBACK_BREAKOUT(R.string.shortcut_value_talkback_breakout),
    LOCAL_BREAKOUT(R.string.shortcut_value_local_breakout),
    READ_FROM_TOP(R.string.shortcut_value_read_from_top),
    READ_FROM_CURRENT(R.string.shortcut_value_read_from_current),
    PERFORM_CLICK_ACTION(R.string.shortcut_value_perform_click_action),
    PERFORM_LONG_CLICK_ACTION(R.string.shortcut_value_perform_long_click_action),
    PRINT_NODE_TREE(R.string.shortcut_value_print_node_tree),
    PRINT_PERFORMANCE_STATS(R.string.shortcut_value_print_performance_stats),
    SHOW_LANGUAGE_OPTIONS(R.string.shortcut_value_show_language_options),
    SELECT_NEXT_SETTING(R.string.shortcut_value_select_next_setting),
    SELECT_PREVIOUS_SETTING(R.string.shortcut_value_select_previous_setting),
    SELECTED_SETTING_NEXT_ACTION(R.string.shortcut_value_selected_setting_next_action),
    SELECTED_SETTING_PREVIOUS_ACTION(R.string.shortcut_value_selected_setting_previous_action),
    SCREEN_SEARCH(R.string.shortcut_value_screen_search),
    MEDIA_CONTROL(R.string.shortcut_value_media_control),
    ENABLE_PASS_THROUGH(R.string.shortcut_value_pass_through_next_gesture),
    ACCESSIBILITY_BUTTON(R.string.shortcut_value_a11y_button),
    ACCESSIBILITY_BUTTON_CHOOSER(R.string.shortcut_value_a11y_button_long_press),
    START_SELECTION_MODE(R.string.shortcut_value_start_selection_mode),
    MOVE_CURSOR_TO_BEGINNING(R.string.shortcut_value_move_cursor_to_beginning),
    MOVE_CURSOR_TO_END(R.string.shortcut_value_move_cursor_to_end),
    TOGGLE_VOICE_FEEDBACK(R.string.shortcut_value_toggle_voice_feedback),
    SELECT_ALL(R.string.shortcut_value_select_all),
    COPY(R.string.shortcut_value_copy),
    CUT(R.string.shortcut_value_cut),
    PASTE(R.string.shortcut_value_paste),
    COPY_LAST_SPOKEN_UTTERANCE(R.string.shortcut_value_copy_last_spoken_phrase),
    PAUSE_FEEDBACK(R.string.shortcut_value_pause_or_resume_feedback),
    ALL_APPS(R.string.shortcut_value_all_apps),
    BRAILLE_KEYBOARD(R.string.shortcut_value_braille_keyboard),
    TUTORIAL(R.string.shortcut_value_tutorial),
    GESTURE_DEBUG(R.string.shortcut_value_report_gesture),
    PRACTICE_GESTURES(R.string.shortcut_value_practice_gestures);

    private A11yAction(int gestureMappingReference) {
        this.gestureMappingReference = gestureMappingReference;
    }

    public int gestureMappingReference;

    public static final String granularityParameter = "mode";

    public static SelectorController.Granularity granularityFrom(String name) {
        for(SelectorController.Granularity granularity : SelectorController.Granularity.values()) {
            if (name.toLowerCase().equals(granularity.name().toLowerCase())) {
                return granularity;
            }
        }
        return SelectorController.Granularity.DEFAULT;
    }
}

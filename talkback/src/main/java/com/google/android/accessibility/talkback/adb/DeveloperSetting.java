package com.google.android.accessibility.talkback.adb;

import com.google.android.accessibility.talkback.R;

enum DeveloperSetting {
    // developer_preferences.xml
    UNKNOWN(-1, -1),
    TOGGLE_SPEECH_OUTPUT(R.string.pref_tts_overlay_key, R.bool.pref_tts_overlay_default),
    BLOCK_OUT(R.string.pref_tb4d_block_overlay_key, R.bool.pref_tb4d_overlay_block_default),
    ECHO_RECOGNIZED_SPEECH(R.string.pref_echo_recognized_text_speech_key, R.bool.pref_echo_recognized_text_default),
    REDUCE_WINDOW_ANNOUNCEMENT_DELAY(R.string.pref_reduce_window_delay_key, R.bool.pref_reduce_window_delay_default),
    ENABLE_PERFORMANCE_STATISTICS(R.string.pref_performance_stats_key, R.bool.pref_performance_stats_default),
    EXPLORE_BY_TOUCH(R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default),
    NODE_TREE_DEBUGGING(R.string.title_pref_tree_debug, R.bool.pref_tree_debug_default);


    private DeveloperSetting(int keyId, int defaultKey) {
        this.keyId = keyId;
        this.defaultKey = defaultKey;
    }

    public int keyId;
    public int defaultKey;

    public static DeveloperSetting fromString(String name) {
        for (DeveloperSetting setting: DeveloperSetting.values()) {
            if (setting.name().equalsIgnoreCase(name)) {
                return setting;
            }
        }
        return UNKNOWN;
    }
}
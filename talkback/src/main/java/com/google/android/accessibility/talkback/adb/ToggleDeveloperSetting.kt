package com.google.android.accessibility.talkback.adb

import android.content.Context
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.utils.SharedPreferencesUtils

object ToggleDeveloperSetting {
    private fun Context.preferences() = SharedPreferencesUtils.getSharedPreferences(this)

    operator fun invoke(context: Context, developerSetting: DeveloperSetting) {
        if (developerSetting == DeveloperSetting.UNKNOWN) return
        val pref = SharedPreferencesUtils.getBooleanPref(
                context.preferences(),
                context.resources,
                developerSetting.keyId,
                developerSetting.defaultKey)
                .not()
        SharedPreferencesUtils.putBooleanPref(
                context.preferences(),
                context.resources,
                developerSetting.keyId,
                pref
        )
    }

    // developer_preferences.xml
    enum class DeveloperSetting(val keyId: Int, val defaultKey: Int) {
        UNKNOWN(-1,-1),
        TOGGLE_SPEECH_OUTPUT(R.string.pref_tts_overlay_key, R.bool.pref_tts_overlay_default),
        ECHO_RECOGNIZED_SPEECH(R.string.pref_echo_recognized_text_speech_key, R.bool.pref_echo_recognized_text_default),
        REDUCE_WINDOW_ANNOUNCEMENT_DELAY(R.string.pref_reduce_window_delay_key, R.bool.pref_reduce_window_delay_default),
        ENABLE_PERFORMANCE_STATISTICS(R.string.pref_performance_stats_key, R.bool.pref_performance_stats_default),
        EXPLORE_BY_TOUCH(R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default),
        NODE_TREE_DEBUGGING(R.string.title_pref_tree_debug, R.bool.pref_tree_debug_default);

        companion object {
            fun fromString(string: String): DeveloperSetting =
                    values().firstOrNull { it.name.lowercase() == string.lowercase() } ?: UNKNOWN
        }
    }
}
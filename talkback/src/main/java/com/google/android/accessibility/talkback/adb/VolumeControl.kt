package com.google.android.accessibility.talkback.adb

enum class VolumeControl {
    NONE,
    VOLUME_MIN,
    VOLUME_QUARTER,
    VOLUME_HALF,
    VOLUME_THREE_QUARTER,
    VOLUME_MAX,
    VOLUME_TOGGLE;

    companion object {
        fun fromString(string: String): VolumeControl =
            values().firstOrNull { it.name.lowercase() == string.lowercase() } ?: NONE
    }
}
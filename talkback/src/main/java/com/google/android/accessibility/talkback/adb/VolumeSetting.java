package com.google.android.accessibility.talkback.adb;

enum VolumeSetting {
    NONE,
    VOLUME_MIN,
    VOLUME_QUARTER,
    VOLUME_HALF,
    VOLUME_THREE_QUARTER,
    VOLUME_MAX,
    VOLUME_TOGGLE;

    public static VolumeSetting fromString(String name) {
        for (VolumeSetting setting: VolumeSetting.values()) {
            if (setting.name().equalsIgnoreCase(name)) {
                return setting;
            }
        }
        return NONE;
    }
}
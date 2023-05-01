package com.google.android.accessibility.talkback.adb;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;

import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

import java.lang.ref.WeakReference;

/**
 * A [BroadcastReceiver] that interprets command line instructions and forwards them to
 * Google TalkBack.
 *
 * GENERAL USAGE:
 * adb shell am broadcast -a com.a11y.adb.[A11yAction] [-e mode [SelectorController.Granularity]]
 * adb shell am broadcast -a com.a11y.adb.[DeveloperSetting]
 * adb shell am broadcast -a com.a11y.adb.[VolumeSetting]
 *
 * EXAMPLES
 * -- A11yAction --
 * adb shell am broadcast -a com.a11y.adb.next
 * adb shell am broadcast -a com.a11y.adb.next -e mode headings
 * adb shell am broadcast -a com.a11y.adb.perform_click_action
 *
 * -- DeveloperSetting --
 * adb shell am broadcast -a com.a11y.adb.toggle_speech_output
 *
 * -- VolumeSetting --
 * adb shell am broadcast -a com.a11y.adb.volume_max
 * adb shell am broadcast -a com.a11y.adb.volume_min
 */
public class AdbReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) return;
        TalkBackService talkBackServiceInstance = TalkBackService.getInstance();
        if (talkBackServiceInstance == null) return;

        String action = intent.getAction().replace(IntentActionPrefix + ".", "").toLowerCase();

        if (performA11yAction(context, action, intent, talkBackServiceInstance)) return;
        if (setDeveloperSetting(context, action, intent, talkBackServiceInstance)) return;
        if (toggleDeveloperSetting(context, action, intent, talkBackServiceInstance)) return;
        if (setAccessibilityVolume(context, action, intent, talkBackServiceInstance)) return;

        Log.tb4d(String.format("INVALID ACTION: %s", action));
    }

    private boolean performA11yAction(Context context,
                                      String action,
                                      Intent intent,
                                      TalkBackService talkBackServiceInstance) {
        for (A11yAction a11yAction : A11yAction.values()) {
            if (a11yAction.name().toLowerCase().equals(action)) {
                if (a11yAction == A11yAction.PREVIOUS || a11yAction == A11yAction.NEXT) {
                    String granularityParam = "default";
                    if (intent.hasExtra(A11yAction.granularityParameter)) {
                        String granularityParamExtra = intent.getStringExtra(A11yAction.granularityParameter);
                        if (granularityParamExtra != null && granularityParamExtra.trim().length() > 0) {
                            granularityParam = granularityParamExtra;
                        }
                    }

                    talkBackServiceInstance.moveAtGranularity(
                            A11yAction.granularityFrom(granularityParam),
                            a11yAction == A11yAction.NEXT
                    );
                } else {
                    talkBackServiceInstance.performGesture(context.getString(a11yAction.gestureMappingReference));
                }
                return true;
            }
        }
        return false;
    }

    private boolean setDeveloperSetting(Context context,
                                           String action,
                                           Intent intent,
                                           TalkBackService talkBackServiceInstance) {
        DeveloperSetting developerSetting = DeveloperSetting.fromString(action);
        if (developerSetting == DeveloperSetting.UNKNOWN) return false;
        if (!intent.hasExtra(DeveloperSetting.valueParameter)) return false;
        Boolean settingValue = Boolean.parseBoolean(intent.getStringExtra(DeveloperSetting.valueParameter));

        SharedPreferences preferences = SharedPreferencesUtils.getSharedPreferences(context);
        Resources resources = context.getResources();
        SharedPreferencesUtils.putBooleanPref(
                preferences,
                resources,
                developerSetting.keyId,
                settingValue
        );
        return true;
    }

    private boolean toggleDeveloperSetting(Context context,
                                           String action,
                                           Intent intent,
                                           TalkBackService talkBackServiceInstance) {
        DeveloperSetting developerSetting = DeveloperSetting.fromString(action);
        if (developerSetting == DeveloperSetting.UNKNOWN) return false;

        SharedPreferences preferences = SharedPreferencesUtils.getSharedPreferences(context);
        Resources resources = context.getResources();
        boolean prefValue = !SharedPreferencesUtils.getBooleanPref(
                preferences,
                resources,
                developerSetting.keyId,
                developerSetting.defaultKey);
        SharedPreferencesUtils.putBooleanPref(
                preferences,
                resources,
                developerSetting.keyId,
                prefValue
        );

        return true;
    }

    private boolean setAccessibilityVolume(Context context,
                                           String action,
                                           Intent intent,
                                           TalkBackService talkBackServiceInstance) {
        VolumeSetting volumeControl = VolumeSetting.fromString(action);
        int stream = AudioManager.STREAM_ACCESSIBILITY;
        AudioManager audioManager = (AudioManager) context.getSystemService(Service.AUDIO_SERVICE);
        int max = audioManager.getStreamMaxVolume(stream);
        int currentVolume = audioManager.getStreamVolume(stream);
        switch (volumeControl) {
            case VOLUME_MAX: {
                audioManager.setStreamVolume(stream, max, 0);
                return true;
            }
            case VOLUME_MIN: {
                audioManager.setStreamVolume(stream, 1, 0);
                return true;
            }
            case VOLUME_QUARTER: {
                audioManager.setStreamVolume(stream, (int)(max * 0.25), 0);
                return true;
            }
            case VOLUME_HALF: {
                audioManager.setStreamVolume(stream, (int)(max * 0.5), 0);
                return true;
            }
            case VOLUME_THREE_QUARTER: {
                audioManager.setStreamVolume(stream, (int)(max * 0.75), 0);
                return true;
            }
            case VOLUME_TOGGLE: {
                if (currentVolume > 1) {
                    audioManager.setStreamVolume(stream, 1, 0);
                } else {
                    audioManager.setStreamVolume(stream, (int)(max * 0.5), 0);
                }
                return true;
            }
            default: return false;
        }
    }

    public static void registerAdbReceiver(Context context) {
        Log.tb4d("Receiver registered");
        instance = new WeakReference(new AdbReceiver());
        context.registerReceiver(instance.get(), createIntentFilter());
    }

    public static void unregisterAdbReceiver(Context context) {
        if (instance == null || instance.get() == null){
            return;
        }
        Log.tb4d("Receiver unregistered");
        context.unregisterReceiver(instance.get());
        instance = null;
    }


    private static IntentFilter createIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        for (A11yAction action : A11yAction.values()) {
            intentFilter.addAction(String.format("%s.%s", IntentActionPrefix, action.name().toLowerCase()));
        }
        for (DeveloperSetting setting : DeveloperSetting.values()) {
            intentFilter.addAction(String.format("%s.%s", IntentActionPrefix, setting.name().toLowerCase()));
        }
        for (VolumeSetting setting : VolumeSetting.values()) {
            if (setting != VolumeSetting.NONE) {
                intentFilter.addAction(String.format("%s.%s", IntentActionPrefix, setting.name().toLowerCase()));
            }
        }
        return intentFilter;
    }

    private static String IntentActionPrefix = "com.a11y.adb";
    private static WeakReference<AdbReceiver> instance = null;
}

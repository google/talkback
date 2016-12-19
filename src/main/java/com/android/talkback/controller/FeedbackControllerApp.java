/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.annotation.TargetApi;
import com.android.talkback.EarconsPlayTask;
import com.android.talkback.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Build;
import android.os.Vibrator;
import android.util.Log;
import android.util.SparseIntArray;
import com.android.talkback.TalkBackUpdateHelper;
import com.android.utils.LogUtils;
import com.android.utils.PackageManagerUtils;
import com.android.utils.SecureSettingsUtils;
import com.android.utils.SharedPreferencesUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * A feedback controller that caches sounds for quicker playback.
 */
public class FeedbackControllerApp implements FeedbackController {
    /** Maximum number of concurrent audio streams. */
    private static final int MAX_STREAMS = 10;

    /** Default stream for audio feedback. */
    private static final int DEFAULT_STREAM = AudioManager.STREAM_MUSIC;

    /** The parent context. */
    private final Context mContext;

    /** The resources for this context. */
    private final Resources mResources;

    /** The SoundPool instance for loading sounds and playing previously loaded sounds. */
    private final SoundPool mSoundPool;

    /** The vibration service used to play vibration patterns. */
    private final Vibrator mVibrator;

    /** Map from the resource IDs of loaded sounds to SoundPool sound IDs. */
    private final SparseIntArray mSoundIds = new SparseIntArray();

    /** The volume adjustment for sound feedback. */
    private float mVolumeAdjustment = 1.0f;

    private boolean mAuditoryEnabled;
    private boolean mHapticEnabled;

    private final boolean mUseCompatKickBack;
    private final boolean mUseCompatSoundBack;
    private final Set<HapticFeedbackListener> mHapticFeedbackListeners = new HashSet<>();

    // Due to a "feature" in SharedPreferences, this must be a member variable
    @SuppressWarnings("FieldCanBeLocal")
    private final OnSharedPreferenceChangeListener prefListener;

    @SuppressWarnings("deprecation")
    public FeedbackControllerApp(Context context) {
        mContext = context;
        mResources = context.getResources();
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {
            mSoundPool = createSoundPoolApi21();
        } else {
            mSoundPool = new SoundPool(MAX_STREAMS, DEFAULT_STREAM, 0);
        }
        mVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

        // TODO: Do we really need to check compatibility on versions >ICS?
        mUseCompatKickBack = shouldUseCompatKickBack();
        mUseCompatSoundBack = shouldUseCompatSoundBack();

        prefListener = new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
                updatePreferences(sharedPreferences, s);
            }
        };

        final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
        prefs.registerOnSharedPreferenceChangeListener(prefListener);
        updatePreferences(prefs, null);
    }

    @TargetApi(21)
    private SoundPool createSoundPoolApi21() {
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        return new SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(aa)
                .build();
    }

    /**
     * @param enabled Whether haptic feedback should be enabled.
     */
    private void setHapticEnabled(boolean enabled) {
        mHapticEnabled = enabled;
    }

    /**
     * @param enabled Whether auditory feedback should be enabled.
     */
    private void setAuditoryEnabled(boolean enabled) {
        mAuditoryEnabled = enabled;
    }

    /**
     * Sets the current volume adjustment for auditory feedback.
     *
     * @param adjustment The amount by which to adjust the volume of auditory feedback. 0.0 mutes
     *               the feedback while 1.0 plays it at its original volume.
     */
    private void setVolumeAdjustment(float adjustment) {
        mVolumeAdjustment = adjustment;
    }

    @Override
    public boolean playHaptic(int resId) {
        if (!mHapticEnabled || resId == 0) {
            return false;
        }

        final int[] patternArray;
        try {
            patternArray = mResources.getIntArray(resId);
        } catch (NotFoundException e) {
            LogUtils.log(this, Log.ERROR, "Failed to load pattern %d", resId);
            return false;
        }

        final long[] pattern = new long[patternArray.length];
        for (int i = 0; i < patternArray.length; i++) {
            pattern[i] = patternArray[i];
        }

        long nanoTime = System.nanoTime();
        for (HapticFeedbackListener listener : mHapticFeedbackListeners) {
            listener.onHapticFeedbackStarting(nanoTime);
        }
        mVibrator.vibrate(pattern, -1);
        return true;
    }

    @Override
    public void addHapticFeedbackListener(HapticFeedbackListener listener) {
        mHapticFeedbackListeners.add(listener);
    }

    @Override
    public void removeHapticFeedbackListener(HapticFeedbackListener listener) {
        mHapticFeedbackListeners.remove(listener);
    }

    @Override
    public void playAuditory(int resId) {
        playAuditory(resId, 1.0f /* rate */, 1.0f /* volume */);
    }

    @Override
    public void playAuditory(int resId, final float rate, float volume) {
        if (!mAuditoryEnabled || resId == 0) return;
        final float adjustedVolume = volume * mVolumeAdjustment;
        int soundId = mSoundIds.get(resId);

        if (soundId != 0) {
            new EarconsPlayTask(mSoundPool, soundId, adjustedVolume, rate).execute();
        } else {
            // The sound could not be played from the cache. Start loading the sound into the
            // SoundPool for future use, and use a listener to play the sound ASAP.
            mSoundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    if(sampleId !=0) {
                        new EarconsPlayTask(mSoundPool, sampleId, adjustedVolume, rate).execute();
                    }
                }
            });
            mSoundIds.put(resId, mSoundPool.load(mContext, resId, 1));
        }
    }

    @Override
    public void interrupt() {
        // TODO: Stop all sounds.
        mVibrator.cancel();
    }

    @Override
    public void shutdown() {
        mHapticFeedbackListeners.clear();
        mSoundPool.release();
        mVibrator.cancel();
    }

    /**
     * Updates preferences from an instance of {@link SharedPreferences}.
     * Optionally specify a key to update only that preference.
     *
     * @param prefs An instance of {@link SharedPreferences}.
     * @param key The key to update, or {@code null} to update all preferences.
     */
    private void updatePreferences(SharedPreferences prefs, String key) {
        if (key == null) {
            updateHapticFromPreference(prefs);
            updateAuditoryFromPreference(prefs);
            updateVolumeAdjustmentFromPreference(prefs);
        } else if (key.equals(mContext.getString(R.string.pref_vibration_key))) {
            updateHapticFromPreference(prefs);
        } else if (key.equals(mContext.getString(R.string.pref_soundback_key))) {
            updateAuditoryFromPreference(prefs);
        } else if (key.equals(mContext.getString(R.string.pref_soundback_volume_key))) {
            updateVolumeAdjustmentFromPreference(prefs);
        }
    }

    private void updateVolumeAdjustmentFromPreference(SharedPreferences prefs) {
        final int adjustment = SharedPreferencesUtils.getIntFromStringPref(prefs, mResources,
                R.string.pref_soundback_volume_key, R.string.pref_soundback_volume_default);

        setVolumeAdjustment(adjustment / 100.0f);
    }

    private void updateHapticFromPreference(SharedPreferences prefs) {
        final boolean enabled;

        if (mUseCompatKickBack) {
            enabled = SecureSettingsUtils.isAccessibilityServiceEnabled(
                    mContext, TalkBackUpdateHelper.KICKBACK_PACKAGE);
        } else {
            enabled = SharedPreferencesUtils.getBooleanPref(
                    prefs, mResources, R.string.pref_vibration_key, R.bool.pref_vibration_default);
        }

        setHapticEnabled(enabled);
    }

    private void updateAuditoryFromPreference(SharedPreferences prefs) {
        final boolean enabled;

        if (mUseCompatSoundBack) {
            enabled = SecureSettingsUtils.isAccessibilityServiceEnabled(
                    mContext, TalkBackUpdateHelper.SOUNDBACK_PACKAGE);
        } else {
            enabled = SharedPreferencesUtils.getBooleanPref(
                    prefs, mResources, R.string.pref_soundback_key, R.bool.pref_soundback_default);
        }

        setAuditoryEnabled(enabled);
    }

    private boolean shouldUseCompatKickBack() {
        if (!PackageManagerUtils.hasPackage(mContext, TalkBackUpdateHelper.KICKBACK_PACKAGE)) {
            return false;
        }

        final int kickBackVersionCode = PackageManagerUtils.getVersionCode(
                mContext, TalkBackUpdateHelper.KICKBACK_PACKAGE);
        return kickBackVersionCode >= TalkBackUpdateHelper.KICKBACK_REQUIRED_VERSION;
    }

    private boolean shouldUseCompatSoundBack() {
        if (!PackageManagerUtils.hasPackage(mContext, TalkBackUpdateHelper.SOUNDBACK_PACKAGE)) {
            return false;
        }

        final int kickBackVersionCode = PackageManagerUtils.getVersionCode(
                mContext, TalkBackUpdateHelper.SOUNDBACK_PACKAGE);
        return kickBackVersionCode >= TalkBackUpdateHelper.SOUNDBACK_REQUIRED_VERSION;
    }
}

/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.talkback;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;
import com.android.talkback.contextmenu.MenuManager;
import com.android.talkback.controller.FeedbackController;
import com.android.talkback.controller.TelevisionNavigationController;
import com.android.utils.LogUtils;
import com.android.utils.StringBuilderUtils;
import com.google.android.marvin.talkback.TalkBackService;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

// TODO: Refactor this class into two separate receivers
// with listener interfaces. This will remove the need to hold dependencies
// and call into other classes.
/**
 * {@link BroadcastReceiver} for receiving updates for our context - device
 * state
 */
public class RingerModeAndScreenMonitor extends BroadcastReceiver {
    /** The intent filter to match phone and screen state changes. */
    private static final IntentFilter STATE_CHANGE_FILTER = new IntentFilter();

    static {
        STATE_CHANGE_FILTER.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        STATE_CHANGE_FILTER.addAction(Intent.ACTION_SCREEN_ON);
        STATE_CHANGE_FILTER.addAction(Intent.ACTION_SCREEN_OFF);
        STATE_CHANGE_FILTER.addAction(Intent.ACTION_USER_PRESENT);
    }

    private final Context mContext;
    private final SpeechController mSpeechController;
    private final ShakeDetector mShakeDetector;
    private final TelevisionNavigationController mTelevisionNavigationController;
    private final AudioManager mAudioManager;
    private final FeedbackController mFeedbackController;
    private final MenuManager mMenuManager;
    private final TelephonyManager mTelephonyManager;
    private final Set<DialogInterface> mOpenDialogs = new HashSet<>();

    /** The current ringer mode. */
    private int mRingerMode = AudioManager.RINGER_MODE_NORMAL;

    private boolean mIsScreenOn;

    /**
     * Creates a new instance.
     */
    public RingerModeAndScreenMonitor(FeedbackController feedbackController,
                                      MenuManager menuManager,
                                      ShakeDetector shakeDetector,
                                      SpeechController speechController,
                                      TalkBackService context) {
        if (feedbackController == null) throw new IllegalStateException();
        if (menuManager == null) throw new IllegalStateException();
        if (speechController == null) throw new IllegalStateException();
        if (shakeDetector == null) throw new IllegalStateException();

        mContext = context;
        mFeedbackController = feedbackController;
        mMenuManager = menuManager;
        mSpeechController = speechController;
        mShakeDetector = shakeDetector;
        mTelevisionNavigationController = context.getTelevisionNavigationController();

        mAudioManager = (AudioManager) context.getSystemService(Service.AUDIO_SERVICE);
        mTelephonyManager = (TelephonyManager) context.getSystemService(Service.TELEPHONY_SERVICE);
        //noinspection deprecation
        mIsScreenOn = ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isScreenOn();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TalkBackService.isServiceActive()) return;

        String action = intent.getAction();
        if (action == null) return;

        switch (action) {
            case AudioManager.RINGER_MODE_CHANGED_ACTION:
                handleRingerModeChanged(intent.getIntExtra(
                        AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL));
                break;
            case Intent.ACTION_SCREEN_ON:
                mIsScreenOn = true;
                handleScreenOn();
                break;
            case Intent.ACTION_SCREEN_OFF:
                mIsScreenOn = false;
                handleScreenOff();
                break;
            case Intent.ACTION_USER_PRESENT:
                handleDeviceUnlocked();
                break;
        }
    }

    public void updateScreenState() {
        //noinspection deprecation
        mIsScreenOn =
                ((PowerManager) mContext.getSystemService(Context.POWER_SERVICE)).isScreenOn();
    }

    public boolean isScreenOn() {
        return mIsScreenOn;
    }

    public IntentFilter getFilter() {
        return STATE_CHANGE_FILTER;
    }

    /**
     * Handles when the device is unlocked. Just speaks "unlocked."
     */
    private void handleDeviceUnlocked() {
        if (isIdle()) {
            final String text = mContext.getString(R.string.value_device_unlocked);
            mSpeechController.speak(text, SpeechController.QUEUE_MODE_INTERRUPT, 0, null);
        }
    }

    /**
     * Handles when the screen is turned off. Announces "screen off" and
     * suspends the proximity sensor.
     */
    @SuppressWarnings("deprecation")
    private void handleScreenOff() {
        mSpeechController.setScreenIsOn(false);
        mMenuManager.dismissAll();

        // Iterate over a copy because dialog dismiss handlers might try to unregister dialogs.
        LinkedList<DialogInterface> openDialogsCopy = new LinkedList<>(mOpenDialogs);
        for (DialogInterface dialog : openDialogsCopy) {
            dialog.cancel();
        }
        mOpenDialogs.clear();

        final SpannableStringBuilder builder =
                new SpannableStringBuilder(mContext.getString(R.string.value_screen_off));
        // Only announce ringer state if we're not in a call.
        if (isIdle()) {
            appendRingerStateAnnouncement(builder);
        }

        mShakeDetector.pausePolling();

        if (mRingerMode == AudioManager.RINGER_MODE_NORMAL) {
            final int soundId;
            final float volume;
            final float musicVolume = getStreamVolume(AudioManager.STREAM_MUSIC);
            if ((musicVolume > 0)
                && (mAudioManager.isWiredHeadsetOn()
                    ||  mAudioManager.isBluetoothA2dpOn())) {
                // Play the ringer beep on the default (music) stream to avoid
                // issues with ringer audio (e.g. no speech on ICS and
                // interruption of music on JB). Adjust playback volume to
                // compensate for music volume.
                final float ringVolume = getStreamVolume(AudioManager.STREAM_RING);
                soundId = R.raw.volume_beep;
                volume = Math.min(1.0f, (ringVolume / musicVolume));
            } else {
                // Normally we'll play the volume beep on the ring stream.
                soundId = R.raw.volume_beep;
                volume = 1.0f;
            }

            mFeedbackController.playAuditory(soundId, 1.0f /* rate */, volume);
        }

        // Always reset the television remote mode to the standard (navigate) mode on screen off.
        if (mTelevisionNavigationController != null) {
            mTelevisionNavigationController.resetToNavigateMode();
        }

        mSpeechController.speak(
                builder, SpeechController.QUEUE_MODE_INTERRUPT, FeedbackItem.FLAG_NO_HISTORY, null);
    }

    /**
     * Handles when the screen is turned on. Announces the current time and the
     * current ringer state when phone is idle.
     */
    private void handleScreenOn() {
        // TODO: This doesn't look right. Should probably be using a listener.
        mSpeechController.setScreenIsOn(true);
        final SpannableStringBuilder builder = new SpannableStringBuilder();

        if (isIdle()) {
            // Need the old version to support version older than JB_MR1
            //noinspection deprecation
            if (Settings.Secure.getInt(mContext.getContentResolver(),
                    Settings.Secure.DEVICE_PROVISIONED, 0) != 0) {
                    appendCurrentTimeAnnouncement(builder);
            } else {
                // Device is not ready, just speak screen on
                builder.append(mContext.getString(R.string.value_screen_on));
            }
        }

        mShakeDetector.resumePolling();
        mSpeechController.speak(builder, SpeechController.QUEUE_MODE_INTERRUPT, 0, null);
    }

    /**
     * Return current phone's call state is idle or not.
     * @return true when phone is idle
     */
    private boolean isIdle() {
        return mTelephonyManager != null
                && mTelephonyManager.getCallState() == TelephonyManager.CALL_STATE_IDLE;
    }

    /**
     * Handles when the ringer mode (ex. volume) changes. Announces the current
     * ringer state.
     */
    private void handleRingerModeChanged(int ringerMode) {
        mRingerMode = ringerMode;
    }

    /**
     * Appends the current time announcement to a {@link StringBuilder}.
     *
     * @param builder The string to append to.
     */
    @SuppressWarnings("deprecation")
    private void appendCurrentTimeAnnouncement(SpannableStringBuilder builder) {
        int timeFlags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;

        if (DateFormat.is24HourFormat(mContext)) {
            timeFlags |= DateUtils.FORMAT_24HOUR;
        }

        final CharSequence dateTime =
                DateUtils.formatDateTime(mContext, System.currentTimeMillis(), timeFlags);

        StringBuilderUtils.appendWithSeparator(builder, dateTime);
    }

    /**
     * Appends the ringer state announcement to a {@link StringBuilder}.
     *
     * @param builder The string to append to.
     */
    private void appendRingerStateAnnouncement(SpannableStringBuilder builder) {
        if (mTelephonyManager == null) {
            return;
        }

        final String announcement;

        switch (mRingerMode) {
            case AudioManager.RINGER_MODE_SILENT:
                announcement = mContext.getString(R.string.value_ringer_silent);
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                announcement = mContext.getString(R.string.value_ringer_vibrate);
                break;
            case AudioManager.RINGER_MODE_NORMAL:
                return;
            default:
                LogUtils.log(TalkBackService.class, Log.ERROR, "Unknown ringer mode: %d",
                        mRingerMode);
                return;
        }

        StringBuilderUtils.appendWithSeparator(builder, announcement);
    }

    /**
     * Returns the volume a stream as a fraction of its maximum volume.
     *
     * @param streamType The stream type for which to return the volume.
     * @return The stream volume as a fraction of its maximum volume.
     */
    private float getStreamVolume(int streamType) {
        final int currentVolume = mAudioManager.getStreamVolume(streamType);
        final int maxVolume = mAudioManager.getStreamMaxVolume(streamType);
        return (currentVolume / (float) maxVolume);
    }

    /** Registers a dialog to be auto-cancelled when the screen turns off. */
    public void registerDialog(DialogInterface dialog) {
        mOpenDialogs.add(dialog);
    }

    /** Removes a dialog from the list of dialogs to be auto-cancelled. */
    public void unregisterDialog(DialogInterface dialog) {
        mOpenDialogs.remove(dialog);
    }
}

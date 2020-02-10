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

package com.google.android.accessibility.utils.output;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Vibrator;
import android.util.SparseIntArray;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.R;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A feedback controller that caches sounds for quicker playback. */
public class FeedbackController {

  private static final String TAG = "FeedbackController";

  /** Default stream for audio feedback. */
  public static final int DEFAULT_STREAM =
      BuildVersionUtils.isAtLeastO()
          ? AudioManager.STREAM_ACCESSIBILITY
          : AudioManager.STREAM_MUSIC;

  /** Maximum number of concurrent audio streams. */
  private static final int MAX_STREAMS = 10;

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

  private final Set<HapticFeedbackListener> mHapticFeedbackListeners = new HashSet<>();

  public FeedbackController(Context context) {
    this(context, createSoundPool(), (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE));
  }

  public FeedbackController(Context context, SoundPool soundPool, Vibrator vibrator) {
    mContext = context;
    mResources = context.getResources();
    mSoundPool = soundPool;
    mVibrator = vibrator;
  }

  /**
   * Plays the vibration pattern associated with the given resource ID.
   *
   * @param resId The vibration pattern's resource identifier.
   * @return {@code true} if successful.
   */
  public boolean playHaptic(int resId, @Nullable EventId eventId) {
    if (!mHapticEnabled || resId == 0) {
      return false;
    }
    LogUtils.v(TAG, "playHaptic() resId=%d eventId=%s", resId, eventId);

    final int[] patternArray;
    try {
      patternArray = mResources.getIntArray(resId);
    } catch (NotFoundException e) {
      LogUtils.e(TAG, "Failed to load pattern %d", resId);
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

  /**
   * Adds a listener to be called when haptic feedback begins.
   *
   * @param listener The listener to add.
   */
  public void addHapticFeedbackListener(FeedbackController.HapticFeedbackListener listener) {
    mHapticFeedbackListeners.add(listener);
  }

  /**
   * Removes a HapticFeedbackListener.
   *
   * @param listener The listener to remove.
   */
  public void removeHapticFeedbackListener(FeedbackController.HapticFeedbackListener listener) {
    mHapticFeedbackListeners.remove(listener);
  }

  /**
   * Plays the auditory feedback associated with the given resource ID using the default rate,
   * volume, and panning.
   *
   * @param resId The auditory feedback's resource identifier.
   */
  public void playAuditory(int resId, @Nullable EventId eventId) {
    playAuditory(resId, 1.0f /* rate */, 1.0f /* volume */, eventId);
  }

  /**
   * Plays the auditory feedback associated with the given resource ID using the specified rate,
   * volume, and panning.
   *
   * @param resId The auditory feedback's resource identifier.
   * @param rate The playback rate adjustment, from 0.5 (half speed) to 2.0 (double speed).
   * @param volume The volume adjustment, from 0.0 (mute) to 1.0 (original volume).
   */
  public void playAuditory(int resId, final float rate, float volume, @Nullable EventId eventId) {
    if (!mAuditoryEnabled || resId == 0) {
      return;
    }
    LogUtils.v(TAG, "playAuditory() resId=%d eventId=%s", resId, eventId);

    final float adjustedVolume = volume * mVolumeAdjustment;
    int soundId = mSoundIds.get(resId);

    if (soundId != 0) {
      new EarconsPlayTask(mSoundPool, soundId, adjustedVolume, rate).execute();
    } else {
      // The sound could not be played from the cache. Start loading the sound into the
      // SoundPool for future use, and use a listener to play the sound ASAP.
      mSoundPool.setOnLoadCompleteListener(
          new OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
              if (sampleId != 0) {
                new EarconsPlayTask(mSoundPool, sampleId, adjustedVolume, rate).execute();
              }
            }
          });
      mSoundIds.put(resId, mSoundPool.load(mContext, resId, 1));
    }
  }

  /** Interrupts all ongoing feedback. */
  public void interrupt() {
    // TODO: Stop all sounds.
    mVibrator.cancel();
  }

  /**
   * Releases all resources held by the feedback controller and clears the shared instance. No calls
   * should be made to this instance after calling this method.
   */
  public void shutdown() {
    mHapticFeedbackListeners.clear();
    mSoundPool.release();
    mVibrator.cancel();
  }

  /**
   * Sets whether to enable or disable the haptic feedback.
   *
   * @param enabled Whether haptic feedback should be enabled.
   */
  public void setHapticEnabled(boolean enabled) {
    mHapticEnabled = enabled;
  }

  /**
   * Sets whether to enable or disable the auditory feedback.
   *
   * @param enabled Whether auditory feedback should be enabled.
   */
  public void setAuditoryEnabled(boolean enabled) {
    mAuditoryEnabled = enabled;
  }

  /**
   * Sets the current volume adjustment for auditory feedback.
   *
   * @param adjustment The amount by which to adjust the volume of auditory feedback. 0.0 mutes the
   *     feedback while 1.0 plays it at its original volume.
   */
  public void setVolumeAdjustment(float adjustment) {
    mVolumeAdjustment = adjustment;
  }

  /**
   * Provides vibration and sound feedback to acknowledge the completion of an action (e.g. item
   * selection in Switch Access, gesture completion in TalkBack, etc.).
   */
  public void playActionCompletionFeedback() {
    playHaptic(R.array.window_state_pattern, EVENT_ID_UNTRACKED);
    playAuditory(R.raw.window_state, EVENT_ID_UNTRACKED);
  }

  private static SoundPool createSoundPool() {
    AudioAttributes aa =
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    return new SoundPool.Builder().setMaxStreams(MAX_STREAMS).setAudioAttributes(aa).build();
  }

  /**
   * Some features, such as the tap detector, may be affected by haptic feedback and want to know
   * when we initiate it.
   */
  public interface HapticFeedbackListener {

    /**
     * Alerts the listener that haptic feedback is about to start.
     *
     * @param currentNanoTime The current system time.
     */
    void onHapticFeedbackStarting(long currentNanoTime);
  }
}

/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.braille.brailledisplay.platform.lib;

import android.content.Context;
import android.content.res.Resources;
import android.media.AudioManager;
import android.media.SoundPool;
import android.util.SparseIntArray;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;

/**
 * A copy of the SoundPlayer class for use with the new braille display connection module.
 * TODO: Remove this class and re-use the shared SoundPlayer.
 */
public class SoundPlayer {

  private static final String TAG = "SoundPlayer";

  /** Default volume for sound playback relative to current stream volume. */
  private static final float DEFAULT_VOLUME = 1.0f;

  /** Default rate for sound playback. Use 1.0f for normal speed playback. */
  private static final float DEFAULT_RATE = 1.0f;

  /** Number of channels to use in SoundPool for auditory icon feedback. */
  private static final int NUMBER_OF_CHANNELS = 10;

  /** Map of resource IDs to loaded sound stream IDs. */
  private final SparseIntArray resourceIdToSoundMap = new SparseIntArray();

  /** Parent context. Required for mapping resource IDs to resources. */
  private final Context context;

  /** Parent resources. Used to distinguish raw and MIDI resources. */
  private final Resources resources;

  /** Sound pool used to play auditory icons. */
  private final SoundPool soundPool;

  /** Current volume (range 0..1). */
  private float volume = DEFAULT_VOLUME;

  /** Constructs and initializes a new feedback controller. */
  public SoundPlayer(Context context) {
    this.context = context;
    resources = context.getResources();
    soundPool = new SoundPool(NUMBER_OF_CHANNELS, AudioManager.STREAM_MUSIC, 1);
    resourceIdToSoundMap.clear();
  }

  /**
   * Sets the current volume for auditory feedback.
   *
   * @param volume Volume value (range 0..100).
   */
  public void setVolume(int volume) {
    this.volume = (Math.min(100, Math.max(0, volume)) / 100.0f);
  }

  /**
   * Releases resources associated with this feedback controller. It is good practice to call this
   * method when you're done using the controller.
   */
  public void shutdown() {
    soundPool.release();
  }

  /**
   * Asynchronously make a sound available for later use if audio feedback is enabled. Sounds should
   * be loaded using this function whenever audio feedback is enabled.
   *
   * @param resId Resource ID of the sound to be loaded.
   * @return The sound pool identifier for the resource.
   */
  public int preloadSound(int resId) {
    if (resourceIdToSoundMap.indexOfKey(resId) >= 0) {
      return resourceIdToSoundMap.get(resId);
    }
    final String resType = resources.getResourceTypeName(resId);
    if ("raw".equals(resType)) {
      final int soundPoolId;
      soundPoolId = soundPool.load(context, resId, 1);
      if (soundPoolId < 0) {
        BrailleDisplayLog.e(TAG, "Failed to load sound: Invalid sound pool ID");
        return -1;
      }
      resourceIdToSoundMap.put(resId, soundPoolId);
      return soundPoolId;
    } else {
      BrailleDisplayLog.e(TAG, "Failed to load sound: Unknown resource type");
      return -1;
    }
  }

  /**
   * Plays the sound file specified by the given resource identifier at the default rate.
   *
   * @param resId The sound file's resource identifier.
   * @return {@code true} if successful
   */
  public boolean playSound(int resId) {
    if (resourceIdToSoundMap.indexOfKey(resId) < 0) {
      return false;
    }

    final int soundId = resourceIdToSoundMap.get(resId);
    final float relativeVolume = volume * DEFAULT_VOLUME;
    final int stream = soundPool.play(soundId, relativeVolume, relativeVolume, 1, 0, DEFAULT_RATE);

    return (stream != 0);
  }
}

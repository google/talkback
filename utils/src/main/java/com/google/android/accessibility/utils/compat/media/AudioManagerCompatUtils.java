/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.utils.compat.media;

import android.media.AudioManager;
import com.google.android.accessibility.utils.compat.CompatUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.reflect.Method;

public class AudioManagerCompatUtils {
  private static final Method METHOD_forceVolumeControlStream =
      CompatUtils.getMethod(AudioManager.class, "forceVolumeControlStream", int.class);

  private static final String TAG = "AudioManagerCompatUtils";

  /**
   * Broadcast intent when the volume for a particular stream type changes. Includes the stream, the
   * new volume and previous volumes
   *
   * @see #EXTRA_VOLUME_STREAM_TYPE
   * @see #EXTRA_VOLUME_STREAM_VALUE
   * @see #EXTRA_PREV_VOLUME_STREAM_VALUE
   */
  public static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";

  /** The stream type for the volume changed intent. */
  public static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";

  /** The stream type alias for the volume changed intent. */
  public static final String EXTRA_VOLUME_STREAM_TYPE_ALIAS =
      "android.media.EXTRA_VOLUME_STREAM_TYPE_ALIAS";

  /** The volume associated with the stream for the volume changed intent. */
  public static final String EXTRA_VOLUME_STREAM_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE";

  /** The previous volume associated with the stream for the volume changed intent. */
  public static final String EXTRA_PREV_VOLUME_STREAM_VALUE =
      "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE";

  /**
   * Broadcast intent when the volume set to mute or unmute. Includes the stream type, the current
   * mute state
   *
   * @see EXTRA_VOLUME_STREAM_TYPE
   * @see EXTRA_STREAM_VOLUME_MUTED
   */
  public static final String STREAM_MUTE_CHANGED_ACTION =
      "android.media.STREAM_MUTE_CHANGED_ACTION";

  /** The mute statefor the stream mute changed intent. */
  public static final String EXTRA_STREAM_VOLUME_MUTED = "android.media.EXTRA_STREAM_VOLUME_MUTED";

  private AudioManagerCompatUtils() {
    // This class is non-instantiable.
  }

  /**
   * Forces the stream controlled by hard volume keys specifying streamType == -1 releases control
   * to the logic.
   *
   * <p><b>Warning:</b> This is a private API, and it may not exist in API 16+.
   */
  public static void forceVolumeControlStream(AudioManager receiver, int streamType) {
    CompatUtils.invoke(receiver, null, METHOD_forceVolumeControlStream, streamType);
  }

  /**
   * Wraps {@link AudioManager#adjustStreamVolume(int, int, int)} to handle exception.
   *
   * @see AudioManager#adjustStreamVolume(int, int, int).
   *     <p>Post N, adjustStreamVolume can throw a SecurityException when changing the stream volume
   *     would change the DnD mode and the caller doesn't have
   *     android.Manifest.permission.MANAGE_NOTIFICATIONS, which is a signature permission. Hence
   *     Talkback catches the exception to avoid crashing.
   */
  public static void adjustStreamVolume(
      AudioManager audioManager, int streamType, int direction, int flags, String source) {
    try {
      if (audioManager != null) {
        audioManager.adjustStreamVolume(streamType, direction, flags);
      }
    } catch (SecurityException e) {
      LogUtils.e(TAG, "Error while adjusting stream volume: %s", e);
    }
  }
}

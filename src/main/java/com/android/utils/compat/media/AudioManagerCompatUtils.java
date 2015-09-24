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

package com.android.utils.compat.media;

import android.media.AudioManager;
import com.android.utils.compat.CompatUtils;

import java.lang.reflect.Method;

public class AudioManagerCompatUtils {
    private static final Method METHOD_forceVolumeControlStream = CompatUtils.getMethod(
            AudioManager.class, "forceVolumeControlStream", int.class);

    /**
     * Broadcast intent when the volume for a particular stream type changes.
     * Includes the stream, the new volume and previous volumes
     *
     * @see #EXTRA_VOLUME_STREAM_TYPE
     * @see #EXTRA_VOLUME_STREAM_VALUE
     * @see #EXTRA_PREV_VOLUME_STREAM_VALUE
     */
    public static final String VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION";

    /**
     * @see #EXTRA_MASTER_VOLUME_VALUE
     * @see #EXTRA_PREV_MASTER_VOLUME_VALUE
     */
    public static final String MASTER_VOLUME_CHANGED_ACTION =
            "android.media.MASTER_VOLUME_CHANGED_ACTION";

    /**
     * The stream type for the volume changed intent.
     */
    public static final String EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE";

    /**
     * The stream type alias for the volume changed intent.
     */
    public static final String EXTRA_VOLUME_STREAM_TYPE_ALIAS =
            "android.media.EXTRA_VOLUME_STREAM_TYPE_ALIAS";

    /**
     * The volume associated with the stream for the volume changed intent.
     */
    public static final String EXTRA_VOLUME_STREAM_VALUE =
            "android.media.EXTRA_VOLUME_STREAM_VALUE";

    /**
     * The previous volume associated with the stream for the volume changed
     * intent.
     */
    public static final String EXTRA_PREV_VOLUME_STREAM_VALUE =
            "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE";

    /**
     * The new master volume value for the master volume changed intent. Value
     * is integer between 0 and 100 inclusive.
     */
    public static final String EXTRA_MASTER_VOLUME_VALUE =
            "android.media.EXTRA_MASTER_VOLUME_VALUE";

    /**
     * The previous master volume value for the master volume changed intent.
     * Value is integer between 0 and 100 inclusive.
     */
    public static final String EXTRA_PREV_MASTER_VOLUME_VALUE =
            "android.media.EXTRA_PREV_MASTER_VOLUME_VALUE";

    private AudioManagerCompatUtils() {
        // This class is non-instantiable.
    }

    /**
     * Forces the stream controlled by hard volume keys specifying streamType ==
     * -1 releases control to the logic.
     * <p>
     * <b>Warning:</b> This is a private API, and it may not exist in API 16+.
     * </p>
     */
    public static void forceVolumeControlStream(AudioManager receiver, int streamType) {
        CompatUtils.invoke(receiver, null, METHOD_forceVolumeControlStream, streamType);
    }
}

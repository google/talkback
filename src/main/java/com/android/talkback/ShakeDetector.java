/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.PowerManager;
import com.android.talkback.controller.FullScreenReadController;
import com.android.utils.SharedPreferencesUtils;
import com.google.android.marvin.talkback.TalkBackService;

/**
 * Detector for shake events used to trigger continuous reading.
 */
public class ShakeDetector implements SensorEventListener {
    private static final float MOVEMENT_WINDOW = 200;
    private final TalkBackService mContext;
    private final SharedPreferences mPrefs;
    private final FullScreenReadController mFullScreenReadController;
    private final SensorManager mSensorManager;
    private final Sensor mAccelerometer;
    private boolean mIsFeatureEnabled;
    private boolean mIsActive;
    private long mLastSensorUpdate;
    private float[] mLastEventValues;

    public ShakeDetector(FullScreenReadController fullScreenReadController,
                         TalkBackService context) {
        if (fullScreenReadController == null) throw new IllegalStateException();

        mContext = context;
        mPrefs = SharedPreferencesUtils.getSharedPreferences(context);
        mFullScreenReadController = fullScreenReadController;
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /**
     * Sets whether or not to enable the shake detection feature.
     *
     * @param enable {@code true} to enable, {@code false} otherwise
     */
    public void setEnabled(boolean enable) {
        mIsFeatureEnabled = enable;
        if (enable) {
            final PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            //noinspection deprecation
            if (pm.isScreenOn()) {
                resumePolling();
            }
        } else {
            pausePolling();
        }
    }

    /**
     * Starts polling the accelerometer for shake detection. If the feature has
     * not be enabled by calling {@link #setEnabled(boolean)}, calling this
     * method is a no-op.
     */
    public void resumePolling() {
        if (!mIsFeatureEnabled || mIsActive) {
            return;
        }

        mLastSensorUpdate = 0;
        mLastEventValues = new float[3];
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        mIsActive = true;
    }

    /**
     * Stops polling the accelerometer for shake detection.
     */
    public void pausePolling() {
        if (!mIsActive) {
            return;
        }

        mSensorManager.unregisterListener(this);
        mIsActive = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        final long time = System.currentTimeMillis();
        final long deltaT = (time - mLastSensorUpdate);

        if (deltaT > MOVEMENT_WINDOW) {
            final float movement = Math.abs(event.values[0] + event.values[1] + event.values[2]
                    - mLastEventValues[0] - mLastEventValues[1] - mLastEventValues[2]);
            final float speed = (movement / deltaT) * 10000;
            mLastSensorUpdate = time;
            mLastEventValues = event.values.clone();

            final int threshold = SharedPreferencesUtils.getIntFromStringPref(mPrefs,
                    mContext.getResources(), R.string.pref_shake_to_read_threshold_key,
                    R.string.pref_shake_to_read_threshold_default);
            if ((threshold > 0) && (speed >= threshold)) {
                mFullScreenReadController.startReadingFromNextNode();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing.
    }
}

/*
 * Copyright (C) 2010 Google Inc.
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

package com.android.utils;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;

/**
 * Convenience class for working with the ProximitySensor. Also uses the ambient
 * light sensor in its place, when available.
 */
public class ProximitySensor {

    /**
     * Number of milliseconds to wait before reporting onSensorChanged events to
     * the listener. Used to compensate for platform inconsistencies surrounding
     * reporting the sensor state after listener registration or an accuracy
     * change.
     */
    private static final long REGISTRATION_EVENT_FILTER_TIMEOUT = 120;

    private final SensorManager mSensorManager;
    private final Sensor mProxSensor;
    private final Handler mHandler = new Handler();

    private final float mFarValue;

    private ProximityChangeListener mCallback;

    /**
     * Whether this class should be dropping onSensorChanged events from
     * reaching the client.
     */
    private boolean mShouldDropEvents;

    /** Whether the user is close to the proximity sensor. */
    private boolean mIsClose;

    /** Whether the sensor is currently active. */
    private boolean mIsActive;

    /**
     * Constructor for ProximitySensor
     *
     * @param context The parent context.
     */
    public ProximitySensor(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mProxSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mFarValue = (mProxSensor != null) ? mProxSensor.getMaximumRange() : 0;
    }

    public void setProximityChangeListener(ProximityChangeListener listener) {
        mCallback = listener;
    }

    /**
     * Checks if something is close to the proximity sensor
     *
     * @return {@code true} if there is something close to the proximity sensor
     */
    public boolean isClose() {
        return mIsClose;
    }

    /**
     * Stops listening for sensor events.
     */
    public void stop() {
        if ((mProxSensor == null) || !mIsActive) {
            return;
        }

        LogUtils.log(
                this, Log.VERBOSE, "Proximity sensor stopped at %d.", System.currentTimeMillis());
        mIsActive = false;
        mSensorManager.unregisterListener(mListener);
    }

    /**
     * Starts listening for sensor events.
     */
    public void start() {
        if ((mProxSensor == null) || mIsActive) {
            return;
        }

        mIsActive = true;
        mShouldDropEvents = true;
        mSensorManager.registerListener(mListener, mProxSensor, SensorManager.SENSOR_DELAY_UI);
        LogUtils.log(this, Log.VERBOSE,
                "Proximity sensor registered at %d.", System.currentTimeMillis());
        mHandler.postDelayed(mFilterRunnable, REGISTRATION_EVENT_FILTER_TIMEOUT);
    }

    private final SensorEventListener mListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            LogUtils.log(this, Log.VERBOSE,
                    "Processing onAccuracyChanged event at %d.", System.currentTimeMillis());
            mShouldDropEvents = true;
            mHandler.removeCallbacks(mFilterRunnable);
            mHandler.postDelayed(mFilterRunnable, REGISTRATION_EVENT_FILTER_TIMEOUT);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mShouldDropEvents) {
                LogUtils.log(this, Log.VERBOSE,
                        "Dropping onSensorChanged event at %d.", System.currentTimeMillis());
                return;
            }

            LogUtils.log(this, Log.VERBOSE,
                    "Processing onSensorChanged event at %d.", System.currentTimeMillis());
            mIsClose = (event.values[0] < mFarValue);
            mCallback.onProximityChanged(mIsClose);
        }
    };

    /**
     * Runnable used to enforce the {@link #REGISTRATION_EVENT_FILTER_TIMEOUT}
     */
    private final Runnable mFilterRunnable = new Runnable() {
        @Override
        public void run() {
            mShouldDropEvents = false;
            LogUtils.log(this, Log.VERBOSE,
                    "Stopped filtering proximity events at %d.", System.currentTimeMillis());
        }
    };

    /**
     * Callback for when the proximity sensor detects a change
     */
    public interface ProximityChangeListener {
        public void onProximityChanged(boolean isClose);
    }
}

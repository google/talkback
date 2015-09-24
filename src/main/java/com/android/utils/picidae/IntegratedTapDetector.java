/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.utils.picidae;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

/**
 * Class to gather data from the accelerometer and gyroscope and detect tapping on the side of the
 * device.  Taps (and double-taps, if requested) are reported to registered listeners.
 * <p>
 * Raw data can be saved to a LittleEndianDataOutputStream for development.
 * <p>
 * This detector integrates output from individual detectors that each detect taps based on a
 * single sensor. Options exist to consider only one sensor at a time and to qualify taps based on
 * a tap quality, which can range from 0.0 to 1.0.
 * <p>
 * Functions adjust the minimum spacing between two individual taps, as well as the maximum spacing
 * between the taps of a double tap. These two parameters are orthogonal, as a double tap is
 * treated as a individual event.
 * <p>
 */
public class IntegratedTapDetector implements SensorEventListener,
        ThreeDSensorTapDetector.TapListener {

    private static final boolean DEBUG = false;

    /*
     * Quantitative tap quality metrics used to match qualitative.
     * TODO(PW) refactor to have individual detectors report their own confidence along some
     * absolute scale so we don't need to define the public quality guidance values based on
     * private constants.
     */
    private static final double TAP_QUALITY_POSSIBLE = 0.15;

    private static final double TAP_QUALITY_DEFINITE = 0.5;

    private static final float DEFAULT_MAX_ACCEL_SCALE = 30f;

    private static final float DEFAULT_MAX_GYRO_SCALE = 8f;

    /**
     * Guidance value for setTapDetectionQuality. Try not to lose any taps and accept that there
     * will be quite a few false positives. This value is the default for double tap quality, as
     * the first tap qualifies the second one.
     */
    public static final double TAP_QUALITY_LOW = TAP_QUALITY_POSSIBLE;

    /**
     * Guidance value for setTapDetectionQuality. Balance between false positives and false
     * negatives.
     */
    public static final double TAP_QUALITY_MEDIUM = 2 * TAP_QUALITY_POSSIBLE;

    /**
     * Guidance value for setTapDetectionQuality. Work hard to avoid false positives.
     * This is the default for tap detection.
     */
    public static final double TAP_QUALITY_HIGH = TAP_QUALITY_DEFINITE;

    /**
     * Guidance value for setTapDetectionQuality. Very few false positives, but tends to miss taps
     * that aren't sampled very well by the accelerometer and gyroscope.
     */
    public static final double TAP_QUALITY_HIGHEST = TAP_QUALITY_DEFINITE + TAP_QUALITY_POSSIBLE;

    /* Suggested value for min tap spacing */
    private static final long DEFAULT_MIN_TAP_SPACING = 100 * 1000 * 1000;

    /*
     * The various tap detectors used may report the same tap at different times. We combine these
     * taps together independent of the value of mMinTapSpacing.
     */
    private static final long MAX_OFFSET_BETWEEN_TAP_DETECTORS = 100 * 1000 * 1000;

    private static final long MILIS_PER_NANO = 1000 * 1000;

    /*
     * Maximum latency of a low-level tap detector
     * TODO(PW): Each low-level detector should expose its latency, and we
     * could query it rather than relying on a constant.
     */
    private static final long MAX_TAP_DETECTOR_LATENCY  = 100 * 1000 * 1000;

    /* Handler containing the internal processing thread */
    private final Handler mHandler;

    /* Framework sensor manager that gives us access to the accelerometer and gyroscope */
    private final SensorManager mSensorManager;

    /* Queues to store taps from accelerometer. */
    private final Queue<Tap> mAccelTapEventQueue = new LinkedList<>();

    /* Queues to store taps from gyroscope. */
    private final Queue<Tap> mGyroTapEventQueue = new LinkedList<>();

    /* Queues to store taps detected by integrating the lower-level detectors. */
    private final Queue<Tap> mIntegratedTapEventQueue = new LinkedList<>();

    /* The accelerometer-based tap detector being integrated */
    private final ThreeDSensorTapDetector mAccelTapDetector;

    /* The gyroscope-based tap detector being integrated */
    private final ThreeDSensorTapDetector mGyroTapDetector;

    /* Minimum quality value used for a single tap and/or the first half of a double tap */
    private double mMinTapQuality;

    /* Minimum quality value used for the second half of a double tap */
    private double mMinDoubleTapQuality;

    /* Map to keep track of listeners and which handlers to call them through */
    private final Map<TapListener, Handler> mListenerMap = new HashMap<>();

    /* Current tap detection strategy */
    private TapDetector mCurrentTapDetector = TapDetector.INTEGRATED_TAP_DETECTOR;

    /* The last time we reported a tap */
    private long mLastReportedTapTime;

    /* Flag that we have reported at least one tap, making mLastReportedTapTime valid */
    private boolean mHaveReportedAtLeastOneTap = false;

    /* Taps that happen too close together are ignored because they are likely noise */
    private long mMinTapSpacingNanos = DEFAULT_MIN_TAP_SPACING;

    /* Taps close enough together are double taps. A value of 0 disables double-tap detection */
    private long mMaxDoubleTapSpacingNanos = 0;

    /* Amount to delay posting of messages, which is helpful for certain services */
    private long mPostDelayTime = 0;

    /**
     * @param sensorManager system's SensorManager. Obtain with
     * getSystemService(Context.SENSOR_SERVICE);
     */
    public IntegratedTapDetector(SensorManager sensorManager) {
        this(sensorManager, null, null);
    }

    // Visible for testing
    public IntegratedTapDetector(SensorManager sensorManager,
                                  ThreeDSensorTapDetector accelTapDetector, ThreeDSensorTapDetector gyroTapDetector) {
        mSensorManager = sensorManager;
        // TODO(PW): Determine the correct priority of this thread
        HandlerThread thread = new HandlerThread("AccelGyroAudioTapDetector", -20);
        thread.start();
        mHandler = new Handler(thread.getLooper());
        mHaveReportedAtLeastOneTap = false;
        mMinTapQuality = TAP_QUALITY_HIGH;
        mMinDoubleTapQuality = TAP_QUALITY_LOW;

        if (accelTapDetector == null) {
            Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            float maxRange = DEFAULT_MAX_ACCEL_SCALE;
            if (accelerometer != null) {
                maxRange = accelerometer.getMaximumRange();
            }

            accelTapDetector = new ThreeDSensorTapDetector(
                    this, maxRange, ThreeDSensorTapDetectorType.ACCELEROMETER);
        }

        if (gyroTapDetector == null) {
            Sensor gyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            float maxRange = DEFAULT_MAX_GYRO_SCALE;
            if (gyroscope != null) {
                maxRange = gyroscope.getMaximumRange();
            }

            gyroTapDetector = new ThreeDSensorTapDetector(
                    this, maxRange, ThreeDSensorTapDetectorType.GYROSCOPE);
        }

        mAccelTapDetector = accelTapDetector;
        mGyroTapDetector = gyroTapDetector;
    }

    /**
     * Choose desired tap detection method
     */
    public void useTapDetector(TapDetector tapDetector) {
        mCurrentTapDetector = tapDetector;
    }

    /**
     * Choose the minimum tap spacing in nanos
     *
     * @param tapSpacing Maximum time (in nanoseconds) by which taps may be separated
     */
    public void setMinimumTapSpacingNanos(long tapSpacing) {
        mMinTapSpacingNanos = tapSpacing;
    }

    /**
     * Choose the maximum spacing in nanos for double-taps, which are reported as separate events
     * from taps. Default is 0, which disables double-tap detection.
     *
     * @param maxDTapSpacing taps closer together than this will be combined into a single double
     * tap. 0 disabled double-tap detection.
     */
    public void setMaxDoubleTapSpacingNanos(long maxDTapSpacing) {
        mMaxDoubleTapSpacingNanos = maxDTapSpacing;
    }

    /**
     * Set desired tap quality
     *
     * @param tq value between 0 and 1. Larger numbers are higher quality taps.
     */
    public void setTapDetectionQuality(double tq) {
        mMinTapQuality = tq;
    }

    /**
     * Set desired double-tap quality. Double-taps must have at least one tap that is of standard
     * tap quality, and one over this quality.
     *
     * @param tq value between 0 and 1. Larger numbers are higher quality taps.
     */
    public void setDoubleTapDetectionQuality(double tq) {
        mMinDoubleTapQuality = tq;
    }

    /**
     * For some applications, it's convenient if taps are reported with a slight delay. Processing
     * gives some delay, so this value is a loose lower bound.
     *
     * @param millisToDelayPosts - target time to delay posting of tap callback
     */
    public void setPostDelayTimeMillis(long millisToDelayPosts) {
        mPostDelayTime = millisToDelayPosts;
    }

    /**
     * Start listening to sensors for taps.
     */
    public void start() {
        Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(
                this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST, mHandler);

        Sensor gyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mSensorManager
                .registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST, mHandler);
    }

    /**
     * Stop listening to sensors. The detector should be disabled when it's not needed (as when the
     * activity paying attention to it doesn't have focus or the screen is off) since it takes
     * considerable CPU resources.
     */
    public void stop() {
        mSensorManager.unregisterListener(this);
    }

    /**
     * Process an accelerometer event.
     */
    void onAccelerometerChanged(long timestamp, float[] values) {
        switch(mCurrentTapDetector) {
            case ACCEL_ONLY_DETECTOR:
            case INTEGRATED_TAP_DETECTOR:
                mAccelTapDetector.onSensorChanged(timestamp, values);
                break;
            default:
        }

        emitTapsFromQueues(timestamp);
    }

    /**
     * Process a gyro event.
     */
    void onGyroscopeChanged(long timestamp, float values[]) {
        switch(mCurrentTapDetector) {
            case GYRO_ONLY_DETECTOR:
            case INTEGRATED_TAP_DETECTOR:
                mGyroTapDetector.onSensorChanged(timestamp, values);
                break;
            default:
        }

        emitTapsFromQueues(timestamp);
    }

    /**
     * Process arrival of more audio data
     */
    public void onNewAudioData(long timestamp, byte audioData[]) {
    }

    /**
     * Add a listener to be called back on the thread specified by Handler.
     */
    void addListener(TapListener listener, Handler handler) {
        synchronized (mListenerMap) {
            mListenerMap.put(listener, handler);
        }
    }

    /**
     * Add a listener on the current thread.
     */
    public void addListener(TapListener listener) {
        addListener(listener, new Handler());
    }

    /**
     * Remove a listener on the current thread.
     */
    public void removeListener(TapListener listener) {
        synchronized (mListenerMap) {
            mListenerMap.remove(listener);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Nothing to do
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                onAccelerometerChanged(System.nanoTime(), event.values);
                break;
            case Sensor.TYPE_GYROSCOPE:
                onGyroscopeChanged(System.nanoTime(), event.values);
                break;
            default:
        }
    }

    @Override
    public void threeDSensorTapDetected(
            ThreeDSensorTapDetector threeDSensorTapDetector, long timestamp, double tapConfidence) {
        if (threeDSensorTapDetector == mAccelTapDetector) {
            mAccelTapEventQueue.add(new Tap(tapConfidence, timestamp));
        }

        if (threeDSensorTapDetector == mGyroTapDetector) {
            mGyroTapEventQueue.add(new Tap(tapConfidence, timestamp));
        }

        emitTapsFromQueues(timestamp);
    }

    // visible for testing
    public void setLastTapNanoTime(long newTime) {
        mLastReportedTapTime = newTime;
    }

    /*
     * Emit or discard all pending taps from the integrated queue as single
     */
    // visible for testing
    public void flushPendingTaps() {
        emitTapsFromQueues(Long.MAX_VALUE);
    }


    /*
     * Send a double tap to all listeners
     */
    // visible for testing
    void sendDoubleTapToListeners(long timestampNanos) {
        final long finalTimestamp = timestampNanos;
        mLastReportedTapTime = timestampNanos;
        mHaveReportedAtLeastOneTap = true;

        // Pass double-tap along to all listeners
        synchronized (mListenerMap) {
            for (TapListener listener : mListenerMap.keySet()) {
                final TapListener finalListener = listener;
                long delay = mPostDelayTime - (System.nanoTime() - timestampNanos);
                delay = (delay > 0) ? delay : 0;
                mListenerMap.get(listener).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finalListener.onDoubleTap(finalTimestamp);
                    }
                }, delay);
            }
        }
    }

    /*
     * Send a single tap to all listeners
     */
    // visible for testing
    void sendSingleTapToListeners(long timestampNanos) {
        final long finalTimestamp = timestampNanos;
        mLastReportedTapTime = timestampNanos;
        mHaveReportedAtLeastOneTap = true;

        // Pass tap along to all listeners
        synchronized (mListenerMap) {
            for (TapListener listener : mListenerMap.keySet()) {
                final TapListener finalListener = listener;
                long delay = mPostDelayTime - (System.nanoTime() - timestampNanos) / MILIS_PER_NANO;
                delay = (delay > 0) ? delay : 0;
                mListenerMap.get(listener).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finalListener.onSingleTap(finalTimestamp);
                    }
                }, delay);
            }
        }
    }

    /*
     * Look at the queues for the individual tap detectors and see if there are taps that are
     * ready to emit.
     *
     * @param timestamp is the current timestamp.
     */
    private void emitTapsFromQueues(long timestamp) {
        integrateAccelAndGyroQueues(timestamp);

        processIntegratedQueueAsSingleAndDoubleTaps(timestamp);
    }

    /*
     * Combine taps from mAccelTapEventQueue and mGyroTapEventQueue into a single
     * mIntegratedTapEventQueue.
     *
     * @param timestamp is the current timestamp.
     */
    private void integrateAccelAndGyroQueues(long timestamp) {
        /*
         * When neither queue is empty, we have all of the information needed to process the most
         * recent tap.
         */
        while ((mGyroTapEventQueue.size() > 0) && (mAccelTapEventQueue.size() > 0)) {
            if (mGyroTapEventQueue.peek().nanos
                    > mAccelTapEventQueue.peek().nanos + MAX_OFFSET_BETWEEN_TAP_DETECTORS) {
                mIntegratedTapEventQueue.add(mAccelTapEventQueue.remove());
                continue;
            }

            if (mAccelTapEventQueue.peek().nanos
                    > mGyroTapEventQueue.peek().nanos + MAX_OFFSET_BETWEEN_TAP_DETECTORS) {
                mIntegratedTapEventQueue.add(mGyroTapEventQueue.remove());
                continue;
            }

            /*
             *  The two times are close enough that we can combine these taps.  We set the quality
             *  of this tap equal to the sum of that from the two detectors.
             */
            Tap accelTap = mAccelTapEventQueue.remove();
            Tap gyroTap = mGyroTapEventQueue.remove();
            Tap integratedTap = new Tap(
                    accelTap.quality + gyroTap.quality, Math.min(accelTap.nanos, gyroTap.nanos));
            mIntegratedTapEventQueue.add(integratedTap);
        }

        /*
         * Emit any taps we can from the non-empty queue. Note that if both queues are empty, the
         * while() loop never executes.
         */
        Queue<Tap> nonEmptyTapQueue =
                (mGyroTapEventQueue.size() > 0) ? mGyroTapEventQueue : mAccelTapEventQueue;
        while (nonEmptyTapQueue.size() > 0) {
            long latestTimeThatCantBeADoubleTap =
                    timestamp - MAX_OFFSET_BETWEEN_TAP_DETECTORS - MAX_TAP_DETECTOR_LATENCY;
            if (nonEmptyTapQueue.peek().nanos > latestTimeThatCantBeADoubleTap) {
                break;
            }

            if (DEBUG) {
                Log.v("Picidae", String.format(
                        "Adding tap at time %d from nonempty to integrated queue at time %d",
                        nonEmptyTapQueue.peek().nanos, timestamp));
            }

            mIntegratedTapEventQueue.add(nonEmptyTapQueue.remove());
        }
    }

    /*
     * Work through the integrated queue as far as possible, reporting single and double taps. One
     * tap may be left in the queue if insufficient time has elapsed to be sure it isn't a double
     * tap.
     */
    private void processIntegratedQueueAsSingleAndDoubleTaps(long timestamp) {
        while (mIntegratedTapEventQueue.size() >= 2) {
            Tap olderTap = mIntegratedTapEventQueue.remove();
            if (!tapAllowedAt(olderTap.nanos)) {
                if (DEBUG) {
                    Log.v("Picidae", String.format("Disallowing tap at time %d with quality %f",
                            olderTap.nanos, olderTap.quality));
                }
                continue;
            }

            Tap newerTap = mIntegratedTapEventQueue.peek();
            if (newerTap.nanos < olderTap.nanos + mMaxDoubleTapSpacingNanos) {
                /*
                 * Taps are close enough together. Must have one tap above min single-tap quality,
                 * and the other above min double-tap quality
                 */
                boolean qualityGoodEnough1 = (newerTap.quality >= mMinTapQuality)
                        && (olderTap.quality >= mMinDoubleTapQuality);
                boolean qualityGoodEnough2 = (newerTap.quality >= mMinDoubleTapQuality)
                        && (olderTap.quality >= mMinTapQuality);
                if (qualityGoodEnough1 || qualityGoodEnough2) {
                    sendDoubleTapToListeners(olderTap.nanos);
                    mIntegratedTapEventQueue.remove();
                    continue;
                }
            }

            /* Not a double tap. Check single-tap quality */
            if (olderTap.quality >= mMinTapQuality) {
                sendSingleTapToListeners(olderTap.nanos);
            } else if (DEBUG) {
                Log.v("Picidae", String.format("Discarding tap at time %d with quality %f",
                        olderTap.nanos, olderTap.quality));
            }
        }

        if (mIntegratedTapEventQueue.size() == 0) {
            return;
        }

        /* Make sure there's no way a second tap is coming before emitting single tap */
        long maxOffsetToConsider = (mMaxDoubleTapSpacingNanos > 0) ?
                MAX_OFFSET_BETWEEN_TAP_DETECTORS + MAX_TAP_DETECTOR_LATENCY : 0;
        if (mIntegratedTapEventQueue.peek().nanos
                <= timestamp - mMaxDoubleTapSpacingNanos - maxOffsetToConsider) {
            Tap tap = mIntegratedTapEventQueue.remove();
            if ((tap.quality >= mMinTapQuality) && tapAllowedAt(tap.nanos)) {
                sendSingleTapToListeners(tap.nanos);
            } else if (DEBUG) {
                Log.v("Picidae", String.format("Discarding tap at time %d with quality %f",
                        tap.nanos, tap.quality));
            }
        }
    }

    /* Check that a tap hasn't been sent out too recently */
    private boolean tapAllowedAt(long timestamp) {
        return ((timestamp - mLastReportedTapTime > mMinTapSpacingNanos)
                || !mHaveReportedAtLeastOneTap);
    }

    /**
     * Listener which is notified whenever a tap is detected.
     */
    public interface TapListener {
        // Invoked when tap occurs
        void onSingleTap(long timestampNanos);

        // Invoked on double-tap
        void onDoubleTap(long timestampNanos);
    }

    /**
     * List of different sensor tap detectors available for use
     */
    public enum TapDetector {
        /** Detector that integrates accelerometer and gyroscope tap detectors */
        INTEGRATED_TAP_DETECTOR,

        /** Detector that uses only accelerometer */
        ACCEL_ONLY_DETECTOR,

        /** Detector that uses only gyroscope */
        GYRO_ONLY_DETECTOR
    }

    /**
     * List of different sensor types that this detector integrates
     */
    public enum SensorTag {
        INVALID, ACCELEROMETER, GYROSCOPE, AUDIO
    }

    /**
     * List of different tap detection quality required to report a tap. Higher
     * qualities have lower false positive rates but are more likely to miss taps.
     * Some detectors - OTP, for example, are not affected by this value.
     */
    public enum TapDetectionQuality {
        HIGHEST, HIGH, MEDIUM, LOW
    }

    /* Simple pair-like class to hold taps. Not using pair for clarity. */
    private static class Tap {
        public final double quality;

        public final long nanos;

        public Tap(double qualityInit, long nanosInit) {
            quality = qualityInit;
            nanos = nanosInit;
        }
    }

}

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

import android.util.Log;

import java.util.LinkedList;


/**
 * Class to take updates from a sensor with three axes (accelerometer or gyroscope) and detect taps.
 * <p>
 * The detector consists of:
 * <ul>
 * <li>An infinite impulse response filter.
 * <li>A history buffer for filtered samples whose length (in time) is configurable.
 * <li>A noise detector that prevents taps from being detected when the average energy of the
 *     samples in the history buffer exceeds a threshold.
 * <li>A state machine that looks at the signal after a candidate tap to see if it stays within
 *     specified envelopes.
 * <li>A callback to a TapListener to report taps and their confidence.
 * </ul>
 * Two confidence values are possible: one for POSSIBLE_TAP and a higher one for DEFINITE_TAP. The
 * two types of taps have different thresholds and different required envelopes.  In addition, a
 * POSSIBLE_TAP can be detected even when the signal is noisy if the signal jumps beyond a multiple
 * of the noise.
 */
public class ThreeDSensorTapDetector {

    private static final boolean DEBUG = false;

    private static final int NUMBER_OF_DIMENSIONS = 3;

    private static final double POSSIBLE_TAP_CONFIDENCE = 0.15;

    private static final double DEFINITE_TAP_CONFIDENCE = 0.5;

    /** If there is a gap larger than this between two samples, the state machine resets. */
    private static final long MAX_PERMITTED_TIME_BETWEEN_SAMPLES_NANOS = 100 * 1000 * 1000;

    /** Keep track of the energy of the filtered signal for this long. */
    private static final long ENERGY_HISTORY_LENGTH_NANOS = 100 * 1000 * 1000;

    /** Force the state machine to assume the signal is noisy until it has this much history. */
    private static final long MIN_HISTORY_FOR_NOT_NOISY_NANOS = 80 * 1000 * 1000;

    private final TapListener mTapListener;

    /** The largest possible energy from the input signal */
    private final float mLargestMagSq;

    private float mConditionedSignalEnergy = 0f;

    private float mLastConditionedMagnitudeSq;

    /** The current state of the state machine */
    private SensorDetectorState mCurrentState;

    /** The last input received from the sensor */
    private float[] mLastInput = {
            0f, 0f, 0f };

    private float[] mLastFilterOutput = {
            0f, 0f, 0f };

    private final LinkedList<EnergySamplePair> mEnergySamplesList;

    private long mLastTimestamp;

    private long mCandidateTapStart;

    private ThreeDSensorTapDetectorType mDetectorType;

    /**
     * @param tapListener Receiver for tap updates
     * @param sensorMaxScale The maximum value available from the sensor (each axis)
     * @param type The type of detector - ACCELEROMETER or GYROSCOPE.
     */
    public ThreeDSensorTapDetector(TapListener tapListener, float sensorMaxScale,
            ThreeDSensorTapDetectorType type) {
        mLargestMagSq = NUMBER_OF_DIMENSIONS * sensorMaxScale * sensorMaxScale;
        mEnergySamplesList = new LinkedList<>();
        mTapListener = tapListener;
        mDetectorType = type;
        mLastTimestamp = 0;
        changeToNewCurrentState(0, SensorDetectorState.TOO_NOISY);
    }

    /** Call with updates from accelerometer sensor. Parameters are from the SensorEvent. */
    public void onSensorChanged(long timestamp, float values[]) {
        if (Math.abs(timestamp - mLastTimestamp) > MAX_PERMITTED_TIME_BETWEEN_SAMPLES_NANOS) {
            clearEnergySamplesList();
            if (DEBUG) {
                Log.v("threeDSensorTapDetector", "Discontinuity in input time");
            }
            if (mCurrentState != SensorDetectorState.TOO_NOISY) {
                changeToNewCurrentState(timestamp, SensorDetectorState.TOO_NOISY);
            }
        }
        mLastTimestamp = timestamp;

        /* High-pass filter each component, sum the squared magnitude */
        mLastConditionedMagnitudeSq = 0f;
        for (int i = 0; i < NUMBER_OF_DIMENSIONS; ++i) {
            mLastFilterOutput[i] = mDetectorType.filterNum[0] * values[i]
                    + mDetectorType.filterNum[1] * mLastInput[i]
                    - mDetectorType.filterDen[1] * mLastFilterOutput[i];
            mLastInput[i] = values[i];
            mLastConditionedMagnitudeSq += mLastFilterOutput[i] * mLastFilterOutput[i];
        }

        /*
         * Track the signal energy (for high-pass signal, nearly identical to variance) for the
         * recent past
         */
        mConditionedSignalEnergy += mLastConditionedMagnitudeSq;
        mEnergySamplesList.addLast(new EnergySamplePair(timestamp, mLastConditionedMagnitudeSq));
        while (mEnergySamplesList.getFirst().mTime <= timestamp - ENERGY_HISTORY_LENGTH_NANOS) {
            mConditionedSignalEnergy -= mEnergySamplesList.getFirst().mValue;
            mEnergySamplesList.removeFirst();
        }

        /* State machine for tap processing */
        if (DEBUG) {
            Log.v("threeDSensorTapDetector", String.format(
                    "State %s, CurrentEnergy %f, size %d, limit %f, signal %f",
                    mCurrentState.name(), mConditionedSignalEnergy, mEnergySamplesList.size(),
                    mEnergySamplesList.size() * mDetectorType.energyPerSampleNoiseLimit,
                    mLastConditionedMagnitudeSq));
        }

        switch (mCurrentState) {
            case NO_TAP:
                stateMachineNoTap(timestamp);
                break;

            case PROCESSING_CANDIDATE_DEFINITE_TAP:
                stateMachineProcessingDefiniteTap(timestamp);
                break;

            case PROCESSING_CANDIDATE_POSSIBLE_TAP:
                stateMachineProcessingPossibleTap(timestamp);
                break;

            case TOO_NOISY:
                stateMachineTooNoisy(timestamp);
                break;

            case DEFINITE_TAP:
            case POSSIBLE_TAP:
                /* Force signal to settle down after taps */
                changeToNewCurrentState(timestamp, SensorDetectorState.TOO_NOISY);
                break;

            default:
        }
    }

    // Visible for testing
    /* package */ float getConditionedSignalEnergy() {
        return mConditionedSignalEnergy;
    }

    // Visible for testing
    /* package */ float getLastConditionedMagnitudeSq() {
        return mLastConditionedMagnitudeSq;
    }

    /*
     * Current state is NO_TAP.
     * Transition to PROCESSING_CANDIDATE_DEFINITE_TAP or PROCESSING_CANDIDATE_POSSIBLE_TAP
     * if above corresponding thresholds.
     * Transition to TOO_NOISY if history buffer contains too much signal energy
     */
    private void stateMachineNoTap(long timestamp) {
        if (mLastConditionedMagnitudeSq > mDetectorType.thresholdForDefiniteTap) {
            changeToNewCurrentState(timestamp,
                    SensorDetectorState.PROCESSING_CANDIDATE_DEFINITE_TAP);
            mCandidateTapStart = timestamp;
        } else if (mLastConditionedMagnitudeSq > mDetectorType.thresholdForPossibleTap) {
            changeToNewCurrentState(timestamp,
                    SensorDetectorState.PROCESSING_CANDIDATE_POSSIBLE_TAP);
            mCandidateTapStart = timestamp;
        } else if (mConditionedSignalEnergy
                > mEnergySamplesList.size() * mDetectorType.energyPerSampleNoiseLimit) {
            changeToNewCurrentState(timestamp, SensorDetectorState.TOO_NOISY);
        }
    }

    /*
     * Current state is PROCESSING_CANDIDATE_DEFINITE_TAP.
     * Transition to PROCESSING_CANDIDATE_POSSIBLE_TAP if the signal doesn't stay within the
     * definite tap envelope. If this transition happens, call the state machine processing for
     * PROCESSING_CANDIDATE_POSSIBLE_TAP. Transition to DEFINITE_TAP if we've been inside the
     * envelope long enough.
     */
    private void stateMachineProcessingDefiniteTap(long timestamp) {
        /*
         * Interpolate from full-scale down to low level. This limit will exceed full scale until
         * highAmplitudeTime expires, and that's fine as the idea is that there is no limit at that
         * point.
         */
        long x1 = mCandidateTapStart + mDetectorType.definiteTapsHighAmplitudeTimeNanos;
        float y1 = mLargestMagSq;
        long x2 = x1 + mDetectorType.definiteTapsFallTimeNanos;
        float y2 = mDetectorType.definiteTapsLowLevel;
        float envelope = y1 + (y2 - y1) * ((float) timestamp - x1) / ((float) x2 - x1);

        /* Force envelope to be at least lowLevel */
        envelope = Math.max(envelope, mDetectorType.definiteTapsLowLevel);

        if (mLastConditionedMagnitudeSq > envelope) {
            /* The signal isn't dying down nicely. Downgrade the tap. */
            if (DEBUG) {
                Log.v("threeDSensorTapDetector", String.format(
                        "Tap downgraded to possible at %d. Signal %f limit %f", timestamp,
                        mLastConditionedMagnitudeSq, envelope));
            }

            changeToNewCurrentState(
                    timestamp, SensorDetectorState.PROCESSING_CANDIDATE_POSSIBLE_TAP);
            stateMachineProcessingPossibleTap(timestamp);
        } else if (timestamp > x2 + mDetectorType.definiteTapsLowTimeNanos) {
            changeToNewCurrentState(mCandidateTapStart, SensorDetectorState.DEFINITE_TAP);
        }
    }

    /*
     * Current state is PROCESSING_CANDIDATE_POSSIBLE_TAP.
     * Transition to TOO_NOISY if the signal doesn't stay within the possible tap envelope.
     * Transition to POSSIBLE_TAP if we've been inside the envelope long enough.
     */
    private void stateMachineProcessingPossibleTap(long timestamp) {
        /*
         * Interpolate from full-scale down to low level. This limit will exceed full scale until
         * highAmplitudeTime expires, and that's fine as the idea is that there is no limit at that
         * point.
         */
        long x1 = mCandidateTapStart + mDetectorType.possibleTapsHighAmplitudeTimeNanos;
        float y1 = mLargestMagSq;
        long x2 = x1 + mDetectorType.possibleTapsFallTimeNanos;
        float y2 = mDetectorType.possibleTapsLowLevel;
        float envelope = y1 + (y2 - y1) * ((float) timestamp - x1) / ((float) x2 - x1);

        /* Force envelope to be at least lowLevel */
        envelope = Math.max(envelope, mDetectorType.possibleTapsLowLevel);

        if (mLastConditionedMagnitudeSq > envelope) {
            if (DEBUG) {
                Log.v("threeDSensorTapDetector", String.format(
                        "Tap downgraded to noise at %d. Signal %f limit %f", timestamp,
                        mLastConditionedMagnitudeSq, envelope));
            }

            changeToNewCurrentState(timestamp, SensorDetectorState.TOO_NOISY);
        } else if (timestamp > x2 + mDetectorType.possibleTapsLowTimeNanos) {
            changeToNewCurrentState(mCandidateTapStart, SensorDetectorState.POSSIBLE_TAP);
        }

    }

    /*
     * Current state is TOO_NOISY.
     * Stay in this state until we have decent amount of signal history.
     * Transition to NO_TAP if the signal history has low energy.
     * Transition to PROCESSING_CANDIDATE_POSSIBLE_TAP if we get a tap well above the current noise
     * level.
     */
    private void stateMachineTooNoisy(long timestamp) {

        /* Stay in this state until we have enough history to judge the signal */
        long timeSpanInHistoryNanos = mEnergySamplesList.getLast().mTime
                - mEnergySamplesList.getFirst().mTime;
        if (timeSpanInHistoryNanos < MIN_HISTORY_FOR_NOT_NOISY_NANOS) {
            return;
        }

        /* Allow a possible tap on a sudden jump in signal energy */
        if (mLastConditionedMagnitudeSq * mEnergySamplesList.size()
                > mDetectorType.multipleOfNoiseForPossibleTap * mConditionedSignalEnergy) {
            changeToNewCurrentState(
                    timestamp, SensorDetectorState.PROCESSING_CANDIDATE_POSSIBLE_TAP);
            mCandidateTapStart = timestamp;
            return;
        }

        /* Check if signal isn't noisy anymore */
        if (mConditionedSignalEnergy <
                mEnergySamplesList.size() * mDetectorType.energyPerSampleNoiseLimit) {
            changeToNewCurrentState(timestamp, SensorDetectorState.NO_TAP);
        }
    }

    private void changeToNewCurrentState(long timestamp, SensorDetectorState newState) {
        mCurrentState = newState;
        if (DEBUG) {
            Log.v("threeDSensorTapDetector", String.format("Transition to State %s at time %d",
                    mCurrentState.name(), timestamp));
        }

        if (newState == SensorDetectorState.POSSIBLE_TAP) {
            mTapListener.threeDSensorTapDetected(this, timestamp, POSSIBLE_TAP_CONFIDENCE);
        }

        if (newState == SensorDetectorState.DEFINITE_TAP) {
            mTapListener.threeDSensorTapDetected(this, timestamp, DEFINITE_TAP_CONFIDENCE);
        }

    }

    private void clearEnergySamplesList() {
        mEnergySamplesList.clear();
        mConditionedSignalEnergy = 0;
    }

    /**
     * Listener to receive detected taps
     */
    public interface TapListener {
        /**
         * Callback for a detected tap
         *
         * @param threeDSensorTapDetector The detector sending the update
         * @param timestamp The timestamp of the detected tap
         * @param tapConfidence The confidence of the tap. 0.0 is no confidence, and 1.0 is
         * absolute certainty.
         */
        public void threeDSensorTapDetected(ThreeDSensorTapDetector threeDSensorTapDetector,
                long timestamp, double tapConfidence);
    }

    /**
     * List of states that detectors for individual sensors can be in
     */
    private enum SensorDetectorState {
        /** Detector is confident that no tap has occurred */
        NO_TAP,

        /** Detector is confident a tap has occurred */
        DEFINITE_TAP,

        /** Detector thinks a tap may have occurred */
        POSSIBLE_TAP,

        /** Detector sees a signal too noisy to know whether or not a tap happened */
        TOO_NOISY,

        /** Intermediate step when detector is processing a candidate possible tap */
        PROCESSING_CANDIDATE_POSSIBLE_TAP,

        /** Intermediate step when detector is processing a candidate definite tap */
        PROCESSING_CANDIDATE_DEFINITE_TAP,
    }

    /*
     * Hold a (time, value) object for the history buffer
     */
    private class EnergySamplePair {
        public long mTime;

        public float mValue;

        public EnergySamplePair(long time, float value) {
            mTime = time;
            mValue = value;
        }
    }

}

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

/**
 * Specific types of tap detectors for accelerometer and gyroscope that
 * initialize ThreeDSensorTapDetector. Encapsulates the large number of parameters
 * needed to support different sensor types.
 */
@SuppressWarnings("PointlessArithmeticExpression")
public enum ThreeDSensorTapDetectorType {
    ACCELEROMETER(
            new float[] {0.8f, -0.8f} /* filterNum */,
            new float[] {1.0f, -0.8f} /* filterDen */,
            1.0f /* energyPerSampleNoiseLimit */,
            5f /* multipleOfNoiseForPossibleTap */,
            30 * 1000 * 1000 /* definiteTapsHighAmplitudeTimeNanos */,
            40 * 1000 * 1000 /* possibleTapsHighAmplitudeTimeNanos */,
            70 * 1000 * 1000 /* definiteTapsFallTimeNanos */,
            60 * 1000 * 1000 /* possibleTapsFallTimeNanos */,
            3f /* definiteTapsLowLevel */,
            5f /* possibleTapsLowLevel */,
            0 /* definiteTapsLowTimeNanos */,
            0 /* possibleTapsLowTimeNanos */),
    GYROSCOPE(
            new float[] {1.0f, -1.0f} /* filterNum */,
            new float[] {1.0f, 0.0f} /* filterDen */,
            0.05f /* energyPerSampleNoiseLimit */,
            4f /* multipleOfNoiseForPossibleTap */,
            30 * 1000 * 1000 /* definiteTapsHighAmplitudeTimeNanos */,
            40 * 1000 * 1000 /* possibleTapsHighAmplitudeTimeNanos */,
            0 * 1000 * 1000 /* definiteTapsFallTimeNanos */,
            0 * 1000 * 1000 /* possibleTapsFallTimeNanos */,
            0.25f /* definiteTapsLowLevel */,
            0.5f /* possibleTapsLowLevel */,
            70 * 1000 * 1000 /* definiteTapsLowTimeNanos */,
            60 * 1000 * 1000 /* possibleTapsLowTimeNanos */);

    /**
     * The three axes are all filtered with an IIR filter. This filter must be
     * be first order, so both mFilterNum and mFilterDen have length 2.
     * mFilterDen[0] is ignored and assumed to be 1.0f.
     */
    public final float filterNum[];

    public final float filterDen[];

    /**
     * Limit to declare a signal noisy. Units are sensor units squared.
     */
    public final float energyPerSampleNoiseLimit;

    /**
     * Start processing a possible tap if we're above this multiple of current
     * noise during NOISY state
     */
    public final float multipleOfNoiseForPossibleTap;

    /**
     * Threshold for starting to detect a possible tap in the absence of noise
     */
    public final float thresholdForPossibleTap;

    /**
     * Threshold for starting to detect a definite tap in the absence of noise
     */
    public final float thresholdForDefiniteTap;

    /**
     * Once we've starting detecting a tap, we want to see its energy stay inside an
     * envelope. The envelope is infinite for the HighAmplitudeTime, and then
     * drops to the maximum detected energy and then falls linearly to the
     * LowLevel during the FallTime. It then stays at LowLevel for LowTime.
     */

    public final long definiteTapsHighAmplitudeTimeNanos;

    public final long possibleTapsHighAmplitudeTimeNanos;

    public final long definiteTapsFallTimeNanos;

    public final long possibleTapsFallTimeNanos;

    public final float definiteTapsLowLevel;

    public final float possibleTapsLowLevel;

    public final long definiteTapsLowTimeNanos;

    public final long possibleTapsLowTimeNanos;

    /**
     * @param filterNumInit IIR filter numerator. Must have length 2.
     * @param filterDenInit IIR filter denominator. Must have length 2.
     * @param energyPerSampleNoiseLimitInit Limit for detecting noise. Sensor
     *            units.
     * @param multipleOfNoiseForPossibleTapInit Threshold to detect possible tap
     *            when signal is noisy
     * @param definiteTapsHighAmplitudeTimeNanosInit Time definite taps may be
     *            high amplitude
     * @param possibleTapsHighAmplitudeTimeNanosInit Time possible taps may be
     *            high amplitude
     * @param definiteTapsFallTimeNanosInit Time definite taps must drop from
     *            high to low amplitude
     * @param possibleTapsFallTimeNanosInit Time possible taps must drop from
     *            high to low amplitude
     * @param definiteTapsLowLevelInit Low level of definite tap envelope
     * @param possibleTapsLowLevelInit Low level of possible tap envelope
     * @param definiteTapsLowTimeNanosInit Time definite taps must stay at low
     *            level
     * @param possibleTapsLowTimeNanosInit Time possible taps must stay at low
     *            level
     */
    private ThreeDSensorTapDetectorType(float[] filterNumInit, float[] filterDenInit,
            float energyPerSampleNoiseLimitInit, float multipleOfNoiseForPossibleTapInit,
            long definiteTapsHighAmplitudeTimeNanosInit,
            long possibleTapsHighAmplitudeTimeNanosInit, long definiteTapsFallTimeNanosInit,
            long possibleTapsFallTimeNanosInit, float definiteTapsLowLevelInit,
            float possibleTapsLowLevelInit, long definiteTapsLowTimeNanosInit,
            long possibleTapsLowTimeNanosInit) {
        filterNum = filterNumInit;
        filterDen = filterDenInit;
        energyPerSampleNoiseLimit = energyPerSampleNoiseLimitInit;
        multipleOfNoiseForPossibleTap = multipleOfNoiseForPossibleTapInit;
        thresholdForPossibleTap =
                energyPerSampleNoiseLimitInit * multipleOfNoiseForPossibleTapInit;
        thresholdForDefiniteTap = 2f * thresholdForPossibleTap;
        definiteTapsHighAmplitudeTimeNanos = definiteTapsHighAmplitudeTimeNanosInit;
        possibleTapsHighAmplitudeTimeNanos = possibleTapsHighAmplitudeTimeNanosInit;
        definiteTapsFallTimeNanos = definiteTapsFallTimeNanosInit;
        possibleTapsFallTimeNanos = possibleTapsFallTimeNanosInit;
        definiteTapsLowLevel = definiteTapsLowLevelInit;
        possibleTapsLowLevel = possibleTapsLowLevelInit;
        definiteTapsLowTimeNanos = definiteTapsLowTimeNanosInit;
        possibleTapsLowTimeNanos = possibleTapsLowTimeNanosInit;
    }


}

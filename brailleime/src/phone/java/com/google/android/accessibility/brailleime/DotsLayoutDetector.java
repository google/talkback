/*
 * Copyright 2019 Google Inc.
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

package com.google.android.accessibility.brailleime;

import static android.content.Context.SENSOR_SERVICE;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;

/** Auto detects whether the device is tabletop or screen away. */
public class DotsLayoutDetector {
  private final Context context;
  private final SensorManager sensorManager;
  private final AutoDetectCallback callback;
  private Optional<DotsLayout> autoModeLayout;

  /** Callback for clients of this class. */
  interface AutoDetectCallback {
    boolean useSensorsToDetectLayout();

    void onDetectionChanged(boolean isTabletop, boolean firstChangedEvent);
  }

  public DotsLayoutDetector(Context context, AutoDetectCallback autoDetectCallback) {
    this.context = context;
    this.callback = autoDetectCallback;
    this.autoModeLayout = Optional.empty();
    this.sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
  }

  /** Starts listening to sensors that inform the orientation detection */
  public void startIfNeeded() {
    if (callback.useSensorsToDetectLayout()) {
      Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
      sensorManager.registerListener(
          sensorEventListener, accelerometer, SensorManager.SENSOR_STATUS_ACCURACY_HIGH);
    }
  }

  /** Stops listening to sensors that inform the orientation detection */
  public void stop() {
    sensorManager.unregisterListener(sensorEventListener);
    autoModeLayout = Optional.empty();
  }

  /** Returns detected layout. Empty before sensor receives events. */
  public Optional<DotsLayout> getDetectedLayout() {
    return autoModeLayout;
  }

  private final SensorEventListener sensorEventListener =
      new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
          float[] sensorEventValues =
              Utils.adjustAccelOrientation(
                  Utils.getDisplayRotationDegrees(context), sensorEvent.values);
          boolean isFlat = Utils.isFlat(sensorEventValues);
          boolean firstChangedEvent = !autoModeLayout.isPresent();
          DotsLayout newLayout = isFlat ? DotsLayout.TABLETOP : DotsLayout.SCREEN_AWAY;
          boolean shouldChange = firstChangedEvent || (autoModeLayout.get() != newLayout);
          autoModeLayout = Optional.of(newLayout);
          if (shouldChange) {
            callback.onDetectionChanged(isFlat, firstChangedEvent);
          }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {}
      };

  @VisibleForTesting
  SensorEventListener getSensorEventListener() {
    return sensorEventListener;
  }
}

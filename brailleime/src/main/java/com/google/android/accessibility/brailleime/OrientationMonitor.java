/*
 * Copyright 2020 Google Inc.
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

import android.content.Context;
import android.view.OrientationEventListener;
import com.google.common.annotations.VisibleForTesting;

/**
 * Monitors the orientation changed event even when device auto rotate is locked.
 *
 * <p>OrientationMonitor is a singleton. The calling sequence is as below.
 *
 * <p>1. Call {@link #init(Context)} <b>once</b> to make sure OrientationMonitor has been
 * initialized.
 *
 * <p>2. Call {@link #enable()} to start monitor orientation changed events from system.
 */
public class OrientationMonitor {
  /** Screen orientations. */
  public enum Orientation {
    UNKNOWN(-1),
    PORTRAIT(0),
    LANDSCAPE(90),
    REVERSE_PORTRAIT(180),
    REVERSE_LANDSCAPE(270),
    ;

    private final int degree;

    Orientation(int degree) {
      this.degree = degree;
    }

    public int getDegree() {
      return degree;
    }
  }

  /** Callback register in {@link OrientationMonitor}. */
  public interface Callback {
    void onOrientationChanged(Orientation orientation);
  }

  private static OrientationMonitor singleton;
  private Callback orientationMonitorCallback;
  private boolean enabled;
  private Orientation currentOrientation = Orientation.UNKNOWN;
  private OrientationEventListener orientationEventListener;

  public static OrientationMonitor getInstance() {
    if (singleton == null) {
      throw new IllegalStateException("You forget to call init()!");
    }
    return singleton;
  }

  public static void init(Context context) {
    if (singleton == null) {
      singleton = new OrientationMonitor();
      singleton.orientationEventListener =
          new OrientationEventListener(context.getApplicationContext()) {
            @Override
            public void onOrientationChanged(int degree) {
              degree = Utils.isDeviceDefaultPortrait(context) ? degree : degree - 90;
              Orientation newOrientation = convertToOrientation(degree);
              if (newOrientation != singleton.currentOrientation) {
                singleton.currentOrientation = newOrientation;
                if (singleton.orientationMonitorCallback != null) {
                  singleton.orientationMonitorCallback.onOrientationChanged(newOrientation);
                }
              }
            }

            private Orientation convertToOrientation(int degree) {
              for (Orientation orientation : Orientation.values()) {
                if (orientation != Orientation.UNKNOWN
                    && Math.abs(degree - orientation.getDegree()) <= 45) {
                  return orientation;
                }
              }
              // Special case for portrait when the degrees is close to 360.
              if (Math.abs(degree - 360) <= 45) {
                return Orientation.PORTRAIT;
              }
              return Orientation.UNKNOWN;
            }
          };
    }
  }

  public void enable() {
    if (enabled) {
      return;
    }
    enabled = true;
    orientationEventListener.enable();
  }

  public void disable() {
    if (!enabled) {
      return;
    }
    enabled = false;
    orientationEventListener.disable();
  }

  public Orientation getCurrentOrientation() {
    return currentOrientation;
  }

  public void registerCallback(Callback orientationMonitorCallback) {
    this.orientationMonitorCallback = orientationMonitorCallback;
  }

  public void unregisterCallback() {
    orientationMonitorCallback = null;
  }

  @VisibleForTesting
  public void testing_setOrientationEventListener(OrientationEventListener listener) {
    orientationEventListener = listener;
  }

  @VisibleForTesting
  public OrientationEventListener testing_getOrientationEventListener() {
    return orientationEventListener;
  }
}

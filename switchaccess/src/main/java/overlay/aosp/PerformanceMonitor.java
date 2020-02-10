/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.switchaccess;

import android.app.Application;
import android.content.Context;

/**
 * Helper class for setting up Android performance monitoring. This class currently does nothing.
 */
public class PerformanceMonitor {

  /** Represents different types of events that can be monitored. */
  public interface PerformanceMonitorEvent {}

  /** A monitored event that is created by the system. */
  public enum TreeBuildingEvent implements PerformanceMonitorEvent {
    REBUILD_TREE_AND_UPDATE_FOCUS,
    REBUILD_TREE,
    SCREEN_VISIBILITY_UPDATE
  }

  /** A monitored event for a key of unknown assignment that is triggered by a key press. */
  public enum KeyPressEvent implements PerformanceMonitorEvent {
    UNKNOWN_KEY_ASSIGNMENT,
    ASSIGNED_KEY_DETECTED,
    UNASSIGNED_KEY_DETECTED
  }

  /** A monitored event that is triggered when a key assignment is known. */
  public enum KeyPressAction implements PerformanceMonitorEvent {
    UNKNOWN_KEY,
    KEYBOARD_ACTION_RUNNABLE_EXECUTED,
    SCAN_START,
    SCAN_REVERSE_START,
    SCAN_MOVE_FORWARD,
    SCAN_MOVE_BACKWARD,
    ITEM_SELECTED,
    GLOBAL_ACTION
  }

  private PerformanceMonitor() {}

  private static PerformanceMonitor instance;

  public static PerformanceMonitor getOrCreateInstance() {
    if (instance == null) {
      instance = new PerformanceMonitor();
    }

    return instance;
  }

  public void initializePerformanceMonitoringIfNotInitialized(
      Context context, Application application) {}

  public void startNewTimerEvent(PerformanceMonitorEvent event) {}

  public void stopTimerEvent(PerformanceMonitorEvent event, boolean appendScanningMethod) {}

  public void cancelTimerEvent(PerformanceMonitorEvent event, boolean appendScanningMethod) {}

  public void shutdown() {}
}

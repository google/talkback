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

package com.google.android.libraries.accessibility.utils.eventfilter;

import android.view.accessibility.AccessibilityEvent;

/**
 * Listener for a11y events. Each class that extends this should specify a mask of the events it
 * would handle and return that mask by implementing {@link #getEventTypes()} method.
 */
// TODO: Implement this interface instead of the android/accessibility/utils one in
// services that don't need to use the EventId.
public interface AccessibilityEventListener {

  /** Returns the mask of the events to be handled. */
  int getEventTypes();

  /**
   * Receives the events that are specified in the mask returned by {@link #getEventTypes()} method.
   */
  void onAccessibilityEvent(AccessibilityEvent event);
}

/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement.interpreter;

import android.accessibilityservice.AccessibilityService;
import androidx.annotation.Nullable;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WindowEventInterpreter.EventInterpretation;
import com.google.android.accessibility.utils.WindowEventInterpreter.WindowEventHandler;
import com.google.android.accessibility.utils.WindowsDelegate;
import java.util.ArrayList;
import java.util.List;

/**
 * A class works as a detector for {@link ScreenState} changes and notifies {@link
 * ScreenStateChangeListener} of the change.
 *
 * <p><strong>Note: </strong>To make this feature works, the {@link AccessibilityService} has to
 * declare the capability to retrieve window content by setting the {@code
 * android.R.styleable#AccessibilityService_canRetrieveWindowContent} property in its meta-data.
 * Also, the service has to opt-in to retrieve the interactive windows by setting the {@link
 * android.accessibilityservice.AccessibilityServiceInfo#FLAG_RETRIEVE_INTERACTIVE_WINDOWS} flag.
 */
public class ScreenStateMonitor implements WindowEventHandler {

  /** Listens to {@link ScreenState} changes. */
  public interface ScreenStateChangeListener {
    /**
     * Callback when {@link ScreenState} changes.
     *
     * @return {@code true} if any accessibility action is successfully performed.
     */
    boolean onScreenStateChanged(ScreenState screenState, EventId eventId);
  }

  private WindowsDelegate windowsDelegate;
  private final AccessibilityService service;
  private final List<ScreenStateChangeListener> listeners =
      new ArrayList<ScreenStateChangeListener>();
  private ScreenState currentScreenState;

  public ScreenStateMonitor(AccessibilityService service) {
    this.service = service;
  }

  @Override
  public void handle(
      EventInterpretation interpretation,
      @org.checkerframework.checker.nullness.qual.Nullable EventId eventId) {
    if (interpretation.getMainWindowsChanged() && interpretation.areWindowsStable()) {
      AccessibilityWindowInfo activeWindow =
          AccessibilityServiceCompatUtils.getActiveWidow(service);
      currentScreenState =
          new ScreenState(windowsDelegate, activeWindow, interpretation.getEventStartTime());
      onScreenStateChanged(currentScreenState, eventId);
    }
  }

  public void setWindowsDelegate(WindowsDelegate delegate) {
    windowsDelegate = delegate;
  }

  public void addScreenStateChangeListener(ScreenStateChangeListener listener) {
    if (listener == null) {
      throw new IllegalArgumentException("Listener must not be null.");
    }
    listeners.add(listener);
  }

  @Nullable
  public ScreenState getCurrentScreenState() {
    return currentScreenState;
  }

  /** Notifies {@link ScreenStateChangeListener} of the {@link ScreenState} changes. */
  private void onScreenStateChanged(ScreenState screenState, EventId eventId) {
    for (ScreenStateChangeListener listener : listeners) {
      listener.onScreenStateChanged(screenState, eventId);
    }
  }
}

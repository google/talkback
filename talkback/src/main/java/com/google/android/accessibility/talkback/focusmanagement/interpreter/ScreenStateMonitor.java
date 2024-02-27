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
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.monitor.InputMethodMonitor;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.input.WindowEventInterpreter.EventInterpretation;
import com.google.android.accessibility.utils.input.WindowEventInterpreter.WindowEventHandler;
import com.google.android.accessibility.utils.input.WindowsDelegate;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A class works as a detector for {@link ScreenState} changes from {@link WindowEventHandler} and
 * notifies {@link ScreenStateChangeListener} of the change.
 *
 * <p><strong>Note: </strong>The detector is mainly used to notify {@link ScreenStateChangeListener}
 * although it also caches the read-only {@code State} for convenience. Be careful to use the cache
 * data because they might not be able to accurately reflect the latest screen state due to the
 * delayed interpretations and abusing of {@link AccessibilityEvent#TYPE_WINDOW_STATE_CHANGED}. It
 * is better to have a fallback plan while using the cache data. For example, {@link
 * AccessibilityFocusActionHistory} reads {@code State} to cache focus for restoration and there
 * should be another focus-searching way triggered if the restoration fails. Otherwise using {@link
 * AccessibilityService#getWindows()} to get the real-time window data.
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

  /** Read-only interface for ScreenStateMonitor-state data. */
  public class State {
    /** Returns true if the active window or split-windows are stable. */
    public boolean areMainWindowsStable() {
      return ScreenStateMonitor.this.areMainWindowsStable;
    }
    /** Returns the {@link ScreenState} from the current stable windows. */
    public @Nullable ScreenState getStableScreenState() {
      return ScreenStateMonitor.this.stableScreenState;
    }
  }

  public final ScreenStateMonitor.State state = new ScreenStateMonitor.State();

  private WindowsDelegate windowsDelegate;
  private final AccessibilityService service;
  private final InputMethodMonitor inputMethodMonitor;
  private final List<ScreenStateChangeListener> listeners = new ArrayList<>();
  private ScreenState stableScreenState;
  private boolean areMainWindowsStable;

  public ScreenStateMonitor(
      @NonNull AccessibilityService service, @NonNull InputMethodMonitor inputMethodMonitor) {
    this.service = service;
    this.inputMethodMonitor = inputMethodMonitor;
  }

  @Override
  public void handle(EventInterpretation interpretation, @Nullable EventId eventId) {
    areMainWindowsStable = !interpretation.getMainWindowsChanged();
    boolean windowsChanged =
        interpretation.getMainWindowsChanged()
            || (interpretation.getInputMethodChanged()
                && inputMethodMonitor.useInputWindowAsActiveWindow());
    if (windowsChanged && interpretation.areWindowsStable()) {
      areMainWindowsStable = true;
      AccessibilityWindowInfo activeWindow =
          AccessibilityServiceCompatUtils.getActiveWidow(service);
      if (inputMethodMonitor.useInputWindowAsActiveWindow()
          && AccessibilityServiceCompatUtils.isInputWindowOnScreen(service)) {
        activeWindow = inputMethodMonitor.getActiveInputWindow();
      }

      stableScreenState =
          new ScreenState(
              windowsDelegate,
              activeWindow,
              interpretation.getEventStartTime(),
              interpretation.isInterpretFirstTimeWhenWakeUp());
      onScreenStateChanged(stableScreenState, eventId);
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

  /** Notifies {@link ScreenStateChangeListener} of the {@link ScreenState} changes. */
  private void onScreenStateChanged(ScreenState screenState, EventId eventId) {
    for (ScreenStateChangeListener listener : listeners) {
      listener.onScreenStateChanged(screenState, eventId);
    }
  }
}

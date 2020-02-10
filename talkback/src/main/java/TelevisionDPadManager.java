/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.accessibility.talkback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.annotation.IntDef;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.controller.TelevisionNavigationController;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Manages the state whether TalkBack should consume DPad KeyEvents. This class only works on TV
 * devices, when {@link TelevisionNavigationController} is enabled.
 *
 * <p>Some Android TV apps deal with accessibility in a special way. (e.g. Netflix does not expose
 * layout hierarchy to accessibility services, the entire window is exposed as one single view. In
 * TalkBack, it's impossible to use remote control to put accessibility focus on elements. However,
 * Netflix maintains its own focus and provides spoken feedback. In this case, TalkBack should not
 * block and consume D-pad KeyEvent, so that users can perform native navigation in the app.)
 * TalkBack should create exception to suspend control of D-pad KeyEvent for these apps.
 *
 * <p>This class is a temporary workaround for this special use case. It allows white-listed apps to
 * request TalkBack to suspend/resume its control over D-pad KeyEvents. D-pad KeyEvent control is
 * automatically resumed if the requester app is closed.
 */
public class TelevisionDPadManager extends BroadcastReceiver implements AccessibilityEventListener {
  // TalkBack is consuming the D-pad events.
  private static final int STATE_RESUMED = 0;
  // TalkBack is consuming the D-pad events, and there is a pending suspend_control request.
  private static final int STATE_RESUMED_PENDING = 1;
  // TalkBack is not consuming the D-pad events.
  private static final int STATE_SUSPENDED = 2;
  // TalkBack is not consuming the D-pad events, and there is a pending suspend_control request.
  private static final int STATE_SUSPENDED_PENDING = 3;

  /** Event types that are handled by TelevisionDPadManager. */
  private static final int MASK_EVENTS_HANDLED_BY_TELEVISION_DPAD_MANAGER =
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;

  @IntDef({
    STATE_RESUMED, STATE_RESUMED_PENDING,
    STATE_SUSPENDED, STATE_SUSPENDED_PENDING
  })
  @Retention(RetentionPolicy.SOURCE)
  @interface State {}

  private static final boolean DEBUG = false;
  private static final String TAG = "DPadManager";

  /** The list of apps who can request for control over D-pad KeyEvents. */
  private static final String[] WHITE_LIST = {
    PackageManagerUtils.TALBACK_PACKAGE, "com.netflix.ninja"
  };

  private static final IntentFilter INTENT_FILTER = new IntentFilter();

  static {
    INTENT_FILTER.addAction(TalkBackService.ACTION_RESUME_DPAD_CONTROL);
    INTENT_FILTER.addAction(TalkBackService.ACTION_SUSPEND_DPAD_CONTROL);
  }

  private final TelevisionNavigationController tvNavigationController;

  /** Package name of current activity window on screen. */
  private String currentWindowPackageName = "";

  @State private int state = STATE_RESUMED; // TalkBack consumes D-pad events by default.

  public TelevisionDPadManager(TelevisionNavigationController tvNavigationController) {
    if (tvNavigationController == null) {
      throw new IllegalArgumentException();
    }
    this.tvNavigationController = tvNavigationController;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    final String currentWindow = currentWindowPackageName;
    final String action = intent.getAction();
    if (DEBUG) {
      LogUtils.d(
          TAG, "Request received: " + action + "; Current control state: " + stateToString(state));
    }
    if (TalkBackService.ACTION_SUSPEND_DPAD_CONTROL.equals(action)) {
      switch (state) {
        case STATE_RESUMED:
          if (suspendDPadControl(currentWindow)) {
            if (DEBUG) {
              LogUtils.d(TAG, "D-pad control suspended for " + currentWindow);
            }
            setState(STATE_SUSPENDED);
          } else {
            if (DEBUG) {
              LogUtils.d(TAG, "Pending request: suspend D-pad control.");
            }
            setState(STATE_RESUMED_PENDING);
          }
          break;
        case STATE_SUSPENDED:
          if (DEBUG) {
            LogUtils.d(TAG, "Pending request: suspend D-pad control.");
          }
          setState(STATE_SUSPENDED_PENDING);
          break;
        case STATE_RESUMED_PENDING:
        case STATE_SUSPENDED_PENDING:
        default:
          // Do nothing
          if (DEBUG) {
            LogUtils.d(TAG, "Request unhandled.");
          }
          break;
      }
    } else if (TalkBackService.ACTION_RESUME_DPAD_CONTROL.equals(action)) {
      if (DEBUG) {
        LogUtils.d(TAG, "Request to resume D-pad control. D-pad control resumed.");
      }
      resumeDPadControl();
      setState(STATE_RESUMED);
    }
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_TELEVISION_DPAD_MANAGER;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, Performance.EventId eventId) {
    if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      return;
    }
    final String packageName =
        event.getPackageName() == null ? "" : event.getPackageName().toString();

    if (!TextUtils.equals(currentWindowPackageName, packageName)) {
      if (DEBUG) {
        LogUtils.d(
            TAG, "Window changed. " + "From: " + currentWindowPackageName + "; To: " + packageName);
      }
      currentWindowPackageName = packageName;
      onWindowChanged();
    }
  }

  public static IntentFilter getFilter() {
    return INTENT_FILTER;
  }

  private void setState(@State int state) {
    if (this.state != state) {
      if (DEBUG) {
        LogUtils.d(
            TAG, "State changed from " + stateToString(this.state) + " to " + stateToString(state));
      }
      this.state = state;
    }
  }

  private void onWindowChanged() {
    final String currentWindow = currentWindowPackageName;
    switch (state) {
      case STATE_RESUMED_PENDING:
        if (suspendDPadControl(currentWindow)) {
          if (DEBUG) {
            LogUtils.d(TAG, "D-pad control suspended for " + currentWindow);
          }
          setState(STATE_SUSPENDED);
        } else {
          if (DEBUG) {
            LogUtils.d(TAG, "Resume D-pad control for unverified requester.");
          }
          setState(STATE_RESUMED);
        }
        break;
      case STATE_SUSPENDED:
        if (DEBUG) {
          LogUtils.d(TAG, "Resume D-pad control for the window change.");
        }
        resumeDPadControl();
        setState(STATE_RESUMED);
        break;
      case STATE_SUSPENDED_PENDING:
        if (validatePackageName(currentWindow)) {
          if (DEBUG) {
            LogUtils.d(TAG, "D-pad control suspended for " + currentWindow);
          }
          setState(STATE_SUSPENDED);
        } else {
          if (DEBUG) {
            LogUtils.d(TAG, "Resume D-pad control for the window change.");
          }
          resumeDPadControl();
          setState(STATE_RESUMED);
        }
        break;
      case STATE_RESUMED:
        // Do nothing.
      default:
        break;
    }
  }

  /**
   * Suspends TalkBack's control over D-pad KeyEvents.
   *
   * @param requester package name of requester
   * @return {@code true} if TalkBack no longer consumes D-pad KeyEvents.
   */
  private boolean suspendDPadControl(String requester) {
    if (!validatePackageName(requester)) {
      return false;
    }

    tvNavigationController.setShouldProcessDPadEvent(false);
    return true;
  }

  /** Resumes TalkBack's control over D-pad KeyEvents. */
  private void resumeDPadControl() {
    tvNavigationController.setShouldProcessDPadEvent(true);
  }

  /**
   * Validates package name of action requester.
   *
   * @param packageName package name of the requester.
   * @return {@code true} if it's a white-listed package name.
   */
  private boolean validatePackageName(String packageName) {
    if (TextUtils.isEmpty(packageName)) {
      return false;
    }
    for (String candidate : WHITE_LIST) {
      if (TextUtils.equals(candidate, packageName)) {
        return true;
      }
    }
    return false;
  }

  /** Get String of the service state for debug use only. */
  private static String stateToString(int state) {
    switch (state) {
      case STATE_RESUMED:
        return "STATE_RESUMED";
      case STATE_RESUMED_PENDING:
        return "STATE_RESUMED_PENDING";
      case STATE_SUSPENDED:
        return "STATE_SUSPENDED";
      case STATE_SUSPENDED_PENDING:
        return "STATE_SUSPENDED_PENDING";
      default:
        return "UNKNOWN";
    }
  }
}

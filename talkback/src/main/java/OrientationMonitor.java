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

package com.google.android.accessibility.talkback;

import android.content.Context;
import android.content.res.Configuration;
import android.os.PowerManager;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import java.util.ArrayList;
import java.util.List;

/** Watches changes in device orientation. */
public class OrientationMonitor {

  public interface OnOrientationChangedListener {
    public void onOrientationChanged(int newOrientation);
  }

  private final Compositor mCompositor;
  private final PowerManager mPowerManager;

  /** The orientation of the most recently received configuration. */
  private int mLastOrientation;

  private List<OnOrientationChangedListener> mListeners;

  public OrientationMonitor(Compositor compositor, Context context) {
    mCompositor = compositor;
    mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    mListeners = new ArrayList<>();
  }

  public void addOnOrientationChangedListener(OnOrientationChangedListener listener) {
    mListeners.add(listener);
  }

  public void notifyOnOrientationChanged(int newOrientation) {
    for (OnOrientationChangedListener listener : mListeners) {
      listener.onOrientationChanged(newOrientation);
    }
  }

  /**
   * Called by {@link com.google.android.accessibility.talkback.TalkBackService} when the
   * configuration changes.
   *
   * @param newConfig The new configuration.
   */
  public void onConfigurationChanged(Configuration newConfig) {
    final int orientation = newConfig.orientation;
    if (orientation == mLastOrientation) {
      return;
    }

    EventId eventId = Performance.getInstance().onRotateEventReceived(orientation);

    mLastOrientation = orientation;
    notifyOnOrientationChanged(mLastOrientation);

    //noinspection deprecation
    if (!mPowerManager.isScreenOn()) {
      // Don't announce rotation when the screen is off.
      return;
    }

    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
      mCompositor.sendEvent(Compositor.EVENT_ORIENTATION_PORTRAIT, eventId);
    } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
      mCompositor.sendEvent(Compositor.EVENT_ORIENTATION_LANDSCAPE, eventId);
    }

    Performance.getInstance().onHandlerDone(eventId);
  }
}

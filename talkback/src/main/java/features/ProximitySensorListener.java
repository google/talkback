/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.talkback.features;

import android.content.Context;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.ProximitySensor;
import com.google.android.accessibility.utils.ServiceStateListener;
import com.google.android.accessibility.utils.output.SpeechController;

/** Watches the proximity sensor, and silences speach when it's triggered. */
public class ProximitySensorListener implements SpeechController.Observer {

  private final Context mContext;
  private final SpeechController mSpeechController;

  /** Proximity sensor for implementing "shut up" functionality. */
  private ProximitySensor mProximitySensor;

  /** Whether to use the proximity sensor to silence speech. */
  private boolean mSilenceOnProximity;

  /**
   * Whether or not the screen is on. This is set by RingerModeAndScreenMonitor and used by
   * SpeechControllerImpl to determine if the ProximitySensor should be on or off.
   */
  private boolean mScreenIsOn;

  public ProximitySensorListener(TalkBackService service, SpeechController speechController) {
    mContext = service;
    mSpeechController = speechController;
    mSpeechController.addObserver(this);

    service.addServiceStateListener(
        new ServiceStateListener() {
          @Override
          public void onServiceStateChanged(int newState) {
            if (newState == ServiceStateListener.SERVICE_STATE_ACTIVE) {
              setProximitySensorState(true);
            } else if (newState == ServiceStateListener.SERVICE_STATE_SUSPENDED) {
              setProximitySensorState(false);
            }
          }
        });

    mScreenIsOn = true;
  }

  public void shutdown() {
    mSpeechController.removeObserver(this);
    setProximitySensorState(false);
  }

  @Override
  public void onSpeechStarting() {
    // Always enable the proximity sensor when speaking.
    setProximitySensorState(true);
  }

  @Override
  public void onSpeechCompleted() {
    // If the screen is on, keep the proximity sensor on.
    setProximitySensorState(mScreenIsOn);
  }

  public void setScreenIsOn(boolean screenIsOn) {
    mScreenIsOn = screenIsOn;

    // The proximity sensor should always be on when the screen is on so
    // that the proximity gesture can be used to silence all apps.
    if (mScreenIsOn) {
      setProximitySensorState(true);
    }
  }

  /**
   * Sets whether or not the proximity sensor should be used to silence speech.
   *
   * <p>This should be called when the user changes the state of the "silence on proximity"
   * preference.
   */
  public void setSilenceOnProximity(boolean silenceOnProximity) {
    mSilenceOnProximity = silenceOnProximity;

    // Propagate the proximity sensor change.
    setProximitySensorState(mSilenceOnProximity);
  }

  /**
   * Enables/disables the proximity sensor. The proximity sensor should be disabled when not in use
   * to save battery.
   *
   * <p>This is a no-op if the user has turned off the "silence on proximity" preference.
   *
   * @param enabled {@code true} if the proximity sensor should be enabled, {@code false} otherwise.
   */
  // TODO: Rewrite for readability.
  private void setProximitySensorState(boolean enabled) {
    if (mProximitySensor != null) {
      // Should we be using the proximity sensor at all?
      if (!mSilenceOnProximity) {
        mProximitySensor.stop();
        mProximitySensor = null;
        return;
      }

      if (!TalkBackService.isServiceActive()) {
        mProximitySensor.stop();
        return;
      }
    } else {
      // Do we need to initialize the proximity sensor?
      if (enabled && mSilenceOnProximity) {
        mProximitySensor = new ProximitySensor(mContext);
        mProximitySensor.setProximityChangeListener(mProximityChangeListener);
      } else {
        // Since mProximitySensor is null, we can return here.
        return;
      }
    }

    // Manage the proximity sensor state.
    if (enabled) {
      mProximitySensor.start();
    } else {
      mProximitySensor.stop();
    }
  }

  /** Stops the TTS engine when the proximity sensor is close. */
  private final ProximitySensor.ProximityChangeListener mProximityChangeListener =
      new ProximitySensor.ProximityChangeListener() {
        @Override
        public void onProximityChanged(boolean isClose) {
          // Stop feedback if the user is close to the sensor.
          if (isClose) {
            mSpeechController.interruptAllFeedback(false /* stopTtsSpeechCompletely */);
          }
        }
      };
}

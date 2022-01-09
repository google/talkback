/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.google.android.accessibility.utils.feedback;

import android.os.Message;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * Manages accessibility hints. When a node is accessibility-focused, the hint will be queued after
 * a short delay. AccessibilityManager will use SpeechController and Compositor to speak hint.
 *
 * <p>Change the check timing for usage hint enable flag from the class to Compositor,
 * REFERTO.
 */
public class AccessibilityHintsManager extends AbstractAccessibilityHintsManager {
  private static final String TAG = "AccessibilityHintsManager";

  /** Timeout before reading a hint. */
  public static final long DELAY_HINT = 400; // ms

  private HintEventListener mHintEventListener;

  private A11yHintHandler mHandler;

  private final SpeechControllerImpl mSpeechController;

  public AccessibilityHintsManager(SpeechControllerImpl speechController) {
    if (speechController == null) {
      throw new IllegalStateException();
    }
    mSpeechController = speechController;
  }

  public void setHintEventListener(HintEventListener listener) {
    mHintEventListener = listener;
  }

  private A11yHintHandler getA11yHintHandler() {
    if (mHandler == null) {
      mHandler = new A11yHintHandler(this);
    }
    return mHandler;
  }

  @Override
  protected void startHintDelay() {
    // The timeout starts after the next utterance is spoken.
    mSpeechController.addUtteranceCompleteAction(
        mSpeechController.peekNextUtteranceId(), mA11yHintRunnable);
  }

  @Override
  protected void cancelHintDelay() {
    mSpeechController.removeUtteranceCompleteAction(mA11yHintRunnable);
    if (mHandler != null) {
      mHandler.cancelA11yHintTimeout();
    }
  }

  private final SpeechController.UtteranceCompleteRunnable mA11yHintRunnable =
      new SpeechController.UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
          // The utterance must have been spoken successfully or the utterance was interrupted by
          // the Switch Access service because it takes longer to complete than the maximum amount
          // of time to wait for spoken feedback.
          if (status != SpeechController.STATUS_SPOKEN
              && status != SpeechController.STATUS_ERROR_DONT_NOTIFY_OBSERVER) {
            return;
          }

          if (hintInfo.getPendingScreenHint() == null && hintInfo.getPendingHintSource() == null) {
            return;
          }

          getA11yHintHandler().startA11yHintTimeout();
        }
      };

  /** A handler for initializing and canceling accessibility hints. */
  private static class A11yHintHandler extends WeakReferenceHandler<AccessibilityHintsManager> {
    /** Message identifier for a hint. */
    private static final int MESSAGE_WHAT_HINT = 1;

    public A11yHintHandler(AccessibilityHintsManager parent) {
      super(parent);
    }

    @Override
    public void handleMessage(Message msg, AccessibilityHintsManager parent) {
      if (msg.what != MESSAGE_WHAT_HINT) {
        return;
      }
      HintInfo hintInfo = parent.hintInfo;
      if (hintInfo.getPendingHintSource() != null) {
        if (parent.mHintEventListener == null) {
          LogUtils.e(TAG, "AccessibilityHintsManager.mHintEventListener is not initialized.");
        } else {
          parent.mHintEventListener.onFocusHint(
              hintInfo.getPendingHintEventType(),
              hintInfo.getPendingHintSource(),
              hintInfo.getNodeHintForcedFeedbackAudioPlaybackActive(),
              hintInfo.getNodeHintForcedFeedbackMicrophoneActive());
        }

        // Clean up.
        hintInfo.clear();

      } else if (hintInfo.getPendingScreenHint() != null) {
        if (parent.mHintEventListener == null) {
          LogUtils.e(TAG, "AccessibilityHintsManager.mHintEventListener is not initialized.");
        } else {
          parent.mHintEventListener.onScreenHint(hintInfo.getPendingScreenHint());
        }

        // Clean up.
        hintInfo.clear();
      }
    }

    public void startA11yHintTimeout() {
      sendEmptyMessageDelayed(MESSAGE_WHAT_HINT, DELAY_HINT);

      HintInfo hintInfo = getParent().hintInfo;

      if (hintInfo.getPendingHintSource() != null) {
        LogUtils.v(TAG, "Queuing hint for node: %s", hintInfo.getPendingHintSource());
      } else if (hintInfo.getPendingScreenHint() != null) {
        LogUtils.v(TAG, "Queuing hint for screen: %s", hintInfo.getPendingScreenHint());
      }
    }

    public void cancelA11yHintTimeout() {
      removeMessages(MESSAGE_WHAT_HINT);
    }
  }
}

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

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;

import android.os.Message;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * Manages accessibility hints. When a node is accessibility-focused, the hint will be queued after
 * a short delay.
 *
 * <p>Change the check timing for usage hint enable flag from the class to Compositor, reference
 * 
 */
public class AccessibilityHintsManager {

  private static final String TAG = "A11yHintsManager";

  /** Timeout before reading a hint. */
  @VisibleForTesting public static final long DELAY_HINT = 400; // ms

  private final SpeechController mSpeechController;
  private HintEventListener mHintEventListener;
  private A11yHintHandler mHandler;

  /** The source node whose hint will be read by the utterance complete action. */
  private @Nullable AccessibilityNodeInfoCompat mPendingHintSource;
  /**
   * Whether the current hint is a forced feedback. Set to {@code true} if the hint corresponds to
   * accessibility focus that was not genenerated from unknown source for audioplayback and
   * microphone active. Set to false if ssb is active.
   *
   * @see FeedbackItem#FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
   * @see FeedbackItem#FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
   * @see FeedbackItem#FLAG_FORCED_FEEDBACK_SSB_ACTIVE
   */
  private boolean isNodeHintForcedFeedbackAudioPlaybackActive = true;

  private boolean isNodeHintForcedFeedbackMicrophoneActive = true;

  /** The event type for the hint source node. */
  private int mPendingHintEventType;

  private @Nullable CharSequence mPendingScreenHint;

  public AccessibilityHintsManager(SpeechController speechController) {
    if (speechController == null) {
      throw new IllegalStateException();
    }
    mSpeechController = speechController;
  }

  /**
   * Should be called when the window state changes. This method will cancel the pending hint if
   * deemed appropriate based on the window event.
   */
  public void onScreenStateChanged() {
    cancelA11yHintBasedOnEventType();
  }

  /**
   * Sets whether the hint for the hint source node is a forced feedback when audio playback is
   * active.
   */
  protected void setNodeHintForcedFeedbackAudioPlaybackActive(
      boolean isNodeHintForcedFeedbackAudioPlaybackActive) {
    this.isNodeHintForcedFeedbackAudioPlaybackActive = isNodeHintForcedFeedbackAudioPlaybackActive;
  }

  /**
   * Sets whether the hint for the hint source node is a forced feedback when microphone is active.
   */
  protected void setNodeHintForcedFeedbackMicrophoneActive(
      boolean isNodeHintForcedFeedbackMicrophoneActive) {
    this.isNodeHintForcedFeedbackMicrophoneActive = isNodeHintForcedFeedbackMicrophoneActive;
  }

  public void setHintEventListener(HintEventListener listener) {
    mHintEventListener = listener;
  }

  /** Posts a hint about screen. The hint will be spoken after the next utterance is completed. */
  public void postHintForScreen(CharSequence hint) {
    cancelA11yHint();

    mPendingScreenHint = hint;

    postA11yHintRunnable();
  }

  /** Posts a hint about node. The hint will be spoken after the next utterance is completed. */
  public void postHintForNode(AccessibilityEvent event, AccessibilityNodeInfoCompat node) {
    cancelA11yHint();

    // Store info about event that caused pending hint.
    mPendingHintSource = node;
    // The hint for a node is usually posted when the node is getting accessibility focus, thus
    // the default value for the hint event type should be TYPE_VIEW_ACCESSIBILITY_FOCUSED.
    mPendingHintEventType =
        (event != null)
            ? event.getEventType()
            : AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
    postA11yHintRunnable();
  }

  /** Starts the hint timeout. */
  private void postA11yHintRunnable() {
    // The timeout starts after the next utterance is spoken.
    mSpeechController.addUtteranceCompleteAction(
        mSpeechController.peekNextUtteranceId(), mA11yHintRunnable);
  }

  /**
   * Cancels the pending accessibility hint if the hint source is null or if the event that
   * triggered the hint was not a view getting focused or accessibility focused.
   *
   * @return {@code true} if the pending accessibility hint was canceled, {@code false} otherwise.
   */
  protected boolean cancelA11yHintBasedOnEventType() {
    if (mPendingHintSource == null
        || (mPendingHintEventType != TYPE_VIEW_FOCUSED
            && mPendingHintEventType != TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
      cancelA11yHint();
      return true;
    }
    return false;
  }

  private A11yHintHandler getA11yHintHandler() {
    if (mHandler == null) {
      mHandler = new A11yHintHandler(this);
    }
    return mHandler;
  }

  /** Removes the hint timeout and completion action. Call this for every event. */
  protected void cancelA11yHint() {
    getA11yHintHandler().cancelA11yHintTimeout();

    mPendingScreenHint = null;

    if (mPendingHintSource != null) {
      mPendingHintSource.recycle();
    }
    mPendingHintSource = null;
    isNodeHintForcedFeedbackAudioPlaybackActive = true;
    isNodeHintForcedFeedbackMicrophoneActive = true;
  }

  /** Posts a delayed hint action. */
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

          if (mPendingScreenHint == null && mPendingHintSource == null) {
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

      if (parent.mPendingHintSource != null) {
        if (parent.mHintEventListener == null) {
          LogUtils.e(TAG, "AccessibilityHintsManager.mHintEventListener is not initialized.");
        } else {
          parent.mHintEventListener.onFocusHint(
              parent.mPendingHintEventType,
              parent.mPendingHintSource,
              parent.isNodeHintForcedFeedbackAudioPlaybackActive,
              parent.isNodeHintForcedFeedbackMicrophoneActive);
        }

        // Clean up.
        parent.mPendingHintSource.recycle();
        parent.mPendingHintSource = null;
        parent.isNodeHintForcedFeedbackAudioPlaybackActive = true;
        parent.isNodeHintForcedFeedbackMicrophoneActive = true;
      } else if (parent.mPendingScreenHint != null) {
        if (parent.mHintEventListener == null) {
          LogUtils.e(TAG, "AccessibilityHintsManager.mHintEventListener is not initialized.");
        } else {
          parent.mHintEventListener.onScreenHint(parent.mPendingScreenHint);
        }
        parent.mPendingScreenHint = null;
      }
    }

    public void startA11yHintTimeout() {
      sendEmptyMessageDelayed(MESSAGE_WHAT_HINT, DELAY_HINT);

      AccessibilityHintsManager parent = getParent();
      if (parent.mPendingHintSource != null) {
        LogUtils.v(TAG, "Queuing hint for node: %s", parent.mPendingHintSource);
      } else if (parent.mPendingScreenHint != null) {
        LogUtils.v(TAG, "Queuing hint for screen: %s", parent.mPendingScreenHint);
      }
    }

    public void cancelA11yHintTimeout() {
      removeMessages(MESSAGE_WHAT_HINT);
    }
  }
}

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

package com.google.android.accessibility.talkback.eventprocessor;

import static android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;

/**
 * Manages accessibility hints. When a node is accessibility-focused and hints are enabled, the hint
 * will be queued after a short delay.
 */
public class ProcessorAccessibilityHints
    implements AccessibilityEventListener, ServiceKeyEventListener {
  private final SharedPreferences mPrefs;
  private final Context mContext;
  private final SpeechController mSpeechController;
  private final Compositor mCompositor;
  private final A11yHintHandler mHandler;

  /** Event types that are handled by ProcessorAccessibilityHints. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_A11Y_HINTS =
      AccessibilityEvent.TYPE_VIEW_CLICKED
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | TYPE_VIEW_FOCUSED;

  /** The source node whose hint will be read by the utterance complete action. */
  private AccessibilityNodeInfoCompat mPendingHintSource;
  /**
   * Whether the hint for mPendingHintSource is a forced feedback. Set to {@code true} if
   * mPendingHintSource is an accessibility focus results from touch exploration or linear
   * navigation. Set to {@code false} if the a11y focus is synchronized from input focus.
   *
   * @see FeedbackItem#FLAG_FORCED_FEEDBACK
   */
  private boolean mIsNodeHintForcedFeedback = true;

  /** The event type for mPendingHintSource. */
  private int mPendingHintEventType;

  private CharSequence mPendingScreenHint;

  public ProcessorAccessibilityHints(
      Context context, SpeechController speechController, Compositor compositor) {
    if (speechController == null) {
      throw new IllegalStateException();
    }
    mPrefs = SharedPreferencesUtils.getSharedPreferences(context);
    mContext = context;
    mCompositor = compositor;
    mSpeechController = speechController;
    mHandler = new A11yHintHandler(this);
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_A11Y_HINTS;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (!areHintsEnabled()) {
      return;
    }

    // Schedule delayed hint for input-focus event.
    final int eventType = event.getEventType();
    if (eventType == TYPE_VIEW_FOCUSED) {
      AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
      AccessibilityNodeInfoCompat source = record.getSource();
      if (source != null) {
        postHintForNode(event, source); // postHintForNode() will recycle source node.
        return;
      }
    }

    // Clear hints that were generated before a click or in an old window configuration.
    if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        || eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
      if (mPendingHintSource == null || mPendingHintEventType != TYPE_VIEW_FOCUSED) {
        cancelA11yHint();
        return;
      }
    }

    // Schedule delayed hint for accessibility-focus event.
    if (eventType == AccessibilityEventCompat.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      EventState eventState = EventState.getInstance();
      if (eventState.checkAndClearRecentFlag(EventState.EVENT_SKIP_HINT_AFTER_GRANULARITY_MOVE)) {
        return;
      }
      if (eventState.checkAndClearRecentFlag(EventState.EVENT_SKIP_HINT_AFTER_CURSOR_CONTROL)) {
        return;
      }

      AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
      AccessibilityNodeInfoCompat source = record.getSource();
      if (source != null) {
        postHintForNode(event, source);
        // DO NOT RECYCLE. postA11yHintRunnable will save the node.
      }
    }
  }

  public void onScreenStateChanged() {
    if (mPendingHintSource == null || mPendingHintEventType != TYPE_VIEW_FOCUSED) {
      cancelA11yHint();
    }
  }

  /** Posts a hint about screen. The hint will be spoken after the next utterance is completed. */
  public void postHintForScreen(CharSequence hint) {
    if (!areHintsEnabled()) {
      return;
    }

    cancelA11yHint();

    mPendingScreenHint = hint;

    postA11yHintRunnable();
  }

  /** Posts a hint about node. The hint will be spoken after the next utterance is completed. */
  private void postHintForNode(AccessibilityEvent event, AccessibilityNodeInfoCompat node) {
    cancelA11yHint();

    // Store info about event that caused pending hint.
    mPendingHintSource = node;
    mPendingHintEventType = event.getEventType();
    mIsNodeHintForcedFeedback =
        !EventState.getInstance()
            .checkAndClearRecentFlag(EventState.EVENT_HINT_FOR_SYNCED_ACCESSIBILITY_FOCUS);
    postA11yHintRunnable();
  }

  /** Starts the hint timeout. */
  private void postA11yHintRunnable() {
    // The timeout starts after the next utterance is spoken.
    mSpeechController.addUtteranceCompleteAction(
        mSpeechController.peekNextUtteranceId(), mA11yHintRunnable);
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      // Mainly to prevent hints from being activated when typing, attempting to perform
      // shortcuts, etc. Doesn't cancel in-progress hint, user can use normal actions to
      // cancel (e.g. Ctrl).
      cancelA11yHint();
    }
    return false;
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return false;
  }

  private boolean areHintsEnabled() {
    final Resources res = mContext.getResources();
    return VerbosityPreferences.getPreferenceValueBool(
        mPrefs,
        res,
        res.getString(R.string.pref_a11y_hints_key),
        res.getBoolean(R.bool.pref_a11y_hints_default));
  }

  private void speakHint(CharSequence text, boolean isForcedFeedback) {
    EventId eventId = EVENT_ID_UNTRACKED; // Hints occur after other feedback.
    // Use QUEUE mode so that we don't interrupt more important messages.
    int flag = FeedbackItem.FLAG_NO_HISTORY;
    if (isForcedFeedback) {
      flag |= FeedbackItem.FLAG_FORCED_FEEDBACK;
    }
    mSpeechController.speak(text, SpeechController.QUEUE_MODE_QUEUE, flag, null, eventId);
  }

  /** Removes the hint timeout and completion action. Call this for every event. */
  private void cancelA11yHint() {
    mHandler.cancelA11yHintTimeout();

    mPendingScreenHint = null;

    if (mPendingHintSource != null) {
      mPendingHintSource.recycle();
    }
    mPendingHintSource = null;
    mIsNodeHintForcedFeedback = true;
  }

  /** Posts a delayed hint action. */
  private final SpeechController.UtteranceCompleteRunnable mA11yHintRunnable =
      new SpeechController.UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
          // The utterance must have been spoken successfully.
          if (status != SpeechController.STATUS_SPOKEN) {
            return;
          }

          if (mPendingScreenHint == null && mPendingHintSource == null) {
            return;
          }

          mHandler.startA11yHintTimeout();
        }
      };

  private static class A11yHintHandler extends WeakReferenceHandler<ProcessorAccessibilityHints> {
    /** Message identifier for a hint. */
    private static final int MESSAGE_WHAT_HINT = 1;

    /** Timeout before reading a hint. */
    private static final long DELAY_HINT = 400; // ms

    public A11yHintHandler(ProcessorAccessibilityHints parent) {
      super(parent);
    }

    @Override
    public void handleMessage(Message msg, ProcessorAccessibilityHints parent) {
      if (msg.what != MESSAGE_WHAT_HINT) {
        return;
      }

      if (parent.mPendingHintSource != null) {
        AccessibilityNodeInfoCompat refreshed =
            AccessibilityNodeInfoUtils.refreshNode(parent.mPendingHintSource);
        if (refreshed != null) {
          if (refreshed.isAccessibilityFocused()) {
            @Compositor.Event int event = 0;
            // Set compositor-event type.
            // TODO: Pass forced-feedback flag through EventInterpretation or
            // GlobalVariables, instead of making a plethora of event types.
            if (parent.mPendingHintEventType == TYPE_VIEW_FOCUSED) {
              event =
                  parent.mIsNodeHintForcedFeedback
                      ? Compositor.EVENT_INPUT_FOCUS_HINT_FORCED
                      : Compositor.EVENT_INPUT_FOCUS_HINT;
            } else {
              event =
                  parent.mIsNodeHintForcedFeedback
                      ? Compositor.EVENT_ACCESS_FOCUS_HINT_FORCED
                      : Compositor.EVENT_ACCESS_FOCUS_HINT;
            }
            EventId eventId = EVENT_ID_UNTRACKED; // Hints occur after other feedback.
            parent.mCompositor.sendEvent(event, refreshed, eventId);
            LogUtils.log(this, Log.VERBOSE, "Speaking hint for node: %s", refreshed);
          } else {
            LogUtils.log(this, Log.VERBOSE, "Skipping hint for node: %s", refreshed);
          }
          refreshed.recycle();
        }

        parent.mPendingHintSource.recycle();
        parent.mPendingHintSource = null;
        parent.mIsNodeHintForcedFeedback = true;
      } else if (parent.mPendingScreenHint != null) {
        parent.speakHint(parent.mPendingScreenHint, false);
        LogUtils.log(this, Log.VERBOSE, "Speaking hint for screen: %s", parent.mPendingScreenHint);
        parent.mPendingScreenHint = null;
      }
    }

    public void startA11yHintTimeout() {
      sendEmptyMessageDelayed(MESSAGE_WHAT_HINT, DELAY_HINT);

      ProcessorAccessibilityHints parent = getParent();
      if (parent.mPendingHintSource != null) {
        LogUtils.log(this, Log.VERBOSE, "Queuing hint for node: %s", parent.mPendingHintSource);
      } else if (parent.mPendingScreenHint != null) {
        LogUtils.log(this, Log.VERBOSE, "Queuing hint for screen: %s", parent.mPendingScreenHint);
      }
    }

    public void cancelA11yHintTimeout() {
      removeMessages(MESSAGE_WHAT_HINT);
    }
  }
}

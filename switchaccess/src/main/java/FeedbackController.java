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

package com.google.android.accessibility.switchaccess;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.os.BuildCompat;

/** Controls output of spoken feedback and notifies listeners of feedback completion. */
public class FeedbackController extends UtteranceProgressListener {

  // The maximum amount of time to wait for spoken feedback to finish before notifying a listener
  // that feedback is complete. This is done to guarantee that a callback will occur each time
  // spoken feedback is requested. Note: If speech takes longer than this time to complete, it
  // may be truncated if the owner of the registered OnUtteranceCompleteListener decides to use
  // completion of speech to speak something else (or stop speech).
  private static final int MAX_FEEDBACK_TIME_MS = 10000;

  private final Handler mHandler;

  private final TextToSpeech mTts;
  private Bundle mTtsBundle;
  private boolean mIsInitialized;

  private OnUtteranceCompleteListener mUtteranceCompleteListener;
  private final Runnable mNotifyFeedbackCompleteRunnable =
      new Runnable() {
        @Override
        public void run() {
          notifyListenerIfNotNull();
        }
      };

  /**
   * Create the FeedbackController and initializes tts.
   *
   * @param context The context with which tts will be initialized
   */
  public FeedbackController(Context context) {
    mHandler = new Handler();
    mIsInitialized = false;
    mTts =
        new TextToSpeech(
            context,
            new TextToSpeech.OnInitListener() {
              @Override
              public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                  mIsInitialized = true;
                }
              }
            });
    if (BuildCompat.isAtLeastO()) {
      // Use the Accessibility audio stream
      mTts.setAudioAttributes(
          new AudioAttributes.Builder()
              .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
              .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
              .build());
      mTtsBundle = new Bundle();
      mTtsBundle.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ACCESSIBILITY);
    }
    mTts.setOnUtteranceProgressListener(FeedbackController.this);
  }

  /**
   * Speak the supplied phrase, interrupting any existing speech.
   *
   * @param phrase Phrase to speak
   */
  public void speak(CharSequence phrase) {
    mHandler.removeCallbacks(mNotifyFeedbackCompleteRunnable);
    if (mIsInitialized) {
      // Note: While we don't care about the 'unique id' parameter, it cannot be null for
      // UtteranceProgressListener to be called.
      mTts.speak(phrase, TextToSpeech.QUEUE_FLUSH, mTtsBundle, "");
    }
    mHandler.postDelayed(mNotifyFeedbackCompleteRunnable, MAX_FEEDBACK_TIME_MS);
  }

  /** Stop speaking, interrupting speech if present. */
  public void stop() {
    mTts.stop();
  }

  /**
   * Set a listener to be notified when speech ends, regardless of the reason (error, completion, or
   * interruption). The listener is guaranteed to be called at least once after each call to {@link
   * FeedbackController#speak}. Note: Only the last listener to be set will be notified.
   */
  public void setOnUtteranceCompleteListener(OnUtteranceCompleteListener listener) {
    mUtteranceCompleteListener = listener;
  }

  /** Shut down tts. This should always be called once FeedbackController is no longer needed. */
  public void shutdown() {
    mTts.shutdown();
  }

  @Override
  public void onDone(String utteranceId) {
    notifyListenerIfNotNull();
  }

  @Override
  public void onError(String utteranceId) {
    notifyListenerIfNotNull();
  }

  @Override
  public void onError(String utteranceId, int errorCode) {
    notifyListenerIfNotNull();
  }

  @Override
  public void onStart(String utteranceId) {
    // Do nothing.
  }

  @Override
  public void onStop(String utteranceId, boolean interrupted) {
    notifyListenerIfNotNull();
  }

  private void notifyListenerIfNotNull() {
    mHandler.removeCallbacks(mNotifyFeedbackCompleteRunnable);
    if (mUtteranceCompleteListener != null) {
      mUtteranceCompleteListener.onUtteranceComplete();
    }
  }

  /** A listener that is notified when feedback is completed. */
  public interface OnUtteranceCompleteListener {
    /**
     * Called when feedback is completed, regardless of reason (error, completion, interruption).
     */
    public abstract void onUtteranceComplete();
  }
}

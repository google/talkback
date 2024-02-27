/*
 * Copyright (C) 2013 Google Inc.
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


import android.content.Context;
import android.media.AudioManager;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.compat.media.AudioManagerCompatUtils;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.volumebutton.VolumeButtonPatternDetector;

/**
 * Handles volume-key events to adjust various volume streams based on current speech state. Locks
 * the volume control stream during a touch interaction event. Currently this class is a mix of
 * event-interpreter and feedback-mapper.
 */
public class ProcessorVolumeStream
    implements AccessibilityEventListener,
        ServiceKeyEventListener,
        VolumeButtonPatternDetector.OnPatternMatchListener {
  /** Default flags for volume adjustment while target stream is TalkBack. */
  private static final int DEFAULT_FLAGS_FOR_TALKBACK_STREAM = (AudioManager.FLAG_VIBRATE);

  /** Default flags for volume adjustment while target stream is default stream. */
  private static final int DEFAULT_FLAGS_FOR_DEFAULT_STREAM =
      (AudioManager.FLAG_SHOW_UI | AudioManager.FLAG_VIBRATE | AudioManager.FLAG_PLAY_SOUND);

  /** TalkBack audio stream. */
  private static final int STREAM_TALKBACK_AUDIO = SpeechController.DEFAULT_STREAM;

  /** System default audio stream. */
  private static final int STREAM_DEFAULT = AudioManager.USE_DEFAULT_STREAM_TYPE;

  /** Tag used for identification of the wake lock held by this class */
  private static final String WL_TAG = ProcessorVolumeStream.class.getSimpleName();

  /** Event types that are handled by ProcessorVolumeStream. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_VOL_STREAM =
      AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;

  /** The audio manager, used to adjust speech volume. */
  private final AudioManager audioManager;

  /** WakeLock used to keep the screen active during key events */
  private final WakeLock wakeLock;

  /** Handler for completing volume key handling outside of the main key-event handler. */
  private final VolumeStreamHandler handler = new VolumeStreamHandler(this);

  private final TouchInteractingIndicator touchInteractingIndicator;

  /**
   * Whether touch interaction is in progress. In practice, a true value means that a single finger
   * is on the screen.
   */
  // TODO: b/212947934#comment9. If the proposal confirmed, it needs
  // 1. Removes the non Accessibility volume change logic.
  // 2. Stops implementing the AccessibilityEventListener
  private boolean isTouchInteracting = false;

  private final ActorState actorState;
  private final VolumeButtonPatternDetector patternDetector;

  private final MostRecentVolumeKeyAdjustment mostRecentVolumeKeyAdjustment =
      new MostRecentVolumeKeyAdjustment();

  // Record of the most recent volume key adjustment
  private static class MostRecentVolumeKeyAdjustment {
    /**
     * If a successive volume key press happens within this duration of time, then it should be
     * interpreted as belonging to the previous train of volume key presses.
     */
    private static final int MOST_RECENT_VOLUME_ADJUSTMENT_TRAIN_THRESHOLD = 1000;

    // When the event was processed.
    private long moment;
    // To which stream the adjustment was routed.
    public int stream;

    // Return true if the key press happened close enough in time to the previous key press to be
    // counted as part of the previous train of presses.
    public boolean onKeyPressed() {
      long momentOld = moment;
      moment = SystemClock.uptimeMillis();
      return (moment - momentOld) < MOST_RECENT_VOLUME_ADJUSTMENT_TRAIN_THRESHOLD;
    }
  }

  @SuppressWarnings("deprecation")
  public ProcessorVolumeStream(
      ActorState actorState,
      TalkBackService service,
      TouchInteractingIndicator touchInteractingIndicator) {

    audioManager = (AudioManager) service.getSystemService(Context.AUDIO_SERVICE);
    this.actorState = actorState;
    this.touchInteractingIndicator = touchInteractingIndicator;

    final PowerManager pm = (PowerManager) service.getSystemService(Context.POWER_SERVICE);
    wakeLock =
        pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, WL_TAG);

    patternDetector = new VolumeButtonPatternDetector(service);
    patternDetector.setOnPatternMatchListener(this);
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_VOL_STREAM;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_START:
        isTouchInteracting = true;
        break;
      case AccessibilityEvent.TYPE_TOUCH_INTERACTION_END:
        isTouchInteracting = false;
        break;
      default: // fall out
    }
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    boolean handled = patternDetector.onKeyEvent(event);

    if (handled) {
      // Quickly acquire and release the wake lock so that
      // PowerManager.ON_AFTER_RELEASE takes effect.
      wakeLock.acquire();
      wakeLock.release();
    }

    return handled;
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return true;
  }

  private void adjustVolumeFromKeyEvent(int button) {
    final int direction =
        ((button == VolumeButtonPatternDetector.VOLUME_UP)
            ? AudioManager.ADJUST_RAISE
            : AudioManager.ADJUST_LOWER);
    boolean shouldRouteToAccessibilityStream;

    // While continuous reading is active, we do not want to show the UI and interrupt continuous
    // reading.
    if (isTouchInteracting
        || actorState.getContinuousRead().isActive()
        || touchInteractingIndicator.isTouchInteracting()) {
      shouldRouteToAccessibilityStream = true;
    } else {
      boolean mostRecentAdjustmentJustHappened = mostRecentVolumeKeyAdjustment.onKeyPressed();

      if (mostRecentAdjustmentJustHappened
              && (mostRecentVolumeKeyAdjustment.stream == STREAM_TALKBACK_AUDIO)
          || (!mostRecentAdjustmentJustHappened
              && actorState.getSpeechState().isSpeakingOrQueuedAndNotSourceIsVolumeAnnouncment())) {
        shouldRouteToAccessibilityStream = true;
        mostRecentVolumeKeyAdjustment.stream = STREAM_TALKBACK_AUDIO;
      } else {
        shouldRouteToAccessibilityStream = false;
        mostRecentVolumeKeyAdjustment.stream = STREAM_DEFAULT;
      }
    }

    if (shouldRouteToAccessibilityStream) {
      AudioManagerCompatUtils.adjustStreamVolume(
          audioManager,
          STREAM_TALKBACK_AUDIO,
          direction,
          DEFAULT_FLAGS_FOR_TALKBACK_STREAM,
          getClass().getName());
    } else {
      // Attempt to adjust the suggested stream, but let the system
      // override in special situations like during voice calls, when an
      // application has locked the volume control stream, or when music
      // is playing.
      audioManager.adjustSuggestedStreamVolume(
          direction, STREAM_DEFAULT, DEFAULT_FLAGS_FOR_DEFAULT_STREAM);
    }
  }

  @Override
  public void onPatternMatched(int patternCode, int buttonCombination, EventId eventId) {
    handler.postPatternMatched(patternCode, buttonCombination, eventId);
  }

  public void onPatternMatchedInternal(int patternCode, int buttonCombination, EventId eventId) {
    switch (patternCode) {
      case VolumeButtonPatternDetector.SHORT_PRESS_PATTERN:
        handleSingleShortTap(buttonCombination);
        break;
      case VolumeButtonPatternDetector.LONG_PRESS_PATTERN:
        handleSingleLongTap(buttonCombination);
        break;
      default: // fall out
    }
  }

  // Generate a single play/pause button click and send it to system.
  private void dispatchPlayPauseSingleClick() {
    audioManager.dispatchMediaKeyEvent(
        new KeyEvent(KeyEvent.ACTION_DOWN, VolumeButtonPatternDetector.PLAY_PAUSE));
    audioManager.dispatchMediaKeyEvent(
        new KeyEvent(KeyEvent.ACTION_UP, VolumeButtonPatternDetector.PLAY_PAUSE));
  }

  private void passThroughMediaButtonClick(int button) {
    if (button == VolumeButtonPatternDetector.PLAY_PAUSE) {
      dispatchPlayPauseSingleClick();
    } else {
      adjustVolumeFromKeyEvent(button);
    }
  }

  private void handleSingleShortTap(int button) {
    passThroughMediaButtonClick(button);
  }

  private void handleSingleLongTap(int button) {
    passThroughMediaButtonClick(button);
  }

  /**
   * Used to run potentially long methods outside of the key handler so that we don't ever hit the
   * key handler timeout.
   */
  private static final class VolumeStreamHandler
      extends WeakReferenceHandler<ProcessorVolumeStream> {

    public VolumeStreamHandler(ProcessorVolumeStream parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, ProcessorVolumeStream parent) {
      int patternCode = msg.arg1;
      int buttonCombination = msg.arg2;
      EventId eventId = (EventId) msg.obj;
      parent.onPatternMatchedInternal(patternCode, buttonCombination, eventId);
    }

    public void postPatternMatched(int patternCode, int buttonCombination, EventId eventId) {
      Message msg =
          obtainMessage(
              /* what= */ 0, /* arg1= */ patternCode, /* arg2= */ buttonCombination, eventId);
      sendMessage(msg);
    }
  }

  /** Indicator determines whether volume key events with touch event. */
  public interface TouchInteractingIndicator {
    /** Indicates if a finger is currently touching the touch-display. */
    boolean isTouchInteracting();
  }
}

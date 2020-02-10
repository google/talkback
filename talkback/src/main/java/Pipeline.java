/*
 * Copyright (C) 2019 Google Inc.
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

import static com.google.android.accessibility.talkback.Feedback.InterruptGroup;
import static com.google.android.accessibility.talkback.Feedback.InterruptLevel;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.HashMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Pipeline stages wrapper. See  */
public class Pipeline {

  public static final String LOG = "Pipeline";
  public static final int GROUP_DIGIT = 10;

  //////////////////////////////////////////////////////////////////////////////////
  // Interface for inputting synthetic events

  /** Synthetic events from internal sources, not from accessibility-framework. */
  public static class SyntheticEvent {

    /** Enumeration of fake event types. */
    public static enum Type {
      SCROLL_TIMEOUT;
    }

    public final @NonNull Type eventType;
    public final long uptimeMs;

    public SyntheticEvent(@NonNull Type eventType) {
      this.eventType = eventType;
      this.uptimeMs = SystemClock.uptimeMillis();
    }

    @Override
    public String toString() {
      return String.format("type=%s time=%d", eventType, uptimeMs);
    }
  }

  /**
   * Restricted sub-interface for inputting fake events from actor timeouts or other internal
   * sources. Maybe replace this with an actor-timeout inside pipeline.
   */
  public class EventReceiver {
    /** Inputs event to pipeline. */
    public void input(SyntheticEvent.Type eventType) {
      Pipeline.this.inputEvent(EVENT_ID_UNTRACKED, new SyntheticEvent(eventType));
    }
  }

  private EventReceiver eventReceiver = new EventReceiver();

  //////////////////////////////////////////////////////////////////////////////////
  // Restricted sub-interface for asynchronously returning feedback.  Used by hybrid event-
  // interpreter & feedback-mappers, and by on-screen user-interface.

  // TODO: When feedback-mappers move inside Pipeline, add separate async-handler for
  // event-interpretation results, and simple run & return for feedback-mapper results. Keep
  // FeedbackReturner for use by talkback-overlay user-interface.

  /**
   * Restricted sub-interface for asynchronously returning feedback from hybrid event-interpreter &
   * feedback-mappers, and from on-screen user-interface. Uses restricted interface because we don't
   * want interpreter/mappers to access containing pipeline. Using interface (instead of just inner
   * class) for easier test mocking.
   */
  public interface FeedbackReturner {
    /** Executes feedback and returns success flag. */
    boolean returnFeedback(EventId eventId, Feedback.Part.Builder part);

    /** Executes feedback and returns success flag. */
    default boolean returnFeedback(EventId eventId, Feedback.EditText.Builder edit) {
      return returnFeedback(eventId, Feedback.Part.builder().setEdit(edit.build()));
    }

    /** Executes feedback and returns success flag. */
    default boolean returnFeedback(EventId eventId, Feedback.Focus.Builder focus) {
      return returnFeedback(eventId, Feedback.Part.builder().setFocus(focus.build()));
    }

    /** Executes feedback and returns success flag. */
    default boolean returnFeedback(EventId eventId, Feedback.FocusDirection.Builder direction) {
      return returnFeedback(eventId, Feedback.Part.builder().setFocusDirection(direction.build()));
    }
  }

  /** Implementation of FeedbackReturner, which executes feedback. */
  private FeedbackReturner feedbackReturner =
      (eventId, partBuilder) -> {
        Feedback.Part part = partBuilder.build();
        LogUtils.d(LOG, "FeedbackReturner.returnFeedback() part=%s", part);
        return execute(Feedback.create(eventId, part));
      };

  /** Provides callback for async feedback-mappers to return feedback for execution in pipeline. */
  // TODO: Pipeline passes this limited interface to feedback-mappers and
  // talkback-overlay user-interfaces, only.
  public FeedbackReturner getFeedbackReturner() {
    return feedbackReturner;
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Interface for inputting speech from Compositor
  // TODO: When pipeline runs Compositor synchronously, remove this interface.

  /**
   * Restricted sub-interface for Compositor to return feedback that was asynchronously initiated by
   * event-interpreters.
   */
  private Compositor.Speaker speaker =
      new Compositor.Speaker() {
        /** Converts compositor-feedback to talkback-feedback, then executes feedback. */
        @Override
        public void speak(CharSequence text, @Nullable EventId eventId, SpeakOptions options) {
          Feedback feedback = Feedback.create(eventId, Feedback.speech(text, options).build());
          execute(feedback);
        }
      };

  public Compositor.Speaker getSpeaker() {
    return speaker;
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Member data

  private final Interpreters interpreters;
  private final Actors actors;

  /** Asynchronous message-handler to delay executing feedback. */
  private final FeedbackDelayer feedbackDelayer;

  //////////////////////////////////////////////////////////////////////////////////
  // Construction

  public Pipeline(Interpreters interpreters, Actors actors) {
    this.interpreters = interpreters;
    this.actors = actors;

    // TODO: When interpreters and mappers split, only give interpreters the
    // interpretation-returner, not the feedback-returner.
    interpreters.setPipelineFeedbackReturner(feedbackReturner);
    interpreters.setActorState(actors.getState());

    actors.setPipelineEventReceiver(eventReceiver);
    actors.setPipelineFeedbackReturner(feedbackReturner);

    feedbackDelayer = new FeedbackDelayer(this, actors);
  }

  public void recycle() {
    actors.recycle();
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Methods

  /** Returns read-only actor state information. */
  public ActorState getActorState() {
    return actors.getState();
  }

  /** Input a synthetic event, from internal source instead of accessibility-framework. */
  // TODO: Make private, only allow synthetic-events from actors inside pipeline.
  private void inputEvent(EventId eventId, SyntheticEvent event) {
    interpreters.interpret(eventId, event);
  }

  /** Execute feedback returned by feedback-mappers. Returns success flag. */
  public boolean execute(Feedback feedback) {

    LogUtils.d(LOG, "execute() feedback=%s", feedback);

    boolean success = true;
    // For each feedback part...
    for (Feedback.Part part : feedback.sequence()) {

      // Cancel delayed feedback from same group and lower/equal level.
      if (part.interruptGroup() != Feedback.DEFAULT) {
        cancelDelay(part.interruptGroup(), part.interruptLevel(), part.senderName());
      }
      // Interrupt playing sound / vibration.
      if (part.interruptSoundAndVibration()) {
        actors.interruptSoundAndVibration();
      }
      // Interrupt playing sound / vibration / speech.
      if (part.interruptSoundAndVibration()) {
        actors.interruptSoundAndVibration();
      }
      if (part.interruptAllFeedback()) {
        actors.interruptAllFeedback(part.stopTts());
      }

      if (part.delayMs() <= 0) {
        // Execute feedback immediately.
        success &= actors.act(feedback.eventId(), part);
      } else {
        // Start feedback delay.
        startDelay(feedback.eventId(), part);
      }
    }
    return success;
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Inner classes and methods for delaying feedback

  /** Asynchronous message handler, to delay executing feedback. */
  private static class FeedbackDelayer extends WeakReferenceHandler<Pipeline> {

    private final Actors actors;

    public FeedbackDelayer(Pipeline parent, Actors actors) {
      super(parent);
      this.actors = actors;
    }

    @Override
    public void handleMessage(Message message, Pipeline parent) {
      @SuppressWarnings("unchecked")
      EventIdAnd<Feedback.Part> eventIdAndFeedback = (EventIdAnd<Feedback.Part>) message.obj;
      actors.act(eventIdAndFeedback.eventId, eventIdAndFeedback.object);
      if (getParent() != null) {
        getParent().clearInterruptLog(message.what);
      }
    }
  }

  /** Delays feedback execution. */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  protected void startDelay(EventId eventId, Feedback.Part feedback) {
    int messageId = toMessageId(feedback.interruptGroup(), feedback.interruptLevel());
    final Message message =
        feedbackDelayer.obtainMessage(messageId, new EventIdAnd<Feedback.Part>(feedback, eventId));
    feedbackDelayer.sendMessageDelayed(message, feedback.delayMs());
    messageIdToSenderName.put(messageId, feedback.senderName());
  }

  /** Cancels all delayed feedback, all groups, all levels. */
  private void cancelAllDelays() {
    feedbackDelayer.removeCallbacksAndMessages(/* token= */ null);
    messageIdToSenderName.clear();
  }

  /** Cancels all delayed feedback for group, at or below level. */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  protected void cancelDelay(
      @InterruptGroup int group, @InterruptLevel int level, String senderName) {
    for (@InterruptLevel int l = 0; l <= level; l++) {
      feedbackDelayer.removeMessages(toMessageId(group, l));
      dumpInterruptLog(toMessageId(group, l), senderName);
    }
  }

  /** Checks whether a delay exists for a given group and level. */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  protected boolean delayExists(@InterruptGroup int group, @InterruptLevel int level) {
    return feedbackDelayer.hasMessages(toMessageId(group, level));
  }

  /** Allows tests to advance the handler time. */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  protected Looper getDelayLooper() {
    return feedbackDelayer.getLooper();
  }

  /** Returns a unique result combining group and level. */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  protected static int toMessageId(@InterruptGroup int group, @InterruptLevel int level) {
    return (group * GROUP_DIGIT) + level;
  }

  ///////////////////////////////////////////////////////////////////////////////
  // Actors pass-through methods, to keep actors private

  public void onBoot(boolean quiet) {
    actors.onBoot(quiet);
  }

  public void onUnbind(float finalAnnouncementVolume) {
    cancelAllDelays();
    actors.onUnbind(finalAnnouncementVolume);
  }

  public void interruptAllFeedback(boolean stopTtsSpeechCompletely) {
    cancelAllDelays();
    actors.interruptAllFeedback(stopTtsSpeechCompletely);
  }

  public void shutdown() {
    cancelAllDelays();
    actors.shutdown();
  }

  public void setOverlayEnabled(boolean enabled) {
    actors.setOverlayEnabled(enabled);
  }

  public void setUseIntonation(boolean use) {
    actors.setUseIntonation(use);
  }

  public void setSpeechPitch(float pitch) {
    actors.setSpeechPitch(pitch);
  }

  public void setSpeechRate(float rate) {
    actors.setSpeechRate(rate);
  }

  public void setUseAudioFocus(boolean use) {
    actors.setUseAudioFocus(use);
  }

  public void setSpeechVolume(float volume) {
    actors.setSpeechVolume(volume);
  }

  ///////////////////////////////////////////////////////////////////////////////
  // Temporary Message Queue for improvement of log mechanism for reference of inter-action of
  // FeedbackDelayer interrupt
  private HashMap<Integer, String> messageIdToSenderName = new HashMap<>();

  /** Remove message at {@link #messageIdToSenderName} by messageId and dump interrupt log. */
  private void dumpInterruptLog(int messageId, String interrupterClassName) {
    if (!messageIdToSenderName.containsKey(messageId)) {
      return;
    }

    String senderName = clearInterruptLog(messageId);
    if (interrupterClassName != null) {
      LogUtils.v(
          LOG,
          "Feedback Interrupt: class %s is interrupted by class %s because in the Group %s.",
          senderName,
          interrupterClassName,
          messageIdToGroupString(messageId));
    }
  }

  private String clearInterruptLog(int messageId) {
    return messageIdToSenderName.remove(messageId);
  }

  private static String messageIdToGroupString(int messageId) {
    int groupId = messageId / GROUP_DIGIT;

    return Feedback.groupIdToString(groupId);
  }
}

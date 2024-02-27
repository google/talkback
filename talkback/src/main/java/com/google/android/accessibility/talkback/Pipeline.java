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
import static com.google.android.accessibility.talkback.eventprocessor.ProcessorAccessibilityHints.DELAY_HINT;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback.InterruptLevel;
import com.google.android.accessibility.talkback.TalkBackService.ProximitySensorListener;
import com.google.android.accessibility.talkback.compositor.Compositor;
import com.google.android.accessibility.talkback.eventprocessor.AccessibilityEventProcessor.AccessibilityEventIdleListener;
import com.google.android.accessibility.talkback.utils.DiagnosticOverlayControllerImpl;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.EventIdAnd;
import com.google.android.accessibility.utils.ProximitySensor;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceCompleteRunnable;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Pipeline stages wrapper. See REFERTO */
public class Pipeline implements AccessibilityEventListener, AccessibilityEventIdleListener {

  public static final String LOG = "Pipeline";
  public static final int GROUP_DIGIT = 10;

  //////////////////////////////////////////////////////////////////////////////////
  // Interface for inputting synthetic events

  /** Synthetic events from internal sources, not from accessibility-framework. */
  public static class SyntheticEvent {

    /** Enumeration of fake event types. */
    public enum Type {
      SCROLL_TIMEOUT,
      TEXT_TRAVERSAL,
    }

    public final @NonNull Type eventType;
    public final CharSequence eventText;
    public final long uptimeMs;

    public SyntheticEvent(@NonNull Type eventType) {
      this.eventType = eventType;
      eventText = null;
      this.uptimeMs = SystemClock.uptimeMillis();
    }

    public SyntheticEvent(@NonNull Type eventType, CharSequence eventText) {
      this.eventType = eventType;
      this.eventText = eventText;
      this.uptimeMs = SystemClock.uptimeMillis();
    }

    @Override
    public String toString() {
      return String.format("type=%s, text=%s, time=%d", eventType, eventText, uptimeMs);
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

    /** Inputs event to pipeline. */
    public void input(SyntheticEvent.Type eventType, CharSequence text) {
      Pipeline.this.inputEvent(EVENT_ID_UNTRACKED, new SyntheticEvent(eventType, text));
    }
  }

  private EventReceiver eventReceiver = new EventReceiver();

  //////////////////////////////////////////////////////////////////////////////////
  // Interface for asynchronously receiving event-interpretations

  /**
   * Restricted sub-interface for inputting event-interpretations. Uses interface instead of inner
   * class to enable test mocking.
   */
  public interface InterpretationReceiver {
    default boolean input(@Nullable EventId eventId, @Nullable Interpretation interpretation) {
      return input(eventId, /* event= */ null, interpretation, /* eventSourceNode= */ null);
    }

    default boolean input(
        @Nullable EventId eventId,
        AccessibilityEvent event,
        @Nullable Interpretation interpretation) {
      return input(eventId, event, interpretation, /* eventSourceNode= */ null);
    }

    /** Inputs event-interpretation to pipeline. */
    boolean input(
        @Nullable EventId eventId,
        @Nullable AccessibilityEvent event,
        @Nullable Interpretation interpretation,
        @Nullable AccessibilityNodeInfoCompat eventSourceNode);
  }

  private InterpretationReceiver interpretationReceiver =
      (eventId, event, interpretation, eventSourceNode) ->
          inputInterpretation(eventId, event, interpretation, eventSourceNode);

  //////////////////////////////////////////////////////////////////////////////////
  // Restricted sub-interface for asynchronously returning feedback.  Used by hybrid event-
  // interpreter & feedback-mappers, and by on-screen user-interface.

  // TODO: When feedback-mappers move inside Pipeline, add separate async-handler for
  // event-interpretation results, and simple run & return for feedback-mapper results. Keep
  // FeedbackReturner for use by talkback-overlay user-interface, and peer actors.

  /**
   * Restricted sub-interface for asynchronously returning feedback from hybrid event-interpreter &
   * feedback-mappers, and from on-screen user-interface. Uses restricted interface because we don't
   * want interpreter/mappers to access containing pipeline. Using interface (instead of just inner
   * class) for easier test mocking.
   */
  public interface FeedbackReturner {
    /** Executes feedback, and returns success flag. */
    boolean returnFeedback(Feedback feedback);

    default boolean returnFeedback(EventId eventId, Feedback.Part.Builder part) {
      if (part == null) {
        LogUtils.e(LOG, "returnFeedback(part=null)");
        return false;
      }
      return returnFeedback(Feedback.create(eventId, part.build()));
    }

    default boolean returnFeedback(EventId eventId, Feedback.EditText.Builder edit) {
      if (edit == null) {
        LogUtils.e(LOG, "returnFeedback(edit=null)");
        return false;
      }
      return returnFeedback(eventId, Feedback.Part.builder().setEdit(edit.build()));
    }

    default boolean returnFeedback(EventId eventId, Feedback.Focus.Builder focus) {
      if (focus == null) {
        LogUtils.e(LOG, "returnFeedback(focus=null)");
        return false;
      }
      return returnFeedback(eventId, Feedback.Part.builder().setFocus(focus.build()));
    }

    default boolean returnFeedback(EventId eventId, Feedback.FocusDirection.Builder direction) {
      if (direction == null) {
        LogUtils.e(LOG, "returnFeedback(direction=null)");
        return false;
      }
      return returnFeedback(eventId, Feedback.Part.builder().setFocusDirection(direction.build()));
    }
  }

  /** Implementation of FeedbackReturner, which executes feedback. */
  private FeedbackReturner feedbackReturner = (feedback) -> execute(feedback);

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

  private final Context context;
  private final @NonNull Monitors monitors;
  private final Interpreters interpreters;
  private final Mappers mappers;
  private final Actors actors;
  private final SpeechObserver speechObserver;
  private final UserInterface userInterface;
  private final DiagnosticOverlayControllerImpl diagnosticOverlayController;
  private final Compositor compositor;

  /** Asynchronous message-handler to delay executing feedback. */
  private final FeedbackDelayer feedbackDelayer;

  /** Collection of delayed-feedback, to ensure cancelled delayed-feedback can be logged. */
  @VisibleForTesting
  final HashMap<Integer, List<Feedback.Part>> messageIdToDelayedFeedback = new HashMap<>();

  //////////////////////////////////////////////////////////////////////////////////
  // Construction

  public Pipeline(
      Context context,
      @NonNull Monitors monitors,
      Interpreters interpreters,
      Mappers mappers,
      Actors actors,
      ProximitySensorListener proximitySensorListener,
      SpeechController speechController,
      DiagnosticOverlayControllerImpl diagnosticOverlayController,
      Compositor compositor,
      UserInterface userInterface) {
    this.context = context;
    this.monitors = monitors;
    this.interpreters = interpreters;
    this.mappers = mappers;
    this.actors = actors;
    this.diagnosticOverlayController = diagnosticOverlayController;
    this.compositor = compositor;
    this.userInterface = userInterface;

    monitors.setPipelineInterpretationReceiver(interpretationReceiver);

    interpreters.setPipelineInterpretationReceiver(interpretationReceiver);
    interpreters.setActorState(actors.getState());

    mappers.setMonitors(monitors.state);

    actors.setPipelineEventReceiver(eventReceiver);
    actors.setPipelineFeedbackReturner(feedbackReturner);
    actors.setUserInterface(userInterface);

    feedbackDelayer = new FeedbackDelayer(this, actors);
    speechObserver = new SpeechObserver(proximitySensorListener, speechController);
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Methods

  /** Returns read-only actor state information. */
  public ActorState getActorState() {
    return actors.getState();
  }

  @Override
  public int getEventTypes() {
    return interpreters.getEventTypes() | monitors.getEventTypes();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    monitors.onAccessibilityEvent(event);
    interpreters.onAccessibilityEvent(event, eventId);
  }

  @Override
  public void onIdle() {
    interpreters.onIdle();
  }

  /** Provides callback for async feedback-mappers to return feedback for execution in pipeline. */
  // TODO: Pipeline passes this limited interface to feedback-mappers and
  // talkback-overlay user-interfaces, only.
  public FeedbackReturner getFeedbackReturner() {
    return feedbackReturner;
  }

  public @NonNull InterpretationReceiver getInterpretationReceiver() {
    return interpretationReceiver;
  }

  /** Input a synthetic event, from internal source instead of accessibility-framework. */
  private void inputEvent(EventId eventId, SyntheticEvent event) {
    interpreters.interpret(eventId, event);
  }

  /** Input an event-interpretation, and map it to feedback-actions. Returns execute() success. */
  private boolean inputInterpretation(
      @Nullable EventId eventId,
      @Nullable AccessibilityEvent event,
      @Nullable Interpretation eventInterpretation,
      @Nullable AccessibilityNodeInfoCompat eventSourceNode) {

    userInterface.handleEvent(eventId, event, eventInterpretation);

    // Map event-interpretation to feedback.
    @Nullable Feedback feedback =
        mappers.mapToFeedback(eventId, event, eventInterpretation, eventSourceNode);
    if (feedback == null) {
      return false;
    }
    return execute(feedback);
  }

  /** Execute feedback returned by feedback-mappers. Returns success flag. */
  boolean execute(Feedback feedback) {

    LogUtils.d(LOG, "execute() feedback=%s", feedback);

    // For each feedback part... if not successful... fail-over to next feedback.
    List<Feedback.Part> parts = feedback.failovers();
    for (int p = 0; p < parts.size(); ++p) {
      Feedback.Part part = parts.get(p);

      // Convert Feedback if this is speak hint
      if ((part.speech() != null)
          && (part.speech().hintSpeakOptions() != null)
          && (part.speech().hint() != null)
          && speakUsageHints(context)) {
        final CharSequence hintTTSOutput = part.speech().hint();
        final int hintFlags = part.speech().hintSpeakOptions().mFlags;
        final @InterruptGroup int hintInterruptGroup = part.speech().hintInterruptGroup();
        final @InterruptLevel int hintInterruptLevel = part.speech().hintInterruptLevel();
        part.speech()
            .hintSpeakOptions()
            .setCompletedAction(
                (status) -> {
                  // The utterance must have been spoken successfully or the utterance was
                  // interrupted by the
                  // other utterances inside hint group (status =
                  // SpeechController.STATUS_INTERRUPTED when
                  // interrupt speaker).
                  if (!((status == SpeechController.STATUS_SPOKEN)
                      || (status == SpeechController.STATUS_INTERRUPTED))) {
                    return;
                  }
                  execute(
                      Feedback.create(
                          EVENT_ID_UNTRACKED,
                          Feedback.Part.builder()
                              .setDelayMs((int) DELAY_HINT)
                              .setInterruptGroup(hintInterruptGroup)
                              .setInterruptLevel(hintInterruptLevel)
                              .setSenderName(LOG)
                              .speech(
                                  hintTTSOutput,
                                  SpeechController.SpeakOptions.create()
                                      .setQueueMode(SpeechController.QUEUE_MODE_QUEUE)
                                      .setFlags(hintFlags)
                                      .setUtteranceGroup(SpeechController.UTTERANCE_GROUP_DEFAULT))
                              .build()));
                });
      }

      // Cancel delayed feedback from same group and lower/equal level.
      if (part.interruptGroup() != Feedback.DEFAULT) {
        cancelDelay(part.interruptGroup(), part.interruptLevel(), part.senderName());
        actors.clearHintUtteranceCompleteAction(part.interruptGroup(), part.interruptLevel());
      }
      // Interrupt playing sound / vibration / speech.
      if (part.interruptAllFeedback()) {
        actors.interruptAllFeedback(part.stopTts());
      }
      if (part.interruptSoundAndVibration()) {
        actors.interruptSoundAndVibration();
      }
      if (part.interruptGentle()) {
        actors.interruptGentle(feedback.eventId());
      }

      if (diagnosticOverlayController.isHighlightOverlayEnabled()) {
        displayFeedbackForDiagnosticOverlay(feedback);
      }

      boolean success = true;
      if (part.delayMs() <= 0) {
        // Execute feedback immediately.
        success = actors.act(feedback.eventId(), part);
        LogUtils.v(LOG, "execute() success=%s for part=%s", success, part);
      } else {
        // Start feedback delay.
        startDelay(feedback.eventId(), part);
      }

      if (success) {
        return true;
      }
    }
    return false;
  }

  private static boolean speakUsageHints(Context context) {
    return VerbosityPreferences.getPreferenceValueBool(
        SharedPreferencesUtils.getSharedPreferences(context),
        context.getResources(),
        context.getString(R.string.pref_a11y_hints_key),
        context.getResources().getBoolean(R.bool.pref_a11y_hints_default));
  }

  private void displayFeedbackForDiagnosticOverlay(Feedback feedback) {
    Feedback.@Nullable Part failover =
        (feedback.failovers() == null || feedback.failovers().size() < 1
            ? null
            : feedback.failovers().get(0));
    /** Checks to make sure both failover and eventID aren't null before checking for gestures */
    if ((failover == null) || (feedback.eventId() == null)) {
      return;
    }

    // Filter for FOCUS and FOCUS DIRECTION actions,
    // which mark beg/end of swipe gesture + associated focus
    if (failover.focus() == null
        && failover.focusDirection() == null
        && failover.scroll() == null) {
      return;
    }

    if ((feedback.eventId().getEventSubtype() == AccessibilityEvent.TYPE_WINDOWS_CHANGED
        || feedback.eventId().getEventSubtype() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        || failover.scroll() != null)) {
      diagnosticOverlayController.clearHighlight();
    }

    if (failover.focus() != null) {
      diagnosticOverlayController.highlightNodesOnScreen(failover.focus().target());
    } else if (failover.focusDirection() != null) {
      diagnosticOverlayController.clearHighlight();
    }

    // Do not display feedback text, because it is directly observable.
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
      Feedback.Part part = eventIdAndFeedback.object;
      actors.act(eventIdAndFeedback.eventId, part);
      if (getParent() != null) {
        getParent().clearCompletedDelayedFeedback(message.what, part);
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

    // Collect delayed feedback.
    List<Feedback.Part> feedbackParts = messageIdToDelayedFeedback.get(messageId);
    if (feedbackParts == null) {
      feedbackParts = new ArrayList<>();
      messageIdToDelayedFeedback.put(messageId, feedbackParts);
    }
    feedbackParts.add(feedback);
  }

  /** Cancels all delayed feedback, all groups, all levels. */
  private void cancelAllDelays() {
    feedbackDelayer.removeCallbacksAndMessages(/* token= */ null);
    messageIdToDelayedFeedback.clear();
  }

  /** Cancels all delayed feedback for group, at or below level. */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  protected void cancelDelay(
      @InterruptGroup int group, @InterruptLevel int level, String senderName) {
    for (@InterruptLevel int l = 0; l <= level; l++) {
      feedbackDelayer.removeMessages(toMessageId(group, l));
      clearInterruptedDelayedFeedback(toMessageId(group, l), senderName);
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

  public void onUnbind(
      float finalAnnouncementVolume, UtteranceCompleteRunnable disableTalkBackCompleteAction) {
    cancelAllDelays();
    compositor.handleEventWithCompletionHandler(
        Compositor.EVENT_SPOKEN_FEEDBACK_DISABLED,
        Performance.EVENT_ID_UNTRACKED,
        disableTalkBackCompleteAction);
    actors.onUnbind(finalAnnouncementVolume);
  }

  public void interruptAllFeedback(boolean stopTtsSpeechCompletely) {
    cancelAllDelays();
    actors.interruptAllFeedback(stopTtsSpeechCompletely);
  }

  public void shutdown() {
    cancelAllDelays();
    actors.shutdown();
    speechObserver.shutdown();
  }

  public void setOverlayEnabled(boolean enabled) {
    actors.setOverlayEnabled(enabled);
  }

  public void setUseIntonation(boolean use) {
    actors.setUseIntonation(use);
  }

  public void setUsePunctuation(boolean use) {
    actors.setUsePunctuation(use);
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
  // Methods to collect delayed feedback

  /** Remove all delayed feedback for messageId, and log interruption. */
  private void clearInterruptedDelayedFeedback(int messageId, String interrupterName) {
    // For each feedback-part with this messageId...
    @Nullable List<Feedback.Part> feedbackParts = messageIdToDelayedFeedback.remove(messageId);
    if (feedbackParts == null) {
      return;
    }
    for (Feedback.Part feedbackPart : feedbackParts) {
      // Log interruption.
      if (interrupterName != null) {
        LogUtils.v(
            LOG,
            "Feedback Interrupt: source %s is interrupted by source %s because in the Group %s.",
            feedbackPart.senderName(),
            interrupterName,
            messageIdToGroupString(messageId));
      }
    }
  }

  private void clearCompletedDelayedFeedback(int messageId, Feedback.Part feedbackPart) {
    // Remove collected delayed feedback.
    @Nullable List<Feedback.Part> feedbackParts = messageIdToDelayedFeedback.get(messageId);
    if (feedbackParts == null) {
      return;
    }
    for (int f = feedbackParts.size() - 1; f >= 0; --f) { // Reverse iterate to preserve indices.
      if (feedbackPart == feedbackParts.get(f)) { // Match by reference.
        feedbackParts.remove(f);
      }
    }
    if (feedbackParts.isEmpty()) {
      messageIdToDelayedFeedback.remove(messageId);
    }
  }

  private static String messageIdToGroupString(int messageId) {
    int groupId = messageId / GROUP_DIGIT;

    return Feedback.groupIdToString(groupId);
  }

  //////////////////////////////////////////////////////////////////////////////////
  //  and methods for delaying feedback

  /**
   * Inner classes to executes {@link SpeechController.Observer} listen actions, such as speech
   * starting, pause and complete, for Proximity Sensor. The normal actor-state, pass-back from
   * actors to interpreters, does not work in this case because the speaker-state has to be pushed
   * to the proximity-interpreter.
   */
  private static class SpeechObserver implements SpeechController.Observer {
    private static final String TAG = "SpeechControllerObserverInterpreter";

    private final ProximitySensorListener proximitySensorListener;
    private final SpeechController speechController;

    public SpeechObserver(
        ProximitySensorListener proximitySensorListener, SpeechController speechController) {
      this.proximitySensorListener = proximitySensorListener;
      this.speechController = speechController;
      this.speechController.addObserver(this);
    }

    @Override
    public void onSpeechStarting() {
      // Always enable the proximity sensor when speaking.
      proximitySensorListener.setProximitySensorState(true);
    }

    @Override
    public void onSpeechCompleted() {
      // If the screen is on, keep the proximity sensor on.
      proximitySensorListener.setProximitySensorStateByScreen();
    }

    @Override
    public void onSpeechPaused() {
      // If the screen is on, keep the proximity sensor on.
      proximitySensorListener.setProximitySensorStateByScreen();
    }

    /** Shuts down the manager and releases resources. */
    public void shutdown() {
      LogUtils.v(TAG, "Shutdown requested.");
      speechController.removeObserver(this);
    }
  }

  /** Stops the TTS engine when the proximity sensor is close. */
  private final ProximitySensor.ProximityChangeListener proximityChangeListener =
      new ProximitySensor.ProximityChangeListener() {
        @Override
        public void onProximityChanged(boolean isClose) {
          // Stop feedback if the user is close to the sensor.
          if (isClose) {
            interruptAllFeedback(/* stopTtsSpeechCompletely= */ false);
          }
        }
      };

  public ProximitySensor.ProximityChangeListener getProximityChangeListener() {
    return proximityChangeListener;
  }

}

/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.talkback.compositor;

import static com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider.EMPTY_FEEDBACK;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH_CAN_IGNORE_INTERRUPTS;

import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree;
import com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPhoneticLetters;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.input.TextEventInterpretation;
import com.google.android.accessibility.utils.output.FailoverTextToSpeech.SpeechParam;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechCleanupUtils;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceCompleteRunnable;
import com.google.android.accessibility.utils.output.Utterance;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Translates events into user visible feedback. */
public class Compositor {

  /////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final String TAG = "Compositor";

  /** Flavors used to load different configurations for different device types and applications. */
  @IntDef({FLAVOR_NONE, FLAVOR_TV, FLAVOR_JASPER})
  @Retention(RetentionPolicy.SOURCE)
  public @interface Flavor {}

  public static final int FLAVOR_NONE = 0;
  public static final int FLAVOR_TV = 2;
  public static final int FLAVOR_JASPER = 3;

  /** Identity numbers for incoming events, including AccessibilityEvents & interpreted events. */
  @IntDef({
    EVENT_UNKNOWN,
    EVENT_SPOKEN_FEEDBACK_ON,
    EVENT_SPOKEN_FEEDBACK_DISABLED,
    EVENT_CAPS_LOCK_ON,
    EVENT_CAPS_LOCK_OFF,
    EVENT_NUM_LOCK_ON,
    EVENT_NUM_LOCK_OFF,
    EVENT_SCROLL_LOCK_ON,
    EVENT_SCROLL_LOCK_OFF,
    EVENT_ORIENTATION_PORTRAIT,
    EVENT_ORIENTATION_LANDSCAPE,
    EVENT_TYPE_INPUT_TEXT_CLEAR,
    EVENT_TYPE_INPUT_TEXT_REMOVE,
    EVENT_TYPE_INPUT_TEXT_ADD,
    EVENT_TYPE_INPUT_TEXT_REPLACE,
    EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD,
    EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE,
    EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE,
    EVENT_TYPE_INPUT_CHANGE_INVALID,
    EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT,
    EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING,
    EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END,
    EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION,
    EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION,
    EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED,
    EVENT_TYPE_INPUT_SELECTION_CUT,
    EVENT_TYPE_INPUT_SELECTION_PASTE,
    EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL,
    EVENT_TYPE_INPUT_SELECTION_SELECT_ALL,
    EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD,
    EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION,
    EVENT_TYPE_SET_TEXT_BY_ACTION,
    EVENT_SPEAK_HINT,
    EVENT_MAGNIFICATION_CHANGED,
    EVENT_SCROLL_POSITION,
    EVENT_INPUT_DESCRIBE_NODE,
    EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED,
    EVENT_TYPE_VIEW_FOCUSED,
    EVENT_TYPE_VIEW_HOVER_ENTER,
    EVENT_TYPE_VIEW_CLICKED,
    EVENT_TYPE_VIEW_LONG_CLICKED,
    EVENT_TYPE_NOTIFICATION_STATE_CHANGED,
    EVENT_TYPE_WINDOW_CONTENT_CHANGED,
    EVENT_TYPE_VIEW_SELECTED,
    EVENT_TYPE_VIEW_SCROLLED,
    EVENT_TYPE_ANNOUNCEMENT,
    EVENT_TYPE_WINDOW_STATE_CHANGED,
    EVENT_TYPE_TOUCH_INTERACTION_START,
    EVENT_TYPE_TOUCH_INTERACTION_END,
    EVENT_TYPE_VIEW_TEXT_CHANGED,
    EVENT_TYPE_VIEW_TEXT_SELECTION_CHANGED,
    EVENT_TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface Event {}

  // Events start from a large number to avoid conflict with AccessibilityEvent.getEventType()
  private static final int BASE_EVENT_ID = TextEventInterpretation.AFTER_TEXT_EVENTS;
  public static final int EVENT_UNKNOWN = BASE_EVENT_ID - 1;
  public static final int EVENT_SPOKEN_FEEDBACK_ON = BASE_EVENT_ID;
  public static final int EVENT_SPOKEN_FEEDBACK_DISABLED = BASE_EVENT_ID + 3;
  public static final int EVENT_CAPS_LOCK_ON = BASE_EVENT_ID + 4;
  public static final int EVENT_CAPS_LOCK_OFF = BASE_EVENT_ID + 5;
  public static final int EVENT_NUM_LOCK_ON = BASE_EVENT_ID + 6;
  public static final int EVENT_NUM_LOCK_OFF = BASE_EVENT_ID + 7;
  public static final int EVENT_SCROLL_LOCK_ON = BASE_EVENT_ID + 8;
  public static final int EVENT_SCROLL_LOCK_OFF = BASE_EVENT_ID + 9;
  public static final int EVENT_ORIENTATION_PORTRAIT = BASE_EVENT_ID + 10;
  public static final int EVENT_ORIENTATION_LANDSCAPE = BASE_EVENT_ID + 11;
  public static final int EVENT_SPEAK_HINT = BASE_EVENT_ID + 12;
  public static final int EVENT_SCROLL_POSITION = BASE_EVENT_ID + 13;
  public static final int EVENT_INPUT_DESCRIBE_NODE = BASE_EVENT_ID + 14;
  public static final int EVENT_MAGNIFICATION_CHANGED = BASE_EVENT_ID + 15;

  public static final int BASE_TEXT_EVENT_ID = BASE_EVENT_ID + 100;
  public static final int EVENT_TYPE_INPUT_TEXT_CLEAR = TextEventInterpretation.TEXT_CLEAR;
  public static final int EVENT_TYPE_INPUT_TEXT_REMOVE = TextEventInterpretation.TEXT_REMOVE;
  public static final int EVENT_TYPE_INPUT_TEXT_ADD = TextEventInterpretation.TEXT_ADD;
  public static final int EVENT_TYPE_INPUT_TEXT_REPLACE = TextEventInterpretation.TEXT_REPLACE;
  public static final int EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD =
      TextEventInterpretation.TEXT_PASSWORD_ADD;
  public static final int EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE =
      TextEventInterpretation.TEXT_PASSWORD_REMOVE;
  public static final int EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE =
      TextEventInterpretation.TEXT_PASSWORD_REPLACE;
  public static final int EVENT_TYPE_INPUT_CHANGE_INVALID = TextEventInterpretation.CHANGE_INVALID;
  public static final int EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT =
      TextEventInterpretation.SELECTION_FOCUS_EDIT_TEXT;
  public static final int EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING =
      TextEventInterpretation.SELECTION_MOVE_CURSOR_TO_BEGINNING;
  public static final int EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END =
      TextEventInterpretation.SELECTION_MOVE_CURSOR_TO_END;
  public static final int EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION =
      TextEventInterpretation.SELECTION_MOVE_CURSOR_NO_SELECTION;
  public static final int EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION =
      TextEventInterpretation.SELECTION_MOVE_CURSOR_WITH_SELECTION;
  public static final int EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED =
      TextEventInterpretation.SELECTION_MOVE_CURSOR_SELECTION_CLEARED;
  public static final int EVENT_TYPE_INPUT_SELECTION_CUT = TextEventInterpretation.SELECTION_CUT;
  public static final int EVENT_TYPE_INPUT_SELECTION_PASTE =
      TextEventInterpretation.SELECTION_PASTE;
  public static final int EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL =
      TextEventInterpretation.SELECTION_TEXT_TRAVERSAL;
  public static final int EVENT_TYPE_INPUT_SELECTION_SELECT_ALL =
      TextEventInterpretation.SELECTION_SELECT_ALL;
  public static final int EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD =
      TextEventInterpretation.SELECTION_SELECT_ALL_WITH_KEYBOARD;
  public static final int EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION =
      TextEventInterpretation.SELECTION_RESET_SELECTION;
  public static final int EVENT_TYPE_SET_TEXT_BY_ACTION =
      TextEventInterpretation.SET_TEXT_BY_ACTION;

  // Accessibility events
  public static final int EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
  public static final int EVENT_TYPE_VIEW_FOCUSED = AccessibilityEvent.TYPE_VIEW_FOCUSED;
  public static final int EVENT_TYPE_VIEW_HOVER_ENTER = AccessibilityEvent.TYPE_VIEW_HOVER_ENTER;
  public static final int EVENT_TYPE_VIEW_CLICKED = AccessibilityEvent.TYPE_VIEW_CLICKED;
  public static final int EVENT_TYPE_VIEW_LONG_CLICKED = AccessibilityEvent.TYPE_VIEW_LONG_CLICKED;
  public static final int EVENT_TYPE_NOTIFICATION_STATE_CHANGED =
      AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;
  public static final int EVENT_TYPE_WINDOW_CONTENT_CHANGED =
      AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
  public static final int EVENT_TYPE_VIEW_SELECTED = AccessibilityEvent.TYPE_VIEW_SELECTED;
  public static final int EVENT_TYPE_VIEW_SCROLLED = AccessibilityEvent.TYPE_VIEW_SCROLLED;
  public static final int EVENT_TYPE_ANNOUNCEMENT = AccessibilityEvent.TYPE_ANNOUNCEMENT;
  public static final int EVENT_TYPE_WINDOW_STATE_CHANGED =
      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
  public static final int EVENT_TYPE_TOUCH_INTERACTION_START =
      AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;
  public static final int EVENT_TYPE_TOUCH_INTERACTION_END =
      AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;
  public static final int EVENT_TYPE_VIEW_TEXT_CHANGED = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED;
  public static final int EVENT_TYPE_VIEW_TEXT_SELECTION_CHANGED =
      AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED;
  public static final int EVENT_TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY =
      AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY;

  @Event
  public static int toCompositorEvent(@TextEventInterpretation.TextEvent int textEvent) {
    return textEvent;
  }

  @Event
  public static int toCompositorEvent(AccessibilityEvent event) {
    final int eventType = event.getEventType();
    if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      return EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED;
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) {
      return EVENT_TYPE_VIEW_FOCUSED;
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_HOVER_ENTER) {
      return EVENT_TYPE_VIEW_HOVER_ENTER;
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
      return EVENT_TYPE_VIEW_CLICKED;
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
      return EVENT_TYPE_VIEW_LONG_CLICKED;
    } else if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
      return EVENT_TYPE_NOTIFICATION_STATE_CHANGED;
    } else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
      return EVENT_TYPE_WINDOW_CONTENT_CHANGED;
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) {
      return EVENT_TYPE_VIEW_SELECTED;
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
      return EVENT_TYPE_VIEW_SCROLLED;
    } else if (eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT) {
      return EVENT_TYPE_ANNOUNCEMENT;
    } else if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      return EVENT_TYPE_WINDOW_STATE_CHANGED;
    } else if (eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
      return EVENT_TYPE_TOUCH_INTERACTION_START;
    } else if (eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) {
      return EVENT_TYPE_TOUCH_INTERACTION_END;
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
      return EVENT_TYPE_VIEW_TEXT_CHANGED;
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
      return EVENT_TYPE_VIEW_TEXT_SELECTION_CHANGED;
    } else if (eventType == AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY) {
      return EVENT_TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY;
    } else {
      return EVENT_UNKNOWN;
    }
  }

  // Enum values
  /**
   * Compositor speech queue mode that can be interrupted by new speech if the speech text length is
   * long.
   *
   * <p>Note: if the speech text length is longer than {@code
   * VERBOSE_UTTERANCE_THRESHOLD_CHARACTERS}, the queue mode is set {@link QUEUE_MODE_QUEUE} or it
   * set {@link QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH}.
   */
  public static final int QUEUE_MODE_INTERRUPTIBLE_IF_LONG = 0x40000001;

  // Constant parameters
  public static final int VERBOSE_UTTERANCE_THRESHOLD_CHARACTERS = 30;

  private static final boolean USE_LEGACY_COMPOSITOR_JSON = false;

  /////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final @Nullable SpeechController speechController;

  /**
   * A callback to speak text via some unknown speech service. Only 1 of speaker and
   * speechController should be set, to avoid speaking twice.
   */
  private @Nullable Speaker speaker;

  private final Context mContext;

  /** Provides the event feedback outputs from compositor-json {@link ParseTree}. */
  private EventFeedbackProvider parseTreeFeedbackProvider;

  /** Provides the event feedback outputs from Compositor-java. */
  private final EventFeedbackProvider talkbackFeedbackProvider;

  private final GlobalVariables globalVariables;
  private final TextComposer textComposer = this::parseTTSText;

  /////////////////////////////////////////////////////////////////////////////////
  // Inner classes

  /** Callback interface for talkback-pipeline to receive async speech feedback. */
  public interface Speaker {
    void speak(CharSequence text, @Nullable EventId eventId, SpeakOptions options);
  }

  /** Limited-scope interface to map an event to text for announcement. */
  public interface TextComposer {
    @Nullable String parseTTSText(
        @Nullable AccessibilityNodeInfoCompat source,
        int event,
        EventInterpretation eventInterpretation);
  }

  /////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  public Compositor(
      @NonNull Context context,
      @Nullable SpeechController speechController,
      @Nullable ImageContents imageContents,
      @NonNull GlobalVariables globalVariables,
      @NonNull ProcessorPhoneticLetters processorPhoneticLetters,
      @Flavor int flavor) {
    this.speechController = speechController;
    this.globalVariables = globalVariables;
    mContext = context;

    long startTime = SystemClock.uptimeMillis();
    // TODO remove the deprecated compositor json parse tree implementation
    if (USE_LEGACY_COMPOSITOR_JSON) {
      VariablesFactory variablesFactory =
          new VariablesFactory(context, globalVariables, imageContents);
      parseTreeFeedbackProvider =
          new ParseTreeFeedbackProvider(
              ParseTreeCreator.createParseTree(context, variablesFactory, flavor),
              variablesFactory);
    }
    talkbackFeedbackProvider =
        new TalkBackFeedbackProvider(
            flavor,
            context,
            imageContents,
            globalVariables,
            new RoleDescriptionExtractor(context, imageContents, globalVariables),
            processorPhoneticLetters);
    long endTime = SystemClock.uptimeMillis();
    LogUtils.i(
        TAG,
        "EventFeedbackProvider built for compositor %s in %d ms",
        getFlavorName(flavor),
        endTime - startTime);
  }

  public TextComposer getTextComposer() {
    return textComposer;
  }

  public void setSpeaker(Speaker speaker) {
    this.speaker = speaker;
  }

  /** Gets the user preferred locale changed using language switcher. */
  public @Nullable Locale getUserPreferredLanguage() {
    return globalVariables.getUserPreferredLocale();
  }

  /** Sets the user preferred locale changed using language switcher. */
  public void setUserPreferredLanguage(Locale locale) {
    globalVariables.setUserPreferredLocale(locale);
  }

  /////////////////////////////////////////////////////////////////////////////////
  // Feedback mapping methods

  /**
   * Handles an event that has no meta-data associated with it.
   *
   * @param event Type of event that has occurred.
   * @param runnable Run when TTS output has completed
   */
  public void handleEventWithCompletionHandler(
      @Event int event, EventId eventId, SpeechController.UtteranceCompleteRunnable runnable) {
    HandleEventOptions options = new HandleEventOptions().onComplete(runnable);
    handleEvent(event, eventId, options);
  }

  /**
   * Handles an event that has no meta-data associated with it.
   *
   * @param event Type of event that has occurred.
   * @param eventId ID of the event used for performance monitoring.
   */
  public void handleEvent(@Event int event, @Nullable EventId eventId) {
    HandleEventOptions options = new HandleEventOptions();
    handleEvent(event, eventId, options);
  }

  /**
   * Handles an event that has a node associated.
   *
   * @param event Type of event that has occurred.
   * @param event Type of event that has occurred.
   * @param eventId ID of the event used for performance monitoring.
   */
  @VisibleForTesting
  public void handleEvent(
      @Event int event, AccessibilityNodeInfoCompat node, @Nullable EventId eventId) {
    HandleEventOptions options = new HandleEventOptions().source(node);
    handleEvent(event, eventId, options);
  }

  /** Handles an internally-generated accessibility event. */
  public void handleEvent(@Nullable EventId eventId, EventInterpretation eventInterpretation) {
    HandleEventOptions options = new HandleEventOptions().interpretation(eventInterpretation);
    handleEvent(eventInterpretation.getEvent(), eventId, options);
  }

  /**
   * Handles an internally-generated accessibility event.
   *
   * @param source Source of the event that has occurred
   * @param eventId ID of the event, used for performance monitoring
   * @param eventInterpretation Information about the event
   */
  public void handleEvent(
      AccessibilityNodeInfoCompat source,
      @Nullable EventId eventId,
      EventInterpretation eventInterpretation) {
    HandleEventOptions options =
        new HandleEventOptions().source(source).interpretation(eventInterpretation);
    handleEvent(eventInterpretation.getEvent(), eventId, options);
  }

  /** Handles a standard AccessibilityEvent */
  public void handleEvent(
      AccessibilityEvent event, @Nullable EventId eventId, EventInterpretation eventInterpreted) {

    @Event int eventType = eventInterpreted.getEvent();

    // TODO: getSource may cost time
    // Allocate source node.
    AccessibilityNodeInfoCompat sourceNode = AccessibilityEventUtils.sourceCompat(event);

    // Compute speech and speech flags.
    HandleEventOptions options =
        new HandleEventOptions().object(event).interpretation(eventInterpreted).source(sourceNode);
    handleEvent(eventType, eventId, options);
  }

  private void handleEvent(int event, @Nullable EventId eventId, HandleEventOptions options) {
    // Extract options.
    @Nullable EventInterpretation eventInterpretation = options.eventInterpretation;
    if (eventInterpretation != null) {
      LogUtils.v(TAG, "eventInterpretation= %s", eventInterpretation);
    }
    @Nullable UtteranceCompleteRunnable runnable = options.onCompleteRunnable;

    EventFeedback eventFeedback = getEventFeedback(event, options);

    // Compose earcons.
    SpeakOptions speakOptions = null;
    int earcon = eventFeedback.earcon();
    if (earcon != -1) {
      if (speakOptions == null) {
        speakOptions = SpeakOptions.create();
      }
      Set<Integer> earcons = new HashSet<>();
      earcons.add(earcon);
      speakOptions.setEarcons(earcons);

      Bundle nonSpeechParams = new Bundle();
      double rate = eventFeedback.earconRate();
      if (rate != 1.0) {
        nonSpeechParams.putFloat(Utterance.KEY_METADATA_EARCON_RATE, (float) rate);
      }
      double volume = eventFeedback.earconVolume();
      if (volume != 1.0) {
        nonSpeechParams.putFloat(Utterance.KEY_METADATA_EARCON_VOLUME, (float) volume);
      }
      speakOptions.setNonSpeechParams(nonSpeechParams);
    }

    // Compose haptics.
    int haptic = eventFeedback.haptic();
    if (haptic != -1) {
      if (speakOptions == null) {
        speakOptions = SpeakOptions.create();
      }
      Set<Integer> haptics = new HashSet<>();
      haptics.add(haptic);
      speakOptions.setHaptics(haptics);
    }

    // FLAG_ADVANCED_CONTINUOUS_READING is used for "read from top". Ensure that the flag is set
    // correctly in SpeakOptions regardless of the speech/haptics/earcon feedback. So that the
    // "read from top" will not stop at focusable node with no feedback.
    if (eventFeedback.advanceContinuousReading()) {
      if (speakOptions == null) {
        speakOptions = SpeakOptions.create();
      }
      speakOptions.mFlags |= FeedbackItem.FLAG_ADVANCE_CONTINUOUS_READING;
    }

    // Compose speech, and speech parameters.
    CharSequence ttsOutput =
        eventFeedback.ttsOutput().isPresent() ? eventFeedback.ttsOutput().get() : "";
    if (!TextUtils.isEmpty(ttsOutput)) {
      // Cleans up the TTS output if it is just 1 character long. This will announce single
      // symbols correctly.
      // TODO: Think about a unified clean up strategy instead of calling clean ups at
      // various places in the code.
      ttsOutput = SpeechCleanupUtils.cleanUp(mContext, ttsOutput);
      // Compute queueing mode.
      int queueMode = eventFeedback.queueMode();
      if (queueMode == QUEUE_MODE_INTERRUPTIBLE_IF_LONG) {
        queueMode =
            (ttsOutput.length() <= VERBOSE_UTTERANCE_THRESHOLD_CHARACTERS)
                ? QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH_CAN_IGNORE_INTERRUPTS
                : QUEUE_MODE_QUEUE;
      }

      // Compose queue group to clear.
      int clearQueueGroup = eventFeedback.ttsClearQueueGroup();
      // Compose other speech flags/parameters.
      int flags = eventFeedback.getOutputSpeechFlags();
      double speechPitch = eventFeedback.ttsPitch();
      Bundle speechParams = new Bundle();
      speechParams.putFloat(SpeechParam.PITCH, (float) speechPitch);

      // Output feedback: speech, haptics, earcons.
      if (speakOptions == null) {
        speakOptions = SpeakOptions.create();
      }
      speakOptions
          .setQueueMode(queueMode)
          .setSpeechParams(speechParams)
          .setUtteranceGroup(clearQueueGroup)
          .setCompletedAction(runnable);
      speakOptions.mFlags |= flags;
      speak(ttsOutput, eventId, speakOptions);
    } else {
      if (speakOptions != null) {
        speakOptions.mFlags |= FeedbackItem.FLAG_NO_SPEECH;
        // TODO: Return feedback as output when feedback-mappers are separated from
        // asynchronous event interpreters.
        speak("", eventId, speakOptions);
      }
      if (runnable != null) {
        runnable.run(SpeechController.STATUS_NOT_SPOKEN);
      }
    }
  }

  public @Nullable String parseTTSText(
      @Nullable AccessibilityNodeInfoCompat source,
      int event,
      EventInterpretation eventInterpretation) {
    EventFeedback eventFeedback =
        getEventFeedback(
            event, new HandleEventOptions().source(source).interpretation(eventInterpretation));
    CharSequence ttsText =
        (eventFeedback != null && eventFeedback.ttsOutput().isPresent())
            ? eventFeedback.ttsOutput().get()
            : null;

    if (ttsText == null) {
      return null;
    }
    return ttsText.toString();
  }

  /**
   * Return {@link EventFeedback} to provide the feedback with the given {@code event} and {@link
   * HandleEventOptions} .
   *
   * @param event Type of event that has occurred
   * @param options The a11y information that compositor handle the event feedback
   * @return eventFeedback
   */
  private EventFeedback getEventFeedback(int event, HandleEventOptions options) {
    EventFeedback eventFeedback = talkbackFeedbackProvider.buildEventFeedback(event, options);
    if (eventFeedback.equals(EMPTY_FEEDBACK) && USE_LEGACY_COMPOSITOR_JSON) {
      eventFeedback = parseTreeFeedbackProvider.buildEventFeedback(event, options);
    }
    return eventFeedback;
  }

  /**
   * The data structure that holds the <a href="http://what/a11y">a11y</a> information for
   * Compositor to handle the event feedback.
   */
  public static class HandleEventOptions {
    public @Nullable AccessibilityEvent eventObject;
    public @Nullable EventInterpretation eventInterpretation;
    public @Nullable AccessibilityNodeInfoCompat sourceNode;
    public @Nullable UtteranceCompleteRunnable onCompleteRunnable;

    @CanIgnoreReturnValue
    public HandleEventOptions object(AccessibilityEvent eventObjArg) {
      eventObject = eventObjArg;
      return this;
    }

    @CanIgnoreReturnValue
    public HandleEventOptions interpretation(EventInterpretation eventInterpArg) {
      eventInterpretation = eventInterpArg;
      return this;
    }

    @CanIgnoreReturnValue
    public HandleEventOptions source(@Nullable AccessibilityNodeInfoCompat sourceArg) {
      sourceNode = sourceArg;
      return this;
    }

    @CanIgnoreReturnValue
    public HandleEventOptions onComplete(UtteranceCompleteRunnable runnableArg) {
      onCompleteRunnable = runnableArg;
      return this;
    }
  }

  private void speak(CharSequence ttsOutput, @Nullable EventId eventId, SpeakOptions speakOptions) {
    if (speechController != null) {
      speechController.speak(ttsOutput, eventId, speakOptions);
    }
    if (speaker != null) {
      speaker.speak(ttsOutput, eventId, speakOptions);
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////
  // Methods for logging

  public static String eventTypeToString(int eventType) {
    switch (eventType) {
      case EVENT_UNKNOWN:
        return "EVENT_UNKNOWN";
      case EVENT_SPOKEN_FEEDBACK_ON:
        return "EVENT_SPOKEN_FEEDBACK_ON";
      case EVENT_SPOKEN_FEEDBACK_DISABLED:
        return "EVENT_SPOKEN_FEEDBACK_DISABLED";
      case EVENT_CAPS_LOCK_ON:
        return "EVENT_CAPS_LOCK_ON";
      case EVENT_CAPS_LOCK_OFF:
        return "EVENT_CAPS_LOCK_OFF";
      case EVENT_NUM_LOCK_ON:
        return "EVENT_NUM_LOCK_ON";
      case EVENT_NUM_LOCK_OFF:
        return "EVENT_NUM_LOCK_OFF";
      case EVENT_SCROLL_LOCK_ON:
        return "EVENT_SCROLL_LOCK_ON";
      case EVENT_SCROLL_LOCK_OFF:
        return "EVENT_SCROLL_LOCK_OFF";
      case EVENT_ORIENTATION_PORTRAIT:
        return "EVENT_ORIENTATION_PORTRAIT";
      case EVENT_ORIENTATION_LANDSCAPE:
        return "EVENT_ORIENTATION_LANDSCAPE";
      case EVENT_SPEAK_HINT:
        return "EVENT_SPEAK_HINT";
      case EVENT_SCROLL_POSITION:
        return "EVENT_SCROLL_POSITION";
      case EVENT_INPUT_DESCRIBE_NODE:
        return "EVENT_INPUT_DESCRIBE_NODE";
      case EVENT_MAGNIFICATION_CHANGED:
        return "EVENT_MAGNIFICATION_CHANGED";
      case EVENT_TYPE_INPUT_TEXT_CLEAR:
        return "EVENT_TYPE_INPUT_TEXT_CLEAR";
      case EVENT_TYPE_INPUT_TEXT_REMOVE:
        return "EVENT_TYPE_INPUT_TEXT_REMOVE";
      case EVENT_TYPE_INPUT_TEXT_ADD:
        return "EVENT_TYPE_INPUT_TEXT_ADD";
      case EVENT_TYPE_INPUT_TEXT_REPLACE:
        return "EVENT_TYPE_INPUT_TEXT_REPLACE";
      case EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD:
        return "EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD";
      case EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE:
        return "EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE";
      case EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE:
        return "EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE";
      case EVENT_TYPE_INPUT_CHANGE_INVALID:
        return "EVENT_TYPE_INPUT_CHANGE_INVALID";
      case EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT:
        return "EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT";
      case EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING:
        return "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING";
      case EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END:
        return "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END";
      case EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION:
        return "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION";
      case EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION:
        return "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION";
      case EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED:
        return "EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED";
      case EVENT_TYPE_INPUT_SELECTION_CUT:
        return "EVENT_TYPE_INPUT_SELECTION_CUT";
      case EVENT_TYPE_INPUT_SELECTION_PASTE:
        return "EVENT_TYPE_INPUT_SELECTION_PASTE";
      case EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL:
        return "EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL";
      case EVENT_TYPE_INPUT_SELECTION_SELECT_ALL:
        return "EVENT_TYPE_INPUT_SELECTION_SELECT_ALL";
      case EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD:
        return "EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD";
      case EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION:
        return "EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION";
      case EVENT_TYPE_SET_TEXT_BY_ACTION:
        return "EVENT_TYPE_SET_TEXT_BY_ACTION";
      default:
        return AccessibilityEventUtils.typeToString(eventType);
    }
  }

  private static String getFlavorName(@Flavor int flavor) {
    switch (flavor) {
      case FLAVOR_NONE:
        return "FLAVOR_NONE";
      case FLAVOR_TV:
        return "FLAVOR_TV";
      case FLAVOR_JASPER:
        return "FLAVOR_JASPER";
      default:
        return "UNKNOWN";
    }
  }
}

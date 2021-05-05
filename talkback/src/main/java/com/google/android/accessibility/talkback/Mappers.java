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

import static com.google.android.accessibility.compositor.Compositor.EVENT_SPEAK_HINT;
import static com.google.android.accessibility.compositor.Compositor.EVENT_UNKNOWN;
import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.INTERRUPT;
import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.READ_FOCUSED_CONTENT;
import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.DISABLE_PASSTHROUGH;
import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.STOP_TIMER;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_UNKNOWN;
import static com.google.android.accessibility.utils.keyboard.KeyComboManager.ACTION_UNKNOWN;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_NO_HISTORY;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UNKNOWN;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.EventInterpretation;
import com.google.android.accessibility.compositor.HintEventInterpretation;
import com.google.android.accessibility.talkback.Feedback.Speech;
import com.google.android.accessibility.talkback.Interpretation.CompositorID;
import com.google.android.accessibility.talkback.Interpretation.VoiceCommand;
import com.google.android.accessibility.talkback.actor.DirectionNavigationMapper;
import com.google.android.accessibility.talkback.actor.voicecommands.VoiceCommandMapper;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusManager;
import com.google.android.accessibility.talkback.focusmanagement.FocusFeedbackMapper;
import com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForTapAndTouchExploration;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.LogDepth;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Feedback-mapper stage in the pipeline. Converts event-interpretations to feedback-actions that
 * will run in actors.
 */
public final class Mappers {

  public static final String LOG_TAG = "Mappers";

  private final Context context;
  private final Compositor compositor;
  private final FocusFinder focusFinder;

  public Mappers(Context context, Compositor compositor, FocusFinder focusFinder) {
    this.context = context;
    this.compositor = compositor;
    this.focusFinder = focusFinder;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  /**
   * Core business-logic, which maps fully-interpreted events to high-level feedback-actions. All
   * mapper sub-functions should be static, stateless, using only variables input. Recycles
   * interpretation.
   */
  public @Nullable Feedback mapToFeedback(
      @Nullable EventId eventId,
      @Nullable AccessibilityEvent event,
      @Nullable Interpretation interpretation,
      @Nullable AccessibilityNodeInfoCompat eventSourceNode) {

    final Variables variables = new Variables(context, event, interpretation);

    try {

      int depth = 0;
      LogDepth.log(
          LOG_TAG,
          depth,
          "mapToFeedback() eventId=%s event=%s interpretation=%s eventSourceNode=%s",
          eventId,
          event,
          interpretation,
          eventSourceNode);

      if (interpretation instanceof Interpretation.ID) {
        // Simple mapping rules based only on interpretation-ID.
        Interpretation.ID.Value id = variables.interpretationID(depth);
        if (id != null) {
          switch (id) {
            case CONTINUOUS_READ_CONTENT_FOCUSED:
              return Feedback.create(
                  eventId, Feedback.continuousRead(READ_FOCUSED_CONTENT).build());
            case CONTINUOUS_READ_INTERRUPT:
              return Feedback.create(eventId, Feedback.continuousRead(INTERRUPT).build());
            case SCROLL_CANCEL_TIMEOUT:
              return Feedback.create(eventId, Feedback.scrollCancelTimeout().build());
            case STATE_CHANGE:
              return Feedback.create(
                  eventId,
                  Feedback.speech(
                          variables.stateDescription(depth),
                          SpeakOptions.create()
                              .setQueueMode(QUEUE_MODE_QUEUE)
                              .setFlags(
                                  FLAG_NO_HISTORY
                                      | FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                                      | FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE))
                      .build());
            case PASS_THROUGH_INTERACTION_START:
              return Feedback.create(eventId, Feedback.passThroughMode(STOP_TIMER).build());
            case PASS_THROUGH_INTERACTION_END:
              return Feedback.create(
                  eventId, Feedback.passThroughMode(DISABLE_PASSTHROUGH).build());
            case ACCESSIBILITY_FOCUSED:
              // do nothing
          }
        }
      } else if (interpretation instanceof Interpretation.CompositorID) {
        // TODO: Make compositor return feedback by value instead of callback.
        @Compositor.Event int id = variables.compositorEventID(depth);
        if (id == EVENT_SPEAK_HINT) {
          EventInterpretation eventInterp =
              ((CompositorID) interpretation).getEventInterpretation();
          int hintType = eventInterp.getHint().getHintType();
          if ((hintType == HintEventInterpretation.HINT_TYPE_INPUT_FOCUS)
              || (hintType == HintEventInterpretation.HINT_TYPE_ACCESSIBILITY_FOCUS)
              || (hintType == HintEventInterpretation.HINT_TYPE_SCREEN)
              || (hintType == HintEventInterpretation.HINT_TYPE_SELECTOR)) {
            String hintTTSOutput =
                compositor.parseTTSText(
                    ((CompositorID) interpretation).getNode(), eventInterp.getEvent(), eventInterp);
            if (TextUtils.isEmpty(hintTTSOutput)) {
              return null;
            }

            int hintFlags = FeedbackItem.FLAG_NO_HISTORY;
            if (eventInterp.getHint().getForceFeedbackAudioPlaybackActive()) {
              hintFlags |= FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE;
            }

            if (eventInterp.getHint().getForceFeedbackMicrophoneActive()) {
              hintFlags |= FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE;
            }

            return Feedback.create(
                eventId,
                Feedback.Part.builder()
                    .setSpeech(
                        Speech.builder()
                            .setAction(Speech.Action.SPEAK)
                            .setHintSpeakOptions(
                                SpeechController.SpeakOptions.create().setFlags(hintFlags))
                            .setHint(hintTTSOutput)
                            .build())
                    .build());
          }
        } else if (id != EVENT_UNKNOWN) {
          EventInterpretation compositorEventInterp = new EventInterpretation(id);
          compositorEventInterp.setReadOnly();
          if (event == null) {
            compositor.handleEvent(eventSourceNode, eventId, compositorEventInterp);
          } else {
            compositor.handleEvent(event, eventId, compositorEventInterp);
          }
        }
      } else if (interpretation instanceof Interpretation.KeyCombo) {
        return DirectionNavigationMapper.onComboPerformed(eventId, variables, depth);
      } else if (interpretation instanceof Interpretation.VoiceCommand) {
        return VoiceCommandMapper.handleSpeechCommand(eventId, variables, depth);
      } else if (interpretation instanceof Interpretation.DirectionNavigation) {
        return Feedback.create(
            eventId,
            Feedback.part()
                .setFocusDirection(
                    Feedback.directionNavigationFollowTo(
                            variables.directionDestination(depth), variables.direction(depth))
                        .build())
                .build());
      } else if (interpretation instanceof Interpretation.InputFocus) {
        Feedback.Part.Builder focusBuilder =
            AccessibilityFocusManager.onViewTargeted(eventId, variables, depth);
        if (focusBuilder == null) {
          LogDepth.log(LOG_TAG, depth, "target is null or fails to refresh");
          return null;
        }
        return Feedback.create(eventId, focusBuilder.build());
      } else if (interpretation instanceof Interpretation.Scroll) {
        Feedback.Part.Builder focusBuilder =
            AccessibilityFocusManager.onScrollEvent(eventId, variables, depth, focusFinder);
        if (focusBuilder == null) {
          LogDepth.log(LOG_TAG, depth, "Scroll event cannot map to a feedback");
          return null;
        }
        return Feedback.create(eventId, focusBuilder.build());
      } else if (interpretation instanceof Interpretation.WindowChange) {
        return FocusFeedbackMapper.mapWindowChangeToFocusAction(eventId, variables, depth);
      } else if (interpretation instanceof Interpretation.Touch) {
        return FocusFeedbackMapper.mapTouchToFocusAction(eventId, variables, depth);
      }
    } finally {
      if (interpretation != null) {
        interpretation.recycle();
      }
      variables.recycle();
    }

    return null;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Inner class for moderated variable access

  /**
   * Wrapper around Event and Interpretation, to simplify variable access in mappers, and to log
   * values while reading them.
   */
  public static final class Variables {
    private final Context context;
    private SharedPreferences prefs;
    private final @Nullable AccessibilityEvent event;
    private final @Nullable Interpretation interpretation;
    private @Nullable AccessibilityNodeInfoCompat source; // Owner, must recycle.

    public Variables(
        Context context,
        @Nullable AccessibilityEvent event,
        @Nullable Interpretation interpretation) {
      this.context = context;
      this.event = event;
      this.interpretation = interpretation;
      prefs = SharedPreferencesUtils.getSharedPreferences(context);
    }

    public void recycle() {
      AccessibilityNodeInfoUtils.recycleNodes(source);
    }

    /** Caller does not own returned node. */
    public @Nullable AccessibilityNodeInfoCompat source(int depth) {
      if (source == null) {
        source = AccessibilityEventUtils.sourceCompat(event);
      }
      return source;
    }

    public @SearchDirection int scrollDirection(int depth) {
      @SearchDirectionOrUnknown
      int directionID =
          (interpretation instanceof Interpretation.Scroll)
              ? ((Interpretation.Scroll) interpretation).direction
              : SEARCH_FOCUS_UNKNOWN;
      LogDepth.logVar(
          LOG_TAG,
          ++depth,
          "scrollDirection",
          TraversalStrategyUtils.directionToString(directionID));
      return directionID;
    }

    public Interpretation.ID.Value interpretationID(int depth) {
      Interpretation.ID.Value id =
          (interpretation instanceof Interpretation.ID)
              ? ((Interpretation.ID) interpretation).value
              : null;
      LogDepth.logVar(LOG_TAG, ++depth, "interpretationID", id);
      return id;
    }

    public @Compositor.Event int compositorEventID(int depth) {
      int id =
          (interpretation instanceof Interpretation.CompositorID)
              ? ((Interpretation.CompositorID) interpretation).value
              : EVENT_UNKNOWN;
      LogDepth.logVar(LOG_TAG, ++depth, "compositorEventID", id);
      return id;
    }

    public int keyCombo(int depth) {
      int id =
          (interpretation instanceof Interpretation.KeyCombo)
              ? ((Interpretation.KeyCombo) interpretation).id()
              : ACTION_UNKNOWN;
      LogDepth.logVar(LOG_TAG, ++depth, "keyCombo", id);
      return id;
    }

    public VoiceCommand voiceCommand(int depth) {
      VoiceCommand voiceCommand =
          (interpretation instanceof Interpretation.VoiceCommand)
              ? ((Interpretation.VoiceCommand) interpretation)
              : null;
      Interpretation.VoiceCommand.Action action =
          (voiceCommand == null) ? VOICE_COMMAND_UNKNOWN : voiceCommand.command();

      LogDepth.logVar(LOG_TAG, ++depth, "voiceCommand", action);
      return voiceCommand;
    }

    public @SearchDirection int direction(int depth) {
      @SearchDirection
      int id =
          (interpretation instanceof Interpretation.DirectionNavigation)
              ? ((Interpretation.DirectionNavigation) interpretation).direction()
              : SEARCH_FOCUS_UNKNOWN;
      LogDepth.logVar(LOG_TAG, ++depth, "direction", id);
      return id;
    }

    /** Caller is not responsible to recycle returned node. */
    public @Nullable AccessibilityNodeInfoCompat directionDestination(int depth) {
      return (interpretation instanceof Interpretation.DirectionNavigation)
          ? ((Interpretation.DirectionNavigation) interpretation).destination()
          : null;
    }

    /** Caller is not responsible to recycle returned node. */
    public @Nullable AccessibilityNodeInfoCompat inputFocusTarget(int depth) {
      return (interpretation instanceof Interpretation.InputFocus)
          ? ((Interpretation.InputFocus) interpretation).getNode()
          : null;
    }

    public @Nullable CharSequence stateDescription(int depth) {
      @Nullable CharSequence state = AccessibilityNodeInfoUtils.getState(source(depth));
      LogDepth.logVar(LOG_TAG, ++depth, "stateDescription", state);
      return state;
    }

    public boolean forceRestoreFocus(int depth) {
      boolean force =
          (interpretation instanceof Interpretation.WindowChange)
              ? ((Interpretation.WindowChange) interpretation).forceRestoreFocus()
              : false;
      LogDepth.logVar(LOG_TAG, ++depth, "forceRestoreFocus", force);
      return force;
    }

    public boolean isTv(int depth) {
      boolean tv = FeatureSupport.isTv(context);
      LogDepth.logVar(LOG_TAG, ++depth, "isTv", tv);
      return tv;
    }

    public boolean isInitialFocusEnabled(int depth) {
      // Initial focus can be enabled in TV for feature parity with mobile and consistency of input
      // and accessibility focus.
      boolean isInitialFocusEnabled =
          !FeatureSupport.isTv(context) || TvNavigation.isInitialFocusEnabled(context);
      LogDepth.logVar(LOG_TAG, ++depth, "isInitialFocusEnabled", isInitialFocusEnabled);
      return isInitialFocusEnabled;
    }

    public @Nullable ScreenState screenState(int depth) {
      @Nullable
      ScreenState state =
          (interpretation instanceof Interpretation.WindowChange)
              ? ((Interpretation.WindowChange) interpretation).screenState()
              : null;
      LogDepth.logVar(LOG_TAG, ++depth, "screenState", state);
      return state;
    }

    public boolean liftToType(int depth) {
      boolean enabled = FocusProcessorForTapAndTouchExploration.ENABLE_LIFT_TO_TYPE;
      LogDepth.logVar(LOG_TAG, ++depth, "liftToType", enabled);
      return enabled;
    }

    public boolean singleTap(int depth) {
      boolean enabled =
          SharedPreferencesUtils.getBooleanPref(
              prefs,
              context.getResources(),
              R.string.pref_single_tap_key,
              R.bool.pref_single_tap_default);
      LogDepth.logVar(LOG_TAG, ++depth, "singleTap", enabled);
      return enabled;
    }

    public Interpretation.Touch.Action touchAction(int depth) {
      Interpretation.Touch.Action action =
          (interpretation instanceof Interpretation.Touch)
              ? ((Interpretation.Touch) interpretation).action()
              : null;
      LogDepth.logVar(LOG_TAG, ++depth, "touchAction", action);
      return action;
    }

    /** Caller does not own returned node. */
    public @Nullable AccessibilityNodeInfoCompat touchTarget(int depth) {
      return (interpretation instanceof Interpretation.Touch)
          ? ((Interpretation.Touch) interpretation).target()
          : null;
    }

    @Override
    public String toString() {
      return "interpretation=" + interpretation + " event=" + event;
    }
  }
}

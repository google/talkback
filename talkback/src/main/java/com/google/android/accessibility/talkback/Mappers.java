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

import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.INTERRUPT;
import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.READ_FOCUSED_CONTENT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.ENSURE_ACCESSIBILITY_FOCUS_ON_SCREEN;
import static com.google.android.accessibility.talkback.Feedback.HINT;
import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.DISABLE_PASSTHROUGH;
import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.STOP_TIMER;
import static com.google.android.accessibility.talkback.Interpretation.VoiceCommand.Action.VOICE_COMMAND_UNKNOWN;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_UNKNOWN;
import static com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForTapAndTouchExploration.DOUBLE_TAP;
import static com.google.android.accessibility.utils.AccessibilityEventUtils.UNKNOWN_EVENT_TYPE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_NO_HISTORY;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UNKNOWN;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback.Focus;
import com.google.android.accessibility.talkback.Feedback.UiChange.Action;
import com.google.android.accessibility.talkback.Interpretation.CompositorID;
import com.google.android.accessibility.talkback.Interpretation.UiChange;
import com.google.android.accessibility.talkback.Interpretation.UiChange.UiChangeType;
import com.google.android.accessibility.talkback.Interpretation.VoiceCommand;
import com.google.android.accessibility.talkback.actor.voicecommands.VoiceCommandMapper;
import com.google.android.accessibility.talkback.compositor.Compositor;
import com.google.android.accessibility.talkback.compositor.EventInterpretation;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorAccessibilityHints;
import com.google.android.accessibility.talkback.focusmanagement.FocusFeedbackMapper;
import com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForTapAndTouchExploration.TypingMethod;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.talkback.monitor.BatteryMonitor;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.LogDepth;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Feedback-mapper stage in the pipeline. Converts event-interpretations to feedback-actions that
 * will run in actors.
 */
public final class Mappers {

  public static final String LOG_TAG = "Mappers";

  private static final int KEY_ACTION_UNKNOWN = -1;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member data

  private final @NonNull Context context;
  private final Compositor compositor;
  private final FocusFinder focusFinder;
  private Monitors.State monitors;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public Mappers(@NonNull Context context, Compositor compositor, FocusFinder focusFinder) {
    this.context = context;
    this.compositor = compositor;
    this.focusFinder = focusFinder;
  }

  public void setMonitors(Monitors.State monitors) {
    this.monitors = monitors;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  /**
   * Core business-logic, which maps fully-interpreted events to high-level feedback-actions. All
   * mapper sub-functions should be static, stateless, using only variables input.
   */
  public @Nullable Feedback mapToFeedback(
      @Nullable EventId eventId,
      @Nullable AccessibilityEvent event,
      @Nullable Interpretation interpretation,
      @Nullable AccessibilityNodeInfoCompat eventSourceNode) {

    final @NonNull Variables variables = new Variables(context, event, interpretation, monitors);

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
            return Feedback.create(eventId, Feedback.continuousRead(READ_FOCUSED_CONTENT).build());
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
                                    | FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                                    | FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE))
                    .build());
          case PASS_THROUGH_INTERACTION_START:
            return Feedback.create(eventId, Feedback.passThroughMode(STOP_TIMER).build());
          case PASS_THROUGH_INTERACTION_END:
            return Feedback.create(eventId, Feedback.passThroughMode(DISABLE_PASSTHROUGH).build());
          case ACCESSIBILITY_FOCUSED:
            // do nothing
            break;
          case SUBTREE_CHANGED:
          case ACCESSIBILITY_EVENT_IDLE:
            return Feedback.create(
                eventId,
                Feedback.part()
                    .setFocus(
                        Focus.builder().setAction(ENSURE_ACCESSIBILITY_FOCUS_ON_SCREEN).build())
                    .build());
          case SPELLING_SUGGESTION_HINT:
            return Feedback.create(
                eventId,
                ProcessorAccessibilityHints.suggestionSpanToHint(
                        context, compositor.getTextComposer())
                    .build());
        }
      }
    } else if (interpretation instanceof Interpretation.CompositorID) {
      // TODO: Make compositor return feedback by value (vs executing speech/etc).
      @Compositor.Event int id = variables.compositorEventID(depth);
      if (id != EVENT_UNKNOWN) {
        EventInterpretation compositorEventInterp =
            ((CompositorID) interpretation).getEventInterpretation();
        if (compositorEventInterp == null) {
          compositorEventInterp = new EventInterpretation(id);
        }
        compositorEventInterp.setReadOnly();
        if (event != null) {
          compositor.handleEvent(event, eventId, compositorEventInterp);
        } else if (eventSourceNode != null) {
          compositor.handleEvent(eventSourceNode, eventId, compositorEventInterp);
        } else {
          compositor.handleEvent(eventId, compositorEventInterp);
        }
      }
    } else if (interpretation instanceof Interpretation.HintableEvent) {
      Feedback.Part.@Nullable Builder feedback =
          ProcessorAccessibilityHints.toFeedback(
              variables, depth, context, compositor.getTextComposer());
      return (feedback == null) ? null : Feedback.create(eventId, feedback.build());
    } else if (interpretation instanceof Interpretation.Key) {
      if (variables.keyAction(depth) == KeyEvent.ACTION_DOWN) {
        // Cancel all delayed hints.
        return Feedback.create(eventId, Feedback.interrupt(HINT, /* level= */ 2).build());
      }
    } else if (interpretation instanceof Interpretation.Power) {
      if (variables.isPhoneCallActive(depth)) {
        return null;
      }
      boolean powerConnected = variables.powerConnected(depth);
      int batteryPercent = variables.batteryPercent(depth);
      @Nullable String announcement = null;
      if (powerConnected) {
        if (batteryPercent == BatteryMonitor.UNKNOWN_LEVEL) {
          announcement =
              context.getString(
                  R.string.template_charging_lite,
                  context.getString(R.string.notification_type_status_started));
        } else {
          announcement =
              context.getString(
                  R.string.template_charging,
                  context.getString(R.string.notification_type_status_started),
                  String.valueOf(batteryPercent));
        }
      } else { // Power disconnected
        if (batteryPercent == BatteryMonitor.UNKNOWN_LEVEL) {
          announcement =
              context.getString(
                  R.string.template_charging_lite,
                  context.getString(R.string.notification_type_status_stopped));
        } else {
          announcement =
              context.getString(
                  R.string.template_charging,
                  context.getString(R.string.notification_type_status_stopped),
                  String.valueOf(batteryPercent));
        }
      }
      if (announcement == null) {
        return null;
      }
      SpeechController.SpeakOptions speakOptions =
          SpeechController.SpeakOptions.create()
              .setQueueMode(SpeechController.QUEUE_MODE_INTERRUPT)
              .setFlags(
                  FeedbackItem.FLAG_NO_HISTORY
                      | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE);
      return Feedback.create(
          eventId, Feedback.Part.builder().speech(announcement, speakOptions).build());
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
      @Nullable AccessibilityNodeInfoCompat targetedNode = variables.inputFocusTarget(depth);
      if (targetedNode == null || !targetedNode.refresh()) {
        LogDepth.log(LOG_TAG, depth, "Return, target is null or fails to refresh");
        return null;
      }
      FocusActionInfo focusActionInfo =
          FocusActionInfo.builder().setSourceAction(FocusActionInfo.FOCUS_SYNCHRONIZATION).build();
      return Feedback.create(
          eventId,
          Feedback.part().setFocus(Feedback.focus(targetedNode, focusActionInfo).build()).build());
    } else if (interpretation instanceof Interpretation.ManualScroll) {
      Feedback.Part.Builder focusBuilder =
          FocusFeedbackMapper.onNodeManuallyScrolled(variables, depth, focusFinder);
      if (focusBuilder == null) {
        LogDepth.log(LOG_TAG, depth, "Return, Manual scroll event cannot map to a feedback");
        return null;
      }
      return Feedback.create(eventId, focusBuilder.build());
    } else if (interpretation instanceof Interpretation.WindowChange) {
      return FocusFeedbackMapper.mapWindowChangeToFocusAction(eventId, variables, depth);
    } else if (interpretation instanceof Interpretation.Touch) {
      return FocusFeedbackMapper.mapTouchToFocusAction(eventId, variables, depth);
    } else if (interpretation instanceof Interpretation.AccessibilityFocused) {
      if (variables.needsCaption(depth) && eventSourceNode != null) {
        return Feedback.create(eventId, Feedback.performImageCaptions(eventSourceNode).build());
      }
    } else if (interpretation instanceof Interpretation.UiChange) {
      @Nullable Rect sourceBounds = variables.sourceBoundsInScreen(depth);
      UiChangeType uiChangeType = variables.uiChangeType(depth);
      if (uiChangeType == UiChangeType.WHOLE_SCREEN_UI_CHANGED) {
        return Feedback.create(eventId, Feedback.wholeScreenChange().build());
      } else {
        return Feedback.create(
            eventId,
            Feedback.partialUiChange(
                    (uiChangeType == UiChangeType.VIEW_CLICKED)
                        ? Action.CLEAR_CACHE_FOR_VIEW
                        : Action.CLEAR_SCREEN_CACHE,
                    sourceBounds)
                .build());
      }
    } else if (interpretation instanceof Interpretation.Scroll) {
      if (variables.isMediaPlayerAutoScroll(depth) || !variables.isFromScrollable(depth)) {
        return null;
      }
      float rate = (float) Math.pow(2.0f, (variables.scrollPercent(depth) / 50.0f) - 1);
      float volume = rate;
      long minSeparationMillisec = 250;
      Feedback feedback =
          Feedback.create(
              eventId,
              Feedback.part()
                  .setSound(
                      Feedback.Sound.create(R.raw.scroll_tone, rate, volume, minSeparationMillisec))
                  .build());
      return feedback;
    }

    return null;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Inner class for moderated variable access

  /**
   * Wrapper around Event and Interpretation, to simplify variable access in mappers, and to log
   * values while reading them.
   */
  public static class Variables {
    private final Context context;
    private SharedPreferences prefs;
    private final @Nullable AccessibilityEvent event;
    private final @Nullable Interpretation interpretation;
    private @Nullable AccessibilityNodeInfoCompat source;
    private final Monitors.State monitors;
    private final FormFactorUtils formFactorUtils = FormFactorUtils.getInstance();

    public Variables(
        Context context,
        @Nullable AccessibilityEvent event,
        @Nullable Interpretation interpretation,
        Monitors.State monitors) {
      this.context = context;
      this.event = event;
      this.interpretation = interpretation;
      prefs = SharedPreferencesUtils.getSharedPreferences(context);
      this.monitors = monitors;
    }

    public int eventType(int depth) {
      int eventType = (event == null) ? UNKNOWN_EVENT_TYPE : event.getEventType();
      LogDepth.logVar(LOG_TAG, ++depth, "eventType", eventType);
      return eventType;
    }

    public @Nullable AccessibilityNodeInfoCompat source(int depth) {
      if (source == null) {
        source = AccessibilityEventUtils.sourceCompat(event);
      }
      return source;
    }

    @SearchDirection
    public int scrollDirection(int depth) {
      @SearchDirectionOrUnknown
      int directionID =
          (interpretation instanceof Interpretation.ManualScroll)
              ? ((Interpretation.ManualScroll) interpretation).direction()
              : SEARCH_FOCUS_UNKNOWN;
      LogDepth.logVar(
          LOG_TAG,
          ++depth,
          "scrollDirection",
          TraversalStrategyUtils.directionToString(directionID));
      return directionID;
    }

    public @Nullable AccessibilityNodeInfoCompat currentNode(int depth) {
      AccessibilityNodeInfoCompat currentNode =
          (interpretation instanceof Interpretation.ManualScroll)
              ? ((Interpretation.ManualScroll) interpretation).currentFocusedNode()
              : null;
      LogDepth.logVar(LOG_TAG, ++depth, "currentNode", currentNode);
      return currentNode;
    }

    public Interpretation.ID.Value interpretationID(int depth) {
      Interpretation.ID.Value id =
          (interpretation instanceof Interpretation.ID)
              ? ((Interpretation.ID) interpretation).value
              : null;
      LogDepth.logVar(LOG_TAG, ++depth, "interpretationID", id);
      return id;
    }

    @Compositor.Event
    public int compositorEventID(int depth) {
      int id =
          (interpretation instanceof Interpretation.CompositorID)
              ? ((Interpretation.CompositorID) interpretation).value
              : EVENT_UNKNOWN;
      LogDepth.logVar(LOG_TAG, ++depth, "compositorEventID", id);
      return id;
    }

    public boolean isPhoneCallActive(int depth) {
      boolean active = (monitors == null) ? false : monitors.isPhoneCallActive();
      LogDepth.logVar(LOG_TAG, ++depth, "isPhoneCallActive", active);
      return active;
    }

    public boolean powerConnected(int depth) {
      boolean connected =
          (interpretation instanceof Interpretation.Power)
              ? ((Interpretation.Power) interpretation).connected
              : false;
      LogDepth.logVar(LOG_TAG, ++depth, "powerConnected", connected);
      return connected;
    }

    public int batteryPercent(int depth) {
      int batteryPercent =
          (interpretation instanceof Interpretation.Power)
              ? ((Interpretation.Power) interpretation).percent
              : BatteryMonitor.UNKNOWN_LEVEL;
      LogDepth.logVar(LOG_TAG, ++depth, "batteryPercent", batteryPercent);
      return batteryPercent;
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

    @SearchDirection
    public int direction(int depth) {
      @SearchDirection
      int id =
          (interpretation instanceof Interpretation.DirectionNavigation)
              ? ((Interpretation.DirectionNavigation) interpretation).direction()
              : SEARCH_FOCUS_UNKNOWN;
      LogDepth.logVar(LOG_TAG, ++depth, "direction", id);
      return id;
    }

    public @Nullable AccessibilityNodeInfoCompat directionDestination(int depth) {
      return (interpretation instanceof Interpretation.DirectionNavigation)
          ? ((Interpretation.DirectionNavigation) interpretation).destination()
          : null;
    }

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
      boolean tv = formFactorUtils.isAndroidTv();
      LogDepth.logVar(LOG_TAG, ++depth, "isTv", tv);
      return tv;
    }

    public @Nullable ScreenState screenState(int depth) {
      @Nullable ScreenState state = null;
      if (interpretation instanceof Interpretation.WindowChange) {
        state = ((Interpretation.WindowChange) interpretation).screenState();
      } else if (interpretation instanceof Interpretation.ManualScroll) {
        state = ((Interpretation.ManualScroll) interpretation).screenState();
      }
      LogDepth.logVar(LOG_TAG, ++depth, "screenState", state);
      return state;
    }

    public boolean liftToType(int depth) {
      @TypingMethod
      int typingMethod =
          SharedPreferencesUtils.getIntFromStringPref(
              prefs,
              context.getResources(),
              R.string.pref_typing_confirmation_key,
              R.string.pref_typing_confirmation_default);
      LogDepth.logVar(LOG_TAG, ++depth, "liftToType", typingMethod);
      return typingMethod != DOUBLE_TAP;
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

    public @Nullable AccessibilityNodeInfoCompat touchTarget(int depth) {
      return (interpretation instanceof Interpretation.Touch)
          ? ((Interpretation.Touch) interpretation).target()
          : null;
    }

    public boolean needsCaption(int depth) {
      boolean result =
          (interpretation instanceof Interpretation.AccessibilityFocused)
              && ((Interpretation.AccessibilityFocused) interpretation).needsCaption();
      LogDepth.logVar(LOG_TAG, ++depth, "needsCaption", result);
      return result;
    }

    public @Nullable Rect sourceBoundsInScreen(int depth) {
      @Nullable Rect sourceBounds = null;
      if (interpretation instanceof UiChange) {
        sourceBounds = ((UiChange) interpretation).sourceBoundsInScreen();
      }
      LogDepth.logVar(LOG_TAG, ++depth, "sourceBoundsInScreen", sourceBounds);
      return sourceBounds;
    }

    public UiChangeType uiChangeType(int depth) {
      UiChangeType uiChangeType =
          (interpretation instanceof UiChange)
              ? ((UiChange) interpretation).uiChangeType()
              : UiChangeType.UNKNOWN;
      LogDepth.logVar(LOG_TAG, ++depth, "uiChangeType", uiChangeType);
      return uiChangeType;
    }

    public boolean isMediaPlayerAutoScroll(int depth) {
      boolean result =
          (interpretation instanceof Interpretation.Scroll)
              && ((Interpretation.Scroll) interpretation).scroll.isMediaPlayerAutoScroll;
      LogDepth.logVar(LOG_TAG, ++depth, "isMediaPlayerAutoScroll", result);
      return result;
    }

    public boolean isFromScrollable(int depth) {
      boolean result =
          (interpretation instanceof Interpretation.Scroll)
              && ((Interpretation.Scroll) interpretation).scroll.isFromScrollable;
      LogDepth.logVar(LOG_TAG, ++depth, "isFromScrollable", result);
      return result;
    }

    public float scrollPercent(int depth) {
      float percent = AccessibilityEventUtils.getScrollPercent(event, 50.0f);
      LogDepth.logVar(LOG_TAG, ++depth, "scrollPercent", percent);
      return percent;
    }

    public int keyAction(int depth) {
      int action =
          (interpretation instanceof Interpretation.Key)
              ? ((Interpretation.Key) interpretation).event.getAction()
              : KEY_ACTION_UNKNOWN;
      LogDepth.logVar(LOG_TAG, ++depth, "keyAction", action);
      return action;
    }

    public boolean forceFeedbackEvenIfAudioPlaybackActive(int depth) {
      boolean result =
          (interpretation instanceof Interpretation.HintableEvent)
              && ((Interpretation.HintableEvent) interpretation)
                  .forceFeedbackEvenIfAudioPlaybackActive;
      LogDepth.logVar(LOG_TAG, ++depth, "forceFeedbackEvenIfAudioPlaybackActive", result);
      return result;
    }

    public boolean forceFeedbackEvenIfMicrophoneActive(int depth) {
      boolean result =
          (interpretation instanceof Interpretation.HintableEvent)
              && ((Interpretation.HintableEvent) interpretation)
                  .forceFeedbackEvenIfMicrophoneActive;
      LogDepth.logVar(LOG_TAG, ++depth, "forceFeedbackEvenIfMicrophoneActive", result);
      return result;
    }

    @Override
    public String toString() {
      return "interpretation=" + interpretation + " event=" + event;
    }
  }
}

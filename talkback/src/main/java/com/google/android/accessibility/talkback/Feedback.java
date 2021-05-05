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

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_SHOW_ON_SCREEN;
import static com.google.android.accessibility.talkback.ScrollEventInterpreter.ACTION_AUTO_SCROLL;
import static com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TARGET_DEFAULT;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.toStringShort;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_UNKNOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UNKNOWN;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.graphics.Region;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback.AdjustVolume.StreamType;
import com.google.android.accessibility.talkback.Feedback.Scroll.Action;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.UserAction;
import com.google.android.accessibility.talkback.actor.AutoScrollActor.AutoScrollRecord.Source;
import com.google.android.accessibility.talkback.actor.TalkBackUIActor;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorCursorState;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.input.InputModeManager.InputMode;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Data-structure of feedback from pipeline-stage feedback-mapper to actors. Feedback is a sequence
 * of failing-over Parts. Each Part is a collection of specific feedback types, mostly empty.
 *
 * <pre>{@code
 * ArrayList<Feedback.Part> failoverSequence = new ArrayList();
 * failoverSequence.add(Feedback.Part.builder()
 *     .speech("hello", SpeakOptions.create()).sound(R.id.sound).interrupt(HINT, 2).build());
 * Feedback feedback = Feedback.create(eventId, failoverSequence);
 * }</pre>
 */
@AutoValue
public abstract class Feedback {

  // TODO: Add Actors that use all this feedback.

  //////////////////////////////////////////////////////////////////////////////////
  // Constants

  /** Default empty node-action id. */
  private static final int NODE_ACTION_UNKNOWN = 0;

  /** Interrupt groups */
  public @interface InterruptGroup {}

  public static final int DEFAULT = -1;
  public static final int HINT = 0;
  public static final int GESTURE_VIBRATION = 1;
  /** Use for speech of cursor state at {@link ProcessorCursorState} */
  public static final int CURSOR_STATE = 2;

  /** Interrupt levels. Level -1 does not interrupt at all. */
  public @interface InterruptLevel {}

  //////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  /** Caller is responsible to recycle the feedback parts. */
  public static Feedback create(@Nullable EventId eventId, List<Part> sequence) {
    return new AutoValue_Feedback(eventId, ImmutableList.copyOf(sequence));
  }

  /** Caller is responsible to recycle the feedback parts. */
  public static Feedback create(@Nullable EventId eventId, Part part) {
    return new AutoValue_Feedback(eventId, ImmutableList.of(part));
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Convenience methods for Feedback

  public static Part.Builder part() {
    return Part.builder();
  }

  public static Part.Builder interrupt(@InterruptGroup int group, @InterruptLevel int level) {
    return Part.builder().setInterruptGroup(group).setInterruptLevel(level);
  }

  /** Copies node at {@link Label.Builder}, caller retains ownership. */
  public static Part.Builder label(@Nullable String text, AccessibilityNodeInfoCompat node) {
    return Part.builder()
        .setLabel(Label.builder().setAction(Label.Action.SET).setText(text).setNode(node).build());
  }

  public static Part.Builder dimScreen(DimScreen.Action action) {
    return Part.builder().setDimScreen(DimScreen.create(action));
  }

  public static Part.Builder speech(Speech.Action action) {
    return Part.builder().setSpeech(Speech.create(action));
  }

  public static Part.Builder speech(CharSequence text) {
    return Part.builder().speech(text, /* options= */ null);
  }

  public static Part.Builder speech(CharSequence text, SpeakOptions options) {
    return Part.builder().speech(text, options);
  }

  public static Part.Builder voiceRecognition(VoiceRecognition.Action action) {
    return Part.builder()
        .setVoiceRecognition(VoiceRecognition.create(action, /* checkDialog= */ false));
  }

  public static Part.Builder voiceRecognition(VoiceRecognition.Action action, boolean checkDialog) {
    return Part.builder().setVoiceRecognition(VoiceRecognition.create(action, checkDialog));
  }

  public static Part.Builder continuousRead(ContinuousRead.Action action) {
    return Part.builder().setContinuousRead(ContinuousRead.create(action));
  }

  public static Part.Builder sound(int resourceId) {
    return Part.builder().sound(resourceId);
  }

  public static Part.Builder sound(int resourceId, float rate, float volume) {
    return Part.builder().sound(resourceId, rate, volume);
  }

  public static Part.Builder vibration(int resourceId) {
    return Part.builder().vibration(resourceId);
  }

  public static Part.Builder triggerIntent(TriggerIntent.Action action) {
    return Part.builder().setTriggerIntent(TriggerIntent.create(action));
  }

  public static Part.Builder language(Language.Action action) {
    return Part.builder().setLanguage(Language.create(action));
  }

  public static Part.Builder setLanguage(@Nullable Locale currentLanguage) {
    return Part.builder()
        .setLanguage(Language.create(Language.Action.SET_LANGUAGE, currentLanguage));
  }

  /** Copies node at {@link EditText.Builder}, caller retains ownership. */
  public static EditText.Builder edit(AccessibilityNodeInfoCompat node, EditText.Action action) {
    // TODO: Push all obtain() calls down to data-structure constructors/setters.
    return EditText.builder().setNode(node).setAction(action);
  }

  public static Part.Builder systemAction(int systemActionId) {
    return Part.builder().setSystemAction(SystemAction.create(systemActionId));
  }

  public static Part.Builder passThroughMode(PassThroughMode.Action action) {
    return Part.builder().setPassThroughMode(PassThroughMode.create(action));
  }

  public static Part.Builder passThroughMode(PassThroughMode.Action action, Region region) {
    return Part.builder().setPassThroughMode(PassThroughMode.create(action, region));
  }

  public static Part.Builder speechRate(SpeechRate.Action action) {
    return Part.builder().setSpeechRate(SpeechRate.create(action));
  }

  public static Part.Builder adjustValue(AdjustValue.Action action) {
    return Part.builder().setAdjustValue(AdjustValue.create(action));
  }

  public static Part.Builder adjustVolume(AdjustVolume.Action action, StreamType streamType) {
    return Part.builder().setAdjustVolume(AdjustVolume.create(action, streamType));
  }

  /** Copies target at {@link NodeAction.Builder}, caller retains ownership. */
  public static Part.Builder nodeAction(AccessibilityNode target, int actionId) {
    Part.Builder partBuilder = Part.builder();
    if (target == null) {
      return partBuilder;
    }
    return partBuilder.setNodeAction(
        NodeAction.builder().setTarget(target).setActionId(actionId).build());
  }

  /**
   * Copies target at {@link #nodeAction(AccessibilityNodeInfoCompat, int, Bundle)}, caller retains
   * ownership.
   */
  public static Part.Builder nodeAction(AccessibilityNodeInfoCompat target, int actionId) {
    return nodeAction(target, actionId, /* args= */ null);
  }

  /** Copies target at {@link NodeAction.Builder}, caller retains ownership. */
  public static Part.Builder nodeAction(
      AccessibilityNodeInfoCompat target, int actionId, @Nullable Bundle args) {
    Part.Builder partBuilder = Part.builder();
    if (target == null) {
      return partBuilder;
    }
    AccessibilityNode accessibilityNode = AccessibilityNode.obtainCopy(target);
    try {
      return partBuilder.setNodeAction(
          NodeAction.builder()
              .setTarget(accessibilityNode)
              .setActionId(actionId)
              .setArgs(args)
              .build());
    } finally {
      AccessibilityNode.recycle(/* caller= */ "Feedback.nodeAction()", accessibilityNode);
    }
  }

  /** Copies target at {@link WebAction.Builder}, caller retains ownership. */
  public static Part.Builder navigateWebByAction(
      AccessibilityNodeInfoCompat target,
      int action,
      String htmlElement,
      boolean updateFocusHistory) {
    Bundle args = new Bundle();
    args.putString(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_HTML_ELEMENT_STRING, htmlElement);
    return webAction(target, action, args, updateFocusHistory);
  }

  /**
   * Copies target at {@link WebAction.Builder}, caller retains ownership.
   *
   * <p>Navigates Exit Special Web Content when enabled is false. Or Enter Special Web Content when
   * enabled is true. This has same function with {@code
   * WebInterfaceUtils.setSpecialContentModeEnabled}
   */
  public static Part.Builder navigateSpecialWeb(
      AccessibilityNodeInfoCompat target, boolean enabled, boolean updateFocusHistory) {
    int action =
        (enabled)
            ? AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
            : AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY;
    Bundle args = new Bundle();

    args.putInt(
        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
        WebInterfaceUtils.ACTION_TOGGLE_SPECIAL_CONTENT);
    return webAction(target, action, args, updateFocusHistory);
  }

  /** Copies target at {@link WebAction.Builder}, caller retains ownership. */
  public static Part.Builder webAction(
      AccessibilityNodeInfoCompat node,
      int action,
      @Nullable Bundle args,
      boolean updateFocusHistory) {
    Part.Builder partBuilder = Part.builder();
    if (node == null) {
      return partBuilder;
    }
    return partBuilder.setWebAction(
        WebAction.builder()
            .setAction(WebAction.Action.PERFORM_ACTION)
            .setTarget(node)
            .setNodeAction(action)
            .setNodeActionArgs(args)
            .setUpdateFocusHistory(updateFocusHistory)
            .build());
  }

  /** Copies start at {@link WebAction.Builder}, caller retains ownership. */
  public static Part.Builder webDirectionHtml(
      AccessibilityNodeInfoCompat target, NavigationAction action) {

    return Part.builder()
        .setWebAction(
            WebAction.builder()
                .setAction(WebAction.Action.HTML_DIRECTION)
                .setTarget(target)
                .setNavigationAction(action)
                .build());
  }

  /** Copies node at {@link Scroll.Builder}, caller retains ownership. */
  public static Part.Builder scroll(
      AccessibilityNode node, @UserAction int userAction, int nodeAction, @Nullable Source source) {
    return Part.builder()
        .setScroll(
            Scroll.builder()
                .setAction(Scroll.Action.SCROLL)
                .setNode(node)
                .setUserAction(userAction)
                .setNodeAction(nodeAction)
                .setSource(source)
                .build());
  }

  /** Copies nodeCompat at {@link Scroll.Builder}, caller retains ownership. */
  public static Part.Builder scroll(
      AccessibilityNodeInfoCompat nodeCompat,
      @UserAction int userAction,
      int nodeAction,
      @Nullable Source source) {
    return Part.builder()
        .setScroll(
            Scroll.builder()
                .setAction(Scroll.Action.SCROLL)
                .setNodeCompat(nodeCompat)
                .setUserAction(userAction)
                .setNodeAction(nodeAction)
                .setSource(source)
                .build());
  }

  public static Part.Builder scrollCancelTimeout() {
    return Part.builder()
        .setScroll(
            Scroll.builder()
                .setAction(Scroll.Action.CANCEL_TIMEOUT)
                .setUserAction(ScrollEventInterpreter.ACTION_UNKNOWN)
                .setNodeAction(NODE_ACTION_UNKNOWN)
                .build());
  }

  public static Part.Builder scrollEnsureOnScreen(
      AccessibilityNodeInfoCompat scrollableNode, AccessibilityNodeInfoCompat targetNode) {
    return Part.builder()
        .setScroll(
            Scroll.builder()
                .setAction(Action.ENSURE_ON_SCREEN)
                .setNodeCompat(scrollableNode)
                .setNodeToMoveOnScreen(targetNode)
                .setUserAction(ACTION_AUTO_SCROLL)
                .setNodeAction(ACTION_SHOW_ON_SCREEN.getId())
                .setSource(Source.FOCUS)
                .build());
  }

  /** Copies target at {@link Focus.Builder}, caller retains ownership. */
  public static Focus.Builder focus(
      AccessibilityNodeInfoCompat target, FocusActionInfo focusActionInfo) {
    return Focus.builder()
        .setAction(Focus.Action.FOCUS)
        .setFocusActionInfo(focusActionInfo)
        .setTarget(target);
  }

  public static Focus.Builder focus(Focus.Action action) {
    return Focus.builder().setAction(action);
  }

  public static Focus.Builder searchFromTop(CharSequence keyword) {
    return Focus.builder().setAction(Focus.Action.SEARCH_FROM_TOP).setSearchKeyword(keyword);
  }

  public static Focus.Builder repeatSearch() {
    return Focus.builder().setAction(Focus.Action.SEARCH_AGAIN);
  }

  public static Part.Builder focusDirection(FocusDirection.Action action) {
    return Part.builder().setFocusDirection(FocusDirection.builder().setAction(action).build());
  }

  public static FocusDirection.Builder focusDirection(@SearchDirection int direction) {
    return FocusDirection.builder()
        .setAction(FocusDirection.Action.NAVIGATE)
        .setDirection(direction);
  }

  /** Copies node at {@link FocusDirection.Builder}, caller retains ownership. */
  public static FocusDirection.Builder directionNavigationFollowTo(
      @Nullable AccessibilityNodeInfoCompat node, @SearchDirection int direction) {
    return FocusDirection.builder()
        .setAction(FocusDirection.Action.FOLLOW)
        .setTargetNode(node)
        .setDirection(direction);
  }

  public static FocusDirection.Builder nextHeading(@InputMode int inputMode) {
    return nextGranularity(inputMode, CursorGranularity.HEADING);
  }

  public static FocusDirection.Builder nextGranularity(
      @InputMode int inputMode, CursorGranularity granularity) {
    return FocusDirection.builder()
        .setAction(FocusDirection.Action.NEXT)
        .setGranularity(granularity)
        .setInputMode(inputMode);
  }

  public static FocusDirection.Builder nextWindow(@InputMode int inputMode) {
    return FocusDirection.builder()
        .setAction(FocusDirection.Action.NAVIGATE)
        .setDirection(SEARCH_FOCUS_FORWARD)
        .setToWindow(true)
        .setInputMode(inputMode);
  }

  public static FocusDirection.Builder previousWindow(@InputMode int inputMode) {
    return FocusDirection.builder()
        .setAction(FocusDirection.Action.NAVIGATE)
        .setDirection(SEARCH_FOCUS_BACKWARD)
        .setToWindow(true)
        .setInputMode(inputMode);
  }

  public static Part.Builder focusTop(@InputMode int inputMode) {
    return Part.builder()
        .setFocusDirection(
            FocusDirection.builder()
                .setAction(FocusDirection.Action.TOP)
                .setInputMode(inputMode)
                .build());
  }

  public static Part.Builder focusBottom(@InputMode int inputMode) {
    return Part.builder()
        .setFocusDirection(
            FocusDirection.builder()
                .setAction(FocusDirection.Action.BOTTOM)
                .setInputMode(inputMode)
                .build());
  }

  public static FocusDirection.Builder granularity(CursorGranularity granularity) {
    return FocusDirection.builder()
        .setAction(FocusDirection.Action.SET_GRANULARITY)
        .setGranularity(granularity);
  }

  /** Copies node {@link FocusDirection.Builder}, caller retains ownership. */
  public static Part.Builder selectionModeOn(AccessibilityNodeInfoCompat node) {
    return Part.builder()
        .setFocusDirection(
            FocusDirection.builder()
                .setAction(FocusDirection.Action.SELECTION_MODE_ON)
                .setTargetNode(node)
                .build());
  }

  public static Part.Builder selectionModeOff() {
    return Part.builder()
        .setFocusDirection(
            FocusDirection.builder().setAction(FocusDirection.Action.SELECTION_MODE_OFF).build());
  }

  public static Part.Builder talkBackUI(TalkBackUI.Action action, TalkBackUIActor.Type type) {
    return Part.builder().setTalkBackUI(TalkBackUI.create(action, type));
  }

  public static Part.Builder showSelectorUI(
      TalkBackUIActor.Type type, CharSequence message, boolean showIcon) {
    return Part.builder()
        .setTalkBackUI(
            TalkBackUI.create(TalkBackUI.Action.SHOW_SELECTOR_UI, type, message, showIcon));
  }

  public static Part.Builder saveGesture(AccessibilityGestureEvent gestureEvent) {
    return Part.builder().setGesture(Gesture.create(Gesture.Action.SAVE, gestureEvent));
  }

  public static Part.Builder reportGesture() {
    return Part.builder().setGesture(Gesture.create(Gesture.Action.REPORT));
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Data access methods

  public abstract @Nullable EventId eventId();

  /** Failover sequence of feedback. */
  public abstract ImmutableList<Part> failovers();

  /////////////////////////////////////////////////////////////////////////////////////
  // Inner class for feedback-sequence part

  /** Data-structure that holds a variety of feedback types, executed at one time. */
  @AutoValue
  public abstract static class Part {

    public abstract int delayMs();

    public abstract @InterruptGroup int interruptGroup();

    // In the future, may also need to separately set interruptable-level.
    public abstract @InterruptLevel int interruptLevel();

    public abstract @Nullable String senderName();

    public abstract boolean interruptSoundAndVibration();

    public abstract boolean interruptAllFeedback();

    public abstract boolean interruptGentle();

    public abstract boolean stopTts();

    public abstract @Nullable Label label();

    public abstract @Nullable DimScreen dimScreen();

    public abstract @Nullable Speech speech();

    public abstract @Nullable VoiceRecognition voiceRecognition();

    public abstract @Nullable ContinuousRead continuousRead();

    // Some redundancy, since Speech.speechOptions can also contain sound and vibration.
    public abstract @Nullable Sound sound();

    public abstract @Nullable Vibration vibration();

    public abstract @Nullable TriggerIntent triggerIntent();

    public abstract @Nullable Language language();

    public abstract @Nullable EditText edit();

    public abstract @Nullable SystemAction systemAction();

    public abstract @Nullable NodeAction nodeAction();

    public abstract @Nullable WebAction webAction();

    public abstract @Nullable Scroll scroll();

    public abstract @Nullable Focus focus();

    public abstract @Nullable FocusDirection focusDirection();

    public abstract @Nullable PassThroughMode passThroughMode();

    public abstract @Nullable SpeechRate speechRate();

    public abstract @Nullable AdjustValue adjustValue();

    public abstract @Nullable AdjustVolume adjustVolume();

    public abstract @Nullable TalkBackUI talkBackUI();

    public abstract @Nullable Gesture gesture();

    public static Builder builder() {
      return new AutoValue_Feedback_Part.Builder()
          // Set default values that are not null.
          .setDelayMs(0)
          .setInterruptGroup(DEFAULT)
          .setInterruptLevel(-1)
          .setInterruptSoundAndVibration(false)
          .setInterruptAllFeedback(false)
          .setInterruptGentle(false)
          .setStopTts(false);
    }

    /** Builder for Feedback.Part. Caller must recycle the feedback part. */
    @AutoValue.Builder
    public abstract static class Builder {

      ////////////////////////////////////////////////////////////////////////////////////
      // Convenience methods for Part

      public Builder speech(CharSequence text, @Nullable SpeakOptions options) {
        return setSpeech(Speech.create(text, options));
      }

      public Builder sound(int resourceId) {
        return setSound(Sound.create(resourceId));
      }

      public Builder sound(int resourceId, float rate, float volume) {
        return setSound(Sound.create(resourceId, rate, volume));
      }

      public Builder vibration(int resourceId) {
        return setVibration(Vibration.create(resourceId));
      }

      ////////////////////////////////////////////////////////////////////////////////
      // Individual member setters

      public abstract Builder setDelayMs(int delayMs);

      // Requires interruptLevel. Suggest to require senderName, too.
      public abstract Builder setInterruptGroup(@InterruptGroup int interruptGroup);

      public abstract Builder setInterruptLevel(@InterruptLevel int interruptLevel);

      public abstract Builder setSenderName(@NonNull String senderName);

      public abstract Builder setInterruptSoundAndVibration(boolean interruptSoundAndVibration);

      public abstract Builder setInterruptAllFeedback(boolean interruptAllFeedback);

      public abstract Builder setInterruptGentle(boolean interruptGentle);

      // Requires interruptAllFeedback.
      public abstract Builder setStopTts(boolean stopTts);

      public abstract Builder setLabel(Label label);

      public abstract Builder setDimScreen(DimScreen dimScreen);

      public abstract Builder setSpeech(Speech speech);

      public abstract Builder setVoiceRecognition(VoiceRecognition voiceRecognition);

      public abstract Builder setContinuousRead(ContinuousRead continuousRead);

      public abstract Builder setSound(Sound sound);

      public abstract Builder setVibration(Vibration vibration);

      public abstract Builder setTriggerIntent(TriggerIntent triggerIntent);

      public abstract Builder setLanguage(Language language);

      /** Takes ownership of edit, and recycles it. */
      public abstract Builder setEdit(EditText edit);

      public abstract Builder setSystemAction(SystemAction systemAction);

      /** Takes ownership of nodeAction, and recycles it. */
      public abstract Builder setNodeAction(NodeAction nodeAction);

      /** Takes ownership of nodeAction, and recycles it. */
      public abstract Builder setWebAction(WebAction webAction);

      public abstract Builder setScroll(Scroll scroll);

      /** Takes ownership of focus, and recycles it. */
      public abstract Builder setFocus(Focus focus);

      public abstract Builder setFocusDirection(FocusDirection focusDirection);

      public abstract Builder setPassThroughMode(PassThroughMode passThroughMode);

      public abstract Builder setSpeechRate(SpeechRate speechRate);

      public abstract Builder setAdjustValue(AdjustValue adjustValue);

      public abstract Builder setAdjustVolume(AdjustVolume adjustVolume);

      public abstract Builder setTalkBackUI(TalkBackUI talkBackUI);

      public abstract Builder setGesture(Gesture gesture);

      public abstract Part build();
    }

    public void recycle() {

      Label label = label();
      if (label != null) {
        label.recycle();
      }

      EditText edit = edit();
      if (edit != null) {
        edit.recycle();
      }

      NodeAction nodeAction = nodeAction();
      if (nodeAction != null) {
        nodeAction.recycle();
      }

      WebAction webAction = webAction();
      if (webAction != null) {
        webAction.recycle();
      }

      Scroll scroll = scroll();
      if (scroll != null) {
        scroll.recycle();
      }

      Focus focus = focus();
      if (focus != null) {
        focus.recycle();
      }

      FocusDirection direction = focusDirection();
      if (direction != null) {
        direction.recycle();
      }
    }

    @Override
    public final String toString() {
      return "Part= "
          + StringBuilderUtils.joinFields(
              StringBuilderUtils.optionalInt("delayMs", delayMs(), 0),
              StringBuilderUtils.optionalInt("interruptGroup", interruptGroup(), DEFAULT),
              StringBuilderUtils.optionalInt("interruptLevel", interruptLevel(), -1),
              StringBuilderUtils.optionalText("senderName", senderName()),
              StringBuilderUtils.optionalTag(
                  "interruptSoundAndVibration", interruptSoundAndVibration()),
              StringBuilderUtils.optionalTag("interruptAllFeedback", interruptAllFeedback()),
              StringBuilderUtils.optionalTag("stopTts", stopTts()),
              StringBuilderUtils.optionalSubObj("label", label()),
              StringBuilderUtils.optionalSubObj("dimScreen", dimScreen()),
              StringBuilderUtils.optionalSubObj("speech", speech()),
              StringBuilderUtils.optionalSubObj("voiceRecognition", voiceRecognition()),
              StringBuilderUtils.optionalSubObj("continuousRead", continuousRead()),
              StringBuilderUtils.optionalSubObj("sound", sound()),
              StringBuilderUtils.optionalSubObj("vibration", vibration()),
              StringBuilderUtils.optionalSubObj("triggerIntent", triggerIntent()),
              StringBuilderUtils.optionalSubObj("language", language()),
              StringBuilderUtils.optionalSubObj("edit", edit()),
              StringBuilderUtils.optionalSubObj("systemAction", systemAction()),
              StringBuilderUtils.optionalSubObj("nodeAction", nodeAction()),
              StringBuilderUtils.optionalSubObj("webAction", webAction()),
              StringBuilderUtils.optionalSubObj("scroll", scroll()),
              StringBuilderUtils.optionalSubObj("focus", focus()),
              StringBuilderUtils.optionalSubObj("focusDirection", focusDirection()),
              StringBuilderUtils.optionalSubObj("passThroughMode", passThroughMode()),
              StringBuilderUtils.optionalSubObj("talkBackUI", talkBackUI()),
              StringBuilderUtils.optionalSubObj("gesture", gesture()),
              StringBuilderUtils.optionalSubObj("speechRate", speechRate()),
              StringBuilderUtils.optionalSubObj("adjustValue", adjustValue()),
              StringBuilderUtils.optionalSubObj("adjustVolume", adjustVolume()));
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////
  // Inner classes for various feedback types

  /** Inner data-structure for custom-labeling actions. */
  @AutoValue
  public abstract static class Label {

    /** Types of exclusive labeling actions. */
    public enum Action {
      SET,
    }

    public abstract Label.Action action();

    public abstract @Nullable String text();

    public abstract AccessibilityNodeInfoCompat node();

    public static Builder builder() {
      return new AutoValue_Feedback_Label.Builder();
    }

    /** Builder for Label feedback data */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setAction(Label.Action action);

      public abstract Builder setText(@Nullable String text);

      public abstract Builder setNode(AccessibilityNodeInfoCompat node);

      abstract AccessibilityNodeInfoCompat node();

      abstract Label autoBuild();

      public Label build() {
        setNode(AccessibilityNodeInfoUtils.obtain(node()));
        return autoBuild();
      }
    }

    public void recycle() {
      AccessibilityNodeInfoUtils.recycleNodes(node());
    }
  }

  /** Inner data-structure for screen-dimming. */
  @AutoValue
  public abstract static class DimScreen {

    /** Types of exclusive dim-screen actions. */
    public enum Action {
      DIM,
      BRIGHTEN
    }

    public static DimScreen create(DimScreen.Action action) {
      return new AutoValue_Feedback_DimScreen(action);
    }

    public abstract DimScreen.Action action();
  }

  /** Inner data-structure for speech feedback. */
  @AutoValue
  public abstract static class Speech {

    /** Types of exclusive speech actions. */
    public enum Action {
      SPEAK,
      SAVE_LAST,
      COPY_SAVED,
      REPEAT_SAVED,
      SPELL_SAVED,
      PAUSE_OR_RESUME,
      TOGGLE_VOICE_FEEDBACK,
      /**
       * The SILENCE and UNSILENCE actions should be used with caution, the caller should maintein
       * the lifecycle of the silence state, it currently only used by voice command.
       */
      SILENCE,
      UNSILENCE,
    }

    public static Speech create(CharSequence text, @Nullable SpeakOptions options) {
      return Speech.builder()
          .setAction(Speech.Action.SPEAK)
          .setText(text)
          .setOptions(options)
          .build();
    }

    public static Speech create(Speech.Action action) {
      return Speech.builder().setAction(action).build();
    }

    public abstract Speech.Action action();

    public abstract @Nullable CharSequence text();

    public abstract @Nullable SpeakOptions options();

    public abstract @Nullable CharSequence hint();

    public abstract @Nullable SpeakOptions hintSpeakOptions();

    public static Builder builder() {
      return new AutoValue_Feedback_Speech.Builder();
    }

    /** Builder for Speech feedback data */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setAction(Speech.Action action);

      public abstract Builder setText(@Nullable CharSequence text);

      public abstract Builder setOptions(@Nullable SpeakOptions options);

      public abstract Builder setHint(@Nullable CharSequence hint);

      public abstract Builder setHintSpeakOptions(@Nullable SpeakOptions hintSpeakOptions);

      public abstract Speech build();
    }
  }

  /** Inner data-structure for performing an action of voice Recognition. */
  @AutoValue
  public abstract static class VoiceRecognition {

    /** Types of exclusive voice Recognition actions, mostly without additional feedback data. */
    public enum Action {
      START_LISTENING,
      STOP_LISTENING,
      SHOW_COMMAND_LIST;
    }

    public abstract Feedback.VoiceRecognition.Action action();

    public abstract boolean checkDialog();

    public static VoiceRecognition create(VoiceRecognition.Action action, boolean checkDialog) {
      return new AutoValue_Feedback_VoiceRecognition(action, checkDialog);
    }
  }

  /** Inner data-structure for continuous-reading feedback. */
  public abstract static class ContinuousRead {
    /** Types of exclusive continuous-reading actions. */
    public enum Action {
      START_AT_TOP,
      START_AT_NEXT,
      READ_FOCUSED_CONTENT,
      INTERRUPT,
    }

    public static ContinuousRead create(ContinuousRead.Action action) {
      return new AutoValue_Feedback_ContinuousRead(action);
    }

    public abstract ContinuousRead.Action action();
  }

  /** Inner data-structure for sound-effect feedback. */
  @AutoValue
  public abstract static class Sound {

    public static Sound create(int resourceId) {
      float rate = 1.0f;
      float volume = 1.0f;
      return create(resourceId, rate, volume);
    }

    public static Sound create(int resourceId, float rate, float volume) {
      return new AutoValue_Feedback_Sound(resourceId, rate, volume);
    }

    public abstract int resourceId();

    public abstract float rate();

    public abstract float volume();
  }

  /** Inner data-structure for vibration feedback. */
  @AutoValue
  public abstract static class Vibration {

    public static Vibration create(int resourceId) {
      return new AutoValue_Feedback_Vibration(resourceId);
    }

    public abstract int resourceId();
  }

  /** Inner data-structure for triggering intent. */
  @AutoValue
  public abstract static class TriggerIntent {
    /** Types of intent action. */
    public enum Action {
      TRIGGER_TUTORIAL,
      TRIGGER_PRACTICE_GESTURE,
      TRIGGER_ASSISTANT,
    }

    public static TriggerIntent create(TriggerIntent.Action action) {
      return new AutoValue_Feedback_TriggerIntent(action);
    }

    public abstract TriggerIntent.Action action();
  }

  /** Inner data-structure for language. */
  @AutoValue
  public abstract static class Language {

    /** Types of exclusive language actions. */
    public enum Action {
      PREVIOUS_LANGUAGE,
      NEXT_LANGUAGE,
      SET_LANGUAGE
    }

    public abstract Action action();

    public abstract @Nullable Locale currentLanguage();

    public static Language create(Language.Action action) {
      return new AutoValue_Feedback_Language(action, null);
    }

    public static Language create(Language.Action action, Locale currentLanguage) {
      return new AutoValue_Feedback_Language(action, currentLanguage);
    }
  }

  /** Inner data-structure for editing in an EditText node. */
  @AutoValue
  public abstract static class EditText {

    /** Types of exclusive edit actions. */
    public enum Action {
      SELECT_ALL,
      START_SELECT,
      END_SELECT,
      COPY,
      CUT,
      PASTE,
      DELETE,
      CURSOR_TO_BEGINNING, // Works with stopSelecting.
      CURSOR_TO_END, // Works with stopSelecting.
      INSERT; // Requires text.
    }

    public abstract AccessibilityNodeInfoCompat node();

    public abstract Action action();

    public abstract boolean stopSelecting();

    public abstract @Nullable CharSequence text();

    public static Builder builder() {
      return new AutoValue_Feedback_EditText.Builder()
          // Set default values that are not null.
          .setStopSelecting(false);
    }

    /** Builder for EditText feedback data */
    @AutoValue.Builder
    public abstract static class Builder {
      /** Copies node at{@link EditText.Builder}, caller retains ownership. */
      public abstract Builder setNode(AccessibilityNodeInfoCompat node);

      public abstract Builder setAction(Action action);

      public abstract Builder setStopSelecting(boolean stopSelecting);

      public abstract Builder setText(@Nullable CharSequence text);

      abstract AccessibilityNodeInfoCompat node();

      abstract EditText autoBuild();

      public EditText build() {
        setNode(AccessibilityNodeInfoUtils.obtain(node()));
        return autoBuild();
      }
    }

    public void recycle() {
      AccessibilityNodeInfoUtils.recycleNodes(node());
    }
  }
  /** Inner data-structure for performing a global action. */
  @AutoValue
  public abstract static class SystemAction {

    public static SystemAction create(int systemActionId) {
      return new AutoValue_Feedback_SystemAction(systemActionId);
    }

    public abstract int systemActionId();
  }

  /** Inner data-structure for performing an action on a node. */
  @AutoValue
  public abstract static class NodeAction {
    /** Owned node, NodeAction must recycle. */
    public abstract AccessibilityNode target();

    public abstract int actionId();

    public abstract @Nullable Bundle args();

    public static Builder builder() {
      return new AutoValue_Feedback_NodeAction.Builder();
    }

    /** Builder for NodeAction feedback data */
    @AutoValue.Builder
    public abstract static class Builder {

      /** Copies node at{@link NodeAction.Builder}, caller retains ownership. */
      public abstract Builder setTarget(AccessibilityNode target);

      public abstract Builder setActionId(int actionId);

      public abstract Builder setArgs(@Nullable Bundle args);

      abstract AccessibilityNode target();

      abstract NodeAction autoBuild();

      public NodeAction build() {
        AccessibilityNode accessibilityNode = target();
        if (accessibilityNode != null) {
          setTarget(accessibilityNode.obtainCopy());
        }
        return autoBuild();
      }
    }

    public void recycle() {
      AccessibilityNode.recycle("Feedback.NodeAction.recycle()", target());
    }

    @Override
    public final String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalSubObj("target", target()),
          StringBuilderUtils.optionalField(
              "actionId", AccessibilityNodeInfoUtils.actionToString(actionId())),
          StringBuilderUtils.optionalSubObj("args", args()));
    }
  }

  /** Inner data-structure for performing an action of web. */
  @AutoValue
  public abstract static class WebAction {
    /** Types of exclusive web actions, mostly without additional feedback data. */
    public enum Action {
      PERFORM_ACTION,
      HTML_DIRECTION; // Requires start, direction, htmlElementType, focusActionInfo.
    }

    public abstract WebAction.Action action();

    /** Owned node, AccessibilityNodeInfoCompat must recycle. */
    public abstract AccessibilityNodeInfoCompat target();

    public abstract int nodeAction();

    public abstract @Nullable Bundle nodeActionArgs();

    public abstract boolean updateFocusHistory();

    public abstract @Nullable NavigationAction navigationAction();

    public static Builder builder() {
      return new AutoValue_Feedback_WebAction.Builder()
          .setUpdateFocusHistory(false)
          .setNodeAction(AccessibilityNodeInfoCompat.ACTION_NEXT_HTML_ELEMENT);
    }

    /** Builder for WebAction feedback data */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setAction(WebAction.Action action);

      /** Copies node at{@link WebAction.Builder}, caller retains ownership. */
      public abstract Builder setTarget(AccessibilityNodeInfoCompat target);

      public abstract Builder setNodeAction(int nodeAction);

      public abstract Builder setNodeActionArgs(@Nullable Bundle nodeActionArgs);

      public abstract Builder setUpdateFocusHistory(boolean updateFocusHistory);

      public abstract Builder setNavigationAction(NavigationAction navigationAction);

      abstract AccessibilityNodeInfoCompat target();

      abstract WebAction autoBuild();

      public WebAction build() {
        /** Owned node, WebAction must recycle. */
        AccessibilityNodeInfoCompat node = target();
        if (node != null) {
          setTarget(AccessibilityNodeInfoCompat.obtain(node));
        }
        return autoBuild();
      }
    }

    public void recycle() {
      AccessibilityNodeInfoUtils.recycleNodes(target());
    }

    @Override
    public final String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalField("action", action()),
          StringBuilderUtils.optionalField("performNodeAction", nodeAction()),
          StringBuilderUtils.optionalSubObj("nodeActionArgs", nodeActionArgs()),
          StringBuilderUtils.optionalTag("updateFocusHistory", updateFocusHistory()),
          StringBuilderUtils.optionalSubObj("navigationAction", navigationAction()),
          StringBuilderUtils.optionalSubObj("target", toStringShort(target())));
    }
  }

  /** Inner data-structure for scrolling feedback. */
  public abstract static class Scroll {

    /** Types of exclusive scroll actions. */
    public enum Action {
      SCROLL,
      CANCEL_TIMEOUT,
      ENSURE_ON_SCREEN
    }

    public abstract Scroll.Action action();

    public abstract @Nullable AccessibilityNode node();

    public abstract @Nullable AccessibilityNodeInfoCompat nodeCompat();

    public abstract @Nullable AccessibilityNodeInfoCompat nodeToMoveOnScreen();

    public abstract @UserAction int userAction();

    public abstract int nodeAction();

    public abstract @Nullable Source source();

    public static Scroll.Builder builder() {
      return new AutoValue_Feedback_Scroll.Builder();
    }

    /** Builder for Scroll feedback data */
    public abstract static class Builder {
      public abstract Scroll.Builder setAction(Scroll.Action action);

      public abstract Scroll.Builder setNode(@Nullable AccessibilityNode node);

      /** Copies node at{@link Scroll.Builder}, caller retains ownership. */
      public abstract Scroll.Builder setNodeCompat(
          @Nullable AccessibilityNodeInfoCompat nodeCompat);

      /** Copies node at{@link Scroll.Builder}, caller retains ownership. */
      public abstract Scroll.Builder setNodeToMoveOnScreen(
          @Nullable AccessibilityNodeInfoCompat nodeToMoveOnScreen);

      public abstract Scroll.Builder setUserAction(@UserAction int userAction);

      public abstract Scroll.Builder setNodeAction(int nodeAction);

      public abstract Scroll.Builder setSource(@Nullable Source source);

      abstract @Nullable AccessibilityNode node();

      abstract @Nullable AccessibilityNodeInfoCompat nodeCompat();

      abstract @Nullable AccessibilityNodeInfoCompat nodeToMoveOnScreen();

      abstract Scroll autoBuild();

      public Scroll build() {
        AccessibilityNode accessibilityNode = node();
        if (accessibilityNode != null) {
          setNode(accessibilityNode.obtainCopy());
        }
        setNodeCompat(AccessibilityNodeInfoUtils.obtain(nodeCompat()));
        AccessibilityNodeInfoCompat nodeToMoveOnScreen = nodeToMoveOnScreen();
        if (nodeToMoveOnScreen != null) {
          setNodeToMoveOnScreen(AccessibilityNodeInfoUtils.obtain(nodeToMoveOnScreen));
        }
        return autoBuild();
      }
    }

    public void recycle() {
      AccessibilityNode.recycle("Feedback.Scroll.recycle()", node());
      AccessibilityNodeInfoUtils.recycleNodes(nodeCompat(), nodeToMoveOnScreen());
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////
  // Focus feedback

  /** Inner data-structure for focus feedback. */
  @AutoValue
  public abstract static class Focus {

    /** Types of exclusive focus actions, mostly without additional feedback data. */
    public enum Action {
      FOCUS, // Requires focusActionInfo and target.
      CLEAR,
      CACHE,
      MUTE_NEXT_FOCUS,
      RESTORE_ON_NEXT_WINDOW,
      RESTORE,
      CLEAR_CACHED,
      INITIAL_FOCUS_RESTORE,
      INITIAL_FOCUS_FOLLOW_INPUT,
      INITIAL_FOCUS_FIRST_CONTENT,
      FOCUS_FOR_TOUCH,
      CLICK_NODE,
      LONG_CLICK_NODE,
      CLICK_CURRENT,
      LONG_CLICK_CURRENT,
      CLICK_ANCESTOR,
      SEARCH_FROM_TOP, // Requires searchKeyword.
      SEARCH_AGAIN;
    }

    public abstract @Nullable AccessibilityNodeInfoCompat start();

    public abstract @Nullable AccessibilityNodeInfoCompat target();

    public abstract @SearchDirection int direction();

    public abstract @Nullable FocusActionInfo focusActionInfo();

    public abstract @Nullable NavigationAction navigationAction();

    public abstract @Nullable CharSequence searchKeyword();

    public abstract boolean forceRefocus();

    public abstract Focus.Action action();

    public abstract @Nullable AccessibilityNodeInfoCompat scrolledNode();

    public abstract @Nullable ScreenState screenState();

    public boolean hasDirection() {
      return (direction() != SEARCH_FOCUS_UNKNOWN);
    }

    public static Builder builder() {
      return new AutoValue_Feedback_Focus.Builder()
          // Set default values that are not null.
          .setDirection(SEARCH_FOCUS_UNKNOWN)
          .setForceRefocus(false);
    }

    /** Builder for Focus feedback data */
    @AutoValue.Builder
    public abstract static class Builder {
      /** Copies node at{@link Focus.Builder}, caller retains ownership. */
      public abstract Builder setStart(@Nullable AccessibilityNodeInfoCompat start);

      /** Copies node at{@link Focus.Builder}, caller retains ownership. */
      public abstract Builder setTarget(@Nullable AccessibilityNodeInfoCompat target);

      public abstract Builder setDirection(@SearchDirection int direction);

      public abstract Builder setFocusActionInfo(@Nullable FocusActionInfo focusActionInfo);

      public abstract Builder setNavigationAction(@Nullable NavigationAction navigationAction);

      public abstract Builder setSearchKeyword(@Nullable CharSequence searchKeyword);

      public abstract Builder setForceRefocus(boolean forceRefocus);

      public abstract Builder setAction(Focus.Action action);

      /** Copies node at{@link Focus.Builder}, caller retains ownership. */
      public abstract Builder setScrolledNode(@Nullable AccessibilityNodeInfoCompat scrolledNode);

      public abstract Builder setScreenState(@Nullable ScreenState screenState);

      abstract @Nullable AccessibilityNodeInfoCompat start();

      abstract @Nullable AccessibilityNodeInfoCompat target();

      abstract @Nullable AccessibilityNodeInfoCompat scrolledNode();

      abstract Focus autoBuild();

      public Focus build() {
        setStart(AccessibilityNodeInfoUtils.obtain(start()));
        setTarget(AccessibilityNodeInfoUtils.obtain(target()));
        setScrolledNode(AccessibilityNodeInfoUtils.obtain(scrolledNode()));
        return autoBuild();
      }
    }

    public void recycle() {
      AccessibilityNodeInfoUtils.recycleNodes(start(), target(), scrolledNode());
    }

    @Override
    public final String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalField("action", action()),
          StringBuilderUtils.optionalSubObj("start", toStringShort(start())),
          StringBuilderUtils.optionalSubObj("target", toStringShort(target())),
          StringBuilderUtils.optionalInt("direction", direction(), SEARCH_FOCUS_UNKNOWN),
          StringBuilderUtils.optionalSubObj("focusActionInfo", focusActionInfo()),
          StringBuilderUtils.optionalSubObj("navigationAction", navigationAction()),
          StringBuilderUtils.optionalText("searchKeyword", searchKeyword()),
          StringBuilderUtils.optionalTag("forceRefocus", forceRefocus()),
          StringBuilderUtils.optionalSubObj("scrolledNode", toStringShort(scrolledNode())),
          StringBuilderUtils.optionalSubObj("screenState", screenState()));
    }
  }

  /** Inner data-structure for pass-through. */
  public abstract static class PassThroughMode {

    /** Types of pass-through actions. */
    public enum Action {
      DISABLE_PASSTHROUGH,
      ENABLE_PASSTHROUGH,
      // This confirm dialog action is used when need to check if pop a user confirms from a dialog
      // is necessary.
      PASSTHROUGH_CONFIRM_DIALOG,
      STOP_TIMER,
      LOCK_PASS_THROUGH
    }

    /**
     * AutoValue enforces the data field check. For interpreter events which do not contain region,
     * fills an unused region to pass the AutoValue check
     */
    public static PassThroughMode create(PassThroughMode.Action action) {
      return new AutoValue_Feedback_PassThroughMode(action, new Region());
    }

    /**
     * AutoValue enforces the data field check. For BrailleIME case which carries null region to
     * disable pass-through, replaces with an empty region to pass the AutoValue check
     */
    public static PassThroughMode create(PassThroughMode.Action action, Region region) {
      if (region == null) {
        region = new Region();
      }
      return new AutoValue_Feedback_PassThroughMode(action, region);
    }

    public abstract PassThroughMode.Action action();

    public abstract Region region();
  }

  /** Inner data-structure for speech rate adjust. */
  @AutoValue
  public abstract static class SpeechRate {

    /** Types of pass-through actions. */
    public enum Action {
      INCREASE_RATE,
      DECREASE_RATE
    }

    /**
     * AutoValue enforces the data field check. For interpreter events which do not contain region,
     * fills an unused region to pass the AutoValue check
     */
    public static SpeechRate create(SpeechRate.Action action) {
      return new AutoValue_Feedback_SpeechRate(action);
    }

    public abstract SpeechRate.Action action();
  }

  /** Inner data-structure for adjust value. */
  @AutoValue
  public abstract static class AdjustValue {

    /** Types of adjust value actions. */
    public enum Action {
      INCREASE_VALUE,
      DECREASE_VALUE
    }

    /**
     * AutoValue enforces the data field check. For interpreter events which do not contain region,
     * fills an unused region to pass the AutoValue check
     */
    public static AdjustValue create(AdjustValue.Action action) {
      return new AutoValue_Feedback_AdjustValue(action);
    }

    public abstract AdjustValue.Action action();
  }

  /** Inner data-structure for adjust volume. */
  public abstract static class AdjustVolume {

    /**
     * Types of volume streams can be adjusted. Currently, we support Accessibility Stream only.
     * Later, we can extend the types for media/call/...,etc
     */
    public enum StreamType {
      STREAM_TYPE_ACCESSIBILITY
    }

    /** Types of adjust volume actions. */
    public enum Action {
      INCREASE_VOLUME,
      DECREASE_VOLUME
    }

    /** This is for the constructor of the AutoValue class AdjustVolume. */
    public static AdjustVolume create(AdjustVolume.Action action, StreamType type) {
      return new AutoValue_Feedback_AdjustVolume(action, type);
    }

    public abstract AdjustVolume.Action action();

    public abstract StreamType streamType();
  }

  /////////////////////////////////////////////////////////////////////////////////////
  // Focus directional navigation feedback

  /** Inner data-structure for focus directional navigation. */
  public abstract static class FocusDirection {

    /** Types of exclusive focus-direction actions, mostly without additional feedback data. */
    public enum Action {
      FOLLOW,
      NEXT,
      NEXT_PAGE,
      PREVIOUS_PAGE,
      TOP,
      BOTTOM,
      SCROLL_UP,
      SCROLL_DOWN,
      SCROLL_LEFT,
      SCROLL_RIGHT,
      SET_GRANULARITY,
      NEXT_GRANULARITY,
      PREVIOUS_GRANULARITY,
      SELECTION_MODE_ON,
      SELECTION_MODE_OFF,
      NAVIGATE;
    }

    public abstract @SearchDirection int direction();

    public abstract @TargetType int htmlTargetType();

    // TODO: Remove follow-focus events & actor logic, and instead pass focused node as
    // argument to all focus-direction feedback.
    public abstract @Nullable AccessibilityNodeInfoCompat targetNode();

    public abstract boolean defaultToInputFocus();

    public abstract boolean scroll();

    public abstract boolean wrap();

    public abstract boolean toWindow();

    public abstract @InputMode int inputMode();

    public abstract @Nullable CursorGranularity granularity();

    public abstract boolean fromUser();

    public abstract FocusDirection.Action action();

    public boolean hasDirection() {
      return (direction() != SEARCH_FOCUS_UNKNOWN);
    }

    public boolean hasHtmlTargetType() {
      return (htmlTargetType() != TARGET_DEFAULT);
    }

    public static Builder builder() {
      return new AutoValue_Feedback_FocusDirection.Builder()
          // Set default values that are not null.
          .setDirection(SEARCH_FOCUS_UNKNOWN)
          .setHtmlTargetType(TARGET_DEFAULT)
          .setDefaultToInputFocus(false)
          .setScroll(false)
          .setWrap(false)
          .setToWindow(false)
          .setInputMode(INPUT_MODE_UNKNOWN)
          .setFromUser(false);
    }

    /** Builder for FocusDirection feedback data */
    public abstract static class Builder {
      public abstract Builder setDirection(@SearchDirection int direction);

      public abstract Builder setHtmlTargetType(@TargetType int htmlTargetType);

      /**
       * Copies targetNode at{@link FocusDirection.Builder}, caller retains ownership. This node can
       * be used at{@link FocusDirection.Action} FOLLOW, SELECTION_MODE_ON and SET_GRANULARITY.
       */
      public abstract Builder setTargetNode(@Nullable AccessibilityNodeInfoCompat targetNode);

      public abstract Builder setDefaultToInputFocus(boolean defaultToInputFocus);

      public abstract Builder setScroll(boolean scroll);

      public abstract Builder setWrap(boolean wrap);

      public abstract Builder setToWindow(boolean toWindow);

      public abstract Builder setInputMode(@InputMode int inputMode);

      public abstract Builder setGranularity(@Nullable CursorGranularity granularity);

      public abstract Builder setFromUser(boolean fromUser);

      public abstract Builder setAction(FocusDirection.Action action);

      abstract @Nullable AccessibilityNodeInfoCompat targetNode();

      abstract FocusDirection autoBuild();

      public FocusDirection build() {
        setTargetNode(AccessibilityNodeInfoUtils.obtain(targetNode()));
        return autoBuild();
      }
    }

    public void recycle() {
      AccessibilityNodeInfoUtils.recycleNodes(targetNode());
    }

    @Override
    public final String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalField("action", action()),
          StringBuilderUtils.optionalInt("direction", direction(), SEARCH_FOCUS_UNKNOWN),
          StringBuilderUtils.optionalInt("htmlTargetType", htmlTargetType(), TARGET_DEFAULT),
          StringBuilderUtils.optionalSubObj("targetNode", toStringShort(targetNode())),
          StringBuilderUtils.optionalTag("defaultToInputFocus", defaultToInputFocus()),
          StringBuilderUtils.optionalTag("scroll", scroll()),
          StringBuilderUtils.optionalTag("wrap", wrap()),
          StringBuilderUtils.optionalTag("toWindow", toWindow()),
          StringBuilderUtils.optionalInt("inputMode", inputMode(), INPUT_MODE_UNKNOWN),
          StringBuilderUtils.optionalField("granularity", granularity()),
          StringBuilderUtils.optionalTag("fromUser", fromUser()));
    }
  }

  /** Inner data-structure for controlling TalkBack UI. */
  @AutoValue
  public abstract static class TalkBackUI {

    /** Types of exclusive UI actions. */
    public enum Action {
      SHOW_SELECTOR_UI,
      HIDE,
      SUPPORT,
      NOT_SUPPORT
    }

    public abstract TalkBackUI.Action action();

    public abstract TalkBackUIActor.Type type();

    public abstract @Nullable CharSequence message();

    public abstract boolean showIcon();

    public static TalkBackUI create(TalkBackUI.Action action, TalkBackUIActor.Type type) {
      return new AutoValue_Feedback_TalkBackUI(
          action, type, /* message= */ null, /* showIcon= */ true);
    }

    public static TalkBackUI create(
        TalkBackUI.Action action,
        TalkBackUIActor.Type type,
        CharSequence message,
        boolean showIcon) {
      return new AutoValue_Feedback_TalkBackUI(action, type, message, showIcon);
    }
  }

  /** Inner data-structure for Gesture. */
  @AutoValue
  public abstract static class Gesture {

    /** Types of exclusive gesture actions. */
    public enum Action {
      SAVE,
      REPORT
    }

    public abstract Action action();

    public abstract @Nullable AccessibilityGestureEvent currentGesture();

    public static Gesture create(Gesture.Action action) {
      return new AutoValue_Feedback_Gesture(action, null);
    }

    public static Gesture create(Gesture.Action action, AccessibilityGestureEvent currentGesture) {
      return new AutoValue_Feedback_Gesture(action, currentGesture);
    }
  }

  // TODO: Add feedback types: braille, UI-action.

  static String groupIdToString(int groupId) {
    switch (groupId) {
      case HINT:
        return "HINT";
      case GESTURE_VIBRATION:
        return "GESTURE_VIBRATION";
      case CURSOR_STATE:
        return "CURSOR_STATE";
      default:
        return "(unknown)";
    }
  }
}

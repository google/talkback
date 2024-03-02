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
import static com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TARGET_DEFAULT;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.toStringShort;
import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_UNKNOWN;
import static com.google.android.accessibility.utils.output.FeedbackController.NO_SEPARATION;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UNKNOWN;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback.AdjustVolume.StreamType;
import com.google.android.accessibility.talkback.Feedback.Scroll.Action;
import com.google.android.accessibility.talkback.actor.TalkBackUIActor;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorCursorState;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.SpellingSuggestion;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter.ScrollTimeout;
import com.google.android.accessibility.utils.monitor.InputModeTracker.InputMode;
import com.google.android.accessibility.utils.output.ScrollActionRecord;
import com.google.android.accessibility.utils.output.ScrollActionRecord.UserAction;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.NonNull;
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

  /** Interrupt levels. Level 1 is default. Level -1 does not interrupt at all. */
  public @interface InterruptLevel {}

  //////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  public static Feedback create(@Nullable EventId eventId, List<Part> sequence) {
    return new AutoValue_Feedback(eventId, ImmutableList.copyOf(sequence));
  }

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

  public static EditText.Builder edit(AccessibilityNodeInfoCompat node, EditText.Action action) {
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

  /**
   * Navigates the previous/next misspelled word if any.
   *
   * @param isNext specifies the direction (previous/next) of traversal.
   */
  public static Part.Builder navigateTypo(boolean isNext, boolean useInputFocusIfEmpty) {
    return Part.builder().setNavigateTypo(NavigateTypo.create(isNext, useInputFocusIfEmpty));
  }

  public static Part.Builder adjustVolume(AdjustVolume.Action action, StreamType streamType) {
    return Part.builder().setAdjustVolume(AdjustVolume.create(action, streamType));
  }

  public static Part.Builder nodeAction(AccessibilityNode target, int actionId) {
    Part.Builder partBuilder = Part.builder();
    if (target == null) {
      return partBuilder;
    }
    return partBuilder.setNodeAction(
        NodeAction.builder().setTarget(target).setActionId(actionId).build());
  }

  public static Part.Builder nodeAction(
      @Nullable AccessibilityNodeInfoCompat target, int actionId) {
    return nodeAction(target, actionId, /* args= */ null);
  }

  public static Part.Builder nodeAction(
      @Nullable AccessibilityNodeInfoCompat target, int actionId, @Nullable Bundle args) {
    Part.Builder partBuilder = Part.builder();
    if (target == null) {
      return partBuilder;
    }
    AccessibilityNode accessibilityNode = AccessibilityNode.takeOwnership(target);
    return partBuilder.setNodeAction(
        NodeAction.builder()
            .setTarget(accessibilityNode)
            .setActionId(actionId)
            .setArgs(args)
            .build());
  }

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

  public static Part.Builder scroll(
      AccessibilityNode node, @UserAction int userAction, int nodeAction, @Nullable String source) {
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

  public static Part.Builder scroll(
      AccessibilityNodeInfoCompat nodeCompat,
      @UserAction int userAction,
      int nodeAction,
      @Nullable String source,
      ScrollTimeout scrollTimeout) {
    return Part.builder()
        .setScroll(
            Scroll.builder()
                .setAction(Scroll.Action.SCROLL)
                .setNodeCompat(nodeCompat)
                .setUserAction(userAction)
                .setNodeAction(nodeAction)
                .setSource(source)
                .setTimeout(scrollTimeout)
                .build());
  }

  public static Part.Builder scrollCancelTimeout() {
    return Part.builder()
        .setScroll(
            Scroll.builder()
                .setAction(Scroll.Action.CANCEL_TIMEOUT)
                .setUserAction(ScrollActionRecord.ACTION_UNKNOWN)
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
                .setUserAction(ScrollActionRecord.ACTION_AUTO_SCROLL)
                .setNodeAction(ACTION_SHOW_ON_SCREEN.getId())
                .setSource(ScrollActionRecord.FOCUS)
                .build());
  }

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

  public static FocusDirection.Builder nextContainer(@InputMode int inputMode) {
    return FocusDirection.builder()
        .setAction(FocusDirection.Action.NAVIGATE)
        .setDirection(SEARCH_FOCUS_FORWARD)
        .setToContainer(true)
        .setInputMode(inputMode);
  }

  public static FocusDirection.Builder prevContainer(@InputMode int inputMode) {
    return FocusDirection.builder()
        .setAction(FocusDirection.Action.NAVIGATE)
        .setDirection(SEARCH_FOCUS_BACKWARD)
        .setToContainer(true)
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

  public static Part.Builder selectionModeOn(AccessibilityNodeInfoCompat node) {
    return Part.builder()
        .setFocusDirection(
            FocusDirection.builder()
                .setAction(FocusDirection.Action.SELECTION_MODE_ON)
                .setTargetNode(node)
                .build());
  }

  /** Turn off the selection mode. */
  public static Part.Builder selectionModeOff() {
    return Part.builder()
        .setFocusDirection(
            FocusDirection.builder().setAction(FocusDirection.Action.SELECTION_MODE_OFF).build());
  }

  public static Part.Builder talkBackUI(TalkBackUI.Action action, TalkBackUIActor.Type type) {
    return Part.builder()
        .setTalkBackUI(TalkBackUI.create(action, type, /* message= */ null, /* showIcon= */ true));
  }

  public static Part.Builder talkBackUI(
      TalkBackUI.Action action, TalkBackUIActor.Type type, CharSequence message, boolean showIcon) {
    return Part.builder().setTalkBackUI(TalkBackUI.create(action, type, message, showIcon));
  }

  public static Part.Builder showToast(
      ShowToast.Action action, CharSequence message, boolean durationIsLong) {
    return Part.builder().setShowToast(ShowToast.create(action, message, durationIsLong));
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

  public static Part.Builder deviceInfo(DeviceInfo.Action action, Configuration configuration) {
    return Part.builder().setDeviceInfo(DeviceInfo.create(action, configuration));
  }

  public static Part.Builder performImageCaptions(AccessibilityNodeInfoCompat node) {
    return performImageCaptions(node, /* isUserRequested= */ false);
  }

  public static Part.Builder performImageCaptions(
      AccessibilityNodeInfoCompat node, boolean isUserRequested) {
    return Part.builder()
        .setImageCaption(
            ImageCaption.builder()
                .setAction(ImageCaption.Action.PERFORM_CAPTIONS)
                .setTarget(node)
                .setUserRequested(isUserRequested)
                .build());
  }

  public static Part.Builder confirmDownloadAndPerformCaptions(AccessibilityNodeInfoCompat node) {
    return Part.builder()
        .setImageCaption(
            ImageCaption.builder()
                .setAction(ImageCaption.Action.CONFIRM_DOWNLOAD_AND_PERFORM_CAPTIONS)
                .setTarget(node)
                .build());
  }

  public static Part.Builder initializeIconDetection() {
    return Part.builder()
        .setImageCaption(
            ImageCaption.builder()
                .setAction(ImageCaption.Action.INITIALIZE_ICON_DETECTION)
                .build());
  }

  public static Part.Builder initializeImageDescription() {
    return Part.builder()
        .setImageCaption(
            ImageCaption.builder()
                .setAction(ImageCaption.Action.INITIALIZE_IMAGE_DESCRIPTION)
                .build());
  }

  public static Part.Builder wholeScreenChange() {
    return Part.builder()
        .setUiChange(
            UiChange.create(UiChange.Action.CLEAR_SCREEN_CACHE, /* sourceBoundsInScreen= */ null));
  }

  public static Part.Builder partialUiChange(UiChange.Action action, Rect sourceBoundsInScreen) {
    return Part.builder().setUiChange(UiChange.create(action, sourceBoundsInScreen));
  }

  public static Part.Builder universalSearch(UniversalSearch.Action action) {
    return Part.builder().setUniversalSearch(UniversalSearch.builder().setAction(action).build());
  }

  public static Part.Builder renewOverlay(Configuration config) {
    return Part.builder()
        .setUniversalSearch(
            UniversalSearch.builder()
                .setAction(UniversalSearch.Action.RENEW_OVERLAY)
                .setConfig(config)
                .build());
  }

  public static Part.Builder requestServiceFlag(ServiceFlag.Action action, int flag) {
    return Part.builder().setServiceFlag(ServiceFlag.create(action, flag));
  }

  public static Part.Builder performBrailleDisplayAction(BrailleDisplay.Action action) {
    return Part.builder().setBrailleDisplay(BrailleDisplay.create(action));
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

    @InterruptGroup
    public abstract int interruptGroup();

    // In the future, may also need to separately set interruptable-level.
    @InterruptLevel
    public abstract int interruptLevel();

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

    public abstract @Nullable NavigateTypo navigateTypo();

    public abstract @Nullable AdjustVolume adjustVolume();

    public abstract @Nullable TalkBackUI talkBackUI();

    public abstract @Nullable ShowToast showToast();

    public abstract @Nullable Gesture gesture();

    public abstract @Nullable ImageCaption imageCaption();

    public abstract @Nullable DeviceInfo deviceInfo();

    public abstract @Nullable UiChange uiChange();

    public abstract @Nullable UniversalSearch universalSearch();

    public abstract @Nullable ServiceFlag serviceFlag();

    public abstract @Nullable BrailleDisplay brailleDisplay();

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

    /** Builder for Feedback.Part. */
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

      public abstract Builder setEdit(EditText edit);

      public abstract Builder setSystemAction(SystemAction systemAction);

      public abstract Builder setNodeAction(NodeAction nodeAction);

      public abstract Builder setWebAction(WebAction webAction);

      public abstract Builder setScroll(Scroll scroll);

      public abstract Builder setFocus(Focus focus);

      public abstract Builder setFocusDirection(FocusDirection focusDirection);

      public abstract Builder setPassThroughMode(PassThroughMode passThroughMode);

      public abstract Builder setSpeechRate(SpeechRate speechRate);

      public abstract Builder setAdjustValue(AdjustValue adjustValue);

      public abstract Builder setNavigateTypo(NavigateTypo navigateTypo);

      public abstract Builder setAdjustVolume(AdjustVolume adjustVolume);

      public abstract Builder setTalkBackUI(TalkBackUI talkBackUI);

      public abstract Builder setShowToast(ShowToast showToast);

      public abstract Builder setGesture(Gesture gesture);

      public abstract Builder setImageCaption(ImageCaption imageCaption);

      public abstract Builder setDeviceInfo(DeviceInfo deviceInfo);

      public abstract Builder setUiChange(UiChange uiChange);

      public abstract Builder setUniversalSearch(UniversalSearch universalSearch);

      public abstract Builder setServiceFlag(ServiceFlag serviceFlag);

      public abstract Builder setBrailleDisplay(BrailleDisplay brailleDisplay);

      public abstract Part build();
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
              StringBuilderUtils.optionalTag("interruptGentle", interruptGentle()),
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
              StringBuilderUtils.optionalSubObj("showToast", showToast()),
              StringBuilderUtils.optionalSubObj("gesture", gesture()),
              StringBuilderUtils.optionalSubObj("imageCaption", imageCaption()),
              StringBuilderUtils.optionalSubObj("deviceInfo", deviceInfo()),
              StringBuilderUtils.optionalSubObj("uiChange", uiChange()),
              StringBuilderUtils.optionalSubObj("speechRate", speechRate()),
              StringBuilderUtils.optionalSubObj("adjustValue", adjustValue()),
              StringBuilderUtils.optionalSubObj("navigateTypo", navigateTypo()),
              StringBuilderUtils.optionalSubObj("adjustVolume", adjustVolume()),
              StringBuilderUtils.optionalSubObj("universalSearch", universalSearch()),
              StringBuilderUtils.optionalSubObj("serviceFlag", serviceFlag()),
              StringBuilderUtils.optionalSubObj("brailleDisplay", brailleDisplay()));
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

      public abstract Label build();
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
      COPY_LAST,
      REPEAT_SAVED,
      SPELL_SAVED,
      PAUSE_OR_RESUME,
      TOGGLE_VOICE_FEEDBACK,
      /**
       * The SILENCE and UNSILENCE actions should be used with caution, the caller should maintain
       * the lifecycle of the silence state, it currently only used by voice command.
       */
      SILENCE,
      UNSILENCE,
      INVALIDATE_FREQUENT_CONTENT_CHANGE_CACHE,
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

    @InterruptGroup
    public abstract int hintInterruptGroup();

    @InterruptLevel
    public abstract int hintInterruptLevel();

    public static Builder builder() {
      // Set default hint-priority & group.
      return new AutoValue_Feedback_Speech.Builder()
          .setHintInterruptGroup(HINT)
          .setHintInterruptLevel(1);
    }

    /** Builder for Speech feedback data */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setAction(Speech.Action action);

      public abstract Builder setText(@Nullable CharSequence text);

      public abstract Builder setOptions(@Nullable SpeakOptions options);

      public abstract Builder setHint(@Nullable CharSequence hint);

      public abstract Builder setHintSpeakOptions(@Nullable SpeakOptions hintSpeakOptions);

      public abstract Builder setHintInterruptGroup(@InterruptGroup int hintInterruptGroup);

      public abstract Builder setHintInterruptLevel(@InterruptLevel int hintInterruptLevel);

      public abstract Speech build();
    }

    @Override
    public final String toString() {
      // Implement toString() has the effect of overriding the AutoValue autogenerated toString
      // method.
      String string =
          StringBuilderUtils.joinFields(
              StringBuilderUtils.optionalField("action", action()),
              StringBuilderUtils.optionalText(
                  "text",
                  FeatureSupport.logcatIncludePsi()
                      ? text()
                      : TextUtils.isEmpty(text()) ? null : "***"),
              StringBuilderUtils.optionalSubObj("options", options()),
              String.format("%s= %s", "hint", hint()),
              String.format("%s= %s", "hintSpeakOptions", hintSpeakOptions()));

      return string;
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
      return create(resourceId, /* rate= */ 1.0f, /* volume= */ 1.0f, NO_SEPARATION);
    }

    public static Sound create(int resourceId, float rate, float volume) {
      return new AutoValue_Feedback_Sound(resourceId, rate, volume, NO_SEPARATION);
    }

    public static Sound create(int resourceId, float rate, float volume, long separationMillisec) {
      return new AutoValue_Feedback_Sound(resourceId, rate, volume, separationMillisec);
    }

    public abstract int resourceId();

    public abstract float rate();

    public abstract float volume();

    public abstract long separationMillisec();
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
      TRIGGER_BRAILLE_DISPLAY_SETTINGS,
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
      INSERT, // Requires text.
      TYPO_CORRECTION, // Requires text and suggestion.
      MOVE_CURSOR; // Requires cursor index.
    }

    public abstract AccessibilityNodeInfoCompat node();

    public abstract Action action();

    public abstract boolean stopSelecting();

    public abstract @Nullable CharSequence text();

    public abstract @Nullable SpellingSuggestion spellingSuggestion();

    public abstract int cursorIndex();

    public static Builder builder() {
      return new AutoValue_Feedback_EditText.Builder()
          // Set default values that are not null.
          .setStopSelecting(false)
          .setCursorIndex(-1);
    }

    /** Builder for EditText feedback data */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setNode(AccessibilityNodeInfoCompat node);

      public abstract Builder setAction(Action action);

      public abstract Builder setStopSelecting(boolean stopSelecting);

      public abstract Builder setText(@Nullable CharSequence text);

      public abstract Builder setSpellingSuggestion(
          @Nullable SpellingSuggestion spellingSuggestion);

      public abstract Builder setCursorIndex(int cursorIndex);

      public abstract EditText build();
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
    public abstract AccessibilityNode target();

    public abstract int actionId();

    public abstract @Nullable Bundle args();

    public static Builder builder() {
      return new AutoValue_Feedback_NodeAction.Builder();
    }

    /** Builder for NodeAction feedback data */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setTarget(AccessibilityNode target);

      public abstract Builder setActionId(int actionId);

      public abstract Builder setArgs(@Nullable Bundle args);

      public abstract NodeAction build();
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

      public abstract Builder setTarget(AccessibilityNodeInfoCompat target);

      public abstract Builder setNodeAction(int nodeAction);

      public abstract Builder setNodeActionArgs(@Nullable Bundle nodeActionArgs);

      public abstract Builder setUpdateFocusHistory(boolean updateFocusHistory);

      public abstract Builder setNavigationAction(NavigationAction navigationAction);

      public abstract WebAction build();
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

    @UserAction
    public abstract int userAction();

    public abstract int nodeAction();

    public abstract @Nullable String source();

    public abstract ScrollTimeout timeout();

    public static Scroll.Builder builder() {
      // By default, use timeout short.
      return new AutoValue_Feedback_Scroll.Builder().setTimeout(ScrollTimeout.SCROLL_TIMEOUT_SHORT);
    }

    /** Builder for Scroll feedback data */
    public abstract static class Builder {
      public abstract Scroll.Builder setAction(Scroll.Action action);

      public abstract Scroll.Builder setNode(@Nullable AccessibilityNode node);

      public abstract Scroll.Builder setNodeCompat(
          @Nullable AccessibilityNodeInfoCompat nodeCompat);

      public abstract Scroll.Builder setNodeToMoveOnScreen(
          @Nullable AccessibilityNodeInfoCompat nodeToMoveOnScreen);

      public abstract Scroll.Builder setUserAction(@UserAction int userAction);

      public abstract Scroll.Builder setNodeAction(int nodeAction);

      public abstract Scroll.Builder setSource(@Nullable String source);

      public abstract Scroll.Builder setTimeout(ScrollTimeout timeout);

      public abstract Scroll build();
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
      RESTORE_TO_CACHE,
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
      SEARCH_AGAIN,
      ENSURE_ACCESSIBILITY_FOCUS_ON_SCREEN,
      RENEW_ENSURE_FOCUS;
    }

    public abstract @Nullable AccessibilityNodeInfoCompat start();

    public abstract @Nullable AccessibilityNodeInfoCompat target();

    public abstract @Nullable FocusActionInfo focusActionInfo();

    public abstract @Nullable NavigationAction navigationAction();

    public abstract @Nullable CharSequence searchKeyword();

    public abstract boolean forceRefocus();

    public abstract Focus.Action action();

    public abstract @Nullable ScreenState screenState();

    public static Builder builder() {
      return new AutoValue_Feedback_Focus.Builder()
          // Set default values that are not null.
          .setForceRefocus(false);
    }

    /** Builder for Focus feedback data */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setStart(@Nullable AccessibilityNodeInfoCompat start);

      public abstract Builder setTarget(@Nullable AccessibilityNodeInfoCompat target);

      public abstract Builder setFocusActionInfo(@Nullable FocusActionInfo focusActionInfo);

      public abstract Builder setNavigationAction(@Nullable NavigationAction navigationAction);

      public abstract Builder setSearchKeyword(@Nullable CharSequence searchKeyword);

      public abstract Builder setForceRefocus(boolean forceRefocus);

      public abstract Builder setAction(Focus.Action action);

      public abstract Builder setScreenState(@Nullable ScreenState screenState);

      public abstract Focus build();
    }

    @Override
    public final String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalField("action", action()),
          StringBuilderUtils.optionalSubObj("start", toStringShort(start())),
          StringBuilderUtils.optionalSubObj("target", toStringShort(target())),
          StringBuilderUtils.optionalSubObj("focusActionInfo", focusActionInfo()),
          StringBuilderUtils.optionalSubObj("navigationAction", navigationAction()),
          StringBuilderUtils.optionalText("searchKeyword", searchKeyword()),
          StringBuilderUtils.optionalTag("forceRefocus", forceRefocus()),
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

  /** Inner data-structure for typo traversal. */
  @AutoValue
  public abstract static class NavigateTypo {
    /**
     * AutoValue enforces the data field check. For interpreter events which do not contain region,
     * fills an unused region to pass the AutoValue check
     */
    public static NavigateTypo create(boolean isNext, boolean useInputFocusIfEmpty) {
      return new AutoValue_Feedback_NavigateTypo(isNext, useInputFocusIfEmpty);
    }

    public abstract boolean isNext();

    public abstract boolean useInputFocusIfEmpty();
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

    @SearchDirectionOrUnknown
    public abstract int direction();

    @TargetType
    public abstract int htmlTargetType();

    public abstract @Nullable AccessibilityNodeInfoCompat targetNode();

    public abstract boolean defaultToInputFocus();

    public abstract boolean scroll();

    public abstract boolean wrap();

    public abstract boolean toContainer();

    public abstract boolean toWindow();

    @InputMode
    public abstract int inputMode();

    public abstract @Nullable CursorGranularity granularity();

    public abstract boolean fromUser();

    public abstract FocusDirection.Action action();

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
          .setToContainer(false)
          .setToWindow(false)
          .setInputMode(INPUT_MODE_UNKNOWN)
          .setFromUser(false);
    }

    /** Builder for FocusDirection feedback data */
    public abstract static class Builder {
      public abstract Builder setDirection(@SearchDirectionOrUnknown int direction);

      public abstract Builder setHtmlTargetType(@TargetType int htmlTargetType);

      /**
       * This node can be used at {@link FocusDirection.Action} FOLLOW, SELECTION_MODE_ON and
       * SET_GRANULARITY.
       */
      public abstract Builder setTargetNode(@Nullable AccessibilityNodeInfoCompat targetNode);

      public abstract Builder setDefaultToInputFocus(boolean defaultToInputFocus);

      public abstract Builder setScroll(boolean scroll);

      public abstract Builder setWrap(boolean wrap);

      public abstract Builder setToContainer(boolean toContainer);

      public abstract Builder setToWindow(boolean toWindow);

      public abstract Builder setInputMode(@InputMode int inputMode);

      public abstract Builder setGranularity(@Nullable CursorGranularity granularity);

      public abstract Builder setFromUser(boolean fromUser);

      public abstract Builder setAction(FocusDirection.Action action);

      public abstract FocusDirection build();
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
          StringBuilderUtils.optionalTag("toContainer", toContainer()),
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
      SHOW_GESTURE_ACTION_UI,
      HIDE,
      SUPPORT,
      NOT_SUPPORT
    }

    public abstract TalkBackUI.Action action();

    public abstract TalkBackUIActor.Type type();

    public abstract @Nullable CharSequence message();

    public abstract boolean showIcon();

    public static TalkBackUI create(
        TalkBackUI.Action action,
        TalkBackUIActor.Type type,
        CharSequence message,
        boolean showIcon) {
      return new AutoValue_Feedback_TalkBackUI(action, type, message, showIcon);
    }
  }

  /** Inner data-structure for displaying toast. */
  @AutoValue
  public abstract static class ShowToast {

    /** Types of showing toast actions. */
    public enum Action {
      SHOW,
    }

    public abstract ShowToast.Action action();

    public abstract @Nullable CharSequence message();

    public abstract boolean durationIsLong();

    public static ShowToast create(
        ShowToast.Action action, CharSequence message, boolean durationIsLong) {
      return new AutoValue_Feedback_ShowToast(action, message, durationIsLong);
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

  /** Inner data-structure for image caption. */
  @AutoValue
  public abstract static class ImageCaption {

    /** Types of exclusive image caption actions. */
    public enum Action {
      /** Creates and performs caption requests for the focused node. */
      PERFORM_CAPTIONS,
      CONFIRM_DOWNLOAD_AND_PERFORM_CAPTIONS,
      INITIALIZE_ICON_DETECTION,
      INITIALIZE_IMAGE_DESCRIPTION,
    }

    public abstract ImageCaption.Action action();

    @Nullable
    public abstract AccessibilityNodeInfoCompat target();

    /** Return true, if the image-caption triggers by users. */
    public abstract boolean userRequested();

    public static Builder builder() {
      return new AutoValue_Feedback_ImageCaption.Builder().setUserRequested(false);
    }

    /** Builder for ImageCaption feedback data. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setAction(ImageCaption.Action action);

      public abstract Builder setTarget(@Nullable AccessibilityNodeInfoCompat target);

      public abstract Builder setUserRequested(boolean isUserRequested);

      public abstract ImageCaption build();
    }
  }

  /** Inner data-structure for controlling device info. */
  public abstract static class DeviceInfo {

    /** Types of exclusive actions of device info. */
    public enum Action {
      CONFIG_CHANGED
    }

    public abstract DeviceInfo.Action action();

    public abstract @Nullable Configuration configuration();

    public static DeviceInfo create(
        DeviceInfo.Action action, @Nullable Configuration configuration) {
      return new AutoValue_Feedback_DeviceInfo(action, configuration);
    }
  }

  /** Inner data-structure for UI change. */
  @AutoValue
  public abstract static class UiChange {

    /** Types of exclusive UI change actions. */
    public enum Action {
      CLEAR_SCREEN_CACHE,
      CLEAR_CACHE_FOR_VIEW,
    }

    public abstract Action action();

    public abstract @Nullable Rect sourceBoundsInScreen();

    public static UiChange create(UiChange.Action action, @Nullable Rect sourceBoundsInScreen) {
      return new AutoValue_Feedback_UiChange(action, sourceBoundsInScreen);
    }
  }

  /** Inner data-structure for UniversalSearch. */
  @AutoValue
  public abstract static class UniversalSearch {

    /** Types of exclusive UniversalSearch actions. */
    public enum Action {
      TOGGLE_SEARCH,
      CANCEL_SEARCH,
      HANDLE_SCREEN_STATE,
      RENEW_OVERLAY
    }

    public abstract Action action();

    public abstract @Nullable Configuration config();

    public static UniversalSearch.Builder builder() {
      return new AutoValue_Feedback_UniversalSearch.Builder();
    }
    /** Builder for UniversalSearch feedback data. */
    @AutoValue.Builder
    public abstract static class Builder {

      public abstract Builder setAction(Action action);

      public abstract Builder setConfig(@Nullable Configuration config);

      public abstract UniversalSearch build();
    }
  }

  /** Inner data-structure for requesting a service flag. */
  @AutoValue
  public abstract static class ServiceFlag {

    /** Types of action to control service flags. */
    public enum Action {
      ENABLE_FLAG,
      DISABLE_FLAG,
    }

    public abstract Action action();

    public abstract int flag();

    public static ServiceFlag create(ServiceFlag.Action action, int flag) {
      return new AutoValue_Feedback_ServiceFlag(action, flag);
    }
  }

  /** Inner data-structure for performing braille display action. */
  @AutoValue
  public abstract static class BrailleDisplay {

    /** Types of action to performed by braille display. */
    public enum Action {
      TOGGLE_BRAILLE_DISPLAY_ON_OR_OFF,
    }

    public abstract BrailleDisplay.Action action();

    public static BrailleDisplay create(BrailleDisplay.Action action) {
      return new AutoValue_Feedback_BrailleDisplay(action);
    }
  }

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

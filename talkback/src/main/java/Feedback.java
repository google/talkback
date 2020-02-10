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

import static com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TARGET_DEFAULT;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_UNKNOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UNKNOWN;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ScrollEventInterpreter.UserAction;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorCursorState;
import com.google.android.accessibility.talkback.focusmanagement.AutoScrollActor.AutoScrollRecord.Source;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.input.InputModeManager.InputMode;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Data-structure of feedback from pipeline-stage feedback-mapper to actors. Feedback is a sequence
 * of Parts. Each Part is a collection of specific feedback types, mostly empty.
 *
 * <pre>{@code
 * ArrayList<Feedback.Part> feedbackSequence = new ArrayList();
 * feedbackSequence.add(Feedback.Part.builder()
 *     .speech("hello", SpeakOptions.create()).sound(R.id.sound).interrupt(HINT, 2).build());
 * Feedback feedback = Feedback.create(eventId, feedbackSequence);
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
  @IntDef({DEFAULT, HINT, GESTURE_VIBRATION, CURSOR_STATE})
  public @interface InterruptGroup {}

  public static final int DEFAULT = -1;
  public static final int HINT = 0;
  public static final int GESTURE_VIBRATION = 1;
  /** Use for speech of cursor state at {@link ProcessorCursorState} */
  public static final int CURSOR_STATE = 2;

  /** Interrupt levels. Level -1 does not interrupt at all. */
  @IntDef({-1, 0, 1, 2})
  public @interface InterruptLevel {}

  /** Interrupt unknown class */
  public static final String CLASS_NAME_UNKNOWN = "Unknown";

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

  public static Part.Builder senderName(String senderName) {
    return Part.builder().setSenderName(senderName);
  }

  public static Part.Builder speech(Speech.Action action) {
    return Part.builder().setSpeech(Speech.create(action));
  }

  public static Part.Builder speech(CharSequence text, @Nullable SpeakOptions options) {
    return Part.builder().speech(text, options);
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

  /** Copies node, caller retains ownership. */
  public static EditText.Builder edit(AccessibilityNodeInfoCompat node, EditText.Action action) {
    return EditText.builder().setNode(AccessibilityNodeInfoCompat.obtain(node)).setAction(action);
  }

  /** Copies target, caller retains ownership. */
  public static Part.Builder nodeAction(AccessibilityNode target, int actionId) {
    Part.Builder partBuilder = Part.builder();
    if (target == null) {
      return partBuilder;
    }
    return partBuilder.setNodeAction(NodeAction.create(target.obtainCopy(), actionId));
  }

  /** Copies target, caller retains ownership. */
  public static Part.Builder nodeAction(AccessibilityNodeInfoCompat target, int actionId) {
    Part.Builder partBuilder = Part.builder();
    if (target == null) {
      return partBuilder;
    }
    return partBuilder.setNodeAction(
        NodeAction.create(AccessibilityNode.obtainCopy(target), actionId));
  }

  /** Copies node, caller retains ownership. */
  public static Part.Builder scroll(
      AccessibilityNode node, @UserAction int userAction, int nodeAction, @Nullable Source source) {
    return Part.builder()
        .setScroll(
            Scroll.createScroll(
                node.obtainCopy(), /* nodeCompat= */ null, userAction, nodeAction, source));
  }

  /** Copies nodeCompat, caller retains ownership. */
  public static Part.Builder scroll(
      AccessibilityNodeInfoCompat nodeCompat,
      @UserAction int userAction,
      int nodeAction,
      @Nullable Source source) {
    return Part.builder()
        .setScroll(
            Scroll.createScroll(
                /* node= */ null,
                AccessibilityNodeInfoCompat.obtain(nodeCompat),
                userAction,
                nodeAction,
                source));
  }

  public static Part.Builder scrollCancelTimeout() {
    return Part.builder().setScroll(Scroll.createCancelTimeout());
  }

  /** Copies target, caller retains ownership. */
  public static Focus.Builder focus(
      AccessibilityNodeInfoCompat target, FocusActionInfo focusActionInfo) {
    return Focus.builder()
        .setAction(Focus.Action.FOCUS)
        .setFocusActionInfo(focusActionInfo)
        .setTarget(AccessibilityNodeInfoCompat.obtain(target));
  }

  public static Focus.Builder focus(Focus.Action action) {
    return Focus.builder().setAction(action);
  }

  /** Copies start, caller retains ownership. */
  public static Part.Builder focusDirectionHtml(
      AccessibilityNodeInfoCompat start,
      int direction,
      String htmlElement,
      FocusActionInfo focusActionInfo) {
    return Part.builder()
        .setFocus(
            Focus.builder()
                .setAction(Focus.Action.HTML_DIRECTION)
                .setStart(AccessibilityNodeInfoCompat.obtain(start))
                .setDirection(direction)
                .setHtmlElementType(htmlElement)
                .setFocusActionInfo(focusActionInfo)
                .build());
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

  /** Copies node, caller retains ownership. */
  public static FocusDirection.Builder directionNavigationFollowTo(
      @Nullable AccessibilityNodeInfoCompat node, @SearchDirection int direction) {
    AccessibilityNodeInfoCompat nodeCopy =
        (node == null) ? null : AccessibilityNodeInfoCompat.obtain(node);
    return FocusDirection.builder()
        .setAction(FocusDirection.Action.FOLLOW)
        .setFollowNode(nodeCopy)
        .setDirection(direction);
  }

  public static FocusDirection.Builder nextHeading(@InputMode int inputMode) {
    return FocusDirection.builder()
        .setAction(FocusDirection.Action.NEXT)
        .setGranularity(CursorGranularity.HEADING)
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

  /** Copies node, caller retains ownership. */
  public static Part.Builder selectionModeOn(AccessibilityNodeInfoCompat node) {
    return Part.builder()
        .setFocusDirection(
            FocusDirection.builder()
                .setAction(FocusDirection.Action.SELECTION_MODE_ON)
                .setSelectionNode(AccessibilityNodeInfoCompat.obtain(node))
                .build());
  }

  public static Part.Builder selectionModeOff() {
    return Part.builder()
        .setFocusDirection(
            FocusDirection.builder().setAction(FocusDirection.Action.SELECTION_MODE_OFF).build());
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Data access methods

  public abstract @Nullable EventId eventId();

  public abstract ImmutableList<Part> sequence();

  /////////////////////////////////////////////////////////////////////////////////////
  // Inner class for feedback-sequence part

  /** Data-structure that holds a variety of feedback types, executed at one time. */
  @AutoValue
  public abstract static class Part {

    public abstract int delayMs();

    public abstract @InterruptGroup int interruptGroup();

    // In the future, may also need to separately set interruptable-level.
    public abstract @InterruptLevel int interruptLevel();

    public abstract String senderName();

    public abstract boolean interruptSoundAndVibration();

    public abstract boolean interruptAllFeedback();

    public abstract boolean stopTts();

    public abstract @Nullable Speech speech();

    // Some redundancy, since Speech.speechOptions can also contain sound and vibration.
    public abstract @Nullable Sound sound();

    public abstract @Nullable Vibration vibration();

    public abstract @Nullable EditText edit();

    public abstract @Nullable NodeAction nodeAction();

    public abstract @Nullable Scroll scroll();

    public abstract @Nullable Focus focus();

    public abstract @Nullable FocusDirection focusDirection();

    public static Builder builder() {
      return new AutoValue_Feedback_Part.Builder()
          // Set default values that are not null.
          .setDelayMs(0)
          .setInterruptGroup(DEFAULT)
          .setInterruptLevel(-1)
          .setSenderName(CLASS_NAME_UNKNOWN)
          .setInterruptSoundAndVibration(false)
          .setInterruptAllFeedback(false)
          .setStopTts(false);
    }

    /** Builder for Feedback.Part */
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

      // Requires interruptLevel.
      public abstract Builder setInterruptGroup(@InterruptGroup int interruptGroup);

      public abstract Builder setInterruptLevel(@InterruptLevel int interruptLevel);

      public abstract Builder setSenderName(@NonNull String senderName);

      public abstract Builder setInterruptSoundAndVibration(boolean interruptSoundAndVibration);

      public abstract Builder setInterruptAllFeedback(boolean interruptAllFeedback);

      // Requires interruptAllFeedback.
      public abstract Builder setStopTts(boolean stopTts);

      public abstract Builder setSpeech(Speech speech);

      public abstract Builder setSound(Sound sound);

      public abstract Builder setVibration(Vibration vibration);

      /** Takes ownership of edit, and recycles it. */
      public abstract Builder setEdit(EditText edit);

      /** Takes ownership of nodeAction, and recycles it. */
      public abstract Builder setNodeAction(NodeAction nodeAction);

      public abstract Builder setScroll(Scroll scroll);

      /** Takes ownership of focus, and recycles it. */
      public abstract Builder setFocus(Focus focus);

      public abstract Builder setFocusDirection(FocusDirection focusDirection);

      public abstract Part build();
    }

    public void recycle() {
      EditText edit = edit();
      if (edit() != null) {
        edit.recycle();
      }

      NodeAction nodeAction = nodeAction();
      if (nodeAction != null) {
        nodeAction.recycle();
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
              StringBuilderUtils.optionalText("senderName", "null"),
              StringBuilderUtils.optionalTag(
                  "interruptSoundAndVibration", interruptSoundAndVibration()),
              StringBuilderUtils.optionalTag("interruptAllFeedback", interruptAllFeedback()),
              StringBuilderUtils.optionalTag("stopTts", stopTts()),
              StringBuilderUtils.optionalSubObj("speech", speech()),
              StringBuilderUtils.optionalSubObj("sound", sound()),
              StringBuilderUtils.optionalSubObj("vibration", vibration()),
              StringBuilderUtils.optionalSubObj("edit", edit()),
              StringBuilderUtils.optionalSubObj("nodeAction", nodeAction()),
              StringBuilderUtils.optionalSubObj("scroll", scroll()),
              StringBuilderUtils.optionalSubObj("focus", focus()),
              StringBuilderUtils.optionalSubObj("focusDirection", focusDirection()));
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////
  // Inner classes for various feedback types

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
    }

    public static Speech create(CharSequence text, @Nullable SpeakOptions options) {
      return new AutoValue_Feedback_Speech(Speech.Action.SPEAK, text, options);
    }

    public static Speech create(Speech.Action action) {
      return new AutoValue_Feedback_Speech(action, null, null);
    }

    public abstract Speech.Action action();

    public abstract @Nullable CharSequence text();

    public abstract @Nullable SpeakOptions options();
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

    /** Builder for Focus feedback data */
    @AutoValue.Builder
    public abstract static class Builder {
      /** Takes ownership of node, and recycles it. */
      public abstract Builder setNode(AccessibilityNodeInfoCompat node);

      public abstract Builder setAction(Action action);

      public abstract Builder setStopSelecting(boolean stopSelecting);

      public abstract Builder setText(@Nullable CharSequence text);

      public abstract EditText build();
    }

    public void recycle() {
      AccessibilityNodeInfoUtils.recycleNodes(node());
    }
  }

  /** Inner data-structure for performing an action on a node. */
  @AutoValue
  public abstract static class NodeAction {

    /** Takes ownership of target, and recycles it. */
    public static NodeAction create(AccessibilityNode target, int actionId) {
      return new AutoValue_Feedback_NodeAction(target, actionId);
    }

    public abstract AccessibilityNode target();

    public abstract int actionId();

    public void recycle() {
      AccessibilityNode.recycle("Feedback.NodeAction.recycle()", target());
    }
  }

  /** Inner data-structure for scrolling feedback. */
  @AutoValue
  public abstract static class Scroll {

    /** Types of exclusive scroll actions. */
    public enum Action {
      SCROLL,
      CANCEL_TIMEOUT;
    }

    public static Scroll createScroll(
        @Nullable AccessibilityNode node,
        @Nullable AccessibilityNodeInfoCompat nodeCompat,
        @UserAction int userAction,
        int nodeAction,
        @Nullable Source source) {
      return new AutoValue_Feedback_Scroll(
          Scroll.Action.SCROLL, node, nodeCompat, userAction, nodeAction, source);
    }

    public static Scroll createCancelTimeout() {
      return new AutoValue_Feedback_Scroll(
          Scroll.Action.CANCEL_TIMEOUT,
          /* node= */ null,
          /* nodeCompat= */ null,
          ScrollEventInterpreter.ACTION_UNKNOWN,
          NODE_ACTION_UNKNOWN,
          /* source= */ null);
    }

    public abstract Scroll.Action action();

    public abstract @Nullable AccessibilityNode node();

    public abstract @Nullable AccessibilityNodeInfoCompat nodeCompat();

    public abstract @UserAction int userAction();

    public abstract int nodeAction();

    public abstract @Nullable Source source();

    public void recycle() {
      AccessibilityNode.recycle("Feedback.Scroll.recycle()", node());
      AccessibilityNodeInfoUtils.recycleNodes(nodeCompat());
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
      CLICK,
      LONG_CLICK,
      CLICK_ANCESTOR,
      HTML_DIRECTION, // Requires start, direction, htmlElementType, focusActionInfo.
      SEARCH_FROM_TOP, // Requires searchKeyword.
      SEARCH_AGAIN;
    }

    public abstract @Nullable AccessibilityNodeInfoCompat start();

    public abstract @Nullable AccessibilityNodeInfoCompat target();

    public abstract @SearchDirection int direction();

    public abstract @Nullable FocusActionInfo focusActionInfo();

    public abstract @Nullable NavigationAction navigationAction();

    public abstract @Nullable String htmlElementType();

    public abstract @Nullable CharSequence searchKeyword();

    public abstract boolean forceRefocus();

    public abstract Focus.Action action();

    public abstract @Nullable AccessibilityNodeInfoCompat scrolledNode();

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
      /** Takes ownership of start, and recycles it. */
      public abstract Builder setStart(@Nullable AccessibilityNodeInfoCompat start);

      /** Takes ownership of target, and recycles it. */
      public abstract Builder setTarget(@Nullable AccessibilityNodeInfoCompat target);

      public abstract Builder setDirection(@SearchDirection int direction);

      public abstract Builder setFocusActionInfo(@Nullable FocusActionInfo focusActionInfo);

      public abstract Builder setNavigationAction(@Nullable NavigationAction navigationAction);

      public abstract Builder setHtmlElementType(@Nullable String htmlElementType);

      public abstract Builder setSearchKeyword(@Nullable CharSequence searchKeyword);

      public abstract Builder setForceRefocus(boolean forceRefocus);

      public abstract Builder setAction(Focus.Action action);

      /** Takes ownership of node, and recycles it. */
      public abstract Builder setScrolledNode(@Nullable AccessibilityNodeInfoCompat scrolledNode);

      public abstract Focus build();
    }

    public void recycle() {
      AccessibilityNodeInfoUtils.recycleNodes(start(), target(), scrolledNode());
    }

    @Override
    public final String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalField("action", action()),
          StringBuilderUtils.optionalSubObj("start", start()),
          StringBuilderUtils.optionalSubObj("target", target()),
          StringBuilderUtils.optionalInt("direction", direction(), SEARCH_FOCUS_UNKNOWN),
          StringBuilderUtils.optionalSubObj("focusActionInfo", focusActionInfo()),
          StringBuilderUtils.optionalSubObj("navigationAction", navigationAction()),
          StringBuilderUtils.optionalText("htmlElementType", htmlElementType()),
          StringBuilderUtils.optionalText("searchKeyword", searchKeyword()),
          StringBuilderUtils.optionalTag("forceRefocus", forceRefocus()),
          StringBuilderUtils.optionalSubObj("scrolledNode", scrolledNode()));
    }
  }

  /////////////////////////////////////////////////////////////////////////////////////
  // Focus directional navigation feedback

  /** Inner data-structure for focus directional navigation. */
  @AutoValue
  public abstract static class FocusDirection {

    /** Types of exclusive focus-direction actions, mostly without additional feedback data. */
    public enum Action {
      FOLLOW,
      NEXT,
      NEXT_PAGE,
      PREVIOUS_PAGE,
      TOP,
      BOTTOM,
      SET_GRANULARITY,
      SAVE_GRANULARITY,
      APPLY_SAVED_GRANULARITY,
      CLEAR_SAVED_GRANULARITY,
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
    public abstract @Nullable AccessibilityNodeInfoCompat followNode();

    public abstract boolean defaultToInputFocus();

    public abstract boolean scroll();

    public abstract boolean wrap();

    public abstract boolean toWindow();

    public abstract @InputMode int inputMode();

    public abstract @Nullable CursorGranularity granularity();

    public abstract boolean fromUser();

    public abstract @Nullable AccessibilityNodeInfoCompat selectionNode();

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
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setDirection(@SearchDirection int direction);

      public abstract Builder setHtmlTargetType(@TargetType int htmlTargetType);

      /** Takes ownership of node, and recycles it. */
      public abstract Builder setFollowNode(@Nullable AccessibilityNodeInfoCompat followNode);

      public abstract Builder setDefaultToInputFocus(boolean defaultToInputFocus);

      public abstract Builder setScroll(boolean scroll);

      public abstract Builder setWrap(boolean wrap);

      public abstract Builder setToWindow(boolean toWindow);

      public abstract Builder setInputMode(@InputMode int inputMode);

      public abstract Builder setGranularity(@Nullable CursorGranularity granularity);

      public abstract Builder setFromUser(boolean fromUser);

      public abstract Builder setAction(FocusDirection.Action action);

      /** Takes ownership of selectionNode, and recycles it. */
      public abstract Builder setSelectionNode(@Nullable AccessibilityNodeInfoCompat selectionNode);

      public abstract FocusDirection build();
    }

    public void recycle() {
      AccessibilityNodeInfoUtils.recycleNodes(followNode(), selectionNode());
    }

    @Override
    public final String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalField("action", action()),
          StringBuilderUtils.optionalInt("direction", direction(), SEARCH_FOCUS_UNKNOWN),
          StringBuilderUtils.optionalInt("htmlTargetType", htmlTargetType(), TARGET_DEFAULT),
          StringBuilderUtils.optionalSubObj("followNode", followNode()),
          StringBuilderUtils.optionalTag("defaultToInputFocus", defaultToInputFocus()),
          StringBuilderUtils.optionalTag("scroll", scroll()),
          StringBuilderUtils.optionalTag("wrap", wrap()),
          StringBuilderUtils.optionalTag("toWindow", toWindow()),
          StringBuilderUtils.optionalInt("inputMode", inputMode(), INPUT_MODE_UNKNOWN),
          StringBuilderUtils.optionalField("granularity", granularity()),
          StringBuilderUtils.optionalTag("fromUser", fromUser()),
          StringBuilderUtils.optionalSubObj("selectionNode", selectionNode()));
    }
  }

  // TODO: Add feedback types: braille, UI-action.

  static String groupIdToString(int groupId) {
    String groupName = Feedback.CLASS_NAME_UNKNOWN;
    switch (groupId) {
      case HINT:
        groupName = "HINT";
        break;
      case GESTURE_VIBRATION:
        groupName = "GESTURE_VIBRATION";
        break;
      case CURSOR_STATE:
        groupName = "CURSOR_STATE";
        break;
    }
    return groupName;
  }
}

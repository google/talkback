/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement.record;

import androidx.annotation.IntDef;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/** The class contains extra information about accessibility focus action. */
public class FocusActionInfo {
  /** Type of the source action of focus, it represents what triggers the Accessibility focus. */
  @IntDef({
    UNKNOWN,
    MANUAL_SCROLL,
    TOUCH_EXPLORATION,
    FOCUS_SYNCHRONIZATION,
    LOGICAL_NAVIGATION,
    SCREEN_STATE_CHANGE,
    ENSURE_ON_SCREEN
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface SourceAction {}

  /** Triggered by unknown reasons. */
  public static final int UNKNOWN = 0;
  /** Triggered by manually scroll from users. */
  public static final int MANUAL_SCROLL = 1;
  /** Triggered by touching the screen manually. */
  public static final int TOUCH_EXPLORATION = 2;
  /** Triggered by changed input focus. */
  public static final int FOCUS_SYNCHRONIZATION = 3;
  /** Triggered by navigating by gestures or continuous reading mode. */
  public static final int LOGICAL_NAVIGATION = 4;
  /** Triggered by window changes. It supports the initial focus strategy. */
  public static final int SCREEN_STATE_CHANGE = 5;
  /**
   * Fallback plan to keep searching focus when detecting main UI changes or no UI changes for a
   * while. It supports the initial focus strategy.
   */
  public static final int ENSURE_ON_SCREEN = 6;

  /**
   * Type of initial focus for source {@code SCREEN_STATE_CHANGE} and {@code ENSURE_ON_SCREEN}. It
   * represents the strategy to find the initial focus.
   */
  @IntDef({
    UNDEFINED,
    FIRST_FOCUSABLE_NODE,
    RESTORED_LAST_FOCUS,
    SYNCED_INPUT_FOCUS,
    REQUESTED_INITIAL_NODE,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface InitialFocusType {}

  /** Default type for other sources without initial focus strategy. */
  public static final int UNDEFINED = 0;
  /** Initial focus from the first focusable node. */
  public static final int FIRST_FOCUSABLE_NODE = 1;
  /**
   * Initial focus from restoring the last focus. The main strategy is from {@link
   * FocusActionRecord#getFocusableNodeFromFocusRecord}.
   */
  public static final int RESTORED_LAST_FOCUS = 2;
  /** Initial focus from syncing the input focus. */
  public static final int SYNCED_INPUT_FOCUS = 3;
  /**
   * Initial focus from request by {@link
   * AccessibilityNodeInfoCompat#hasRequestInitialAccessibilityFocus()}.
   */
  public static final int REQUESTED_INITIAL_NODE = 4;

  @SourceAction public final int sourceAction;
  public final boolean isFromRefocusAction;
  public final NavigationAction navigationAction;
  @InitialFocusType public final int initialFocusType;
  public final boolean forceMuteFeedback;

  public boolean forceFeedbackEvenIfAudioPlaybackActive() {
    return sourceAction != UNKNOWN;
  }

  public boolean forceFeedbackEvenIfMicrophoneActive() {
    return sourceAction != UNKNOWN;
  }

  // TODO: have better name for voice recognition/dictation than ssb
  public boolean forceFeedbackEvenIfSsbActive() {
    return (sourceAction == TOUCH_EXPLORATION) || (sourceAction == LOGICAL_NAVIGATION);
  }

  public boolean isSourceEnsureOnScreen() {
    return sourceAction == ENSURE_ON_SCREEN;
  }

  public static String sourceActionToString(@SourceAction int sourceAction) {
    switch (sourceAction) {
      case MANUAL_SCROLL:
        return "MANUAL_SCROLL";
      case TOUCH_EXPLORATION:
        return "TOUCH_EXPLORATION";
      case FOCUS_SYNCHRONIZATION:
        return "FOCUS_SYNCHRONIZATION";
      case LOGICAL_NAVIGATION:
        return "LOGICAL_NAVIGATION";
      case SCREEN_STATE_CHANGE:
        return "SCREEN_STATE_CHANGE";
      case ENSURE_ON_SCREEN:
        return "ENSURE_ON_SCREEN";
      case UNKNOWN:
        // Fall down
      default:
        return "UNKNOWN";
    }
  }

  private static String initialFocusTypeToString(@InitialFocusType int initialFocusType) {
    switch (initialFocusType) {
      case FIRST_FOCUSABLE_NODE:
        return "FIRST_FOCUSABLE_NODE";
      case RESTORED_LAST_FOCUS:
        return "RESTORED_LAST_FOCUS";
      case SYNCED_INPUT_FOCUS:
        return "SYNCED_INPUT_FOCUS";
      case REQUESTED_INITIAL_NODE:
        return "REQUESTED_INITIAL_NODE";
      case UNDEFINED:
        // fall through
      default:
        return "UNDEFINED";
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builds {@link FocusActionInfo}. */
  public static class Builder {
    @SourceAction private int sourceAction = UNKNOWN;
    private boolean isFromRefocusAction = false;
    private @Nullable NavigationAction navigationAction = null;
    @InitialFocusType private int initialFocusType = UNDEFINED;
    private boolean forceMuteFeedback = false;

    public Builder() {}

    public Builder(FocusActionInfo focusActionInfo) {
      sourceAction = focusActionInfo.sourceAction;
      isFromRefocusAction = focusActionInfo.isFromRefocusAction;
      navigationAction = focusActionInfo.navigationAction;
      initialFocusType = focusActionInfo.initialFocusType;
      forceMuteFeedback = focusActionInfo.forceMuteFeedback;
    }

    public FocusActionInfo build() {
      return new FocusActionInfo(this);
    }

    @CanIgnoreReturnValue
    public Builder setSourceAction(@SourceAction int sourceAction) {
      this.sourceAction = sourceAction;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setIsFromRefocusAction(boolean isFromRefocusAction) {
      this.isFromRefocusAction = isFromRefocusAction;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setNavigationAction(NavigationAction navigationAction) {
      this.navigationAction = navigationAction;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setInitialFocusType(@InitialFocusType int initialFocusType) {
      this.initialFocusType = initialFocusType;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setForceMuteFeedback(boolean forceMuteFeedback) {
      this.forceMuteFeedback = forceMuteFeedback;
      return this;
    }
  }

  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        "FocusActionInfo{",
        StringBuilderUtils.optionalField("sourceAction", sourceActionToString(sourceAction)),
        StringBuilderUtils.optionalTag("isFromRefocusAction", isFromRefocusAction),
        StringBuilderUtils.optionalSubObj("navigationAction", navigationAction),
        StringBuilderUtils.optionalField(
            "initialFocusType", initialFocusTypeToString(initialFocusType)),
        StringBuilderUtils.optionalTag("forceMuteFeedback", forceMuteFeedback),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfAudioPlaybackActive", forceFeedbackEvenIfAudioPlaybackActive()),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfMicrophoneActive", forceFeedbackEvenIfMicrophoneActive()),
        StringBuilderUtils.optionalTag(
            "forceFeedbackEvenIfSsbActive", forceFeedbackEvenIfSsbActive()),
        "}");
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        sourceAction, isFromRefocusAction, navigationAction, initialFocusType, forceMuteFeedback);
  }

  @Override
  public boolean equals(@androidx.annotation.Nullable Object otherObject) {
    if (!(otherObject instanceof FocusActionInfo)) {
      return false;
    }
    FocusActionInfo other = (FocusActionInfo) otherObject;
    return this.forceMuteFeedback == other.forceMuteFeedback
        && this.isFromRefocusAction == other.isFromRefocusAction
        && this.initialFocusType == other.initialFocusType
        && this.sourceAction == other.sourceAction
        && Objects.equals(this.navigationAction, other.navigationAction);
  }

  private FocusActionInfo(Builder builder) {
    sourceAction = builder.sourceAction;
    isFromRefocusAction = builder.isFromRefocusAction;
    navigationAction = builder.navigationAction;
    initialFocusType = builder.initialFocusType;
    forceMuteFeedback = builder.forceMuteFeedback;
  }

  /** Modifies accepted {@link FocusActionInfo}. */
  public interface Modifier {

    /**
     * Modifies {@link FocusActionInfo}.
     *
     * @return The original {@code info} if the modification doesn't apply. Otherwise return a
     *     different {@link FocusActionInfo} object.
     */
    FocusActionInfo modify(FocusActionInfo info);
  }
}

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

package com.google.android.accessibility.talkback.focusmanagement.action;

import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_UNKNOWN;

import androidx.annotation.IntDef;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.monitor.InputModeTracker;
import com.google.android.accessibility.utils.monitor.InputModeTracker.InputMode;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirectionOrUnknown;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A class defining directional navigation action performed by user. */
public class NavigationAction {

  public static final int UNKNOWN = 0;
  public static final int DIRECTIONAL_NAVIGATION = 1;
  public static final int JUMP_TO_TOP = 2;
  public static final int JUMP_TO_BOTTOM = 3;
  public static final int SCROLL_FORWARD = 4;
  public static final int SCROLL_BACKWARD = 5;
  public static final int SCROLL_UP = 6;
  public static final int SCROLL_DOWN = 7;
  public static final int SCROLL_LEFT = 8;
  public static final int SCROLL_RIGHT = 9;

  /** navigation action types. */
  @IntDef({
    UNKNOWN,
    DIRECTIONAL_NAVIGATION,
    JUMP_TO_TOP,
    JUMP_TO_BOTTOM,
    SCROLL_FORWARD,
    SCROLL_BACKWARD,
    SCROLL_UP,
    SCROLL_DOWN,
    SCROLL_LEFT,
    SCROLL_RIGHT
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface ActionType {}

  @ActionType public final int actionType;
  /** Defines what to be focused for {@link #DIRECTIONAL_NAVIGATION} actions. */
  @TargetType public final int targetType;

  @InputMode public final int inputMode;
  /** Defines direction for {@link #DIRECTIONAL_NAVIGATION} actions. */
  @SearchDirectionOrUnknown public final int searchDirection;

  public final boolean shouldWrap;
  public final boolean shouldScroll;
  public final boolean useInputFocusAsPivotIfEmpty;
  public final CursorGranularity originalNavigationGranularity;
  public final int autoScrollAttempt;
  public final int prevScrollDeltaSumX;
  public final int prevScrollDeltaSumY;

  private NavigationAction(Builder builder) {
    actionType = builder.actionType;
    searchDirection = builder.searchDirection;
    targetType = builder.targetType;
    inputMode = builder.inputMode;
    shouldWrap = builder.shouldWrap;
    shouldScroll = builder.shouldScroll;
    useInputFocusAsPivotIfEmpty = builder.useInputFocusAsPivotIfEmpty;
    originalNavigationGranularity = builder.originalNavigationGranularity;
    autoScrollAttempt = builder.autoScrollAttempt;
    prevScrollDeltaSumX = builder.prevScrollDeltaSumX;
    prevScrollDeltaSumY = builder.prevScrollDeltaSumY;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        actionType,
        searchDirection,
        targetType,
        inputMode,
        shouldWrap,
        shouldScroll,
        useInputFocusAsPivotIfEmpty,
        originalNavigationGranularity,
        autoScrollAttempt,
        prevScrollDeltaSumX,
        prevScrollDeltaSumY);
  }

  @Override
  public boolean equals(@androidx.annotation.Nullable Object otherObject) {
    if (!(otherObject instanceof NavigationAction)) {
      return false;
    }
    NavigationAction other = (NavigationAction) otherObject;
    return this.actionType == other.actionType
        && this.searchDirection == other.searchDirection
        && this.targetType == other.targetType
        && this.inputMode == other.inputMode
        && this.shouldWrap == other.shouldWrap
        && this.shouldScroll == other.shouldScroll
        && this.useInputFocusAsPivotIfEmpty == other.useInputFocusAsPivotIfEmpty
        && this.originalNavigationGranularity == other.originalNavigationGranularity
        && this.autoScrollAttempt == other.autoScrollAttempt
        && this.prevScrollDeltaSumX == other.prevScrollDeltaSumX
        && this.prevScrollDeltaSumY == other.prevScrollDeltaSumY;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("NavigationAction{");
    sb.append("actionType=").append(actionTypeToString(actionType));
    sb.append(", targetType=").append(NavigationTarget.targetTypeToString(targetType));
    sb.append(", inputMode=").append(InputModeTracker.inputModeToString(inputMode));
    sb.append(", searchDirection=")
        .append(TraversalStrategyUtils.directionToString(searchDirection));
    sb.append(", shouldWrap=").append(shouldWrap);
    sb.append(", shouldScroll=").append(shouldScroll);
    sb.append(", useInputFocusAsPivotIfEmpty=").append(useInputFocusAsPivotIfEmpty);
    sb.append(", originalNavigationGranularity=").append(originalNavigationGranularity);
    sb.append(", autoScrollAttempt=").append(autoScrollAttempt);
    sb.append(", prevScrollDeltaSumX=").append(prevScrollDeltaSumX);
    sb.append(", prevScrollDeltaSumY=").append(prevScrollDeltaSumY);
    sb.append('}');
    return sb.toString();
  }

  public static String actionTypeToString(@ActionType int actionType) {
    switch (actionType) {
      case DIRECTIONAL_NAVIGATION:
        return "DIRECTIONAL_NAVIGATION";
      case JUMP_TO_TOP:
        return "JUMP_TO_TOP";
      case JUMP_TO_BOTTOM:
        return "JUMP_TO_BOTTOM";
      case SCROLL_FORWARD:
        return "SCROLL_FORWARD";
      case SCROLL_BACKWARD:
        return "SCROLL_BACKWARD";
      case SCROLL_UP:
        return "SCROLL_UP";
      case SCROLL_DOWN:
        return "SCROLL_DOWN";
      case SCROLL_LEFT:
        return "SCROLL_LEFT";
      case SCROLL_RIGHT:
        return "SCROLL_RIGHT";
      default:
        return "UNKNOWN";
    }
  }

  /** Builds {@link NavigationAction}. */
  public static final class Builder {
    @ActionType private int actionType = UNKNOWN;
    @TargetType private int targetType = NavigationTarget.TARGET_DEFAULT;
    @InputMode private int inputMode = INPUT_MODE_UNKNOWN;
    @SearchDirectionOrUnknown private int searchDirection = TraversalStrategy.SEARCH_FOCUS_UNKNOWN;
    private boolean shouldWrap = false;
    private boolean shouldScroll = false;
    private boolean useInputFocusAsPivotIfEmpty = false;
    private @Nullable CursorGranularity originalNavigationGranularity = null;
    private int autoScrollAttempt = 0;
    private int prevScrollDeltaSumX = 0;
    private int prevScrollDeltaSumY = 0;

    public static Builder copy(NavigationAction action) {
      Builder builder = new Builder();
      builder.actionType = action.actionType;
      builder.searchDirection = action.searchDirection;
      builder.targetType = action.targetType;
      builder.inputMode = action.inputMode;
      builder.shouldWrap = action.shouldWrap;
      builder.shouldScroll = action.shouldScroll;
      builder.useInputFocusAsPivotIfEmpty = action.useInputFocusAsPivotIfEmpty;
      builder.originalNavigationGranularity = action.originalNavigationGranularity;
      builder.autoScrollAttempt = action.autoScrollAttempt;
      builder.prevScrollDeltaSumX = action.prevScrollDeltaSumX;
      builder.prevScrollDeltaSumY = action.prevScrollDeltaSumY;
      return builder;
    }

    public NavigationAction build() {
      return new NavigationAction(this);
    }

    @CanIgnoreReturnValue
    public Builder setAction(@ActionType int actionType) {
      this.actionType = actionType;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setDirection(@SearchDirectionOrUnknown int searchDirection) {
      this.searchDirection = searchDirection;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTarget(@TargetType int targetType) {
      this.targetType = targetType;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setInputMode(@InputMode int inputMode) {
      this.inputMode = inputMode;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setShouldWrap(boolean shouldWrap) {
      this.shouldWrap = shouldWrap;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setShouldScroll(boolean shouldScroll) {
      this.shouldScroll = shouldScroll;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setUseInputFocusAsPivotIfEmpty(boolean useInputFocusAsPivotIfEmpty) {
      this.useInputFocusAsPivotIfEmpty = useInputFocusAsPivotIfEmpty;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setOriginalNavigationGranularity(
        CursorGranularity originalNavigationGranularity) {
      this.originalNavigationGranularity = originalNavigationGranularity;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setAutoScrollAttempt(int autoScrollAttempt) {
      this.autoScrollAttempt = autoScrollAttempt;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPrevScrollDeltaSumX(int prevScrollDeltaSumX) {
      this.prevScrollDeltaSumX = prevScrollDeltaSumX;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setPrevScrollDeltaSumY(int prevScrollDeltaSumY) {
      this.prevScrollDeltaSumY = prevScrollDeltaSumY;
      return this;
    }
  }
}

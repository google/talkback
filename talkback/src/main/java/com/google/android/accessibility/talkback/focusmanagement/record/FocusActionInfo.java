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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.Nullable;

/** The class contains extra information about accessibility focus action. */
public class FocusActionInfo {
  /** Type of the source focus action. */
  @IntDef({
    UNKNOWN,
    MANUAL_SCROLL,
    TOUCH_EXPLORATION,
    FOCUS_SYNCHRONIZATION,
    LOGICAL_NAVIGATION,
    SCREEN_STATE_CHANGE
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface SourceAction {}

  public static final int UNKNOWN = 0;
  public static final int MANUAL_SCROLL = 1;
  public static final int TOUCH_EXPLORATION = 2;
  public static final int FOCUS_SYNCHRONIZATION = 3;
  public static final int LOGICAL_NAVIGATION = 4;
  public static final int SCREEN_STATE_CHANGE = 5;

  /** Type of initial focus after screen state change. */
  @IntDef({
    UNDEFINED,
    FIRST_FOCUSABLE_NODE,
    RESTORED_LAST_FOCUS,
    SYNCED_EDIT_TEXT,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface InitialFocusType {}

  public static final int UNDEFINED = 0;
  public static final int FIRST_FOCUSABLE_NODE = 1;
  public static final int RESTORED_LAST_FOCUS = 2;
  public static final int SYNCED_EDIT_TEXT = 3;

  @SourceAction public final int sourceAction;
  public final boolean isFromRefocusAction;
  public final NavigationAction navigationAction;
  @InitialFocusType public final int initialFocusType;
  public final boolean forceMuteFeedback;

  public boolean isForcedFeedbackAudioPlaybackActive() {
    return sourceAction != UNKNOWN;
  }

  public boolean isForcedFeedbackMicrophoneActive() {
    return sourceAction != UNKNOWN;
  }

  // TODO: have better name for voice recognition/dictation than ssb
  public boolean isForcedFeedbackSsbActive() {
    return (sourceAction == TOUCH_EXPLORATION) || (sourceAction == LOGICAL_NAVIGATION);
  }

  private static String sourceActionToString(@SourceAction int sourceAction) {
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
      case SYNCED_EDIT_TEXT:
        return "SYNCED_EDIT_TEXT";
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
    @Nullable private NavigationAction navigationAction = null;
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

    public Builder setSourceAction(@SourceAction int sourceAction) {
      this.sourceAction = sourceAction;
      return this;
    }

    public Builder setIsFromRefocusAction(boolean isFromRefocusAction) {
      this.isFromRefocusAction = isFromRefocusAction;
      return this;
    }

    public Builder setNavigationAction(NavigationAction navigationAction) {
      this.navigationAction = navigationAction;
      return this;
    }

    public Builder setInitialFocusType(@InitialFocusType int initialFocusType) {
      this.initialFocusType = initialFocusType;
      return this;
    }

    public Builder forceMuteFeedback() {
      forceMuteFeedback = true;
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
            "isForcedFeedbackAudioPlaybackActive", isForcedFeedbackAudioPlaybackActive()),
        StringBuilderUtils.optionalTag(
            "isForcedFeedbackMicrophoneActive", isForcedFeedbackMicrophoneActive()),
        StringBuilderUtils.optionalTag("isForcedFeedbackSsbActive", isForcedFeedbackSsbActive()),
        "}");
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

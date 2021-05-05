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

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import org.checkerframework.checker.nullness.qual.Nullable;
import javax.annotation.Generated;

// This file is normally auto-generated using the @AutoValue processor.  But
// that operation has been failing on the gradle-based build, so this file is
// committed into version control for now.
@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_FocusDirection extends Feedback.FocusDirection {

  private final int direction;

  private final int htmlTargetType;

  private final @Nullable AccessibilityNodeInfoCompat targetNode;

  private final boolean defaultToInputFocus;

  private final boolean scroll;

  private final boolean wrap;

  private final boolean toWindow;

  private final int inputMode;

  private final @Nullable CursorGranularity granularity;

  private final boolean fromUser;

  private final Feedback.FocusDirection.Action action;

  private AutoValue_Feedback_FocusDirection(
      int direction,
      int htmlTargetType,
      @Nullable AccessibilityNodeInfoCompat targetNode,
      boolean defaultToInputFocus,
      boolean scroll,
      boolean wrap,
      boolean toWindow,
      int inputMode,
      @Nullable CursorGranularity granularity,
      boolean fromUser,
      Feedback.FocusDirection.Action action) {
    this.direction = direction;
    this.htmlTargetType = htmlTargetType;
    this.targetNode = targetNode;
    this.defaultToInputFocus = defaultToInputFocus;
    this.scroll = scroll;
    this.wrap = wrap;
    this.toWindow = toWindow;
    this.inputMode = inputMode;
    this.granularity = granularity;
    this.fromUser = fromUser;
    this.action = action;
  }

  @TraversalStrategy.SearchDirection
  @Override
  public int direction() {
    return direction;
  }

  @NavigationTarget.TargetType
  @Override
  public int htmlTargetType() {
    return htmlTargetType;
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat targetNode() {
    return targetNode;
  }

  @Override
  public boolean defaultToInputFocus() {
    return defaultToInputFocus;
  }

  @Override
  public boolean scroll() {
    return scroll;
  }

  @Override
  public boolean wrap() {
    return wrap;
  }

  @Override
  public boolean toWindow() {
    return toWindow;
  }

  @InputModeManager.InputMode
  @Override
  public int inputMode() {
    return inputMode;
  }

  @Override
  public @Nullable CursorGranularity granularity() {
    return granularity;
  }

  @Override
  public boolean fromUser() {
    return fromUser;
  }

  @Override
  public Feedback.FocusDirection.Action action() {
    return action;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.FocusDirection) {
      Feedback.FocusDirection that = (Feedback.FocusDirection) o;
      return this.direction == that.direction()
          && this.htmlTargetType == that.htmlTargetType()
          && (this.targetNode == null ? that.targetNode() == null : this.targetNode.equals(that.targetNode()))
          && this.defaultToInputFocus == that.defaultToInputFocus()
          && this.scroll == that.scroll()
          && this.wrap == that.wrap()
          && this.toWindow == that.toWindow()
          && this.inputMode == that.inputMode()
          && (this.granularity == null ? that.granularity() == null : this.granularity.equals(that.granularity()))
          && this.fromUser == that.fromUser()
          && this.action.equals(that.action());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= direction;
    h$ *= 1000003;
    h$ ^= htmlTargetType;
    h$ *= 1000003;
    h$ ^= (targetNode == null) ? 0 : targetNode.hashCode();
    h$ *= 1000003;
    h$ ^= defaultToInputFocus ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= scroll ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= wrap ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= toWindow ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= inputMode;
    h$ *= 1000003;
    h$ ^= (granularity == null) ? 0 : granularity.hashCode();
    h$ *= 1000003;
    h$ ^= fromUser ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    return h$;
  }

  static final class Builder extends Feedback.FocusDirection.Builder {
    private Integer direction;
    private Integer htmlTargetType;
    private @Nullable AccessibilityNodeInfoCompat targetNode;
    private Boolean defaultToInputFocus;
    private Boolean scroll;
    private Boolean wrap;
    private Boolean toWindow;
    private Integer inputMode;
    private @Nullable CursorGranularity granularity;
    private Boolean fromUser;
    private Feedback.FocusDirection.Action action;
    Builder() {
    }
    @Override
    public Feedback.FocusDirection.Builder setDirection(int direction) {
      this.direction = direction;
      return this;
    }
    @Override
    public Feedback.FocusDirection.Builder setHtmlTargetType(int htmlTargetType) {
      this.htmlTargetType = htmlTargetType;
      return this;
    }
    @Override
    public Feedback.FocusDirection.Builder setTargetNode(@Nullable AccessibilityNodeInfoCompat targetNode) {
      this.targetNode = targetNode;
      return this;
    }
    @Override
    @Nullable AccessibilityNodeInfoCompat targetNode() {
      return targetNode;
    }
    @Override
    public Feedback.FocusDirection.Builder setDefaultToInputFocus(boolean defaultToInputFocus) {
      this.defaultToInputFocus = defaultToInputFocus;
      return this;
    }
    @Override
    public Feedback.FocusDirection.Builder setScroll(boolean scroll) {
      this.scroll = scroll;
      return this;
    }
    @Override
    public Feedback.FocusDirection.Builder setWrap(boolean wrap) {
      this.wrap = wrap;
      return this;
    }
    @Override
    public Feedback.FocusDirection.Builder setToWindow(boolean toWindow) {
      this.toWindow = toWindow;
      return this;
    }
    @Override
    public Feedback.FocusDirection.Builder setInputMode(int inputMode) {
      this.inputMode = inputMode;
      return this;
    }
    @Override
    public Feedback.FocusDirection.Builder setGranularity(@Nullable CursorGranularity granularity) {
      this.granularity = granularity;
      return this;
    }
    @Override
    public Feedback.FocusDirection.Builder setFromUser(boolean fromUser) {
      this.fromUser = fromUser;
      return this;
    }
    @Override
    public Feedback.FocusDirection.Builder setAction(Feedback.FocusDirection.Action action) {
      if (action == null) {
        throw new NullPointerException("Null action");
      }
      this.action = action;
      return this;
    }
    @Override
    Feedback.FocusDirection autoBuild() {
      String missing = "";
      if (this.direction == null) {
        missing += " direction";
      }
      if (this.htmlTargetType == null) {
        missing += " htmlTargetType";
      }
      if (this.defaultToInputFocus == null) {
        missing += " defaultToInputFocus";
      }
      if (this.scroll == null) {
        missing += " scroll";
      }
      if (this.wrap == null) {
        missing += " wrap";
      }
      if (this.toWindow == null) {
        missing += " toWindow";
      }
      if (this.inputMode == null) {
        missing += " inputMode";
      }
      if (this.fromUser == null) {
        missing += " fromUser";
      }
      if (this.action == null) {
        missing += " action";
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_Feedback_FocusDirection(
          this.direction,
          this.htmlTargetType,
          this.targetNode,
          this.defaultToInputFocus,
          this.scroll,
          this.wrap,
          this.toWindow,
          this.inputMode,
          this.granularity,
          this.fromUser,
          this.action);
    }
  }

}

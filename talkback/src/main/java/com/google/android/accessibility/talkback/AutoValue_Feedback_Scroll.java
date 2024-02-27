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
import com.google.android.accessibility.talkback.Feedback.Scroll;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter.ScrollTimeout;
import com.google.android.accessibility.utils.output.ScrollActionRecord;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

// This file is normally auto-generated using the @AutoValue processor.  But
// that operation has been failing on the gradle-based build, so this file is
// committed into version control for now.  Also read go/talkback-for-p section on AutoValue.
@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_Scroll extends Feedback.Scroll {

  private final Feedback.Scroll.Action action;

  private final @Nullable AccessibilityNode node;

  private final @Nullable AccessibilityNodeInfoCompat nodeCompat;

  private final @Nullable AccessibilityNodeInfoCompat nodeToMoveOnScreen;

  private final int userAction;

  private final int nodeAction;

  private final @Nullable String source;

  private final ScrollTimeout timeout;

  private AutoValue_Feedback_Scroll(
      Feedback.Scroll.Action action,
      @Nullable AccessibilityNode node,
      @Nullable AccessibilityNodeInfoCompat nodeCompat,
      @Nullable AccessibilityNodeInfoCompat nodeToMoveOnScreen,
      int userAction,
      int nodeAction,
      @Nullable String source,
      ScrollTimeout timeout) {
    this.action = action;
    this.node = node;
    this.nodeCompat = nodeCompat;
    this.nodeToMoveOnScreen = nodeToMoveOnScreen;
    this.userAction = userAction;
    this.nodeAction = nodeAction;
    this.source = source;
    this.timeout = timeout;
  }

  @Override
  public Feedback.Scroll.Action action() {
    return action;
  }

  @Override
  public @Nullable AccessibilityNode node() {
    return node;
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat nodeCompat() {
    return nodeCompat;
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat nodeToMoveOnScreen() {
    return nodeToMoveOnScreen;
  }

  @ScrollActionRecord.UserAction
  @Override
  public int userAction() {
    return userAction;
  }

  @Override
  public int nodeAction() {
    return nodeAction;
  }

  @Override
  public @Nullable String source() {
    return source;
  }

  @Override
  public ScrollTimeout timeout() {
    return timeout;
  }

  @Override
  public String toString() {
    return "Scroll{"
        + "action="
        + action
        + ", "
        + "node="
        + node
        + ", "
        + "nodeCompat="
        + nodeCompat
        + ", "
        + "nodeToMoveOnScreen="
        + nodeToMoveOnScreen
        + ", "
        + "userAction="
        + userAction
        + ", "
        + "nodeAction="
        + nodeAction
        + ", "
        + "source="
        + source
        + ", "
        + "timeout="
        + timeout
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.Scroll) {
      Feedback.Scroll that = (Feedback.Scroll) o;
      return this.action.equals(that.action())
          && (this.node == null ? that.node() == null : this.node.equals(that.node()))
          && (this.nodeCompat == null
              ? that.nodeCompat() == null
              : this.nodeCompat.equals(that.nodeCompat()))
          && (this.nodeToMoveOnScreen == null
              ? that.nodeToMoveOnScreen() == null
              : this.nodeToMoveOnScreen.equals(that.nodeToMoveOnScreen()))
          && this.userAction == that.userAction()
          && this.nodeAction == that.nodeAction()
          && (this.source == null ? that.source() == null : this.source.equals(that.source()))
          && this.timeout == that.timeout();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= (node == null) ? 0 : node.hashCode();
    h$ *= 1000003;
    h$ ^= (nodeCompat == null) ? 0 : nodeCompat.hashCode();
    h$ *= 1000003;
    h$ ^= (nodeToMoveOnScreen == null) ? 0 : nodeToMoveOnScreen.hashCode();
    h$ *= 1000003;
    h$ ^= userAction;
    h$ *= 1000003;
    h$ ^= nodeAction;
    h$ *= 1000003;
    h$ ^= (source == null) ? 0 : source.hashCode();
    h$ *= 1000003;
    h$ ^= timeout.getTimeoutMillis();
    return h$;
  }

  static final class Builder extends Feedback.Scroll.Builder {
    private Feedback.Scroll.Action action;
    private @Nullable AccessibilityNode node;
    private @Nullable AccessibilityNodeInfoCompat nodeCompat;
    private @Nullable AccessibilityNodeInfoCompat nodeToMoveOnScreen;
    private Integer userAction;
    private Integer nodeAction;
    private @Nullable String source;
    private ScrollTimeout timeout;

    Builder() {
    }
    @Override
    public Feedback.Scroll.Builder setAction(Feedback.Scroll.Action action) {
      if (action == null) {
        throw new NullPointerException("Null action");
      }
      this.action = action;
      return this;
    }
    @Override
    public Feedback.Scroll.Builder setNode(@Nullable AccessibilityNode node) {
      this.node = node;
      return this;
    }
    @Override
    public Feedback.Scroll.Builder setNodeCompat(@Nullable AccessibilityNodeInfoCompat nodeCompat) {
      this.nodeCompat = nodeCompat;
      return this;
    }
    @Override
    public Feedback.Scroll.Builder setNodeToMoveOnScreen(@Nullable AccessibilityNodeInfoCompat nodeToMoveOnScreen) {
      this.nodeToMoveOnScreen = nodeToMoveOnScreen;
      return this;
    }
    @Override
    public Feedback.Scroll.Builder setUserAction(int userAction) {
      this.userAction = userAction;
      return this;
    }
    @Override
    public Feedback.Scroll.Builder setNodeAction(int nodeAction) {
      this.nodeAction = nodeAction;
      return this;
    }

    @Override
    public Feedback.Scroll.Builder setSource(@Nullable String source) {
      this.source = source;
      return this;
    }

    @Override
    public Scroll.Builder setTimeout(ScrollTimeout timeout) {
      this.timeout = timeout;
      return this;
    }

    @Override
    public Feedback.Scroll build() {
      String missing = "";
      if (this.action == null) {
        missing += " action";
      }
      if (this.userAction == null) {
        missing += " userAction";
      }
      if (this.nodeAction == null) {
        missing += " nodeAction";
      }
      if (this.timeout == null) {
        missing += " timeout";
      }
      if (!missing.isEmpty()) {
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_Feedback_Scroll(
          this.action,
          this.node,
          this.nodeCompat,
          this.nodeToMoveOnScreen,
          this.userAction,
          this.nodeAction,
          this.source,
          this.timeout);
    }
  }

}

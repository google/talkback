package com.google.android.accessibility.talkback;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenState;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_Focus extends Feedback.Focus {

  private final @Nullable AccessibilityNodeInfoCompat start;

  private final @Nullable AccessibilityNodeInfoCompat target;

  private final int direction;

  private final @Nullable FocusActionInfo focusActionInfo;

  private final @Nullable NavigationAction navigationAction;

  private final @Nullable CharSequence searchKeyword;

  private final boolean forceRefocus;

  private final Feedback.Focus.Action action;

  private final @Nullable AccessibilityNodeInfoCompat scrolledNode;

  private final @Nullable ScreenState screenState;

  private AutoValue_Feedback_Focus(
      @Nullable AccessibilityNodeInfoCompat start,
      @Nullable AccessibilityNodeInfoCompat target,
      int direction,
      @Nullable FocusActionInfo focusActionInfo,
      @Nullable NavigationAction navigationAction,
      @Nullable CharSequence searchKeyword,
      boolean forceRefocus,
      Feedback.Focus.Action action,
      @Nullable AccessibilityNodeInfoCompat scrolledNode,
      @Nullable ScreenState screenState) {
    this.start = start;
    this.target = target;
    this.direction = direction;
    this.focusActionInfo = focusActionInfo;
    this.navigationAction = navigationAction;
    this.searchKeyword = searchKeyword;
    this.forceRefocus = forceRefocus;
    this.action = action;
    this.scrolledNode = scrolledNode;
    this.screenState = screenState;
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat start() {
    return start;
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat target() {
    return target;
  }

  @TraversalStrategy.SearchDirection
  @Override
  public int direction() {
    return direction;
  }

  @Override
  public @Nullable FocusActionInfo focusActionInfo() {
    return focusActionInfo;
  }

  @Override
  public @Nullable NavigationAction navigationAction() {
    return navigationAction;
  }

  @Override
  public @Nullable CharSequence searchKeyword() {
    return searchKeyword;
  }

  @Override
  public boolean forceRefocus() {
    return forceRefocus;
  }

  @Override
  public Feedback.Focus.Action action() {
    return action;
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat scrolledNode() {
    return scrolledNode;
  }

  @Override
  public @Nullable ScreenState screenState() {
    return screenState;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.Focus) {
      Feedback.Focus that = (Feedback.Focus) o;
      return (this.start == null ? that.start() == null : this.start.equals(that.start()))
          && (this.target == null ? that.target() == null : this.target.equals(that.target()))
          && this.direction == that.direction()
          && (this.focusActionInfo == null ? that.focusActionInfo() == null : this.focusActionInfo.equals(that.focusActionInfo()))
          && (this.navigationAction == null ? that.navigationAction() == null : this.navigationAction.equals(that.navigationAction()))
          && (this.searchKeyword == null ? that.searchKeyword() == null : this.searchKeyword.equals(that.searchKeyword()))
          && this.forceRefocus == that.forceRefocus()
          && this.action.equals(that.action())
          && (this.scrolledNode == null ? that.scrolledNode() == null : this.scrolledNode.equals(that.scrolledNode()))
          && (this.screenState == null ? that.screenState() == null : this.screenState.equals(that.screenState()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= (start == null) ? 0 : start.hashCode();
    h$ *= 1000003;
    h$ ^= (target == null) ? 0 : target.hashCode();
    h$ *= 1000003;
    h$ ^= direction;
    h$ *= 1000003;
    h$ ^= (focusActionInfo == null) ? 0 : focusActionInfo.hashCode();
    h$ *= 1000003;
    h$ ^= (navigationAction == null) ? 0 : navigationAction.hashCode();
    h$ *= 1000003;
    h$ ^= (searchKeyword == null) ? 0 : searchKeyword.hashCode();
    h$ *= 1000003;
    h$ ^= forceRefocus ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= (scrolledNode == null) ? 0 : scrolledNode.hashCode();
    h$ *= 1000003;
    h$ ^= (screenState == null) ? 0 : screenState.hashCode();
    return h$;
  }

  static final class Builder extends Feedback.Focus.Builder {
    private @Nullable AccessibilityNodeInfoCompat start;
    private @Nullable AccessibilityNodeInfoCompat target;
    private Integer direction;
    private @Nullable FocusActionInfo focusActionInfo;
    private @Nullable NavigationAction navigationAction;
    private @Nullable CharSequence searchKeyword;
    private Boolean forceRefocus;
    private Feedback.Focus.Action action;
    private @Nullable AccessibilityNodeInfoCompat scrolledNode;
    private @Nullable ScreenState screenState;
    Builder() {
    }
    @Override
    public Feedback.Focus.Builder setStart(@Nullable AccessibilityNodeInfoCompat start) {
      this.start = start;
      return this;
    }
    @Override
    @Nullable AccessibilityNodeInfoCompat start() {
      return start;
    }
    @Override
    public Feedback.Focus.Builder setTarget(@Nullable AccessibilityNodeInfoCompat target) {
      this.target = target;
      return this;
    }
    @Override
    @Nullable AccessibilityNodeInfoCompat target() {
      return target;
    }
    @Override
    public Feedback.Focus.Builder setDirection(int direction) {
      this.direction = direction;
      return this;
    }
    @Override
    public Feedback.Focus.Builder setFocusActionInfo(@Nullable FocusActionInfo focusActionInfo) {
      this.focusActionInfo = focusActionInfo;
      return this;
    }
    @Override
    public Feedback.Focus.Builder setNavigationAction(@Nullable NavigationAction navigationAction) {
      this.navigationAction = navigationAction;
      return this;
    }
    @Override
    public Feedback.Focus.Builder setSearchKeyword(@Nullable CharSequence searchKeyword) {
      this.searchKeyword = searchKeyword;
      return this;
    }
    @Override
    public Feedback.Focus.Builder setForceRefocus(boolean forceRefocus) {
      this.forceRefocus = forceRefocus;
      return this;
    }
    @Override
    public Feedback.Focus.Builder setAction(Feedback.Focus.Action action) {
      if (action == null) {
        throw new NullPointerException("Null action");
      }
      this.action = action;
      return this;
    }
    @Override
    public Feedback.Focus.Builder setScrolledNode(@Nullable AccessibilityNodeInfoCompat scrolledNode) {
      this.scrolledNode = scrolledNode;
      return this;
    }
    @Override
    @Nullable AccessibilityNodeInfoCompat scrolledNode() {
      return scrolledNode;
    }
    @Override
    public Feedback.Focus.Builder setScreenState(@Nullable ScreenState screenState) {
      this.screenState = screenState;
      return this;
    }
    @Override
    Feedback.Focus autoBuild() {
      if (this.direction == null
          || this.forceRefocus == null
          || this.action == null) {
        StringBuilder missing = new StringBuilder();
        if (this.direction == null) {
          missing.append(" direction");
        }
        if (this.forceRefocus == null) {
          missing.append(" forceRefocus");
        }
        if (this.action == null) {
          missing.append(" action");
        }
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_Feedback_Focus(
          this.start,
          this.target,
          this.direction,
          this.focusActionInfo,
          this.navigationAction,
          this.searchKeyword,
          this.forceRefocus,
          this.action,
          this.scrolledNode,
          this.screenState);
    }
  }

}

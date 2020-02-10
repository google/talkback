/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.contextmenu;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.view.ActionProvider;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import com.google.android.accessibility.talkback.focusmanagement.FocusActor;
import org.checkerframework.checker.nullness.qual.Nullable;

/** ContextMenuItem class */
public abstract class ContextMenuItem implements MenuItem {

  private Context context;
  private int itemId;
  private int groupId;
  private char alphaShortcut;
  private char numericShortcut;
  @Nullable private Drawable icon;
  private int order;
  private Intent intent;
  private boolean enabled = true;
  private CharSequence title;
  private CharSequence titleCondensed;
  private boolean visible = true;
  private boolean checkable = false;
  private boolean checked = false;
  private OnMenuItemClickListener listener;
  private ContextMenuInfo menuInfo;
  // Used for EditText cursor control.
  // When the user performs cursor control(Move cursor to end, etc, from Local Context Menu)
  // in an EditText, the accessibility focus will be saved (when navigating in the context menu)
  // and reset back(when the context menu dismisses). A TYPE_ACCESSIBILITY_VIEW_FOCUS and a
  // TYPE_WINDOWS_CHANGED event will be fired. To avoid EditText description re-read after
  // the cursor control, we need to skip these two events.
  private boolean skipRefocusEvents = false;
  private boolean skipWindowEvents = false;
  private boolean showsAlertDialog = false;
  private DeferredType deferredType = DeferredType.NONE;
  private boolean needRestoreFocus = false;

  /** Enum defining kinds of rule to activate deferred action. */
  public enum DeferredType {
    /** Default value not to defer action. */
    NONE,
    /** Defers the action until windows are stable. */
    WINDOWS_STABLE,
    /** Defers the action until the service receives accessibility focus event. */
    ACCESSIBILITY_FOCUS_RECEIVED
  }

  protected ContextMenuItem(
      Context context, int groupId, int itemId, int order, CharSequence title) {
    this.itemId = itemId;
    this.groupId = groupId;
    this.context = context;
    this.order = order;
    this.title = title;
    menuInfo = new ContextMenuInfo();
  }

  @Override
  public char getAlphabeticShortcut() {
    return alphaShortcut;
  }

  @Override
  public char getNumericShortcut() {
    return numericShortcut;
  }

  @Override
  public ContextMenuItem setAlphabeticShortcut(char alphaChar) {
    alphaShortcut = alphaChar;
    return this;
  }

  @Override
  public ContextMenuItem setNumericShortcut(char numericChar) {
    numericShortcut = numericChar;
    return this;
  }

  @Override
  public ContextMenuItem setShortcut(char numericChar, char alphaChar) {
    numericShortcut = numericChar;
    alphaShortcut = alphaChar;
    return this;
  }

  @Override
  public int getGroupId() {
    return groupId;
  }

  @Override
  public Drawable getIcon() {
    return icon;
  }

  @Override
  public MenuItem setIcon(Drawable icon) {
    this.icon = icon;
    return this;
  }

  @Override
  public MenuItem setIcon(int iconRes) {
    if (iconRes == 0) {
      icon = null;
    } else {
      icon = context.getResources().getDrawable(iconRes);
    }

    return this;
  }

  @Override
  public int getOrder() {
    return order;
  }

  @Override
  public Intent getIntent() {
    return intent;
  }

  @Override
  public MenuItem setIntent(Intent intent) {
    this.intent = intent;
    return this;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public MenuItem setEnabled(boolean enabled) {
    this.enabled = enabled;
    return this;
  }

  @Override
  public CharSequence getTitle() {
    return title;
  }

  @Override
  public MenuItem setTitle(CharSequence title) {
    this.title = title;
    return this;
  }

  @Override
  public CharSequence getTitleCondensed() {
    return titleCondensed;
  }

  @Override
  public MenuItem setTitleCondensed(CharSequence titleCondensed) {
    this.titleCondensed = titleCondensed;
    return this;
  }

  @Override
  public MenuItem setTitle(int titleRes) {
    title = context.getText(titleRes);
    return this;
  }

  @Override
  public int getItemId() {
    return itemId;
  }

  @Override
  public boolean isCheckable() {
    return checkable;
  }

  @Override
  public boolean isChecked() {
    return checkable && checked;
  }

  @Override
  public boolean isVisible() {
    return visible;
  }

  @Override
  public MenuItem setCheckable(boolean checkable) {
    this.checkable = checkable;
    return this;
  }

  @Override
  public MenuItem setChecked(boolean checked) {
    this.checked = checked;
    return this;
  }

  @Override
  public MenuItem setVisible(boolean visible) {
    this.visible = visible;
    return this;
  }

  @Override
  public MenuItem setOnMenuItemClickListener(OnMenuItemClickListener menuItemClickListener) {
    listener = menuItemClickListener;
    return this;
  }

  @Override
  public boolean collapseActionView() {
    return false;
  }

  @Override
  public boolean expandActionView() {
    return false;
  }

  @Override
  public ActionProvider getActionProvider() {
    return null;
  }

  @Override
  public View getActionView() {
    return null;
  }

  @Override
  public boolean isActionViewExpanded() {
    return false;
  }

  @Override
  public MenuItem setActionProvider(ActionProvider actionProvider) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MenuItem setActionView(View view) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MenuItem setActionView(int resId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MenuItem setOnActionExpandListener(OnActionExpandListener listener) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setShowAsAction(int actionEnum) {
    throw new UnsupportedOperationException();
  }

  @Override
  public MenuItem setShowAsActionFlags(int actionEnum) {
    throw new UnsupportedOperationException();
  }

  @Override
  public ContextMenu.ContextMenuInfo getMenuInfo() {
    return menuInfo;
  }

  //TODO internal sdk has no setIconTintList abstract method but public sdk does.
  // Check it after M release
  @Override
  public MenuItem setIconTintList(ColorStateList tint) {
    return this;
  }

  //TODO internal sdk has no setIconTintMode abstract method but public sdk does.
  // Check it after M release
  @Override
  public MenuItem setIconTintMode(PorterDuff.Mode tintMode) {
    return this;
  }

  /**
   * Attempts to perform this item's click action.
   *
   * @return {@code true} if this item performs an action
   */
  public boolean onClickPerformed() {
    if (checkable) {
      checked = !checked;
    }

    return listener != null && listener.onMenuItemClick(this);
  }

  /** This class doesn't actually do anything. */
  private static class ContextMenuInfo implements ContextMenu.ContextMenuInfo {
    // Empty class.
  }

  /**
   * Returns whether menu should restore focus on next screen change. It will restore cache node
   * from {@link FocusActor#cacheNodeToRestoreFocus()}.
   */
  public boolean shouldRestoreFocusOnScreenChange() {
    return !hasNextPopupWidget() && getNeedRestoreFocus();
  }

  /**
   * Some item actions need to perform on the current active window otherwise it would be failed.
   * For example, "Read from Next" and "Navigation Granularity" require delay... to wait for the
   * context-menu to disappear, so that focus lands on the content window.
   *
   * @return one of {@code DeferredType}
   */
  public DeferredType getDeferActionType() {
    if (hasNextPopupWidget()) {
      return DeferredType.NONE;
    }
    return getDeferredType();
  }

  /**
   * Some item actions will send spoken feedback. So skip next focus announcement to prevent confuse
   * user.
   *
   * @return return {@code true} to skip next focus announcement.
   */
  public boolean needToSkipNextFocusAnnouncement() {
    return !hasNextPopupWidget() && getSkipRefocusEvents();
  }

  /**
   * Some item actions will send spoken feedback. So skip next window announcement to prevent
   * confuse user.
   *
   * @return return {@code true} to skip next window announcement.
   */
  public boolean needToSkipNextWindowAnnouncement() {
    return !hasNextPopupWidget() && getSkipWindowEvents();
  }

  /**
   * Returns {@code true} if clicking the menu item has next popup widget to postpone close rules to
   * next popup widget to handle.
   */
  private boolean hasNextPopupWidget() {
    return hasSubMenu() || getShowsAlertDialog();
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////
  // Ref  provides methods to get/set configs and these configs are triggered
  // when the item has been clicked and menu is about to close.

  /** Sets whether to mute next focus event announcement when clicks the menu item. */
  public void setSkipRefocusEvents(boolean skip) {
    skipRefocusEvents = skip;
  }

  /** Returns whether to mute next focus event announcement when clicks the menu item. */
  public boolean getSkipRefocusEvents() {
    return skipRefocusEvents;
  }

  /** Sets whether to mute next window event announcement when clicks the menu item. */
  public void setSkipWindowEvents(boolean skip) {
    skipWindowEvents = skip;
  }

  /** Returns whether to mute next window event announcement when clicks the menu item. */
  public boolean getSkipWindowEvents() {
    return skipWindowEvents;
  }

  /** Sets whether to show alert dialog when clicks the menu item. */
  public void setShowsAlertDialog(boolean showsAlertDialog) {
    this.showsAlertDialog = showsAlertDialog;
  }

  /** Returns whether to show alert dialog when clicks the menu item. */
  public boolean getShowsAlertDialog() {
    return showsAlertDialog;
  }

  /**
   * Sets defer type to defer action when clicks the menu item.
   *
   * @param deferredType one of {@code DeferredType}.
   */
  public void setDeferredType(DeferredType deferredType) {
    this.deferredType = deferredType;
  }

  /** Returns {@code DeferredType} to defer action when clicks the menu item. */
  public DeferredType getDeferredType() {
    return deferredType;
  }

  /** Sets whether to restore cached focus when clicks the menu item. */
  public void setNeedRestoreFocus(boolean needRestoreFocus) {
    this.needRestoreFocus = needRestoreFocus;
  }

  /** Returns whether to restore cached focus when clicks the menu item. */
  public boolean getNeedRestoreFocus() {
    return needRestoreFocus;
  }
}

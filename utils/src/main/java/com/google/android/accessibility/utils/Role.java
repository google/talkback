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
package com.google.android.accessibility.utils;

import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utility methods for managing AccessibilityNodeInfo Roles. */
public class Role {

  /**
   * Ids of user-interface element roles, which are flexibly mapped from specific UI classes. This
   * mapping allows us to abstract similar UI elements to the same role, and to isolate UI element
   * interpretation logic.
   */
  @IntDef({
    ROLE_NONE,
    ROLE_BUTTON,
    ROLE_CHECK_BOX,
    ROLE_CHECKED_TEXT_VIEW,
    ROLE_DROP_DOWN_LIST,
    ROLE_EDIT_TEXT,
    ROLE_GRID,
    ROLE_IMAGE,
    ROLE_IMAGE_BUTTON,
    ROLE_LIST,
    ROLE_PAGER,
    ROLE_RADIO_BUTTON,
    ROLE_SEEK_CONTROL,
    ROLE_SWITCH,
    ROLE_TAB_BAR,
    ROLE_TOGGLE_BUTTON,
    ROLE_VIEW_GROUP,
    ROLE_WEB_VIEW,
    ROLE_PROGRESS_BAR,
    ROLE_ACTION_BAR_TAB,
    ROLE_DRAWER_LAYOUT,
    ROLE_SLIDING_DRAWER,
    ROLE_ICON_MENU,
    ROLE_TOAST,
    ROLE_ALERT_DIALOG,
    ROLE_DATE_PICKER_DIALOG,
    ROLE_TIME_PICKER_DIALOG,
    ROLE_DATE_PICKER,
    ROLE_TIME_PICKER,
    ROLE_NUMBER_PICKER,
    ROLE_SCROLL_VIEW,
    ROLE_HORIZONTAL_SCROLL_VIEW,
    ROLE_KEYBOARD_KEY,
    ROLE_TALKBACK_EDIT_TEXT_OVERLAY,
    ROLE_TEXT_ENTRY_KEY,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface RoleName {}

  // Please keep the constants in this list sorted by constant index order, and not by
  // alphabetical order. If you add a new constant, it must also be added to the RoleName
  // annotation interface.
  public static final int ROLE_NONE = 0;
  public static final int ROLE_BUTTON = 1;
  public static final int ROLE_CHECK_BOX = 2;
  public static final int ROLE_DROP_DOWN_LIST = 3;
  public static final int ROLE_EDIT_TEXT = 4;
  public static final int ROLE_GRID = 5;
  public static final int ROLE_IMAGE = 6;
  public static final int ROLE_IMAGE_BUTTON = 7;
  public static final int ROLE_LIST = 8;
  public static final int ROLE_RADIO_BUTTON = 9;
  public static final int ROLE_SEEK_CONTROL = 10;
  public static final int ROLE_SWITCH = 11;
  public static final int ROLE_TAB_BAR = 12;
  public static final int ROLE_TOGGLE_BUTTON = 13;
  public static final int ROLE_VIEW_GROUP = 14;
  public static final int ROLE_WEB_VIEW = 15;
  public static final int ROLE_PAGER = 16;
  public static final int ROLE_CHECKED_TEXT_VIEW = 17;
  public static final int ROLE_PROGRESS_BAR = 18;
  public static final int ROLE_ACTION_BAR_TAB = 19;
  public static final int ROLE_DRAWER_LAYOUT = 20;
  public static final int ROLE_SLIDING_DRAWER = 21;
  public static final int ROLE_ICON_MENU = 22;
  public static final int ROLE_TOAST = 23;
  public static final int ROLE_ALERT_DIALOG = 24;
  public static final int ROLE_DATE_PICKER_DIALOG = 25;
  public static final int ROLE_TIME_PICKER_DIALOG = 26;
  public static final int ROLE_DATE_PICKER = 27;
  public static final int ROLE_TIME_PICKER = 28;
  public static final int ROLE_NUMBER_PICKER = 29;
  public static final int ROLE_SCROLL_VIEW = 30;
  public static final int ROLE_HORIZONTAL_SCROLL_VIEW = 31;
  public static final int ROLE_KEYBOARD_KEY = 32;
  public static final int ROLE_TALKBACK_EDIT_TEXT_OVERLAY = 33;
  public static final int ROLE_TEXT_ENTRY_KEY = 34;

  // Number of roles: 34

  /** Used to identify and ignore a11y overlay windows created by Talkback. */
  public static final String TALKBACK_EDIT_TEXT_OVERLAY_CLASSNAME = "TalkbackEditTextOverlay";

  /**
   * Gets the source {@link Role} from the {@link AccessibilityEvent}.
   *
   * <p>It checks the role with {@link AccessibilityEvent#getClassName()}. If it returns {@link
   * #ROLE_NONE}, fallback to check {@link AccessibilityNodeInfoCompat#getClassName()} of the source
   * node.
   */
  public static @RoleName int getSourceRole(AccessibilityEvent event) {
    if (event == null) {
      return ROLE_NONE;
    }

    // Try to get role from event's class name.
    @RoleName int role = sourceClassNameToRole(event);
    if (role != ROLE_NONE) {
      return role;
    }

    // Extract event's source node, and map source node class to role.
    AccessibilityRecordCompat eventRecord = AccessibilityEventCompat.asRecord(event);
    AccessibilityNodeInfoCompat source = eventRecord.getSource();
    try {
      return getRole(source);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(source);
    }
  }

  /** Find role from source event's class name string. */
  private static @RoleName int sourceClassNameToRole(AccessibilityEvent event) {
    if (event == null) {
      return ROLE_NONE;
    }

    // Event TYPE_NOTIFICATION_STATE_CHANGED always has null source node.
    CharSequence eventClassName = event.getClassName();

    // When comparing event.getClassName() to class name of standard widgets, we should take care of
    // the order of the "if" statements: check subclasses before checking superclasses.

    // Toast.TN is a private class, thus we have to hard code the class name.
    // "$TN" is only in the class-name before android-R.
    if (ClassLoadingCache.checkInstanceOf(eventClassName, "android.widget.Toast$TN")
        || ClassLoadingCache.checkInstanceOf(eventClassName, "android.widget.Toast")) {
      return ROLE_TOAST;
    }

    // Some events have different value for getClassName() and getSource().getClass()
    if (ClassLoadingCache.checkInstanceOf(eventClassName, android.app.ActionBar.Tab.class)) {
      return ROLE_ACTION_BAR_TAB;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    // Subclasses of ViewGroup.

    // Inheritance: View->ViewGroup->DrawerLayout
    if (ClassLoadingCache.checkInstanceOf(
            eventClassName, androidx.drawerlayout.widget.DrawerLayout.class)
        || ClassLoadingCache.checkInstanceOf(
            eventClassName, "android.support.v4.widget.DrawerLayout")) {
      return ROLE_DRAWER_LAYOUT;
    }

    // Inheritance: View->ViewGroup->SlidingDrawer
    if (ClassLoadingCache.checkInstanceOf(eventClassName, android.widget.SlidingDrawer.class)) {
      return ROLE_SLIDING_DRAWER;
    }

    // Inheritance: View->ViewGroup->IconMenuView
    // IconMenuView is a hidden class, thus we have to hard code the class name.
    if (ClassLoadingCache.checkInstanceOf(
        eventClassName, "com.android.internal.view.menu.IconMenuView")) {
      return ROLE_ICON_MENU;
    }

    // Inheritance: View->ViewGroup->FrameLayout->DatePicker
    if (ClassLoadingCache.checkInstanceOf(eventClassName, android.widget.DatePicker.class)) {
      return ROLE_DATE_PICKER;
    }

    // Inheritance: View->ViewGroup->FrameLayout->TimePicker
    if (ClassLoadingCache.checkInstanceOf(eventClassName, android.widget.TimePicker.class)) {
      return ROLE_TIME_PICKER;
    }

    // Inheritance: View->ViewGroup->LinearLayout->NumberPicker
    if (ClassLoadingCache.checkInstanceOf(eventClassName, android.widget.NumberPicker.class)) {
      return ROLE_NUMBER_PICKER;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    // Subclasses of Dialog.
    // Inheritance: Dialog->AlertDialog->DatePickerDialog
    if (ClassLoadingCache.checkInstanceOf(eventClassName, android.app.DatePickerDialog.class)) {
      return ROLE_DATE_PICKER_DIALOG;
    }

    // Inheritance: Dialog->AlertDialog->TimePickerDialog
    if (ClassLoadingCache.checkInstanceOf(eventClassName, android.app.TimePickerDialog.class)) {
      return ROLE_TIME_PICKER_DIALOG;
    }

    // Inheritance: Dialog->AlertDialog
    if (ClassLoadingCache.checkInstanceOf(eventClassName, android.app.AlertDialog.class)
        || ClassLoadingCache.checkInstanceOf(
            eventClassName, "androidx.appcompat.app.AlertDialog")) {
      return ROLE_ALERT_DIALOG;
    }

    return ROLE_NONE;
  }

  /** Gets {@link Role} for {@link AccessibilityNodeInfoCompat}. */
  public static @RoleName int getRole(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return ROLE_NONE;
    }

    // We check Text entry key from property instead of class, so it needs to be in the beginning.
    if (AccessibilityNodeInfoUtils.isTextEntryKey(node)) {
      return ROLE_TEXT_ENTRY_KEY;
    }
    CharSequence className = node.getClassName();

    // When comparing node.getClassName() to class name of standard widgets, we should take care of
    // the order of the "if" statements: check subclasses before checking superclasses.
    // e.g. RadioButton is a subclass of Button, we should check Role RadioButton first and fall
    // down to check Role Button.

    // Identifies a11y overlay added by Talkback on edit texts.
    if (ClassLoadingCache.checkInstanceOf(className, TALKBACK_EDIT_TEXT_OVERLAY_CLASSNAME)) {
      return ROLE_TALKBACK_EDIT_TEXT_OVERLAY;
    }

    // Inheritance: View->ImageView
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.ImageView.class)) {
      return node.isClickable() ? ROLE_IMAGE_BUTTON : ROLE_IMAGE;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    // Subclasses of TextView.

    // Inheritance: View->TextView->Button->CompoundButton->Switch
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.Switch.class)) {
      return ROLE_SWITCH;
    }

    // Inheritance: View->TextView->Button->CompoundButton->ToggleButton
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.ToggleButton.class)) {
      return ROLE_TOGGLE_BUTTON;
    }

    // Inheritance: View->TextView->Button->CompoundButton->RadioButton
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.RadioButton.class)) {
      return ROLE_RADIO_BUTTON;
    }

    // Inheritance: View->TextView->Button->CompoundButton
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.CompoundButton.class)) {
      return ROLE_CHECK_BOX;
    }

    // Inheritance: View->TextView->Button
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.Button.class)) {
      return ROLE_BUTTON;
    }

    // Inheritance: View->TextView->CheckedTextView
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.CheckedTextView.class)) {
      return ROLE_CHECKED_TEXT_VIEW;
    }

    // Inheritance: View->TextView->EditText
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.EditText.class)) {
      return ROLE_EDIT_TEXT;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    // Subclasses of ProgressBar.

    // Inheritance: View->ProgressBar->AbsSeekBar->SeekBar
    if (ClassLoadingCache.checkInstanceOf(className, SeekBar.class)
        || (AccessibilityNodeInfoUtils.hasValidRangeInfo(node)
            && AccessibilityNodeInfoUtils.supportsAction(
                node, android.R.id.accessibilityActionSetProgress))) {
      return ROLE_SEEK_CONTROL;
    }

    // Inheritance: View->ProgressBar
    if (ClassLoadingCache.checkInstanceOf(className, ProgressBar.class)
        || (AccessibilityNodeInfoUtils.hasValidRangeInfo(node)
            && !AccessibilityNodeInfoUtils.supportsAction(
                node, android.R.id.accessibilityActionSetProgress))) {
      // ProgressBar check must come after SeekBar, because SeekBar specializes ProgressBar.
      return ROLE_PROGRESS_BAR;
    }

    if (ClassLoadingCache.checkInstanceOf(
        className, android.inputmethodservice.Keyboard.Key.class)) {
      return ROLE_KEYBOARD_KEY;
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    // Subclasses of ViewGroup.

    // Inheritance: View->ViewGroup->AbsoluteLayout->WebView
    if (ClassLoadingCache.checkInstanceOf(className, android.webkit.WebView.class)) {
      return ROLE_WEB_VIEW;
    }

    // Inheritance: View->ViewGroup->LinearLayout->TabWidget
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.TabWidget.class)) {
      return ROLE_TAB_BAR;
    }

    // Inheritance: View->ViewGroup->FrameLayout->HorizontalScrollView
    // If there is a CollectionInfo, fall into a ROLE_LIST/ROLE_GRID
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.HorizontalScrollView.class)
        && node.getCollectionInfo() == null) {
      return ROLE_HORIZONTAL_SCROLL_VIEW;
    }

    // Inheritance: View->ViewGroup->FrameLayout->ScrollView
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.ScrollView.class)) {
      return ROLE_SCROLL_VIEW;
    }

    // Inheritance: View->ViewGroup->ViewPager
    if (ClassLoadingCache.checkInstanceOf(className, androidx.viewpager.widget.ViewPager.class)
        || ClassLoadingCache.checkInstanceOf(className, "android.support.v4.view.ViewPager")) {
      return ROLE_PAGER;
    }

    // TODO: Check if we should add Role RecyclerView.
    // By default, RecyclerView node has CollectionInfo, so that it will be classified as a List or
    // Grid.
    // View->ViewGroup->RecyclerView
    /* TODO:
    if (ClassLoadingCache.checkInstanceOf(className, "androidx.recyclerview.widget.RecyclerView")
        || ClassLoadingCache.checkInstanceOf(
            className, "androidx.recyclerview.widget.RecyclerView")) {
      return ROLE_RECYCLER_VIEW;
    }
    */

    // Inheritance: View->ViewGroup->AdapterView->AbsSpinner->Spinner
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.Spinner.class)) {
      return ROLE_DROP_DOWN_LIST;
    }

    // Inheritance: View->ViewGroup->AdapterView->AbsListView->GridView
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.GridView.class)) {
      return ROLE_GRID;
    }

    // Inheritance: View->ViewGroup->AdapterView->AbsListView
    if (ClassLoadingCache.checkInstanceOf(className, android.widget.AbsListView.class)) {
      return ROLE_LIST;
    }

    // Inheritance: View->ViewGroup->ViewPager2
    if (AccessibilityNodeInfoUtils.supportsAction(
            node, AccessibilityActionCompat.ACTION_PAGE_UP.getId())
        || AccessibilityNodeInfoUtils.supportsAction(
            node, AccessibilityActionCompat.ACTION_PAGE_DOWN.getId())
        || AccessibilityNodeInfoUtils.supportsAction(
            node, AccessibilityActionCompat.ACTION_PAGE_LEFT.getId())
        || AccessibilityNodeInfoUtils.supportsAction(
            node, AccessibilityActionCompat.ACTION_PAGE_RIGHT.getId())) {
      return ROLE_PAGER;
    }

    CollectionInfoCompat collection = node.getCollectionInfo();
    if (collection != null) {
      // RecyclerView will be classified as a list or grid.
      if (collection.getRowCount() > 1 && collection.getColumnCount() > 1) {
        return ROLE_GRID;
      } else {
        return ROLE_LIST;
      }
    }

    // Inheritance: View->ViewGroup
    if (ClassLoadingCache.checkInstanceOf(className, android.view.ViewGroup.class)) {
      return ROLE_VIEW_GROUP;
    }

    return ROLE_NONE;
  }

  /**
   * Gets {@link Role} for {@link AccessibilityNodeInfo}. @See {@link
   * #getRole(AccessibilityNodeInfoCompat)}
   */
  public static @RoleName int getRole(AccessibilityNodeInfo node) {
    if (node == null) {
      return Role.ROLE_NONE;
    }

    AccessibilityNodeInfoCompat nodeCompat = AccessibilityNodeInfoUtils.toCompat(node);
    return getRole(nodeCompat);
  }

  /** For use in logging. */
  public static String roleToString(@RoleName int role) {
    switch (role) {
      case ROLE_NONE:
        return "ROLE_NONE";
      case ROLE_BUTTON:
        return "ROLE_BUTTON";
      case ROLE_CHECK_BOX:
        return "ROLE_CHECK_BOX";
      case ROLE_DROP_DOWN_LIST:
        return "ROLE_DROP_DOWN_LIST";
      case ROLE_EDIT_TEXT:
        return "ROLE_EDIT_TEXT";
      case ROLE_GRID:
        return "ROLE_GRID";
      case ROLE_IMAGE:
        return "ROLE_IMAGE";
      case ROLE_IMAGE_BUTTON:
        return "ROLE_IMAGE_BUTTON";
      case ROLE_LIST:
        return "ROLE_LIST";
      case ROLE_RADIO_BUTTON:
        return "ROLE_RADIO_BUTTON";
      case ROLE_SEEK_CONTROL:
        return "ROLE_SEEK_CONTROL";
      case ROLE_SWITCH:
        return "ROLE_SWITCH";
      case ROLE_TAB_BAR:
        return "ROLE_TAB_BAR";
      case ROLE_TOGGLE_BUTTON:
        return "ROLE_TOGGLE_BUTTON";
      case ROLE_VIEW_GROUP:
        return "ROLE_VIEW_GROUP";
      case ROLE_WEB_VIEW:
        return "ROLE_WEB_VIEW";
      case ROLE_PAGER:
        return "ROLE_PAGER";
      case ROLE_CHECKED_TEXT_VIEW:
        return "ROLE_CHECKED_TEXT_VIEW";
      case ROLE_PROGRESS_BAR:
        return "ROLE_PROGRESS_BAR";
      case ROLE_ACTION_BAR_TAB:
        return "ROLE_ACTION_BAR_TAB";
      case ROLE_DRAWER_LAYOUT:
        return "ROLE_DRAWER_LAYOUT";
      case ROLE_SLIDING_DRAWER:
        return "ROLE_SLIDING_DRAWER";
      case ROLE_ICON_MENU:
        return "ROLE_ICON_MENU";
      case ROLE_TOAST:
        return "ROLE_TOAST";
      case ROLE_ALERT_DIALOG:
        return "ROLE_ALERT_DIALOG";
      case ROLE_DATE_PICKER_DIALOG:
        return "ROLE_DATE_PICKER_DIALOG";
      case ROLE_TIME_PICKER_DIALOG:
        return "ROLE_TIME_PICKER_DIALOG";
      case ROLE_DATE_PICKER:
        return "ROLE_DATE_PICKER";
      case ROLE_TIME_PICKER:
        return "ROLE_TIME_PICKER";
      case ROLE_NUMBER_PICKER:
        return "ROLE_NUMBER_PICKER";
      case ROLE_SCROLL_VIEW:
        return "ROLE_SCROLL_VIEW";
      case ROLE_HORIZONTAL_SCROLL_VIEW:
        return "ROLE_HORIZONTAL_SCROLL_VIEW";
      case ROLE_KEYBOARD_KEY:
        return "ROLE_KEYBOARD_KEY";
      case ROLE_TALKBACK_EDIT_TEXT_OVERLAY:
        return "ROLE_TALKBACK_EDIT_TEXT_OVERLAY";
      case ROLE_TEXT_ENTRY_KEY:
        return "ROLE_TEXT_ENTRY_KEY";
      default:
        return "(unknown role " + role + ")";
    }
  }
}

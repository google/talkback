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

package com.google.android.accessibility.talkback.focusmanagement;

import android.content.Context;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

/**
 * Defines different types of target nodes for navigation action. Provides some utils towards {@link
 * TargetType}.
 */
public final class NavigationTarget {

  private static final String TAG = "NavigationTarget";

  private NavigationTarget() {}

  private static final int MASK_TARGET_HTML_ELEMENT = 1 << 16;
  private static final int MASK_TARGET_NATIVE_MACRO_GRANULARITY = 1 << 20;

  public static final int TARGET_DEFAULT = 0;

  // Targets for native-macro-granularity navigation.
  public static final int TARGET_HEADING = MASK_TARGET_NATIVE_MACRO_GRANULARITY + 1;
  public static final int TARGET_CONTROL = MASK_TARGET_NATIVE_MACRO_GRANULARITY + 2;
  public static final int TARGET_LINK = MASK_TARGET_NATIVE_MACRO_GRANULARITY + 3;

  // Targets for web-granularity navigation and keycombo navigation on html elements.
  public static final int TARGET_HTML_ELEMENT_LINK = MASK_TARGET_HTML_ELEMENT + 102;
  public static final int TARGET_HTML_ELEMENT_LIST = MASK_TARGET_HTML_ELEMENT + 103;
  public static final int TARGET_HTML_ELEMENT_CONTROL = MASK_TARGET_HTML_ELEMENT + 104;
  public static final int TARGET_HTML_ELEMENT_HEADING = MASK_TARGET_HTML_ELEMENT + 105;

  // Web element targets used by keycombo navigation only.
  public static final int TARGET_HTML_ELEMENT_BUTTON = MASK_TARGET_HTML_ELEMENT + 106;
  public static final int TARGET_HTML_ELEMENT_CHECKBOX = MASK_TARGET_HTML_ELEMENT + 107;
  public static final int TARGET_HTML_ELEMENT_ARIA_LANDMARK = MASK_TARGET_HTML_ELEMENT + 108;
  public static final int TARGET_HTML_ELEMENT_EDIT_FIELD = MASK_TARGET_HTML_ELEMENT + 109;
  public static final int TARGET_HTML_ELEMENT_FOCUSABLE_ITEM = MASK_TARGET_HTML_ELEMENT + 110;
  public static final int TARGET_HTML_ELEMENT_HEADING_1 = MASK_TARGET_HTML_ELEMENT + 111;
  public static final int TARGET_HTML_ELEMENT_HEADING_2 = MASK_TARGET_HTML_ELEMENT + 112;
  public static final int TARGET_HTML_ELEMENT_HEADING_3 = MASK_TARGET_HTML_ELEMENT + 113;
  public static final int TARGET_HTML_ELEMENT_HEADING_4 = MASK_TARGET_HTML_ELEMENT + 114;
  public static final int TARGET_HTML_ELEMENT_HEADING_5 = MASK_TARGET_HTML_ELEMENT + 115;
  public static final int TARGET_HTML_ELEMENT_HEADING_6 = MASK_TARGET_HTML_ELEMENT + 116;
  public static final int TARGET_HTML_ELEMENT_GRAPHIC = MASK_TARGET_HTML_ELEMENT + 117;
  public static final int TARGET_HTML_ELEMENT_LIST_ITEM = MASK_TARGET_HTML_ELEMENT + 118;
  public static final int TARGET_HTML_ELEMENT_TABLE = MASK_TARGET_HTML_ELEMENT + 119;
  public static final int TARGET_HTML_ELEMENT_COMBOBOX = MASK_TARGET_HTML_ELEMENT + 120;

  // Target used to navigate to previous/next window with keyboard shortcuts.
  public static final int TARGET_WINDOW = 201;
  /** navigation target types. */
  @IntDef({
    TARGET_DEFAULT,
    TARGET_HEADING,
    TARGET_CONTROL,
    TARGET_LINK,
    TARGET_HTML_ELEMENT_LINK,
    TARGET_HTML_ELEMENT_LIST,
    TARGET_HTML_ELEMENT_CONTROL,
    TARGET_HTML_ELEMENT_HEADING,
    TARGET_HTML_ELEMENT_BUTTON,
    TARGET_HTML_ELEMENT_CHECKBOX,
    TARGET_HTML_ELEMENT_ARIA_LANDMARK,
    TARGET_HTML_ELEMENT_EDIT_FIELD,
    TARGET_HTML_ELEMENT_FOCUSABLE_ITEM,
    TARGET_HTML_ELEMENT_HEADING_1,
    TARGET_HTML_ELEMENT_HEADING_2,
    TARGET_HTML_ELEMENT_HEADING_3,
    TARGET_HTML_ELEMENT_HEADING_4,
    TARGET_HTML_ELEMENT_HEADING_5,
    TARGET_HTML_ELEMENT_HEADING_6,
    TARGET_HTML_ELEMENT_GRAPHIC,
    TARGET_HTML_ELEMENT_LIST_ITEM,
    TARGET_HTML_ELEMENT_TABLE,
    TARGET_HTML_ELEMENT_COMBOBOX,
    TARGET_WINDOW,
  })
  @Retention(RetentionPolicy.SOURCE)
  public @interface TargetType {}

  private static final String HTML_ELEMENT_HEADING = "HEADING";
  private static final String HTML_ELEMENT_BUTTON = "BUTTON";
  private static final String HTML_ELEMENT_CHECKBOX = "CHECKBOX";
  private static final String HTML_ELEMENT_ARIA_LANDMARK = "LANDMARK";
  private static final String HTML_ELEMENT_EDIT_FIELD = "TEXT_FIELD";
  private static final String HTML_ELEMENT_FOCUSABLE_ITEM = "FOCUSABLE";
  private static final String HTML_ELEMENT_HEADING_1 = "H1";
  private static final String HTML_ELEMENT_HEADING_2 = "H2";
  private static final String HTML_ELEMENT_HEADING_3 = "H3";
  private static final String HTML_ELEMENT_HEADING_4 = "H4";
  private static final String HTML_ELEMENT_HEADING_5 = "H5";
  private static final String HTML_ELEMENT_HEADING_6 = "H6";
  private static final String HTML_ELEMENT_LINK = "LINK";
  private static final String HTML_ELEMENT_CONTROL = "CONTROL";
  private static final String HTML_ELEMENT_GRAPHIC = "GRAPHIC";
  private static final String HTML_ELEMENT_LIST_ITEM = "LIST_ITEM";
  private static final String HTML_ELEMENT_LIST = "LIST";
  private static final String HTML_ELEMENT_TABLE = "TABLE";
  private static final String HTML_ELEMENT_COMBOBOX = "COMBOBOX";

  /** Returns whether the target is html element. */
  public static boolean isHtmlTarget(@TargetType int type) {
    return ((type & MASK_TARGET_HTML_ELEMENT) != 0);
  }

  /** Returns whether the target is native macro granularity. */
  public static boolean isMacroGranularity(@TargetType int type) {
    return ((type & MASK_TARGET_NATIVE_MACRO_GRANULARITY) != 0);
  }

  /** Gets display name of HTML {@link TargetType}. Used to compose speaking feedback. */
  public static String htmlTargetToDisplayName(Context context, @TargetType int type) {
    int resId;
    switch (type) {
      case TARGET_HTML_ELEMENT_LINK:
        resId = R.string.display_name_link;
        break;
      case TARGET_HTML_ELEMENT_LIST:
        resId = R.string.display_name_list;
        break;
      case TARGET_HTML_ELEMENT_CONTROL:
        resId = R.string.display_name_control;
        break;
      case TARGET_HTML_ELEMENT_HEADING:
        resId = R.string.display_name_heading;
        break;
      case TARGET_HTML_ELEMENT_BUTTON:
        resId = R.string.display_name_button;
        break;
      case TARGET_HTML_ELEMENT_CHECKBOX:
        resId = R.string.display_name_checkbox;
        break;
      case TARGET_HTML_ELEMENT_ARIA_LANDMARK:
        resId = R.string.display_name_aria_landmark;
        break;
      case TARGET_HTML_ELEMENT_EDIT_FIELD:
        resId = R.string.display_name_edit_field;
        break;
      case TARGET_HTML_ELEMENT_FOCUSABLE_ITEM:
        resId = R.string.display_name_focusable_item;
        break;
      case TARGET_HTML_ELEMENT_HEADING_1:
        resId = R.string.display_name_heading_1;
        break;
      case TARGET_HTML_ELEMENT_HEADING_2:
        resId = R.string.display_name_heading_2;
        break;
      case TARGET_HTML_ELEMENT_HEADING_3:
        resId = R.string.display_name_heading_3;
        break;
      case TARGET_HTML_ELEMENT_HEADING_4:
        resId = R.string.display_name_heading_4;
        break;
      case TARGET_HTML_ELEMENT_HEADING_5:
        resId = R.string.display_name_heading_5;
        break;
      case TARGET_HTML_ELEMENT_HEADING_6:
        resId = R.string.display_name_heading_6;
        break;
      case TARGET_HTML_ELEMENT_GRAPHIC:
        resId = R.string.display_name_graphic;
        break;
      case TARGET_HTML_ELEMENT_LIST_ITEM:
        resId = R.string.display_name_list_item;
        break;
      case TARGET_HTML_ELEMENT_TABLE:
        resId = R.string.display_name_table;
        break;
      case TARGET_HTML_ELEMENT_COMBOBOX:
        resId = R.string.display_name_combobox;
        break;
      default:
        throw new IllegalArgumentException("Invalid target type.");
    }
    return context.getString(resId);
  }

  /** Gets display name of Native Macro {@link TargetType}. Used to compose speaking feedback. */
  public static String macroTargetToDisplayName(Context context, @TargetType int type) {
    int resId;
    switch (type) {
      case TARGET_HEADING:
        resId = R.string.display_name_heading;
        break;
      case TARGET_CONTROL:
        resId = R.string.display_name_control;
        break;
      case TARGET_LINK:
        resId = R.string.display_name_link;
        break;
      default:
        throw new IllegalArgumentException("Invalid target type.");
    }
    return context.getString(resId);
  }

  /**
   * Gets HTML element name of {@link TargetType}. Used as parameter to perform html navigation
   * action.
   */
  @Nullable
  public static String targetTypeToHtmlElement(@TargetType int targetType) {
    switch (targetType) {
      case TARGET_HTML_ELEMENT_LINK:
        return HTML_ELEMENT_LINK;
      case TARGET_HTML_ELEMENT_LIST:
        return HTML_ELEMENT_LIST;
      case TARGET_HTML_ELEMENT_CONTROL:
        return HTML_ELEMENT_CONTROL;
      case TARGET_HTML_ELEMENT_HEADING:
        return HTML_ELEMENT_HEADING;
      case TARGET_HTML_ELEMENT_BUTTON:
        return HTML_ELEMENT_BUTTON;
      case TARGET_HTML_ELEMENT_CHECKBOX:
        return HTML_ELEMENT_CHECKBOX;
      case TARGET_HTML_ELEMENT_ARIA_LANDMARK:
        return HTML_ELEMENT_ARIA_LANDMARK;
      case TARGET_HTML_ELEMENT_EDIT_FIELD:
        return HTML_ELEMENT_EDIT_FIELD;
      case TARGET_HTML_ELEMENT_FOCUSABLE_ITEM:
        return HTML_ELEMENT_FOCUSABLE_ITEM;
      case TARGET_HTML_ELEMENT_HEADING_1:
        return HTML_ELEMENT_HEADING_1;
      case TARGET_HTML_ELEMENT_HEADING_2:
        return HTML_ELEMENT_HEADING_2;
      case TARGET_HTML_ELEMENT_HEADING_3:
        return HTML_ELEMENT_HEADING_3;
      case TARGET_HTML_ELEMENT_HEADING_4:
        return HTML_ELEMENT_HEADING_4;
      case TARGET_HTML_ELEMENT_HEADING_5:
        return HTML_ELEMENT_HEADING_5;
      case TARGET_HTML_ELEMENT_HEADING_6:
        return HTML_ELEMENT_HEADING_6;
      case TARGET_HTML_ELEMENT_GRAPHIC:
        return HTML_ELEMENT_GRAPHIC;
      case TARGET_HTML_ELEMENT_LIST_ITEM:
        return HTML_ELEMENT_LIST_ITEM;
      case TARGET_HTML_ELEMENT_TABLE:
        return HTML_ELEMENT_TABLE;
      case TARGET_HTML_ELEMENT_COMBOBOX:
        return HTML_ELEMENT_COMBOBOX;
      case TARGET_DEFAULT:
        return "";
      default:
        return null;
    }
  }

  /** Gets node filter for non-html {@link TargetType}. */
  @Nullable
  public static Filter<AccessibilityNodeInfoCompat> createNodeFilter(
      @TargetType int target,
      @Nullable final Map<AccessibilityNodeInfoCompat, Boolean> speakingNodeCache) {
    if (NavigationTarget.isHtmlTarget(target)) {
      LogUtils.w(TAG, "Cannot define node filter for html target.");
      return null;
    }
    Filter<AccessibilityNodeInfoCompat> nodeFilter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return (node != null)
                && AccessibilityNodeInfoUtils.shouldFocusNode(node, speakingNodeCache);
          }
        };
    Filter<AccessibilityNodeInfoCompat> additionalCheckFilter = null;
    switch (target) {
      case TARGET_HEADING:
        additionalCheckFilter = AccessibilityNodeInfoUtils.FILTER_HEADING;
        break;
      case TARGET_CONTROL:
        additionalCheckFilter =
            AccessibilityNodeInfoUtils.getFilterIncludingChildren(
                AccessibilityNodeInfoUtils.FILTER_CONTROL);
        break;
      case TARGET_LINK:
        additionalCheckFilter = AccessibilityNodeInfoUtils.FILTER_LINK;
        break;
      default:
        // TARGET_DEFAULT:
        break;
    }
    if (additionalCheckFilter != null) {
      nodeFilter = nodeFilter.and(additionalCheckFilter);
    }
    return nodeFilter;
  }

  public static String targetTypeToString(@TargetType int targetType) {
    switch (targetType) {
      case TARGET_DEFAULT:
        return "TARGET_DEFAULT";
      case TARGET_HEADING:
        return "TARGET_HEADING";
      case TARGET_CONTROL:
        return "TARGET_CONTROL";
      case TARGET_LINK:
        return "TARGET_LINK";
      case TARGET_HTML_ELEMENT_LINK:
        return "TARGET_HTML_ELEMENT_LINK";
      case TARGET_HTML_ELEMENT_LIST:
        return "TARGET_HTML_ELEMENT_LIST";
      case TARGET_HTML_ELEMENT_CONTROL:
        return "TARGET_HTML_ELEMENT_CONTROL";
      case TARGET_HTML_ELEMENT_HEADING:
        return "TARGET_HTML_ELEMENT_HEADING";
      case TARGET_HTML_ELEMENT_BUTTON:
        return "TARGET_HTML_ELEMENT_BUTTON";
      case TARGET_HTML_ELEMENT_CHECKBOX:
        return "TARGET_HTML_ELEMENT_CHECKBOX";
      case TARGET_HTML_ELEMENT_ARIA_LANDMARK:
        return "TARGET_HTML_ELEMENT_ARIA_LANDMARK";
      case TARGET_HTML_ELEMENT_EDIT_FIELD:
        return "TARGET_HTML_ELEMENT_EDIT_FIELD";
      case TARGET_HTML_ELEMENT_FOCUSABLE_ITEM:
        return "TARGET_HTML_ELEMENT_FOCUSABLE_ITEM";
      case TARGET_HTML_ELEMENT_HEADING_1:
        return "TARGET_HTML_ELEMENT_HEADING_1";
      case TARGET_HTML_ELEMENT_HEADING_2:
        return "TARGET_HTML_ELEMENT_HEADING_2";
      case TARGET_HTML_ELEMENT_HEADING_3:
        return "TARGET_HTML_ELEMENT_HEADING_3";
      case TARGET_HTML_ELEMENT_HEADING_4:
        return "TARGET_HTML_ELEMENT_HEADING_4";
      case TARGET_HTML_ELEMENT_HEADING_5:
        return "TARGET_HTML_ELEMENT_HEADING_5";
      case TARGET_HTML_ELEMENT_HEADING_6:
        return "TARGET_HTML_ELEMENT_HEADING_6";
      case TARGET_HTML_ELEMENT_GRAPHIC:
        return "TARGET_HTML_ELEMENT_GRAPHIC";
      case TARGET_HTML_ELEMENT_LIST_ITEM:
        return "TARGET_HTML_ELEMENT_LIST_ITEM";
      case TARGET_HTML_ELEMENT_TABLE:
        return "TARGET_HTML_ELEMENT_TABLE";
      case TARGET_HTML_ELEMENT_COMBOBOX:
        return "TARGET_HTML_ELEMENT_COMBOBOX";
      case TARGET_WINDOW:
        return "TARGET_WINDOW";
      default:
        return "UNKNOWN";
    }
  }
}

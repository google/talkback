/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.talkback.menurules;

import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.TARGET_SPAN_CLASS;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItemBuilder;
import com.google.android.accessibility.utils.SpannableUtils;
import com.google.android.accessibility.utils.traversal.SpannableTraversalUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Menu population rule for views with Spannable link contents. */
public class RuleSpannables implements NodeMenuRule {

  private static final String TAG = "RuleSpannables";

  @Override
  public boolean accept(TalkBackService service, AccessibilityNodeInfoCompat node) {
    return SpannableTraversalUtils.hasTargetSpanInNodeTreeDescription(node, TARGET_SPAN_CLASS);
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      TalkBackService service,
      ContextMenuItemBuilder menuItemBuilder,
      AccessibilityNodeInfoCompat node,
      boolean includeAncestors) {
    final List<SpannableString> spannableStrings = new ArrayList<>();

    // Could copy node, and recycle it through *SpanMenuItemClickListener. But the
    // span-click-listener would rarely recycle the node, because most text has no spans, and thus
    // no click listeners to recycle copies. And even nodes with spans usually exit the menu through
    // some other menu-item other than span-click.
    // TODO: Refactor to provide a general menu-cleanup method.
    // TODO: When Robolectric copies extras bundle, add unit test.
    SpannableTraversalUtils.collectSpannableStringsWithTargetSpanInNodeDescriptionTree(
        node, // Root node of description tree
        TARGET_SPAN_CLASS, // Target span class
        spannableStrings // List of SpannableStrings collected
        );

    final List<ContextMenuItem> result = new ArrayList<>();
    for (SpannableString spannable : spannableStrings) {
      if (spannable == null) {
        continue;
      }
      final Object[] spans = spannable.getSpans(0, spannable.length(), TARGET_SPAN_CLASS);
      if ((spans == null) || (spans.length == 0)) {
        continue;
      }
      for (int i = 0; i < spans.length; i++) {
        final Object span = spans[i];
        if (span == null) {
          continue;
        }
        ContextMenuItem menuItem = null;
        if (span instanceof URLSpan) {
          // For ir-relative UrlSpans, open the link with browser directly.
          menuItem =
              createMenuItemForUrlSpan(service, menuItemBuilder, i, spannable, (URLSpan) span);
        }
        // For other kinds of ClickableSpans(including relative UrlSpan) from O, activate it with
        // ClickableSpan.onClick(null).
        if (menuItem == null && span instanceof ClickableSpan) {
          menuItem =
              createMenuItemForClickableSpan(
                  service, menuItemBuilder, i, spannable, (ClickableSpan) span);
        }
        if (menuItem != null) {
          result.add(menuItem);
        }
      }
    }
    return result;
  }

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.links);
  }

  @Override
  public boolean canCollapseMenu() {
    return true;
  }

  /**
   * Creates a menu item for URLSpan. <strong>Note: </strong> This method will not create menu item
   * for relative URLs.
   */
  private @Nullable ContextMenuItem createMenuItemForUrlSpan(
      Context context,
      ContextMenuItemBuilder menuItemBuilder,
      int itemId,
      Spannable spannable,
      URLSpan span) {
    final String url = span.getURL();
    final int start = spannable.getSpanStart(span);
    final int end = spannable.getSpanEnd(span);
    if (start < 0 || end < 0) {
      return null;
    }
    final CharSequence label = spannable.subSequence(start, end);
    if (TextUtils.isEmpty(url) || TextUtils.isEmpty(label)) {
      return null;
    }

    final Uri uri = Uri.parse(url);
    if (uri.isRelative()) {
      // Generally, only absolute URIs are resolvable to an activity
      return null;
    }

    // Strip out ClickableSpans/UrlSpans from the label text.
    // A11y framework has changed how it handles double-tap from O. It's possible that double-tap
    // on the menu item will invoke ClickableSpans in the label text instead of calling
    // MenuItemClickListener. Thus we should remove ClickableSpans from label text.
    // Also apply this rule to pre-O in order to have consistent text appearance.
    SpannableUtils.stripTargetSpanFromText(label, TARGET_SPAN_CLASS);
    final ContextMenuItem item =
        menuItemBuilder.createMenuItem(context, R.id.group_links, itemId, Menu.NONE, label);
    item.setOnMenuItemClickListener(new UrlSpanMenuItemClickListener(context, uri));
    return item;
  }

  /** Creates a menu item for ClickableSpan. */
  private @Nullable ContextMenuItem createMenuItemForClickableSpan(
      Context context,
      ContextMenuItemBuilder menuItemBuilder,
      int itemId,
      Spannable spannable,
      ClickableSpan clickableSpan) {
    final int start = spannable.getSpanStart(clickableSpan);
    final int end = spannable.getSpanEnd(clickableSpan);
    if (start < 0 || end < 0) {
      return null;
    }
    final CharSequence label = spannable.subSequence(start, end);
    if (TextUtils.isEmpty(label)) {
      return null;
    }

    SpannableUtils.stripTargetSpanFromText(label, TARGET_SPAN_CLASS);
    final ContextMenuItem item =
        menuItemBuilder.createMenuItem(context, R.id.group_links, itemId, Menu.NONE, label);
    item.setOnMenuItemClickListener(new ClickableSpanMenuItemClickListener(clickableSpan));
    return item;
  }

  /** Click listener for menu items representing {@link URLSpan}s. */
  private static class UrlSpanMenuItemClickListener implements MenuItem.OnMenuItemClickListener {

    final Context context;
    final Uri uri;

    public UrlSpanMenuItemClickListener(Context context, Uri uri) {
      this.context = context;
      this.uri = uri;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      if (context == null) {
        return false;
      }

      final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      try {
        context.startActivity(intent);
      } catch (ActivityNotFoundException e) {
        return false;
      }

      return true;
    }
  }

  /** Click listener for menu items representing {@link ClickableSpan}s. */
  private static class ClickableSpanMenuItemClickListener
      implements MenuItem.OnMenuItemClickListener {
    final ClickableSpan clickableSpan;

    public ClickableSpanMenuItemClickListener(ClickableSpan clickableSpan) {
      this.clickableSpan = clickableSpan;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      if (clickableSpan != null) {
        // TODO: We accept ClickableSpans from content descriptions, which is not
        // expected by framework, but already "abused" by some apps. To avoid unexpected anonymous
        // crashes, wrap the ClickableSpan.onClick() with try-catch structure.
        try {
          // The View argument is ignored when the ClickableSpan.onClick(View) is invoked
          // from an accessibility service.
          clickableSpan.onClick(null);
        } catch (Exception e) {
          LogUtils.e(TAG, "Failed to invoke ClickableSpan: %s\n%s", item.getTitle(), e);
        }
        return true;
      }

      return false;
    }
  }
}

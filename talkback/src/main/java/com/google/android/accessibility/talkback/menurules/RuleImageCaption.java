/*
 * Copyright (C) 2021 Google Inc.
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

import static com.google.android.accessibility.talkback.contextmenu.TalkbackMenuProcessor.ORDER_IMAGE_CAPTION;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.AbstractOnContextMenuItemClickListener;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import java.util.ArrayList;
import java.util.List;

/** Performs image captions from menu. */
public class RuleImageCaption extends NodeMenuRule {

  private final Pipeline.FeedbackReturner pipeline;
  private final TalkBackAnalytics analytics;

  public RuleImageCaption(Pipeline.FeedbackReturner pipeline, TalkBackAnalytics analytics) {
    super(
        R.string.pref_show_context_menu_image_caption_setting_key,
        R.bool.pref_show_context_menu_image_caption_default);
    this.pipeline = pipeline;
    this.analytics = analytics;
  }

  @Override
  public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
    // Manual-caption item is shown for ALL views if the device can run image caption.
    return ImageCaptioner.supportsImageCaption(context);
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      Context context, AccessibilityNodeInfoCompat node, boolean includeAncestors) {
    List<ContextMenuItem> items = new ArrayList<>();

    final ImageCaptionMenuItemOnClickListener menuItemOnClickListener =
        new ImageCaptionMenuItemOnClickListener(node, pipeline, analytics);

    ContextMenuItem item =
        ContextMenu.createMenuItem(
            context,
            Menu.NONE,
            R.id.image_caption_menu,
            ORDER_IMAGE_CAPTION,
            context.getString(R.string.title_image_caption));
    item.setOnMenuItemClickListener(menuItemOnClickListener);
    item.setSkipRefocusEvents(true);
    item.setSkipWindowEvents(true);
    item.setDeferredType(DeferredType.WINDOWS_STABLE);
    items.add(item);

    return items;
  }

  @Override
  CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_image_caption);
  }

  @Override
  boolean isSubMenu() {
    return false;
  }

  private static class ImageCaptionMenuItemOnClickListener
      extends AbstractOnContextMenuItemClickListener {

    public ImageCaptionMenuItemOnClickListener(
        AccessibilityNodeInfoCompat node,
        Pipeline.FeedbackReturner pipeline,
        TalkBackAnalytics analytics) {
      super(node, pipeline, analytics);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.confirmDownloadAndPerformCaptions(node));
      return true;
    }
  }
}

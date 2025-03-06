/*
 * Copyright (C) 2013 Google Inc.
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

import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.MENU_TYPE_GRANULARITY;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.input.CursorGranularity.LINE;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.view.Menu;
import android.view.MenuItem;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.AbstractOnContextMenuItemClickListener;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import com.google.android.accessibility.talkback.selector.SelectorController;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Adds supported granularities to the talkback context menu. If the target node contains web
 * content, adds web-specific granularities.
 */
public class RuleGranularity extends NodeMenuRule {
  // TODO: Combine enum of Granularity at RuleGranularity & CursorGranularity
  @VisibleForTesting
  enum GranularitySetting {
    CHARACTERS(
        R.string.pref_show_navigation_menu_characters_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_character,
        R.bool.pref_show_navigation_menu_characters_default),
    WORDS(
        R.string.pref_show_navigation_menu_words_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_word,
        R.bool.pref_show_navigation_menu_words_default),
    LINES(
        R.string.pref_show_navigation_menu_lines_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_line,
        R.bool.pref_show_navigation_menu_lines_default),
    PARAGRAPHS(
        R.string.pref_show_navigation_menu_paragraphs_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_paragraph,
        R.bool.pref_show_navigation_menu_paragraphs_default),
    HEADINGS(
        R.string.pref_show_navigation_menu_headings_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_native_heading,
        R.bool.pref_show_navigation_menu_headings_default),
    CONTROLS(
        R.string.pref_show_navigation_menu_controls_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_native_control,
        R.bool.pref_show_navigation_menu_controls_default),
    LINKS(
        R.string.pref_show_navigation_menu_links_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_native_link,
        R.bool.pref_show_navigation_menu_links_default),
    WEB_HEADINGS(
        R.string.pref_show_navigation_menu_headings_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_web_heading,
        R.bool.pref_show_navigation_menu_headings_default),
    WEB_CONTROLS(
        R.string.pref_show_navigation_menu_controls_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_web_control,
        R.bool.pref_show_navigation_menu_controls_default),
    WEB_LINKS(
        R.string.pref_show_navigation_menu_links_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_web_link,
        R.bool.pref_show_navigation_menu_links_default),
    WEB_LANDMARKS(
        R.string.pref_show_navigation_menu_landmarks_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_web_landmark,
        R.bool.pref_show_navigation_menu_landmarks_default),
    WINDOW(
        R.string.pref_show_navigation_menu_window_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_window,
        R.bool.pref_show_navigation_menu_window_default),
    CONTAINER(
        R.string.pref_show_navigation_menu_container_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_container,
        R.bool.pref_show_navigation_menu_container_default),
    DEFAULT_NAVIGATION(
        R.string.pref_show_navigation_menu_granularity_default_setting_key,
        com.google.android.accessibility.utils.R.string.granularity_default,
        R.bool.pref_show_navigation_menu_granularity_default);

    /** The preference key in the granularity settings. */
    final int prefKeyResId;
    /** The menu item of granularity of preference key */
    final int granularityResId;
    /** The setting is on or off in default. */
    final int defaultValueResId;

    /** Constructor of a new Setting with the specific preference key and value. */
    GranularitySetting(int prefKeyResId, int granularityResId, int defaultValueResId) {
      this.prefKeyResId = prefKeyResId;
      this.granularityResId = granularityResId;
      this.defaultValueResId = defaultValueResId;
    }

    /** Returns a Setting associated with the given title string. */
    public static @Nullable GranularitySetting getGranularityFromResId(int resId) {
      for (GranularitySetting setting : values()) {
        if (setting.granularityResId == resId) {
          return setting;
        }
      }
      return null;
    }
  }

  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;
  TalkBackAnalytics analytics;

  public RuleGranularity(
      Pipeline.FeedbackReturner pipeline, ActorState actorState, TalkBackAnalytics analytics) {
    super(
        R.string.pref_show_context_menu_granularity_setting_key,
        R.bool.pref_show_context_menu_granularity_default);
    this.pipeline = pipeline;
    this.actorState = actorState;
    this.analytics = analytics;
  }

  @Override
  public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
    // RuleGranularity doesn't use the flag includeAncestors, so the value doesn't matter.
    return !getMenuItemsForNode(context, node, /* includeAncestors= */ false).isEmpty();
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      Context context, AccessibilityNodeInfoCompat node, boolean includeAncestors) {
    final CursorGranularity current = actorState.getDirectionNavigation().getGranularityAt(node);
    final List<ContextMenuItem> items = new ArrayList<>();
    final boolean hasWebContent = WebInterfaceUtils.hasNavigableWebContent(node);

    final GranularityMenuItemClickListener clickListener =
        new GranularityMenuItemClickListener(context, pipeline, node, analytics);

    for (CursorGranularity granularity : CursorGranularity.values()) {
      if (!isShowItemByGranularity(context, granularity, actorState)) {
        continue;
      }

      if (granularity.isWebGranularity() && !hasWebContent) {
        continue;
      }

      if (granularity.isNativeMacroGranularity() && hasWebContent) {
        continue;
      }

      ContextMenuItem item =
          ContextMenu.createMenuItem(
              context,
              Menu.NONE,
              granularity.resourceId,
              Menu.NONE,
              context.getString(granularity.resourceId));
      item.setOnMenuItemClickListener(clickListener);
      item.setCheckable(true);
      item.setChecked(granularity.equals(current));
      // Skip window and focued event for granularity options, REFERTO.
      item.setSkipRefocusEvents(true);
      item.setSkipWindowEvents(true);

      // Items are added in "natural" order, e.g. object first.
      items.add(item);
    }

    for (ContextMenuItem item : items) {
      item.setDeferredType(DeferredType.WINDOWS_STABLE);
    }

    return items;
  }

  @Override
  public CharSequence getUserFriendlyMenuName(Context context) {
    return context.getString(R.string.title_granularity);
  }

  /** Listener may be shared by multi-contextItems. */
  private static class GranularityMenuItemClickListener
      extends AbstractOnContextMenuItemClickListener {

    private final Context context;

    public GranularityMenuItemClickListener(
        Context context,
        Pipeline.FeedbackReturner pipeline,
        AccessibilityNodeInfoCompat node,
        TalkBackAnalytics analytics) {
      super(node, pipeline, analytics);
      this.context = context;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance for menu events.
      try {
        if (item == null) {
          return false;
        }

        final int itemId = item.getItemId();
        analytics.onLocalContextMenuAction(MENU_TYPE_GRANULARITY, itemId);

        final CursorGranularity granularity = CursorGranularity.fromResourceId(itemId);
        if (granularity == null) {
          return false;
        }

        boolean result =
            pipeline.returnFeedback(
                eventId, Feedback.granularity(granularity).setFromUser(true).setTargetNode(node));
        if (result) {
          // Granularities are flattened into the selector menu and swiping up/down to move focus
          // according to the selected granularity in the selector. To move at specific granularity,
          // the granularity needs to sync to the selector.
          SelectorController.updateSettingPrefForGranularity(context, granularity);
        }
        return result;
      } finally {
        clear();
      }
    }
  }

  private static boolean isShowItemByGranularity(
      Context service, CursorGranularity granularity, ActorState actorState) {
    GranularitySetting granularitySetting =
        GranularitySetting.getGranularityFromResId(granularity.resourceId);
    if (granularitySetting == null) {
      return false;
    }
    // TODO: As the text selection for line granularity movement does not work,
    // we mask off the LINE granularity temporarily.
    if (granularity == LINE
        && actorState.getDirectionNavigation().isSelectionModeActive()
        && !FeatureSupport.supportInputConnectionByA11yService()) {
      return false;
    }

    return isShowItemByGranularity(
        service, granularitySetting.prefKeyResId, granularitySetting.defaultValueResId);
  }

  private static boolean isShowItemByGranularity(
      Context service, int keyResId, int keyDefaultResId) {
    final Resources res = service.getResources();
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);
    return prefs.getBoolean(res.getString(keyResId), res.getBoolean(keyDefaultResId));
  }
}

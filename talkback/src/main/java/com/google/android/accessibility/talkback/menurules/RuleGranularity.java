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
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.Menu;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.CursorGranularityManager;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.AbstractOnContextMenuItemClickListener;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import com.google.android.accessibility.talkback.controller.SelectorController;
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
  private enum GranularitySetting {
    CHARACTERS(
        R.string.pref_show_navigation_menu_characters_setting_key,
        R.string.granularity_character,
        R.bool.pref_show_navigation_menu_characters_default),
    WORDS(
        R.string.pref_show_navigation_menu_words_setting_key,
        R.string.granularity_word,
        R.bool.pref_show_navigation_menu_words_default),
    LINES(
        R.string.pref_show_navigation_menu_lines_setting_key,
        R.string.granularity_line,
        R.bool.pref_show_navigation_menu_lines_default),
    PARAGRAPHS(
        R.string.pref_show_navigation_menu_paragraphs_setting_key,
        R.string.granularity_paragraph,
        R.bool.pref_show_navigation_menu_paragraphs_default),
    HEADINGS(
        R.string.pref_show_navigation_menu_headings_setting_key,
        R.string.granularity_native_heading,
        R.bool.pref_show_navigation_menu_headings_default),
    CONTROLS(
        R.string.pref_show_navigation_menu_controls_setting_key,
        R.string.granularity_native_control,
        R.bool.pref_show_navigation_menu_controls_default),
    LINKS(
        R.string.pref_show_navigation_menu_links_setting_key,
        R.string.granularity_native_link,
        R.bool.pref_show_navigation_menu_links_default),
    WEB_HEADINGS(
        R.string.pref_show_navigation_menu_headings_setting_key,
        R.string.granularity_web_heading,
        R.bool.pref_show_navigation_menu_headings_default),
    WEB_CONTROLS(
        R.string.pref_show_navigation_menu_controls_setting_key,
        R.string.granularity_web_control,
        R.bool.pref_show_navigation_menu_controls_default),
    WEB_LINKS(
        R.string.pref_show_navigation_menu_links_setting_key,
        R.string.granularity_web_link,
        R.bool.pref_show_navigation_menu_links_default),
    WEB_LANDMARKS(
        R.string.pref_show_navigation_menu_landmarks_setting_key,
        R.string.granularity_web_landmark,
        R.bool.pref_show_navigation_menu_landmarks_default),
    WEB_SPECIAL_CONTENT(
        R.string.pref_show_navigation_menu_special_content_setting_key,
        R.string.granularity_pseudo_web_special_content,
        R.bool.pref_show_navigation_menu_special_content_default),
    OTHER_WEB_NAVIGATION(
        R.string.pref_show_navigation_menu_other_web_navigation_setting_key,
        R.string.title_other_web_navigation,
        R.bool.pref_show_navigation_menu_other_web_navigation_default),
    DEFAULT_NAVIGATION(
        R.string.pref_show_navigation_menu_granularity_default_setting_key,
        R.string.granularity_default,
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
  public boolean accept(AccessibilityService service, AccessibilityNodeInfoCompat node) {
    EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance for menu events.
    return !CursorGranularityManager.getSupportedGranularities(service, node, eventId).isEmpty();
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      AccessibilityService service, AccessibilityNodeInfoCompat node, boolean includeAncestors) {
    EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance for menu events.
    final CursorGranularity current = actorState.getDirectionNavigation().getGranularityAt(node);
    final List<ContextMenuItem> items = new ArrayList<>();
    final List<CursorGranularity> granularities =
        CursorGranularityManager.getSupportedGranularities(service, node, eventId);
    final boolean hasWebContent = WebInterfaceUtils.hasNavigableWebContent(node);

    // Don't populate the menu if only object is supported.
    if (granularities.size() == 1) {
      return items;
    }

    final GranularityMenuItemClickListener clickListener =
        new GranularityMenuItemClickListener(service, pipeline, node, hasWebContent, analytics);

    for (CursorGranularity granularity : granularities) {
      if (!isShowItemByGranularity(service, granularity)) {
        continue;
      }

      ContextMenuItem item =
          ContextMenu.createMenuItem(
              service,
              Menu.NONE,
              granularity.resourceId,
              Menu.NONE,
              service.getString(granularity.resourceId));
      item.setOnMenuItemClickListener(clickListener);
      item.setCheckable(true);
      item.setChecked(granularity.equals(current));
      // Skip window and focued event for granularity options, REFERTO.
      item.setSkipRefocusEvents(true);
      item.setSkipWindowEvents(true);

      // Items are added in "natural" order, e.g. object first.
      items.add(item);
    }

    if (hasWebContent) {
      // Web content support navigation at a pseudo granularity for
      // entering special content like math or tables. This must be
      // special cased as it doesn't fit the semantics of an actual
      // granularity.

      // Landmark granularity will be available for webviews only via Talkback menu and so it is
      // added separately from the granularities list.
      if (isShowItemByGranularity(
          service,
          R.string.pref_show_navigation_menu_landmarks_setting_key,
          R.bool.pref_show_navigation_menu_landmarks_default)) {
        ContextMenuItem landmark =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                CursorGranularity.WEB_LANDMARK.resourceId,
                Menu.NONE,
                service.getString(R.string.granularity_web_landmark));
        landmark.setOnMenuItemClickListener(clickListener);
        items.add(landmark);
      }

      if (isShowItemByGranularity(
          service,
          R.string.pref_show_navigation_menu_special_content_setting_key,
          R.bool.pref_show_navigation_menu_special_content_default)) {
        ContextMenuItem specialContent =
            ContextMenu.createMenuItem(
                service,
                Menu.NONE,
                R.id.pseudo_web_special_content,
                Menu.NONE,
                service.getString(R.string.granularity_pseudo_web_special_content));
        specialContent.setOnMenuItemClickListener(clickListener);
        items.add(specialContent);
      }
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
    private final boolean hasWebContent;

    public GranularityMenuItemClickListener(
        Context context,
        Pipeline.FeedbackReturner pipeline,
        AccessibilityNodeInfoCompat node,
        boolean hasWebContent,
        TalkBackAnalytics analytics) {
      super(node, pipeline, analytics);
      this.context = context;
      this.hasWebContent = hasWebContent;
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

        if (itemId == R.id.pseudo_web_special_content) {
          // If the user chooses to enter special web content, notify
          // ChromeVox that the user entered this navigation mode and
          // send further navigation movements at the default
          // granularity.
          // TODO: Check if this is still needed.
          // Re-use FocusDirection and WebAction at Pipeline
          if (pipeline.returnFeedback(eventId, Feedback.granularity(DEFAULT))) {
            pipeline.returnFeedback(
                eventId,
                Feedback.navigateSpecialWeb(
                    node, /* enabled= */ true, /* updateFocusHistory= */ true));
            return true;
          }
          return false;
        }

        final CursorGranularity granularity = CursorGranularity.fromResourceId(itemId);
        if (granularity == null) {
          return false;
        } else if (hasWebContent && granularity == CursorGranularity.DEFAULT) {
          // When the user switches to default granularity, always
          // inform ChromeVox of this change so it can exit special
          // content navigation mode if applicable. Sending this even
          // when that mode hasn't been entered is fine and is simply
          // a no-op on the ChromeVox side.
          // TODO: Check if this is still needed.
          pipeline.returnFeedback(
              eventId,
              Feedback.navigateSpecialWeb(
                  node, /* enabled= */ false, /* updateFocusHistory= */ true));
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

  private static boolean isShowItemByGranularity(Context service, CursorGranularity granularity) {
    final Resources res = service.getResources();
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);

    GranularitySetting granularitySetting =
        GranularitySetting.getGranularityFromResId(granularity.resourceId);
    if (granularitySetting == null) {
      return false;
    }

    return prefs.getBoolean(
        res.getString(granularitySetting.prefKeyResId),
        res.getBoolean(granularitySetting.defaultValueResId));
  }

  private static boolean isShowItemByGranularity(
      Context service, int keyResId, int keyDefaultResId) {
    final Resources res = service.getResources();
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);
    return prefs.getBoolean(res.getString(keyResId), res.getBoolean(keyDefaultResId));
  }
}

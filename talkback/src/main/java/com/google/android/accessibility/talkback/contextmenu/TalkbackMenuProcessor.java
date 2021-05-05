/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.content.SharedPreferences;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.Menu;
import android.view.MenuInflater;
import androidx.annotation.BoolRes;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.actor.DimScreenActor;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleProcessor;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.ScreenMonitor;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Configure dynamic menu items on the talkback context menu. */
public class TalkbackMenuProcessor {

  private static final String TAG = "TalkbackMenuProcessor";
  
  /*
   * Talkback context menu items order
   * Reserves for context_menu.xml to implement
   * ORDER_READ_FROM_TOP = 8;
   * ORDER_READ_FROM_NEXT_ITEM = 9;
   * ORDER_COPY_LAST_SPOKEN_PHRASE = 10;
   * ORDER_SPELL_LAST_SPOKEN_PHRASE = 11;
   * ORDER_REPEAT_LAST_SPOKEN_PHRASE = 12;
   * ORDER_VERBOSITY = 13;
   * ORDER_AUDIO_DUCKING = 16;
   * ORDER_SOUND_FEEDBACK = 17;
   * ORDER_VIBRATION_FEEDBACK = 18;
   * ORDER_SCREEN_SEARCH = 19;
   * ORDER_VOICE_COMMANDS = 21;
   * ORDER_TALKBACK_SETTINGS = 22;
   * ORDER_TTS_SETTINGS = 23;
   * ORDER_SUSPEND_TALKBACK = 24;
   */
  private static final int ORDER_ACTIONS = 1;
  private static final int ORDER_EDIT_OPTIONS = 2;
  private static final int ORDER_LINKS = 3;
  private static final int ORDER_PAGE_NAVIGATION = 4;
  private static final int ORDER_LABELS = 5;
  private static final int ORDER_NAVIGATION = 7;

  private static final int ORDER_LANGUAGES = 14;

  private static final int ORDER_SHOW_HIDE_SCREEN = 20;

  private static final int ORDER_SYSTEM_ACTIONS = 25;

  private final TalkBackService service;
  private final ActorState actorState;
  private final Pipeline.FeedbackReturner pipeline;
  private final NodeMenuRuleProcessor nodeMenuRuleProcessor;
  private final AccessibilityNodeInfoCompat currentNode;

  // TODO: Reduces dependency on TalkBackService to dependency on Context.
  public TalkbackMenuProcessor(
      TalkBackService service,
      ActorState actorState,
      Pipeline.FeedbackReturner pipeline,
      NodeMenuRuleProcessor nodeMenuRuleProcessor,
      AccessibilityNodeInfoCompat currentNode) {
    this.service = service;
    this.actorState = actorState;
    this.pipeline = pipeline;
    this.nodeMenuRuleProcessor = nodeMenuRuleProcessor;
    this.currentNode = currentNode;
  }

  /**
   * Populates a {@link Menu} with dynamic items relevant to the current global TalkBack state.
   *
   * @param menu The menu to populate.
   * @return {@code true} if successful, {@code false} otherwise.
   */
  public boolean prepareMenu(ContextMenu menu) {
    // Apply attributes to menu items.
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);

    // Custom Action
    addItemOrSubMenuForCurrentNode(
        menu, R.id.custom_action_menu, R.string.title_custom_action, ORDER_ACTIONS);
    // Editing option
    addItemOrSubMenuForCurrentNode(
        menu, R.id.editing_menu, R.string.title_edittext_controls, ORDER_EDIT_OPTIONS);
    // Links
    addItemOrSubMenuForCurrentNode(menu, R.id.links_menu, R.string.links, ORDER_LINKS);
    // Page Navigation
    addItemOrSubMenuForCurrentNode(
        menu, R.id.viewpager_menu, R.string.title_viewpager_controls, ORDER_PAGE_NAVIGATION);
    // Labeling
    addItemOrSubMenuForCurrentNode(
        menu, R.id.labeling_breakout_add_label, R.string.title_labeling_controls, ORDER_LABELS);

    // Navigation
    addItemOrSubMenuForCurrentNode(
        menu, R.id.granularity_menu, R.string.title_granularity, ORDER_NAVIGATION);

    // Read From & Last Phrase Spoken & screen search at context_menu.xml
    addContextMenuXMLMenu(menu);

    // Show/hide screen
    addDimOrBrightenScreen(menu);
    // Language
    addLanguageMenuIfValid(menu);
    // System Action
    addWindowActionMenu(menu);

    setMenuItemShowsDialog(
        menu,
        R.id.enable_dimming,
        prefs.getBoolean(
            service.getString(R.string.pref_show_dim_screen_confirmation_dialog), true));

    setMenuItemShowsDialog(
        menu,
        R.id.read_from_top,
        prefs.getBoolean(
            service.getString(R.string.pref_show_continuous_reading_mode_dialog), true));
    setMenuItemShowsDialog(
        menu,
        R.id.read_from_current,
        prefs.getBoolean(
            service.getString(R.string.pref_show_continuous_reading_mode_dialog), true));

    setMenuItemDeferredType(menu, R.id.screen_search, DeferredType.ACCESSIBILITY_FOCUS_RECEIVED);
    setSkipRefocusAndWindowAnnounce(menu, R.id.screen_search, true);

    setMenuItemDeferredType(menu, R.id.read_from_top, DeferredType.WINDOWS_STABLE);
    setSkipRefocusAndWindowAnnounce(menu, R.id.read_from_top, true);

    setMenuItemDeferredType(
        menu, R.id.read_from_current, DeferredType.ACCESSIBILITY_FOCUS_RECEIVED);
    setSkipRefocusAndWindowAnnounce(menu, R.id.read_from_current, true);

    setSkipRefocusAndWindowAnnounce(menu, R.id.spell_last_utterance, true);
    setSkipRefocusAndWindowAnnounce(menu, R.id.repeat_last_utterance, true);
    setSkipRefocusAndWindowAnnounce(menu, R.id.copy_last_utterance_to_clipboard, true);
    setSkipRefocusAndWindowAnnounce(menu, R.id.audio_ducking, true);
    setSkipRefocusAndWindowAnnounce(menu, R.id.sound_feedback, true);
    setSkipRefocusAndWindowAnnounce(menu, R.id.vibration_feedback, true);

    menu.sortItemsByOrder();

    return true;
  }

  private boolean showMenuItem(@StringRes int prefKeyId, @BoolRes int defaultValueResId) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);
    return prefs.getBoolean(
        service.getString(prefKeyId), service.getResources().getBoolean(defaultValueResId));
  }

  // Adds context_menu.xml, such as read from, last spoken, find on screen sub menu, .., to menu
  private void addContextMenuXMLMenu(ContextMenu menu) {
    new MenuInflater(service).inflate(R.menu.context_menu, menu);

    if (FeatureSupport.hasAccessibilityShortcut(service)
        || !showMenuItem(
            R.string.pref_show_context_menu_pause_feedback_setting_key,
            R.bool.pref_show_context_menu_pause_feedback_default)) {
      menu.removeItem(R.id.pause_feedback);
    }

    // Removes talkback TTS settings if phone is locked, Setup Wizard doesn't complete or uncheck
    // show item.
    if (ScreenMonitor.isDeviceLocked(service)
        || !SettingsUtils.allowLinksOutOfSettings(service)
        || !showMenuItem(
            R.string.pref_show_context_menu_tts_settings_setting_key,
            R.bool.pref_show_context_menu_tts_settings_default)) {
      menu.removeItem(R.id.tts_settings);
    }

    if (!showMenuItem(
        R.string.pref_show_context_menu_read_from_top_setting_key,
        R.bool.pref_show_context_menu_read_from_top_default)) {
      menu.removeItem(R.id.read_from_top);
    }

    if (!showMenuItem(
        R.string.pref_show_context_menu_read_from_current_setting_key,
        R.bool.pref_show_context_menu_read_from_current_default)) {
      menu.removeItem(R.id.read_from_current);
    }

    if (!showMenuItem(
        R.string.pref_show_context_menu_copy_last_spoken_phrase_setting_key,
        R.bool.pref_show_context_menu_copy_last_spoken_phrase_default)) {
      menu.removeItem(R.id.copy_last_utterance_to_clipboard);
    }

    if (!showMenuItem(
        R.string.pref_show_context_menu_spell_last_spoken_phrase_setting_key,
        R.bool.pref_show_context_menu_spell_last_spoken_phrase_default)) {
      menu.removeItem(R.id.spell_last_utterance);
    }

    if (!showMenuItem(
        R.string.pref_show_context_menu_repeat_last_spoken_phrase_setting_key,
        R.bool.pref_show_context_menu_repeat_last_spoken_phrase_default)) {
      menu.removeItem(R.id.repeat_last_utterance);
    }

    // Removes screen search if this is watch or uncheck show item.
    if (FeatureSupport.isWatch(service)
        || !showMenuItem(
            R.string.pref_show_context_menu_find_on_screen_setting_key,
            R.bool.pref_show_context_menu_find_on_screen_default)) {
      menu.removeItem(R.id.screen_search);
    }

    // Removes voice command if phone is locked, Setup Wizard doesn't complete or uncheck
    // show item.
    if (!TalkBackService.ENABLE_VOICE_COMMANDS
        || ScreenMonitor.isDeviceLocked(service)
        || !SettingsUtils.allowLinksOutOfSettings(service)
        || !showMenuItem(
            R.string.pref_show_context_menu_voice_commands_setting_key,
            R.bool.pref_show_context_menu_voice_commands_default)) {
      menu.removeItem(R.id.voice_commands);
    }

    // Removes talkback settings if phone is locked or uncheck show item.
    if (ScreenMonitor.isDeviceLocked(service)
        || !showMenuItem(
            R.string.pref_show_context_menu_talkback_settings_setting_key,
            R.bool.pref_show_context_menu_talkback_settings_default)) {
      menu.removeItem(R.id.talkback_settings);
    }

    if (ScreenMonitor.isDeviceLocked(service)
        || !showMenuItem(
            R.string.pref_show_context_menu_verbosity_setting_key,
            R.bool.pref_show_context_menu_verbosity_default)) {
      menu.removeItem(R.id.verbosity);
    }

    if (showMenuItem(
        R.string.pref_show_context_menu_audio_ducking_setting_key,
        R.bool.pref_show_context_menu_audio_ducking_default)) {
      computeTitleForMenuItem(
          menu.findItem(R.id.audio_ducking),
          R.string.audio_focus_state,
          R.string.pref_use_audio_focus_key,
          R.bool.pref_use_audio_focus_default);
    } else {
      menu.removeItem(R.id.audio_ducking);
    }

    if (showMenuItem(
        R.string.pref_show_context_menu_sound_feedback_setting_key,
        R.bool.pref_show_context_menu_sound_feedback_default)) {
      computeTitleForMenuItem(
          menu.findItem(R.id.sound_feedback),
          R.string.sound_feedback_state,
          R.string.pref_soundback_key,
          R.bool.pref_soundback_default);
    } else {
      menu.removeItem(R.id.sound_feedback);
    }

    if (FeatureSupport.isVibratorSupported(service)
        && showMenuItem(
            R.string.pref_show_context_menu_vibration_feedback_setting_key,
            R.bool.pref_show_context_menu_vibration_feedback_default)) {
      computeTitleForMenuItem(
          menu.findItem(R.id.vibration_feedback),
          R.string.vibration_feedback_state,
          R.string.pref_vibration_key,
          R.bool.pref_vibration_default);
    } else {
      menu.removeItem(R.id.vibration_feedback);
    }
  }

  /**
   * Updates title of menu item by current status, on or off, of item.
   *
   * @param item The menu item to update title.
   * @param titleResId The menu title to update.
   * @param prefKeyResId The pref key id of status of feedback that item likes to change.
   * @param defaultPrefKeyResId The default setting of status of feedback.
   */
  private void computeTitleForMenuItem(
      ContextMenuItem item, int titleResId, int prefKeyResId, int defaultPrefKeyResId) {
    boolean preferenceValue =
        SharedPreferencesUtils.getBooleanPref(
            SharedPreferencesUtils.getSharedPreferences(service),
            service.getResources(),
            prefKeyResId,
            defaultPrefKeyResId);

    String newTitle =
        service.getString(
            titleResId,
            preferenceValue
                ? service.getString(R.string.value_on)
                : service.getString(R.string.value_off));

    item.setTitle(newTitle);
  }

  /**
   * Adds sub menu to menu by current node
   *
   * @param menu The menu to add sub menu.
   * @param itemId The item Id on menu.
   * @param titleId The title Id on sub menu.
   */
  private void addItemOrSubMenuForCurrentNode(
      ContextMenu menu, int itemId, int titleId, int itemOrder) {
    menu.removeItem(itemId);

    // If itemId is to add label item, removes edit label item after removes add label,
    if (itemId == R.id.labeling_breakout_add_label) {
      menu.removeItem(R.id.labeling_breakout_edit_label);
    }

    if (!nodeMenuRuleProcessor.isEnabled(itemId)) {
      return;
    }

    if (currentNode == null) {
      return;
    }
    AccessibilityNodeInfoCompat node = AccessibilityNodeInfoUtils.obtain(currentNode);

    try {
      nodeMenuRuleProcessor.prepareTalkbackMenuForNode(menu, node, itemId, titleId, itemOrder);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
  }

  private void addLanguageMenuIfValid(ContextMenu menu) {
    // If the menu already has languages menu item, remove it. This will avoid multiple language
    // options appearing in the global context menu. This will dismiss the menu and clears the menu.
    menu.removeItem(R.id.language_menu);

    if (!showMenuItem(
        R.string.pref_show_context_menu_language_setting_key,
        R.bool.pref_show_context_menu_language_default)) {
      return;
    }

    // The Languages menu item will be shown only if there is more than 1 language installed and
    // is populated dynamically.
    boolean shouldShowLanguageMenu = actorState.getLanguageState().allowSelectLanguage();
    LogUtils.v(TAG, "Language menu added: " + shouldShowLanguageMenu);
    if (!shouldShowLanguageMenu) {
      return;
    }
    // Add the latest set of languages in the sub menu.
    ListSubMenu subMenu =
        menu.addSubMenu(
            /* groupId= */ 0,
            /* itemId= */ R.id.language_menu,
            ORDER_LANGUAGES,
            service.getString(R.string.spoken_language));
    if (!LanguageMenuProcessor.prepareLanguageSubMenu(service, pipeline, actorState, subMenu)) {
      menu.removeItem(R.id.language_menu);
    }
  }

  private void addDimOrBrightenScreen(ContextMenu menu) {
    menu.removeItem(R.id.disable_dimming);
    menu.removeItem(R.id.enable_dimming);

    if (!showMenuItem(
        R.string.pref_show_context_menu_dim_or_brighten_setting_key,
        R.bool.pref_show_context_menu_dim_or_brighten_default)) {
      return;
    }

    // Add Dim Screen items depending on API level and current device lock state.
    boolean shouldShowDimOrBrightenScreen = DimScreenActor.isSupported(service);
    LogUtils.d(TAG, "Dim or Brighten screen functionality added: " + shouldShowDimOrBrightenScreen);
    if (!shouldShowDimOrBrightenScreen) {
      return;
    }
    if (actorState.getDimScreen().isDimmingEnabled()) {
      menu.add(
          /* groupId= */ 0,
          R.id.disable_dimming,
          ORDER_SHOW_HIDE_SCREEN,
          R.string.shortcut_disable_dimming);
    } else {
      menu.add(
          /* groupId= */ 0,
          R.id.enable_dimming,
          ORDER_SHOW_HIDE_SCREEN,
          R.string.shortcut_enable_dimming);
    }
  }

  // Adds menu item of window action to menu
  private void addWindowActionMenu(ContextMenu menu) {
    menu.removeItem(R.id.window_menu);

    if (!showMenuItem(
        R.string.pref_show_context_menu_system_action_setting_key,
        R.bool.pref_show_context_menu_system_action_default)) {
      return;
    }

    if (!FeatureSupport.supportSystemActions()
        || ScreenMonitor.isDeviceLocked(service)
        || !SettingsUtils.allowLinksOutOfSettings(service)) {
      return;
    }
    ListSubMenu subMenu =
        menu.addSubMenu(
            /* groupId= */ 0,
            /* itemId= */ R.id.window_menu,
            ORDER_SYSTEM_ACTIONS,
            /* title= */ service.getString(R.string.system_actions));
    if (!WindowNavigationMenuProcessor.prepareWindowSubMenu(service, subMenu, pipeline)) {
      menu.removeItem(R.id.window_menu);
    }
  }

  private static void setMenuItemShowsDialog(ContextMenu menu, int itemId, boolean showsDialog) {
    @Nullable ContextMenuItem item = menu.findItemInMenuOrSubmenus(itemId);
    if (item != null) {
      item.setShowsAlertDialog(showsDialog);
    }
  }

  private static void setMenuItemDeferredType(
      ContextMenu menu, int itemId, DeferredType deferAction) {
    @Nullable ContextMenuItem item = menu.findItemInMenuOrSubmenus(itemId);
    if (item != null) {
      item.setDeferredType(deferAction);
    }
  }

  private static void setSkipRefocusAndWindowAnnounce(ContextMenu menu, int itemId, boolean skip) {
    @Nullable ContextMenuItem item = menu.findItemInMenuOrSubmenus(itemId);
    if (item != null) {
      item.setSkipRefocusEvents(skip);
      item.setSkipWindowEvents(skip);
    }
  }
}

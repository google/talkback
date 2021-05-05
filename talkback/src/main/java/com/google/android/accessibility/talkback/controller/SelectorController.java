/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.talkback.controller;

import static com.google.android.accessibility.talkback.Feedback.AdjustValue.Action.DECREASE_VALUE;
import static com.google.android.accessibility.talkback.Feedback.AdjustValue.Action.INCREASE_VALUE;
import static com.google.android.accessibility.talkback.Feedback.AdjustVolume.Action.DECREASE_VOLUME;
import static com.google.android.accessibility.talkback.Feedback.AdjustVolume.Action.INCREASE_VOLUME;
import static com.google.android.accessibility.talkback.Feedback.AdjustVolume.StreamType.STREAM_TYPE_ACCESSIBILITY;
import static com.google.android.accessibility.talkback.Feedback.DimScreen.Action.BRIGHTEN;
import static com.google.android.accessibility.talkback.Feedback.DimScreen.Action.DIM;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.NEXT_PAGE;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.PREVIOUS_PAGE;
import static com.google.android.accessibility.talkback.Feedback.Language.Action.NEXT_LANGUAGE;
import static com.google.android.accessibility.talkback.Feedback.Language.Action.PREVIOUS_LANGUAGE;
import static com.google.android.accessibility.talkback.actor.TalkBackUIActor.Type.SELECTOR_ITEM_ACTION_OVERLAY;
import static com.google.android.accessibility.talkback.actor.TalkBackUIActor.Type.SELECTOR_MENU_ITEM_OVERLAY_MULTI_FINGER;
import static com.google.android.accessibility.talkback.actor.TalkBackUIActor.Type.SELECTOR_MENU_ITEM_OVERLAY_SINGLE_FINGER;
import static com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor.NUMBER_PICKER_FILTER_FOR_ADJUST;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_TOUCH;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.SpeechRate.Action;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping;
import com.google.android.accessibility.talkback.preference.TalkBackVerbosityPreferencesActivity;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.feedback.AbstractAccessibilityHintsManager;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Class to handle changes to selector and calls from {@link GestureController}. */
public class SelectorController {

  /** The type of granularity. */
  @IntDef({GRANULARITY_FOR_ALL_NODE, GRANULARITY_FOR_NATIVE_NODE, GRANULARITY_FOR_WEB_NODE})
  public @interface GranularityType {}

  private static final int GRANULARITY_FOR_ALL_NODE = 0;
  private static final int GRANULARITY_FOR_NATIVE_NODE = 1;
  private static final int GRANULARITY_FOR_WEB_NODE = 2;
  private static final int NO_SETTING_INSERTED = -1;

  /** Selector settings. */
  public enum Setting {
    SPEECH_RATE(
        R.string.pref_selector_speech_rate_key,
        R.string.selector_speech_rate,
        R.bool.pref_selector_speech_rate_default),
    LANGUAGE(
        R.string.pref_selector_language_key,
        R.string.selector_language,
        R.bool.pref_selector_language_default),
    VERBOSITY(
        R.string.pref_selector_verbosity_key,
        R.string.selector_verbosity,
        R.bool.pref_selector_verbosity_default),
    PUNCTUATION(
        R.string.pref_selector_punctuation_key,
        R.string.selector_punctuation,
        R.bool.pref_selector_punctuation_default),
    GRANULARITY(
        R.string.pref_selector_granularity_key,
        R.string.selector_granularity,
        R.bool.pref_selector_granularity_default),
    HIDE_SCREEN(
        R.string.pref_selector_hide_screen_key,
        R.string.selector_hide_screen,
        R.bool.pref_selector_hide_screen_default),
    AUDIO_FOCUS(
        R.string.pref_selector_audio_focus_key,
        R.string.selector_audio_focus,
        R.bool.pref_selector_audio_focus_default),
    SCROLLING_SEQUENTIAL(
        R.string.pref_selector_scroll_seq_key,
        R.string.selector_scroll_seq,
        R.bool.pref_selector_scroll_seq_default),
    CHANGE_ACCESSIBILITY_VOLUME(
        R.string.pref_selector_change_a11y_volume_key,
        R.string.selector_a11y_volume_change,
        R.bool.pref_selector_a11y_volume_default),
    GRANULARITY_HEADINGS(
        R.string.pref_selector_granularity_headings_key,
        R.string.selector_granularity_headings,
        R.bool.pref_selector_granularity_headings_default),
    GRANULARITY_WORDS(
        R.string.pref_selector_granularity_words_key,
        R.string.selector_granularity_words,
        R.bool.pref_selector_granularity_words_default),
    GRANULARITY_PARAGRAPHS(
        R.string.pref_selector_granularity_paragraphs_key,
        R.string.selector_granularity_paragraphs,
        R.bool.pref_selector_granularity_paragraphs_default),
    GRANULARITY_CHARACTERS(
        R.string.pref_selector_granularity_characters_key,
        R.string.selector_granularity_characters,
        R.bool.pref_selector_granularity_characters_default),
    GRANULARITY_LINES(
        R.string.pref_selector_granularity_lines_key,
        R.string.selector_granularity_lines,
        R.bool.pref_selector_granularity_lines_default),
    GRANULARITY_LINKS(
        R.string.pref_selector_granularity_links_key,
        R.string.selector_granularity_links,
        R.bool.pref_selector_granularity_links_default),
    GRANULARITY_CONTROLS(
        R.string.pref_selector_granularity_controls_key,
        R.string.selector_granularity_controls,
        R.bool.pref_selector_granularity_controls_default),
    GRANULARITY_LANDMARKS(
        R.string.pref_selector_granularity_landmarks_key,
        R.string.selector_granularity_landmarks,
        R.bool.pref_selector_granularity_landmarks_default),
    GRANULARITY_DEFAULT(
        R.string.pref_selector_granularity_key,
        R.string.granularity_default,
        R.bool.pref_show_navigation_menu_granularity_default),
    ADJUSTABLE_WIDGET(
        R.string.pref_selector_special_widget_key,
        R.string.selector_special_widget,
        R.bool.pref_selector_special_widget_default);

    /** The preference key of the filter in the selector settings page. */
    final int prefKeyResId;
    /**
     * When the user select the setting, the value will be saved in the current setting preference.
     */
    final int prefValueResId;
    /** The setting is on or off in default. */
    final int defaultValueResId;

    /** Constructor of a new Setting with the specific preference key and value. */
    Setting(int prefKeyResId, int prefValueResId, int defaultValueResId) {
      this.prefKeyResId = prefKeyResId;
      this.prefValueResId = prefValueResId;
      this.defaultValueResId = defaultValueResId;
    }

    /** Returns a Setting associated with the given preference value. */
    public static @Nullable Setting getSettingFromPrefValue(Context context, String prefValue) {
      for (Setting setting : values()) {
        if (TextUtils.equals(context.getString(setting.prefValueResId), prefValue)) {
          return setting;
        }
      }
      return null;
    }
  }

  /** Granularities are provided by the selector. */
  public enum Granularity {
    HEADINGS(Setting.GRANULARITY_HEADINGS, CursorGranularity.HEADING, GRANULARITY_FOR_NATIVE_NODE),
    WORDS(Setting.GRANULARITY_WORDS, CursorGranularity.WORD, GRANULARITY_FOR_ALL_NODE),
    PARAGRAPHS(
        Setting.GRANULARITY_PARAGRAPHS, CursorGranularity.PARAGRAPH, GRANULARITY_FOR_ALL_NODE),
    CHARACTERS(
        Setting.GRANULARITY_CHARACTERS, CursorGranularity.CHARACTER, GRANULARITY_FOR_ALL_NODE),
    LINES(Setting.GRANULARITY_LINES, CursorGranularity.LINE, GRANULARITY_FOR_ALL_NODE),
    LINKS(Setting.GRANULARITY_LINKS, CursorGranularity.LINK, GRANULARITY_FOR_NATIVE_NODE),
    CONTROLS(Setting.GRANULARITY_CONTROLS, CursorGranularity.CONTROL, GRANULARITY_FOR_NATIVE_NODE),
    DEFAULT(Setting.GRANULARITY_DEFAULT, CursorGranularity.DEFAULT, GRANULARITY_FOR_ALL_NODE),

    // For WebView.
    WEB_LANDMARKS(
        Setting.GRANULARITY_LANDMARKS, CursorGranularity.WEB_LANDMARK, GRANULARITY_FOR_WEB_NODE),
    WEB_HEADINGS(
        Setting.GRANULARITY_HEADINGS, CursorGranularity.WEB_HEADING, GRANULARITY_FOR_WEB_NODE),
    WEB_LINKS(Setting.GRANULARITY_LINKS, CursorGranularity.WEB_LINK, GRANULARITY_FOR_WEB_NODE),
    WEB_CONTROLS(
        Setting.GRANULARITY_CONTROLS, CursorGranularity.WEB_CONTROL, GRANULARITY_FOR_WEB_NODE);

    final Setting setting;
    final CursorGranularity cursorGranularity;
    @GranularityType final int granularityType;

    Granularity(
        Setting setting,
        CursorGranularity cursorGranularity,
        @GranularityType int granularityType) {
      this.setting = setting;
      this.cursorGranularity = cursorGranularity;
      this.granularityType = granularityType;
    }

    /**
     * Returns a list of Granularity associated with the given Setting.
     *
     * <p>Related Granularity can have more than one because different Granularity can connect to
     * the same Setting. For example, the Setting of Granularity.HEADINGS and
     * Granularity.WEB_HEADINGS are the same (Setting.GRANULARITY_HEADINGS).
     */
    public static List<Granularity> getFromSetting(Setting setting) {
      List<Granularity> granularities = new ArrayList<>();
      for (Granularity granularity : values()) {
        if (granularity.setting.prefKeyResId == setting.prefKeyResId) {
          granularities.add(granularity);
        }
      }
      return granularities;
    }

    /** Returns a Granularity, which is the supported granularity of the focused node. */
    public static @Nullable Granularity getSupportedGranularity(
        ActorState actorState, Setting setting) {
      List<Granularity> granularities = Granularity.getFromSetting(setting);
      for (Granularity granularity : granularities) {
        if (actorState
            .getDirectionNavigation()
            .supportedGranularity(granularity.cursorGranularity, EVENT_ID_UNTRACKED)) {
          return granularity;
        }
      }
      return null;
    }

    /** Returns a Setting, which is for setting the given CursorGranularity. */
    public static @Nullable Setting getSettingFromCursorGranularity(
        CursorGranularity cursorGranularity) {
      for (Granularity granularity : values()) {
        if (granularity.cursorGranularity == cursorGranularity) {
          return granularity.setting;
        }
      }
      return null;
    }
  }

  private final Context context;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private Pipeline.FeedbackReturner pipeline;
  private ActorState actorState;
  private final TalkBackAnalytics analytics;
  private final SharedPreferences prefs;
  private final GestureShortcutMapping gestureMapping;
  private final AbstractAccessibilityHintsManager hintsManager;
  /**
   * Keeps gestures to announce the usage hint for how to select setting (change quick menu item).
   */
  private List<String> selectSettingGestures;
  /**
   * Keeps gestures to announce the usage hint for how to adjust selected setting (change quick menu
   * item action).
   */
  private List<String> adjustSelectedSettingGestures;
  // Flattening granularities into the quick menu for all devices from TalkBack 9.1.
  // This index of GRANULARITY_XXX is used in granularity voice command array.
  public static final ImmutableList<Setting> SELECTOR_SETTINGS =
      ImmutableList.of(
          Setting.GRANULARITY_CHARACTERS,
          Setting.GRANULARITY_WORDS,
          Setting.GRANULARITY_LINES,
          Setting.GRANULARITY_PARAGRAPHS,
          Setting.GRANULARITY_HEADINGS,
          Setting.GRANULARITY_CONTROLS,
          Setting.GRANULARITY_LINKS,
          Setting.GRANULARITY_LANDMARKS,
          Setting.GRANULARITY_DEFAULT,
          // TODO Supports special content.
          Setting.SPEECH_RATE,
          Setting.VERBOSITY,
          Setting.PUNCTUATION,
          Setting.LANGUAGE,
          // TODO Supports sound feedback and vibration feedback.
          Setting.HIDE_SCREEN,
          Setting.AUDIO_FOCUS,
          Setting.SCROLLING_SEQUENTIAL,
          Setting.CHANGE_ACCESSIBILITY_VOLUME,
          Setting.ADJUSTABLE_WIDGET);

  private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
      (prefs, key) -> {
        // Clears selector gestures but doesn't update them now because GestureShortcutMapping
        // hasn't finished reloading yet.
        selectSettingGestures = null;
        adjustSelectedSettingGestures = null;
      };

  /**
   * Denote the widget in focus is either NumberPicker or SeekBar so that the reading menu can
   * handle the special action for it.
   */
  private boolean isFocusedNodeAdjustable;
  /**
   * When the focused node is adjustable, this index is used as the pseudo index of adjust-value
   * setting. It's also used to record the current setting before a focused editable set to
   * selection mode.
   */
  private int adjustableIndex = NO_SETTING_INSERTED;
  /** When the focused node is adjustable, this index is used as the index of current setting */
  private int currentIndex;

  public SelectorController(
      @NonNull Context context,
      @NonNull AccessibilityFocusMonitor accessibilityFocusMonitor,
      @NonNull TalkBackAnalytics analytics,
      @NonNull GestureShortcutMapping gestureMapping,
      AbstractAccessibilityHintsManager hintsManager) {

    this.context = context;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.analytics = analytics;
    this.gestureMapping = gestureMapping;
    this.hintsManager = hintsManager;

    prefs = SharedPreferencesUtils.getSharedPreferences(this.context);
    prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  /** Gets the default Setting. */
  private static Setting getDefaultSetting() {
    return SELECTOR_SETTINGS.get(0);
  }

  /** Updates the current setting in preference. */
  private static void updateSettingPref(Context context, Setting newSetting) {
    SharedPreferencesUtils.getSharedPreferences(context)
        .edit()
        .putString(
            context.getString(R.string.pref_current_selector_setting_key),
            context.getString(newSetting.prefValueResId))
        .apply();
  }

  /**
   * Sets current setting preference according to the given CursorGranularity.
   *
   * <p>The granularity, which is selected from the talkback context menu, should sync to the
   * selector. It's happened only if the selected setting is a granularity.
   */
  public static void updateSettingPrefForGranularity(
      Context context, CursorGranularity cursorGranularity) {
    Setting setting = Granularity.getSettingFromCursorGranularity(cursorGranularity);
    if (setting != null) {
      switch (setting) {
        case GRANULARITY_HEADINGS:
        case GRANULARITY_WORDS:
        case GRANULARITY_PARAGRAPHS:
        case GRANULARITY_CHARACTERS:
        case GRANULARITY_LINES:
        case GRANULARITY_LINKS:
        case GRANULARITY_CONTROLS:
        case GRANULARITY_LANDMARKS:
        case GRANULARITY_DEFAULT:
          updateSettingPref(context, setting);
          return;
        default:
      }
    }
  }

  /** Sets the current Setting and announces the hint if {@code announce} is true. */
  private void setCurrentSetting(
      EventId eventId, Setting newSetting, boolean announce, boolean showOverlay) {
    updateSettingPref(context, newSetting);
    String actionDescription = null;
    String hint = null;
    switch (newSetting) {
      case SPEECH_RATE:
        actionDescription = context.getString(R.string.title_pref_selector_speech_rate);
        hint = getAdjustSelectedSettingGestures();
        break;
      case LANGUAGE:
        actionDescription = context.getString(R.string.spoken_language);
        hint = getAdjustSelectedSettingGestures();
        break;
      case VERBOSITY:
        actionDescription = context.getString(R.string.title_pref_selector_verbosity);
        hint = getAdjustSelectedSettingGestures();
        break;
      case PUNCTUATION:
        actionDescription = context.getString(R.string.title_pref_selector_punctuation);
        hint = getAdjustSelectedSettingGestures();
        break;
      case HIDE_SCREEN:
        actionDescription = context.getString(R.string.shortcut_enable_dimming);
        hint = getAdjustSelectedSettingGestures();
        break;
      case AUDIO_FOCUS:
        actionDescription = context.getString(R.string.title_pref_selector_audio_focus);
        hint = getAdjustSelectedSettingGestures();
        break;
      case SCROLLING_SEQUENTIAL:
        actionDescription = context.getString(R.string.title_pref_support_scroll_seq);
        hint = getAdjustSelectedSettingGestures();
        break;
      case CHANGE_ACCESSIBILITY_VOLUME:
        actionDescription = context.getString(R.string.title_pref_a11y_volume);
        hint = getAdjustSelectedSettingGestures();
        break;
      case GRANULARITY_HEADINGS:
      case GRANULARITY_WORDS:
      case GRANULARITY_PARAGRAPHS:
      case GRANULARITY_CHARACTERS:
      case GRANULARITY_LINES:
      case GRANULARITY_LINKS:
      case GRANULARITY_CONTROLS:
      case GRANULARITY_LANDMARKS:
      case GRANULARITY_DEFAULT:
        {
          @Nullable
          Granularity granularity = Granularity.getSupportedGranularity(actorState, newSetting);
          if (granularity != null) {
            // Changes the current granularity.
            pipeline.returnFeedback(eventId, Feedback.granularity(granularity.cursorGranularity));
            actionDescription = context.getString(granularity.cursorGranularity.resourceId);
            hint = getAdjustSelectedGranularityGestures(granularity);
          }
        }
        break;
      case ADJUSTABLE_WIDGET:
        @Nullable AccessibilityNodeInfoCompat node = null;
        try {
          node = accessibilityFocusMonitor.getSupportedAdjustableNode();
          if (node != null) {
            if (Role.getRole(node) == Role.ROLE_SEEK_CONTROL) {
              actionDescription =
                  context.getString(R.string.title_pref_selector_adjustable_widget_slider);
            } else {
              actionDescription =
                  context.getString(R.string.title_pref_selector_adjustable_widget_number_picker);
            }
          }
        } finally {
          AccessibilityNodeInfoUtils.recycleNodes(node);
        }
        hint = getAdjustSelectedSettingGestures();
        break;
      default:
    }

    if (hint != null) {
      analytics.onSelectorEvent();
    }
    if (TextUtils.isEmpty(actionDescription)) {
      return;
    }
    if (announce) {
      announceSetting(eventId, actionDescription, hint);
    }
    if (showOverlay) {
      showQuickMenuOverlay(eventId, actionDescription);
    }
  }

  /** Gets the current setting from the preference. */
  private Setting getCurrentSetting() {
    @Nullable
    Setting currentSetting =
        Setting.getSettingFromPrefValue(
            context,
            prefs.getString(
                context.getString(R.string.pref_current_selector_setting_key),
                context.getString(R.string.pref_selector_setting_default)));
    return currentSetting == null ? getDefaultSetting() : currentSetting;
  }

  /**
   * When the selection mode of edit text is changed, the default setting would change to
   * GRANULARITY_CHARACTERS. Users still can change setting by themselves. If users did not change
   * the setting during the selection mode life time, system will change the setting back to its
   * original value when the selection mode stops.
   */
  public void editTextSelected(boolean isSelected) {
    if (isSelected) {
      List<Setting> settings = getFilteredSettings();
      adjustableIndex = settings.indexOf(getCurrentSetting());
      setCurrentSetting(EVENT_ID_UNTRACKED, Setting.GRANULARITY_CHARACTERS, false, false);
      return;
    }

    // adjustableIndex is used for restoring the current setting when the focus is moved to a
    // node which is not adjustable, so the index can't be cleared if the focus is on an adjustable
    // node.
    if (adjustableIndex == NO_SETTING_INSERTED || isFocusedNodeAdjustable) {
      return;
    }

    // Removes adjust-slider item and restores the current setting.
    List<Setting> settings = getFilteredSettings();
    if (adjustableIndex < settings.size()) {
      setCurrentSetting(EVENT_ID_UNTRACKED, settings.get(adjustableIndex), false, false);
    }
    adjustableIndex = NO_SETTING_INSERTED;
  }

  /**
   * Each time the Accessibility focus changed, we need to consider to adjust the reading menu
   * setting. The argument [nodeInfo] is wrapped into AccessibilityNodeInfoCompat node which would
   * be recycled in this method.
   */
  public void newItemFocused(AccessibilityNodeInfo nodeInfo) {
    isFocusedNodeAdjustable = false;
    if (nodeInfo != null) {
      // This is for checking the focused is adjustable or not.
      // It could be the case when the TYPE_VIEW_ACCESSIBILITY_FOCUSED event comes with focus
      // dropped. In this condition, the AccessibilityEvent#getSource will return null.
      AccessibilityNodeInfoCompat wrapNode = null;
      AccessibilityNodeInfoCompat adjustableNode = null;
      try {
        wrapNode = AccessibilityNodeInfoCompat.wrap(nodeInfo);
        adjustableNode =
            AccessibilityNodeInfoUtils.getMatchingAncestor(
                wrapNode, NUMBER_PICKER_FILTER_FOR_ADJUST);
        isFocusedNodeAdjustable =
            (Role.getRole(nodeInfo) == Role.ROLE_SEEK_CONTROL) || (adjustableNode != null);
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(adjustableNode, wrapNode);
      }
    }

    if (isFocusedNodeAdjustable) {
      List<Setting> settings = getFilteredSettings();
      adjustableIndex = Math.max(0, settings.indexOf(getCurrentSetting()));
      currentIndex = adjustableIndex;
    }
  }

  /** Selects the previous or next setting. For selecting setting via gesture. */
  public void selectPreviousOrNextSetting(EventId eventId, boolean isNext) {
    List<Setting> settings = getFilteredSettings();

    // When losing the Accessibility focus, the filtered setting items change, and the original
    // record of adjustable info is invalid.
    if (isFocusedNodeAdjustable
        && !accessibilityFocusMonitor.hasAccessibilityFocus(/* useInputFocusIfEmpty= */ false)) {
      isFocusedNodeAdjustable = false;
    }
    if (isFocusedNodeAdjustable) {
      settings.add(adjustableIndex, Setting.ADJUSTABLE_WIDGET);
    } else {
      // In case the adjustableIndex is used to keep the setting index for edit text selection, it
      // has to be reset when user changes the setting.
      adjustableIndex = NO_SETTING_INSERTED;
    }

    int settingsSize = settings.size();
    if (settingsSize == 0) {
      return;
    }

    // Get the index of the selected setting.
    int index;
    if (isFocusedNodeAdjustable) {
      index = currentIndex;
    } else {
      index = settings.indexOf(getCurrentSetting());
    }

    // Change the selected setting.
    // If the current settings is not valid, the index (-1) will fall-back to 0 or settingsSize - 1
    // depending on isNext
    if (isNext) {
      index++;
      if (index >= settingsSize || index < 0) {
        // User has reached end of list so wrap to start of list.
        index = 0;
      }
    } else { // Choose previous setting.
      index--;
      if (index >= settingsSize || index < 0) {
        // User has reached start of list so wrap to end of list.
        index = settingsSize - 1;
      }
    }

    currentIndex = index;
    if (settings.get(index) == Setting.ADJUSTABLE_WIDGET) {
      @Nullable AccessibilityNodeInfoCompat node = null;
      String actionDescription = null;
      String hint;
      try {
        node = accessibilityFocusMonitor.getSupportedAdjustableNode();
        if (node != null) {
          if (Role.getRole(node) == Role.ROLE_SEEK_CONTROL) {
            actionDescription =
                context.getString(R.string.title_pref_selector_adjustable_widget_slider);
          } else {
            actionDescription =
                context.getString(R.string.title_pref_selector_adjustable_widget_number_picker);
          }
        }
      } finally {
        AccessibilityNodeInfoUtils.recycleNodes(node);
      }

      hint = getAdjustSelectedSettingGestures();

      if (hint != null) {
        analytics.onSelectorEvent();
      }
      if (!TextUtils.isEmpty(actionDescription)) {
        announceSetting(eventId, actionDescription, hint);
        showQuickMenuOverlay(eventId, actionDescription);
      }
    } else {
      setCurrentSetting(eventId, settings.get(index), true, /* showOverlay= */ true);
    }
  }

  /** Selects setting and announces selected setting. For selecting setting via voice-shortcut. */
  public boolean selectSetting(@Nullable Setting setting) {
    return selectSetting(setting, /* announce= */ true, /* showOverlay= */ true);
  }

  /** Selects setting and announces selected setting. For selecting setting via voice-shortcut. */
  public boolean selectSetting(@Nullable Setting setting, boolean showOverlay) {
    return selectSetting(setting, /* announce= */ true, showOverlay);
  }

  /** Selects setting and announces selected setting. For selecting setting via voice-shortcut. */
  private boolean selectSetting(@Nullable Setting setting, boolean announce, boolean showOverlay) {
    if (setting == null || !allowedSetting(setting)) {
      return false;
    }

    String currentSettingKey = context.getString(R.string.pref_current_selector_setting_key);
    String currentSetting = prefs.getString(currentSettingKey, null);
    String settingValue = context.getString(setting.prefValueResId);
    // Sets selected setting if setting is changed.
    if (!settingValue.equals(currentSetting)) {
      prefs.edit().putString(currentSettingKey, settingValue).apply();
    }

    setCurrentSetting(EVENT_ID_UNTRACKED, setting, announce, showOverlay);
    return true;
  }

  /** Returns whether the setting is in selector preferences. */
  public boolean validSetting(Setting setting) {
    return SELECTOR_SETTINGS.contains(setting);
  }

  /** Returns whether the setting is turned on in selector preferences. */
  private boolean allowedSetting(Setting setting) {
    List<Setting> allSettings = getFilteredSettings();
    return allSettings.contains(setting);
  }

  /**
   * Filter settings based on device. Filter out the settings turned off by users in selector
   * preferences.
   */
  private List<Setting> getFilteredSettings() {
    ArrayList<Setting> filteredSettings = new ArrayList<>();

    String preferenceKey;

    for (Setting setting : SELECTOR_SETTINGS) {
      preferenceKey = context.getString(setting.prefKeyResId);

      // Check if the SwitchPreference is on for this setting.
      if (prefs.getBoolean(
          preferenceKey, context.getResources().getBoolean(setting.defaultValueResId))) {
        switch (setting) {
          case LANGUAGE:
            {
              if (actorState.getLanguageState().allowSelectLanguage()) {
                filteredSettings.add(setting);
              }
              break;
            }
          case AUDIO_FOCUS:
            {
              // Add audio focus setting if not ARC, as for ARC this preference is not supported.
              if (!FeatureSupport.isArc()) {
                filteredSettings.add(setting);
              }
              break;
            }
          case SCROLLING_SEQUENTIAL:
            filteredSettings.add(setting);
            break;
          case CHANGE_ACCESSIBILITY_VOLUME:
            filteredSettings.add(setting);
            break;
          case GRANULARITY_HEADINGS:
          case GRANULARITY_WORDS:
          case GRANULARITY_PARAGRAPHS:
          case GRANULARITY_CHARACTERS:
          case GRANULARITY_LINES:
          case GRANULARITY_LINKS:
          case GRANULARITY_CONTROLS:
          case GRANULARITY_LANDMARKS:
          case GRANULARITY_DEFAULT:
            {
              @Nullable
              Granularity granularity = Granularity.getSupportedGranularity(actorState, setting);
              if (granularity != null) {
                filteredSettings.add(setting);
              }
              break;
            }
          default:
            filteredSettings.add(setting);
        }
      }
    }
    return filteredSettings;
  }

  /** Announces the quick menu item or action, and the usage hint. */
  private void announceSetting(
      EventId eventId, @Nullable String announcement, @Nullable String hint) {
    if (!TextUtils.isEmpty(announcement)) {
      if (!TextUtils.isEmpty(hint)) {
        // To ensure the hint will follow the main text, we use SpeechController
        // #setCompletedAction(UtteranceCompleteRunnable) to implement the usage hint. It means the
        // hint should be added before the main text, then the SpeechController will defer it until
        // finishing the main text.
        // TODO: Handles the hint through the pipeline.
        hintsManager.postHintForSelector(hint);
      }
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(
              announcement,
              SpeakOptions.create()
                  .setQueueMode(SpeechController.QUEUE_MODE_INTERRUPT)
                  .setFlags(
                      FeedbackItem.FLAG_NO_HISTORY
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_PHONE_CALL_ACTIVE
                          | FeedbackItem.FLAG_SKIP_DUPLICATE)));
    }
  }

  /**
   * Returns the usage hint when adjusting the selected setting, like "three-finger swipe left or
   * three-finger swipe right to select a different setting."
   */
  private String getSelectSettingGestures() {
    if (selectSettingGestures == null) {
      selectSettingGestures =
          gestureMapping.getGestureTextsFromActionKeys(
              context.getString(R.string.shortcut_value_select_previous_setting),
              context.getString(R.string.shortcut_value_select_next_setting));
    }
    CharSequence gestureNames;
    switch (selectSettingGestures.size()) {
      case 0:
        // There is no gesture to select setting.
        return context.getString(
            R.string.no_adjust_setting_gesture,
            Ascii.toLowerCase(context.getString(R.string.shortcut_select_next_setting)));
      case 1:
        gestureNames = Ascii.toLowerCase(selectSettingGestures.get(0));
        return context.getString(R.string.select_setting_hint, gestureNames);
      default:
        gestureNames =
            context.getString(
                R.string.gesture_1_or_2,
                Ascii.toLowerCase(selectSettingGestures.get(0)),
                Ascii.toLowerCase(selectSettingGestures.get(1)));
        return context.getString(R.string.select_setting_hint, gestureNames);
    }
  }

  /**
   * Returns the usage hint when selecting a setting, like "swipe up or swipe down to to adjust the
   * setting."
   */
  private String getAdjustSelectedSettingGestures() {
    if (adjustSelectedSettingGestures == null) {
      adjustSelectedSettingGestures =
          gestureMapping.getGestureTextsFromActionKeys(
              context.getString(R.string.shortcut_value_selected_setting_previous_action),
              context.getString(R.string.shortcut_value_selected_setting_next_action));
    }
    CharSequence gestureNames;
    switch (adjustSelectedSettingGestures.size()) {
      case 0:
        // There is no gesture to adjust setting.
        return context.getString(
            R.string.no_adjust_setting_gesture,
            Ascii.toLowerCase(context.getString(R.string.shortcut_selected_setting_next_action)));
      case 1:
        gestureNames = Ascii.toLowerCase(adjustSelectedSettingGestures.get(0));
        return context.getString(R.string.adjust_setting_hint, gestureNames);
      default:
        gestureNames =
            context.getString(
                R.string.gesture_1_or_2,
                Ascii.toLowerCase(adjustSelectedSettingGestures.get(0)),
                Ascii.toLowerCase(adjustSelectedSettingGestures.get(1)));
        return context.getString(R.string.adjust_setting_hint, gestureNames);
    }
  }

  /**
   * Returns the usage hint when selected setting is a granularity, like "swipe up or swipe down to
   * read by word."
   */
  private String getAdjustSelectedGranularityGestures(Granularity granularity) {
    if (adjustSelectedSettingGestures == null) {
      adjustSelectedSettingGestures =
          gestureMapping.getGestureTextsFromActionKeys(
              context.getString(R.string.shortcut_value_selected_setting_previous_action),
              context.getString(R.string.shortcut_value_selected_setting_next_action));
    }
    CharSequence gestureNames;
    String cursorGranularity =
        (granularity.setting == Setting.GRANULARITY_DEFAULT)
            ? context.getString(R.string.title_granularity_default)
            : context.getString(granularity.cursorGranularity.resourceId);
    switch (adjustSelectedSettingGestures.size()) {
      case 0:
        // There is no gesture to read by selected granularity.
        return context.getString(
            R.string.no_adjust_setting_gesture,
            Ascii.toLowerCase(context.getString(R.string.shortcut_selected_setting_next_action)));
      case 1:
        gestureNames = Ascii.toLowerCase(adjustSelectedSettingGestures.get(0));
        return context.getString(
            R.string.adjust_granularity_hint, gestureNames, Ascii.toLowerCase(cursorGranularity));
      default:
        gestureNames =
            context.getString(
                R.string.gesture_1_or_2,
                Ascii.toLowerCase(adjustSelectedSettingGestures.get(0)),
                Ascii.toLowerCase(adjustSelectedSettingGestures.get(1)));
        return context.getString(
            R.string.adjust_granularity_hint, gestureNames, Ascii.toLowerCase(cursorGranularity));
    }
  }

  /** Change the value of the selected setting or scroll forward/backard of the seeker. */
  public void adjustSelectedSetting(EventId eventId, boolean isNext) {
    Setting currentSetting;
    if (isFocusedNodeAdjustable
        && !accessibilityFocusMonitor.hasAccessibilityFocus(/* useInputFocusIfEmpty= */ false)) {
      isFocusedNodeAdjustable = false;
    }
    if (isFocusedNodeAdjustable) {
      List<Setting> settings = getFilteredSettings();
      settings.add(adjustableIndex, Setting.ADJUSTABLE_WIDGET);
      // Ensure the recorded currentIndex is still valid.
      if (currentIndex >= 0 && currentIndex < settings.size()) {
        currentSetting = settings.get(currentIndex);
      } else {
        currentSetting = getCurrentSetting();
      }
    } else {
      currentSetting = getCurrentSetting();
    }
    analytics.onSelectorActionEvent(currentSetting);

    switch (currentSetting) {
      case SPEECH_RATE:
        // TODO: Increase speech rate when swipe-up is assigned to the selected setting's
        // previous action, which will be the default assignment.
        changeSpeechRate(eventId, !isNext);
        return;
      case LANGUAGE:
        changeLanguage(eventId, isNext);
        return;
      case VERBOSITY:
        changeVerbosity(eventId, isNext);
        return;
      case PUNCTUATION:
        switchOnOrOffPunctuation(eventId);
        return;
      case HIDE_SCREEN:
        showOrHideScreen(eventId);
        return;
      case AUDIO_FOCUS:
        switchOnOrOffAudioDucking(eventId);
        return;
      case SCROLLING_SEQUENTIAL:
        scrollForwardBackward(eventId, isNext);
        return;
      case CHANGE_ACCESSIBILITY_VOLUME:
        changeAccessibilityVolume(eventId, isNext);
        return;
      case GRANULARITY:
        {
          // Granularity is phased out. Assigns to the default setting.
          updateSettingPref(context, getDefaultSetting());
          adjustSelectedSetting(eventId, isNext);
          return;
        }
      case GRANULARITY_HEADINGS:
      case GRANULARITY_WORDS:
      case GRANULARITY_PARAGRAPHS:
      case GRANULARITY_CHARACTERS:
      case GRANULARITY_LINES:
      case GRANULARITY_LINKS:
      case GRANULARITY_CONTROLS:
      case GRANULARITY_LANDMARKS:
      case GRANULARITY_DEFAULT:
        {
          List<Granularity> granularities = Granularity.getFromSetting(currentSetting);
          if (granularities.isEmpty()) {
            return;
          }

          @Nullable AccessibilityNodeInfoCompat node = null;
          try {
            node = accessibilityFocusMonitor.getAccessibilityFocus(false);
            boolean hasNavigableWebContent = WebInterfaceUtils.hasNavigableWebContent(node);
            for (Granularity granularity : granularities) {
              if ((granularity.granularityType == GRANULARITY_FOR_NATIVE_NODE
                      && hasNavigableWebContent)
                  || (granularity.granularityType == GRANULARITY_FOR_WEB_NODE
                      && !hasNavigableWebContent)) {
                continue;
              }
              moveAtGranularity(eventId, granularity, isNext);
              return;
            }
          } finally {
            AccessibilityNodeInfoUtils.recycleNodes(node);
          }
        }
        return;
      case ADJUSTABLE_WIDGET:
        handleAdjustable(eventId, isNext);
        return;
    }
  }

  private void handleAdjustable(EventId eventId, boolean isNext) {
    pipeline.returnFeedback(
        eventId, Feedback.adjustValue(isNext ? DECREASE_VALUE : INCREASE_VALUE));
  }

  /** Moves to the next or previous at specific granularity. */
  private void moveAtGranularity(EventId eventId, Granularity granularity, boolean isNext) {
    // Sets granularity and locks navigate within the focused node.
    pipeline.returnFeedback(eventId, Feedback.granularity(granularity.cursorGranularity));

    boolean result =
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(isNext ? SEARCH_FOCUS_FORWARD : SEARCH_FOCUS_BACKWARD)
                .setInputMode(INPUT_MODE_TOUCH)
                .setDefaultToInputFocus(true)
                .setScroll(true)
                .setWrap(true));
    if (!result) {
      pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
    }
  }

  public void changeSpeechRate(EventId eventId, boolean isIncrease) {
    boolean result =
        pipeline.returnFeedback(
            eventId, Feedback.speechRate(isIncrease ? Action.INCREASE_RATE : Action.DECREASE_RATE));
    String displayText;
    if (result) {
      displayText =
          context.getString(
              isIncrease
                  ? R.string.template_speech_rate_change_faster
                  : R.string.template_speech_rate_change_slower);
    } else {
      displayText =
          context.getString(
              isIncrease
                  ? R.string.template_speech_rate_change_fastest
                  : R.string.template_speech_rate_change_slowest);
    }
    announceSetting(eventId, displayText, getSelectSettingGestures());
    showQuickMenuActionOverlay(eventId, displayText);
  }

  /** Changes to the previous or next installed language. */
  private void changeLanguage(EventId eventId, boolean isNext) {
    pipeline.returnFeedback(eventId, Feedback.language(isNext ? NEXT_LANGUAGE : PREVIOUS_LANGUAGE));
    announceSetting(
        eventId,
        actorState.getLanguageState().getCurrentLanguageString(),
        getSelectSettingGestures());
    showQuickMenuActionOverlay(eventId, actorState.getLanguageState().getCurrentLanguageString());
  }

  /** Set the current verbosity preset to the previous or next verbosity preset. */
  private void changeVerbosity(EventId eventId, boolean isNext) {
    List<String> verbosities =
        Arrays.asList(context.getResources().getStringArray(R.array.pref_verbosity_preset_values));
    int verbositiesSize = verbosities.size();
    if (verbositiesSize == 0) {
      return;
    }

    // Get the index of the current verbosity preset.
    String verbosityPresetKey = context.getString(R.string.pref_verbosity_preset_key);
    String currentVerbosity =
        prefs.getString(
            verbosityPresetKey, context.getString(R.string.pref_verbosity_preset_value_default));
    int index = verbosities.indexOf(currentVerbosity);

    // Change the verbosity preset.
    if (isNext) {
      index++;
      if (index >= verbositiesSize || index < 0) {
        // User has reached end of list so wrap to start of list.
        index = 0;
      }
    } else { // Choose previous preset.
      index--;
      if (index >= verbositiesSize || index < 0) {
        // User has reached start of list so wrap to end of list.
        index = verbositiesSize - 1;
      }
    }

    String newVerbosity = verbosities.get(index);
    analytics.onManuallyChangeSetting(
        verbosityPresetKey, TalkBackAnalytics.TYPE_SELECTOR, /* isPending= */ true);
    prefs.edit().putString(verbosityPresetKey, newVerbosity).apply();
    // Announce new preset. If the TalkBackVerbosityPreferencesActivity fragment is visible,
    // the fragment's OnSharedPreferenceChangeListener.onSharedPreferenceChanged will also call this
    // method. SpeechController will then deduplicate the announcement event so only one is spoken.
    announceSetting(
        eventId,
        TalkBackVerbosityPreferencesActivity.getVerbosityChangeAnnouncement(newVerbosity, context),
        getSelectSettingGestures());
    showQuickMenuActionOverlay(
        eventId, TalkBackVerbosityPreferencesActivity.presetValueToName(newVerbosity, context));
  }

  private void switchOnOrOffPunctuation(EventId eventId) {
    Resources res = context.getResources();
    boolean punctuationOn =
        SharedPreferencesUtils.getBooleanPref(
            prefs, res, R.string.pref_punctuation_key, R.bool.pref_punctuation_default);
    analytics.onManuallyChangeSetting(
        res.getString(R.string.pref_use_audio_focus_key),
        TalkBackAnalytics.TYPE_SELECTOR, /* isPending */
        true);
    SharedPreferencesUtils.putBooleanPref(
        prefs, res, R.string.pref_punctuation_key, !punctuationOn);
    announceSetting(
        eventId,
        context.getString(
            R.string.punctuation_state,
            !punctuationOn
                ? context.getString(R.string.value_on)
                : context.getString(R.string.value_off)),
        getSelectSettingGestures());
    showQuickMenuActionOverlay(
        eventId, context.getString(!punctuationOn ? R.string.value_on : R.string.value_off));
  }

  /** Validate the verbosity index and set verbosity string if necessary */
  public void changeVerbosity(EventId eventId, int newIndex) {
    List<String> verbosities =
        Arrays.asList(context.getResources().getStringArray(R.array.pref_verbosity_preset_values));
    if (newIndex >= verbosities.size() || newIndex < 0) {
      return;
    }

    // Get the index of the current verbosity preset.
    String verbosityPresetKey = context.getString(R.string.pref_verbosity_preset_key);
    String currentVerbosity =
        prefs.getString(
            verbosityPresetKey, context.getString(R.string.pref_verbosity_preset_value_default));
    int currentIndex = verbosities.indexOf(currentVerbosity);
    String newVerbosity = verbosities.get(newIndex);
    if (currentIndex != newIndex) {
      prefs.edit().putString(verbosityPresetKey, newVerbosity).apply();
    }
    announceSetting(
        eventId,
        TalkBackVerbosityPreferencesActivity.getVerbosityChangeAnnouncement(newVerbosity, context),
        getSelectSettingGestures());
  }

  /** Shows (brightens) or hides (dims) the screen. */
  private void showOrHideScreen(EventId eventId) {
    if (actorState.getDimScreen().isDimmingEnabled()) {
      // Brightens the screen.
      pipeline.returnFeedback(eventId, Feedback.dimScreen(BRIGHTEN));
      announceSetting(
          eventId,
          context.getString(R.string.screen_brightness_restored),
          getSelectSettingGestures());
      showQuickMenuActionOverlay(eventId, context.getString(R.string.shortcut_disable_dimming));
    } else {
      // Dims the screen.
      pipeline.returnFeedback(eventId, Feedback.dimScreen(DIM));
      // TODO Speaks the usage hint when dim screen dialog is dismissed.
      showQuickMenuActionOverlay(eventId, context.getString(R.string.shortcut_enable_dimming));
    }
  }

  /** Switch on or off audio ducking. */
  private void switchOnOrOffAudioDucking(EventId eventId) {
    Resources res = context.getResources();
    boolean audioFocusOn =
        SharedPreferencesUtils.getBooleanPref(
            prefs, res, R.string.pref_use_audio_focus_key, R.bool.pref_use_audio_focus_default);
    analytics.onManuallyChangeSetting(
        res.getString(R.string.pref_use_audio_focus_key),
        TalkBackAnalytics.TYPE_SELECTOR, /* isPending */
        true);
    SharedPreferencesUtils.putBooleanPref(
        prefs, res, R.string.pref_use_audio_focus_key, !audioFocusOn);
    announceSetting(
        eventId,
        context.getString(
            R.string.audio_focus_state,
            !audioFocusOn
                ? context.getString(R.string.value_on)
                : context.getString(R.string.value_off)),
        getSelectSettingGestures());
    showQuickMenuActionOverlay(
        eventId, context.getString(!audioFocusOn ? R.string.value_on : R.string.value_off));
  }

  /** Perform scrolling to previous/next page. */
  private void scrollForwardBackward(EventId eventId, boolean isNext) {
    pipeline.returnFeedback(eventId, Feedback.focusDirection(isNext ? PREVIOUS_PAGE : NEXT_PAGE));
  }

  /** Perform Accessibility volume change. */
  private void changeAccessibilityVolume(EventId eventId, boolean isNext) {
    pipeline.returnFeedback(
        eventId,
        Feedback.adjustVolume(
            isNext ? DECREASE_VOLUME : INCREASE_VOLUME, STREAM_TYPE_ACCESSIBILITY));
    String displayText =
        context.getString(
            isNext
                ? R.string.template_volume_change_decrease
                : R.string.template_volume_change_increase);
    showQuickMenuActionOverlay(eventId, displayText);
  }

  /**
   * Resets the selector and removes non-useful preferences.
   *
   * <p>Resets the setting to granularity to ensure the behavior is the same as before.
   */
  public static void resetSelectorPreferences(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);

    // Remove legacy preferences
    String hasActivated = context.getString(R.string.pref_selector_first_time_activation_key);
    if (prefs.contains(hasActivated)) {
      prefs.edit().remove(hasActivated).apply();
      removeLegacyPreference(context);
    }
    String selectorActivationKey = context.getString(R.string.pref_selector_activation_key);
    if (prefs.contains(selectorActivationKey)) {
      prefs.edit().remove(selectorActivationKey).apply();
    }

    resetSelectedSetting(context);
    assignSelectorShortcuts(context);
    resetSelectorFilterValue(context);
  }

  /** Resets the selected setting to characters, then allows to read by character. */
  private static void resetSelectedSetting(Context context) {
    Setting defaultGranularitySetting = Setting.GRANULARITY_CHARACTERS;
    SharedPreferencesUtils.getSharedPreferences(context)
        .edit()
        .putString(
            context.getString(R.string.pref_current_selector_setting_key),
            context.getString(defaultGranularitySetting.prefValueResId))
        .putBoolean(context.getString(defaultGranularitySetting.prefKeyResId), true)
        .apply();
  }

  /** Assigns selector shortcuts by default. */
  private static void assignSelectorShortcuts(Context context) {
    String[] initialSelectorGestures =
        context.getResources().getStringArray(R.array.initial_selector_gestures);
    String[] selectorShortcutValues =
        context.getResources().getStringArray(R.array.selector_shortcut_values);

    if (initialSelectorGestures.length != selectorShortcutValues.length) {
      return;
    }

    Editor editor = SharedPreferencesUtils.getSharedPreferences(context).edit();
    for (int i = 0; i < initialSelectorGestures.length; i++) {
      // Assigns selector shortcuts to gestures.
      editor.putString(initialSelectorGestures[i], selectorShortcutValues[i]);
    }
    editor.apply();
  }

  private static void resetSelectorFilterValue(Context context) {
    Editor editor = SharedPreferencesUtils.getSharedPreferences(context).edit();
    for (Setting setting : Setting.values()) {
      editor.putBoolean(
          context.getString(setting.prefKeyResId),
          context.getResources().getBoolean(setting.defaultValueResId));
    }
    editor.apply();
  }

  /**
   * Removes selector/non-selector saved shortcuts.
   *
   * <p>From 9.0, the selector is always on and the backup preferences are unnecessary.
   *
   * <p>The method is for upgrading from a version earlier than 9.0.
   */
  private static void removeLegacyPreference(Context context) {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    String[] gestureShortcutKeys =
        context.getResources().getStringArray(R.array.pref_shortcut_keys);
    String selectorSavedKeySuffix = context.getString(R.string.pref_selector_saved_gesture_suffix);
    String notSelectorSavedKeySuffix =
        context.getString(R.string.pref_not_selector_saved_gesture_suffix);

    // Iterates through all the gestures and their shortcut assignments.
    for (String gestureShortcutKey : gestureShortcutKeys) {
      SharedPreferencesUtils.remove(
          prefs,
          gestureShortcutKey + selectorSavedKeySuffix,
          gestureShortcutKey + notSelectorSavedKeySuffix);
    }
  }

  private void showQuickMenuOverlay(EventId eventId, @Nullable CharSequence message) {
    String selectPreviousSetting =
        context.getString(R.string.shortcut_value_select_previous_setting);
    String selectNextSetting = context.getString(R.string.shortcut_value_select_next_setting);

    if (FeatureSupport.isMultiFingerGestureSupported()
        && selectPreviousSetting.equals(
            prefs.getString(
                context.getString(R.string.pref_shortcut_3finger_swipe_left_key),
                context.getString(R.string.pref_shortcut_3finger_swipe_left_default)))
        && selectNextSetting.equals(
            prefs.getString(
                context.getString(R.string.pref_shortcut_3finger_swipe_right_key),
                context.getString(R.string.pref_shortcut_3finger_swipe_right_default)))) {
      // Shows a selector overlay with 3-finger swipe left/right gesture icons.
      pipeline.returnFeedback(
          eventId,
          Feedback.showSelectorUI(
              SELECTOR_MENU_ITEM_OVERLAY_SINGLE_FINGER, message, /* showIcon= */ true));
    } else {
      boolean isSwipeUpDownAndDownUpForSelector =
          selectPreviousSetting.equals(
                  prefs.getString(
                      context.getString(R.string.pref_shortcut_up_and_down_key),
                      context.getString(R.string.pref_shortcut_up_and_down_default)))
              && selectNextSetting.equals(
                  prefs.getString(
                      context.getString(R.string.pref_shortcut_down_and_up_key),
                      context.getString(R.string.pref_shortcut_down_and_up_default)));
      // Shows a selector overlay with swipe up-down/down-up gesture icons if swiping
      // up-down/down-up gestures are for selecting selector items. Otherwise, shows a selector
      // overlay without gesture icons.
      pipeline.returnFeedback(
          eventId,
          Feedback.showSelectorUI(
              SELECTOR_MENU_ITEM_OVERLAY_MULTI_FINGER, message, isSwipeUpDownAndDownUpForSelector));
    }
  }

  private void showQuickMenuActionOverlay(EventId eventId, CharSequence message) {
    String selectedSettingPreviousAction =
        context.getString(R.string.shortcut_value_selected_setting_previous_action);
    String selectedSettingNextAction =
        context.getString(R.string.shortcut_value_selected_setting_next_action);
    boolean isSwipeUpDownForSelector =
        selectedSettingPreviousAction.equals(
                prefs.getString(
                    context.getString(R.string.pref_shortcut_up_key),
                    context.getString(R.string.pref_shortcut_up_default)))
            && selectedSettingNextAction.equals(
                prefs.getString(
                    context.getString(R.string.pref_shortcut_down_key),
                    context.getString(R.string.pref_shortcut_down_default)));
    // Shows a selector overlay with swipe up/down gesture icons if swiping up/down gestures are
    // for adjusting selected settings. Otherwise, shows a selector overlay without gesture icons.
    pipeline.returnFeedback(
        eventId,
        Feedback.showSelectorUI(SELECTOR_ITEM_ACTION_OVERLAY, message, isSwipeUpDownForSelector));
  }
}

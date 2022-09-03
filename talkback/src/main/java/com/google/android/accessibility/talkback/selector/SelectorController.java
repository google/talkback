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

package com.google.android.accessibility.talkback.selector;

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
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.ACTIONS;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.ADJUSTABLE_WIDGET;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.GRANULARITY_CHARACTERS;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.GRANULARITY_WINDOWS;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_TOUCH;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;

import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.Speech;
import com.google.android.accessibility.talkback.Feedback.SpeechRate.Action;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping;
import com.google.android.accessibility.talkback.preference.base.VerbosityPrefFragment;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.feedbackpolicy.AbstractAccessibilityHintsManager;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Class to handle changes to selector and calls from {@link GestureController}. */
public class SelectorController {

  /** The type of granularity. */
  @IntDef({GRANULARITY_FOR_ALL_NODE, GRANULARITY_FOR_NATIVE_NODE, GRANULARITY_FOR_WEB_NODE})
  public @interface GranularityType {}

  private static final int GRANULARITY_FOR_ALL_NODE = 0;
  private static final int GRANULARITY_FOR_NATIVE_NODE = 1;
  private static final int GRANULARITY_FOR_WEB_NODE = 2;
  private static final int NO_SETTING_INSERTED = -1;

  private static final Filter<AccessibilityNodeInfoCompat> FILTER_HAS_MENU_ACTION =
      new Filter.NodeCompat(
          (node) -> {
            for (AccessibilityActionCompat action : node.getActionList()) {
              if (shouldIncludeAction(action)) {
                return true;
              }
            }
            return false;
          });

  /** The current action id selected for the actions setting. */
  private int currentActionId = NO_SETTING_INSERTED;

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
    ACTIONS(
        R.string.pref_selector_actions_key,
        R.string.selector_actions,
        R.bool.pref_selector_actions_default),
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
    GRANULARITY_WINDOWS(
        R.string.pref_selector_granularity_windows_key,
        R.string.selector_granularity_windows,
        R.bool.pref_selector_granularity_windows_default),
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
    WINDOWS(GRANULARITY_WINDOWS, CursorGranularity.WINDOWS, GRANULARITY_FOR_ALL_NODE),
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
          Setting.ACTIONS,
          Setting.GRANULARITY_CHARACTERS,
          Setting.GRANULARITY_WORDS,
          Setting.GRANULARITY_LINES,
          Setting.GRANULARITY_PARAGRAPHS,
          Setting.GRANULARITY_HEADINGS,
          Setting.GRANULARITY_CONTROLS,
          Setting.GRANULARITY_LINKS,
          Setting.GRANULARITY_LANDMARKS,
          Setting.GRANULARITY_WINDOWS,
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

  /** Lists all {@link Setting} that should be hidden for users. */
  private static final ImmutableList<Setting> HIDDEN_SETTINGS = ImmutableList.of(Setting.ACTIONS);

  private static final ImmutableList<ContextualSetting> CONTEXTUAL_SETTINGS =
      ImmutableList.of(new AdjustableWidgetSetting());

  private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
      (prefs, key) -> {
        // Clears selector gestures but doesn't update them now because GestureShortcutMapping
        // hasn't finished reloading yet.
        selectSettingGestures = null;
        adjustSelectedSettingGestures = null;
      };

  private Setting settingToRestore;

  /**
   * Interface for contextual settings, the selector will automatically select the setting if {@link
   * #shouldActivateSetting(Context, AccessibilityNodeInfoCompat)} return true.
   */
  interface ContextualSetting {
    /** Returns the dependent item in {@link Setting}. */
    Setting getSetting();

    /** Returns whether the setting should be activated automatically. */
    boolean shouldActivateSetting(Context context, AccessibilityNodeInfoCompat node);
  }

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

  /** Gets the Setting for by granularity resources ID. */
  public static @Nullable Setting getSettingByGranularityId(@IdRes int granularity) {
    if (granularity == R.string.granularity_character) {
      return Setting.GRANULARITY_CHARACTERS;
    } else if (granularity == R.string.granularity_word) {
      return Setting.GRANULARITY_WORDS;
    } else if (granularity == R.string.granularity_line) {
      return Setting.GRANULARITY_LINES;
    } else if (granularity == R.string.granularity_paragraph) {
      return Setting.GRANULARITY_PARAGRAPHS;
    } else if (granularity == R.string.granularity_web_heading) {
      return Setting.GRANULARITY_HEADINGS;
    } else if (granularity == R.string.granularity_web_control) {
      return Setting.GRANULARITY_CONTROLS;
    } else if (granularity == R.string.granularity_web_landmark) {
      return Setting.GRANULARITY_LANDMARKS;
    } else if (granularity == R.string.granularity_window) {
      return Setting.GRANULARITY_WINDOWS;
    } else if (granularity == R.string.granularity_default) {
      return Setting.GRANULARITY_DEFAULT;
    }
    return null;
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
        case GRANULARITY_WINDOWS:
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
      case ACTIONS:
        actionDescription = context.getString(R.string.title_pref_selector_actions);
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
      case GRANULARITY_WINDOWS:
      case GRANULARITY_DEFAULT:
        {
          @Nullable Granularity granularity =
              Granularity.getSupportedGranularity(actorState, newSetting);
          if (granularity != null) {
            // Changes the current granularity.
            pipeline.returnFeedback(eventId, Feedback.granularity(granularity.cursorGranularity));
            actionDescription = context.getString(granularity.cursorGranularity.resourceId);
            hint = getAdjustSelectedGranularityGestures(granularity);
          }
        }
        break;
      case ADJUSTABLE_WIDGET:
        @Nullable AccessibilityNodeInfoCompat node =
            accessibilityFocusMonitor.getSupportedAdjustableNode();
        if (node != null) {
          if (Role.getRole(node) == Role.ROLE_SEEK_CONTROL) {
            actionDescription =
                context.getString(R.string.title_pref_selector_adjustable_widget_slider);
          } else {
            actionDescription =
                context.getString(R.string.title_pref_selector_adjustable_widget_number_picker);
          }
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
    @Nullable Setting currentSetting =
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
      settingToRestore = getCurrentSetting();
      setCurrentSetting(
          EVENT_ID_UNTRACKED,
          Setting.GRANULARITY_CHARACTERS,
          /* announce= */ false,
          /* showOverlay= */ false);
      return;
    }

    // When ending the selection mode, restore to the original setting if it is allowed by the
    // current focused node.
    if (settingToRestore != null) {
      restoreSetting();
    }
  }

  /**
   * Processes the accessibility focus changed event.
   *
   * <p>Note: We need to consider to adjust the reading menu setting. The argument {@code nodeInfo}
   * is wrapped into AccessibilityNodeInfoCompat node.
   */
  public void newItemFocused(AccessibilityNodeInfo nodeInfo) {
    Setting currentSetting = getCurrentSetting();
    if (nodeInfo != null) {
      Optional<ContextualSetting> contextualMenu =
          getMatchedContextualSetting(context, AccessibilityNodeInfoCompat.wrap(nodeInfo));
      // If a matched contextual setting is available, switch to that setting automatically.
      if (contextualMenu.isPresent()) {
        if (contextualMenu.get().getSetting() != currentSetting) {
          settingToRestore = currentSetting;
          setCurrentSetting(
              EVENT_ID_UNTRACKED,
              contextualMenu.get().getSetting(),
              /* announce= */ false,
              /* showOverlay= */ false);
        }

        return;
      }
    }

    // If the current setting is a contextual setting and no longer allowed by the new focused
    // node, then restore to the cached setting or move the setting to the first available item if
    // the cached setting is also unavailable.
    if (isContextualSetting(currentSetting)) {
      restoreSetting();
    }
  }

  /** Selects the previous or next setting. For selecting setting via gesture. */
  public void selectPreviousOrNextSetting(EventId eventId, boolean isNext) {
    List<Setting> settings = getFilteredSettings();

    int settingsSize = settings.size();
    if (settingsSize == 0) {
      return;
    }

    // Get the index of the selected setting.
    int index = settings.indexOf(getCurrentSetting());

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

    setCurrentSetting(eventId, settings.get(index), true, /* showOverlay= */ true);
  }

  /**
   * Selects the first setting from {@link #getFilteredSettings()} if trying to use an unavailable
   * setting.
   */
  private void selectFirstAvailableSetting() {
    List<Setting> settings = getFilteredSettings();
    if (!settings.isEmpty()) {
      selectSetting(settings.get(0), false, false);
    } else {
      resetSelectedSetting(context);
    }
  }

  /**
   * Restores the cached setting, or switches to the first allowed item if the cached setting is
   * rejected.
   */
  private void restoreSetting() {
    if (settingToRestore != null && allowedSetting(settingToRestore)) {
      setCurrentSetting(
          EVENT_ID_UNTRACKED, settingToRestore, /* announce= */ false, /* showOverlay= */ false);
    } else {
      selectFirstAvailableSetting();
    }

    // Clear the cache setting.
    settingToRestore = null;
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

  /** Returns whether the {@link Setting} is available or not. */
  private boolean allowedSetting(Setting setting) {
    return allowedSetting(setting, /* node= */ null);
  }

  /** Returns whether the {@link Setting} is available or not. */
  private boolean allowedSetting(Setting setting, @Nullable AccessibilityNodeInfoCompat node) {
    String preferenceKey = context.getString(setting.prefKeyResId);

    // Check if the SwitchPreference is on for this setting.
    if (!prefs.getBoolean(
        preferenceKey, context.getResources().getBoolean(setting.defaultValueResId))) {
      return false;
    }

    switch (setting) {
      case LANGUAGE:
        {
          return actorState.getLanguageState().allowSelectLanguage();
        }
      case AUDIO_FOCUS:
        {
          // Add audio focus setting if not ARC, as for ARC this preference is not supported.
          return !FeatureSupport.isArc();
        }
      case SCROLLING_SEQUENTIAL:
        return true;
      case CHANGE_ACCESSIBILITY_VOLUME:
        return FeatureSupport.hasAccessibilityAudioStream(context);
      case ACTIONS:
        {
          if (node == null) {
            node = accessibilityFocusMonitor.getAccessibilityFocus(false);
          }

          return hasActions(node);
        }
      case GRANULARITY_HEADINGS:
      case GRANULARITY_WORDS:
      case GRANULARITY_PARAGRAPHS:
      case GRANULARITY_CHARACTERS:
      case GRANULARITY_LINES:
      case GRANULARITY_LINKS:
      case GRANULARITY_CONTROLS:
      case GRANULARITY_LANDMARKS:
      case GRANULARITY_WINDOWS:
      case GRANULARITY_DEFAULT:
        {
          return Granularity.getSupportedGranularity(actorState, setting) != null;
        }
      case ADJUSTABLE_WIDGET:
        {
          Optional<ContextualSetting> adjustableWidget = findContextualSetting(ADJUSTABLE_WIDGET);
          if (!adjustableWidget.isPresent()) {
            return false;
          }

          if (node == null) {
            node =
                accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ false);
          }
          return adjustableWidget.get().shouldActivateSetting(context, node);
        }
      default:
        return true;
    }
  }

  /**
   * Filter settings based on device and the focused node. Filter out the settings turned off by
   * users in selector preferences.
   */
  @VisibleForTesting
  ImmutableList<Setting> getFilteredSettings() {
    ImmutableList.Builder<Setting> filteredSettingsBuilder = ImmutableList.builder();
    @Nullable AccessibilityNodeInfoCompat node =
        accessibilityFocusMonitor.getAccessibilityFocus(false);

    for (Setting setting : SELECTOR_SETTINGS) {
      if (HIDDEN_SETTINGS.contains(setting)) {
        continue;
      }

      if (allowedSetting(setting, node)) {
        filteredSettingsBuilder.add(setting);
      }
    }
    return filteredSettingsBuilder.build();
  }

  private boolean hasActions(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    AccessibilityNodeInfoCompat actionNode =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_HAS_MENU_ACTION);
    return actionNode != null;
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
                          | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE
                          | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_PHONE_CALL_ACTIVE
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
    Setting currentSetting = getCurrentSetting();
    if (isContextualSetting(currentSetting)
        && !accessibilityFocusMonitor.hasAccessibilityFocus(/* useInputFocusIfEmpty= */ false)) {
      restoreSetting();
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
          // Granularity is phased out. Assigns to the character setting.
          updateSettingPref(context, GRANULARITY_CHARACTERS);
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
      case GRANULARITY_WINDOWS:
      case GRANULARITY_DEFAULT:
        {
          List<Granularity> granularities = Granularity.getFromSetting(currentSetting);
          if (granularities.isEmpty()) {
            return;
          }

          @Nullable AccessibilityNodeInfoCompat node =
              accessibilityFocusMonitor.getAccessibilityFocus(false);
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
        }
        return;
      case ACTIONS:
        changeAction(eventId, isNext);
        return;
      case ADJUSTABLE_WIDGET:
        handleAdjustable(eventId, isNext);
        return;
    }
  }

  /** Activate the currently selected action in the actions setting. */
  public void activateCurrentAction(EventId eventId) {
    if (!accessibilityFocusMonitor.hasAccessibilityFocus(/* useInputFocusIfEmpty= */ false)) {
      return;
    }
    analytics.onSelectorActionEvent(getCurrentSetting());

    // Check if the SwitchPreference is on for ACTION setting.
    if (!prefs.getBoolean(
        context.getString(ACTIONS.prefKeyResId),
        context.getResources().getBoolean(ACTIONS.defaultValueResId))) {
      pipeline.returnFeedback(
          eventId, Feedback.speech(context.getString(R.string.actions_setting_not_enabled)));
      return;
    }
    if (currentActionId == NO_SETTING_INSERTED) {
      // Hint needs to be before text in talkback structure.
      pipeline.returnFeedback(
          eventId,
          Feedback.Part.builder()
              .setSpeech(
                  Speech.builder()
                      .setAction(Speech.Action.SPEAK)
                      .setText(context.getString(R.string.no_action_selected))
                      .setHintSpeakOptions(
                          SpeechController.SpeakOptions.create()
                              .setFlags(FeedbackItem.FLAG_NO_HISTORY))
                      .setHint(context.getString(R.string.hint_select_action))
                      .build()));
      return;
    }

    @Nullable AccessibilityNodeInfoCompat node =
        accessibilityFocusMonitor.getAccessibilityFocus(false);
    if (node != null) {
      if (!performActionOnNodeOrAncestor(eventId, node)) {
        pipeline.returnFeedback(
            eventId,
            Feedback.Part.builder()
                .setSpeech(
                    Speech.builder()
                        .setAction(Speech.Action.SPEAK)
                        .setText(context.getString(R.string.action_not_supported))
                        .setHintSpeakOptions(
                            SpeechController.SpeakOptions.create()
                                .setFlags(FeedbackItem.FLAG_NO_HISTORY))
                        .setHint(context.getString(R.string.hint_select_action))
                        .build()));
      }
    }
  }

  /**
   * Perform the selected action on this node or its parents. Starting from this node, this function
   * will search up to find a node which has qualified actions. If the node found has the selected
   * action, the action will be performed and return true. Otherwise, this function will return
   * false.
   *
   * @param eventId An event id that can be used to track performance through later stages.
   * @param node The current node.
   * @return If the node found has the selected action, the action will be performed and return
   *     true. Otherwise, it will return false.
   */
  private boolean performActionOnNodeOrAncestor(
      EventId eventId, @Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    AccessibilityNodeInfoCompat actionNode =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_HAS_MENU_ACTION);
    if (actionNode == null) {
      return false;
    }

    // Only actions from the lowest-matching-ancestor will be used.
    for (AccessibilityActionCompat action : actionNode.getActionList()) {
      if (currentActionId == action.getId()) {
        pipeline.returnFeedback(eventId, Feedback.nodeAction(actionNode, currentActionId));
        return true;
      }
    }
    return false;
  }

  private void handleAdjustable(EventId eventId, boolean isNext) {
    pipeline.returnFeedback(
        eventId, Feedback.adjustValue(isNext ? DECREASE_VALUE : INCREASE_VALUE));
  }

  /** Moves to the next or previous at specific granularity. */
  public void moveAtGranularity(EventId eventId, Granularity granularity, boolean isNext) {
    // Sets granularity and locks navigate within the focused node.
    pipeline.returnFeedback(eventId, Feedback.granularity(granularity.cursorGranularity));

    Setting setting = Granularity.getSettingFromCursorGranularity(granularity.cursorGranularity);
    boolean setToWindow = setting.equals(GRANULARITY_WINDOWS);
    boolean result =
        pipeline.returnFeedback(
            eventId,
            Feedback.focusDirection(isNext ? SEARCH_FOCUS_FORWARD : SEARCH_FOCUS_BACKWARD)
                .setInputMode(INPUT_MODE_TOUCH)
                .setToWindow(setToWindow)
                .setDefaultToInputFocus(true)
                .setScroll(true)
                .setWrap(true));
    if (!result) {
      pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
    }
  }

  public void changeSpeechRate(EventId eventId, boolean isIncrease) {
    pipeline.returnFeedback(
        eventId, Feedback.speechRate(isIncrease ? Action.INCREASE_RATE : Action.DECREASE_RATE));
    String displayText =
        context.getString(
            R.string.template_speech_rate_change,
            actorState.getSpeechRateState().getSpeechRatePercentage());
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
        VerbosityPrefFragment.getVerbosityChangeAnnouncement(newVerbosity, context),
        getSelectSettingGestures());
    showQuickMenuActionOverlay(
        eventId, VerbosityPrefFragment.verbosityValueToName(newVerbosity, context));
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
        VerbosityPrefFragment.getVerbosityChangeAnnouncement(newVerbosity, context),
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
    boolean result =
        pipeline.returnFeedback(
            eventId,
            Feedback.adjustVolume(
                isNext ? DECREASE_VOLUME : INCREASE_VOLUME, STREAM_TYPE_ACCESSIBILITY));
    String displayText;
    if (result) {
      displayText =
          context.getString(
              isNext
                  ? R.string.template_volume_change_decrease
                  : R.string.template_volume_change_increase);
    } else {
      displayText =
          context.getString(
              isNext
                  ? R.string.template_volume_change_minimum
                  : R.string.template_volume_change_maximum);
    }
    announceSetting(eventId, displayText, getSelectSettingGestures());
    showQuickMenuActionOverlay(eventId, displayText);
  }

  private void changeAction(EventId eventId, boolean isNext) {
    CharSequence displayText;
    @Nullable AccessibilityNodeInfoCompat node =
        accessibilityFocusMonitor.getAccessibilityFocus(false);
    if (node != null) {
      List<AccessibilityActionCompat> actions = new ArrayList<>();
      int currentActionIndex = populateActionItemsForNode(node, actions);
      // This could happen when the accessibility focused node changed.
      int actionSize = actions.size();
      if (actionSize == 0) {
        announceSetting(
            eventId, context.getString(R.string.no_action_available), getSelectSettingGestures());
        return;
      }
      final AccessibilityActionCompat action;
      if (currentActionIndex == -1) {
        action = actions.get(0);
      } else {
        if (isNext) {
          action = actions.get((currentActionIndex + 1) % actionSize);
        } else {
          action = actions.get((currentActionIndex - 1 + actionSize) % actionSize);
        }
      }
      currentActionId = action.getId();
      displayText = action.getLabel();
      if (displayText == null) {
        if (currentActionId == AccessibilityNodeInfoCompat.ACTION_DISMISS) {
          displayText = context.getString(R.string.title_action_dismiss);
        } else if (currentActionId == AccessibilityNodeInfoCompat.ACTION_EXPAND) {
          displayText = context.getString(R.string.title_action_expand);
        } else if (currentActionId == AccessibilityNodeInfoCompat.ACTION_COLLAPSE) {
          displayText = context.getString(R.string.title_action_collapse);
        } else {
          // This should not happen as when we populate the actions, we already check the label.
          displayText = context.getString(R.string.value_unlabelled);
        }
      }
      announceSetting(eventId, displayText.toString(), getSelectSettingGestures());
      showQuickMenuActionOverlay(eventId, displayText);
    }
  }

  /**
   * Populate eligible actions for the node. If this node doesn t'have any eligible actions, search
   * it parents until eligible actions are found or until all the parents are searched.
   *
   * @param node current node.
   * @param actions list of eligible action.
   * @return if we find an action with the same id of currentActionId, return the index of it in the
   *     actions list. Otherwise we return -1 to indicate action with the same id of currentActionId
   *     is not found.
   */
  private int populateActionItemsForNode(
      @Nullable AccessibilityNodeInfoCompat node, List<AccessibilityActionCompat> actions) {
    if (node == null) {
      return -1;
    }

    AccessibilityNodeInfoCompat actionNode = null;
    actionNode = AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(node, FILTER_HAS_MENU_ACTION);
    if (actionNode == null) {
      return -1;
    }

    int currentActionIndex = -1;
    for (AccessibilityActionCompat action : actionNode.getActionList()) {
      if (shouldIncludeAction(action)) {
        if (action.getId() == currentActionId) {
          currentActionIndex = actions.size();
        }
        actions.add(action);
      }
    }
    return currentActionIndex;
  }

  private static boolean shouldIncludeAction(AccessibilityActionCompat action) {
    int id = action.getId();
    return ((AccessibilityNodeInfoUtils.isCustomAction(action) && (action.getLabel() != null))
        || (id == AccessibilityNodeInfoCompat.ACTION_DISMISS)
        || (id == AccessibilityNodeInfoCompat.ACTION_EXPAND)
        || (id == AccessibilityNodeInfoCompat.ACTION_COLLAPSE));
  }

  private static boolean isContextualSetting(Setting setting) {
    return CONTEXTUAL_SETTINGS.stream().anyMatch((s) -> s.getSetting() == setting);
  }

  private static Optional<ContextualSetting> findContextualSetting(Setting setting) {
    return CONTEXTUAL_SETTINGS.stream().filter((s) -> s.getSetting() == setting).findFirst();
  }

  private static Optional<ContextualSetting> getMatchedContextualSetting(
      Context context, AccessibilityNodeInfoCompat node) {
    return CONTEXTUAL_SETTINGS.stream()
        .filter((s) -> s.shouldActivateSetting(context, node))
        .findFirst();
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

    if (FeatureSupport.isWatch(context)) {
      // Watch never uses multi-finger and won't show surrounding icons.
      pipeline.returnFeedback(
          eventId,
          Feedback.showSelectorUI(
              SELECTOR_MENU_ITEM_OVERLAY_SINGLE_FINGER, message, /* showIcon= */ false));
    } else if (FeatureSupport.isMultiFingerGestureSupported()
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
              SELECTOR_MENU_ITEM_OVERLAY_MULTI_FINGER, message, /* showIcon= */ true));
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
              SELECTOR_MENU_ITEM_OVERLAY_SINGLE_FINGER,
              message,
              isSwipeUpDownAndDownUpForSelector));
    }
  }

  private void showQuickMenuActionOverlay(EventId eventId, CharSequence message) {
    String selectedSettingPreviousAction =
        context.getString(R.string.shortcut_value_selected_setting_previous_action);
    String selectedSettingNextAction =
        context.getString(R.string.shortcut_value_selected_setting_next_action);
    boolean isSwipeUpDownForSelector =
        !FeatureSupport.isWatch(context)
            && selectedSettingPreviousAction.equals(
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

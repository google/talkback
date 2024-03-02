/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.preference.base;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.preference.DialogPreference;
import com.google.android.accessibility.talkback.FeatureFlagReader;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.GestureReporter;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping;
import com.google.android.accessibility.talkback.utils.TalkbackFeatureSupport;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.preference.AccessibilitySuiteDialogPreference;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;

/**
 * A {@link DialogPreference} which contains a list for all TalkBack supported actions. It works
 * with {@link GesturePreferenceFragmentCompat} to provide a customized dialog for gesture setting.
 *
 * <p><b>Use {@link #createDialogFragment()} to create the dialog fragment.<b/>
 */
public final class GestureListPreference extends AccessibilitySuiteDialogPreference {
  private static final String TAG = "GestureListPreference";

  /** Type of view style for the action list. */
  @IntDef({TYPE_TITLE, TYPE_ACTION_ITEM})
  @Retention(RetentionPolicy.SOURCE)
  private @interface ActionViewType {}

  /** A category of actions. */
  static final int TYPE_TITLE = 0;

  /** A supported action. */
  static final int TYPE_ACTION_ITEM = 1;

  // Every item in the page, contains titles and actions.
  private ImmutableList<ActionItem> items;
  private String initialValue;
  // A customized summary which is used when the preference is in disable state
  @Nullable private String summaryWhenDisabled;
  private final FormFactorUtils formFactorUtils;

  public GestureListPreference(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
    formFactorUtils = FormFactorUtils.getInstance();
    // Change preference title to multi-line style.
    setSingleLineTitle(false);
    populateActionItems();
  }

  @Override
  public CharSequence getSummary() {
    return getCurrentActionText();
  }

  @Override
  protected Object onGetDefaultValue(TypedArray a, int index) {
    initialValue = a.getString(index);
    return initialValue;
  }

  /** Updates the summary of preference to default value. */
  void updateSummaryToDefaultValue() {
    String actionString = GestureShortcutMapping.getActionString(getContext(), initialValue);
    setSummary(actionString);
  }

  /**
   * Creates a fragment to show the available action items for this gesture preference. The style of
   * this fragment is subject to the form factor.
   *
   * <p>The base classes for the {@link GesturePreferenceFragmentCompat} are as follows:
   *
   * <ul>
   *   <li>Handset: {@link androidx.preference.PreferenceDialogFragmentCompat}
   *   <li>Auto: {@link androidx.preference.PreferenceDialogFragmentCompat}
   *   <li>Wear: {@link androidx.preference.PreferenceFragmentCompat}
   * </ul>
   */
  public GesturePreferenceFragmentCompat createDialogFragment() {
    return GesturePreferenceFragmentCompat.create(this);
  }

  /** Returns all supported actions. */
  public ActionItem[] getActionItems() {
    return items.toArray(new ActionItem[items.size()]);
  }

  /** The setter of the preference value. */
  public void setValue(String value) {
    SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
    sharedPreferences.edit().putString(getKey(), value).apply();
  }

  /** The setter of the summary for disabled case. */
  public void setSummaryWhenDisabled(String summary) {
    summaryWhenDisabled = summary;
  }

  /**
   * Returns the current value of the preference. For example, "READ_FROM_TOP" if the preference is
   * pointed to the action "Read from top".
   */
  public String getCurrentValue() {
    String targetValue =
        getPreferenceManager().getSharedPreferences().getString(getKey(), initialValue);

    // returns "TALKBACK_BREAKOUT" if targetText is "LOCAL_BREAKOUT"
    if (TextUtils.equals(
        targetValue, getContext().getString(R.string.shortcut_value_local_breakout))) {
      return getContext().getString(R.string.shortcut_value_talkback_breakout);
    }
    // returns "SHOW_CUSTOM_ACTIONS" if targetText is "EDITING"
    if (TextUtils.equals(targetValue, getContext().getString(R.string.shortcut_value_editing))) {
      return getContext().getString(R.string.shortcut_value_show_custom_actions);
    }

    return targetValue;
  }

  public String getDefaultValue() {
    return initialValue;
  }

  /** Returns the text of the action for the current preference value. */
  private String getCurrentActionText() {
    if (!isEnabled() && summaryWhenDisabled != null) {
      return summaryWhenDisabled;
    }
    String value = getCurrentValue();
    Optional<ActionItem> item =
        items.stream().filter((s) -> TextUtils.equals(s.value, value)).findFirst();
    if (item.isPresent()) {
      return item.get().text;
    }
    LogUtils.w(TAG, "%s - Can't find the value from supported action list.", getTitle());
    return getContext().getResources().getString(R.string.shortcut_unassigned);
  }

  private void populateActionItems() {
    ImmutableList.Builder<ActionItem> builder = ImmutableList.builder();
    builder.add(
        new ActionItem(
            getContext().getResources().getString(R.string.shortcut_unassigned),
            getContext().getResources().getString(R.string.shortcut_value_unassigned),
            TYPE_ACTION_ITEM));
    addActionItemsToList(builder, createBasicNavigation());
    addActionItemsToList(builder, createSystemActions());
    addActionItemsToList(builder, createReadingControl());
    addActionItemsToList(builder, createMenuControl());
    addActionItemsToList(builder, createTextEditing());
    addActionItemsToList(builder, createSpecialFeatures());
    items = builder.build();
  }

  private static void addActionItemsToList(
      ImmutableList.Builder<ActionItem> builder, ImmutableList<ActionItem> items) {
    // Skips if only has title without any items.
    if ((items.size() == 1) && (items.get(0).viewType == TYPE_TITLE)) {
      return;
    }
    builder.addAll(items);
  }

  private ImmutableList<ActionItem> createBasicNavigation() {
    ImmutableList.Builder<ActionItem> builder =
        createActionListBuilder(
            R.string.shortcut_title_basic_navigation,
            R.array.shortcut_basic_navigation,
            R.array.shortcut_value_basic_navigation);
    Resources resources = getContext().getResources();

    // TODO: Remove scroll left/right/up/down once 2-finger pass through is ready.
    if (FeatureSupport.isMultiFingerGestureSupported() && !formFactorUtils.isAndroidWear()) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_scroll_up),
              resources.getString(R.string.shortcut_value_scroll_up),
              TYPE_ACTION_ITEM));
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_scroll_down),
              resources.getString(R.string.shortcut_value_scroll_down),
              TYPE_ACTION_ITEM));
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_scroll_left),
              resources.getString(R.string.shortcut_value_scroll_left),
              TYPE_ACTION_ITEM));
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_scroll_right),
              resources.getString(R.string.shortcut_value_scroll_right),
              TYPE_ACTION_ITEM));
    }

    return builder.build();
  }

  private ImmutableList<ActionItem> createSystemActions() {
    ImmutableList.Builder<ActionItem> builder =
        createActionListBuilder(
            R.string.shortcut_title_system_actions,
            R.array.shortcut_system_actions,
            R.array.shortcut_value_system_actions);
    Resources resources = getContext().getResources();
    if (FeatureSupport.supportGetSystemActions(getContext())) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_all_apps),
              resources.getString(R.string.shortcut_value_all_apps),
              TYPE_ACTION_ITEM));
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_a11y_button),
              resources.getString(R.string.shortcut_value_a11y_button),
              TYPE_ACTION_ITEM));
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_a11y_button_long_press),
              resources.getString(R.string.shortcut_value_a11y_button_long_press),
              TYPE_ACTION_ITEM));
    }
    return builder.build();
  }

  private ImmutableList<ActionItem> createReadingControl() {
    return createActionListBuilder(
            R.string.shortcut_title_reading_control,
            R.array.shortcut_reading_control,
            R.array.shortcut_value_reading_control)
        .build();
  }

  private ImmutableList<ActionItem> createMenuControl() {
    return createActionListBuilder(
            R.string.shortcut_title_menu_control,
            R.array.shortcut_menu_control,
            R.array.shortcut_value_menu_control)
        .build();
  }

  private ImmutableList<ActionItem> createTextEditing() {
    ImmutableList.Builder<ActionItem> builder =
        createActionListBuilder(
            R.string.shortcut_title_text_editing,
            R.array.shortcut_text_editing,
            R.array.shortcut_value_text_editing);
    Resources resources = getContext().getResources();
    if (FeatureSupport.supportSwitchToInputMethod() && !formFactorUtils.isAndroidWear()) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_braille_keyboard),
              resources.getString(R.string.shortcut_value_braille_keyboard),
              TYPE_ACTION_ITEM));
    }
    return builder.build();
  }

  private ImmutableList<ActionItem> createSpecialFeatures() {
    ImmutableList.Builder<ActionItem> builder =
        createActionListBuilder(R.string.shortcut_title_special_features, 0, 0);
    Resources resources = getContext().getResources();

    if (FeatureSupport.supportMediaControls()) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_media_control),
              resources.getString(R.string.shortcut_value_media_control),
              TYPE_ACTION_ITEM));
    }

    builder.add(
        new ActionItem(
            resources.getString(R.string.shortcut_increase_volume),
            resources.getString(R.string.shortcut_value_increase_volume),
            TYPE_ACTION_ITEM));

    builder.add(
        new ActionItem(
            resources.getString(R.string.shortcut_decrease_volume),
            resources.getString(R.string.shortcut_value_decrease_volume),
            TYPE_ACTION_ITEM));

    if (TalkbackFeatureSupport.supportSpeechRecognize()) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_voice_commands),
              resources.getString(R.string.shortcut_value_voice_commands),
              TYPE_ACTION_ITEM));
    }

    if (!formFactorUtils.isAndroidWear()) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.title_show_screen_search),
              resources.getString(R.string.shortcut_value_screen_search),
              TYPE_ACTION_ITEM));
    }

    builder.add(
        new ActionItem(
            resources.getString(R.string.title_show_hide_screen),
            resources.getString(R.string.shortcut_value_show_hide_screen),
            TYPE_ACTION_ITEM));

    if (FeatureSupport.supportPassthrough()) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_pass_through_next),
              resources.getString(R.string.shortcut_value_pass_through_next_gesture),
              TYPE_ACTION_ITEM));
    }

    SharedPreferences preferences = SharedPreferencesUtils.getSharedPreferences(getContext());
    if (preferences.getBoolean(resources.getString(R.string.pref_tree_debug_key), false)) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_print_node_tree),
              resources.getString(R.string.shortcut_value_print_node_tree),
              TYPE_ACTION_ITEM));
    }

    if (preferences.getBoolean(resources.getString(R.string.pref_performance_stats_key), false)) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_print_performance_stats),
              resources.getString(R.string.shortcut_value_print_performance_stats),
              TYPE_ACTION_ITEM));
    }

    builder.add(
        new ActionItem(
            resources.getString(R.string.shortcut_show_custom_actions),
            resources.getString(R.string.shortcut_value_show_custom_actions),
            TYPE_ACTION_ITEM));

    if (FeatureSupport.supportBrailleDisplay(getContext())) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_braille_display_settings),
              resources.getString(R.string.shortcut_value_braille_display_settings),
              TYPE_ACTION_ITEM));
    }

    builder.add(
        new ActionItem(
            resources.getString(R.string.shortcut_tutorial),
            resources.getString(R.string.shortcut_value_tutorial),
            TYPE_ACTION_ITEM));

    if (!formFactorUtils.isAndroidWear()) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_practice_gestures),
              resources.getString(R.string.shortcut_value_practice_gestures),
              TYPE_ACTION_ITEM));
    }

    if (GestureReporter.ENABLED && SettingsUtils.allowLinksOutOfSettings(getContext())) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_report_gesture),
              resources.getString(R.string.shortcut_value_report_gesture),
              TYPE_ACTION_ITEM));
    }

    if (FeatureSupport.supportBrailleDisplay(getContext())
        && FeatureFlagReader.useGestureBrailleDisplayOnOff(getContext())) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.shortcut_toggle_braille_display),
              resources.getString(R.string.shortcut_value_toggle_braille_display),
              TYPE_ACTION_ITEM));
    }

    if (ImageCaptioner.supportsImageCaption(getContext())) {
      builder.add(
          new ActionItem(
              resources.getString(R.string.title_image_caption),
              resources.getString(R.string.shortcut_value_describe_image),
              TYPE_ACTION_ITEM));
    }

    return builder.build();
  }

  private ImmutableList.Builder<ActionItem> createActionListBuilder(
      int titleId, int shortcutId, int valueId) {
    ImmutableList.Builder<ActionItem> builder = ImmutableList.builder();
    Resources resources = getContext().getResources();
    builder.add(new ActionItem(resources.getString(titleId), TYPE_TITLE));
    if (shortcutId != 0) {
      builder.addAll(
          ActionItem.createItemsFromArray(
              resources.getStringArray(shortcutId), resources.getStringArray(valueId)));
    }
    return builder;
  }

  /**
   * Represents a TalkBack action item. It implements {@link Parcelable} to pass the value by
   * arguments of a fragment.
   */
  public static final class ActionItem implements Parcelable {
    public static final Parcelable.Creator<ActionItem> CREATOR =
        new Parcelable.Creator<ActionItem>() {
          @Override
          public ActionItem createFromParcel(Parcel in) {
            String text = in.readString();
            String value = in.readString();
            int viewType = in.readInt();
            return new ActionItem(text, value, viewType);
          }

          @Override
          public ActionItem[] newArray(int size) {
            return new ActionItem[size];
          }
        };

    private static final String ACTION_NO_VALUE = "";
    final String text;
    final String value;
    @ActionViewType final int viewType;

    ActionItem(String text, @ActionViewType int viewType) {
      this(text, ACTION_NO_VALUE, viewType);
    }

    ActionItem(String text, String value, @ActionViewType int viewType) {
      this.text = text;
      this.value = value;
      this.viewType = viewType;
    }

    @Override
    public int describeContents() {
      return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeString(text);
      dest.writeString(value);
      dest.writeInt(viewType);
    }

    private static ImmutableList<ActionItem> createItemsFromArray(String[] texts, String[] values) {
      if (texts.length != values.length) {
        LogUtils.e(TAG, "createItemsFromArray : Length doesn't match.");
        return ImmutableList.of();
      }

      if (texts.length == 0) {
        LogUtils.e(TAG, "createItemsFromArray : Empty array");
        return ImmutableList.of();
      }

      ImmutableList.Builder<ActionItem> listBuilder = ImmutableList.builder();
      for (int i = 0; i < texts.length; ++i) {
        listBuilder.add(new ActionItem(texts[i], values[i], TYPE_ACTION_ITEM));
      }
      return listBuilder.build();
    }
  }
}

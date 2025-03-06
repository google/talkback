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

package com.google.android.accessibility.braille.brailledisplay.controller.utils;

import static androidx.core.content.res.ResourcesCompat.ID_NULL;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.accessibility.braille.brailledisplay.FeatureFlagReader;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.Category;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.KeyDescriptor;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.Subcategory;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.brltty.BrailleKeyBinding;
import com.google.android.accessibility.braille.brltty.BrlttyUtils;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.auto.value.AutoValue;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Contains utility methods for working with BrailleKeyBindings. */
public class BrailleKeyBindingUtils {
  private static List<SupportedCommand> supportedCommands;

  private BrailleKeyBindingUtils() {
    // Not instantiable.
  }

  /** Returns a sorted list of bindings supported by the display. */
  public static ArrayList<BrailleKeyBinding> getSortedBindingsForDisplay(
      BrailleDisplayProperties props) {
    BrailleKeyBinding[] bindings = props.getKeyBindings();
    ArrayList<BrailleKeyBinding> sortedBindings =
        new ArrayList<BrailleKeyBinding>(Arrays.asList(bindings));
    Collections.sort(sortedBindings, COMPARE_BINDINGS);
    return sortedBindings;
  }

  /**
   * Returns the binding that matches the specified command in the sorted list of
   * BrailleKeyBindings. Returns null if not found.
   */
  @Nullable
  public static BrailleKeyBinding getBrailleKeyBindingForCommand(
      int command, ArrayList<BrailleKeyBinding> sortedBindings) {
    BrailleKeyBinding dummyBinding = new BrailleKeyBinding();
    dummyBinding.setCommand(command);
    int index = Collections.binarySearch(sortedBindings, dummyBinding, COMPARE_BINDINGS_BY_COMMAND);
    if (index < 0) {
      return null;
    }
    while (index > 0 && sortedBindings.get(index - 1).getCommand() == command) {
      index -= 1;
    }
    return sortedBindings.get(index);
  }

  /** Returns the friendly name for the specified key binding. */
  public static String getFriendlyKeyNamesForCommand(
      BrailleKeyBinding binding, Map<String, String> friendlyKeyNames, Context context) {
    List<String> keyNames =
        getFriendlyKeyNames(Lists.newArrayList(binding.getKeyNames()), friendlyKeyNames);
    BrailleCharacter brailleCharacter = BrlttyUtils.extractBrailleCharacter(context, keyNames);
    if (!brailleCharacter.isEmpty()) {
      keyNames.add(getDotsDescription(context.getResources(), brailleCharacter));
    }
    String keys =
        changeToSentence(context.getResources(), keyNames.stream().toArray(String[]::new));
    return binding.isLongPress()
        ? context.getString(R.string.bd_commands_touch_and_hold_template, keys)
        : context.getString(R.string.bd_commands_press_template, keys);
  }

  /**
   * Returns a String representation of {@link BrailleCharacter} with human understanding the
   * sentence. Example: "⠏" -> "dots 1, 2, 3 and 4".
   */
  private static String getDotsDescription(Resources resources, BrailleCharacter brailleCharacter) {
    String dotsString = changeToSentence(resources, brailleCharacter.toLocaleString().split(""));
    return resources.getQuantityString(
            com.google.android.accessibility.braille.common.R.plurals.braille_dots, brailleCharacter.getOnCount(), dotsString);
  }

  /** Compose words to a sentence. Example: "abcd" -> "a, b, c and d". */
  private static String changeToSentence(Resources resources, String[] words) {
    if (words.length > 1) {
      StringBuilder sentence = new StringBuilder();
      sentence.append(words[0]);
      for (int i = 1; i < words.length; i++) {
        sentence.append(resources.getString(com.google.android.accessibility.braille.common.R.string.split_comma, words[i]));
      }
      return sentence.toString();
    } else {
      return TextUtils.join("", words);
    }
  }

  /** Returns friendly key names (if available) based on the map. */
  public static List<String> getFriendlyKeyNames(
      List<String> unfriendlyNames, Map<String, String> friendlyNames) {
    List<String> result = new ArrayList<>();
    for (String unfriendlyName : unfriendlyNames) {
      String friendlyName = friendlyNames.get(unfriendlyName);
      if (friendlyName != null) {
        result.add(friendlyName);
      } else {
        result.add(unfriendlyName);
      }
    }
    return result;
  }

  /**
   * Compares key bindings by command number, then in an order that is deterministic and that makes
   * sure that the binding that should appear on the help screen comes first.
   */
  public static final Comparator<BrailleKeyBinding> COMPARE_BINDINGS =
      new Comparator<BrailleKeyBinding>() {
        @Override
        public int compare(BrailleKeyBinding lhs, BrailleKeyBinding rhs) {
          int command1 = lhs.getCommand();
          int command2 = rhs.getCommand();
          if (command1 != command2) {
            return command1 - command2;
          }
          // Prefer a binding without long press.
          boolean longPress1 = lhs.isLongPress();
          boolean longPress2 = rhs.isLongPress();
          if (longPress1 != longPress2) {
            return longPress1 ? 1 : -1;
          }
          // Prefer unified KeyBinding.
          boolean unified1 = lhs.isUnifiedKeyBinding();
          boolean unified2 = rhs.isUnifiedKeyBinding();
          if (unified1 != unified2) {
            return unified1 ? -1 : 1;
          }
          String[] names1 = lhs.getKeyNames();
          String[] names2 = rhs.getKeyNames();
          // Prefer fewer keys.
          if (names1.length != names2.length) {
            return names1.length - names2.length;
          }
          // Compare key names for determinism.
          for (int i = 0; i < names1.length; ++i) {
            String key1 = names1[i];
            String key2 = names2[i];
            int res = key1.compareTo(key2);
            if (res != 0) {
              return res;
            }
          }
          return 0;
        }
      };

  /** Compares key bindings by command number. Used for search. */
  public static final Comparator<BrailleKeyBinding> COMPARE_BINDINGS_BY_COMMAND =
      new Comparator<BrailleKeyBinding>() {
        @Override
        public int compare(BrailleKeyBinding lhs, BrailleKeyBinding rhs) {
          return lhs.getCommand() - rhs.getCommand();
        }
      };

  /** Constructs and returns an immutable and order-sensitive {@link SupportedCommand} list. */
  public static List<SupportedCommand> getSupportedCommands(Context context) {
    if (supportedCommands == null) {
      List<SupportedCommand> commands = new ArrayList<>();
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_PAN_DOWN,
              R.string.bd_cmd_nav_pan_down,
              SupportedCommand.Category.BASIC,
              KeyDescriptor.builder().setKeyNameRes(R.string.bd_key_pan_down).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_PAN_UP,
              R.string.bd_cmd_nav_pan_up,
              SupportedCommand.Category.BASIC,
              KeyDescriptor.builder().setKeyNameRes(R.string.bd_key_pan_up).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_ROUTE,
              R.string.bd_cmd_route,
              SupportedCommand.Category.BASIC,
              KeyDescriptor.builder().setKeyNameRes(R.string.bd_key_route).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_ACTIVATE_CURRENT,
              R.string.bd_cmd_activate_current,
              SupportedCommand.Category.BASIC,
              KeyDescriptor.builder().setKeyNameRes(R.string.bd_key_route).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_LONG_PRESS_CURRENT,
              R.string.bd_cmd_touch_and_hold_current,
              SupportedCommand.Category.BASIC,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS,
              R.string.bd_cmd_nav_item_previous,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.BASIC,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_ITEM_NEXT,
              R.string.bd_cmd_nav_item_next,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.BASIC,
              KeyDescriptor.builder().setDots(new BrailleCharacter(4, 5, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_SCROLL_BACKWARD,
              R.string.bd_cmd_scroll_backward,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.BASIC,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 4, 6, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_SCROLL_FORWARD,
              R.string.bd_cmd_scroll_forward,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.BASIC,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 3, 5, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent
                  .CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_BACKWARD,
              R.string.bd_cmd_move_by_reading_granularity_or_adjust_reading_control_backward,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.BASIC,
              KeyDescriptor.builder().setDots(new BrailleCharacter(3, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent
                  .CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD,
              R.string.bd_cmd_move_by_reading_granularity_or_adjust_reading_control_forward,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.BASIC,
              KeyDescriptor.builder().setDots(new BrailleCharacter(6, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_TOGGLE_AUTO_SCROLL,
              R.string.bd_cmd_toggle_auto_scroll,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.AUTO_SCROLL,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 2, 4, 5, 6))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_INCREASE_AUTO_SCROLL_DURATION,
              R.string.bd_cmd_increase_auto_scroll_duration,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.AUTO_SCROLL,
              KeyDescriptor.builder().setDots(new BrailleCharacter(4)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_DECREASE_AUTO_SCROLL_DURATION,
              R.string.bd_cmd_decrease_auto_scroll_duration,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.AUTO_SCROLL,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_WINDOW_PREVIOUS,
              R.string.bd_cmd_nav_window_previous,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.WINDOW,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 4, 5, 6, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_WINDOW_NEXT,
              R.string.bd_cmd_nav_window_next,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.WINDOW,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 4, 5, 6, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_TOP,
              R.string.bd_cmd_nav_top,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.PLACE_ON_PAGE,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 2, 3))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_BOTTOM,
              R.string.bd_cmd_nav_bottom,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.PLACE_ON_PAGE,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(4, 5, 6))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_PREVIOUS_READING_CONTROL,
              R.string.bd_cmd_previous_reading_control,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.READING_CONTROLS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 3, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NEXT_READING_CONTROL,
              R.string.bd_cmd_next_reading_control,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.READING_CONTROLS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(5, 6, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_HEADING_PREVIOUS,
              R.string.bd_cmd_heading_previous,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.READING_CONTROLS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 5, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_HEADING_NEXT,
              R.string.bd_cmd_heading_next,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.READING_CONTROLS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 5, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_CONTROL_PREVIOUS,
              R.string.bd_cmd_control_previous,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.READING_CONTROLS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 4, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_CONTROL_NEXT,
              R.string.bd_cmd_control_next,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.READING_CONTROLS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 4, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_LINK_PREVIOUS,
              R.string.bd_cmd_list_previous,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.READING_CONTROLS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 3, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_LINK_NEXT,
              R.string.bd_cmd_list_next,
              SupportedCommand.Category.NAVIGATION,
              Subcategory.READING_CONTROLS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 3, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_GLOBAL_BACK,
              R.string.bd_cmd_global_back,
              SupportedCommand.Category.SYSTEM_ACTIONS,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(1, 2)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_GLOBAL_HOME,
              R.string.bd_cmd_global_home,
              SupportedCommand.Category.SYSTEM_ACTIONS,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 2, 5))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_GLOBAL_NOTIFICATIONS,
              R.string.bd_cmd_global_notifications,
              SupportedCommand.Category.SYSTEM_ACTIONS,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 3, 4, 5))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_GLOBAL_RECENTS,
              R.string.bd_cmd_global_recents,
              SupportedCommand.Category.SYSTEM_ACTIONS,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 2, 3, 5))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_QUICK_SETTINGS,
              R.string.bd_cmd_quick_settings,
              SupportedCommand.Category.SYSTEM_ACTIONS,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 2, 3, 4, 5))
                  .build()));
      if (FeatureSupport.supportGetSystemActions(context)) {
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_ALL_APPS,
                R.string.bd_cmd_global_all_apps,
                SupportedCommand.Category.SYSTEM_ACTIONS,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(1, 2, 3, 4))
                    .build()));
      }
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_TOGGLE_SCREEN_SEARCH,
              R.string.bd_cmd_toggle_screen_search,
              SupportedCommand.Category.TALKBACK_FEATURES,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(3, 4)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_EDIT_CUSTOM_LABEL,
              R.string.bd_cmd_edit_custom_label,
              SupportedCommand.Category.TALKBACK_FEATURES,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 3, 4, 8))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_OPEN_TALKBACK_MENU,
              R.string.bd_cmd_open_talkback_menu,
              SupportedCommand.Category.TALKBACK_FEATURES,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 3, 4))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_TOGGLE_VOICE_FEEDBACK,
              R.string.bd_cmd_toggle_voice_feedback,
              SupportedCommand.Category.TALKBACK_FEATURES,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 3, 4, 7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_STOP_READING,
              R.string.bd_cmd_read_stop,
              SupportedCommand.Category.TALKBACK_FEATURES,
              KeyDescriptor.builder().setDots(new BrailleCharacter(7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_TALKBACK_SETTINGS,
              R.string.bd_cmd_talkback_settings,
              SupportedCommand.Category.TALKBACK_FEATURES,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 3, 4, 5, 7, 8)).build()));
      if (FeatureSupport.supportGetSystemActions(context)
          && FeatureFlagReader.usePlayPauseMedia(context)) {
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_PLAY_PAUSE_MEDIA,
                R.string.bd_cmd_play_pause_media,
                Category.TALKBACK_FEATURES,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(7, 8))
                    .build()));
      }
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_SWITCH_TO_NEXT_INPUT_LANGUAGE,
              R.string.bd_cmd_switch_to_next_input_language,
              SupportedCommand.Category.BRAILLE_SETTINGS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 4, 7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_SWITCH_TO_NEXT_OUTPUT_LANGUAGE,
              R.string.bd_cmd_switch_to_next_output_language,
              SupportedCommand.Category.BRAILLE_SETTINGS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 3, 5, 7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_TOGGLE_BRAILLE_GRADE,
              R.string.bd_cmd_toggle_contracted_mode,
              SupportedCommand.Category.BRAILLE_SETTINGS,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 2, 4, 5))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_HELP,
              R.string.bd_cmd_help,
              SupportedCommand.Category.BRAILLE_SETTINGS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 3, 7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_BRAILLE_DISPLAY_SETTINGS,
              R.string.bd_cmd_braille_display_settings,
              SupportedCommand.Category.BRAILLE_SETTINGS,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_TURN_OFF_BRAILLE_DISPLAY,
              R.string.bd_cmd_turn_off_braille_display,
              SupportedCommand.Category.BRAILLE_SETTINGS,
              KeyDescriptor.builder()
                  .setDots(new BrailleCharacter(1, 2, 3, 4, 5, 6, 7, 8))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NEXT_INPUT_METHOD,
              R.string.bd_cmd_switch_to_next_input_method,
              SupportedCommand.Category.EDITING,
              Subcategory.SWITCH_KEYBOARD,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 3, 8))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_CHARACTER_PREVIOUS,
              R.string.bd_cmd_nav_character_previous,
              SupportedCommand.Category.EDITING,
              Subcategory.MOVE_CURSOR,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(3)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_CHARACTER_NEXT,
              R.string.bd_cmd_nav_character_next,
              SupportedCommand.Category.EDITING,
              Subcategory.MOVE_CURSOR,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(6)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_WORD_PREVIOUS,
              R.string.bd_cmd_nav_word_previous,
              SupportedCommand.Category.EDITING,
              Subcategory.MOVE_CURSOR,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(2)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_WORD_NEXT,
              R.string.bd_cmd_nav_word_next,
              SupportedCommand.Category.EDITING,
              Subcategory.MOVE_CURSOR,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(5)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_LINE_PREVIOUS,
              R.string.bd_cmd_nav_line_previous,
              SupportedCommand.Category.EDITING,
              Subcategory.MOVE_CURSOR,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(1)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_LINE_NEXT,
              R.string.bd_cmd_nav_line_next,
              SupportedCommand.Category.EDITING,
              Subcategory.MOVE_CURSOR,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(4)).build()));
      if (FeatureFlagReader.useSelectAll(context)) {
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_SELECTION_SELECT_ALL,
                R.string.bd_cmd_select_all,
                SupportedCommand.Category.EDITING,
                Subcategory.SELECT,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(1, 2, 3, 4, 5, 6, 8))
                    .build()));
      }
      if (FeatureFlagReader.useSelectCurrentToStartOrEnd(context)) {
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_SELECTION_SELECT_CURRENT_TO_START,
                R.string.bd_cmd_select_cursor_to_start,
                SupportedCommand.Category.EDITING,
                Subcategory.SELECT,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(1, 2, 3, 7, 8))
                    .build()));
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_SELECTION_SELECT_CURRENT_TO_END,
                R.string.bd_cmd_select_cursor_to_end,
                SupportedCommand.Category.EDITING,
                Subcategory.SELECT,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(4, 5, 6, 7, 8))
                    .build()));
      }
      if (FeatureFlagReader.useSelectPreviousNextCharacterWordLine(context)) {
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_SELECT_PREVIOUS_CHARACTER,
                R.string.bd_cmd_select_previous_character,
                SupportedCommand.Category.EDITING,
                Subcategory.SELECT,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(3, 8))
                    .build()));
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_SELECT_NEXT_CHARACTER,
                R.string.bd_cmd_select_next_character,
                SupportedCommand.Category.EDITING,
                Subcategory.SELECT,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(6, 8))
                    .build()));
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_SELECT_PREVIOUS_WORD,
                R.string.bd_cmd_select_previous_word,
                SupportedCommand.Category.EDITING,
                Subcategory.SELECT,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(2, 8))
                    .build()));
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_SELECT_NEXT_WORD,
                R.string.bd_cmd_select_next_word,
                SupportedCommand.Category.EDITING,
                Subcategory.SELECT,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(5, 8))
                    .build()));

        // TODO: As the text selection for line granularity movement does not work,
        // we mask off the action of selecting text by line.
        /* commands.add(
        new SupportedCommand(
            BrailleInputEvent.CMD_SELECT_PREVIOUS_LINE,
            R.string.bd_cmd_select_previous_line,
            SupportedCommand.Category.EDITING,
            Subcategory.SELECT,
            KeyDescriptor.builder()
                .setSpace(true)
                .setDots(new BrailleCharacter(1, 8))
            .build())); */
        /* commands.add(
        new SupportedCommand(
            BrailleInputEvent.CMD_SELECT_NEXT_LINE,
            R.string.bd_cmd_select_next_line,
            SupportedCommand.Category.EDITING,
            Subcategory.SELECT,
            KeyDescriptor.builder()
                .setSpace(true)
                .setDots(new BrailleCharacter(4, 8))
            .build())); */
      }
      if (FeatureFlagReader.useCutCopyPaste(context)) {
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_SELECTION_COPY,
                R.string.bd_cmd_copy,
                SupportedCommand.Category.EDITING,
                Subcategory.EDIT,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(1, 4, 8))
                    .build()));
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_SELECTION_CUT,
                R.string.bd_cmd_cut,
                SupportedCommand.Category.EDITING,
                Subcategory.EDIT,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(1, 3, 4, 6, 8))
                    .build()));
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_SELECTION_PASTE,
                R.string.bd_cmd_paste,
                SupportedCommand.Category.EDITING,
                Subcategory.EDIT,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(1, 2, 3, 6, 8))
                    .build()));
      }
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_KEY_DEL,
              R.string.bd_cmd_key_del,
              SupportedCommand.Category.EDITING,
              Subcategory.EDIT,
              KeyDescriptor.builder().setDots(new BrailleCharacter(7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_DEL_WORD,
              R.string.bd_cmd_del_word,
              SupportedCommand.Category.EDITING,
              Subcategory.EDIT,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(2, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_KEY_ENTER,
              R.string.bd_cmd_key_enter,
              SupportedCommand.Category.EDITING,
              Subcategory.EDIT,
              KeyDescriptor.builder().setDots(new BrailleCharacter(8)).build()));
      supportedCommands = Collections.unmodifiableList(commands);
    }
    return supportedCommands;
  }

  /** Converts {@link BrailleInputEvent} to {@link SupportedCommand} by press key. */
  @Nullable
  public static SupportedCommand convertToCommand(Context context, boolean hasSpace, int pressDot) {
    return getSupportedCommands(context).stream()
        .filter(c -> c.hasSpace() == hasSpace && c.getPressDot().toInt() == pressDot)
        .findFirst()
        .orElse(null);
  }

  /**
   * Converts {@link BrailleInputEvent} to {@link SupportedCommand}. Returns null if {@link
   * BrailleInputEvent} is not supported.
   */
  @Nullable
  public static SupportedCommand convertToCommand(Context context, BrailleInputEvent event) {
    return getSupportedCommands(context).stream()
        .filter(c -> c.command == event.getCommand())
        .findFirst()
        .orElse(null);
  }

  /** Supported braille command. */
  public static class SupportedCommand {
    /** Category of command. */
    public enum Category {
      BASIC,
      NAVIGATION,
      SYSTEM_ACTIONS,
      TALKBACK_FEATURES,
      BRAILLE_SETTINGS,
      EDITING(R.string.bd_cmd_subcategory_editing_description);

      @StringRes private final int descriptionRes;

      Category() {
        this(ID_NULL);
      }

      Category(@StringRes int descriptionRes) {
        this.descriptionRes = descriptionRes;
      }

      /** Gets the description of the {@link Category}. */
      public String getDescription(Resources resources) {
        return descriptionRes == ID_NULL ? "" : resources.getString(descriptionRes);
      }
    }

    /** Subcategory of command. */
    public enum Subcategory {
      UNDEFINED(ID_NULL),
      BASIC(R.string.bd_cmd_subcategory_title_basic),
      WINDOW(R.string.bd_cmd_subcategory_title_window),
      PLACE_ON_PAGE(R.string.bd_cmd_subcategory_title_place_on_page),
      WEB_CONTENT(R.string.bd_cmd_subcategory_title_web_content),
      READING_CONTROLS(R.string.bd_cmd_subcategory_title_reading_controls),
      AUTO_SCROLL(R.string.bd_cmd_subcategory_title_auto_scroll),
      MOVE_CURSOR(R.string.bd_cmd_subcategory_title_move_cursor),
      SELECT(R.string.bd_cmd_subcategory_title_select),
      EDIT(R.string.bd_cmd_subcategory_title_edit),
      SWITCH_KEYBOARD(R.string.bd_cmd_subcategory_title_switch_keyboard);

      @StringRes private final int titleRes;

      Subcategory(@StringRes int titleRes) {
        this.titleRes = titleRes;
      }

      /** Gets the title of the {@link Subcategory}. */
      public String getTitle(Resources resources) {
        return titleRes == ID_NULL ? "" : resources.getString(titleRes);
      }
    }

    private final int command;
    @StringRes private final int commandDescriptionRes;
    private final Category category;
    private final Subcategory subcategory;
    private final KeyDescriptor keyDescriptor;

    private SupportedCommand(
        int command,
        @StringRes int commandDescriptionRes,
        Category category,
        KeyDescriptor keyDescriptor) {
      this(command, commandDescriptionRes, category, Subcategory.UNDEFINED, keyDescriptor);
    }

    private SupportedCommand(
        int command,
        @StringRes int commandDescriptionRes,
        Category category,
        Subcategory subcategory,
        KeyDescriptor keyDescriptor) {
      this.command = command;
      this.commandDescriptionRes = commandDescriptionRes;
      this.category = category;
      this.subcategory = subcategory;
      this.keyDescriptor = keyDescriptor;
    }

    /** Gets the corresponding braille input event command. */
    public int getCommand() {
      return command;
    }

    /** Gets the unified braille command key description. */
    public String getKeyDescription(Resources resources) {
      return keyDescriptor.getDescription(resources);
    }

    /** Gets the category of this braille command. */
    public Category getCategory() {
      return category;
    }

    /** Gets the subcategory of this braille command. */
    public Subcategory getSubcategory() {
      return subcategory;
    }

    /** Gets dots pressed for the command. */
    public BrailleCharacter getPressDot() {
      return keyDescriptor.dots();
    }

    /** Whether command includes space key. */
    public boolean hasSpace() {
      return keyDescriptor.space();
    }

    /** Gets the corresponding braille input event command description. */
    public String getCommandDescription(Resources resources) {
      return resources.getString(commandDescriptionRes);
    }

    /** The descriptor describe a braille command. */
    @AutoValue
    abstract static class KeyDescriptor {
      abstract boolean space();

      abstract BrailleCharacter dots();

      abstract boolean longPress();

      @StringRes
      abstract int keyNameRes();

      static Builder builder() {
        return new AutoValue_BrailleKeyBindingUtils_SupportedCommand_KeyDescriptor.Builder()
            .setSpace(false)
            .setDots(BrailleCharacter.EMPTY_CELL)
            .setLongPress(false)
            .setKeyNameRes(0);
      }

      private String getDescription(Resources resources) {
        String result = "";
        if (keyNameRes() != 0) {
          result = resources.getString(keyNameRes());
        } else {
          String tmp = "";
          if (space()) {
            tmp = resources.getString(R.string.bd_key_space);
          }
          if (!dots().equals(BrailleCharacter.EMPTY_CELL)) {
            String r = getDotsDescription(resources, dots());
            if (TextUtils.isEmpty(tmp)) {
              tmp = r;
            } else {
              tmp = resources.getString(R.string.bd_commands_delimiter, tmp, r);
            }
          }
          result = tmp;
        }
        return longPress()
            ? resources.getString(R.string.bd_commands_touch_and_hold_template, result)
            : resources.getString(R.string.bd_commands_press_template, result);
      }

      /** KeyDescriptor builder. */
      @AutoValue.Builder
      abstract static class Builder {
        abstract Builder setSpace(boolean space);

        abstract Builder setDots(BrailleCharacter brailleCharacter);

        abstract Builder setLongPress(boolean longPress);

        abstract Builder setKeyNameRes(@StringRes int keyNameRes);

        abstract KeyDescriptor build();
      }
    }
  }
}

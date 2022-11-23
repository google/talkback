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

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand.KeyDescriptor;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.brltty.BrailleKeyBinding;
import com.google.android.accessibility.braille.brltty.BrlttyUtils;
import com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils;
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
  private static final String COMMAND_DELIMITER = " + ";
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
    List<String> keyNames = Lists.newArrayList(binding.getKeyNames());
    BrailleCharacter brailleCharacter = BrlttyUtils.extractBrailleCharacter(context, keyNames);
    if (!brailleCharacter.isEmpty()) {
      keyNames.add(BrailleTranslateUtils.getDotsText(context.getResources(), brailleCharacter));
    }
    String keys =
        TextUtils.join(COMMAND_DELIMITER, getFriendlyKeyNames(keyNames, friendlyKeyNames));
    if (binding.isLongPress()) {
      keys = context.getString(R.string.bd_commands_touch_and_hold_template, keys);
    }
    return keys;
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

  /** Constructs and returns an immutable {@link SupportedCommand} list. */
  public static List<SupportedCommand> getSupportedCommands(Context context) {
    if (supportedCommands == null) {
      List<SupportedCommand> commands = new ArrayList<>();
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_PAN_DOWN,
              R.string.bd_cmd_nav_pan_down,
              KeyDescriptor.builder().setKeyNameRes(R.string.bd_key_pan_down).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_PAN_UP,
              R.string.bd_cmd_nav_pan_up,
              KeyDescriptor.builder().setKeyNameRes(R.string.bd_key_pan_up).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_ITEM_NEXT,
              R.string.bd_cmd_nav_item_next,
              KeyDescriptor.builder().setDots(new BrailleCharacter(4, 5, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_ITEM_PREVIOUS,
              R.string.bd_cmd_nav_item_previous,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_LINE_NEXT,
              R.string.bd_cmd_nav_line_next,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(4)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_LINE_PREVIOUS,
              R.string.bd_cmd_nav_line_previous,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(1)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_WORD_NEXT,
              R.string.bd_cmd_nav_word_next,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(5)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_WORD_PREVIOUS,
              R.string.bd_cmd_nav_word_previous,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(2)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_WINDOW_NEXT,
              R.string.bd_cmd_nav_window_next,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 4, 5, 6, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_WINDOW_PREVIOUS,
              R.string.bd_cmd_nav_window_previous,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 4, 5, 6, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_CHARACTER_NEXT,
              R.string.bd_cmd_nav_character_next,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(6)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_CHARACTER_PREVIOUS,
              R.string.bd_cmd_nav_character_previous,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(3)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent
                  .CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD,
              R.string.bd_cmd_move_by_reading_granularity_or_adjust_reading_control_forward,
              KeyDescriptor.builder().setDots(new BrailleCharacter(6, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent
                  .CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_BACKWARD,
              R.string.bd_cmd_move_by_reading_granularity_or_adjust_reading_control_backward,
              KeyDescriptor.builder().setDots(new BrailleCharacter(3, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_SCROLL_FORWARD,
              R.string.bd_cmd_scroll_forward,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 3, 5, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_SCROLL_BACKWARD,
              R.string.bd_cmd_scroll_backward,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 4, 6, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_TOP,
              R.string.bd_cmd_nav_top,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 2, 3))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NAV_BOTTOM,
              R.string.bd_cmd_nav_bottom,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(4, 5, 6))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_ROUTE,
              R.string.bd_cmd_route,
              KeyDescriptor.builder().setKeyNameRes(R.string.bd_key_route).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_LONG_PRESS_ROUTE,
              R.string.bd_cmd_touch_and_hold_route,
              KeyDescriptor.builder()
                  .setLongPress(true)
                  .setKeyNameRes(R.string.bd_key_route)
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_ACTIVATE_CURRENT,
              R.string.bd_cmd_activate_current,
              KeyDescriptor.builder().setKeyNameRes(R.string.bd_key_route).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_LONG_PRESS_CURRENT,
              R.string.bd_cmd_touch_and_hold_current,
              KeyDescriptor.builder()
                  .setLongPress(true)
                  .setKeyNameRes(R.string.bd_key_route)
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_GLOBAL_BACK,
              R.string.bd_cmd_global_back,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(1, 2)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_GLOBAL_HOME,
              R.string.bd_cmd_global_home,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 2, 5))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_GLOBAL_NOTIFICATIONS,
              R.string.bd_cmd_global_notifications,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 3, 4, 5))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_GLOBAL_RECENTS,
              R.string.bd_cmd_global_recents,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 2, 3, 5))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_QUICK_SETTINGS,
              R.string.bd_cmd_quick_settings,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 2, 3, 4, 5))
                  .build()));
      if (FeatureSupport.supportGetSystemActions(context)) {
        commands.add(
            new SupportedCommand(
                BrailleInputEvent.CMD_ALL_APPS,
                R.string.bd_cmd_global_all_apps,
                KeyDescriptor.builder()
                    .setSpace(true)
                    .setDots(new BrailleCharacter(1, 2, 3, 4))
                    .build()));
      }
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_KEY_ENTER,
              R.string.bd_cmd_key_enter,
              KeyDescriptor.builder().setDots(new BrailleCharacter(8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_KEY_DEL,
              R.string.bd_cmd_key_del,
              KeyDescriptor.builder().setDots(new BrailleCharacter(7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_DEL_WORD,
              R.string.bd_cmd_del_word,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_HEADING_NEXT,
              R.string.bd_cmd_heading_next,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 5, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_HEADING_PREVIOUS,
              R.string.bd_cmd_heading_previous,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 5, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_CONTROL_NEXT,
              R.string.bd_cmd_control_next,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 4, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_CONTROL_PREVIOUS,
              R.string.bd_cmd_control_previous,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 4, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_LINK_NEXT,
              R.string.bd_cmd_list_next,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 3, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_LINK_PREVIOUS,
              R.string.bd_cmd_list_previous,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 3, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_NEXT_READING_CONTROL,
              R.string.bd_cmd_next_reading_control,
              KeyDescriptor.builder().setDots(new BrailleCharacter(5, 6, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_PREVIOUS_READING_CONTROL,
              R.string.bd_cmd_previous_reading_control,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 3, 7)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_TOGGLE_SCREEN_SEARCH,
              R.string.bd_cmd_toggle_screen_search,
              KeyDescriptor.builder().setSpace(true).setDots(new BrailleCharacter(3, 4)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_EDIT_CUSTOM_LABEL,
              R.string.bd_cmd_edit_custom_label,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 3, 4, 8))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_OPEN_TALKBACK_MENU,
              R.string.bd_cmd_open_talkback_menu,
              KeyDescriptor.builder()
                  .setSpace(true)
                  .setDots(new BrailleCharacter(1, 3, 4))
                  .build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_SWITCH_TO_NEXT_INPUT_LANGUAGE,
              R.string.bd_cmd_switch_to_next_input_language,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 4, 7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_SWITCH_TO_NEXT_OUTPUT_LANGUAGE,
              R.string.bd_cmd_switch_to_next_output_language,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 3, 5, 7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_TOGGLE_VOICE_FEEDBACK,
              R.string.bd_cmd_toggle_voice_feedback,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 3, 4, 7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_HELP,
              R.string.bd_cmd_help,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 3, 7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_BRAILLE_DISPLAY_SETTINGS,
              R.string.bd_cmd_braille_display_settings,
              KeyDescriptor.builder().setDots(new BrailleCharacter(1, 2, 7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_TALKBACK_SETTINGS,
              R.string.bd_cmd_talkback_settings,
              KeyDescriptor.builder().setDots(new BrailleCharacter(2, 3, 4, 5, 7, 8)).build()));
      commands.add(
          new SupportedCommand(
              BrailleInputEvent.CMD_TURN_OFF_BRAILLE_DISPLAY,
              R.string.bd_cmd_turn_off_braille_display,
              KeyDescriptor.builder()
                  .setDots(new BrailleCharacter(1, 2, 3, 4, 5, 6, 7, 8))
                  .build()));
      supportedCommands = Collections.unmodifiableList(commands);
    }
    return supportedCommands;
  }

  /** Supported braille command. */
  public static class SupportedCommand {
    private final int command;
    @StringRes private final int commandDescriptionRes;
    private final KeyDescriptor keyDescriptor;

    private SupportedCommand(
        int command, @StringRes int commandDescriptionRes, KeyDescriptor keyDescriptor) {
      this.command = command;
      this.commandDescriptionRes = commandDescriptionRes;
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
        StringBuilder stringBuilder = new StringBuilder();
        if (keyNameRes() != 0) {
          stringBuilder.append(resources.getString(keyNameRes()));
        } else {
          if (space()) {
            stringBuilder.append(resources.getString(R.string.bd_key_space));
          }
          if (!dots().equals(BrailleCharacter.EMPTY_CELL)) {
            if (stringBuilder.length() > 0) {
              stringBuilder.append(COMMAND_DELIMITER);
            }
            stringBuilder.append(BrailleTranslateUtils.getDotsText(resources, dots()));
          }
        }
        return longPress()
            ? resources.getString(
                R.string.bd_commands_touch_and_hold_template, stringBuilder.toString())
            : stringBuilder.toString();
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

/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brltty;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * An input event, originating from a braille display.
 *
 * <p>An event contains a command that is a high-level representation of the key or key combination
 * that was pressed on the display such as a navigation key or braille keyboard combination. For
 * some commands, there is also an integer argument that contains additional information.
 *
 * <p>Members of this class are accessed by native brltty code.
 */
public class BrailleInputEvent implements Parcelable {

  // Movement commands.

  /** Keyboard command: Used when there is no actual command. */
  public static final int CMD_NONE = -1;

  /** Keyboard command: Navigate upwards. */
  public static final int CMD_NAV_LINE_PREVIOUS = 1;
  /** Keyboard command: Navigate downwards. */
  public static final int CMD_NAV_LINE_NEXT = 2;
  /** Keyboard command: Navigate left one item. */
  public static final int CMD_NAV_ITEM_PREVIOUS = 3;
  /** Keyboard command: Navigate right one item. */
  public static final int CMD_NAV_ITEM_NEXT = 4;
  /** Keyboard command: Navigate one display window to the up. */
  public static final int CMD_NAV_PAN_UP = 5;
  /** Keyboard command: Navigate one display window to the down. */
  public static final int CMD_NAV_PAN_DOWN = 6;
  /** Keyboard command: Navigate to the top or beginning. */
  public static final int CMD_NAV_TOP = 7;
  /** Keyboard command: Navigate to the bottom or end. */
  public static final int CMD_NAV_BOTTOM = 8;
  /** Keyboard command: Navigate previous character. */
  public static final int CMD_CHARACTER_PREVIOUS = 9;
  /** Keyboard command: Navigate next character. */
  public static final int CMD_CHARACTER_NEXT = 10;
  /** Keyboard command: Navigate previous word. */
  public static final int CMD_WORD_PREVIOUS = 11;
  /** Keyboard command: Navigate next word. */
  public static final int CMD_WORD_NEXT = 12;
  /** Keyboard command: Navigate next window. */
  public static final int CMD_WINDOW_PREVIOUS = 13;
  /** Keyboard command: Navigate previous window. */
  public static final int CMD_WINDOW_NEXT = 14;
  /** Keyboard command: Navigate backward by reading granularity or adjust reading control down. */
  public static final int CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_BACKWARD =
      15;
  /** Keyboard command: Navigate forward by reading granularity or adjust reading control up. */
  public static final int CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD =
      16;

  // Activation commands.

  /** Keyboard command: Activate the currently selected/focused item. */
  public static final int CMD_ACTIVATE_CURRENT = 20;
  /** Keyboard command: Long press the currently selected/focused item. */
  public static final int CMD_LONG_PRESS_CURRENT = 21;

  // Scrolling.

  /** Keyboard command: Scroll backward. */
  public static final int CMD_SCROLL_BACKWARD = 30;
  /** Keyboard command: Scroll forward. */
  public static final int CMD_SCROLL_FORWARD = 31;

  // Selection commands.

  /** Keyboard command: Set the start ot the selection. */
  public static final int CMD_SELECTION_START = 40;
  /** Keyboard command: Set the end of the selection. */
  public static final int CMD_SELECTION_END = 41;
  /** Keyboard command: Select all content of the current field. */
  public static final int CMD_SELECTION_SELECT_ALL = 42;
  /** Keyboard command: Cut the content of the selection. */
  public static final int CMD_SELECTION_CUT = 43;
  /** Keyboard command: Copy the current selection. */
  public static final int CMD_SELECTION_COPY = 44;
  /** Keyboard command: Paste the content of the clipboard at the current insertion point. */
  public static final int CMD_SELECTION_PASTE = 45;

  /**
   * Keyboard command: Primary routing key pressed, typically used to move the insertion point or
   * click/tap on the item under the key. The argument is the zero-based position, relative to the
   * first cell on the display, of the cell that is closed to the key that was pressed.
   */
  public static final int CMD_ROUTE = 50;
  /**
   * Keyboard command: Primary routing key long pressed, typically used to long press the item under
   * the key. The argument is the zero-based position, relative to the first cell on the display, of
   * the cell that is closed to the key that was pressed.
   */
  public static final int CMD_LONG_PRESS_ROUTE = 51;

  // Braille keyboard input.

  /**
   * Keyboard command: A key combination was pressed on the braille keyboard. The argument contains
   * the dots that were pressed as a bitmask.
   */
  public static final int CMD_BRAILLE_KEY = 60;

  // Editing keys.

  /** Keyboard command: Enter key. */
  public static final int CMD_KEY_ENTER = 70;
  /** Keyboard command: Delete backward. */
  public static final int CMD_KEY_DEL = 71;
  /** Keyboard command: Delete a word backward. */
  public static final int CMD_DEL_WORD = 72;
  /** Keyboard command: Select previous character. */
  public static final int CMD_SELECT_PREVIOUS_CHARACTER = 77;
  /** Keyboard command: Select next character. */
  public static final int CMD_SELECT_NEXT_CHARACTER = 78;
  /** Keyboard command: Select previous word. */
  public static final int CMD_SELECT_PREVIOUS_WORD = 79;
  /** Keyboard command: Select next word. */
  public static final int CMD_SELECT_NEXT_WORD = 80;
  /** Keyboard command: Select previous line. */
  public static final int CMD_SELECT_PREVIOUS_LINE = 81;
  /** Keyboard command: Select next line. */
  public static final int CMD_SELECT_NEXT_LINE = 82;

  // Global navigation keys.

  /** Keyboard command: Back button. */
  public static final int CMD_GLOBAL_BACK = 90;
  /** Keyboard command: Home button. */
  public static final int CMD_GLOBAL_HOME = 91;
  /** Keyboard command: Recent apps button. */
  public static final int CMD_GLOBAL_RECENTS = 92;
  /** Keyboard command: Show notifications. */
  public static final int CMD_GLOBAL_NOTIFICATIONS = 93;
  /** Keyboard command: Quick Settings. */
  public static final int CMD_QUICK_SETTINGS = 94;
  /** Keyboard command: All apps. */
  public static final int CMD_ALL_APPS = 95;

  /** Keyboard command: Play or pause media. */
  public static final int CMD_PLAY_PAUSE_MEDIA = 134;

  // Miscellaneous commands.

  /** Keyboard command: Invoke keyboard help. */
  public static final int CMD_HELP = 100;

  /** Keyboard command: Edit custom label. */
  public static final int CMD_EDIT_CUSTOM_LABEL = 117;

  /** Keyboard command: Open TalkBack menu. */
  public static final int CMD_OPEN_TALKBACK_MENU = 118;

  /** Keyboard command: Switch to next input language. */
  public static final int CMD_SWITCH_TO_NEXT_INPUT_LANGUAGE = 119;

  /** Keyboard command: Switch to next output language. */
  public static final int CMD_SWITCH_TO_NEXT_OUTPUT_LANGUAGE = 120;

  /** Keyboard command: Navigate Braille display settings. */
  public static final int CMD_BRAILLE_DISPLAY_SETTINGS = 121;

  /** Keyboard command: Navigate TalkBack settings. */
  public static final int CMD_TALKBACK_SETTINGS = 122;

  /** Keyboard command: Turn off braille display. */
  public static final int CMD_TURN_OFF_BRAILLE_DISPLAY = 123;

  /** Keyboard command: Mute/Unmute voice feedback. */
  public static final int CMD_TOGGLE_VOICE_FEEDBACK = 124;

  /** Keyboard command: Switch to previous reading control. */
  public static final int CMD_PREVIOUS_READING_CONTROL = 125;

  /** Keyboard command: Switch to next reading control. */
  public static final int CMD_NEXT_READING_CONTROL = 126;

  /** Keyboard command: Toggle braille grade. */
  public static final int CMD_TOGGLE_BRAILLE_GRADE = 127;

  /** Keyboard command: In editing text, navigate to the top. In common state, activate key. */
  public static final int CMD_NAV_TOP_OR_KEY_ACTIVATE = 128;

  /** Keyboard command: In editing text, navigate to the bottom. In common state, activate key. */
  public static final int CMD_NAV_BOTTOM_OR_KEY_ACTIVATE = 129;

  /** Keyboard command: Stop reading. */
  public static final int CMD_STOP_READING = 130;

  /** Keyboard command: Start or stop auto scroll. */
  public static final int CMD_TOGGLE_AUTO_SCROLL = 131;

  /** Keyboard command: Increase auto scroll duration. */
  public static final int CMD_INCREASE_AUTO_SCROLL_DURATION = 132;

  /** Keyboard command: Decrease auto scroll duration. */
  public static final int CMD_DECREASE_AUTO_SCROLL_DURATION = 133;

  // Web content commands.

  /** Keyboard command: Next heading in page. */
  public static final int CMD_HEADING_NEXT = 110;
  /** Keyboard command: Previous heading in page. */
  public static final int CMD_HEADING_PREVIOUS = 111;
  /** Keyboard command: Next control in page. */
  public static final int CMD_CONTROL_NEXT = 112;
  /** Keyboard command: Previous control in page. */
  public static final int CMD_CONTROL_PREVIOUS = 113;
  /** Keyboard command: Next link in page. */
  public static final int CMD_LINK_NEXT = 114;
  /** Keyboard command: Previous link in page. */
  public static final int CMD_LINK_PREVIOUS = 115;
  /** Keyboard command: Toggle Screen search. */
  public static final int CMD_TOGGLE_SCREEN_SEARCH = 116;

  // Meanings of the argument to a command.

  /** This command doesn't have an argument. */
  public static final int ARGUMENT_NONE = 0;
  /**
   * The lower order bits of the arguemnt to this command represent braille dots. Dot 1 is
   * represented by the rightmost bit and so on until dot 8, which is represented by bit 7, counted
   * from the right.
   */
  public static final int ARGUMENT_DOTS = 1;
  /** The argument represents a 0-based position on the display counted from the leftmost cell. */
  public static final int ARGUMENT_POSITION = 2;

  private final int command;
  private final int argument;
  private final long eventTime;

  public BrailleInputEvent(int command, int argument, long eventTime) {
    this.command = command;
    this.argument = argument;
    this.eventTime = eventTime;
  }

  /** Returns the keyboard command that this event represents. */
  public int getCommand() {
    return command;
  }

  /**
   * Returns the command-specific argument of the event, or zero if the command doesn't have an
   * argument. See the individual command constants for more details.
   */
  public int getArgument() {
    return argument;
  }

  /**
   * Returns the approximate time when this event happened as returned by {@link
   * android.os.SystemClock#uptimeMillis}.
   */
  public long getEventTime() {
    return eventTime;
  }

  /** Returns the type of argument for the given {@code command}. */
  public static int argumentType(int command) {
    switch (command) {
      case CMD_SELECTION_START:
      case CMD_SELECTION_END:
      case CMD_ROUTE:
      case CMD_LONG_PRESS_ROUTE:
        return ARGUMENT_POSITION;
      case CMD_BRAILLE_KEY:
        return ARGUMENT_DOTS;
      default:
        return ARGUMENT_NONE;
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("BrailleInputEvent {");
    sb.append("cmd=");
    sb.append(command);
    sb.append(", arg=");
    sb.append(argument);
    sb.append("}");
    return sb.toString();
  }

  // For Parcelable support.

  public static final Creator<BrailleInputEvent> CREATOR =
      new Creator<BrailleInputEvent>() {
        @Override
        public BrailleInputEvent createFromParcel(Parcel in) {
          return new BrailleInputEvent(in);
        }

        @Override
        public BrailleInputEvent[] newArray(int size) {
          return new BrailleInputEvent[size];
        }
      };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeInt(command);
    out.writeInt(argument);
    out.writeLong(eventTime);
  }

  private BrailleInputEvent(Parcel in) {
    command = in.readInt();
    argument = in.readInt();
    eventTime = in.readLong();
  }
}

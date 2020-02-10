package com.google.android.accessibility.switchaccess.proto;

/** Defines an enum of possible types of screens in the setup guide. */
public class SwitchAccessSetupScreenEnum {

  /** Possible types of screens in the setup guide. */
  public enum SetupScreen {
    SCREEN_UNDEFINED,
    SWITCH_TYPE_SCREEN,
    USB_DEVICE_LIST_SCREEN,
    PAIR_BLUETOOTH_SCREEN,
    NUMBER_OF_SWITCHES_SCREEN,
    ONE_SWITCH_OPTION_SCREEN,
    TWO_SWITCH_OPTION_SCREEN,
    AUTO_SCAN_KEY_SCREEN,
    STEP_SPEED_SCREEN,
    NEXT_KEY_SCREEN,
    SELECT_KEY_SCREEN,
    GROUP_ONE_KEY_SCREEN,
    GROUP_TWO_KEY_SCREEN,
    SWITCH_GAME_VALID_CONFIGURATION_SCREEN,
    SWITCH_GAME_INVALID_CONFIGURATION_SCREEN,
    COMPLETION_SCREEN,
    EXIT_SETUP,
    VIEW_NOT_CREATED
  }
}

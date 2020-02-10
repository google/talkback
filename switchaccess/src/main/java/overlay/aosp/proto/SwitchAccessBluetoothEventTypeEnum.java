package com.google.android.accessibility.switchaccess.proto;

/** Defines an enum of the actions user can execute during bluetooth setup. */
public class SwitchAccessBluetoothEventTypeEnum {

  /** The various actions the user can execute during bluetooth setup. */
  public enum BluetoothEventType {
    EVENT_TYPE_UNDEFINED,
    PAIR_BLUETOOTH_SWITCH_FAILED,
    PAIR_BLUETOOTH_SWITCH_SUCCESS,
    RECONNECT_PREVIOUSLY_PAIRED_DEVICE_FAILED,
    RECONNECT_PREVIOUSLY_PAIRED_DEVICE_SUCCESS,
    LAUNCHED_BLUETOOTH_SETTINGS
  }
}

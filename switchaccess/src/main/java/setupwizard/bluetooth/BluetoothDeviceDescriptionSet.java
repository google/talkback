/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.switchaccess.setupwizard.bluetooth;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;

/**
 * Holds the description strings associated with different types of Bluetooth devices. Use of this
 * class allows applications to set the strings themselves, while allowing the library to enforce
 * that none of the description strings are null.
 */
@AutoValue
public abstract class BluetoothDeviceDescriptionSet {
  /* Content descriptions for different {@link BluetoothClass} types. */
  abstract String computerContentDescription();

  abstract String phoneContentDescription();

  abstract String peripheralContentDescription();

  abstract String imagingContentDescription();

  abstract String headphoneContentDescription();

  abstract String defaultBluetoothDeviceContentDescription();

  /* Connection state descriptions for a Bluetooth device. */
  abstract String connectedDescription();

  abstract String connectingDescription();

  abstract String unavailableDeviceDescription();

  /* Text to appear on a dialog when attempting to reconnect to a previously paired device is
   * unsuccessful. */
  abstract String reconnectingUnsupportedTitle();

  abstract String reconnectingUnsupportedMessage();

  abstract String launchBluetoothSettingsButtonText();

  /** @return Builder for {@link BluetoothDeviceDescriptionSet} */
  public static Builder builder() {
    return new AutoValue_BluetoothDeviceDescriptionSet.Builder();
  }

  /** Builder for {@link BluetoothDeviceDescriptionSet} that sets string descriptions. */
  @AutoValue.Builder
  public abstract static class Builder {

    /** Sets the content description for a computer Bluetooth device. */
    public abstract Builder setComputerContentDescription(String value);

    /** Sets the content description for a phone Bluetooth device. */
    public abstract Builder setPhoneContentDescription(String value);

    /** Sets teh content description for a peripheral Bluetooth device. */
    public abstract Builder setPeripheralContentDescription(String value);

    /** Sets the content description for an imaging Bluetooth device. */
    public abstract Builder setImagingContentDescription(String value);

    /** Sets the content description for a headphone Bluetooth device. */
    public abstract Builder setHeadphoneContentDescription(String value);

    /** Sets the default content description for Bluetooth devices. */
    public abstract Builder setDefaultBluetoothDeviceContentDescription(String value);

    /** Sets the message to display when a Bluetooth device is successfully connected. */
    public abstract Builder setConnectedDescription(String value);

    /** Sets the message to display while attempting to connect a Bluetooth device. */
    public abstract Builder setConnectingDescription(String value);

    /**
     * Sets the title of a dialog displayed when attempting to reconnect a Bluetooth device is
     * unsuccessful.
     */
    public abstract Builder setReconnectingUnsupportedTitle(String value);

    /**
     * Sets the message to display when attempting to reconnect a Bluetooth device is unsuccessful.
     */
    public abstract Builder setReconnectingUnsupportedMessage(String value);

    /** Sets the text of the button that will launch Bluetooth Settings. */
    public abstract Builder setLaunchBluetoothSettingsButtonText(String value);

    /**
     * Sets the message to display when connecting or pairing a Bluetooth device is unsuccessful.
     */
    public abstract Builder setUnavailableDeviceDescription(String value);

    abstract BluetoothDeviceDescriptionSet autoBuild();

    /** Builds a {@link BluetoothDeviceDescriptionSet}. */
    public BluetoothDeviceDescriptionSet build() {
      BluetoothDeviceDescriptionSet descriptionSet = autoBuild();

      // Check that all strings used are not empty.
      Preconditions.checkState(!descriptionSet.computerContentDescription().isEmpty());
      Preconditions.checkState(!descriptionSet.phoneContentDescription().isEmpty());
      Preconditions.checkState(!descriptionSet.peripheralContentDescription().isEmpty());
      Preconditions.checkState(!descriptionSet.imagingContentDescription().isEmpty());
      Preconditions.checkState(!descriptionSet.headphoneContentDescription().isEmpty());
      Preconditions.checkState(
          !descriptionSet.defaultBluetoothDeviceContentDescription().isEmpty());
      Preconditions.checkState(!descriptionSet.connectedDescription().isEmpty());
      Preconditions.checkState(!descriptionSet.connectingDescription().isEmpty());
      Preconditions.checkState(!descriptionSet.unavailableDeviceDescription().isEmpty());
      Preconditions.checkState(!descriptionSet.reconnectingUnsupportedTitle().isEmpty());
      Preconditions.checkState(!descriptionSet.reconnectingUnsupportedMessage().isEmpty());
      Preconditions.checkState(!descriptionSet.launchBluetoothSettingsButtonText().isEmpty());
      return descriptionSet;
    }
  }
}

/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.braille.brltty;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Properties retrieved from the remote braille display. */
public class BrailleDisplayProperties implements Parcelable {
  private final String deviceName;
  private final int numTextCells;
  private final int numStatusCells;
  private final BrailleKeyBinding[] keyBindings;
  private final Map<String, String> friendlyKeyNames;

  public BrailleDisplayProperties(
      String name,
      int numTextCells,
      int numStatusCells,
      BrailleKeyBinding[] keyBindings,
      Map<String, String> friendlyKeyNames) {
    this.numTextCells = numTextCells;
    this.numStatusCells = numStatusCells;
    this.keyBindings = keyBindings;
    this.friendlyKeyNames = friendlyKeyNames;
    deviceName = name;
  }

  private BrailleDisplayProperties(Parcel in) {
    deviceName = in.readString();
    numTextCells = in.readInt();
    numStatusCells = in.readInt();
    keyBindings = in.createTypedArray(BrailleKeyBinding.CREATOR);
    int size = in.readInt();
    Map<String, String> names = new HashMap<>();
    for (int i = 0; i < size; ++i) {
      names.put(in.readString(), in.readString());
    }
    friendlyKeyNames = Collections.unmodifiableMap(names);
  }

  /**
   * Returns the number of cells on the main display intended for display of text or other content.
   */
  public int getNumTextCells() {
    return numTextCells;
  }

  /**
   * Returns the number of status cells that are separated from the main display. This value will be
   * {@code 0} for displays without any separate status cells.
   */
  public int getNumStatusCells() {
    return numStatusCells;
  }

  /** Returns the list of key bindings for this display. */
  public BrailleKeyBinding[] getKeyBindings() {
    return keyBindings;
  }

  /**
   * Returns an unmodifiable map mapping key names in {@link BrailleKeyBinding} objects to localized
   * user-friendly key names.
   */
  public Map<String, String> getFriendlyKeyNames() {
    return friendlyKeyNames;
  }

  /** Returns the name of the display. */
  public String getDeviceName() {
    return deviceName;
  }

  @Override
  public String toString() {
    return String.format(
        "BrailleDisplayProperties [numTextCells: %d, numStatusCells: %d, "
            + "keyBindings: %d], deviceName: %s",
        numTextCells, numStatusCells, keyBindings.length, deviceName);
  }

  // For Parcelable support.

  public static final Creator<BrailleDisplayProperties> CREATOR =
      new Creator<BrailleDisplayProperties>() {
        @Override
        public BrailleDisplayProperties createFromParcel(Parcel in) {
          return new BrailleDisplayProperties(in);
        }

        @Override
        public BrailleDisplayProperties[] newArray(int size) {
          return new BrailleDisplayProperties[size];
        }
      };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeString(deviceName);
    out.writeInt(numTextCells);
    out.writeInt(numStatusCells);
    out.writeTypedArray(keyBindings, flags);
    out.writeInt(friendlyKeyNames.size());
    for (Map.Entry<String, String> entry : friendlyKeyNames.entrySet()) {
      out.writeString(entry.getKey());
      out.writeString(entry.getValue());
    }
  }
}

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
import com.google.android.apps.common.proguard.UsedByNative;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Represents a binding between a combination of braille device keys and a command as declared in
 * {@link BrailleInputEvent}.
 *
 * <p>Members of this class are accessed by native brltty code.
 */
public class BrailleKeyBinding implements Parcelable {
  private static final int FLAG_LONG_PRESS = 0x00000001;

  private int command;
  private String[] keyNames;
  private int flags;
  private boolean unifiedKeyBinding;

  public BrailleKeyBinding() {}

  @UsedByNative("BrlttyWrapper.c")
  public BrailleKeyBinding(
      int command, String[] keyNames, boolean longPress, boolean unifiedKeyBinding) {
    this.command = command;
    this.keyNames = keyNames;
    flags = longPress ? FLAG_LONG_PRESS : 0;
    this.unifiedKeyBinding = unifiedKeyBinding;
  }

  private BrailleKeyBinding(Parcel in) {
    command = in.readInt();
    keyNames = in.createStringArray();
    flags = in.readInt();
    unifiedKeyBinding = in.readInt() == 1;
  }

  /** Sets the command for this binding. */
  @CanIgnoreReturnValue
  public BrailleKeyBinding setCommand(int command) {
    this.command = command;
    return this;
  }

  /** Sets the key names for this binding. */
  @CanIgnoreReturnValue
  public BrailleKeyBinding setKeyNames(String[] keyNames) {
    this.keyNames = keyNames;
    return this;
  }

  /** Sets whether this is a long press key binding. */
  @CanIgnoreReturnValue
  public BrailleKeyBinding setLongPress(boolean longPress) {
    flags = (flags & ~FLAG_LONG_PRESS) | (longPress ? FLAG_LONG_PRESS : 0);
    return this;
  }

  /**
   * Returns the command for this key binding.
   *
   * @see {@link BrailleInputEvent}.
   */
  public int getCommand() {
    return command;
  }

  /**
   * Returns the list of device-specific keys that, when pressed at the same time, will yield the
   * command of this key binding.
   */
  public String[] getKeyNames() {
    return keyNames;
  }

  /** Returns whether this is a long press key binding. */
  public boolean isLongPress() {
    return (flags & FLAG_LONG_PRESS) != 0;
  }

  /**
   * Whether this {@link BrailleKeyBinding} is unified command used by all braille hardware devices.
   */
  public boolean isUnifiedKeyBinding() {
    return unifiedKeyBinding;
  }

  // For Parcelable support.

  public static final Creator<BrailleKeyBinding> CREATOR =
      new Creator<BrailleKeyBinding>() {
        @Override
        public BrailleKeyBinding createFromParcel(Parcel in) {
          return new BrailleKeyBinding(in);
        }

        @Override
        public BrailleKeyBinding[] newArray(int size) {
          return new BrailleKeyBinding[size];
        }
      };

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel out, int flags) {
    out.writeInt(command);
    out.writeStringArray(keyNames);
    out.writeInt(this.flags);
    out.writeInt(unifiedKeyBinding ? 1 : 0);
  }
}

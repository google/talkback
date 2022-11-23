/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2019 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.app/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

//package org.a11y.BRLTTY;

public class brltty {
  public enum ProgramExitStatus {
    SUCCESS  (0),
    FORCE    (1),
    SYNTAX   (2),
    SEMANTIC (3),
    FATAL    (4);

    public final int value;

    ProgramExitStatus (int value) {
      this.value = value;
    }
  }

  public static native int construct (String[] arguments);
  public static native boolean update ();
  public static native void destruct ();

  public static void main (String[] arguments) {
    int exitStatus = construct(arguments);

    if (exitStatus == ProgramExitStatus.SUCCESS.value) {
      while (update()) {
      }
    } else if (exitStatus == ProgramExitStatus.FORCE.value) {
      exitStatus = ProgramExitStatus.SUCCESS.value;
    }

    destruct();
    System.exit(exitStatus);
  }

  static {
    System.loadLibrary("brltty_jni");
  }
}

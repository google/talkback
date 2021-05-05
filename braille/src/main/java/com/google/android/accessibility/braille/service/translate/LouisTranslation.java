/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.braille.service.translate;

import android.util.Log;

/**
 * Wraps the liblouis functions to translate to and from braille.
 *
 * <p>NOTE: Braille translation involves reading tables from disk and can therefore be blocking. In
 * addition, translation by all instances of this class is serialized because of the underlying
 * implementation, which increases the possibility of translations blocking on I/O if multiple
 * translators are used.
 */
public class LouisTranslation {
  private static final String LOG_TAG = LouisTranslation.class.getSimpleName();

  /**
   * Translation mode passed to liblouis.
   * REFERTO
   */
  public static class TranslationMode {

    /**
     * If this bit is set, during forward translation, Liblouis will produce output as dot patterns.
     * During back-translation Liblouis accepts input as dot patterns.
     */
    public static final int DOTS_IO = 1 << 2;

    /**
     * The default is to output the hexadecimal value (as ’\xhhhh’) of an undefined character when
     * forward-translating and the dot numbers (as \ddd/) of an undefined braille pattern when
     * back-translating. If this bit is set, it disables the output of hexadecimal values or dot
     * numbers when braille patterns that are not matched by any rule).
     */
    public static final int NO_UNDEFINED_DOTS = 1 << 7;
    /** If this bit is set, back-translation input should be treated as an incomplete word. */
    public static final int PARTIAL_TRANSLATE = 1 << 8;

    private TranslationMode() {}
  }

  /**
   * This method should be called before any other method is called. {@code path} should point to a
   * location in the file system under which the liblouis translation tables can be found.
   */
  public static boolean setTablesDir(String path) {
    return setTablesDirNative(path);
  }

  /** Compiles the given table and makes sure it is valid. */
  public static boolean checkTable(String tableName) {
    if (!checkTableNative(tableName)) {
      Log.w(LOG_TAG, "Table not found or invalid: " + tableName);
      return false;
    }
    return true;
  }

  /** Translates a string into the corresponding dot patterns. */
  public static TranslationResult translate(
      String text, String tableName, int cursorPosition, boolean computerBrailleAtCursor) {
    return translateNative(text, tableName, cursorPosition, computerBrailleAtCursor);
  }

  /** Back-translates a byte array of dot patterns into the corresponding String. */
  public static String backTranslate(byte[] cells, String tableName, int mode) {
    return backTranslateNative(cells, tableName, mode);
  }

  // Native methods.

  private static native TranslationResult translateNative(
      String text, String tableName, int cursorPosition, boolean computerBrailleAtCursor);

  private static native String backTranslateNative(byte[] dotPatterns, String tableName, int mode);

  private static native boolean checkTableNative(String tableName);

  private static native boolean setTablesDirNative(String path);

  private static native void classInitNative();

  static {
    System.loadLibrary("louiswrap");
    classInitNative();
  }

  private LouisTranslation() {}
}

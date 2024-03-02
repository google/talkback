/*
 * Copyright 2020 Google Inc.
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

package com.google.android.accessibility.braille.translate.liblouis;

import static java.util.stream.Collectors.joining;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.R;
import com.google.android.accessibility.braille.translate.TranslationResult;
import com.google.android.accessibility.braille.translate.liblouis.LouisTranslation.TranslationMode;
import com.google.android.accessibility.utils.BuildVersionUtils;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A LibLouis translator based on the {@link LibLouisTranslator#tableName}. */
public class LibLouisTranslator implements BrailleTranslator {

  private final String tableName;
  private final Map<BrailleWord, String> bypassMap;
  private final Map<BrailleCharacter, String> commutativityMap;

  public LibLouisTranslator(Context context, String tableName) {
    this.tableName = tableName;
    File tablesDir;
    // Extract tables to device storage so we can read tables before device is unlocked after
    // reboot.
    if (BuildVersionUtils.isAtLeastN()) {
      context = ContextCompat.createDeviceProtectedStorageContext(context);
    }
    File customTablesDir = context.getExternalFilesDir(/* type= */ null);
    File customTablesSubDir = new File(customTablesDir, "/liblouis/tables");
    File[] files = customTablesSubDir.listFiles();
    if (files != null && files.length != 0) {
      tablesDir = customTablesDir;
    } else {
      tablesDir = context.getDir("translator", Context.MODE_PRIVATE);
      TranslateUtils.extractTables(context.getResources(), R.raw.translationtables, tablesDir);
    }
    LouisTranslation.setTablesDir(tablesDir.getPath());
    bypassMap = new LinkedHashMap<>();
    commutativityMap = new LinkedHashMap<>();
  }

  /**
   * Returns a map that allows the backing translator to be bypassed, in case the {@link
   * BrailleWord} is present as a key in the map, in which case the corresponding String value is
   * used as the translation result.
   */
  protected Map<BrailleWord, String> getBypassMap() {
    return bypassMap;
  }

  /**
   * Returns a map that allows the backing translator's translation of certain {@link
   * BrailleCharacter} to be overridden.
   *
   * <p>For example, for Spanish, LibLouis wrongly translates "26" as the number 5, when it actually
   * should be translated as a question mark. To fix this mis-translation, fill the commutativity
   * map with the key-value pair ("26", "?").
   *
   * <p>Note that overriding the translation in this manner can only occur when the concatenation
   * and translation operators commute for the given input phrase.
   */
  protected Map<BrailleCharacter, String> getCommutativityMap() {
    return commutativityMap;
  }

  /** Modifies the result from the backing translator, post-translation. */
  protected String transformPostTranslation(String translation) {
    return translation;
  }

  @Override
  public String translateToPrint(BrailleWord brailleWord) {
    return translateToPrintInternal(brailleWord, false);
  }

  @Override
  public String translateToPrintPartial(BrailleWord brailleWord) {
    return translateToPrintInternal(brailleWord, true);
  }

  private String translateToPrintInternal(BrailleWord brailleWord, boolean partial) {
    String bypassValueOrNull = getBypassMap().get(brailleWord);
    if (bypassValueOrNull != null) {
      return bypassValueOrNull;
    }

    String translation = translateToPrintDirect(brailleWord, partial);
    translation = transformUsingCommutativityMap(brailleWord, translation);
    translation = transformPostTranslation(translation);
    return translation;
  }

  private String translateToPrintDirect(BrailleWord brailleWord, boolean partial) {
    int flags =
        LouisTranslation.TranslationMode.NO_UNDEFINED_DOTS
            | LouisTranslation.TranslationMode.DOTS_IO;
    if (partial) {
      flags |= TranslationMode.PARTIAL_TRANSLATE;
    }
    return LouisTranslation.backTranslate(brailleWord.toByteArray(), tableName, flags);
  }

  private String transformUsingCommutativityMap(BrailleWord brailleWord, String translationRaw) {
    if (!brailleWord.containsAny(commutativityMap.keySet())) {
      return translationRaw;
    }
    List<BrailleWord> tokens = brailleWord.tokenize(commutativityMap.keySet());
    String translateThenConcatenate =
        tokens.stream()
            .map(brailleWord1 -> translateToPrintDirect(brailleWord1, false))
            .collect(joining());
    if (!translationRaw.equals(translateThenConcatenate)) {
      return translationRaw;
    }
    // Hooray - the translate and concatenate operations commute.  That means that we can
    // translate token-by-token, which allows keys in the commutativityMap to be translated
    // to their corresponding commutativityMap values.
    StringBuilder translationSB = new StringBuilder();
    for (BrailleWord token : tokens) {
      String tokenTranslation;
      if (token.size() == 1 && commutativityMap.containsKey(token.get(0))) {
        tokenTranslation = commutativityMap.get(token.get(0));
      } else {
        tokenTranslation = translateToPrintDirect(token, false);
      }
      translationSB.append(tokenTranslation);
    }
    return translationSB.toString();
  }

  @Override
  public TranslationResult translate(CharSequence text, int cursorPosition) {
    return LouisTranslation.translate(text, tableName, cursorPosition);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LibLouisTranslator)) {
      return false;
    }
    return tableName.equals(((LibLouisTranslator) o).tableName);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(tableName);
  }
}

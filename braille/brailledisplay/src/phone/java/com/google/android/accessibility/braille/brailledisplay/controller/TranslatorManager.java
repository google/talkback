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

package com.google.android.accessibility.braille.brailledisplay.controller;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.TranslatorFactory;
import java.util.ArrayList;
import java.util.List;

/**
 * Controls the translators used in the braille display.
 *
 * <p>Threading: This class is not thread safe, except {@link
 * TranslatorManager#getOutputTranslator()} may be called from any thread. Note that the result from
 * calling those functions may not be consistent while the selected tables change. This shouldn't be
 * a problem if a caller uses the callback API to re-translate the display content on configuration
 * changes.
 */
public class TranslatorManager implements SharedPreferences.OnSharedPreferenceChangeListener {
  private static final String TAG = "TranslatorManager";
  private final Context context;
  private final SharedPreferences sharedPreferences;
  private volatile BrailleTranslator outputTranslator;
  private volatile BrailleTranslator inputTranslator;
  private final List<OutputCodeChangedListener> outputCodeChangedListeners = new ArrayList<>();
  private final List<InputCodeChangedListener> inputCodeChangedListeners = new ArrayList<>();

  /** Callback interface to be invoked when output code has changed. */
  public interface OutputCodeChangedListener {
    /**
     * Called when output code has changed, including when the translation service is initialized so
     * that code is available.
     */
    void onOutputCodeChanged();
  }

  /** Callback interface to be invoked when input code has changed. */
  public interface InputCodeChangedListener {
    /**
     * Called when output code has changed, including when the translation service is initialized so
     * that code is available.
     */
    void onInputCodeChanged();
  }

  /**
   * Constructs an instance, creating a connection to the translation service and setting up this
   * instance to provide tables for the current user configuration.
   */
  public TranslatorManager(final Context context) {
    this.context = context;
    sharedPreferences =
        BrailleUserPreferences.getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME);
    sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    updateInputTranslator();
    updateOutputTranslators();
  }

  /**
   * Frees resources used by this instance, most notably the connection to the translation service.
   */
  public void shutdown() {
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    outputTranslator = null;
    inputTranslator = null;
  }

  /** Adds a listener to be called when output translation tables have changed. */
  public void addOnOutputTablesChangedListener(OutputCodeChangedListener listener) {
    outputCodeChangedListeners.add(listener);
  }

  /** Adds a listener to be called when inpput translation tables have changed. */
  public void addOnInputTablesChangedListener(InputCodeChangedListener listener) {
    inputCodeChangedListeners.add(listener);
  }

  /** Removes an output table change listener. */
  public void removeOnOutputTablesChangedListener(OutputCodeChangedListener listener) {
    outputCodeChangedListeners.remove(listener);
  }

  /** Removes an input table change listener. */
  public void removeOnInputTablesChangedListener(InputCodeChangedListener listener) {
    inputCodeChangedListeners.remove(listener);
  }

  /** Returns the translator to use on output. */
  public BrailleTranslator getOutputTranslator() {
    return outputTranslator;
  }

  /** Returns the translator to use on input. */
  public BrailleTranslator getInputTranslator() {
    return inputTranslator;
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    if (context.getString(com.google.android.accessibility.braille.common.R.string.pref_bd_output_code).equals(key)) {
      updateOutputTranslators();
    } else if (context.getString(com.google.android.accessibility.braille.common.R.string.pref_brailleime_translator_code).equals(key)) {
      updateInputTranslator();
    } else if (context.getString(com.google.android.accessibility.braille.common.R.string.pref_braille_contracted_mode).equals(key)) {
      updateOutputTranslators();
      updateInputTranslator();
    }
  }

  private void updateOutputTranslators() {
    TranslatorFactory translatorFactory = BrailleUserPreferences.readTranslatorFactory(context);
    Code code = BrailleUserPreferences.readCurrentActiveOutputCodeAndCorrect(context);
    boolean contracted = BrailleUserPreferences.readContractedMode(context);
    BrailleTranslator newTranslator = translatorFactory.create(context, code.name(), contracted);
    boolean changed = !newTranslator.equals(outputTranslator);
    outputTranslator = newTranslator;
    if (changed) {
      callOnOutputCodeChangedListeners();
    }
  }

  private void updateInputTranslator() {
    TranslatorFactory translatorFactory = BrailleUserPreferences.readTranslatorFactory(context);
    Code code = BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(context);
    boolean contracted = BrailleUserPreferences.readContractedMode(context);
    BrailleTranslator newTranslator = translatorFactory.create(context, code.name(), contracted);
    boolean changed = !newTranslator.equals(inputTranslator);
    inputTranslator = newTranslator;
    if (changed) {
      callOnInputCodeChangedListeners();
    }
  }

  private void callOnOutputCodeChangedListeners() {
    for (OutputCodeChangedListener listener : outputCodeChangedListeners) {
      listener.onOutputCodeChanged();
    }
  }

  private void callOnInputCodeChangedListeners() {
    for (InputCodeChangedListener listener : inputCodeChangedListeners) {
      listener.onInputCodeChanged();
    }
  }
}

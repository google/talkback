/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.actor;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.TextUtils;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline.FeedbackReturner;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService.SpeechLanguage;
import com.google.android.accessibility.utils.ScreenMonitor;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Changes the TTS output language and provides language state. */
public class LanguageActor {

  /** Read-only interface for actor-state data. */
  public class State {
    public boolean allowSelectLanguage() {
      return LanguageActor.this.allowSelectLanguage();
    }

    public @Nullable Set<Locale> getInstalledLanguages() {
      return LanguageActor.this.getInstalledLanguages();
    }

    public String getCurrentLanguageString() {
      return LanguageActor.this.getCurrentLanguageString();
    }
  }

  private static final String TAG = "LanguageActor";
  private final Context context;
  private FeedbackReturner pipeline;
  private ActorState actorState;
  private SpeechLanguage speechLanguage;
  private @Nullable Set<Locale> installLanguages;

  public final LanguageActor.State state = new LanguageActor.State();

  public LanguageActor(Context context, SpeechLanguage speechLanguage) {
    this.context = context;
    this.speechLanguage = speechLanguage;
  }

  public void setPipeline(FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  /**
   * The language list should be shown only if there is more than 1 language installed. But there is
   * a default item "Reset" in the list, the method would be true only if there are 3 or more items
   * in the list.
   */
  public boolean allowSelectLanguage() {
    // We do not want to show languages menu if the user is on the lock screen.
    if (ScreenMonitor.isDeviceLocked(context)) {
      return false;
    }

    Set<Voice> voices = actorState.getSpeechState().getVoices();
    if (voices == null) {
      return false;
    }

    // Using Set because there are many duplicate Voice in TextToSpeech.getVoices().
    Set<Locale> languagesAvailable = new HashSet<>();

    // The item is "Reset" means using system language.
    languagesAvailable.add(null);

    for (Voice voice : voices) {
      Set<String> features = voice.getFeatures();
      // Filtering the installed voices to add to the menu
      if ((features != null)
          && !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
          && !voice.isNetworkConnectionRequired()) {
        languagesAvailable.add(voice.getLocale());
      }
    }

    LogUtils.v(TAG, "Installed languages: " + languagesAvailable);
    if (languagesAvailable.size() >= 3) {
      installLanguages = languagesAvailable;
      return true;
    }

    return false;
  }

  /** Changes the current language to the previous/next installed language. */
  public void selectPreviousOrNextLanguage(boolean isNext) {
    @Nullable Set<Locale> languageSet = getInstalledLanguages();
    if (languageSet == null) {
      return;
    }

    List<Locale> languages = new ArrayList<>(languageSet);
    int size = languages.size();

    // Get the index of the current language.
    Locale currentLanguage = speechLanguage.getCurrentLanguage();
    int index = languages.indexOf(currentLanguage);

    // Changes to the next/previous language.
    if (isNext) {
      index++;
      // Current item is the last item of the list, so wrap to the front of the list.
      if (index >= size) {
        index = 0;
      }
    } else {
      index--;
      // Current item is the first item of the list, so wrap to the end of the list.
      if (index < 0) {
        index = size - 1;
      }
    }

    setLanguage(languages.get(index));
  }

  /**
   * Sets and announces the current language.
   *
   * @param language new language, null means using the system language.
   */
  public void setLanguage(@Nullable Locale language) {
    speechLanguage.setCurrentLanguage(language);
    pipeline.returnFeedback(
        EVENT_ID_UNTRACKED, Feedback.speech(getLocaleString(context, language)));
  }

  /**
   * Gets all installed languages.
   *
   * @return returns a set of installed languages only if there is more than one language installed.
   */
  public @Nullable Set<Locale> getInstalledLanguages() {
    if (allowSelectLanguage()) {
      return installLanguages;
    }
    return null;
  }

  /** Gets a string of current speech language, including language and country. */
  public String getCurrentLanguageString() {
    return getLocaleString(context, speechLanguage.getCurrentLanguage());
  }

  /** Gets a string of locale, including language and country. */
  public static String getLocaleString(Context context, @Nullable Locale locale) {
    if (locale == null) {
      // Null means to reset language.
      return context.getString(R.string.reset_user_language_preference);
    }
    String country = locale.getDisplayCountry();
    if (TextUtils.isEmpty(country)) {
      return locale.getDisplayLanguage();
    } else {
      return context.getString(
          R.string.template_language_options_menu_item, locale.getDisplayLanguage(), country);
    }
  }
}

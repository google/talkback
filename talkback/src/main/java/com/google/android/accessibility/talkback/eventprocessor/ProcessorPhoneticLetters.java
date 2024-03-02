/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.talkback.eventprocessor;

import static androidx.core.view.accessibility.AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD;
import static com.google.android.accessibility.talkback.Feedback.HINT;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.LocaleSpan;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Manages phonetic letters. If the user waits on a key or selected character, the word from the
 * phonetic alphabet that represents it. This class currently is a mix of event-interpreter and
 * feedback-mapper.
 */
public class ProcessorPhoneticLetters implements AccessibilityEventListener {

  private static final String TAG = "ProcPhoneticLetters";

  /** Timeout before reading a phonetic letter. */
  private static final int DELAY_READING_PHONETIC_LETTER = 250;

  private static final String FALLBACK_LOCALE = "en-US";
  private static final int MASK_EVENT_CANCEL_PHONETIC_LETTERS =
      ~(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
          | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
          | AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
          | AccessibilityEvent.TYPE_VIEW_HOVER_ENTER
          | AccessibilityEvent.TYPE_ANNOUNCEMENT
          // When gesture detection occurs in TalkBack, the events dispatch order is different than
          // when gestures are detected by the framework. When gestures are detected in TalkBack,
          // the TYPE_TOUCH_INTERACTION_END comes after the onGesture; that's why the phonetic hints
          // are interrupted. We put the TYPE_TOUCH_INTERACTION_END event in here so that the
          // phonetic letter won't be interrupted.
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_END
          |
          // Do not cancel phonetic letter feedback for TYPE_WINDOWS_CHANGED event.
          // ProcessorCursorState introduces an overlay on EditText. When we open a
          // keyboard and long press a key, the overlay is hidden and trigger a
          // TYPE_WINDOWS_CHANGED event, which might interrupt the phonetic letter
          // feedback. In other use cases, windows change are fired with other types of
          // events (e.g. TYPE_WINDOW_STATE_CHANGED, touch interaction on screen, etc.).
          // We could add TYPE_WINDOWS_CHANGED here as an exemption.
          AccessibilityEvent.TYPE_WINDOWS_CHANGED);

  /** Event types that are handled by ProcessorPhoneticLetters. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_PHONETIC_LETTERS =
      MASK_EVENT_CANCEL_PHONETIC_LETTERS
          | AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY;

  private final SharedPreferences prefs;
  private final TalkBackService service;

  /** Callback to return generated feedback to pipeline. */
  private Pipeline.FeedbackReturner pipeline;

  // Maps Language -> letter -> Phonetic letter.
  private final Map<String, Map<String, String>> phoneticLetters = new HashMap<>();

  private final GlobalVariables globalVariables;

  public ProcessorPhoneticLetters(TalkBackService service, GlobalVariables globalVariables) {
    prefs = SharedPreferencesUtils.getSharedPreferences(service);
    this.service = service;
    this.globalVariables = globalVariables;
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_PHONETIC_LETTERS;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (shouldCancelPhoneticLetter(event)) {
      // The pending phonetic letter should be interrupted when receiving event
      // TYPE_GESTURE_DETECTION_START. Since the system wouldn't sent this event when using TalkBack
      // gesture detection, TalkBackService needs to notify it manually.
      cancelPhoneticLetter(eventId);
    }

    if (!arePhoneticLettersEnabled()) {
      return;
    }

    if (AccessibilityEventUtils.isCharacterTraversalEvent(event)) {
      processTraversalEvent(event, eventId);
    }
  }

  /**
   * Returns the spelling for the given {@code event} if it corresponds to an accessibility-focus
   * change on an on-screen keyboard.
   *
   * <p>If phonetic spelling is turned off in the settings, or it is a password input field, it will
   * return nothing.
   */
  public Optional<CharSequence> getPhoneticLetterForKeyboardFocusEvent(AccessibilityEvent event) {
    if (!arePhoneticLettersEnabled() || !isKeyboardEvent(event)) {
      return Optional.empty();
    }

    if (globalVariables != null
        && globalVariables.getLastTextEditIsPassword()
        && !globalVariables.shouldSpeakPasswords()) {
      // Skip phonetic letters when editing passwords.
      return Optional.empty();
    }

    final CharSequence text = AccessibilityEventUtils.getEventTextOrDescription(event);
    if (TextUtils.isEmpty(text)) {
      return Optional.empty();
    }

    String localeString = null;
    // For new version of Gboard, contentDescription of the node is wrapped in the locale of the
    // IME.
    if (text instanceof Spannable) {
      Spannable spannable = (Spannable) text;
      LocaleSpan[] spans = spannable.getSpans(0, text.length(), LocaleSpan.class);
      for (LocaleSpan span : spans) {
        // Quit the loop when a LocaleSpan is detected. We expect just one LocaleSpan.
        localeString = span.getLocale().toLanguageTag();
        break;
      }
    }
    // Use system locale as the fallback option.
    if (localeString == null) {
      localeString = Locale.getDefault().toLanguageTag();
    }

    return Optional.ofNullable(getPhoneticLetter(localeString, text.toString()));
  }

  private boolean arePhoneticLettersEnabled() {
    final Resources res = service.getResources();
    return VerbosityPreferences.getPreferenceValueBool(
        prefs,
        res,
        res.getString(R.string.pref_phonetic_letters_key),
        res.getBoolean(R.bool.pref_phonetic_letters_default));
  }

  private boolean isKeyboardEvent(AccessibilityEvent event) {
    if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      return false;
    }

    // For platform since lollipop, check that the current window is an
    // Input Method.
    final AccessibilityNodeInfo source = event.getSource();
    AccessibilityWindowInfo window = AccessibilityNodeInfoUtils.getWindow(source);
    return (AccessibilityWindowInfoUtils.getType(window) == TYPE_INPUT_METHOD);
  }

  /** Handle an event that indicates a text is being traversed at character granularity. */
  private void processTraversalEvent(AccessibilityEvent event, EventId eventId) {
    final CharSequence text;
    if (Role.getSourceRole(event) == Role.ROLE_EDIT_TEXT) {
      text = AccessibilityEventUtils.getEventAggregateText(event);
    } else {
      text = AccessibilityEventUtils.getEventTextOrDescription(event);
    }
    if (TextUtils.isEmpty(text)) {
      return;
    }

    String letter;
    if ((event.getAction() == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY
            || event.getAction()
                == AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
        && event.getFromIndex() >= 0
        && event.getFromIndex() < text.length()) {
      letter = String.valueOf(text.charAt(event.getFromIndex()));
    } else {
      return;
    }
    speakPhoneticLetterForTraversedText(
        TextUtils.equals(event.getPackageName(), PackageManagerUtils.TALKBACK_PACKAGE),
        letter,
        eventId);
  }

  public void speakPhoneticLetterForTraversedText(
      boolean isTalkbackPackage, String letter, EventId eventId) {
    String localeString;
    // Change the locale to the user preferred locale
    // changed using language switcher, with an exception while reading talkback text.
    // As Talkback text is always in the system language.
    if (isTalkbackPackage || service.getUserPreferredLocale() == null) {
      localeString = Locale.getDefault().toLanguageTag();
    } else {
      localeString = service.getUserPreferredLocale().toLanguageTag();
    }
    CharSequence phoneticLetter = getPhoneticLetter(localeString, letter);
    if (phoneticLetter != null) {
      postPhoneticLetterRunnable(phoneticLetter, eventId);
    }
  }

  // Map a character to a phonetic letter. If the locale cannot be parsed, falls back to english.
  private @Nullable CharSequence getPhoneticLetter(String locale, String letter) {
    Locale parsedLocale = LocaleUtils.parseLocaleString(locale);
    if (parsedLocale == null) {
      parsedLocale = Locale.getDefault();
    }
    String normalizedLetter = letter.toLowerCase(parsedLocale);
    String value = getPhoneticLetterMap(locale).get(normalizedLetter);
    if (value == null) {
      if (parsedLocale.getCountry().isEmpty()) {
        // As a last resort, fall back to English.
        value = getPhoneticLetterMap(FALLBACK_LOCALE).get(normalizedLetter);
      } else {
        // Get the letter for the base language, if possible.
        CharSequence valueInBaseLanguage =
            getPhoneticLetter(parsedLocale.getLanguage(), normalizedLetter);
        return valueInBaseLanguage;
      }
    }
    // Attaching the locale to the phonetic letter
    SpannableString ss = null;
    if (value != null) {
      ss = new SpannableString(value);
      ss.setSpan(new LocaleSpan(parsedLocale), 0, ss.length(), 0);
    }
    return ss;
  }

  /**
   * Get the mapping from letter to phonetic letter for a given locale. The map is loaded as needed.
   */
  private Map<String, String> getPhoneticLetterMap(String locale) {
    Map<String, String> map = this.phoneticLetters.get(locale);
    if (map == null) {
      // If there is no entry for the local, the map will be left
      // empty.  This prevents future load attempts for that locale.
      map = new HashMap<String, String>();
      this.phoneticLetters.put(locale, map);

      InputStream stream = service.getResources().openRawResource(R.raw.phonetic_letters);
      BufferedReader reader = null;
      try {
        reader = new BufferedReader(new InputStreamReader(stream, UTF_8));
        StringBuilder stringBuilder = new StringBuilder();
        String input;
        while ((input = reader.readLine()) != null) {
          stringBuilder.append(input);
        }
        stream.close();
        reader.close();

        JSONObject locales = new JSONObject(stringBuilder.toString());
        JSONObject phoneticLetters = locales.getJSONObject(locale);

        if (phoneticLetters != null) {
          Iterator<?> keys = phoneticLetters.keys();
          while (keys.hasNext()) {
            String letter = (String) keys.next();
            map.put(letter, phoneticLetters.getString(letter));
          }
        }
      } catch (java.io.IOException e) {
        LogUtils.e(TAG, e.toString());
      } catch (JSONException e) {
        LogUtils.e(TAG, e.toString());
      }
    }
    return map;
  }

  /** Returns true if a pending phonetic letter should be interrupted. */
  private boolean shouldCancelPhoneticLetter(AccessibilityEvent event) {
    return (event.getEventType() & MASK_EVENT_CANCEL_PHONETIC_LETTERS) != 0;
  }

  /** Starts the phonetic letter timeout. Call this whenever a letter has been paused on. */
  private void postPhoneticLetterRunnable(CharSequence phoneticLetter, EventId eventId) {
    SpeakOptions speakOptions =
        SpeakOptions.create()
            .setQueueMode(SpeechController.QUEUE_MODE_QUEUE)
            .setFlags(
                FeedbackItem.FLAG_NO_HISTORY
                    | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                    | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                    | FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE);
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(phoneticLetter, speakOptions)
            .setDelayMs(DELAY_READING_PHONETIC_LETTER)
            .setInterruptGroup(HINT)
            .setInterruptLevel(1)
            .setSenderName(TAG));
  }

  /** Removes the phonetic letter timeout and completion action. */
  public void cancelPhoneticLetter(EventId eventId) {
    pipeline.returnFeedback(eventId, Feedback.interrupt(HINT, /* level= */ 1));
  }
}

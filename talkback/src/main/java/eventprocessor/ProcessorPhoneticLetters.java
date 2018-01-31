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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.LocaleSpan;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Manages phonetic letters. If the user waits on a key or selected character, the word from the
 * phonetic alphabet that represents it.
 */
public class ProcessorPhoneticLetters implements AccessibilityEventListener {
  private static final String FALLBACK_LOCALE = "en_US";
  private static final int MASK_EVENT_CANCEL_PHONETIC_LETTERS =
      ~(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
          | AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
          | AccessibilityEvent.TYPE_VIEW_LONG_CLICKED
          | AccessibilityEvent.TYPE_ANNOUNCEMENT
          |
          // Do not cancel phonetic letter feedback for TYPE_WINDOWS_CHANGED event.
          // ProcessorCursorState introduces an overlay on EditText. When we open a
          // keyboard and long press a key, the overlay is hidden and trigger a
          // TYPE_WINDOWS_CHANGED event, which might interrupt the phonetic letter
          // feedback. In other use cases, windows change are fired with other types of
          // events (e.g. TYPE_WINDOW_STATE_CHANGED, touch interaction on screen, etc.).
          // We could add TYPE_WINDOWS_CHANGED here as an exemption.
          AccessibilityEvent.TYPE_WINDOWS_CHANGED);
  public static final String TALBACK_PACKAGE = "com.google.android.marvin.talkback";

  /** Event types that are handled by ProcessorPhoneticLetters. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_PHONETIC_LETTERS =
      MASK_EVENT_CANCEL_PHONETIC_LETTERS
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY;

  private final SharedPreferences mPrefs;
  private final TalkBackService mService;
  private final SpeechController mSpeechController;
  private final PhoneticLetterHandler mHandler;

  // Maps Language -> letter -> Phonetic letter.
  private Map<String, Map<String, String>> mPhoneticLetters =
      new HashMap<String, Map<String, String>>();

  public ProcessorPhoneticLetters(TalkBackService service, SpeechController speechController) {
    if (speechController == null) throw new IllegalStateException();
    mPrefs = SharedPreferencesUtils.getSharedPreferences(service);
    mService = service;
    mSpeechController = speechController;
    mHandler = new PhoneticLetterHandler(this);
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_PHONETIC_LETTERS;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (shouldCancelPhoneticLetter(event)) {
      cancelPhoneticLetter();
    }

    if (!arePhoneticLettersEnabled()) {
      return;
    }

    if (isKeyboardEvent(event)) {
      processKeyboardKeyEvent(event);
    }

    if (AccessibilityEventUtils.isCharacterTraversalEvent(event)) {
      processTraversalEvent(event);
    }
  }

  /** Handle an event that indicates a key is held on the soft keyboard. */
  private void processKeyboardKeyEvent(AccessibilityEvent event) {
    final CharSequence text = AccessibilityEventUtils.getEventTextOrDescription(event);
    if (TextUtils.isEmpty(text)) {
      return;
    }

    String localeString = null;
    // For new version of Gboard, contentDescription of the node is wrapped in the locale of the
    // IME.
    if (text instanceof Spannable) {
      Spannable spannable = (Spannable) text;
      LocaleSpan[] spans = spannable.getSpans(0, text.length(), LocaleSpan.class);
      for (LocaleSpan span : spans) {
        // Quit the loop when a LocaleSpan is detected. We expect just one LocaleSpan.
        localeString = span.getLocale().toString();
        break;
      }
    }
    // Old version of Gboard does not provide content description wrapped in the locale of the IME
    // so we try using InputMethodManager.
    if (localeString == null) {
      InputMethodManager inputMethodManager =
          (InputMethodManager) mService.getSystemService(Context.INPUT_METHOD_SERVICE);
      InputMethodSubtype inputMethod = inputMethodManager.getCurrentInputMethodSubtype();
      if (inputMethod != null) {
        String localeStringFromIme = inputMethod.getLocale();
        if (!localeStringFromIme.isEmpty()) {
          localeString = localeStringFromIme;
        }
      }
    }
    // Use system locale as the fallback option.
    if (localeString == null) {
      localeString = Locale.getDefault().toString();
    }

    CharSequence phoneticLetter = getPhoneticLetter(localeString, text.toString());
    if (phoneticLetter != null) {
      postPhoneticLetterRunnable(phoneticLetter);
    }
  }

  private boolean arePhoneticLettersEnabled() {
    final Resources res = mService.getResources();
    return VerbosityPreferences.getPreferenceValueBool(
        mPrefs,
        res,
        res.getString(R.string.pref_phonetic_letters_key),
        res.getBoolean(R.bool.pref_phonetic_letters_default));
  }

  private boolean isKeyboardEvent(AccessibilityEvent event) {
    if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      return false;
    }

    if (BuildVersionUtils.isAtLeastLMR1()) {
      // For platform since lollipop, check that the current window is an
      // Input Method.
      final AccessibilityNodeInfo source = event.getSource();
      if (source == null) {
        return false;
      }

      int windowId = source.getWindowId();
      source.recycle();
      WindowManager manager = new WindowManager(mService);
      return manager.getWindowType(windowId) == AccessibilityWindowInfo.TYPE_INPUT_METHOD;
    } else {
      // For old platforms, we can't check the window type directly, so just
      // manually check the classname.
      if (event.getClassName() != null) {
        return event.getClassName().equals("com.android.inputmethod.keyboard.Key");
      } else {
        return false;
      }
    }
  }

  /** Handle an event that indicates a text is being traversed at character granularity. */
  private void processTraversalEvent(AccessibilityEvent event) {
    final CharSequence text = AccessibilityEventUtils.getEventTextOrDescription(event);
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
    String localeString;
    // Change the locale to the user preferred locale
    // changed using language switcher, with an exception while reading talkback text.
    // As Talkback text is always in the system language.
    if (TextUtils.equals(event.getPackageName(), TALBACK_PACKAGE)
        || mService.getUserPreferredLocale() == null) {
      localeString = Locale.getDefault().toString();
    } else {
      localeString = mService.getUserPreferredLocale().toString();
    }
    CharSequence phoneticLetter = getPhoneticLetter(localeString, letter);
    if (phoneticLetter != null) {
      postPhoneticLetterRunnable(phoneticLetter);
    }
  }

  // Map a character to a phonetic letter. If the locale cannot be parsed, falls back to english.
  private CharSequence getPhoneticLetter(String locale, String letter) {
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
    // Attaching the locale to the the phonetic letter
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
    Map<String, String> map = mPhoneticLetters.get(locale);
    if (map == null) {
      // If there is no entry for the local, the map will be left
      // empty.  This prevents future load attempts for that locale.
      map = new HashMap<String, String>();
      mPhoneticLetters.put(locale, map);

      InputStream stream = mService.getResources().openRawResource(R.raw.phonetic_letters);
      BufferedReader reader = null;
      try {
        reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuilder stringBuilder = new StringBuilder();
        String input;
        while ((input = reader.readLine()) != null) {
          stringBuilder.append(input);
        }
        stream.close();

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
        LogUtils.log(this, Log.ERROR, e.toString());
      } catch (JSONException e) {
        LogUtils.log(this, Log.ERROR, e.toString());
      }
    }
    return map;
  }

  /** Returns true if a pending phonetic letter should be interrupted. */
  private boolean shouldCancelPhoneticLetter(AccessibilityEvent event) {
    return (event.getEventType() & MASK_EVENT_CANCEL_PHONETIC_LETTERS) != 0;
  }

  /** Starts the phonetic letter timeout. Call this whenever a letter has been paused on. */
  private void postPhoneticLetterRunnable(CharSequence phoneticLetter) {
    mHandler.startPhoneticLetterTimeout(phoneticLetter);
  }

  /** Removes the phonetic letter timeout and completion action. */
  private void cancelPhoneticLetter() {
    mHandler.cancelPhoneticLetterTimeout();
  }

  private static class PhoneticLetterHandler
      extends WeakReferenceHandler<ProcessorPhoneticLetters> {
    /** Message identifier for a phonetic letter notification. */
    private static final int PHONETIC_LETTER_TIMEOUT = 1;

    /** Timeout before reading a phonetic letter. */
    private static final long DELAY_PHONETIC_LETTER_TIMEOUT = 1000;

    public PhoneticLetterHandler(ProcessorPhoneticLetters parent) {
      super(parent);
    }

    @Override
    public void handleMessage(Message msg, ProcessorPhoneticLetters parent) {
      switch (msg.what) {
        case PHONETIC_LETTER_TIMEOUT:
          {
            final CharSequence phoneticLetter = (CharSequence) msg.obj;
            // Use QUEUE mode so that we don't interrupt more important messages.
            EventId eventId = EVENT_ID_UNTRACKED; // Hints occur after other feedback.
            parent.mSpeechController.speak(
                phoneticLetter, /* Text */
                SpeechController.QUEUE_MODE_QUEUE, /* QueueMode */
                FeedbackItem.FLAG_NO_HISTORY | FeedbackItem.FLAG_FORCED_FEEDBACK, /* Flags */
                null, /* SpeechParams */
                eventId);
            break;
          }
        default: // fall out
      }
    }

    public void startPhoneticLetterTimeout(CharSequence phoneticLetter) {
      final Message msg = obtainMessage(PHONETIC_LETTER_TIMEOUT, phoneticLetter);
      sendMessageDelayed(msg, DELAY_PHONETIC_LETTER_TIMEOUT);
    }

    public void cancelPhoneticLetterTimeout() {
      removeMessages(PHONETIC_LETTER_TIMEOUT);
    }
  }
}

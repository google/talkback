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

package com.android.talkback.eventprocessor;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import com.android.talkback.FeedbackItem;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityEventUtils;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;
import com.android.utils.SharedPreferencesUtils;
import com.android.utils.WeakReferenceHandler;
import com.android.utils.WindowManager;
import com.google.android.marvin.talkback.TalkBackService;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * Manages phonetic letters. If the user waits on a key or selected character,
 * the word from the phonetic alphabet that represents it.
 */
public class ProcessorPhoneticLetters implements AccessibilityEventListener {
    private static final String FALLBACK_LOCALE = "en_US";

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
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (shouldCancelPhoneticLetter(event)) {
            cancelPhoneticLetter();
        }

        if (!arePhoneticLettersEnabled())
            return;

        if (isKeyboardEvent(event))
            processKeyboardKeyEvent(event);

        if (AccessibilityEventUtils.isCharacterTraversalEvent(event))
            processTraversalEvent(event);
    }

    /**
     * Handle an event that indicates a key is held on the soft keyboard.
     */
    private void processKeyboardKeyEvent(AccessibilityEvent event) {
        final CharSequence text = AccessibilityEventUtils.getEventTextOrDescription(event);
        if (TextUtils.isEmpty(text)) {
            return;
        }

        String localeString = FALLBACK_LOCALE;
        InputMethodManager inputMethodManager =
                (InputMethodManager) mService.getSystemService(Context.INPUT_METHOD_SERVICE);
        InputMethodSubtype inputMethod = inputMethodManager.getCurrentInputMethodSubtype();
        if (inputMethod != null) {
            localeString = inputMethod.getLocale();
        }

        String phoneticLetter = getPhoneticLetter(localeString, text.toString());
        if (phoneticLetter != null) {
            postPhoneticLetterRunnable(phoneticLetter);
        }
    }

    private boolean arePhoneticLettersEnabled() {
        return SharedPreferencesUtils.getBooleanPref(
                mPrefs, mService.getResources(),
                R.string.pref_phonetic_letters_key,
                R.bool.pref_phonetic_letters_default);
    }

    private boolean isKeyboardEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
            return false;
        }

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            // For platform since lollipop, check that the current window is an
            // Input Method.
            final AccessibilityNodeInfo source = event.getSource();
            if (source == null) {
                return false;
            }

            int windowId = source.getWindowId();
            WindowManager manager = new WindowManager(mService.isScreenLayoutRTL());
            manager.setWindows(mService.getWindows());
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

    /**
     * Handle an event that indicates a text is being traversed at character
     * granularity.
     */
    private void processTraversalEvent(AccessibilityEvent event) {
        final CharSequence text = AccessibilityEventUtils.getEventTextOrDescription(event);
        if (TextUtils.isEmpty(text)) {
            return;
        }

        String letter;
        if ((event.getAction() == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY ||
                event.getAction() ==
                        AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY) &&
                        event.getFromIndex() >= 0 && event.getFromIndex() < text.length()) {
            letter = String.valueOf(text.charAt(event.getFromIndex()));
        } else {
            return;
        }

        String phoneticLetter = getPhoneticLetter(Locale.getDefault().toString(), letter);
        if (phoneticLetter != null) {
            postPhoneticLetterRunnable(phoneticLetter);
        }
    }

    /**
     * Get the Locale from a language tag's language and country.
     * The variant is discarded.  Returns Locale.ENGLISH on failure.
     */
    static Locale parseLanguageTag(String languageTag) {
        String localeParts[] = languageTag.split("_", 3);

        if (localeParts.length >= 2) {
            return new Locale(localeParts[0], localeParts[1]);
        } else if (localeParts.length >= 1) {
            return new Locale(localeParts[0]);
        } else {
            return Locale.ENGLISH;
        }
    }

    /**
     * Map a character to a phonetic letter.
     */
    private String getPhoneticLetter(String locale, String letter) {
        Locale bcp47_locale = parseLanguageTag(locale);

        String normalized_letter = letter.toLowerCase(bcp47_locale);
        String value = getPhoneticLetterMap(locale).get(normalized_letter);
        if (value == null) {
            if (bcp47_locale.getCountry().isEmpty()) {
                // As a last resort, fall back to English.
                value = getPhoneticLetterMap(FALLBACK_LOCALE).get(normalized_letter);
            } else {
                // Get the letter for the base language, if possible.
                value = getPhoneticLetter(bcp47_locale.getLanguage(), normalized_letter);
            }
        }
        return value;
    }

    /**
     * Get the mapping from letter to phonetic letter for a given locale.
     * The map is loaded as needed.
     */
    private Map<String, String> getPhoneticLetterMap(String locale) {
        Map<String, String> map = mPhoneticLetters.get(locale);
        if (map == null) {
            // If there is no entry for the local, the map will be left
            // empty.  This prevents future load attempts for that locale.
            map = new HashMap<String, String>();
            mPhoneticLetters.put(locale, map);

            InputStream stream =
                    mService.getResources().openRawResource(R.raw.phonetic_letters);
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

    /**
     * Returns true if a pending phonetic letter should be interrupted.
     */
    private boolean shouldCancelPhoneticLetter(AccessibilityEvent event) {
        return event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                event.getEventType() != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                event.getEventType() != AccessibilityEvent.TYPE_VIEW_LONG_CLICKED &&
                event.getEventType() != AccessibilityEvent.TYPE_ANNOUNCEMENT;
    }

    /**
     * Starts the phonetic letter timeout. Call this whenever a letter has
     * been paused on.
     */
    private void postPhoneticLetterRunnable(String phoneticLetter) {
        mHandler.startPhoneticLetterTimeout(phoneticLetter);
    }

    /**
     * Removes the phonetic letter timeout and completion action.
     */
    private void cancelPhoneticLetter() {
        mHandler.cancelPhoneticLetterTimeout();
    }

    private static class PhoneticLetterHandler extends
            WeakReferenceHandler<ProcessorPhoneticLetters> {
        /**
         * Message identifier for a phonetic letter notification.
         */
        private static final int PHONETIC_LETTER_TIMEOUT = 1;

        /**
         * Timeout before reading a phonetic letter.
         */
        private static final long DELAY_PHONETIC_LETTER_TIMEOUT = 1000;

        public PhoneticLetterHandler(ProcessorPhoneticLetters parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, ProcessorPhoneticLetters parent) {
            switch (msg.what) {
                case PHONETIC_LETTER_TIMEOUT: {
                    final String phoneticLetter = (String) msg.obj;
                    // Use QUEUE mode so that we don't interrupt more important messages.
                    parent.mSpeechController.speak(
                            phoneticLetter, SpeechController.QUEUE_MODE_QUEUE,
                            FeedbackItem.FLAG_NO_HISTORY, null);
                    break;
                }
            }
        }

        public void startPhoneticLetterTimeout(String phoneticLetter) {
            final Message msg = obtainMessage(PHONETIC_LETTER_TIMEOUT, phoneticLetter);
            sendMessageDelayed(msg, DELAY_PHONETIC_LETTER_TIMEOUT);
        }

        public void cancelPhoneticLetterTimeout() {
            removeMessages(PHONETIC_LETTER_TIMEOUT);
        }
    }
}

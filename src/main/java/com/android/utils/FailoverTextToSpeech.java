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

package com.android.utils;

import com.android.talkback.SpeechController;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.provider.Settings.Secure;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.text.TextUtils;
import android.util.Log;

import com.android.utils.compat.provider.SettingsCompatUtils.SecureCompatUtils;
import com.android.utils.compat.speech.tts.TextToSpeechCompatUtils;
import com.android.utils.LogUtils;
import com.android.utils.WeakReferenceHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Wrapper for {@link TextToSpeech} that handles fail-over when a specific
 * engine does not work.
 * <p>
 * Does <strong>NOT</strong> implement queuing! Every call to {@link #speak}
 * flushes the global speech queue.
 * <p>
 * This wrapper handles the following:
 * <ul>
 * <li>Fail-over from a failing TTS to a working one
 * <li>Splitting utterances into &lt;4k character chunks
 * <li>Switching to the system TTS when media is unmounted
 * <li>Utterance-specific pitch and rate changes
 * <li>Pitch and rate changes relative to the user preference
 * </ul>
 */
@SuppressWarnings("deprecation")
public class FailoverTextToSpeech {
    private static final String TAG = "FailoverTextToSpeech";
    /** The package name for the Google TTS engine. */
    private static final String PACKAGE_GOOGLE_TTS = "com.google.android.tts";

    /** Number of times a TTS engine can fail before switching. */
    private static final int MAX_TTS_FAILURES = 3;

    /** Constant to flush speech globally. This is a private API. */
    private static final int SPEECH_FLUSH_ALL = 2;

    /**
     * {@link BroadcastReceiver} for determining changes in the media state used
     * for switching the TTS engine.
     */
    private final MediaMountStateMonitor mMediaStateMonitor = new MediaMountStateMonitor();

    /** A list of installed TTS engines. */
    private final LinkedList<String> mInstalledTtsEngines = new LinkedList<>();

    private final Context mContext;
    private final ContentResolver mResolver;

    /** The TTS engine. */
    private TextToSpeech mTts;

    /** The engine loaded into the current TTS. */
    private String mTtsEngine;

    /** The number of time the current TTS has failed consecutively. */
    private int mTtsFailures;

    /** The package name of the preferred TTS engine. */
    private String mDefaultTtsEngine;

    /** The package name of the system TTS engine. */
    private String mSystemTtsEngine;

    /** A temporary TTS used for switching engines. */
    private TextToSpeech mTempTts;

    /** The engine loading into the temporary TTS. */
    private String mTempTtsEngine;

    /** The rate adjustment specified in {@link android.provider.Settings}. */
    private float mDefaultRate;

    /** The pitch adjustment specified in {@link android.provider.Settings}. */
    private float mDefaultPitch;

    /** The most recent rate sent to {@link TextToSpeech#setSpeechRate}. */
    private float mCurrentRate = 1.0f;

    /** The most recent pitch sent to {@link TextToSpeech#setSpeechRate}. */
    private float mCurrentPitch = 1.0f;

    private List<FailoverTtsListener> mListeners = new ArrayList<>();

    public FailoverTextToSpeech(Context context) {
        mContext = context;
        mContext.registerReceiver(mMediaStateMonitor, mMediaStateMonitor.getFilter());

        final Uri defaultSynth = Secure.getUriFor(Secure.TTS_DEFAULT_SYNTH);
        final Uri defaultPitch = Secure.getUriFor(Secure.TTS_DEFAULT_PITCH);
        final Uri defaultRate = Secure.getUriFor(Secure.TTS_DEFAULT_RATE);

        mResolver = context.getContentResolver();
        mResolver.registerContentObserver(defaultSynth, false, mSynthObserver);
        mResolver.registerContentObserver(defaultPitch, false, mPitchObserver);
        mResolver.registerContentObserver(defaultRate, false, mRateObserver);

        registerGoogleTtsFixCallbacks();

        updateDefaultPitch();
        updateDefaultRate();

        // Updating the default engine reloads the list of installed engines and
        // the system engine. This also loads the default engine.
        updateDefaultEngine();
    }

    /**
     * Add a new listener for changes in speaking state.
     *
     * @param listener The listener to add.
     */
    public void addListener(FailoverTtsListener listener) {
        mListeners.add(listener);
    }

    /**
     * Whether the text-to-speech engine is ready to speak.
     *
     * @return {@code true} if calling {@link #speak} is expected to succeed.
     */
    public boolean isReady() {
        return (mTts != null);
    }

    /**
     * Returns the label for the current text-to-speech engine.
     *
     * @return The localized name of the current engine.
     */
    public CharSequence getEngineLabel() {
        return TextToSpeechUtils.getLabelForEngine(mContext, mTtsEngine);
    }

    /**
     * Returns the {@link TextToSpeech} instance that is currently being used as the engine.
     *
     * @return The engine instance.
     */
    @SuppressWarnings("UnusedDeclaration") // Used by analytics
    public TextToSpeech getEngineInstance() {
        return mTts;
    }

    /**
     * Speak the specified text.
     *
     * @param text The text to speak.
     * @param pitch The pitch adjustment, in the range [0 ... 1].
     * @param rate The rate adjustment, in the range [0 ... 1].
     * @param params The parameters to pass to the text-to-speech engine.
     */
    public void speak(CharSequence text, float pitch, float rate, HashMap<String, String> params,
            int stream, float volume) {
        // Handle empty text immediately.
        if (TextUtils.isEmpty(text)) {
            mHandler.onUtteranceCompleted(params.get(Engine.KEY_PARAM_UTTERANCE_ID));
            return;
        }

        int result;

        Exception failureException = null;
        try {
            result = trySpeak(text, pitch, rate, params, stream, volume);
        } catch (Exception e) {
            failureException = e;
            result = TextToSpeech.ERROR;
        }

        if (result == TextToSpeech.ERROR) {
            attemptTtsFailover(mTtsEngine);
        }

        if ((result != TextToSpeech.SUCCESS)
                && params.containsKey(Engine.KEY_PARAM_UTTERANCE_ID)) {
            if (failureException != null) {
                if(LogUtils.LOG_LEVEL <= Log.WARN) {
                    Log.w(TAG, "Failed to speak " + text + " due to an exception");
                }
                failureException.printStackTrace();
            } else {
                if (LogUtils.LOG_LEVEL <= Log.WARN) {
                    Log.w(TAG, "Failed to speak " + text);
                }
            }

            mHandler.onUtteranceCompleted(params.get(Engine.KEY_PARAM_UTTERANCE_ID));
        }
    }

    /**
     * Stops speech from all applications. No utterance callbacks will be sent.
     */
    public void stopAll() {
        try {
            mTts.speak("", SPEECH_FLUSH_ALL, null);
        } catch (Exception e) {
            // Don't care, we're not speaking.
        }
    }

    /**
     * Unregisters receivers, observers, and shuts down the text-to-speech
     * engine. No calls should be made to this object after calling this method.
     */
    public void shutdown() {
        mContext.unregisterReceiver(mMediaStateMonitor);
        unregisterGoogleTtsFixCallbacks();

        mResolver.unregisterContentObserver(mSynthObserver);
        mResolver.unregisterContentObserver(mPitchObserver);
        mResolver.unregisterContentObserver(mRateObserver);

        TextToSpeechUtils.attemptTtsShutdown(mTts);
        mTts = null;

        TextToSpeechUtils.attemptTtsShutdown(mTempTts);
        mTempTts = null;
    }

    /**
     * Attempts to speak the specified text.
     *
     * @param text to speak, must be under 3999 chars.
     * @param pitch to speak text in.
     * @param rate to speak text in.
     * @param params to the TTS.
     * @return The result of speaking the specified text.
     */
    @SuppressWarnings("unused")
    private int trySpeak(CharSequence text, float pitch, float rate, HashMap<String, String> params,
            int stream, float volume) {
        if (mTts == null) {
            return TextToSpeech.ERROR;
        }

        float effectivePitch = (pitch * mDefaultPitch);
        float effectiveRate = (rate * mDefaultRate);

        int result;
        String utteranceId = params.get(Engine.KEY_PARAM_UTTERANCE_ID);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {
            result = speakApi21(text, params, utteranceId, effectivePitch, effectiveRate, stream,
                    volume);
        } else {
            // Set the pitch and rate only if necessary, since that is slow.
            if ((mCurrentPitch != effectivePitch) || (mCurrentRate != effectiveRate)) {
                mTts.stop();
                mTts.setPitch(effectivePitch);
                mTts.setSpeechRate(effectiveRate);
            }

            result = speakCompat(text, params);
        }

        mCurrentPitch = effectivePitch;
        mCurrentRate = effectiveRate;
        if (result != TextToSpeech.SUCCESS) {
            ensureSupportedLocale();
        }

        if (LogUtils.LOG_LEVEL < Log.DEBUG) {
            Log.d(TAG, "Speak call for " + utteranceId + " returned " + result);
        }
        return result;
    }

    private int speakCompat(CharSequence text, HashMap<String, String> params) {
        return mTts.speak(text.toString(), SPEECH_FLUSH_ALL, params);
    }

    @TargetApi(21)
    private int speakApi21(CharSequence text, HashMap<String, String> params, String utteranceId,
            float pitch, float rate, int stream, float volume) {
        Bundle bundle = new Bundle();

        if (params != null) {
            for (String key : params.keySet()) {
                bundle.putString(key, params.get(key));
            }
        }

        bundle.putInt(SpeechController.SpeechParam.PITCH, (int)(pitch*100));
        bundle.putInt(SpeechController.SpeechParam.RATE, (int)(rate*100));
        bundle.putInt(Engine.KEY_PARAM_STREAM, stream);
        bundle.putFloat(SpeechController.SpeechParam.VOLUME, volume);

        return mTts.speak(text, SPEECH_FLUSH_ALL, bundle, utteranceId);
    }

    /**
     * Try to switch the TTS engine.
     *
     * @param engine The package name of the desired TTS engine
     */
    private void setTtsEngine(String engine, boolean resetFailures) {
        if (resetFailures) {
            mTtsFailures = 0;
        }

        // Always try to stop the current engine before switching.
        TextToSpeechUtils.attemptTtsShutdown(mTts);

        if (mTempTts != null) {
            LogUtils.log(SpeechController.class, Log.ERROR,
                    "Can't start TTS engine %s while still loading previous engine", engine);
            return;
        }

        LogUtils.log(SpeechController.class, Log.INFO, "Switching to TTS engine: %s", engine);

        mTempTtsEngine = engine;
        mTempTts = new TextToSpeech(mContext, mTtsChangeListener, engine);
    }

    /**
     * Assumes the current engine has failed and attempts to start the next
     * available engine.
     *
     * @param failedEngine The package name of the engine to switch from.
     */
    private void attemptTtsFailover(String failedEngine) {
        LogUtils.log(
                SpeechController.class, Log.ERROR, "Attempting TTS failover from %s", failedEngine);

        mTtsFailures++;

        // If there is only one installed engine, or if the current engine
        // hasn't failed enough times, just restart the current engine.
        if ((mInstalledTtsEngines.size() <= 1) || (mTtsFailures < MAX_TTS_FAILURES)) {
            setTtsEngine(failedEngine, false);
            return;
        }

        // Move the engine to the back of the list.
        if (failedEngine != null) {
            mInstalledTtsEngines.remove(failedEngine);
            mInstalledTtsEngines.addLast(failedEngine);
        }

        // Try to use the first available TTS engine.
        final String nextEngine = mInstalledTtsEngines.getFirst();

        setTtsEngine(nextEngine, true);
    }

    /**
     * Handles TTS engine initialization.
     *
     * @param status The status returned by the TTS engine.
     */
    @SuppressWarnings("deprecation")
    private void handleTtsInitialized(int status) {
        if (mTempTts == null) {
            LogUtils.log(this, Log.ERROR, "Attempted to initialize TTS more than once!");
            return;
        }

        final TextToSpeech tempTts = mTempTts;
        final String tempTtsEngine = mTempTtsEngine;

        mTempTts = null;
        mTempTtsEngine = null;

        if (status != TextToSpeech.SUCCESS) {
            attemptTtsFailover(tempTtsEngine);
            return;
        }

        final boolean isSwitchingEngines = (mTts != null);

        if (isSwitchingEngines) {
            TextToSpeechUtils.attemptTtsShutdown(mTts);
        }

        mTts = tempTts;
        mTts.setOnUtteranceCompletedListener(mTtsListener);

        if (tempTtsEngine == null) {
            mTtsEngine = TextToSpeechCompatUtils.getCurrentEngine(mTts);
        } else {
            mTtsEngine = tempTtsEngine;
        }

        updateDefaultLocale();


        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {
            setAudioAttributesApi21();
        }

        LogUtils.log(SpeechController.class, Log.INFO, "Switched to TTS engine: %s", tempTtsEngine);

        for (FailoverTtsListener mListener : mListeners) {
            mListener.onTtsInitialized(isSwitchingEngines);
        }
    }

    @TargetApi(21)
    private void setAudioAttributesApi21() {
        mTts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .build());
    }

    /**
     * Method that's called by TTS whenever an utterance is completed. Do common
     * tasks and execute any UtteranceCompleteActions associate with this
     * utterance index (or an earlier index, in case one was accidentally
     * dropped).
     *
     * @param utteranceId The utteranceId from the onUtteranceCompleted callback
     *            - we expect this to consist of UTTERANCE_ID_PREFIX followed by
     *            the utterance index.
     * @param success {@code true} if the utterance was spoken successfully.
     */
    private void handleUtteranceCompleted(String utteranceId, boolean success) {
        if (success) {
            mTtsFailures = 0;
        }

        for (FailoverTtsListener mListener : mListeners) {
            mListener.onUtteranceCompleted(utteranceId, success);
        }
    }

    /**
     * Handles media state changes.
     *
     * @param action The current media state.
     */
    private void handleMediaStateChanged(String action) {
        if (Intent.ACTION_MEDIA_UNMOUNTED.equals(action)) {
            if (!TextUtils.equals(mSystemTtsEngine, mTtsEngine)) {
                // Temporarily switch to the system TTS engine.
                LogUtils.log(this, Log.VERBOSE, "Saw media unmount");
                setTtsEngine(mSystemTtsEngine, true);
            }
        }

        if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            if (!TextUtils.equals(mDefaultTtsEngine, mTtsEngine)) {
                // Try to switch back to the default engine.
                LogUtils.log(this, Log.VERBOSE, "Saw media mount");
                setTtsEngine(mDefaultTtsEngine, true);
            }
        }
    }

    private void updateDefaultEngine() {
        final ContentResolver resolver = mContext.getContentResolver();

        // Always refresh the list of available engines, since the user may have
        // installed a new TTS and then switched to it.
        mInstalledTtsEngines.clear();
        mSystemTtsEngine = TextToSpeechUtils.reloadInstalledTtsEngines(
                mContext.getPackageManager(), mInstalledTtsEngines);

        // This may be null if the user hasn't specified an engine.
        mDefaultTtsEngine = Secure.getString(resolver, Secure.TTS_DEFAULT_SYNTH);

        // Always switch engines when the system default changes.
        setTtsEngine(mDefaultTtsEngine, true);
    }

    /**
     * Loads the default pitch adjustment from {@link Secure#TTS_DEFAULT_PITCH}.
     * This will take effect during the next call to {@link #trySpeak}.
     */
    private void updateDefaultPitch() {
        mDefaultPitch = (Secure.getInt(mResolver, Secure.TTS_DEFAULT_PITCH, 100) / 100.0f);
    }

    /**
     * Loads the default rate adjustment from {@link Secure#TTS_DEFAULT_RATE}.
     * This will take effect during the next call to {@link #trySpeak}.
     */
    private void updateDefaultRate() {
        mDefaultRate = (Secure.getInt(mResolver, Secure.TTS_DEFAULT_RATE, 100) / 100.0f);
    }

    /** Whether we need to always force locale changes through TTS. */
    private static final boolean FORCE_TTS_LOCALE_CHANGES =
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2);

    /**
     * Preferred locale for fallback language.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private static final Locale PREFERRED_FALLBACK_LOCALE = Locale.US;

    /**
     * The system's default locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private Locale mSystemLocale = Locale.getDefault();

    /**
     * The current engine's default locale. This will be {@code null} if the
     * user never specified a preference.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private Locale mDefaultLocale = null;

    /**
     * Whether we're using a fallback locale because the TTS attempted to use an
     * unsupported locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private boolean mUsingFallbackLocale;

    /**
     * Whether we've ever explicitly set the locale using
     * {@link TextToSpeech#setLanguage}. If so, we'll need to work around a TTS
     * bug and manually update the TTS locale every time the user changes
     * locale-related settings.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private boolean mHasSetLocale;

    /**
     * Helper method that ensures the text-to-speech engine works even when the
     * user is using the Google TTS and has the system set to a non-embedded
     * language.
     * <p>
     * This method should be called on API >= 15 whenever the TTS engine is
     * loaded, the system locale changes, or the default TTS locale changes.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void ensureSupportedLocale() {
        if (needsFallbackLocale()) {
            attemptSetFallbackLanguage();
        } else if (mUsingFallbackLocale || mHasSetLocale || FORCE_TTS_LOCALE_CHANGES) {
            // We might need to restore the system locale. Or, if we've ever
            // explicitly set the locale, we'll need to work around a bug where
            // there's no way to tell the TTS engine to use whatever it thinks
            // the default language should be.
            attemptRestorePreferredLocale();
        }
    }

    /**
     * Returns whether we need to attempt to use a fallback language.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private boolean needsFallbackLocale() {
        // If the user isn't using Google TTS, or if they set a preferred
        // locale, we do not need to check locale support.
        if (!PACKAGE_GOOGLE_TTS.equals(mTtsEngine) || (mDefaultLocale != null)) {
            return false;
        }

        if (mTts == null) {
            return false;
        }

        // Otherwise, the TTS engine will attempt to use the system locale which
        // may not be supported. If the locale is embedded or advertised as
        // available, we're fine.
        final Set<String> features = mTts.getFeatures(mSystemLocale);
        return !(((features != null)
                && features.contains(Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS))
                || !isNotAvailableStatus(mTts.isLanguageAvailable(mSystemLocale)));
    }

    /**
     * Attempts to obtain and set a fallback TTS locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void attemptSetFallbackLanguage() {
        final Locale fallbackLocale = getBestAvailableLocale();
        if (fallbackLocale == null) {
            LogUtils.log(this, Log.ERROR, "Failed to find fallback locale");
            return;
        }

        if (mTts == null) {
            LogUtils.log(this, Log.ERROR, "mTts null when setting fallback locale.");
            return;
        }

        final int status = mTts.setLanguage(fallbackLocale);
        if (isNotAvailableStatus(status)) {
            LogUtils.log(this, Log.ERROR, "Failed to set fallback locale to %s", fallbackLocale);
            return;
        }

        LogUtils.log(this, Log.VERBOSE, "Set fallback locale to %s", fallbackLocale);

        mUsingFallbackLocale = true;
        mHasSetLocale = true;
    }

    /**
     * Attempts to obtain a supported TTS locale with preference given to
     * {@link #PREFERRED_FALLBACK_LOCALE}. The resulting locale may not be
     * optimal for the user, but it will likely be enough to understand what's
     * on the screen.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private Locale getBestAvailableLocale() {
        if (mTts == null) {
            return null;
        }

        // Always attempt to use the preferred locale first.
        if (mTts.isLanguageAvailable(PREFERRED_FALLBACK_LOCALE) >= 0) {
            return PREFERRED_FALLBACK_LOCALE;
        }

        // Since there's no way to query available languages from an engine,
        // we'll need to check every locale supported by the device.
        Locale bestLocale = null;
        int bestScore = -1;

        final Locale[] locales = Locale.getAvailableLocales();
        for (Locale locale : locales) {
            final int status = mTts.isLanguageAvailable(locale);
            if (isNotAvailableStatus(status)) {
                continue;
            }

            final int score = compareLocales(mSystemLocale, locale);
            if (score > bestScore) {
                bestLocale = locale;
                bestScore = score;
            }
        }

        return bestLocale;
    }

    /**
     * Attempts to restore the user's preferred TTS locale, if set. Otherwise
     * attempts to restore the system locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void attemptRestorePreferredLocale() {
        if (mTts == null) {
            return;
        }

        final Locale preferredLocale = (mDefaultLocale != null ? mDefaultLocale : mSystemLocale);
        final int status = mTts.setLanguage(preferredLocale);
        if (isNotAvailableStatus(status)) {
            LogUtils.log(this, Log.ERROR, "Failed to restore TTS locale to %s", preferredLocale);
            return;
        }

        LogUtils.log(this, Log.INFO, "Restored TTS locale to %s", preferredLocale);

        mUsingFallbackLocale = false;
        mHasSetLocale = true;
    }

    /**
     * Handles updating the default locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void updateDefaultLocale() {
        final String defaultLocale = TextToSpeechUtils.getDefaultLocaleForEngine(
                mResolver, mTtsEngine);
        mDefaultLocale = (!TextUtils.isEmpty(defaultLocale)) ? new Locale(defaultLocale) : null;

        // The default locale changed, which may mean we can restore the user's
        // preferred locale.
        ensureSupportedLocale();
    }

    /**
     * Handles updating the system locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void onConfigurationChanged(Configuration newConfig) {
        final Locale newLocale = newConfig.locale;
        if (newLocale.equals(mSystemLocale)) {
            return;
        }

        mSystemLocale = newLocale;

        // The system locale changed, which may mean we need to override the
        // current TTS locale.
        ensureSupportedLocale();
    }

    /**
     * Registers the configuration change callback.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void registerGoogleTtsFixCallbacks() {
        final Uri defaultLocaleUri = Secure.getUriFor(SecureCompatUtils.TTS_DEFAULT_LOCALE);
        mResolver.registerContentObserver(defaultLocaleUri, false, mLocaleObserver);
        mContext.registerComponentCallbacks(mComponentCallbacks);
    }

    /**
     * Unregisters the configuration change callback.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
    private void unregisterGoogleTtsFixCallbacks() {
        mResolver.unregisterContentObserver(mLocaleObserver);
        mContext.unregisterComponentCallbacks(mComponentCallbacks);
    }

    /**
     * Compares a locale against a primary locale. Returns higher values for
     * closer matches. A return value of 3 indicates that the locale is an exact
     * match for the primary locale's language, country, and variant.
     *
     * @param primary The primary locale for comparison.
     * @param other The other locale to compare against the primary locale.
     * @return A value indicating how well the other locale matches the primary
     *         locale. Higher is better.
     */
    private static int compareLocales(Locale primary, Locale other) {
        final String lang = primary.getLanguage();
        if ((lang == null) || !lang.equals(other.getLanguage())) {
            return 0;
        }

        final String country = primary.getCountry();
        if ((country == null) || !country.equals(other.getCountry())) {
            return 1;
        }

        final String variant = primary.getVariant();
        if ((variant == null) || !variant.equals(other.getVariant())) {
            return 2;
        }

        return 3;
    }

    /**
     * Returns {@code true} if the specified status indicates that the language
     * is available.
     *
     * @param status A language availability code, as returned from
     *            {@link TextToSpeech#isLanguageAvailable}.
     * @return {@code true} if the status indicates that the language is
     *         available.
     */
    private static boolean isNotAvailableStatus(int status) {
        return (status != TextToSpeech.LANG_AVAILABLE)
                && (status != TextToSpeech.LANG_COUNTRY_AVAILABLE)
                && (status != TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE);
    }

    private final FailoverTextToSpeech.SpeechHandler mHandler = new SpeechHandler(this);

    /**
     * Handles changes to the default TTS engine.
     */
    private final ContentObserver mSynthObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateDefaultEngine();
        }
    };

    private final ContentObserver mPitchObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateDefaultPitch();
        }
    };

    private final ContentObserver mRateObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateDefaultRate();
        }
    };

    /**
     * Callbacks used to observe changes to the TTS locale.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private final ContentObserver mLocaleObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
            updateDefaultLocale();
        }
    };

    /** Hands utterance completed processing to the main thread. */
    private final OnUtteranceCompletedListener mTtsListener = new OnUtteranceCompletedListener() {
        @Override
        public void onUtteranceCompleted(String utteranceId) {
            LogUtils.log(this, Log.DEBUG, "Received completion for \"%s\"", utteranceId);

            mHandler.onUtteranceCompleted(utteranceId);
        }
    };

    /**
     * When changing TTS engines, switches the active TTS engine when the new
     * engine is initialized.
     */
    private final OnInitListener mTtsChangeListener = new OnInitListener() {
        @Override
        public void onInit(int status) {
            mHandler.onTtsInitialized(status);
        }
    };

    /**
     * Callbacks used to observe configuration changes.
     * <p>
     * Only used on API >= 15 to work around language issues with Google TTS.
     */
    private final ComponentCallbacks mComponentCallbacks = new ComponentCallbacks() {
        @Override
        public void onLowMemory() {
            // Do nothing.
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            FailoverTextToSpeech.this.onConfigurationChanged(newConfig);
        }
    };

    /**
     * {@link BroadcastReceiver} for detecting media mount and unmount.
     */
    private class MediaMountStateMonitor extends BroadcastReceiver {
        private final IntentFilter mMediaIntentFilter;

        public MediaMountStateMonitor() {
            mMediaIntentFilter = new IntentFilter();
            mMediaIntentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            mMediaIntentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            mMediaIntentFilter.addDataScheme("file");
        }

        public IntentFilter getFilter() {
            return mMediaIntentFilter;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            mHandler.onMediaStateChanged(action);
        }
    }

    /** Handler used to return to the main thread from the TTS thread. */
    private static class SpeechHandler extends WeakReferenceHandler<FailoverTextToSpeech> {
        /** Hand-off engine initialized. */
        private static final int MSG_INITIALIZED = 1;

        /** Hand-off utterance completed. */
        private static final int MSG_UTTERANCE_COMPLETED = 2;

        /** Hand-off media state changes. */
        private static final int MSG_MEDIA_STATE_CHANGED = 3;

        public SpeechHandler(FailoverTextToSpeech parent) {
            super(parent);
        }

        @Override
        public void handleMessage(Message msg, FailoverTextToSpeech parent) {
            switch (msg.what) {
                case MSG_INITIALIZED:
                    parent.handleTtsInitialized(msg.arg1);
                    break;
                case MSG_UTTERANCE_COMPLETED:
                    parent.handleUtteranceCompleted((String) msg.obj, true);
                    break;
                case MSG_MEDIA_STATE_CHANGED:
                    parent.handleMediaStateChanged((String) msg.obj);
            }
        }

        public void onTtsInitialized(int status) {
            obtainMessage(MSG_INITIALIZED, status, 0).sendToTarget();
        }

        public void onUtteranceCompleted(String utteranceId) {
            obtainMessage(MSG_UTTERANCE_COMPLETED, utteranceId).sendToTarget();
        }

        public void onMediaStateChanged(String action) {
            obtainMessage(MSG_MEDIA_STATE_CHANGED, action).sendToTarget();
        }
    }

    /**
     * Listener for TTS events.
     */
    public interface FailoverTtsListener {
        /*
         * Called after the class has initialized with a tts engine.
         */
        public void onTtsInitialized(boolean wasSwitchingEngines);

        /*
         * Called after an utterance has completed speaking.
         */
        public void onUtteranceCompleted(String utteranceId, boolean success);
    }
}

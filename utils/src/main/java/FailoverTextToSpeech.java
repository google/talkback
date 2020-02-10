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

package com.google.android.accessibility.utils;

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
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings.Secure;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import com.google.android.accessibility.utils.compat.provider.SettingsCompatUtils.SecureCompatUtils;
import com.google.android.accessibility.utils.compat.speech.tts.TextToSpeechCompatUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Wrapper for {@link TextToSpeech} that handles fail-over when a specific engine does not work.
 *
 * <p>Does <strong>NOT</strong> implement queuing! Every call to {@link #speak} flushes the global
 * speech queue.
 *
 * <p>This wrapper handles the following:
 *
 * <ul>
 *   <li>Fail-over from a failing TTS to a working one
 *   <li>Splitting utterances into &lt;4k character chunks
 *   <li>Switching to the system TTS when media is unmounted
 *   <li>Utterance-specific pitch and rate changes
 *   <li>Pitch and rate changes relative to the user preference
 * </ul>
 */
@SuppressWarnings("deprecation")
public class FailoverTextToSpeech {
  private static final String TAG = "FailoverTextToSpeech";

  /** The package name for the Google TTS engine. */
  private static final String PACKAGE_GOOGLE_TTS = "com.google.android.tts";

  /** Number of times a TTS engine can fail before switching. */
  private static final int MAX_TTS_FAILURES = 3;

  /** Maximum number of TTS error messages to print to the log. */
  private static final int MAX_LOG_MESSAGES = 10;

  /** Class defining constants used for describing speech parameters. */
  public static class SpeechParam {
    /** Float parameter for controlling speech volume. Range is {0 ... 2}. */
    public static final String VOLUME = TextToSpeech.Engine.KEY_PARAM_VOLUME;

    /** Float parameter for controlling speech rate. Range is {0 ... 2}. */
    public static final String RATE = "rate";

    /** Float parameter for controlling speech pitch. Range is {0 ... 2}. */
    public static final String PITCH = "pitch";
  }

  /**
   * Constant to flush speech globally. The constant corresponds to the non-public API {@link
   * TextToSpeech#QUEUE_DESTROY}. To avoid a bug, we always need to use {@link
   * TextToSpeech#QUEUE_FLUSH} before using {@link #SPEECH_FLUSH_ALL} -- on Android version M only.
   */
  private static final int SPEECH_FLUSH_ALL = 2;

  /**
   * What fraction of the volume seekbar corresponds to a doubling of audio volume.
   *
   * <p>During a phone call, TalkBack speech is redirected from STREAM_MUSIC to STREAM_VOICE_CALL,
   * causing an unexpected change in TalkBack speech volume. During a phone call, we reduce the
   * TalkBack speech volume based on the volume difference between STREAM_MUSIC and
   * STREAM_VOICE_CALL. VOLUME_FRAC_PER_DOUBLING controls the amount of volume reduction per
   * difference of STREAM_MUSIC vs STREAM_VOICE_CALL.
   *
   * <p>On nexus 6, volume doubles every 11% volume seekbar step. On samsung s5, volume doubles
   * every 27% volume step. Setting adjustment too aggressively (too low) causes effective volume to
   * go down when call volume is higher -- the call volume seekbar would work in reverse for
   * TalkBack speech. Setting this adjustment too conservatively (too high) causes the original
   * volume jump to continue, though in lesser degree.
   */
  private static final float VOLUME_FRAC_PER_DOUBLING = 0.25f;

  /**
   * {@link BroadcastReceiver} for determining changes in the media state used for switching the TTS
   * engine.
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
  @Nullable private TextToSpeech mTempTts;

  /** The engine loading into the temporary TTS. */
  @Nullable private String mTempTtsEngine;

  /** The rate adjustment specified in {@link android.provider.Settings}. */
  private float mDefaultRate;

  /** The pitch adjustment specified in {@link android.provider.Settings}. */
  private float mDefaultPitch;

  private List<FailoverTtsListener> mListeners = new ArrayList<>();

  /** Wake lock for keeping the device unlocked while reading */
  private PowerManager.WakeLock mWakeLock;

  private final AudioManager mAudioManager;
  private final TelephonyManager mTelephonyManager;

  private boolean mShouldHandleTtsCallbackInMainThread = true;

  /**
   * A buffer of N most recent utterance ids, used to ensure that a recent utterance's completion
   * handler does not unlock a WakeLock used by the currently speaking utterance.
   */
  private LinkedList<String> mRecentUtteranceIds = new LinkedList<>(); // may contain nulls

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

    // connect to system services
    initWakeLock(context);
    mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
  }

  /** Separate function for overriding in unit tests, because WakeLock cannot be mocked. */
  protected void initWakeLock(Context context) {
    mWakeLock =
        ((PowerManager) context.getSystemService(Context.POWER_SERVICE))
            .newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
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
  public @Nullable CharSequence getEngineLabel() {
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
   * Sets whether to handle TTS callback in main thread. If {@code false}, the callback will be
   * handled in TTS thread.
   */
  public void setHandleTtsCallbackInMainThread(boolean shouldHandleInMainThread) {
    mShouldHandleTtsCallbackInMainThread = shouldHandleInMainThread;
  }

  /**
   * Speak the specified text.
   *
   * @param text The text to speak.
   * @param locale Language of the text.
   * @param pitch The pitch adjustment, in the range [0 ... 1].
   * @param rate The rate adjustment, in the range [0 ... 1].
   * @param params The parameters to pass to the text-to-speech engine.
   */
  public void speak(
      CharSequence text,
      @Nullable Locale locale,
      float pitch,
      float rate,
      HashMap<String, String> params,
      int stream,
      float volume,
      boolean preventDeviceSleep) {

    String utteranceId = params.get(Engine.KEY_PARAM_UTTERANCE_ID);
    addRecentUtteranceId(utteranceId);

    // Handle empty text immediately.
    if (TextUtils.isEmpty(text)) {
      mHandler.onUtteranceCompleted(params.get(Engine.KEY_PARAM_UTTERANCE_ID), /* success= */ true);
      return;
    }

    int result;

    volume *= calculateVolumeAdjustment();

    if (preventDeviceSleep && mWakeLock != null && !mWakeLock.isHeld()) {
      mWakeLock.acquire();
    }

    Exception failureException = null;
    try {
      result = trySpeak(text, locale, pitch, rate, params, stream, volume);
    } catch (Exception e) {
      failureException = e;
      result = TextToSpeech.ERROR;
      allowDeviceSleep();
    }

    if (result == TextToSpeech.ERROR) {
      attemptTtsFailover(mTtsEngine);
    }

    if ((result != TextToSpeech.SUCCESS) && params.containsKey(Engine.KEY_PARAM_UTTERANCE_ID)) {
      if (failureException != null) {
        LogUtils.w(TAG, "Failed to speak %s due to an exception", text);
        failureException.printStackTrace();
      } else {
        LogUtils.w(TAG, "Failed to speak %s", text);
      }

      mHandler.onUtteranceCompleted(params.get(Engine.KEY_PARAM_UTTERANCE_ID), /* success= */ true);
    }
  }

  /** Adjust volume if we are in a phone call and speaking with phone audio stream * */
  private float calculateVolumeAdjustment() {
    float multiple = 1.0f;

    // Accessibility services will eventually have their own audio stream, making this
    // adjustment unnecessary.
    if (!BuildVersionUtils.isAtLeastN()) {

      // If we are in a phone call...
      // (Phone call state is often reported late, missing the first utterance.)
      if (mTelephonyManager != null) {
        int callState = mTelephonyManager.getCallState();
        if (callState != TelephonyManager.CALL_STATE_IDLE) {
          // find audio stream volumes
          if (mAudioManager != null) {
            int volumeMusic = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (volumeMusic <= 0) {
              return 0.0f;
            }
            int volumeVoice = mAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
            int maxVolMusic = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int maxVolVoice = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
            float volumeMusicFrac =
                (maxVolMusic <= 0) ? -1.0f : (float) volumeMusic / (float) maxVolMusic;
            float volumeVoiceCallFrac =
                (maxVolVoice <= 0) ? -1.0f : (float) volumeVoice / (float) maxVolVoice;
            // If phone volume is higher than talkback/media volume...
            if (0.0f <= volumeMusicFrac && volumeMusicFrac < volumeVoiceCallFrac) {
              // Reduce effective volume closer to media volume.
              // The UI volume seekbars have an exponential effect on volume,
              // but text-to-speech volume multiple has a linear effect.
              // So take the Nth root of the volume difference to reduce speech
              // volume multiplier exponentially, to match the volume seekbar effect.
              float diff = volumeVoiceCallFrac - volumeMusicFrac;
              float num_doubling_steps = diff / VOLUME_FRAC_PER_DOUBLING;
              multiple = (float) Math.pow(2.0f, -num_doubling_steps);
            }
          }
        }
      }
    }
    return multiple;
  }

  /** Releases the {@link PowerManager.WakeLock} */
  private void allowDeviceSleep() {
    allowDeviceSleep(null);
  }

  private void allowDeviceSleep(@Nullable String completedUtteranceId) {
    if (mWakeLock != null && mWakeLock.isHeld()) {
      boolean isRecent = mRecentUtteranceIds.contains(completedUtteranceId);
      boolean isLast =
          mRecentUtteranceIds.size() > 0
              && mRecentUtteranceIds.getLast().equals(completedUtteranceId);
      if (completedUtteranceId == null || isLast || !isRecent) {
        mWakeLock.release();
      }
    }
  }

  private void addRecentUtteranceId(String utteranceId) {
    mRecentUtteranceIds.add(utteranceId);
    while (mRecentUtteranceIds.size() > 10) {
      mRecentUtteranceIds.remove();
    }
  }

  /** For use in unit tests */
  public List<String> getRecentUtteranceIds() {
    return Collections.unmodifiableList(mRecentUtteranceIds);
  }

  /** Stops speech from all applications. No utterance callbacks will be sent. */
  public void stopAll() {
    try {
      allowDeviceSleep();
      ensureQueueFlush();
      mTts.speak("", SPEECH_FLUSH_ALL, null);
    } catch (Exception e) {
      // Don't care, we're not speaking.
    }
  }

  /** Stops all speech that originated from TalkBack. No utterance callbacks will be sent. */
  public void stopFromTalkBack() {
    try {
      allowDeviceSleep();
      mTts.speak("", TextToSpeech.QUEUE_FLUSH, null);
    } catch (Exception e) {
      // Don't care, we're not speaking.
    }
  }

  /**
   * Unregisters receivers, observers, and shuts down the text-to-speech engine. No calls should be
   * made to this object after calling this method.
   */
  public void shutdown() {
    allowDeviceSleep();
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
   * @param locale language to speak with. Use default language if it's null.
   * @param pitch to speak text in.
   * @param rate to speak text in.
   * @param params to the TTS.
   * @return The result of speaking the specified text.
   */
  private int trySpeak(
      CharSequence text,
      @Nullable Locale locale,
      float pitch,
      float rate,
      HashMap<String, String> params,
      int stream,
      float volume) {
    if (mTts == null) {
      return TextToSpeech.ERROR;
    }

    float effectivePitch = (pitch * mDefaultPitch);
    float effectiveRate = (rate * mDefaultRate);

    String utteranceId = params.get(Engine.KEY_PARAM_UTTERANCE_ID);
    if ((locale != null) && !locale.equals(mLastUtteranceLocale)) {
      if (attemptSetLanguage(locale)) {
        mLastUtteranceLocale = locale;
      }
    } else if ((locale == null) && (mLastUtteranceLocale != null)) {
      ensureSupportedLocale();
      mLastUtteranceLocale = null;
    }
    int result = speak(text, params, utteranceId, effectivePitch, effectiveRate, stream, volume);

    if (result != TextToSpeech.SUCCESS) {
      ensureSupportedLocale();
    }

    LogUtils.d(TAG, "Speak call for %s returned %d", utteranceId, result);
    return result;
  }

  private int speak(
      CharSequence text,
      HashMap<String, String> params,
      String utteranceId,
      float pitch,
      float rate,
      int stream,
      float volume) {
    Bundle bundle = new Bundle();

    if (params != null) {
      for (String key : params.keySet()) {
        bundle.putString(key, params.get(key));
      }
    }

    bundle.putInt(SpeechParam.PITCH, (int) (pitch * 100));
    bundle.putInt(SpeechParam.RATE, (int) (rate * 100));
    bundle.putInt(Engine.KEY_PARAM_STREAM, stream);
    bundle.putFloat(SpeechParam.VOLUME, volume);

    ensureQueueFlush();
    return mTts.speak(text, SPEECH_FLUSH_ALL, bundle, utteranceId);
  }

  /**
   * Flushes the TextToSpeech queue for fast speech queueing, needed only on Android M. See bug
   * 
   */
  private void ensureQueueFlush() {
    if (BuildVersionUtils.isM()) {
      mTts.speak("", TextToSpeech.QUEUE_FLUSH, null, null);
    }
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
      LogUtils.e(TAG, "Can't start TTS engine %s while still loading previous engine", engine);
      return;
    }

    LogUtils.logWithLimit(
        TAG, Log.INFO, mTtsFailures, MAX_LOG_MESSAGES, "Switching to TTS engine: %s", engine);

    mTempTtsEngine = engine;
    mTempTts = new TextToSpeech(mContext, mTtsChangeListener, engine);
  }

  /**
   * Assumes the current engine has failed and attempts to start the next available engine.
   *
   * @param failedEngine The package name of the engine to switch from.
   */
  private void attemptTtsFailover(String failedEngine) {
    LogUtils.logWithLimit(
        TAG,
        Log.ERROR,
        mTtsFailures,
        MAX_LOG_MESSAGES,
        "Attempting TTS failover from %s",
        failedEngine);

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
      LogUtils.e(TAG, "Attempted to initialize TTS more than once!");
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
    mTts.setOnUtteranceProgressListener(mUtteranceProgressListener);

    if (tempTtsEngine == null) {
      mTtsEngine = TextToSpeechCompatUtils.getCurrentEngine(mTts);
    } else {
      mTtsEngine = tempTtsEngine;
    }

    updateDefaultLocale();

    mTts.setAudioAttributes(
        new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .build());

    LogUtils.i(TAG, "Switched to TTS engine: %s", tempTtsEngine);

    for (FailoverTtsListener mListener : mListeners) {
      mListener.onTtsInitialized(isSwitchingEngines);
    }
  }

  /**
   * Method that's called by TTS whenever an utterance starts.
   *
   * @param utteranceId The utteranceId from the onUtteranceStarted callback - we expect this to
   *     consist of UTTERANCE_ID_PREFIX followed by the utterance index.
   */
  private void handleUtteranceStarted(String utteranceId) {
    for (FailoverTtsListener mListener : mListeners) {
      mListener.onUtteranceStarted(utteranceId);
    }
  }

  /**
   * Method that's called by TTS to update the range of utterance being spoken.
   *
   * @param utteranceId The utteranceId from the onUtteranceStarted callback - we expect this to
   *     consist of UTTERANCE_ID_PREFIX followed by the utterance index.
   * @param start The start index of the range in the utterance text.
   * @param end The end index of the range in the utterance text.
   */
  private void handleUtteranceRangeStarted(String utteranceId, int start, int end) {
    for (FailoverTtsListener mListener : mListeners) {
      mListener.onUtteranceRangeStarted(utteranceId, start, end);
    }
  }

  /**
   * Method that's called by TTS whenever an utterance is completed. Do common tasks and execute any
   * UtteranceCompleteActions associate with this utterance index (or an earlier index, in case one
   * was accidentally dropped).
   *
   * @param utteranceId The utteranceId from the onUtteranceCompleted callback - we expect this to
   *     consist of UTTERANCE_ID_PREFIX followed by the utterance index.
   * @param success {@code true} if the utterance was spoken successfully.
   */
  private void handleUtteranceCompleted(String utteranceId, boolean success) {
    if (success) {
      mTtsFailures = 0;
    }
    allowDeviceSleep(utteranceId);
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
        LogUtils.v(TAG, "Saw media unmount");
        setTtsEngine(mSystemTtsEngine, true);
      }
    }

    if (Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
      if (!TextUtils.equals(mDefaultTtsEngine, mTtsEngine)) {
        // Try to switch back to the default engine.
        LogUtils.v(TAG, "Saw media mount");
        setTtsEngine(mDefaultTtsEngine, true);
      }
    }
  }

  public void updateDefaultEngine() {
    final ContentResolver resolver = mContext.getContentResolver();

    // Always refresh the list of available engines, since the user may have
    // installed a new TTS and then switched to it.
    mInstalledTtsEngines.clear();
    mSystemTtsEngine =
        TextToSpeechUtils.reloadInstalledTtsEngines(
            mContext.getPackageManager(), mInstalledTtsEngines);

    // This may be null if the user hasn't specified an engine.
    mDefaultTtsEngine = Secure.getString(resolver, Secure.TTS_DEFAULT_SYNTH);

    // Switch engines when the system default changes and it's not the current engine.
    if (mTtsEngine == null || !mTtsEngine.equals(mDefaultTtsEngine)) {
      if (mInstalledTtsEngines.contains(mDefaultTtsEngine)) {
        // Can load the default engine.
        setTtsEngine(mDefaultTtsEngine, true);
      } else if (!mInstalledTtsEngines.isEmpty()) {
        // We'll take whatever TTS we can get.
        setTtsEngine(mInstalledTtsEngines.get(0), true);
      }
    }
  }

  /**
   * Loads the default pitch adjustment from {@link Secure#TTS_DEFAULT_PITCH}. This will take effect
   * during the next call to {@link #trySpeak}.
   */
  private void updateDefaultPitch() {
    mDefaultPitch = (Secure.getInt(mResolver, Secure.TTS_DEFAULT_PITCH, 100) / 100.0f);
  }

  /**
   * Loads the default rate adjustment from {@link Secure#TTS_DEFAULT_RATE}. This will take effect
   * during the next call to {@link #trySpeak}.
   */
  private void updateDefaultRate() {
    mDefaultRate = (Secure.getInt(mResolver, Secure.TTS_DEFAULT_RATE, 100) / 100.0f);
  }

  /** Preferred locale for fallback language. */
  private static final Locale PREFERRED_FALLBACK_LOCALE = Locale.US;

  /** The system's default locale. */
  private Locale mSystemLocale = Locale.getDefault();

  /**
   * The current engine's default locale. This will be {@code null} if the user never specified a
   * preference.
   */
  @Nullable private Locale mDefaultLocale = null;

  /**
   * The locale specified by the last utterance with {@link #speak(CharSequence, Locale, float,
   * float, HashMap, int, float, boolean)}.
   */
  @Nullable private Locale mLastUtteranceLocale = null;

  /**
   * Helper method that ensures the text-to-speech engine works even when the user is using the
   * Google TTS and has the system set to a non-embedded language.
   *
   * <p>This method should be called whenever the TTS engine is loaded, the system locale changes,
   * or the default TTS locale changes.
   */
  private void ensureSupportedLocale() {
    if (needsFallbackLocale()) {
      attemptSetFallbackLanguage();
    } else {
      // We might need to restore the system locale. Or, if we've ever
      // explicitly set the locale, we'll need to work around a bug where
      // there's no way to tell the TTS engine to use whatever it thinks
      // the default language should be.
      attemptRestorePreferredLocale();
    }
  }

  /** Returns whether we need to attempt to use a fallback language. */
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
    return !(((features != null) && features.contains(Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS))
        || !isNotAvailableStatus(mTts.isLanguageAvailable(mSystemLocale)));
  }

  /** Attempts to obtain and set a fallback TTS locale. */
  private void attemptSetFallbackLanguage() {
    final Locale fallbackLocale = getBestAvailableLocale();
    if (fallbackLocale == null) {
      LogUtils.e(TAG, "Failed to find fallback locale");
      return;
    }
    LogUtils.v(TAG, "Attempt setting fallback TTS locale.");
    attemptSetLanguage(fallbackLocale);
  }

  /**
   * Attempts to set a TTS locale.
   *
   * @param locale TTS locale to set.
   * @return {@code true} if successfully set the TTS locale.
   */
  private boolean attemptSetLanguage(Locale locale) {
    if (locale == null) {
      LogUtils.w(TAG, "Cannot set null locale.");
      return false;
    }
    if (mTts == null) {
      LogUtils.e(TAG, "mTts null when setting locale.");
      return false;
    }

    final int status = mTts.setLanguage(locale);
    if (isNotAvailableStatus(status)) {
      LogUtils.e(TAG, "Failed to set locale to %s", locale);
      return false;
    }

    LogUtils.v(TAG, "Set locale to %s", locale);
    return true;
  }

  /**
   * Attempts to obtain a supported TTS locale with preference given to {@link
   * #PREFERRED_FALLBACK_LOCALE}. The resulting locale may not be optimal for the user, but it will
   * likely be enough to understand what's on the screen.
   */
  private @Nullable Locale getBestAvailableLocale() {
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
   * Attempts to restore the user's preferred TTS locale, if set. Otherwise attempts to restore the
   * system locale.
   */
  private void attemptRestorePreferredLocale() {
    if (mTts == null) {
      return;
    }
    mLastUtteranceLocale = null;
    final Locale preferredLocale = (mDefaultLocale != null ? mDefaultLocale : mSystemLocale);
    try {
      final int status = mTts.setLanguage(preferredLocale);
      if (!isNotAvailableStatus(status)) {
        LogUtils.i(TAG, "Restored TTS locale to %s", preferredLocale);
        return;
      }
    } catch (Exception e) {
      LogUtils.e(TAG, "Failed to setLanguage(): %s", e.toString());
    }

    LogUtils.e(TAG, "Failed to restore TTS locale to %s", preferredLocale);
  }

  /** Handles updating the default locale. */
  private void updateDefaultLocale() {
    final String defaultLocale = TextToSpeechUtils.getDefaultLocaleForEngine(mResolver, mTtsEngine);
    mDefaultLocale = (!TextUtils.isEmpty(defaultLocale)) ? new Locale(defaultLocale) : null;

    // The default locale changed, which may mean we can restore the user's
    // preferred locale.
    ensureSupportedLocale();
  }

  /** Handles updating the system locale. */
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

  /** Registers the configuration change callback. */
  private void registerGoogleTtsFixCallbacks() {
    final Uri defaultLocaleUri = Secure.getUriFor(SecureCompatUtils.TTS_DEFAULT_LOCALE);
    mResolver.registerContentObserver(defaultLocaleUri, false, mLocaleObserver);
    mContext.registerComponentCallbacks(mComponentCallbacks);
  }

  /** Unregisters the configuration change callback. */
  private void unregisterGoogleTtsFixCallbacks() {
    mResolver.unregisterContentObserver(mLocaleObserver);
    mContext.unregisterComponentCallbacks(mComponentCallbacks);
  }

  /**
   * Compares a locale against a primary locale. Returns higher values for closer matches. A return
   * value of 3 indicates that the locale is an exact match for the primary locale's language,
   * country, and variant.
   *
   * @param primary The primary locale for comparison.
   * @param other The other locale to compare against the primary locale.
   * @return A value indicating how well the other locale matches the primary locale. Higher is
   *     better.
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
   * Returns {@code true} if the specified status indicates that the language is available.
   *
   * @param status A language availability code, as returned from {@link
   *     TextToSpeech#isLanguageAvailable}.
   * @return {@code true} if the status indicates that the language is available.
   */
  private static boolean isNotAvailableStatus(int status) {
    return (status != TextToSpeech.LANG_AVAILABLE)
        && (status != TextToSpeech.LANG_COUNTRY_AVAILABLE)
        && (status != TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE);
  }

  private final FailoverTextToSpeech.SpeechHandler mHandler = new SpeechHandler(this);

  /** Handles changes to the default TTS engine. */
  private final ContentObserver mSynthObserver =
      new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
          updateDefaultEngine();
        }
      };

  private final ContentObserver mPitchObserver =
      new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
          updateDefaultPitch();
        }
      };

  private final ContentObserver mRateObserver =
      new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
          updateDefaultRate();
        }
      };

  /** Callbacks used to observe changes to the TTS locale. */
  private final ContentObserver mLocaleObserver =
      new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange) {
          updateDefaultLocale();
        }
      };

  /**
   * A listener for {@code TextToSpeech} progress.
   *
   * <p><strong>Note: </strong> By default, the callback is invoked in TTS thread and we hand over
   * the message to main thread for processing. In some special cases when we want to handle the
   * callback in TTS thread, call {@link #setHandleTtsCallbackInMainThread(boolean)}.
   */
  private final UtteranceProgressListener mUtteranceProgressListener =
      new UtteranceProgressListener() {
        @Nullable private String mLastUpdatedUtteranceId = null;

        private void updatePerformanceMetrics(String utteranceId) {
          // Update performance for this utterance, only if we did not recently update
          // for the same utterance.
          if (utteranceId != null && !utteranceId.equals(mLastUpdatedUtteranceId)) {
            Performance.getInstance().onFeedbackOutput(utteranceId);
          }
          mLastUpdatedUtteranceId = utteranceId;
        }

        private void handleUtteranceCompleted(String utteranceId, boolean success) {
          LogUtils.d(TAG, "Received callback for \"%s\"", utteranceId);
          if (mShouldHandleTtsCallbackInMainThread) {
            // Hand utterance completed processing to the main thread.
            mHandler.onUtteranceCompleted(utteranceId, success);
          } else {
            FailoverTextToSpeech.this.handleUtteranceCompleted(utteranceId, success);
          }
        }

        @Override
        public void onStart(String utteranceId) {
          if (mShouldHandleTtsCallbackInMainThread) {
            mHandler.onUtteranceStarted(utteranceId);
          } else {
            FailoverTextToSpeech.this.handleUtteranceStarted(utteranceId);
          }
        }

        @TargetApi(Build.VERSION_CODES.N) // This callback will only be called on N+.
        @Override
        public void onAudioAvailable(String utteranceId, byte[] audio) {
          // onAudioAvailable() is usually called many times per utterance,
          // once for each audio chunk.
          updatePerformanceMetrics(utteranceId);
        }

        @TargetApi(Build.VERSION_CODES.O)
        @Override
        public void onRangeStart(String utteranceId, int start, int end, int frame) {
          if (mShouldHandleTtsCallbackInMainThread) {
            mHandler.onUtteranceRangeStarted(utteranceId, start, end);
          } else {
            FailoverTextToSpeech.this.handleUtteranceRangeStarted(utteranceId, start, end);
          }
        }

        @Override
        public void onStop(String utteranceId, boolean interrupted) {
          handleUtteranceCompleted(utteranceId, /* success= */ !interrupted);
        }

        @Override
        public void onError(String utteranceId) {
          handleUtteranceCompleted(utteranceId, /* success= */ false);
        }

        @Override
        public void onDone(String utteranceId) {
          handleUtteranceCompleted(utteranceId, /* success= */ true);
        }
      };

  /**
   * When changing TTS engines, switches the active TTS engine when the new engine is initialized.
   */
  private final OnInitListener mTtsChangeListener =
      new OnInitListener() {
        @Override
        public void onInit(int status) {
          mHandler.onTtsInitialized(status);
        }
      };

  /** Callbacks used to observe configuration changes. */
  private final ComponentCallbacks mComponentCallbacks =
      new ComponentCallbacks() {
        @Override
        public void onLowMemory() {
          // Do nothing.
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
          FailoverTextToSpeech.this.onConfigurationChanged(newConfig);
        }
      };

  /** {@link BroadcastReceiver} for detecting media mount and unmount. */
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

    /** Hand-off utterance started. */
    private static final int MSG_UTTERANCE_STARTED = 2;

    /** Hand-off utterance completed. */
    private static final int MSG_UTTERANCE_COMPLETED = 3;

    /** Hand-off media state changes. */
    private static final int MSG_MEDIA_STATE_CHANGED = 4;

    /** Hand-off a range of utterance started. */
    private static final int MSG_UTTERANCE_RANGE_STARTED = 5;

    public SpeechHandler(FailoverTextToSpeech parent) {
      super(parent);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleMessage(Message msg, FailoverTextToSpeech parent) {
      switch (msg.what) {
        case MSG_INITIALIZED:
          parent.handleTtsInitialized(msg.arg1);
          break;
        case MSG_UTTERANCE_STARTED:
          parent.handleUtteranceStarted((String) msg.obj);
          break;
        case MSG_UTTERANCE_COMPLETED:
          Pair<String, Boolean> data = (Pair<String, Boolean>) msg.obj;
          parent.handleUtteranceCompleted(
              /* utteranceId= */ data.first, /* success= */ data.second);
          break;
        case MSG_MEDIA_STATE_CHANGED:
          parent.handleMediaStateChanged((String) msg.obj);
          break;
        case MSG_UTTERANCE_RANGE_STARTED:
          parent.handleUtteranceRangeStarted((String) msg.obj, msg.arg1, msg.arg2);
          break;
        default: // fall out
      }
    }

    public void onTtsInitialized(int status) {
      obtainMessage(MSG_INITIALIZED, status, 0).sendToTarget();
    }

    public void onUtteranceStarted(String utteranceId) {
      obtainMessage(MSG_UTTERANCE_STARTED, utteranceId).sendToTarget();
    }

    public void onUtteranceRangeStarted(String utteranceId, int start, int end) {
      obtainMessage(MSG_UTTERANCE_RANGE_STARTED, start, end, utteranceId).sendToTarget();
    }

    public void onUtteranceCompleted(String utteranceId, boolean success) {
      obtainMessage(MSG_UTTERANCE_COMPLETED, Pair.create(utteranceId, success)).sendToTarget();
    }

    public void onMediaStateChanged(String action) {
      obtainMessage(MSG_MEDIA_STATE_CHANGED, action).sendToTarget();
    }
  }

  /** Listener for TTS events. */
  public interface FailoverTtsListener {
    /*
     * Called after the class has initialized with a tts engine.
     */
    void onTtsInitialized(boolean wasSwitchingEngines);

    /*
     * Called before an utterance starts speaking.
     */
    void onUtteranceStarted(String utteranceId);

    /*
     * .Called before speaking the range of an utterance.
     */
    void onUtteranceRangeStarted(String utteranceId, int start, int end);

    /*
     * Called after an utterance has completed speaking.
     */
    void onUtteranceCompleted(String utteranceId, boolean success);
  }
}

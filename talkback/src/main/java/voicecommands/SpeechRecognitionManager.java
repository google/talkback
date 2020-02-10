/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.accessibility.talkback.voicecommands;

import static android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS;
import static android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY;
import static android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.controller.GestureController;
import com.google.android.accessibility.utils.DelayHandler;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.widget.DialogUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Starts and ends the voice recognition for Talkback. For more information on implementation check
 * 
 */
public class SpeechRecognitionManager {

  private static final String TAG = "SpeechRecognitionManager";

  public static final String ACTION_DONE = "done";
  public static final String ACTION_REJECTED = "rejected";
  public String language;
  private final Context talkbackContext;
  @Nullable private AlertDialog helpDialog;
  private final SpeechRecognitionDialog speechRecognitionDialog;
  private static final int TURN_OFF_RECOGNITION_DELAY_MS = 10000;
  private static final int SPEECH_DELAY_MS = 100;
  private boolean recognizerProducesFinalResults = false;

  /** Wait up to a second between command words, before executing command. */
  private static final int PARTIAL_SPEECH_COMMAND_PROCESS_DELAY_MS = 1000;

  /** Wait less time when the speech is known to be final. */
  private static final int FINAL_SPEECH_COMMAND_PROCESS_DELAY_MS = 250;

  /** Wait between/after command words, before executing command. */
  private final Handler executeCommandDelayHandler = new Handler();

  private final DelayHandler<Object> stopListeningDelayHandler =
      new DelayHandler<Object>() {
        @Override
        public void handle(Object arg) {
          timeOut();
        }
      };
  private final Pipeline.FeedbackReturner pipeline;

  private final BroadcastReceiver receiver =
      new BroadcastReceiver() {
        /** Broadcast to start speech recognition if the user accepts. */
        @Override
        public void onReceive(Context context, Intent intent) {
          context.unregisterReceiver(receiver);
          String action = intent.getAction();
          // If the user accepted.
          if (ACTION_DONE.equals(action)) {
            hasMicPermission = true;
            startListening();
          } else {
            Toast.makeText(
                    context,
                    context.getString(R.string.voice_commands_no_mic_permissions),
                    Toast.LENGTH_LONG)
                .show();
          }
        }
      };
  private boolean listening = false;
  private boolean hasMicPermission = false;
  @Nullable public SpeechRecognizer speechRecognizer;
  @Nullable public Intent recognizerIntent;
  private GestureController gestureController;
  public RecognitionListener speechRecognitionListener =
      new RecognitionListener() {
        @Override
        public void onBeginningOfSpeech() {
          // Nothing to do.
        }

        @Override
        public void onBufferReceived(byte[] buffer) {
          // Nothing to do.
        }

        /** Turns the mic off after the user has stopped speaking. */
        @Override
        public void onEndOfSpeech() {
          listening = false;
          stopListeningDelayHandler.removeMessages();
          speechRecognizer.stopListening();
        }

        /** If there is an error, alerts the user. */
        @Override
        public void onError(int error) {
          LogUtils.v(TAG, "Speech recognizer onError() error=%d", error);
          listening = false;
          stopListeningDelayHandler.removeMessages();
          // Note: This will only occur if user turned off mic permissions after initial use.
          if (error == ERROR_INSUFFICIENT_PERMISSIONS) {
            speakDelayed(talkbackContext, R.string.voice_commands_no_mic_permissions);
            hasMicPermission = false;
          } else if (error == ERROR_RECOGNIZER_BUSY) {
            // Backup case: This should not happen.
            speechRecognizer.stopListening();
            speakDelayed(talkbackContext, R.string.voice_commands_many_requests);
          } else if (error == ERROR_SPEECH_TIMEOUT) {
            // Nothing heard.
            speakDelayed(
                talkbackContext.getString(
                    R.string.voice_commands_partial_result,
                    talkbackContext.getString(R.string.title_pref_help)));
          } else {
            speakDelayed(talkbackContext, R.string.voice_commands_error);
          }
          reset();
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
          // Nothing to do.
        }

        /** Speech recognition did not fully understand. */
        @Override
        public void onPartialResults(Bundle partialResults) {
          LogUtils.v(TAG, "Speech recognizer onPartialResults()");
          // For watches SpeechRecognizer returns partial results, but the string is recognized
          // correctly and that is enough for Talkback to process voice commands.
          // Hence we try to handle partial results to improve the performance.
          handleResult(
              partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
              /* isPartialResult= */ true);
        }

        @Override
        public void onReadyForSpeech(Bundle params) {
          // Nothing to do.
        }

        /** Gets the results from SpeechRecognizer and converts to a string. */
        @Override
        public void onResults(Bundle results) {
          LogUtils.v(TAG, "Speech recognizer onResults()");
          handleResult(
              results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
              /* isPartialResult= */ false);
        }

        @Override
        public void onRmsChanged(float rmsdB) {
          // Nothing to do.
        }
      };

  /**
   * Handles the result recognized by speech recognizer and sends that over to gesture controller.
   * Called by onPartialResults(), which generates a whole sequence of incomplete results, that need
   * to be de-duplicated. Also called by onResults() never, once, or possibly more than once, also
   * requiring de-duplication. So handleResult() delays responding to un/recognized commands, to
   * allow newer results to replace older results.
   *
   * @param result A series of speech strings recognized.
   * @param isPartialResult Does the result come from onResults() or onPartialResults()
   */
  private void handleResult(List<String> result, boolean isPartialResult) {

    // Refresh auto-shut-off timer.
    stopListeningDelayHandler.removeMessages();
    stopListeningDelayHandler.delay(TURN_OFF_RECOGNITION_DELAY_MS, null);

    // Record whether the internal recognizer can produce final (non-partial) results.
    if (!isPartialResult) {
      recognizerProducesFinalResults = true;
    }

    // If a final-result is expected, discard the partial-result.
    if (recognizerProducesFinalResults && isPartialResult) {
      return;
    }

    LogUtils.v(
        TAG,
        "Speech recognized %s: %s",
        (isPartialResult ? "partial" : "final"),
        (result == null) ? "null" : String.format("\"%s\"", TextUtils.join("\" \"", result)));

    if (!isPartialResult) {
      listening = false;
      stopListeningDelayHandler.removeMessages();
      reset();
    }

    // Cancel commands from overlapping partial/results.
    executeCommandDelayHandler.removeCallbacksAndMessages(null);

    final String command = (result == null || result.size() == 0) ? null : result.get(0);
    // SpeechRecognizer after recognizing the speech is triggering onPlaybackConfigChanged
    // in AudioPlaybackMonitor for config USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, which is
    // activating VoiceActionMonitor#AudioPlaybackStateChangedListener() and interrupting
    // all talkback feedback. This delay would help avoid the interruption while processing
    // voice commands in GestureController.
    // Although AudioPlayback is only available from O, adding this delay for all versions,
    // helps to make () difficult to reproduce.
    // This delay also de-duplicates commands from partial-recognition results.
    // Wait longer if final-result is unknown, because more partial-results may be coming.
    long commandDelayMs =
        recognizerProducesFinalResults
            ? FINAL_SPEECH_COMMAND_PROCESS_DELAY_MS
            : PARTIAL_SPEECH_COMMAND_PROCESS_DELAY_MS;
    if (!TextUtils.isEmpty(command)) {
      executeCommandDelayHandler.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              if (gestureController.handleSpeechCommand(command.toLowerCase())) {
                reset();
              }
            }
          },
          commandDelayMs);
    }
  }

  /** Constructor to initialize variables needed from GestureController. */
  public SpeechRecognitionManager(Context context, Pipeline.FeedbackReturner pipeline) {
    talkbackContext = context;
    this.pipeline = pipeline;
    speechRecognitionDialog = new SpeechRecognitionDialog((TalkBackService) context, this);
  }

  public void setGestureController(GestureController gestureController) {
    this.gestureController = gestureController;
  }

  public boolean isListening() {
    return listening;
  }

  /** Looks to see if the appropriate mic permissions are given for voice commands. */
  public void getSpeechPermissions() {
    // If the user does not have mic permissions.
    if (ContextCompat.checkSelfPermission(talkbackContext, Manifest.permission.RECORD_AUDIO)
        != PackageManager.PERMISSION_GRANTED) {
      getMicPermission();
    } else {
      startListening();
    }
  }

  /**
   * Starts speech recognition. Constraints include that speechRecognizer, recognizerIntent &
   * speechRecognitionListener must not be null.
   */
  public void startListening() {
    startListening(/* checkDialog= */ true);
  }
  /**
   * @param checkDialog If the dialog is dismissed and call startListening() again, it needs to
   *     ignore checking dialog display preference, or the dialog would show again.
   */
  public void startListening(boolean checkDialog) {
    if (checkDialog && speechRecognitionDialog.getShouldShowDialogPref()) {
      speechRecognitionDialog.showDialog();
      return;
    }

    if (speechRecognizer == null) {
      createSpeechObjects();
    }
    listening = true;
    speechRecognizer.startListening(recognizerIntent);
    stopListeningDelayHandler.delay(TURN_OFF_RECOGNITION_DELAY_MS, null);
  }

  /**
   * Stops speech recognition. Constraints include that speechRecognizer, recognizerIntent &
   * speechRecognitionListener must not be null.
   */
  public void stopListening() {
    if (speechRecognizer != null && recognizerIntent != null && speechRecognitionListener != null) {
      listening = false;
      speechRecognizer.stopListening();
      stopListeningDelayHandler.removeMessages();
    }
  }

  public void reset() {
    if (speechRecognizer != null) {
      speechRecognizer.setRecognitionListener(null);
      speechRecognizer.cancel();
      speechRecognizer.destroy();
      speechRecognizer = null;
    }
    listening = false;
    recognizerIntent = null;
  }

  /** Calls method to create speech recognizer, recognition intent and speechRecognitionListener. */
  private void createSpeechObjects() {
    createSpeechRecognizer();
    createRecogIntent();
    setSpeechRecognitionListener();
  }

  /** Creates a speech recognizer & checks if the user has voice recognition ability. */
  private void createSpeechRecognizer() {
    // Checks if user can use voice recognition.
    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(talkbackContext);
    if (!SpeechRecognizer.isRecognitionAvailable(talkbackContext)) {
      Toast.makeText(
              talkbackContext,
              talkbackContext.getString(R.string.voice_commands_no_voice_recognition_ability),
              Toast.LENGTH_SHORT)
          .show();
      return;
    }
  }

  /** Create and initialize the recognition intent. */
  private void createRecogIntent() {
    // Works without wifi, but provides many extra partial results. Respects the system language.
    recognizerIntent =
        new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
  }

  /** Creates RecognitionListener and connects recognition listener to speech recognizer. */
  private void setSpeechRecognitionListener() {
    // Note: Need to setRecognitionListener before you can start listening to anything.
    speechRecognizer.setRecognitionListener(speechRecognitionListener);
  }

  /** Calls activity that asks user for mic access for talkback. */
  private void getMicPermission() {
    // Creates the intent needed for the mic permission activity.
    Intent intent = new Intent(talkbackContext, SpeechRecognitionMicActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
    // Creates an intent filter for broadcast receiver.
    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_REJECTED);
    filter.addAction(ACTION_DONE);
    talkbackContext.registerReceiver(receiver, filter);
    // Start activity.
    talkbackContext.startActivity(intent);
  }

  /** Activated when SpeechRecognizer has not stopped listening after 10 seconds. */
  private void timeOut() {
    listening = false;
    stopListening();
    speakDelayed(
        talkbackContext.getString(
            R.string.voice_commands_partial_result,
            talkbackContext.getString(R.string.title_pref_help)));
    reset();
  }

  public void showCommandsList(Context context) {
    // Close the dialog if its already open.
    if (helpDialog != null && helpDialog.isShowing()) {
      helpDialog.dismiss();
      helpDialog = null;
    }
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setTitle(context.getString(R.string.voice_commands_possible_options_title));
    builder.setNegativeButton(
        context.getString(R.string.title_cancel_button),
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int id) {
            dialog.cancel();
          }
        });

    Resources res = context.getResources();
    String[] commands = res.getStringArray(R.array.voice_commands);
    // TODO: Instead of string-array, use a layout so that we can include headings.
    ArrayAdapter<String> adapter =
        new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, commands) {
          @Override
          public boolean isEnabled(int position) {
            return false;
          }
        };
    ListView listView = new ListView(context);
    listView.setAdapter(adapter);
    helpDialog = builder.create();
    helpDialog.setView(listView);

    // Without setting a window type, alert dialog shows an error.
    DialogUtils.setWindowTypeToDialog(helpDialog.getWindow());
    helpDialog.show();
  }

  // TODO: Remove this once the bug is resolved.
  /** Speak into the voice-commands speech queue. Used internally and by GestureController. */
  public void speakDelayed(Context context, int stringResourceId) {
    speakDelayed(context.getString(stringResourceId));
  }

  public void speakDelayed(String text) {
    SpeakOptions speakOptions =
        SpeakOptions.create()
            .setFlags(
                FeedbackItem.FLAG_NO_HISTORY
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                    | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE);
    pipeline.returnFeedback(
        // TODO: Add performance EventId support for speech commands.
        EVENT_ID_UNTRACKED, Feedback.speech(text, speakOptions).setDelayMs(SPEECH_DELAY_MS));
  }

  public boolean hasMicPermission() {
    return hasMicPermission;
  }
}

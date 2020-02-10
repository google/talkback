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

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;

/** Activity proxy to create a dialog asking for microphone permissions */
public class SpeechRecognitionMicActivity extends Activity {

  /** Create the activity screen to ask for mic permissions */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  /** Everytime this is activated, ask for mic permission */
  @Override
  public void onResume() {
    super.onResume();
    String[] sList = {Manifest.permission.RECORD_AUDIO};
    ActivityCompat.requestPermissions(this, sList, 1);
  }

  /** Depending on the response, send a broadcast intent either accepting or rejecting */
  @Override
  public void onRequestPermissionsResult(
      int requestCode, String[] permissions, int[] grantResults) {
    Intent broadcastIntent = new Intent();
    String action;
    // user accepted
    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
      action = SpeechRecognitionManager.ACTION_DONE;
    } else {
      action = SpeechRecognitionManager.ACTION_REJECTED;
    }
    // finish this activity
    finish();
    // send the broadcast
    broadcastIntent.setAction(action);
    sendBroadcast(broadcastIntent);
  }
}

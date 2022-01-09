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

package com.google.android.accessibility.brailleime.input;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.BrailleImeLog;
import com.google.android.accessibility.brailleime.BrailleWord;
import com.google.android.accessibility.brailleime.Utils;
import com.google.common.base.Splitter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Properties;

/**
 * Reads number-encoded braille from file and turns it into {@link BrailleInputPlaneResult}.
 *
 * <p>The file is read from getExternalFilesDir() + "/braillekeyboard_autoperform.txt".
 *
 * <p>The file must be in the .properties format. See https://en.wikipedia.org/wiki/.properties.
 *
 * <p>Example file:
 * /sdcard/Android/data/com.google.android.marvin.talkback/files/braillekeyboard_autoperform.txt
 *
 * <pre>
 * braille=6-5-2346 356 1345-135 1235-135-135-134 123456 1346-256
 * interCharacterDelay=300
 * interWordDelay=500
 * initialDelay=3000
 * </pre>
 */
public class AutoPerformer extends Handler {

  private static final String TAG = "AutoPerformer";

  private static final int DEFAULT_INTER_CHARACTER_DELAY = 500;
  private static final int DEFAULT_INTER_WORD_DELAY = 1000;
  private static final int DEFAULT_INITIAL_DELAY = 1000;

  private final Context context;
  private final Callback callback;

  private ArrayDeque<BrailleCharacter> characters;
  private int interCharacterDelay;
  private int interWordDelay;

  interface Callback {
    void onPerform(BrailleInputPlaneResult bipr);

    BrailleInputPlaneResult createSwipe(Swipe swipe);

    void onFinish();
  }

  AutoPerformer(Context context, Callback callback) {
    this.context = context;
    this.callback = callback;
  }

  void start() {
    BrailleImeLog.logD(TAG, "autoperform begin");
    File file = new File(context.getExternalFilesDir(null), "braillekeyboard_autoperform.txt");
    if (file.exists()) {
      try {
        FileReader fileReader = new FileReader(file);
        Properties props = new Properties();
        props.load(fileReader);
        fileReader.close();
        scheduleAutoPerform(props);
      } catch (IOException e) {
        BrailleImeLog.logE(TAG, "autoperform read failure: " + e.getMessage());
      }
    } else {
      BrailleImeLog.logD(TAG, "autoperform file not found at: " + file.getAbsolutePath());
    }
  }

  private void scheduleAutoPerform(Properties props) {
    String spaceDelimitedLine = props.getProperty("braille");
    if (TextUtils.isEmpty(spaceDelimitedLine)) {
      return;
    }
    interCharacterDelay =
        Utils.parseIntWithDefault(
            props.getProperty("interCharacterDelay"), DEFAULT_INTER_CHARACTER_DELAY);
    interWordDelay =
        Utils.parseIntWithDefault(props.getProperty("interWordDelay"), DEFAULT_INTER_WORD_DELAY);
    int initialDelay =
        Utils.parseIntWithDefault(props.getProperty("initialDelay"), DEFAULT_INITIAL_DELAY);
    characters = new ArrayDeque<>();
    for (String dashDelimited : Splitter.on(' ').omitEmptyStrings().split(spaceDelimitedLine)) {
      characters.addAll(new BrailleWord(dashDelimited).toList());
      characters.add(new BrailleCharacter());
    }
    sendEmptyMessageDelayed(0, initialDelay);
  }

  @Override
  public void handleMessage(Message msg) {
    if (!characters.isEmpty()) {
      BrailleCharacter character = characters.removeFirst();
      BrailleInputPlaneResult bipr;
      long delayUntilNextMessage;
      if (character.isEmpty()) {
        bipr = callback.createSwipe(new Swipe(Swipe.Direction.LEFT, /*touchCount=*/ 1));
        delayUntilNextMessage = interWordDelay;
      } else {
        bipr = BrailleInputPlaneResult.createTapAndRelease(character);
        delayUntilNextMessage = interCharacterDelay;
      }
      callback.onPerform(bipr);
      sendEmptyMessageDelayed(0, delayUntilNextMessage);
    } else {
      callback.onFinish();
    }
  }
}

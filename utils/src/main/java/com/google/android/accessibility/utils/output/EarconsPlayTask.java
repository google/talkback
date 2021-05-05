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

package com.google.android.accessibility.utils.output;

import android.media.SoundPool;
import android.os.AsyncTask;

/** Task to play earcons in background thread */
public class EarconsPlayTask extends AsyncTask<Void, Integer, Boolean> {
  private SoundPool mSoundPool;
  private int soundId;
  private float volume;
  private float rate;

  public EarconsPlayTask(SoundPool soundPool, int soundId, float volume, float rate) {
    this.mSoundPool = soundPool;
    this.soundId = soundId;
    this.volume = volume;
    this.rate = rate;
  }

  /**
   * Play earcon with given soundId in background thread
   *
   * @param voids not using any parameters as they will passed in via constructor
   * @return if play successful or not
   */
  @Override
  protected Boolean doInBackground(Void... voids) {
    return mSoundPool.play(soundId, volume, volume, 0, 0, rate) != 0;
  }
}

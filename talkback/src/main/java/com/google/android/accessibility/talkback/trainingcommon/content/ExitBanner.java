/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.android.accessibility.talkback.trainingcommon.content;

import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TRAINING_BUTTON_TURN_OFF_TALKBACK;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackMetricStore;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;

/**
 * A {@link PageContentConfig}. It has a TalkBack exit banner UI that and a TalkBack-exit button for
 * TalkBack mis-triggering recovery. The user can turn off TalkBack settings by tapping the button.
 */
public class ExitBanner extends PageContentConfig {

  /** Interface for tutorial activity to request disabling TalkBack. */
  public interface RequestDisableTalkBack {
    void onRequestDisableTalkBack();
  }

  private RequestDisableTalkBack requestDisableTalkBack;

  private TalkBackMetricStore metricStore;

  private boolean firstTapPerformed;

  public ExitBanner() {}

  @Override
  public View createView(
      LayoutInflater inflater, ViewGroup container, Context context, ServiceData data) {
    firstTapPerformed = false;
    View view = inflater.inflate(R.layout.training_exit_banner, container, false);
    Button exitButton = view.findViewById(R.id.training_exit_talkback_button);
    exitButton.setLongClickable(false);
    exitButton.setOnClickListener(
        (View v) -> {
          // Statistic turn-off button event.
          if (metricStore != null) {
            metricStore.onTutorialEvent(TRAINING_BUTTON_TURN_OFF_TALKBACK);
          }
          // The first click change the button label to remind the user to click again to turn off
          // TalkBack.
          if (firstTapPerformed && requestDisableTalkBack != null) {
            firstTapPerformed = false;
            requestDisableTalkBack.onRequestDisableTalkBack();
          } else {
            firstTapPerformed = true;
            exitButton.setText(R.string.tap_again_to_turn_off);
          }
        });
    return view;
  }

  public void setRequestDisableTalkBack(RequestDisableTalkBack callback) {
    requestDisableTalkBack = callback;
  }

  public void setMetricStore(TalkBackMetricStore metricStore) {
    this.metricStore = metricStore;
  }
}

/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.accessibility.switchaccess.setupwizard;

import android.content.Context;
import android.view.KeyEvent;
import android.view.ViewStub;
import android.widget.ScrollView;
import com.google.android.accessibility.switchaccess.R;

/**
 * Final screen of the setup wizard. Displays a simple message confirming completion and informing
 * users that they can visit the SA settings page to make changes to their settings.
 */
public class SetupWizardCompletionScreen extends SetupScreen {

  public SetupWizardCompletionScreen(Context context, SetupWizardActivity.ScreenIterator iterator) {
    super(context, iterator);
    setHeadingText(R.string.setup_completion_heading);
    setSubheadingText(SetupScreen.EMPTY_TEXT);

    /* Sets the xml stub to visible for the first time. This by default inflates the stub. */
    ((ViewStub) findViewById(R.id.switch_access_setup_completion_import)).setVisibility(VISIBLE);
  }

  @Override
  public void onStart() {
    super.onStart();
    setCompletionViewVisible(true);
  }

  @Override
  public void onStop() {
    super.onStop();
    setCompletionViewVisible(false);
  }

  private void setCompletionViewVisible(boolean isVisible) {
    final ScrollView completionView =
        (ScrollView) findViewById(R.id.switch_access_setup_completion_inflated_import);
    completionView.setVisibility(isVisible ? VISIBLE : GONE);
    completionView.setFocusable(isVisible);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    return false;
  }

  @Override
  public int getNextScreen() {
    return SetupWizardActivity.INDEX_EXIT_SETUP;
  }

  @Override
  public String getScreenName() {
    return SetupWizardCompletionScreen.class.getSimpleName();
  }
}

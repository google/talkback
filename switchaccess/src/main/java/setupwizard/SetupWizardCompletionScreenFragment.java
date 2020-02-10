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

import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessSetupScreenEnum.SetupScreen;

/**
 * Final screen of the setup wizard. Displays a simple message confirming completion and informing
 * users that they can visit the SA settings page to make changes to their settings.
 */
public class SetupWizardCompletionScreenFragment extends SetupWizardScreenFragment {

  @Override
  public SetupScreen getNextScreen() {
    return SetupScreen.EXIT_SETUP;
  }

  @Override
  protected int getLayoutResourceId() {
    return R.layout.switch_access_setup_completion;
  }

  @Override
  protected void updateUiOnCreateOrRefresh() {
    setHeadingText(R.string.setup_completion_heading);
    setSubheadingText(SetupWizardScreenFragment.EMPTY_TEXT);
  }
}

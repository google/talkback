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
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.android.accessibility.switchaccess.R;

/** Provides a generic framework for each screen of the setup wizard. */
public abstract class SetupScreen extends FrameLayout implements OnClickListener {

  /* Constant value used to indicate no that resource is being put into the heading/subheading. */
  protected static final int EMPTY_TEXT = 0;

  /* Base view for the setup wizard. Holds the stubs of the specific Setup Screens. */
  protected final View mSetupBaseView;
  protected final Context mContext;
  protected final SetupWizardActivity.ScreenIterator mIterator;

  /**
   * @param context Calling Activity
   * @param iterator Iterator for controlling setup flow
   */
  public SetupScreen(Context context, SetupWizardActivity.ScreenIterator iterator) {
    super(context);

    mIterator = iterator;
    mContext = context;
    mSetupBaseView =
        LayoutInflater.from(mContext).inflate(R.layout.switch_access_setup_layout, this);
    ((Button) mSetupBaseView.findViewById(R.id.previous_button)).setOnClickListener(this);
    ((Button) mSetupBaseView.findViewById(R.id.next_button)).setOnClickListener(this);
  }

  @Override
  public void onClick(View v) {
    if (v.getId() == R.id.next_button) {
      mIterator.nextScreen();
    } else if (v.getId() == R.id.previous_button) {
      mIterator.previousScreen();
    }
  }

  /**
   * Setup for each time SetupScreen is displayed should go here. This method should be explicitly
   * called each time a screen is displayed.
   */
  protected void onStart() {
    /* Do nothing */
  }

  /**
   * Cleanup after SetupScreen is finished being displayed should go here. This method should be
   * explicitly called each time a screen is finished being displayed.
   */
  protected void onStop() {
    clearFocus();
  }

  /**
   * Get string from resources using activity context.
   *
   * @param resId Resource id of the string being looked up
   */
  protected String getString(int resId) {
    return mContext.getString(resId);
  }

  /**
   * Get string from resources using activity context.
   *
   * @param resId Resource id of the string being looked up
   * @param formatArgs Format arugments that will be used for substitution
   */
  protected String getString(int resId, Object... formatArgs) {
    return mContext.getString(resId, formatArgs);
  }

  /**
   * Sets the heading text for the wizard.
   *
   * @param resId Resource id of the string that will be placed in the heading
   */
  public void setHeadingText(int resId) {
    if (resId == EMPTY_TEXT) {
      setText((TextView) mSetupBaseView.findViewById(R.id.setup_heading), null);
    } else {
      setText((TextView) mSetupBaseView.findViewById(R.id.setup_heading), getString(resId));
    }
  }

  /**
   * Sets the heading text for the wizard.
   *
   * @param headingString String to be placed in the heading
   */
  public void setHeadingText(String headingString) {
    setText((TextView) mSetupBaseView.findViewById(R.id.setup_heading), headingString);
  }

  /**
   * Sets the subheading text for the wizard.
   *
   * @param resId Resource id of the string that will be placed in the subheading
   */
  public void setSubheadingText(int resId) {
    if (resId == EMPTY_TEXT) {
      setText((TextView) mSetupBaseView.findViewById(R.id.setup_subheading), "");
    } else {
      setText((TextView) mSetupBaseView.findViewById(R.id.setup_subheading), getString(resId));
    }
  }

  /**
   * Sets the subheading text for the wizard.
   *
   * @param subheadingString String to be placed in the heading
   */
  public void setSubheadingText(String subheadingString) {
    setText((TextView) mSetupBaseView.findViewById(R.id.setup_subheading), subheadingString);
  }

  private void setText(TextView textView, String text) {
    if (TextUtils.isEmpty(text)) {
      textView.setVisibility(View.GONE);
    } else {
      textView.setText(text);
      textView.setVisibility(View.VISIBLE);
    }
  }

  /**
   * Returns the name of the current screen. This name is passed to a {@link ScreenViewListener}.
   */
  public abstract String getScreenName();

  private void setButtonText(int buttonId, int buttonTextId) {
    final Button button = (Button) findViewById(buttonId);
    button.setText(mContext.getString(buttonTextId));
  }

  /*
   * Show the "Previous" button.
   */
  public void showPreviousButton() {
    setButtonText(R.id.previous_button, R.string.action_name_previous);
  }

  /*
   * Show the "Exit" button.
   */
  public void showExitButton() {
    setButtonText(R.id.previous_button, R.string.exit);
  }

  /*
   * Show the "Next" button.
   */
  public void showNextButton() {
    setButtonText(R.id.next_button, R.string.action_name_next);
  }

  /*
   * Show the "Finish" button.
   */
  public void showFinishButton() {
    setButtonText(R.id.next_button, R.string.finish);
  }

  /**
   * Handle keys pressed when the user is on this screen.
   *
   * @param event KeyEvent to be consumed or ignored
   * @return {@code true} If key event was consumed, false otherwise
   */
  @Override
  public abstract boolean dispatchKeyEvent(KeyEvent event);

  /**
   * Get the index of the next screen to be shown.
   *
   * @return The index of the next screen
   */
  public abstract int getNextScreen();
}

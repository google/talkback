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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ViewAnimator;
import com.google.android.accessibility.switchaccess.Analytics;
import com.google.android.accessibility.switchaccess.KeyAssignmentUtils;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.ScreenViewListener;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceActivity;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardConfigureSwitch.Action;
import java.util.Stack;

/** A short and simple wizard to configure a working one or two switch system. */
public class SetupWizardActivity extends Activity {

  /*
   * Indices of screens in the view animator.
   */
  public static final int INDEX_NUMBER_OF_SWITCHES_SCREEN = 0;
  public static final int INDEX_ONE_SWITCH_OPTION_SCREEN = 1;
  public static final int INDEX_TWO_SWITCH_OPTION_SCREEN = 2;
  public static final int INDEX_AUTO_SCAN_KEY_SCREEN = 3;
  public static final int INDEX_STEP_SPEED_SCREEN = 4;
  public static final int INDEX_NEXT_KEY_SCREEN = 5;
  public static final int INDEX_SELECT_KEY_SCREEN = 6;
  public static final int INDEX_OPTION_ONE_KEY_SCREEN = 7;
  public static final int INDEX_OPTION_TWO_KEY_SCREEN = 8;
  public static final int INDEX_SWITCH_GAME_SCREEN = 9;
  public static final int INDEX_COMPLETION_SCREEN = 10;
  public static final int INDEX_EXIT_SETUP = -1;

  /* Holds all setup wiard views and navigates between them. */
  private ViewAnimator mViewAnimator;

  /* Keeps a history of previous screen indexes. */
  private Stack<Integer> mPreviousScreenIndexes;

  private ScreenViewListener mScreenViewListener;

  /**
   * Create the view animator which holds the setup wizard views. Populate it with the necessary
   * screens.
   *
   * @param savedInstanceState Saved state from prior execution
   */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    SwitchAccessPreferenceActivity.setRefreshUiOnResume(true);

    final Animation inAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in);
    final Animation outAnimation = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);

    ScreenIterator screenIterator = new ScreenIterator();
    mViewAnimator = new ViewAnimator(this);
    mViewAnimator.setInAnimation(inAnimation);
    mViewAnimator.setOutAnimation(outAnimation);
    mViewAnimator.addView(
        new SetupWizardNumberOfSwitches(this, screenIterator), INDEX_NUMBER_OF_SWITCHES_SCREEN);

    mViewAnimator.addView(
        new SetupWizardScanningMethod(
            this, screenIterator, SetupWizardScanningMethod.NumberOfSwitches.ONE),
        INDEX_ONE_SWITCH_OPTION_SCREEN);
    mViewAnimator.addView(
        new SetupWizardScanningMethod(
            this, screenIterator, SetupWizardScanningMethod.NumberOfSwitches.TWO),
        INDEX_TWO_SWITCH_OPTION_SCREEN);

    mViewAnimator.addView(
        new SetupWizardConfigureSwitch(this, Action.AUTO_SCAN, screenIterator),
        INDEX_AUTO_SCAN_KEY_SCREEN);
    mViewAnimator.addView(new SetupWizardStepSpeed(this, screenIterator), INDEX_STEP_SPEED_SCREEN);

    /* Row Column Scanning */
    mViewAnimator.addView(
        new SetupWizardConfigureSwitch(this, Action.NEXT, screenIterator), INDEX_NEXT_KEY_SCREEN);
    mViewAnimator.addView(
        new SetupWizardConfigureSwitch(this, Action.SELECT, screenIterator),
        INDEX_SELECT_KEY_SCREEN);

    /* Option Scanning */
    mViewAnimator.addView(
        new SetupWizardConfigureSwitch(this, Action.OPTION_ONE, screenIterator),
        INDEX_OPTION_ONE_KEY_SCREEN);
    mViewAnimator.addView(
        new SetupWizardConfigureSwitch(this, Action.OPTION_TWO, screenIterator),
        INDEX_OPTION_TWO_KEY_SCREEN);

    /* Wrap-up screens */
    mViewAnimator.addView(
        new SetupWizardSwitchGame(this, screenIterator), INDEX_SWITCH_GAME_SCREEN);
    mViewAnimator.addView(
        new SetupWizardCompletionScreen(this, screenIterator), INDEX_COMPLETION_SCREEN);

    mViewAnimator.setDisplayedChild(INDEX_NUMBER_OF_SWITCHES_SCREEN);
    mPreviousScreenIndexes = new Stack<>();
    setContentView(mViewAnimator);

    getCurrentScreen().onStart();
    setNavigationButtonText(getCurrentScreen());
    ActionBar actionBar = getActionBar();
    // The Action Bar can be null during robolectric testing, so check.
    if (actionBar != null) {
      actionBar.setTitle(R.string.setup_wizard_heading);
    }

    warnUserIfSwitchesAssigned();

    // Set the listener if Analytics instance exists; do not create a new instance.
    mScreenViewListener = Analytics.getInstanceIfExists();
  }

  private void warnUserIfSwitchesAssigned() {
    if (KeyAssignmentUtils.areKeysAssigned(this)) {
      AlertDialog.Builder clearKeysBuilder = new AlertDialog.Builder(this);
      clearKeysBuilder.setTitle(getString(R.string.clear_keys_dialog_title));
      clearKeysBuilder.setMessage(getString(R.string.clear_keys_dialog_message));

      clearKeysBuilder.setPositiveButton(
          R.string.clear_keys_dialog_positive_button,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
              KeyAssignmentUtils.clearAllKeyPrefs(SetupWizardActivity.this);
              recreate();
              dialog.cancel();
            }
          });

      clearKeysBuilder.setNegativeButton(
          R.string.clear_keys_dialog_negative_button,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
            }
          });

      AlertDialog clearKeysDialog = clearKeysBuilder.create();
      clearKeysDialog.show();
    }
  }

  private void displayScreen(int screenToDisplay) {
    if (screenToDisplay == mViewAnimator.getDisplayedChild()) {
      return;
    }

    getCurrentScreen().onStop();

    mViewAnimator.setDisplayedChild(screenToDisplay);
    getCurrentScreen().onStart();
    setNavigationButtonText(getCurrentScreen());

    if (mScreenViewListener != null) {
      mScreenViewListener.onScreenShown(getCurrentScreen().getScreenName());
    }
  }

  private void displayNextScreenOrWarning() {
    /*
     * If we are currently on a switch configuration screen, display a dialog asking if the user
     * wishes to continue if they attempt to proceed to the next screen without assigning any
     * switches. This combats human error in completing the setup wizard.
     */
    if (getCurrentScreen() instanceof SetupWizardConfigureSwitch
        && !((SetupWizardConfigureSwitch) getCurrentScreen()).hasSwitchesAdded()) {
      AlertDialog.Builder noKeysBuilder = new AlertDialog.Builder(this);
      noKeysBuilder.setTitle(getString(R.string.setup_switch_assignment_nothing_assigned_title));
      noKeysBuilder.setMessage(
          getString(R.string.setup_switch_assignment_nothing_assigned_message));

      noKeysBuilder.setPositiveButton(
          android.R.string.ok,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
              displayNextScreen();
              dialog.cancel();
            }
          });

      noKeysBuilder.setNegativeButton(
          android.R.string.cancel,
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
            }
          });

      AlertDialog noKeysDialog = noKeysBuilder.create();
      noKeysDialog.show();
    } else {
      displayNextScreen();
    }
  }

  private void displayNextScreen() {
    int nextScreen = getCurrentScreen().getNextScreen();
    if (nextScreen == INDEX_EXIT_SETUP) {
      finish();
      return;
    }

    mPreviousScreenIndexes.push(mViewAnimator.getDisplayedChild());
    displayScreen(nextScreen);
  }

  private void displayPreviousScreen() {
    if (mPreviousScreenIndexes.empty()) {
      finish();
      return;
    }

    displayScreen(mPreviousScreenIndexes.pop());
  }

  /**
   * Sets the text on the bottom navigation buttons. "Previous" shown when the previous screen
   * exists, "Exit" otherwise. "Next" shown when the next screen exists, "Finish" otherwise.
   */
  protected void setNavigationButtonText(SetupScreen screen) {
    if (screen.getNextScreen() == INDEX_EXIT_SETUP) {
      screen.showFinishButton();
    } else {
      screen.showNextButton();
    }
    if (mPreviousScreenIndexes.empty()) {
      screen.showExitButton();
    } else {
      screen.showPreviousButton();
    }
  }

  /**
   * Get the view currently being displayed.
   *
   * @return SetupScreen active in the view animator
   */
  public SetupScreen getCurrentScreen() {
    return (SetupScreen) mViewAnimator.getCurrentView();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
      super.dispatchKeyEvent(event);
      return false;
    } else {
      /* Pass key event on to child screen */
      return getCurrentScreen().dispatchKeyEvent(event);
    }
  }

  /**
   * Get the view animator containing the setup screens.
   *
   * @return the ViewAnimator object belonging to this activity
   */
  public ViewAnimator getViewAnimator() {
    return this.mViewAnimator;
  }

  /** Used to pass the control over screen navigation to the views associated with this Activity. */
  public class ScreenIterator {
    public void nextScreen() {
      displayNextScreenOrWarning();
    }

    public void previousScreen() {
      displayPreviousScreen();
    }
  }
}

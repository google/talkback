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
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import com.google.android.accessibility.switchaccess.KeyAssignmentUtils;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceActivity;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.Set;

/**
 * Setup wizard screen that allows the user to test their configured Switch Access setup on a game
 * of Tic-Tac-Toe.
 */
public class SetupWizardSwitchGame extends SetupScreen {

  private static final String SCREEN_NAME_SUFFIX_FUNCTIONAL_CONFIGURATION =
      "FunctionalConfiguration";
  private static final String SCREEN_NAME_SUFFIX_NON_FUNCTIONAL_CONFIGURATION =
      "NonFunctionalConfiguration";

  private final Context mContext;

  private SetupWizardTicTacToeController mTicTacToeGame;

  private final SetupWizardActivity.ScreenIterator mIterator;

  private GridLayout mGameBoard;

  private boolean mIsFunctionalConfiguration;

  public SetupWizardSwitchGame(Context context, SetupWizardActivity.ScreenIterator iterator) {
    super(context, iterator);
    mContext = context;

    mIterator = iterator;
    setHeadingText(R.string.switch_game_heading);

    /* Sets the xml stub to visible for the first time. This by default inflates the stub. */
    ((ViewStub) findViewById(R.id.switch_access_setup_switch_game_import)).setVisibility(VISIBLE);
  }

  @Override
  public void onStart() {
    super.onStart();
    setSubheadingText(checkConfigurationAndGetSubheading());

    mGameBoard = (GridLayout) findViewById(R.id.game_board);
    mTicTacToeGame =
        new SetupWizardTicTacToeController(
            mContext, mGameBoard, (Button) findViewById(R.id.reset_game_button), mIterator);

    View.OnClickListener gameBoardSquareListener =
        new View.OnClickListener() {
          @Override
          public void onClick(View boardSquare) {
            if (TextUtils.isEmpty(((Button) boardSquare).getText().toString())) {
              mTicTacToeGame.playerMove(Integer.parseInt(boardSquare.getTag().toString()));
            } else {
              Toast.makeText(
                      mContext, getString(R.string.game_square_already_taken), Toast.LENGTH_SHORT)
                  .show();
            }
          }
        };

    for (int i = 0; i < mGameBoard.getChildCount(); i++) {
      Button gameBoardSquare = ((Button) mGameBoard.getChildAt(i));
      gameBoardSquare.setOnClickListener(gameBoardSquareListener);
    }

    setGameVisible(true);
  }

  @Override
  public void onStop() {
    super.onStop();

    for (int i = 0; i < mGameBoard.getChildCount(); i++) {
      Button gameBoardSquare = ((Button) mGameBoard.getChildAt(i));
      gameBoardSquare.setOnClickListener(null);
    }

    mTicTacToeGame = null;
    setGameVisible(false);
  }

  private void setGameVisible(boolean isVisible) {
    final ScrollView switchGameView =
        (ScrollView) findViewById(R.id.switch_access_setup_switch_game_inflated_import);
    switchGameView.setVisibility(isVisible ? VISIBLE : GONE);
    switchGameView.setFocusable(isVisible);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    return false;
  }

  @Override
  public int getNextScreen() {
    return SetupWizardActivity.INDEX_COMPLETION_SCREEN;
  }

  /**
   * Formats customized subheadings giving user friendly tips on how to use the exact current switch
   * setup. If the current configuration is not valid, a message conveying that is displayed
   * instead.
   */
  private String checkConfigurationAndGetSubheading() {
    mIsFunctionalConfiguration = true;
    SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(mContext);

    boolean isAutoScanEnabled = SwitchAccessPreferenceActivity.isAutoScanEnabled(mContext);
    String scanningMethod =
        sharedPreferences.getString(
            getString(R.string.pref_scanning_methods_key),
            getString(R.string.pref_scanning_methods_default));

    // If Auto-scan is enabled and we have an Auto-scan key assigned, then we have a complete
    // configuration. However, if we don't have keys assigned to Auto-scan, we could still have
    // as complete Next/Select assignment, so we can't eliminate the possibility of a two switch
    // configuration even if Auto-scan is enabled.
    Set<String> autoScanSwitchNames =
        KeyAssignmentUtils.getStringSet(
            sharedPreferences, getString(R.string.pref_key_mapped_to_auto_scan_key));
    if (isAutoScanEnabled && !autoScanSwitchNames.isEmpty()) {
      String autoScanFirstSwitchName =
          KeyAssignmentUtils.describeExtendedKeyCode(
              Long.parseLong(autoScanSwitchNames.iterator().next()), mContext);
      if (scanningMethod.equals(getString(R.string.row_col_scanning_key))) {
        return getString(
            R.string.switch_game_one_switch_row_col_subheading, autoScanFirstSwitchName);
      } else {
        return getString(
            R.string.switch_game_one_switch_linear_subheading, autoScanFirstSwitchName);
      }
    }

    // Check if we have a complete two-switch configuration.
    Set<String> actionOneSwitchNames =
        KeyAssignmentUtils.getStringSet(
            sharedPreferences, getString(R.string.pref_key_mapped_to_next_key));
    Set<String> actionTwoSwitchNames =
        KeyAssignmentUtils.getStringSet(
            sharedPreferences, getString(R.string.pref_key_mapped_to_click_key));

    if (actionOneSwitchNames.isEmpty() || actionTwoSwitchNames.isEmpty()) {
      return getIncompleteConfigurationSubheading();
    }

    String actionOneFirstSwitchName =
        KeyAssignmentUtils.describeExtendedKeyCode(
            Long.parseLong(actionOneSwitchNames.iterator().next()), mContext);
    String actionTwoFirstSwitchName =
        KeyAssignmentUtils.describeExtendedKeyCode(
            Long.parseLong(actionTwoSwitchNames.iterator().next()), mContext);

    if (scanningMethod.equals(getString(R.string.option_scanning_key))) {
      String optionOneColor =
          getGroupSelectionColor(
              R.string.pref_highlight_0_color_key, R.string.pref_highlight_0_color_default);
      String optionTwoColor =
          getGroupSelectionColor(
              R.string.pref_highlight_1_color_key, R.string.pref_highlight_1_color_default);
      return getString(
          R.string.switch_game_two_switch_option_scan_subheading,
          actionTwoFirstSwitchName,
          optionOneColor,
          optionTwoColor,
          actionOneFirstSwitchName);
    } else if (scanningMethod.equals(getString(R.string.row_col_scanning_key))) {
      return getString(
          R.string.switch_game_two_switch_row_col_subheading,
          actionOneFirstSwitchName,
          actionTwoFirstSwitchName);
    } else {
      return getString(
          R.string.switch_game_two_switch_linear_subheading,
          actionOneFirstSwitchName,
          actionTwoFirstSwitchName);
    }
  }

  private String getIncompleteConfigurationSubheading() {
    mIsFunctionalConfiguration = false;
    return getString(R.string.switch_game_incomplete_switches_subheading);
  }

  private String getGroupSelectionColor(int colorKey, int colorDefaultKey) {
    return SwitchActionInformationUtils.hexToColorString(
        SwitchActionInformationUtils.getHexFromSharedPreferences(
            colorKey, colorDefaultKey, mContext),
        mContext);
  }

  @Override
  public String getScreenName() {
    return SetupWizardSwitchGame.class.getSimpleName()
        + (mIsFunctionalConfiguration
            ? SCREEN_NAME_SUFFIX_FUNCTIONAL_CONFIGURATION
            : SCREEN_NAME_SUFFIX_NON_FUNCTIONAL_CONFIGURATION);
  }
}

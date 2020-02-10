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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.Toast;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.keyassignment.KeyAssignmentUtils;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessSetupScreenEnum.SetupScreen;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Setup wizard screen that allows the user to test their configured Switch Access setup on a game
 * of Tic-Tac-Toe.
 */
public class SetupWizardSwitchGameFragment extends SetupWizardScreenFragment {

  private static final String SCREEN_NAME_SUFFIX_FUNCTIONAL_CONFIGURATION =
      "FunctionalConfiguration";
  private static final String SCREEN_NAME_SUFFIX_NON_FUNCTIONAL_CONFIGURATION =
      "NonFunctionalConfiguration";

  private SetupWizardTicTacToeController ticTacToeGame;

  private GridLayout gameBoard;

  private boolean isFunctionalConfiguration;

  @Override
  public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    gameBoard = view.findViewById(R.id.game_board);
    ticTacToeGame =
        new SetupWizardTicTacToeController(
            getActivity(), gameBoard, view.findViewById(R.id.reset_game_button));

    View.OnClickListener gameBoardSquareListener =
        boardSquare -> {
          if (TextUtils.isEmpty(((Button) boardSquare).getText().toString())) {
            ticTacToeGame.playerMove(Integer.parseInt(boardSquare.getTag().toString()));
          } else {
            Toast.makeText(
                    getActivity(),
                    getString(R.string.game_square_already_taken),
                    Toast.LENGTH_SHORT)
                .show();
          }
        };

    for (int i = 0; i < gameBoard.getChildCount(); i++) {
      Button gameBoardSquare = ((Button) gameBoard.getChildAt(i));
      gameBoardSquare.setOnClickListener(gameBoardSquareListener);
    }
  }

  @Override
  public void onStop() {
    super.onStop();

    for (int i = 0; i < gameBoard.getChildCount(); i++) {
      Button gameBoardSquare = ((Button) gameBoard.getChildAt(i));
      gameBoardSquare.setOnClickListener(null);
    }
  }

  @Override
  public SetupScreen getNextScreen() {
    return SetupScreen.COMPLETION_SCREEN;
  }

  @Override
  protected int getLayoutResourceId() {
    return R.layout.switch_access_setup_switch_game;
  }

  @Override
  protected void updateUiOnCreateOrRefresh() {
    setHeadingText(R.string.switch_game_heading);
    setSubheadingText(getSubheading());
  }

  /**
   * Formats customized subheadings giving user friendly tips on how to use the exact current switch
   * setup. If the current configuration is not valid, a message conveying that is displayed
   * instead.
   */
  private String getSubheading() {
    SharedPreferences sharedPreferences =
        SharedPreferencesUtils.getSharedPreferences(getActivity());

    if (!KeyAssignmentUtils.isConfigurationFunctionalAfterSetup(sharedPreferences, getActivity())) {
      return getIncompleteConfigurationSubheading();
    }

    boolean isAutoScanEnabled = SwitchAccessPreferenceUtils.isAutoScanEnabled(getActivity());
    String scanningMethod = SwitchAccessPreferenceUtils.getCurrentScanningMethod(getActivity());

    if (isAutoScanEnabled) {
      Set<String> autoScanSwitchNames =
          KeyAssignmentUtils.getStringSet(
              sharedPreferences, getString(R.string.pref_key_mapped_to_auto_scan_key));

      if (!autoScanSwitchNames.isEmpty()) {
        String autoScanFirstSwitchName =
            KeyAssignmentUtils.describeExtendedKeyCode(
                Long.parseLong(autoScanSwitchNames.iterator().next()), getActivity());
        if (scanningMethod.equals(getString(R.string.row_col_scanning_key))) {
          return getString(
              R.string.switch_game_one_switch_row_col_subheading, autoScanFirstSwitchName);
        } else {
          return getString(
              R.string.switch_game_one_switch_linear_subheading, autoScanFirstSwitchName);
        }
      }
    }

    Set<String> actionOneSwitchNames =
        KeyAssignmentUtils.getStringSet(
            sharedPreferences, getString(R.string.pref_key_mapped_to_next_key));
    Set<String> actionTwoSwitchNames =
        KeyAssignmentUtils.getStringSet(
            sharedPreferences, getString(R.string.pref_key_mapped_to_click_key));

    String actionOneFirstSwitchName =
        KeyAssignmentUtils.describeExtendedKeyCode(
            Long.parseLong(actionOneSwitchNames.iterator().next()), getActivity());
    String actionTwoFirstSwitchName =
        KeyAssignmentUtils.describeExtendedKeyCode(
            Long.parseLong(actionTwoSwitchNames.iterator().next()), getActivity());

    if (scanningMethod.equals(getString(R.string.group_selection_key))) {
      String groupOneColor =
          getGroupSelectionColor(
              R.string.pref_highlight_0_color_key, R.string.pref_highlight_0_color_default);
      String groupTwoColor =
          getGroupSelectionColor(
              R.string.pref_highlight_1_color_key, R.string.pref_highlight_1_color_default);
      return getString(
          R.string.switch_game_two_switch_option_scan_subheading,
          actionTwoFirstSwitchName,
          groupOneColor,
          groupTwoColor,
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
    isFunctionalConfiguration = false;
    return getString(R.string.switch_game_incomplete_switches_subheading);
  }

  private String getGroupSelectionColor(int colorKey, int colorDefaultKey) {
    return SwitchActionInformationUtils.getColorStringFromSharedPreferences(
        colorKey, colorDefaultKey, getActivity());
  }

  @Override
  public String getScreenName() {
    return super.getScreenName()
        + (isFunctionalConfiguration
            ? SCREEN_NAME_SUFFIX_FUNCTIONAL_CONFIGURATION
            : SCREEN_NAME_SUFFIX_NON_FUNCTIONAL_CONFIGURATION);
  }
}

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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import com.google.android.accessibility.switchaccess.KeyAssignmentUtils;
import com.google.android.accessibility.switchaccess.R;
import java.util.ArrayList;
import java.util.List;

/** Controller for the logic of the switch controlled game of Tic-Tac-Toe. */
public class SetupWizardTicTacToeController {

  /* Tic-tac-toe markers for the human and computer plyers */
  public static final String HUMAN_PLAYER_MARKER = "O";
  public static final String COMPUTER_PLAYER_MARKER = "X";

  /* Spaces at indices that are multiples of 4, the center index, lie on the forward diagonal */
  private static final int CENTER_INDEX = 4;

  /*
   * Spaces at indices that are multiples of 2 and not multiples of 4 (excluding the center tile)
   * lie on the anti-diagonal
   */
  private static final int ANTI_DIAGONAL = 2;

  /* Number of rows and number of columns on the game board */
  private static final int ROW_COLUMN_COUNT = 3;

  /* Number of spaces on the board */
  private static final int SPACES_COUNT = ROW_COLUMN_COUNT * ROW_COLUMN_COUNT;

  /* Time delay between the human move and the following computer move */
  private static final int COMPUTER_MOVE_DELAY_MS = 1500;

  private final Context mContext;

  /* GridLayout consisting of the buttons that make up the game board */
  private final GridLayout mGameBoard;

  /* Button to reset Tic-Tac-Toe back to a new game */
  private final Button mResetButton;

  /* Used to track which spaces remain open on the board */
  private final List<Integer> mEmptySpacesIndicies;

  /* Screen iterator to the next screen when a user is satisfied with their configuration */
  private final SetupWizardActivity.ScreenIterator mIterator;

  /* Helper class used to generate moves of the computer opponent */
  private final TicTacToeComputerMoveGenerator mComputerMoveGenerator;

  /**
   * @param context Current application context
   * @param gameBoard GridLayout containing the buttons that make up the tic-tac-toe board
   * @param resetButton Button to reset the board to a new game
   * @param iterator Screen iterator to navigate forward if a user is satisfied with their setup
   */
  public SetupWizardTicTacToeController(
      Context context,
      GridLayout gameBoard,
      Button resetButton,
      SetupWizardActivity.ScreenIterator iterator) {
    this(context, gameBoard, resetButton, iterator, new TicTacToeComputerMoveGenerator());
  }

  /**
   * Constructor that allows a custom computer move generator to be supplied.
   *
   * @param context Current application context
   * @param gameBoard GridLayout containing the buttons that make up the tic-tac-toe board
   * @param resetButton Button to reset the board to a new game
   * @param iterator Screen iterator to navigate forward if a user is satisfied with their setup
   * @param computerMoveGenerator Custom computer move generator for this game
   */
  public SetupWizardTicTacToeController(
      Context context,
      GridLayout gameBoard,
      Button resetButton,
      SetupWizardActivity.ScreenIterator iterator,
      TicTacToeComputerMoveGenerator computerMoveGenerator) {
    mContext = context;
    mGameBoard = gameBoard;
    mIterator = iterator;
    mEmptySpacesIndicies = new ArrayList<>();
    mComputerMoveGenerator = computerMoveGenerator;

    mResetButton = resetButton;
    mResetButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            resetGameBoard();
          }
        });

    resetGameBoard();
  }

  private void resetGameBoard() {
    for (int i = 0; i < mGameBoard.getChildCount(); i++) {
      ((Button) mGameBoard.getChildAt(i)).setText("");
    }

    mEmptySpacesIndicies.clear();

    for (int i = 0; i < SPACES_COUNT; i++) {
      mEmptySpacesIndicies.add(i);
    }

    setGameButtonsEnabled(true);
    mResetButton.setEnabled(false);
  }

  /**
   * Executes a human player's move at the specified location on the board.
   *
   * @param moveLocation Index of the space the player is making a move at
   */
  public void playerMove(int moveLocation) {
    setGameButtonsEnabled(false);

    Button moveTile = ((Button) mGameBoard.getChildAt(moveLocation));

    moveTile.setText(HUMAN_PLAYER_MARKER);
    moveTile.setTextColor(Color.BLACK);

    mEmptySpacesIndicies.remove(mEmptySpacesIndicies.indexOf(moveLocation));

    if (checkForWin(moveLocation)) {
      displayFinishDialog(mContext.getString(R.string.game_win_outcome_title));
      return;
    }

    if (mEmptySpacesIndicies.isEmpty()) {
      displayFinishDialog(mContext.getString(R.string.game_tie_outcome_title));
      return;
    }

    /* Player always moves first, followed by a compuer move. The computer move is delayed by
     * one and a half second to give the game a more natural feel.
     */
    Handler handler = new Handler();
    handler.postDelayed(
        new Runnable() {
          @Override
          public void run() {
            computerMove();
          }
        },
        COMPUTER_MOVE_DELAY_MS);
  }

  /**
   * Computer chooses a random space from the set of remaining open tiles and executes a move at
   * that location.
   */
  private void computerMove() {
    int moveLocation = mComputerMoveGenerator.generateRandomMove(mEmptySpacesIndicies);

    ((Button) mGameBoard.getChildAt(moveLocation)).setText(COMPUTER_PLAYER_MARKER);
    ((Button) mGameBoard.getChildAt(moveLocation)).setTextColor(Color.WHITE);

    mEmptySpacesIndicies.remove(mEmptySpacesIndicies.indexOf(moveLocation));

    if (checkForWin(moveLocation)) {
      displayFinishDialog(mContext.getString(R.string.game_loss_outcome_title));
      return;
    }

    /*
     * This should never be triggered with the current game flow. With the human moving first
     * and an odd number of moves to play, a tie will only result from the human's last move.
     */
    if (mEmptySpacesIndicies.isEmpty()) {
      displayFinishDialog(mContext.getString(R.string.game_tie_outcome_title));
      return;
    }

    setGameButtonsEnabled(true);
  }

  private boolean checkForWin(int moveLocation) {

    int row = moveLocation / ROW_COLUMN_COUNT;
    int column = moveLocation % ROW_COLUMN_COUNT;

    String currentMoveMarker = ((Button) mGameBoard.getChildAt(moveLocation)).getText().toString();

    /* Check the move row for a win */
    if (currentMoveMarker.equals(((Button) mGameBoard.getChildAt(row * ROW_COLUMN_COUNT)).getText())
        && currentMoveMarker.equals(
            ((Button) mGameBoard.getChildAt(row * ROW_COLUMN_COUNT + 1)).getText())
        && currentMoveMarker.equals(
            ((Button) mGameBoard.getChildAt(row * ROW_COLUMN_COUNT + 2)).getText())) {
      return true;
    }

    /* Check the move column for a win */
    if (currentMoveMarker.equals(((Button) mGameBoard.getChildAt(column)).getText())
        && currentMoveMarker.equals(
            ((Button) mGameBoard.getChildAt(column + ROW_COLUMN_COUNT)).getText())
        && currentMoveMarker.equals(
            ((Button) mGameBoard.getChildAt(column + ROW_COLUMN_COUNT * 2)).getText())) {
      return true;
    }

    /*
     * If the move lies on the diagonal or anti-diagonal, check those for a win. The center
     * is part of both groups, so we can't exclude a win on the anti-diagonal by checking
     * whether the move location modulo CENTER_INDEX is not zero.
     */
    if ((moveLocation % CENTER_INDEX == 0)
        && currentMoveMarker.equals(((Button) mGameBoard.getChildAt(0)).getText())
        && currentMoveMarker.equals(((Button) mGameBoard.getChildAt(CENTER_INDEX)).getText())
        && currentMoveMarker.equals(((Button) mGameBoard.getChildAt(CENTER_INDEX * 2)).getText())) {
      return true;
    } else if ((moveLocation % ANTI_DIAGONAL == 0)
        && currentMoveMarker.equals(((Button) mGameBoard.getChildAt(ANTI_DIAGONAL)).getText())
        && currentMoveMarker.equals(((Button) mGameBoard.getChildAt(CENTER_INDEX)).getText())
        && currentMoveMarker.equals(
            ((Button) mGameBoard.getChildAt(ANTI_DIAGONAL * 3)).getText())) {
      return true;
    }
    return false;
  }

  private void displayFinishDialog(String outcome) {
    setGameButtonsEnabled(false);
    mResetButton.setEnabled(true);

    AlertDialog.Builder gameOverBuilder = new AlertDialog.Builder(mContext);
    gameOverBuilder.setTitle(outcome);
    gameOverBuilder.setMessage(mContext.getString(R.string.game_outcome_body_text));

    gameOverBuilder.setNegativeButton(
        mContext.getString(R.string.game_outcome_negative_response),
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int id) {
            ((Activity) mContext).recreate();
            KeyAssignmentUtils.clearAllKeyPrefs(mContext);
            dialog.cancel();
          }
        });

    gameOverBuilder.setPositiveButton(
        mContext.getString(R.string.game_outcome_positive_response),
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int id) {
            mIterator.nextScreen();
            dialog.cancel();
          }
        });

    AlertDialog gameOverDialog = gameOverBuilder.create();
    gameOverDialog.show();
  }

  private void setGameButtonsEnabled(boolean setEnabled) {
    for (int i = 0; i < mGameBoard.getChildCount(); i++) {
      ((Button) mGameBoard.getChildAt(i)).setEnabled(setEnabled);
    }
    mResetButton.setEnabled(setEnabled);
  }
}

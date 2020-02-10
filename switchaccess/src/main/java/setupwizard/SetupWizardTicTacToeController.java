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
import android.graphics.Color;
import android.os.Handler;
import android.view.ContextThemeWrapper;
import android.widget.Button;
import android.widget.GridLayout;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.keyassignment.KeyAssignmentUtils;
import java.util.ArrayList;
import java.util.List;

/** Controller for the logic of the switch controlled game of Tic-Tac-Toe. */
public class SetupWizardTicTacToeController {

  /* Tic-tac-toe markers for the human and computer players */
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

  private final Context context;

  /* GridLayout consisting of the buttons that make up the game board */
  private final GridLayout gameBoard;

  /* Button to reset Tic-Tac-Toe back to a new game */
  private final Button resetButton;

  /* Used to track which spaces remain open on the board */
  private final List<Integer> emptySpacesIndices;

  /* Helper class used to generate moves of the computer opponent */
  private final TicTacToeComputerMoveGenerator computerMoveGenerator;

  /**
   * @param context Current application context
   * @param gameBoard GridLayout containing the buttons that make up the tic-tac-toe board
   * @param resetButton Button to reset the board to a new game
   */
  public SetupWizardTicTacToeController(Context context, GridLayout gameBoard, Button resetButton) {
    this(context, gameBoard, resetButton, new TicTacToeComputerMoveGenerator());
  }

  /**
   * Constructor that allows a custom computer move generator to be supplied.
   *
   * @param context Current application context
   * @param gameBoard GridLayout containing the buttons that make up the tic-tac-toe board
   * @param resetButton Button to reset the board to a new game
   * @param computerMoveGenerator Custom computer move generator for this game
   */
  public SetupWizardTicTacToeController(
      Context context,
      GridLayout gameBoard,
      Button resetButton,
      TicTacToeComputerMoveGenerator computerMoveGenerator) {
    this.context = context;
    this.gameBoard = gameBoard;
    emptySpacesIndices = new ArrayList<>();
    this.computerMoveGenerator = computerMoveGenerator;

    this.resetButton = resetButton;
    this.resetButton.setOnClickListener(view -> resetGameBoard());

    resetGameBoard();
  }

  private void resetGameBoard() {
    String emptySpaceDescription =
        context.getString(R.string.game_square_no_marker_content_description);
    emptySpacesIndices.clear();
    for (int position = 0; position < SPACES_COUNT; position++) {
      ((Button) gameBoard.getChildAt(position)).setText("");
      updateContentDescriptionForPosition(position, emptySpaceDescription);
      emptySpacesIndices.add(position);
    }

    setGameButtonsEnabled(true);
    resetButton.setEnabled(false);
  }

  /**
   * Executes a human player's move at the specified location on the board.
   *
   * @param moveLocation Index of the space the player is making a move at
   */
  public void playerMove(int moveLocation) {
    setGameButtonsEnabled(false);

    boolean gameIsOver =
        makeMoveAndMaybeEndGame(
            moveLocation,
            HUMAN_PLAYER_MARKER,
            Color.BLACK,
            false /* announceMove */,
            R.string.game_win_outcome_title);
    if (gameIsOver) {
      return;
    }

    /* Player always moves first, followed by a computer move. The computer move is delayed by
     * one and a half second to give the game a more natural feel.
     */
    Handler handler = new Handler();
    handler.postDelayed(this::computerMove, COMPUTER_MOVE_DELAY_MS);
  }

  /**
   * Computer chooses a random space from the set of remaining open tiles and executes a move at
   * that location.
   */
  private void computerMove() {
    int moveLocation = computerMoveGenerator.generateRandomMove(emptySpacesIndices);

    boolean gameIsOver =
        makeMoveAndMaybeEndGame(
            moveLocation,
            COMPUTER_PLAYER_MARKER,
            Color.WHITE,
            true /* announceMove */,
            R.string.game_loss_outcome_title);
    if (gameIsOver) {
      return;
    }

    setGameButtonsEnabled(true);
  }

  /**
   * Makes a move at the specified location. If this ends the game, displays a dialog.
   *
   * @param moveLocation The location of the move
   * @param playerMarker The character that will be used as marker for the player that just made the
   *     move (human or computer)
   * @param playerColor The color that will be used to draw the playerMarker
   * @param announceMove Whether to announce the move that has just been made
   * @param onWinDialogStringResId The string that will be displayed if the player (human or
   *     computer) making this move just won the game
   * @return {@code true} if the game is over
   */
  private boolean makeMoveAndMaybeEndGame(
      int moveLocation,
      String playerMarker,
      int playerColor,
      boolean announceMove,
      int onWinDialogStringResId) {
    Button moveLocationButton = ((Button) gameBoard.getChildAt(moveLocation));
    moveLocationButton.setText(playerMarker);
    moveLocationButton.setTextColor(playerColor);
    String moveDescription = updateContentDescriptionForPosition(moveLocation, playerMarker);
    if (announceMove) {
      moveLocationButton.announceForAccessibility(moveDescription);
    }

    emptySpacesIndices.remove(emptySpacesIndices.indexOf(moveLocation));

    if (checkForWin(moveLocation)) {
      displayFinishDialog(context.getString(onWinDialogStringResId));
      return true;
    }

    if (emptySpacesIndices.isEmpty()) {
      displayFinishDialog(context.getString(R.string.game_tie_outcome_title));
      return true;
    }

    // The game is not yet over.
    return false;
  }

  private String updateContentDescriptionForPosition(int position, String markerDescription) {
    String row = Integer.toString((position / ROW_COLUMN_COUNT) + 1);
    String column = Integer.toString((position % ROW_COLUMN_COUNT) + 1);
    String spaceContentDescription =
        context.getString(R.string.game_square_content_description, markerDescription, row, column);
    gameBoard.getChildAt(position).setContentDescription(spaceContentDescription);
    return spaceContentDescription;
  }

  private boolean checkForWin(int moveLocation) {

    int row = moveLocation / ROW_COLUMN_COUNT;
    int column = moveLocation % ROW_COLUMN_COUNT;

    String currentMoveMarker = getButtonTextAtPosition(moveLocation);

    /* Check the move row for a win */
    if (currentMoveMarker.equals(getButtonTextAtPosition(row * ROW_COLUMN_COUNT))
        && currentMoveMarker.equals(getButtonTextAtPosition(row * ROW_COLUMN_COUNT + 1))
        && currentMoveMarker.equals(getButtonTextAtPosition(row * ROW_COLUMN_COUNT + 2))) {
      return true;
    }

    /* Check the move column for a win */
    if (currentMoveMarker.equals(getButtonTextAtPosition(column))
        && currentMoveMarker.equals(getButtonTextAtPosition(column + ROW_COLUMN_COUNT))
        && currentMoveMarker.equals(getButtonTextAtPosition(column + ROW_COLUMN_COUNT * 2))) {
      return true;
    }

    /*
     * If the move lies on the diagonal or anti-diagonal, check those for a win. The center
     * is part of both groups, so we can't exclude a win on the anti-diagonal by checking
     * whether the move location modulo CENTER_INDEX is not zero.
     */
    if ((moveLocation % CENTER_INDEX == 0)
        && currentMoveMarker.equals(getButtonTextAtPosition(0))
        && currentMoveMarker.equals(getButtonTextAtPosition(CENTER_INDEX))
        && currentMoveMarker.equals(getButtonTextAtPosition(CENTER_INDEX * 2))) {
      return true;
    } else if ((moveLocation % ANTI_DIAGONAL == 0)
        && currentMoveMarker.equals(getButtonTextAtPosition(ANTI_DIAGONAL))
        && currentMoveMarker.equals(getButtonTextAtPosition(CENTER_INDEX))
        && currentMoveMarker.equals(getButtonTextAtPosition(ANTI_DIAGONAL * 3))) {
      return true;
    }
    return false;
  }

  private void displayFinishDialog(String outcome) {
    setGameButtonsEnabled(false);
    resetButton.setEnabled(true);

    AlertDialog.Builder gameOverBuilder =
        new AlertDialog.Builder(
            new ContextThemeWrapper(context, R.style.SetupGuideAlertDialogStyle));
    gameOverBuilder.setTitle(outcome);
    gameOverBuilder.setMessage(context.getString(R.string.game_outcome_body_text));

    gameOverBuilder.setNegativeButton(
        context.getString(R.string.game_outcome_negative_response),
        (dialogInterface, viewId) -> {
          ((Activity) context).recreate();
          KeyAssignmentUtils.clearAllKeyPrefs(context);
          dialogInterface.cancel();
        });

    gameOverBuilder.setPositiveButton(
        context.getString(R.string.game_outcome_positive_response),
        (dialogInterface, viewId) -> dialogInterface.cancel());

    AlertDialog gameOverDialog = gameOverBuilder.create();
    gameOverDialog.show();
  }

  private void setGameButtonsEnabled(boolean setEnabled) {
    for (int i = 0; i < gameBoard.getChildCount(); i++) {
      gameBoard.getChildAt(i).setEnabled(setEnabled);
    }
    resetButton.setEnabled(setEnabled);
  }

  private String getButtonTextAtPosition(int position) {
    return ((Button) gameBoard.getChildAt(position)).getText().toString();
  }
}

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

import java.util.Collections;
import java.util.List;

/** Simple class for generating computer moves in Tic-Tac-Toe. */
public class TicTacToeComputerMoveGenerator {

  /**
   * Return a random space from the list of spaces left on the board.
   *
   * @param spacesLeft List of the integer indices of the free spaces left on the board
   * @return a random index from the list
   */
  public int generateRandomMove(List<Integer> spacesLeft) {
    Collections.shuffle(spacesLeft);
    return spacesLeft.get(0);
  }
}

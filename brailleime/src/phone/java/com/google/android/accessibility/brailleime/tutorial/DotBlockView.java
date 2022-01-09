/*
 * Copyright 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime.tutorial;

import static com.google.android.accessibility.brailleime.input.BrailleInputPlane.DOT_COUNT;
import static com.google.android.accessibility.brailleime.input.BrailleInputPlane.NUMBER_OF_COLUMNS_SCREEN_AWAY;
import static com.google.android.accessibility.brailleime.input.BrailleInputPlane.NUMBER_OF_COLUMNS_TABLETOP;
import static com.google.android.accessibility.brailleime.input.BrailleInputPlane.NUMBER_OF_ROWS_SCREEN_AWAY;
import static com.google.android.accessibility.brailleime.input.BrailleInputPlane.NUMBER_OF_ROWS_TABLETOP;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.ROTATION_270;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.util.Size;
import android.view.View;
import com.google.android.accessibility.brailleime.BrailleIme.OrientationSensitive;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.Utils;

/** View that draws yellow rectangles to separate braille dots. */
class DotBlockView extends View implements OrientationSensitive {

  private final Paint blocksPaint;
  private final Paint dashPathPaint;
  private final DashPathEffect dashPathEffectInColor1;
  private final Path path;
  private int orientation;
  private final boolean isTabletop;

  DotBlockView(Context context, int orientation, boolean isTabletop) {
    super(context);
    this.orientation = orientation;
    this.isTabletop = isTabletop;
    path = new Path();
    float pathIntervalInPixels =
        getResources().getDimensionPixelSize(R.dimen.dot_block_dash_path_interval);

    blocksPaint = new Paint();
    blocksPaint.setColor(context.getColor(R.color.braille_block_background));

    dashPathPaint = new Paint();
    dashPathPaint.setStyle(Style.STROKE);
    dashPathPaint.setStrokeWidth(
        getResources().getDimensionPixelSize(R.dimen.dot_block_dash_path_stroke_width));
    dashPathPaint.setAlpha(100);

    // Use these two dash paths to draw color interleaving border.
    dashPathEffectInColor1 =
        new DashPathEffect(
            new float[] {pathIntervalInPixels, pathIntervalInPixels}, /* phase= */ 0);
  }

  @Override
  public void onOrientationChanged(int orientation, Size screenSize) {
    this.orientation = orientation;
    invalidate();
    requestLayout();
  }

  @Override
  public void onDraw(Canvas canvas) {
    int width = getWidth();
    int height = getHeight();
    canvas.save();
    if (Utils.isPhoneSizedDevice(getResources())
        && orientation == Configuration.ORIENTATION_PORTRAIT) {
      width = getHeight();
      height = getWidth();
      canvas.rotate(ROTATION_270);
      canvas.translate(/* dx= */ -getHeight(), /* dy= */ 0);
    }

    int columnCount = isTabletop ? NUMBER_OF_COLUMNS_TABLETOP : NUMBER_OF_COLUMNS_SCREEN_AWAY;
    int rowCount = isTabletop ? NUMBER_OF_ROWS_TABLETOP : NUMBER_OF_ROWS_SCREEN_AWAY;
    int dotDiameter = 2 * getResources().getDimensionPixelSize(R.dimen.input_plane_dot_radius);
    float columnSpace = ((float) width - columnCount * dotDiameter) / (columnCount + 1);
    float rowSpace = ((float) height - rowCount * dotDiameter) / (rowCount + 1);
    int padding = getResources().getDimensionPixelSize(R.dimen.dot_block_padding);
    for (int column = 0; column < columnCount; column++) {
      float left = getPairedPosition1(dotDiameter, columnSpace, column, padding);
      float right = getPairedPosition2(dotDiameter, columnSpace, column, columnCount, padding);
      for (int row = 0; row < rowCount; row++) {
        // Draw braille block.
        float top = getPairedPosition1(dotDiameter, rowSpace, row, padding);
        float bottom = getPairedPosition2(dotDiameter, rowSpace, row, rowCount, padding);
        canvas.drawRect(left, top, right, bottom, blocksPaint);

        // Draw the border of braille block.
        path.reset();
        path.moveTo(left, top);
        path.lineTo(right, top);
        path.lineTo(right, bottom);
        path.lineTo(left, bottom);
        path.lineTo(left, top);
        dashPathPaint.setPathEffect(dashPathEffectInColor1);
        dashPathPaint.setColor(getContext().getColor(R.color.braille_block_dash_path));
        canvas.drawPath(path, dashPathPaint);
      }
    }
    canvas.restore();
  }

  private static float getPairedPosition1(
      int dotDiameter, float columnOrRowSpace, int columnOrRowIndex, int padding) {
    return columnOrRowSpace * columnOrRowIndex
        + (columnOrRowSpace / 2)
            * ((columnOrRowIndex + 1) / (2 + 2 * (columnOrRowIndex / (DOT_COUNT / 2))))
        + dotDiameter * columnOrRowIndex
        + padding;
  }

  private static float getPairedPosition2(
      int dotDiameter,
      float columnOrRowSpace,
      int columnOrRowIndex,
      int columnOrRowCount,
      int padding) {
    return columnOrRowSpace * (columnOrRowIndex + 1)
        + (columnOrRowSpace / 2)
        + (columnOrRowSpace / 2) * ((columnOrRowIndex + 1) / columnOrRowCount)
        + dotDiameter * (columnOrRowIndex + 1)
        - padding;
  }
}

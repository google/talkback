/*
 * Copyright (C) 2013 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * View which displays Braille dot patterns and corresponding text. Requires a mapping between the
 * two to provide proper alignment. TODO: Draw dots using drawText (on braille unicode
 * points), instead of drawCircle.
 */
public class BrailleView extends View {

  /** Interface for listening to taps on Braille cells. */
  public interface OnBrailleCellClickListener {
    void onBrailleCellClick(BrailleView view, int cellIndex);
  }

  /**
   * Interface for listening to width changes, expressed in terms of the number of Braille cells
   * that can be displayed.
   */
  public interface OnResizeListener {
    void onResize(int maxNumTextCells);
  }

  private static final int HIGHLIGHT_TIME_MS = 300;
  private static final int DIMMED_ALPHA = 0x40;
  private static final float[] DOT_POSITIONS = {
    0.0f, 0.00f, /* dot 1 */
    0.0f, 0.33f, /* dot 2 */
    0.0f, 0.67f, /* dot 3 */
    1.0f, 0.00f, /* dot 4 */
    1.0f, 0.33f, /* dot 5 */
    1.0f, 0.67f, /* dot 6 */
    0.0f, 1.00f, /* dot 7 */
    1.0f, 1.00f /* dot 8 */
  };

  private final Runnable clearHighlightedCell =
      new Runnable() {
        @Override
        public void run() {
          highlightedCell = -1;
          invalidate();
        }
      };
  private final Paint primaryPaint;
  private final Paint secondaryPaint;
  private final Drawable highlightDrawable;
  private final float dotRadius;
  private final float cellPadding;
  private final float cellWidth;
  private final float cellHeight;
  private final float outerWidth;
  private final float outerHeight;
  private final int touchSlop;

  private volatile OnResizeListener resizeListener;
  private volatile OnBrailleCellClickListener brailleCellClickListener;
  private int numTextCells = 0;
  private byte[] braille = new byte[0];
  private CharSequence text = "";
  private int[] brailleToTextPositions = new int[0];
  private int maxNumTextCells = 0;
  private int highlightedCell = -1;
  private int pressedCell = -1;

  public BrailleView(Context context, AttributeSet attrs) {
    super(context, attrs);

    TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.BrailleView, 0, 0);
    try {
      primaryPaint = new Paint();
      primaryPaint.setAntiAlias(true);
      primaryPaint.setColor(a.getColor(R.styleable.BrailleView_android_textColor, 0xFFFFFFFF));
      primaryPaint.setTextSize(a.getDimension(R.styleable.BrailleView_android_textSize, 20.0f));
      primaryPaint.setTextAlign(Paint.Align.CENTER);
      secondaryPaint = new Paint(primaryPaint);
      secondaryPaint.setAlpha(DIMMED_ALPHA);
      highlightDrawable = a.getDrawable(R.styleable.BrailleView_highlightDrawable);
      dotRadius = a.getDimension(R.styleable.BrailleView_dotRadius, 4.0f);
      cellWidth = a.getDimension(R.styleable.BrailleView_cellWidth, 10.0f);
      cellHeight = a.getDimension(R.styleable.BrailleView_cellHeight, 30.0f);
      cellPadding = a.getDimension(R.styleable.BrailleView_cellPadding, 13.0f);
      outerWidth = cellWidth + 2 * cellPadding;
      outerHeight = cellHeight + 2 * cellPadding;
      touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    } finally {
      a.recycle();
    }
  }

  public void displayDots(byte[] braille, CharSequence text, int[] brailleToTextPositions) {
    this.braille = braille;
    this.text = text;
    this.brailleToTextPositions = brailleToTextPositions;
    invalidate();
  }

  /** Sets the number of cells to display. */
  public void setTextCellNum(int numTextCells) {
    if (this.numTextCells != numTextCells) {
      this.numTextCells = numTextCells;
      requestLayout();
    }
  }

  public void setOnResizeListener(OnResizeListener listener) {
    resizeListener = listener;
  }

  public void highlightCell(int cellIndex) {
    highlightedCell = cellIndex;
    removeCallbacks(clearHighlightedCell);
    postDelayed(clearHighlightedCell, HIGHLIGHT_TIME_MS);
    invalidate();
  }

  public void setOnBrailleCellClickListener(OnBrailleCellClickListener listener) {
    brailleCellClickListener = listener;
  }

  public void cancelPendingTouches() {
    if (pressedCell != -1) {
      pressedCell = -1;
      invalidate();
    }
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec) {
    Paint.FontMetrics metrics = primaryPaint.getFontMetrics();

    int width =
        MeasureSpec.getMode(widthSpec) == MeasureSpec.UNSPECIFIED
            ? Math.round(numTextCells * outerWidth)
            : MeasureSpec.getSize(widthSpec);
    setMeasuredDimension(
        width, Math.round(outerHeight + cellPadding - metrics.ascent + metrics.descent));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    // Determine the actual rectangle on which the dots will be rendered.
    // We assume 8-dot braille; 6-dot braille will simply have empty bottom
    // dots. So this rectangle must be 3 times as tall as it is wide.
    float innerWidth;
    float innerHeight;
    float offsetX;
    float offsetY;
    if (3 * cellWidth >= cellHeight) {
      // Add more horizontal padding.
      innerWidth = cellHeight / 3;
      innerHeight = cellHeight;
      offsetX = cellPadding + (cellWidth - innerWidth) / 2;
      offsetY = cellPadding;
    } else {
      // Add more vertical padding.
      innerWidth = cellWidth;
      innerHeight = cellWidth * 3;
      offsetX = cellPadding;
      offsetY = cellPadding + (cellHeight - innerHeight) / 2;
    }

    // Draw the highlighted cell.
    if (highlightedCell >= 0 && highlightedCell < numTextCells && highlightDrawable != null) {
      highlightDrawable.setBounds(
          Math.round(highlightedCell * outerWidth),
          0,
          Math.round((highlightedCell + 1) * outerWidth),
          Math.round(outerHeight));
      highlightDrawable.draw(canvas);
    }

    // Draw the pressed cell, if different.
    if (pressedCell >= 0
        && pressedCell < numTextCells
        && pressedCell != highlightedCell
        && highlightDrawable != null) {
      highlightDrawable.setBounds(
          Math.round(pressedCell * outerWidth),
          0,
          Math.round((pressedCell + 1) * outerWidth),
          Math.round(outerHeight));
      highlightDrawable.draw(canvas);
    }

    // Draw dot patterns.
    // Note that braille.length may not match numTextCells.
    for (int i = 0; i < numTextCells; i++) {
      canvas.save();
      canvas.translate(i * outerWidth, 0);
      canvas.clipRect(0, 0, outerWidth, outerHeight);
      int pattern = i < braille.length ? (braille[i] & 0xFF) : 0x00;
      for (int j = 0; j < DOT_POSITIONS.length; j += 2) {
        float x = offsetX + DOT_POSITIONS[j] * innerWidth;
        float y = offsetY + DOT_POSITIONS[j + 1] * innerHeight;
        Paint paint = (pattern & 1) != 0 ? primaryPaint : secondaryPaint;
        canvas.drawCircle(x, y, dotRadius, paint);
        pattern = pattern >> 1;
      }
      canvas.restore();
    }

    // Draw corresponding text.
    Paint.FontMetrics metrics = primaryPaint.getFontMetrics();
    int brailleIndex = 0;
    while (brailleIndex < numTextCells && brailleIndex < brailleToTextPositions.length) {
      int brailleStart = brailleIndex;
      int textStart = brailleToTextPositions[brailleStart];
      do {
        brailleIndex++;
      } while (brailleIndex < brailleToTextPositions.length
          && brailleToTextPositions[brailleIndex] <= textStart);
      int brailleEnd = brailleIndex;
      int textEnd =
          brailleEnd < brailleToTextPositions.length
              ? brailleToTextPositions[brailleEnd]
              : text.length();

      float clipLeft = outerWidth * brailleStart;
      float clipRight = outerWidth * brailleEnd;
      float clipTop = outerHeight;
      float clipBottom = clipTop + cellPadding - metrics.ascent + metrics.descent + cellPadding;
      float x = (clipLeft + clipRight) / 2;
      float y = clipTop - metrics.ascent;
      float measuredWidth = primaryPaint.measureText(text, textStart, textEnd);
      if (measuredWidth > clipRight - clipLeft) {
        primaryPaint.setTextScaleX((clipRight - clipLeft) / measuredWidth);
      }
      canvas.save();
      canvas.clipRect(clipLeft, clipTop, clipRight, clipBottom);
      canvas.drawText(text, textStart, textEnd, x, y, primaryPaint);
      canvas.restore();
      primaryPaint.setTextScaleX(1.0f);
    }
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldW, int oldH) {
    int newMaxNumTextCells = (int) (w / outerWidth);
    if (newMaxNumTextCells == maxNumTextCells) {
      return;
    }
    maxNumTextCells = newMaxNumTextCells;
    OnResizeListener localListener = resizeListener;
    if (localListener != null) {
      localListener.onResize(newMaxNumTextCells);
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        int cellIndex = (int) (event.getX() / outerWidth);
        if (0 <= cellIndex && cellIndex < numTextCells) {
          pressedCell = cellIndex;
          invalidate();
        } else {
          cancelPendingTouches();
        }
        break;

      case MotionEvent.ACTION_UP:
        if (withinTouchSlopOfCell(event, pressedCell)) {
          reportBrailleCellClick(pressedCell);
        }
        cancelPendingTouches();
        break;

      case MotionEvent.ACTION_CANCEL:
        cancelPendingTouches();
        break;

      case MotionEvent.ACTION_MOVE:
        if (!withinTouchSlopOfCell(event, pressedCell)) {
          cancelPendingTouches();
        }
        break;
    }
    return false;
  }

  private boolean withinTouchSlopOfCell(MotionEvent event, int cellIndex) {
    if (0 <= cellIndex && cellIndex < numTextCells) {
      float x = event.getX();
      return (cellIndex * outerWidth - touchSlop) <= x
          && x <= ((cellIndex + 1) * outerWidth + touchSlop);
    } else {
      return false;
    }
  }

  private void reportBrailleCellClick(int cellIndex) {
    OnBrailleCellClickListener localListener = brailleCellClickListener;
    if (localListener != null) {
      localListener.onBrailleCellClick(this, cellIndex);
    }
  }
}

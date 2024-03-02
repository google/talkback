/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.overlay;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.BadTokenException;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.widget.SimpleOverlay;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

public class BlockOutOverlay extends SimpleOverlay {
  static View highlightView;
  private AccessibilityNodeInfoCompat focusedNode;

  public void refresh(AccessibilityNodeInfoCompat node) {
    highlightView.setVisibility(View.VISIBLE);
    try {
      show();
    } catch (BadTokenException e) {
      LogUtils.e(
              "BlurOutOverlay Overlay", e, "Caught WindowManager.BadTokenException while displaying text.");
    }
    focusedNode = node;
    highlightView.invalidate();
  }

  public class BlurOutHighlightView extends View {
    private final Paint blackPaint = new Paint();
    private final Paint cutPaint = new Paint();

    public BlurOutHighlightView(Context context) {
      super(context);
      blackPaint.setColor(Color.BLACK);
      blackPaint.setStyle(Style.FILL_AND_STROKE);
      //blackPaint.setMaskFilter(new BlurMaskFilter(100, BlurMaskFilter.Blur.SOLID));

      cutPaint.setColor(Color.TRANSPARENT);
      cutPaint.setStyle(Style.FILL);
      cutPaint.setStrokeWidth(context.getResources().getDimensionPixelSize(R.dimen.highlight_overlay_border));
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        cutPaint.setBlendMode(BlendMode.DST_IN);
        blackPaint.setBlendMode(BlendMode.SRC);
      }
    }

    @Override
    public void onDraw(@NonNull Canvas canvas) {
      if (focusedNode != null) {
        focusedNode.getBoundsInScreen(nodeBounds);
        //blockOutExceptFocus(canvas, nodeBounds);
        blockOutEntireScreen(canvas);
      }
    }

    private void blockOutExceptFocus(Canvas canvas, Rect rectOnScreen) {
      // Adjust location by overlay position on screen.
      int[] overlayScreenXY = {0, 0};
      highlightView.getLocationOnScreen(overlayScreenXY);
      Rect rectInHighlightView = moveRect(rectOnScreen, -overlayScreenXY[0], -overlayScreenXY[1]);

      highlightView.setBackgroundColor(blackPaint.getColor());
      canvas.drawRect(rectInHighlightView, cutPaint);
    }

    private void blockOutEntireScreen(Canvas canvas) {
      // Adjust location by overlay position on screen.
      int[] overlayScreenXY = {0, 0};
      highlightView.getLocationOnScreen(overlayScreenXY);
      highlightView.setBackgroundColor(blackPaint.getColor());
      canvas.drawRect(0,0,500,500, blackPaint);
    }
  }

  private static Rect moveRect(Rect rect, int deltaX, int deltaY) {
    return new Rect(
            rect.left + deltaX,
            rect.top + deltaY,
            rect.right + deltaX,
            rect.bottom + deltaY
    );
  }

  public BlockOutOverlay(Context context) {
    super(context, 0, false);

    final WindowManager windowManager =
            (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    final WindowManager.LayoutParams layPar = new WindowManager.LayoutParams();
    layPar.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    layPar.format = PixelFormat.TRANSLUCENT;
    layPar.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    layPar.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    layPar.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    layPar.width = ViewGroup.LayoutParams.MATCH_PARENT;
    layPar.height = ViewGroup.LayoutParams.MATCH_PARENT;

    final ViewGroup layout = new FrameLayout(context);
    highlightView = new BlurOutHighlightView(context);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(layPar.width, layPar.height);
    highlightView.setLayoutParams(params);
    highlightView.setVisibility(View.INVISIBLE);
    layout.addView(highlightView);
    setContentView(layout);
    setParams(layPar);
  }

  public void removeHighlight() {
    highlightView.setVisibility(View.INVISIBLE);
    hide();
  }

  private Rect nodeBounds = new Rect();
}
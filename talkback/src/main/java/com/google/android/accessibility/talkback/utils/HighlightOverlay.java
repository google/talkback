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

package com.google.android.accessibility.talkback.utils;

import static com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_FAIL_ALL_FOCUS_TESTS;
import static com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_NOT_SPEAKABLE;
import static com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_NOT_VISIBLE;
import static com.google.android.accessibility.utils.DiagnosticOverlayUtils.FOCUS_FAIL_SAME_WINDOW_BOUNDS_CHILDREN;

import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.BadTokenException;
import android.widget.FrameLayout;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.DiagnosticOverlayUtils.DiagnosticType;
import com.google.android.accessibility.utils.widget.SimpleOverlay;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;

/** Highlights for clickability of nodes and for nodes that were traversed but not focused */
public class HighlightOverlay extends SimpleOverlay {
  private static final float HIGHLIGHT_ALPHA = 0.45f;
  static View highlightView;
  private static int ORANGE = 0xFFFFA500;

  /** {@code unfocusableNodes} obtained in
   * {@link DiagnosticOverlayControllerImpl#appendLog and
   * @link DiagnosticOverlayControllerImpl#clearUnfocusedNodes()} respectively}}*/
  private HashMap<Integer, ArrayList<AccessibilityNodeInfoCompat>> unfocusableNodes = null;

  /** {@code focusednode} obtained in {@link DiagnosticOverlayControllerImpl#appendLog(Feedback)} */
  private AccessibilityNodeInfoCompat focusedNode;

  /** Highlights multiple nodes */
  public class MultipleHighlightView extends View {
    private final Paint clickablePaint = new Paint();
    private final Paint nonClickablePaint = new Paint();
    private final Paint unfocusablePaint = new Paint();
    private final Paint borderPaint = new Paint();

    public MultipleHighlightView(Context context) {
      super(context);

      /** Use {@link BlendMode#DST_OUT for clickable highlight if larger screen focusability
       * can be filtered out*/
      clickablePaint.setColor(Color.GREEN);
      clickablePaint.setBlendMode(BlendMode.COLOR);
      nonClickablePaint.setColor(Color.BLUE);
      nonClickablePaint.setBlendMode(BlendMode.COLOR);

      unfocusablePaint.setStyle(Style.FILL);
      unfocusablePaint.setBlendMode(BlendMode.OVERLAY);
      // Paint requires one to draw same rectangle twice for different colored borders - once
      // w/ fill and once w/ stroke
      borderPaint.setColor(Color.BLACK);
      borderPaint.setStyle(Style.STROKE);
      borderPaint.setBlendMode(BlendMode.DARKEN);
      borderPaint.setStrokeWidth(
          context.getResources().getDimensionPixelSize(R.dimen.highlight_overlay_border));
    }

    /** Draws color-coded unfocused/clickable nodes onto {@code canvas} defined as device screen */
    @Override
    public void onDraw(Canvas canvas) {
      if (unfocusableNodes != null) {
        unfocusablePaint.setColor(Color.RED);
        processUnfocusableNodes(FOCUS_FAIL_FAIL_ALL_FOCUS_TESTS, canvas);

        unfocusablePaint.setColor(Color.MAGENTA);
        processUnfocusableNodes(FOCUS_FAIL_NOT_SPEAKABLE, canvas);

        unfocusablePaint.setColor(Color.YELLOW);
        processUnfocusableNodes(FOCUS_FAIL_NOT_VISIBLE, canvas);

        unfocusablePaint.setColor(ORANGE);
        processUnfocusableNodes(FOCUS_FAIL_SAME_WINDOW_BOUNDS_CHILDREN, canvas);
      }
      Rect clickableBounds = new Rect();
      focusedNode.getBoundsInScreen(clickableBounds);

      boolean clickable = focusedNode.isClickable();
      if (clickable) {
        canvas.drawRect(clickableBounds, clickablePaint);
      } else {
        canvas.drawRect(clickableBounds, nonClickablePaint);
      }
    }

    private void processUnfocusableNodes(@DiagnosticType Integer type, Canvas canvas) {
      ArrayList<AccessibilityNodeInfoCompat> currentNodes =
          new ArrayList<AccessibilityNodeInfoCompat>();
      currentNodes = unfocusableNodes.get(type);
      if (currentNodes != null) {
        for (AccessibilityNodeInfoCompat node : currentNodes) {
          Rect r = new Rect();
          node.getBoundsInScreen(r);
          canvas.drawRect(r, unfocusablePaint);
          canvas.drawRect(r, borderPaint);
        }
      }
    }
  }

  public HighlightOverlay(Context context) {
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
    highlightView = new MultipleHighlightView(context);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(layPar.width, layPar.height);
    highlightView.setLayoutParams(params);
    highlightView.setVisibility(View.INVISIBLE);
    highlightView.setAlpha(HIGHLIGHT_ALPHA);
    layout.addView(highlightView);
    setContentView(layout);
    setParams(layPar);
  }

  public void highlightNodesOnScreen(
      AccessibilityNodeInfoCompat focusedNode,
      HashMap<Integer, ArrayList<AccessibilityNodeInfoCompat>> unfocusedNodeList) {
    highlightView.setVisibility(View.VISIBLE);
    try {
      show();
    } catch (BadTokenException e) {
      LogUtils.e(
          "Highlight Overlay", e, "Caught WindowManager.BadTokenException while displaying text.");
    }
    unfocusableNodes = unfocusedNodeList;
    this.focusedNode = focusedNode;
    // calling invalidate will update highlightView
    highlightView.invalidate();
  }

  public void clearHighlight() {
    highlightView.setVisibility(View.INVISIBLE);
    hide();
  }
}

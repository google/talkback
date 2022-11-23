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
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.DiagnosticOverlayUtils.DiagnosticType;
import com.google.android.accessibility.utils.widget.SimpleOverlay;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

/** Highlights for clickability of nodes and for nodes that were traversed but not focused */
public class HighlightOverlay extends SimpleOverlay {
  private static final float HIGHLIGHT_ALPHA = 0.25f;
  static View highlightView;

  // Nodes passed over during sequential navigation
  private HashMap<Integer, ArrayList<AccessibilityNodeInfoCompat>> skippedNodes = null;

  // Nodes on refocus node path
  private HashSet<AccessibilityNode> refocusNodePath = null;

  // Node currently focused
  private AccessibilityNodeInfoCompat focusedNode;

  /** Highlights multiple nodes */
  public class MultipleHighlightView extends View {
    private final Paint refocusPaint = new Paint();
    private final Paint skippedNodePaint = new Paint();
    private final Paint borderPaint = new Paint();

    public MultipleHighlightView(Context context) {
      super(context);

      /** Use {@link BlendMode#DST_OUT for clickable highlight if larger screen focusability
       * can be filtered out*/
      refocusPaint.setColor(Color.GREEN);
      refocusPaint.setBlendMode(BlendMode.COLOR);

      skippedNodePaint.setStyle(Style.FILL);
      skippedNodePaint.setBlendMode(BlendMode.OVERLAY);
      // Paint requires one to draw same rectangle twice for different colored borders - once
      // w/ fill and once w/ stroke
      borderPaint.setColor(Color.BLACK);
      borderPaint.setStyle(Style.STROKE);
      borderPaint.setBlendMode(BlendMode.DARKEN);
      borderPaint.setStrokeWidth(
          context.getResources().getDimensionPixelSize(R.dimen.highlight_overlay_border));
    }

    @Override
    public void onDraw(Canvas canvas) {
      if (skippedNodes != null) {
        skippedNodePaint.setColor(Color.RED);
        processUnfocusableNodes(FOCUS_FAIL_FAIL_ALL_FOCUS_TESTS, canvas);
        processUnfocusableNodes(FOCUS_FAIL_NOT_SPEAKABLE, canvas);
        processUnfocusableNodes(FOCUS_FAIL_NOT_VISIBLE, canvas);
        processUnfocusableNodes(FOCUS_FAIL_SAME_WINDOW_BOUNDS_CHILDREN, canvas);
      }
      if (refocusNodePath != null) {
        for (AccessibilityNode node : refocusNodePath) {
          Rect nodeBounds = new Rect();
          node.getBoundsInScreen(nodeBounds);
          drawRectangle(canvas, nodeBounds, refocusPaint);
        }
      }
    }

    private void processUnfocusableNodes(@DiagnosticType Integer type, Canvas canvas) {
      ArrayList<AccessibilityNodeInfoCompat> currentNodes =
          new ArrayList<AccessibilityNodeInfoCompat>();
      currentNodes = skippedNodes.get(type);
      if (currentNodes != null) {
        for (AccessibilityNodeInfoCompat node : currentNodes) {
          Rect r = new Rect();
          node.getBoundsInScreen(r);
          drawRectangle(canvas, r, skippedNodePaint);
        }
      }
    }

    private void drawRectangle(Canvas canvas, Rect rectOnScreen, Paint paint) {
      // Adjust location by overlay position on screen.
      int[] overlayScreenXY = {0, 0};
      highlightView.getLocationOnScreen(overlayScreenXY);
      Rect rectInHighlightView = moveRect(rectOnScreen, -overlayScreenXY[0], -overlayScreenXY[1]);

      // Draw fill and outline.
      canvas.drawRect(rectInHighlightView, paint);
      canvas.drawRect(rectInHighlightView, borderPaint);
    }
  }

  private static Rect moveRect(Rect rect, int deltaX, int deltaY) {
    return new Rect(
        rect.left + deltaX, rect.top + deltaY, rect.right + deltaX, rect.bottom + deltaY);
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
      HashMap<Integer, ArrayList<AccessibilityNodeInfoCompat>> skippedNodes,
      HashSet<AccessibilityNode> refocusNodePath) {
    highlightView.setVisibility(View.VISIBLE);
    try {
      show();
    } catch (BadTokenException e) {
      LogUtils.e(
          "Highlight Overlay", e, "Caught WindowManager.BadTokenException while displaying text.");
    }
    this.skippedNodes = skippedNodes;
    this.refocusNodePath = refocusNodePath;
    this.focusedNode = focusedNode;
    // calling invalidate will update highlightView
    highlightView.invalidate();
  }

  public void clearHighlight() {
    highlightView.setVisibility(View.INVISIBLE);
    hide();
  }
}

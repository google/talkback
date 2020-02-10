/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.accessibility.talkback.contextmenu;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.GradientDrawable.Orientation;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.ExploreByTouchObjectHelper;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** RadialMenuView class */
public class RadialMenuView extends SurfaceView {

  private static final String TAG = "RadialMenuView";

  /** SubMenuMode enum class */
  public enum SubMenuMode {
    /** Activate sub-menus by touching them for an extended time. */
    LONG_PRESS,
    /** Activate sub-menus by touching them and lifting a finger. */
    LIFT_TO_ACTIVATE
  }

  /** String used to ellipsize text. */
  private static final String ELLIPSIS = "…";

  /** The point at which the user started touching the menu. */
  private final PointF entryPoint = new PointF();

  /** Temporary matrix used for calculations. */
  private final Matrix tempMatrix = new Matrix();

  // Cached bounds.
  private final RectF cachedOuterBound = new RectF();
  private final RectF cachedCornerBound = new RectF();
  private final RectF cachedExtremeBound = new RectF();

  // Cached paths representing a single item.
  private final Path cachedOuterPath = new Path();
  private final Path cachedOuterPathReverse = new Path();
  private final Path cachedCornerPath = new Path();
  private final Path cachedCornerPathReverse = new Path();

  // Cached widths for rendering text on paths.
  private float cachedOuterPathWidth;
  private float cachedCornerPathWidth;

  /** The root menu represented by this view. */
  private final RadialMenu rootMenu;

  /** The paint used to draw this view. */
  private final Paint paint;

  /** The long-press handler for this view. */
  private final LongPressHandler handler;

  /** The background drawn below the radial menu. */
  private GradientDrawable gradientBackground;

  /** The maximum radius allowable for a single-tap gesture. */
  private final int singleTapRadiusSq;

  // Dimensions loaded from context.
  private final int innerRadius;
  private final int outerRadius;
  private final int cornerRadius;
  private final int extremeRadius;
  private final int spacing;
  private final int textSize;
  private final int textShadowRadius;
  private final int shadowRadius;

  // Generated from dimensions.
  private final int innerRadiusSq;
  private final int extremeRadiusSq;

  // Colors loaded from context.
  private final int outerFillColor;
  private final int textFillColor;
  private final int cornerFillColor;
  private final int cornerTextFillColor;
  private final int dotFillColor;
  private final int dotStrokeColor;
  private final int selectionColor;
  private final int selectionTextFillColor;
  private final int selectionShadowColor;
  private final int centerFillColor;
  private final int centerTextFillColor;
  private final int textShadowColor;

  private final DashPathEffect dotPathEffect = new DashPathEffect(new float[] {20, 20}, 0);

  private static final float DOT_STROKE_WIDTH = 5;

  // Color filters for modal content.
  private final ColorFilter subMenuFilter;

  /**
   * Whether to use a node provider. If set to {@code false}, converts hover events to touch events
   * and does not send any accessibility events.
   */
  private final boolean useNodeProvider;

  /** The current interaction mode for activating elements. */
  private SubMenuMode subMenuMode = SubMenuMode.LIFT_TO_ACTIVATE;

  /** The surface holder onto which the view is drawn. */
  @Nullable private SurfaceHolder holder;

  /** The currently focused item. */
  @Nullable private RadialMenuItem focusedItem;

  /** The currently displayed sub-menu, if any. */
  @Nullable private RadialSubMenu subMenu;

  /**
   * The offset of the current sub-menu, in degrees. Used to align the sub-menu with its parent menu
   * item.
   */
  private float subMenuOffset;

  /**
   * The offset of the root menu, in degrees. Used to align the first menu item at the top or right
   * of the radial menu.
   */
  private float rootMenuOffset = 0;

  /** The center point of the radial menu. */
  private PointF center = new PointF();

  /** Whether to display the "carrot" dot. */
  private boolean displayWedges;

  /** Whether the current touch might be a single tap gesture. */
  private boolean maybeSingleTap;

  public RadialMenuView(Context context, RadialMenu menu, boolean useNodeProvider) {
    super(context);

    rootMenu = menu;
    rootMenu.setLayoutListener(
        new RadialMenu.MenuLayoutListener() {
          @Override
          public void onLayoutChanged() {
            invalidate();
          }
        });

    paint = new Paint();
    paint.setAntiAlias(true);

    handler = new LongPressHandler(context);
    handler.setListener(
        new LongPressHandler.LongPressListener() {
          @Override
          public void onLongPress() {
            onItemLongPressed(focusedItem);
          }
        });

    final SurfaceHolder holder = getHolder();
    holder.setFormat(PixelFormat.TRANSLUCENT);
    holder.addCallback(
        new SurfaceHolder.Callback() {
          @Override
          public void surfaceCreated(SurfaceHolder holder) {
            RadialMenuView.this.holder = holder;
          }

          @Override
          public void surfaceDestroyed(SurfaceHolder holder) {
            RadialMenuView.this.holder = null;
          }

          @Override
          public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            invalidate();
          }
        });

    final Resources res = context.getResources();
    final ViewConfiguration config = ViewConfiguration.get(context);

    singleTapRadiusSq = config.getScaledTouchSlop();

    // Dimensions.
    innerRadius = res.getDimensionPixelSize(R.dimen.inner_radius);
    outerRadius = res.getDimensionPixelSize(R.dimen.outer_radius);
    cornerRadius = res.getDimensionPixelSize(R.dimen.corner_radius);
    extremeRadius = res.getDimensionPixelSize(R.dimen.extreme_radius);
    spacing = res.getDimensionPixelOffset(R.dimen.spacing);
    textSize = res.getDimensionPixelSize(R.dimen.text_size);
    textShadowRadius = res.getDimensionPixelSize(R.dimen.text_shadow_radius);
    shadowRadius = res.getDimensionPixelSize(R.dimen.shadow_radius);

    // Colors.
    outerFillColor = res.getColor(R.color.outer_fill);
    textFillColor = res.getColor(R.color.text_fill);
    cornerFillColor = res.getColor(R.color.corner_fill);
    cornerTextFillColor = res.getColor(R.color.corner_text_fill);
    dotFillColor = res.getColor(R.color.dot_fill);
    dotStrokeColor = res.getColor(R.color.dot_stroke);
    selectionColor = res.getColor(R.color.selection_fill);
    selectionTextFillColor = res.getColor(R.color.selection_text_fill);
    selectionShadowColor = res.getColor(R.color.selection_shadow);
    centerFillColor = res.getColor(R.color.center_fill);
    centerTextFillColor = res.getColor(R.color.center_text_fill);
    textShadowColor = res.getColor(R.color.text_shadow);

    // Gradient colors.
    final int gradientInnerColor = res.getColor(R.color.gradient_inner);
    final int gradientOuterColor = res.getColor(R.color.gradient_outer);
    final int[] colors = new int[] {gradientInnerColor, gradientOuterColor};
    gradientBackground = new GradientDrawable(Orientation.TOP_BOTTOM, colors);
    gradientBackground.setGradientType(GradientDrawable.RADIAL_GRADIENT);
    gradientBackground.setGradientRadius(extremeRadius * 2.0f);

    final int subMenuOverlayColor = res.getColor(R.color.submenu_overlay);

    // Lighting filters generated from colors.
    subMenuFilter = new PorterDuffColorFilter(subMenuOverlayColor, PorterDuff.Mode.SCREEN);

    innerRadiusSq = (innerRadius * innerRadius);
    extremeRadiusSq = (extremeRadius * extremeRadius);

    this.useNodeProvider = useNodeProvider;

    if (this.useNodeProvider) {
      // Lazily-constructed node provider helper.
      ViewCompat.setAccessibilityDelegate(this, new RadialMenuHelper(this));
    }

    // Corner shapes only need to be invalidated and cached once.
    initializeCachedShapes();
  }

  /**
   * Sets the sub-menu activation mode.
   *
   * @param subMenuMode A sub-menu activation mode.
   */
  public void setSubMenuMode(SubMenuMode subMenuMode) {
    this.subMenuMode = subMenuMode;
  }

  /** Displays a dot at the center of the screen and draws the corner menu items. */
  public void displayDot() {
    displayWedges = false;
    subMenu = null;
    focusedItem = null;

    invalidate();
  }

  /**
   * Displays the menu centered at the specified coordinates.
   *
   * @param centerX The center X coordinate.
   * @param centerY The center Y coordinate.
   */
  public void displayAt(float centerX, float centerY) {
    center.x = centerX;
    center.y = centerY;

    displayWedges = true;
    subMenu = null;
    focusedItem = null;

    invalidate();
  }

  /** Re-draw cached wedge bitmaps. */
  private void invalidateCachedWedgeShapes() {
    final RadialMenu menu = subMenu != null ? subMenu : rootMenu;
    final int menuSize = menu.size();
    if (menuSize <= 0) {
      return;
    }

    final float wedgeArc = (360.0f / menuSize);
    final float offsetArc = ((wedgeArc / 2.0f) + 90.0f);
    final float spacingArc = (float) Math.toDegrees(Math.tan(spacing / (double) outerRadius));
    final float left = (wedgeArc - spacingArc - offsetArc);
    final float center = ((wedgeArc / 2.0f) - offsetArc);
    final float right = (spacingArc - offsetArc);

    // Outer wedge.
    cachedOuterPath.rewind();
    cachedOuterPath.arcTo(cachedOuterBound, center, (left - center));
    cachedOuterPath.arcTo(cachedExtremeBound, left, (right - left));
    cachedOuterPath.arcTo(cachedOuterBound, right, (center - right));
    cachedOuterPath.close();

    cachedOuterPathWidth = arcLength((left - right), extremeRadius);

    // Outer wedge in reverse, for rendering text.
    cachedOuterPathReverse.rewind();
    cachedOuterPathReverse.arcTo(cachedOuterBound, center, (right - center));
    cachedOuterPathReverse.arcTo(cachedExtremeBound, right, (left - right));
    cachedOuterPathReverse.arcTo(cachedOuterBound, left, (center - left));
    cachedOuterPathReverse.close();
  }

  /**
   * Initialized cached bounds and corner shapes.
   *
   * <p><b>Note:</b> This method should be called whenever a radius value changes. It must be called
   * before any calls are made to {@link #invalidateCachedWedgeShapes()}.
   */
  private void initializeCachedShapes() {
    final int diameter = (extremeRadius * 2);

    createBounds(cachedOuterBound, diameter, outerRadius);
    createBounds(cachedCornerBound, diameter, cornerRadius);
    createBounds(cachedExtremeBound, diameter, extremeRadius);

    final float cornerWedgeArc = 90.0f;
    final float cornerOffsetArc = ((cornerWedgeArc / 2.0f) + 90.0f);
    final float cornerLeft = (cornerWedgeArc - cornerOffsetArc);
    final float cornerCenter = ((cornerWedgeArc / 2.0f) - cornerOffsetArc);
    final float cornerRight = -cornerOffsetArc;

    // Corner wedge.
    cachedCornerPath.rewind();
    cachedCornerPath.arcTo(cachedCornerBound, cornerCenter, (cornerLeft - cornerCenter));
    cachedCornerPath.arcTo(cachedExtremeBound, cornerLeft, (cornerRight - cornerLeft));
    cachedCornerPath.arcTo(cachedCornerBound, cornerRight, (cornerCenter - cornerRight));
    cachedCornerPath.close();

    cachedCornerPathWidth = arcLength((cornerLeft - cornerRight), extremeRadius);

    // Corner wedge in reverse, for rendering text.
    cachedCornerPathReverse.rewind();
    cachedCornerPathReverse.arcTo(cachedCornerBound, cornerCenter, (cornerRight - cornerCenter));
    cachedCornerPathReverse.arcTo(cachedExtremeBound, cornerRight, (cornerLeft - cornerRight));
    cachedCornerPathReverse.arcTo(cachedCornerBound, cornerLeft, (cornerCenter - cornerLeft));
    cachedCornerPathReverse.close();
  }

  @Override
  public void invalidate() {
    super.invalidate();

    final SurfaceHolder holder = this.holder;
    if (holder == null) {
      return;
    }

    final Canvas canvas = holder.lockCanvas();
    if (canvas == null) {
      return;
    }

    // Clear the canvas.
    canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);

    if (getVisibility() != View.VISIBLE) {
      holder.unlockCanvasAndPost(canvas);
      return;
    }

    final int width = getWidth();
    final int height = getHeight();

    if (!displayWedges) {
      this.center.x = (width / 2.0f);
      this.center.y = (height / 2.0f);
    }

    // Draw the pretty gradient background.
    gradientBackground.setGradientCenter((this.center.x / width), (this.center.y / height));
    gradientBackground.setBounds(0, 0, width, height);
    gradientBackground.draw(canvas);

    final RadialMenu menu = (subMenu != null) ? subMenu : rootMenu;
    final float center = extremeRadius;

    if (displayWedges) {
      final int wedges = menu.size();
      final float degrees = 360.0f / wedges;

      // Refresh cached wedge shapes if necessary.
      if (0 != menu.size()) {
        invalidateCachedWedgeShapes();
      }

      // Draw the cancel dot.
      drawCancel(canvas);

      // Draw wedges.
      for (int i = 0; i < wedges; i++) {
        drawWedge(canvas, center, i, menu, degrees);
      }
    } else {
      // Draw the center dot.
      drawCenterDot(canvas, width, height);
    }

    // Draw corners.
    for (int i = 0; i < 4; i++) {
      drawCorner(canvas, width, height, center, i);
    }

    holder.unlockCanvasAndPost(canvas);
  }

  private void drawCenterDot(Canvas canvas, int width, int height) {
    final float centerX = (width / 2.0f);
    final float centerY = (height / 2.0f);
    final float radius = innerRadius;
    final RectF dotBounds =
        new RectF((centerX - radius), (centerY - radius), (centerX + radius), (centerY + radius));

    paint.setStyle(Style.FILL);
    paint.setColor(dotFillColor);
    paint.setShadowLayer(textShadowRadius, 0, 0, textShadowColor);
    canvas.drawOval(dotBounds, paint);
    paint.setShadowLayer(0, 0, 0, 0);

    contractBounds(dotBounds, (DOT_STROKE_WIDTH / 2));
    paint.setStrokeWidth(DOT_STROKE_WIDTH);
    paint.setStyle(Style.STROKE);
    paint.setColor(dotStrokeColor);
    paint.setPathEffect(dotPathEffect);
    canvas.drawOval(dotBounds, paint);
    paint.setPathEffect(null);
  }

  private void drawCancel(Canvas canvas) {
    final float centerX = center.x;
    final float centerY = center.y;
    final float radius = innerRadius;
    final float iconRadius = (radius / 4.0f);

    final RectF dotBounds =
        new RectF((centerX - radius), (centerY - radius), (centerX + radius), (centerY + radius));

    // Apply the appropriate color filters.
    final boolean selected = (focusedItem == null);

    paint.setStyle(Style.FILL);
    paint.setColor(selected ? selectionColor : centerFillColor);
    paint.setShadowLayer(shadowRadius, 0, 0, (selected ? selectionShadowColor : textShadowColor));
    canvas.drawOval(dotBounds, paint);
    paint.setShadowLayer(0, 0, 0, 0);

    paint.setStyle(Style.STROKE);
    paint.setColor(selected ? selectionTextFillColor : centerTextFillColor);
    paint.setStrokeCap(Cap.SQUARE);
    paint.setStrokeWidth(10.0f);
    canvas.drawLine(
        (centerX - iconRadius),
        (centerY - iconRadius),
        (centerX + iconRadius),
        (centerY + iconRadius),
        paint);
    canvas.drawLine(
        (centerX + iconRadius),
        (centerY - iconRadius),
        (centerX - iconRadius),
        (centerY + iconRadius),
        paint);
  }

  private void drawWedge(Canvas canvas, float center, int i, RadialMenu menu, float degrees) {
    final float offset = subMenu != null ? subMenuOffset : rootMenuOffset;

    final RadialMenuItem wedge = menu.getItem(i);
    final String title = wedge.getTitle().toString();
    final float rotation = ((degrees * i) + offset);
    final boolean selected = wedge.equals(focusedItem);

    // Apply the appropriate color filters.
    if (wedge.hasSubMenu()) {
      paint.setColorFilter(subMenuFilter);
    } else {
      paint.setColorFilter(null);
    }

    wedge.offset = rotation;

    tempMatrix.reset();
    tempMatrix.setRotate(rotation, center, center);
    tempMatrix.postTranslate((this.center.x - center), (this.center.y - center));
    canvas.setMatrix(tempMatrix);

    paint.setStyle(Style.FILL);
    paint.setColor(selected ? selectionColor : outerFillColor);
    paint.setShadowLayer(shadowRadius, 0, 0, (selected ? selectionShadowColor : textShadowColor));
    canvas.drawPath(cachedOuterPath, paint);
    paint.setShadowLayer(0, 0, 0, 0);

    paint.setStyle(Style.FILL);
    paint.setColor(selected ? selectionTextFillColor : textFillColor);
    paint.setTextAlign(Align.CENTER);
    paint.setTextSize(textSize);
    paint.setShadowLayer(textShadowRadius, 0, 0, textShadowColor);

    final String renderText = getEllipsizedText(paint, title, cachedOuterPathWidth);

    // Orient text differently depending on the angle.
    if ((rotation < 90) || (rotation > 270)) {
      canvas.drawTextOnPath(renderText, cachedOuterPathReverse, 0, (2 * textSize), paint);
    } else {
      canvas.drawTextOnPath(renderText, cachedOuterPath, 0, -textSize, paint);
    }

    paint.setShadowLayer(0, 0, 0, 0);
    paint.setColorFilter(null);
  }

  private void drawCorner(Canvas canvas, int width, int height, float center, int i) {
    final RadialMenuItem wedge = rootMenu.getCorner(i);
    if (wedge == null || !wedge.isVisible()) {
      return;
    }

    final float rotation = RadialMenu.getCornerRotation(i);
    final PointF cornerLocation = RadialMenu.getCornerLocation(i);
    if (cornerLocation == null) {
      return;
    }
    final float cornerX = (cornerLocation.x * width);
    final float cornerY = (cornerLocation.y * height);
    final String title = wedge.getTitle().toString();
    final boolean selected = wedge.equals(focusedItem);

    // Apply the appropriate color filters.
    if (wedge.hasSubMenu()) {
      paint.setColorFilter(subMenuFilter);
    } else {
      paint.setColorFilter(null);
    }

    wedge.offset = rotation;

    tempMatrix.reset();
    tempMatrix.setRotate(rotation, center, center);
    tempMatrix.postTranslate((cornerX - center), (cornerY - center));
    canvas.setMatrix(tempMatrix);

    paint.setStyle(Style.FILL);
    paint.setColor(selected ? selectionColor : cornerFillColor);
    paint.setShadowLayer(shadowRadius, 0, 0, (selected ? selectionShadowColor : textShadowColor));
    canvas.drawPath(cachedCornerPath, paint);
    paint.setShadowLayer(0, 0, 0, 0);

    paint.setStyle(Style.FILL);
    paint.setColor(selected ? selectionTextFillColor : cornerTextFillColor);
    paint.setTextAlign(Align.CENTER);
    paint.setTextSize(textSize);
    paint.setShadowLayer(textShadowRadius, 0, 0, textShadowColor);

    final String renderText = getEllipsizedText(paint, title, cachedCornerPathWidth);

    // Orient text differently depending on the angle.
    if (((rotation < 90) && (rotation > -90)) || (rotation > 270)) {
      canvas.drawTextOnPath(renderText, cachedCornerPathReverse, 0, (2 * textSize), paint);
    } else {
      canvas.drawTextOnPath(renderText, cachedCornerPath, 0, -textSize, paint);
    }

    paint.setShadowLayer(0, 0, 0, 0);
    paint.setColorFilter(null);
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
    final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
    final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
    final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
    final int measuredWidth = (widthMode == MeasureSpec.UNSPECIFIED) ? 320 : widthSize;
    final int measuredHeight = (heightMode == MeasureSpec.UNSPECIFIED) ? 480 : heightSize;

    setMeasuredDimension(measuredWidth, measuredHeight);
  }

  @Override
  public boolean onHoverEvent(@NonNull MotionEvent event) {
    if (useNodeProvider) {
      return super.onHoverEvent(event);
    } else {
      return onTouchEvent(event);
    }
  }

  @Override
  public boolean onTouchEvent(@NonNull MotionEvent event) {
    switch (event.getAction()) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_HOVER_ENTER:
        onEnter(event.getX(), event.getY());
        // Fall-through to movement events.
      case MotionEvent.ACTION_MOVE:
      case MotionEvent.ACTION_HOVER_MOVE:
        onMove(event.getX(), event.getY());
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_HOVER_EXIT:
        onUp(event.getX(), event.getY());
        break;
      default:
        // Don't handle other types of events.
        return false;
    }

    handler.onTouch(this, event);

    return true;
  }

  /**
   * Computes and returns which menu item or corner the user is touching.
   *
   * @param x The touch X coordinate.
   * @param y The touch Y coordinate.
   * @return A pair containing the menu item that the user is touching and whether the user is
   *     touching the menu item directly.
   */
  private TouchedMenuItem getTouchedMenuItem(float x, float y) {
    final TouchedMenuItem result = new TouchedMenuItem();
    final float dX = (x - center.x);
    final float dY = (y - center.y);
    final float touchDistSq = (dX * dX) + (dY * dY);

    if (getClosestTouchedCorner(x, y, result)) {
      // First preference goes to corners.
    } else if (displayWedges && getClosestTouchedWedge(dX, dY, touchDistSq, result)) {
      // Second preference goes to wedges, if displayed.
    }

    return result;
  }

  private boolean getClosestTouchedCorner(float x, float y, TouchedMenuItem result) {
    final int width = getWidth();
    final int height = getHeight();

    // How close is the user to a corner?
    for (int groupId = 0; groupId < 4; groupId++) {
      final RadialMenuItem corner = rootMenu.getCorner(groupId);
      if (corner == null || !corner.isVisible()) {
        continue;
      }

      final PointF cornerLocation = RadialMenu.getCornerLocation(groupId);
      if (cornerLocation == null) {
        continue;
      }
      final float cornerDX = (x - (cornerLocation.x * width));
      final float cornerDY = (y - (cornerLocation.y * height));
      final float cornerTouchDistSq = (cornerDX * cornerDX) + (cornerDY * cornerDY);

      // If the user is touching within a corner's outer radius, consider
      // it a direct touch.
      if (cornerTouchDistSq < extremeRadiusSq) {
        result.item = corner;
        result.isDirectTouch = true;
        return true;
      }
    }

    return false;
  }

  private boolean getClosestTouchedWedge(
      float dX, float dY, float touchDistSq, TouchedMenuItem result) {
    if (touchDistSq <= innerRadiusSq) {
      // The user is touching the center dot.
      return false;
    }

    final RadialMenu menu = (subMenu != null) ? subMenu : rootMenu;
    int menuSize = menu.size();
    if (menuSize == 0) {
      return false;
    }
    final float offset = (subMenu != null) ? subMenuOffset : rootMenuOffset;

    // Which wedge is the user touching?
    final double angle = Math.atan2(dX, dY);
    final double wedgeArc = (360.0 / menuSize);
    final double offsetArc = (wedgeArc / 2.0) - offset;

    double touchArc = (((180.0 - Math.toDegrees(angle)) + offsetArc) % 360);

    if (touchArc < 0) {
      touchArc += 360;
    }

    final int wedgeNum = (int) (touchArc / wedgeArc);
    if ((wedgeNum < 0) || (wedgeNum >= menuSize)) {
      LogUtils.e(TAG, "Invalid wedge index: %d", wedgeNum);
      return false;
    }

    result.item = menu.getItem(wedgeNum);
    result.isDirectTouch = (touchDistSq < extremeRadiusSq);
    return true;
  }

  /**
   * Called when the user's finger first touches the radial menu.
   *
   * @param x The touch X coordinate.
   * @param y The touch Y coordinate.
   */
  private void onEnter(float x, float y) {
    entryPoint.set(x, y);
    maybeSingleTap = true;
  }

  /**
   * Called when the user moves their finger. Focuses a menu item.
   *
   * @param x The touch X coordinate.
   * @param y The touch Y coordinate.
   */
  private void onMove(float x, float y) {
    if (maybeSingleTap && (distSq(entryPoint, x, y) >= singleTapRadiusSq)) {
      maybeSingleTap = false;
    }

    final TouchedMenuItem touchedItem = getTouchedMenuItem(x, y);

    // Only focus the item if this is definitely not a single tap or the
    // user is directly touching a menu item.
    if (!maybeSingleTap || (touchedItem.item == null) || touchedItem.isDirectTouch) {
      onItemFocused(touchedItem.item);
    }

    // Only display the wedges if we didn't just place focus on an item.
    if ((touchedItem.item == null) && !displayWedges) {
      displayWedges = true;
      displayAt(x, y);
    }
  }

  /**
   * Called when the user lifts their finger. Selects a menu item.
   *
   * @param x The touch X coordinate.
   * @param y The touch Y coordinate.
   */
  private void onUp(float x, float y) {
    final TouchedMenuItem touchedItem = getTouchedMenuItem(x, y);

    // Only select the item if this is definitely not a single tap or the
    // user is directly touching a menu item.
    if (!maybeSingleTap || (touchedItem.item == null) || touchedItem.isDirectTouch) {
      onItemSelected(touchedItem.item);
    }
  }

  /**
   * Sets a sub-menu as the currently displayed menu.
   *
   * @param subMenu The sub-menu to display.
   * @param offset The offset of the sub-menu's menu item.
   */
  private void setSubMenu(RadialSubMenu subMenu, float offset) {
    this.subMenu = subMenu;
    subMenuOffset = offset;

    invalidateCachedWedgeShapes();
    invalidate();
    subMenu.onShow();

    if ((subMenu != null) && (subMenu.size() > 0) && (subMenuMode == SubMenuMode.LONG_PRESS)) {
      onItemFocused(subMenu.getItem(0));
    }
  }

  /**
   * Called when an item is focused. If the newly focused item is the same as the previously focused
   * item, this is a no-op. Otherwise, the menu item's select action is triggered and an
   * accessibility select event is fired.
   *
   * @param item The item that the user focused.
   */
  private void onItemFocused(RadialMenuItem item) {
    if (focusedItem == item) {
      return;
    }

    final RadialMenu menu = (subMenu != null) ? subMenu : rootMenu;

    focusedItem = item;

    invalidate();

    if (item == null) {
      menu.clearSelection(0);
    } else if (item.isCorner()) {
      // Currently only the root menu is allowed to have corners.
      rootMenu.selectMenuItem(item, 0);
    } else {
      menu.selectMenuItem(item, 0);
    }

    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
  }

  /**
   * Called when the user stops over an item. If the user stops over the "no selection" area and the
   * current menu is a sub-menu, the sub-menu closes. If the user stops over a sub-menu item, that
   * sub-menu opens.
   *
   * @param item The menu item that the user stopped over.
   */
  private void onItemLongPressed(RadialMenuItem item) {
    if (subMenuMode == SubMenuMode.LONG_PRESS) {
      if (item != null) {
        if (item.hasSubMenu()) {
          setSubMenu(item.getSubMenu(), item.offset);
        }
      } else if (subMenu != null) {
        // Switch back to the root menu.
        setSubMenu(null, 0);
      }
    }
  }

  /**
   * Called when a menu item is selected. The menu item's perform action is triggered and a click
   * accessibility event is fired.
   *
   * @param item The item that the used selected.
   */
  private void onItemSelected(RadialMenuItem item) {
    final RadialMenu menu = (subMenu != null) ? subMenu : rootMenu;

    focusedItem = item;

    invalidate();

    if (item == null) {
      menu.performMenuItem(null, 0);
    } else if (item.hasSubMenu()) {
      setSubMenu(item.getSubMenu(), item.offset);
      // TODO: Refactor such that the identifier action for an item with a
      // sub-menu is to simply open the sub-menu. Currently only the view
      // (this class) can manipulate sub-menus.
      if (item.isCorner()) {
        // Currently only the root menu is allowed to have corners.
        rootMenu.performMenuItem(item, RadialMenu.FLAG_PERFORM_NO_CLOSE);
      } else {
        menu.performMenuItem(item, RadialMenu.FLAG_PERFORM_NO_CLOSE);
      }
    } else {
      if (item.isCorner()) {
        // Currently only the root menu is allowed to have corners.
        rootMenu.performMenuItem(item, 0);
      } else {
        menu.performMenuItem(item, 0);
      }
    }

    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
  }

  private static String getEllipsizedText(Paint paint, String title, float maxWidth) {
    final float textWidth = paint.measureText(title);
    if (textWidth <= maxWidth) {
      return title;
    }

    // Find the maximum length with an ellipsis.
    final float ellipsisWidth = paint.measureText(ELLIPSIS);
    final int length = paint.breakText(title, true, (maxWidth - ellipsisWidth), null);

    // Try to land on a word break.
    // TODO: Use breaking iterator for better i18n support.
    final int space = title.lastIndexOf(' ', length);
    if (space > 0) {
      return title.substring(0, space) + ELLIPSIS;
    }

    // Otherwise, cut off characters.
    return title.substring(0, length) + ELLIPSIS;
  }

  @SuppressWarnings("SuspiciousNameCombination")
  private static void createBounds(RectF target, int diameter, int radius) {
    final float center = (diameter / 2.0f);
    final float left = (center - radius);
    final float right = (center + radius);

    target.set(left, left, right, right);
  }

  private static void contractBounds(RectF rect, float amount) {
    rect.left += amount;
    rect.top += amount;
    rect.right -= amount;
    rect.bottom -= amount;
  }

  /**
   * Computes the squared distance between a point and an (x,y) coordinate.
   *
   * @param p The point.
   * @param x The x-coordinate.
   * @param y The y-coordinate.
   * @return The squared distance between the point and (x,y) coordinate.
   */
  private static float distSq(PointF p, float x, float y) {
    final float dX = (x - p.x);
    final float dY = (y - p.y);
    return ((dX * dX) + (dY * dY));
  }

  /**
   * Computes the length of an arc defined by an angle and radius.
   *
   * @param angle The angle of the arc, in degrees.
   * @param radius The radius of the arc.
   * @return The length of the arc.
   */
  private static float arcLength(float angle, float radius) {
    return ((2.0f * (float) Math.PI * radius) * (angle / 360.0f));
  }

  private static class TouchedMenuItem {
    @Nullable private RadialMenuItem item = null;
    private boolean isDirectTouch = false;
  }

  private class RadialMenuHelper extends ExploreByTouchObjectHelper<RadialMenuItem> {
    private final Rect tempRect = new Rect();

    public RadialMenuHelper(View parentView) {
      super(parentView);
    }

    @Override
    protected void populateNodeForItem(RadialMenuItem item, AccessibilityNodeInfoCompat node) {
      node.setContentDescription(item.getTitle());
      node.setVisibleToUser(item.isVisible());
      node.setCheckable(item.isCheckable());
      node.setChecked(item.isChecked());
      node.setEnabled(item.isEnabled());
      node.setClickable(true);

      getLocalVisibleRect(tempRect);
      node.setBoundsInParent(tempRect);

      getGlobalVisibleRect(tempRect);
      node.setBoundsInScreen(tempRect);
    }

    @Override
    protected void populateEventForItem(RadialMenuItem item, AccessibilityEvent event) {
      event.setContentDescription(item.getTitle());
      event.setChecked(item.isChecked());
      event.setEnabled(item.isEnabled());
    }

    @Override
    protected boolean performActionForItem(RadialMenuItem item, int action) {
      switch (action) {
        case AccessibilityNodeInfoCompat.ACTION_CLICK:
          item.onClickPerformed();
          return true;
      }

      return false;
    }

    @Override
    protected RadialMenuItem getItemForVirtualViewId(int id) {
      return rootMenu.getItem(id);
    }

    @Override
    protected int getVirtualViewIdForItem(RadialMenuItem item) {
      return rootMenu.indexOf(item);
    }

    @Override
    protected RadialMenuItem getItemAt(float x, float y) {
      return getTouchedMenuItem(x, y).item;
    }

    @Override
    protected void getVisibleItems(List<RadialMenuItem> items) {
      for (int i = 0; i < rootMenu.size(); i++) {
        final RadialMenuItem item = rootMenu.getItem(i);
        items.add(item);
      }
    }
  }
}

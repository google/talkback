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

package com.android.talkback.contextmenu;

import android.annotation.TargetApi;
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
import android.support.annotation.NonNull;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

import com.android.talkback.R;
import com.android.utils.ExploreByTouchObjectHelper;
import com.android.utils.LogUtils;

public class RadialMenuView extends SurfaceView {
    public enum SubMenuMode {
        /** Activate sub-menus by touching them for an extended time. */
        LONG_PRESS,
        /** Activate sub-menus by touching them and lifting a finger. */
        LIFT_TO_ACTIVATE
    }

    /** String used to ellipsize text. */
    private static final String ELLIPSIS = "…";

    /** The point at which the user started touching the menu. */
    private final PointF mEntryPoint = new PointF();

    /** Temporary matrix used for calculations. */
    private final Matrix mTempMatrix = new Matrix();

    // Cached bounds.
    private final RectF mCachedOuterBound = new RectF();
    private final RectF mCachedCornerBound = new RectF();
    private final RectF mCachedExtremeBound = new RectF();

    // Cached paths representing a single item.
    private final Path mCachedOuterPath = new Path();
    private final Path mCachedOuterPathReverse = new Path();
    private final Path mCachedCornerPath = new Path();
    private final Path mCachedCornerPathReverse = new Path();

    // Cached widths for rendering text on paths.
    private float mCachedOuterPathWidth;
    private float mCachedCornerPathWidth;

    /** The root menu represented by this view. */
    private final RadialMenu mRootMenu;

    /** The paint used to draw this view. */
    private final Paint mPaint;

    /** The long-press handler for this view. */
    private final LongPressHandler mHandler;

    /** The background drawn below the radial menu. */
    private GradientDrawable mGradientBackground;

    /** The maximum radius allowable for a single-tap gesture. */
    private final int mSingleTapRadiusSq;

    // Dimensions loaded from context.
    private final int mInnerRadius;
    private final int mOuterRadius;
    private final int mCornerRadius;
    private final int mExtremeRadius;
    private final int mSpacing;
    private final int mTextSize;
    private final int mTextShadowRadius;
    private final int mShadowRadius;

    // Generated from dimensions.
    private final int mInnerRadiusSq;
    private final int mExtremeRadiusSq;

    // Colors loaded from context.
    private final int mOuterFillColor;
    private final int mTextFillColor;
    private final int mCornerFillColor;
    private final int mCornerTextFillColor;
    private final int mDotFillColor;
    private final int mDotStrokeColor;
    private final int mSelectionColor;
    private final int mSelectionTextFillColor;
    private final int mSelectionShadowColor;
    private final int mCenterFillColor;
    private final int mCenterTextFillColor;
    private final int mTextShadowColor;

    private final DashPathEffect mDotPathEffect = new DashPathEffect(new float[] {20, 20}, 0);

    private static final float DOT_STROKE_WIDTH = 5;

    // Color filters for modal content.
    private final ColorFilter mSubMenuFilter;

    /**
     * Whether to use a node provider. If set to {@code false}, converts hover
     * events to touch events and does not send any accessibility events.
     */
    private final boolean mUseNodeProvider;

    /** The current interaction mode for activating elements. */
    private SubMenuMode mSubMenuMode = SubMenuMode.LIFT_TO_ACTIVATE;

    /** The surface holder onto which the view is drawn. */
    private SurfaceHolder mHolder;

    /** The currently focused item. */
    private RadialMenuItem mFocusedItem;

    /** The currently displayed sub-menu, if any. */
    private RadialSubMenu mSubMenu;

    /**
     * The offset of the current sub-menu, in degrees. Used to align the
     * sub-menu with its parent menu item.
     */
    private float mSubMenuOffset;

    /**
     * The offset of the root menu, in degrees. Used to align the first menu
     * item at the top or right of the radial menu.
     */
    private float mRootMenuOffset = 0;

    /** The center point of the radial menu. */
    private PointF mCenter = new PointF();

    /** Whether to display the "carrot" dot. */
    private boolean mDisplayWedges;

    /** Whether the current touch might be a single tap gesture. */
    private boolean mMaybeSingleTap;

    public RadialMenuView(Context context, RadialMenu menu, boolean useNodeProvider) {
        super(context);

        mRootMenu = menu;
        mRootMenu.setLayoutListener(new RadialMenu.MenuLayoutListener() {
            @Override
            public void onLayoutChanged() {
                invalidate();
            }
        });

        mPaint = new Paint();
        mPaint.setAntiAlias(true);

        mHandler = new LongPressHandler(context);
        mHandler.setListener(new LongPressHandler.LongPressListener() {
            @Override
            public void onLongPress() {
                onItemLongPressed(mFocusedItem);
            }
        });

        final SurfaceHolder holder = getHolder();
        holder.setFormat(PixelFormat.TRANSLUCENT);
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                mHolder = holder;
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                mHolder = null;
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                invalidate();
            }
        });

        final Resources res = context.getResources();
        final ViewConfiguration config = ViewConfiguration.get(context);

        mSingleTapRadiusSq = config.getScaledTouchSlop();

        // Dimensions.
        mInnerRadius = res.getDimensionPixelSize(R.dimen.inner_radius);
        mOuterRadius = res.getDimensionPixelSize(R.dimen.outer_radius);
        mCornerRadius = res.getDimensionPixelSize(R.dimen.corner_radius);
        mExtremeRadius = res.getDimensionPixelSize(R.dimen.extreme_radius);
        mSpacing = res.getDimensionPixelOffset(R.dimen.spacing);
        mTextSize = res.getDimensionPixelSize(R.dimen.text_size);
        mTextShadowRadius = res.getDimensionPixelSize(R.dimen.text_shadow_radius);
        mShadowRadius = res.getDimensionPixelSize(R.dimen.shadow_radius);

        // Colors.
        mOuterFillColor = res.getColor(R.color.outer_fill);
        mTextFillColor = res.getColor(R.color.text_fill);
        mCornerFillColor = res.getColor(R.color.corner_fill);
        mCornerTextFillColor = res.getColor(R.color.corner_text_fill);
        mDotFillColor = res.getColor(R.color.dot_fill);
        mDotStrokeColor = res.getColor(R.color.dot_stroke);
        mSelectionColor = res.getColor(R.color.selection_fill);
        mSelectionTextFillColor = res.getColor(R.color.selection_text_fill);
        mSelectionShadowColor = res.getColor(R.color.selection_shadow);
        mCenterFillColor = res.getColor(R.color.center_fill);
        mCenterTextFillColor = res.getColor(R.color.center_text_fill);
        mTextShadowColor = res.getColor(R.color.text_shadow);

        // Gradient colors.
        final int gradientInnerColor = res.getColor(R.color.gradient_inner);
        final int gradientOuterColor = res.getColor(R.color.gradient_outer);
        final int[] colors = new int[] {gradientInnerColor, gradientOuterColor};
        mGradientBackground = new GradientDrawable(Orientation.TOP_BOTTOM, colors);
        mGradientBackground.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        mGradientBackground.setGradientRadius(mExtremeRadius * 2.0f);

        final int subMenuOverlayColor = res.getColor(R.color.submenu_overlay);

        // Lighting filters generated from colors.
        mSubMenuFilter = new PorterDuffColorFilter(subMenuOverlayColor, PorterDuff.Mode.SCREEN);

        mInnerRadiusSq = (mInnerRadius * mInnerRadius);
        mExtremeRadiusSq = (mExtremeRadius * mExtremeRadius);

        mUseNodeProvider = useNodeProvider;

        if (mUseNodeProvider) {
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
        mSubMenuMode = subMenuMode;
    }

    /**
     * Displays a dot at the center of the screen and draws the corner menu
     * items.
     */
    public void displayDot() {
        mDisplayWedges = false;
        mSubMenu = null;
        mFocusedItem = null;

        invalidate();
    }

    /**
     * Displays the menu centered at the specified coordinates.
     *
     * @param centerX The center X coordinate.
     * @param centerY The center Y coordinate.
     */
    public void displayAt(float centerX, float centerY) {
        mCenter.x = centerX;
        mCenter.y = centerY;

        mDisplayWedges = true;
        mSubMenu = null;
        mFocusedItem = null;

        invalidate();
    }

    /**
     * Re-draw cached wedge bitmaps.
     */
    private void invalidateCachedWedgeShapes() {
        final RadialMenu menu = mSubMenu != null ? mSubMenu : mRootMenu;
        final int menuSize = menu.size();
        if (menuSize <= 0) {
            return;
        }

        final float wedgeArc = (360.0f / menuSize);
        final float offsetArc = ((wedgeArc / 2.0f) + 90.0f);
        final float spacingArc = (float) Math.toDegrees(
                Math.tan(mSpacing / (double) mOuterRadius));
        final float left = (wedgeArc - spacingArc - offsetArc);
        final float center = ((wedgeArc / 2.0f) - offsetArc);
        final float right = (spacingArc - offsetArc);

        // Outer wedge.
        mCachedOuterPath.rewind();
        mCachedOuterPath.arcTo(mCachedOuterBound, center, (left - center));
        mCachedOuterPath.arcTo(mCachedExtremeBound, left, (right - left));
        mCachedOuterPath.arcTo(mCachedOuterBound, right, (center - right));
        mCachedOuterPath.close();

        mCachedOuterPathWidth = arcLength((left - right), mExtremeRadius);

        // Outer wedge in reverse, for rendering text.
        mCachedOuterPathReverse.rewind();
        mCachedOuterPathReverse.arcTo(mCachedOuterBound, center, (right - center));
        mCachedOuterPathReverse.arcTo(mCachedExtremeBound, right, (left - right));
        mCachedOuterPathReverse.arcTo(mCachedOuterBound, left, (center - left));
        mCachedOuterPathReverse.close();
    }

    /**
     * Initialized cached bounds and corner shapes.
     * <p>
     * <b>Note:</b> This method should be called whenever a radius value
     * changes. It must be called before any calls are made to
     * {@link #invalidateCachedWedgeShapes()}.
     */
    private void initializeCachedShapes() {
        final int diameter = (mExtremeRadius * 2);

        createBounds(mCachedOuterBound, diameter, mOuterRadius);
        createBounds(mCachedCornerBound, diameter, mCornerRadius);
        createBounds(mCachedExtremeBound, diameter, mExtremeRadius);

        final float cornerWedgeArc = 90.0f;
        final float cornerOffsetArc = ((cornerWedgeArc / 2.0f) + 90.0f);
        final float cornerLeft = (cornerWedgeArc - cornerOffsetArc);
        final float cornerCenter = ((cornerWedgeArc / 2.0f) - cornerOffsetArc);
        final float cornerRight = -cornerOffsetArc;

        // Corner wedge.
        mCachedCornerPath.rewind();
        mCachedCornerPath.arcTo(mCachedCornerBound, cornerCenter, (cornerLeft - cornerCenter));
        mCachedCornerPath.arcTo(mCachedExtremeBound, cornerLeft, (cornerRight - cornerLeft));
        mCachedCornerPath.arcTo(mCachedCornerBound, cornerRight, (cornerCenter - cornerRight));
        mCachedCornerPath.close();

        mCachedCornerPathWidth = arcLength((cornerLeft - cornerRight), mExtremeRadius);

        // Corner wedge in reverse, for rendering text.
        mCachedCornerPathReverse.rewind();
        mCachedCornerPathReverse.arcTo(
                mCachedCornerBound, cornerCenter, (cornerRight - cornerCenter));
        mCachedCornerPathReverse.arcTo(
                mCachedExtremeBound, cornerRight, (cornerLeft - cornerRight));
        mCachedCornerPathReverse.arcTo(mCachedCornerBound, cornerLeft, (cornerCenter - cornerLeft));
        mCachedCornerPathReverse.close();
    }

    @Override
    public void invalidate() {
        super.invalidate();

        final SurfaceHolder holder = mHolder;
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

        if (!mDisplayWedges) {
            mCenter.x = (width / 2.0f);
            mCenter.y = (height / 2.0f);
        }

        // Draw the pretty gradient background.
        mGradientBackground.setGradientCenter((mCenter.x / width), (mCenter.y / height));
        mGradientBackground.setBounds(0, 0, width, height);
        mGradientBackground.draw(canvas);

        final RadialMenu menu = (mSubMenu != null) ? mSubMenu : mRootMenu;
        final float center = mExtremeRadius;

        if (mDisplayWedges) {
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
        final float radius = mInnerRadius;
        final RectF dotBounds = new RectF(
                (centerX - radius), (centerY - radius), (centerX + radius), (centerY + radius));

        mPaint.setStyle(Style.FILL);
        mPaint.setColor(mDotFillColor);
        mPaint.setShadowLayer(mTextShadowRadius, 0, 0, mTextShadowColor);
        canvas.drawOval(dotBounds, mPaint);
        mPaint.setShadowLayer(0, 0, 0, 0);

        contractBounds(dotBounds, (DOT_STROKE_WIDTH / 2));
        mPaint.setStrokeWidth(DOT_STROKE_WIDTH);
        mPaint.setStyle(Style.STROKE);
        mPaint.setColor(mDotStrokeColor);
        mPaint.setPathEffect(mDotPathEffect);
        canvas.drawOval(dotBounds, mPaint);
        mPaint.setPathEffect(null);
    }

    private void drawCancel(Canvas canvas) {
        final float centerX = mCenter.x;
        final float centerY = mCenter.y;
        final float radius = mInnerRadius;
        final float iconRadius = (radius / 4.0f);

        final RectF dotBounds = new RectF(
                (centerX - radius), (centerY - radius), (centerX + radius), (centerY + radius));

        // Apply the appropriate color filters.
        final boolean selected = (mFocusedItem == null);

        mPaint.setStyle(Style.FILL);
        mPaint.setColor(selected ? mSelectionColor : mCenterFillColor);
        mPaint.setShadowLayer(mShadowRadius, 0, 0,
                (selected ? mSelectionShadowColor : mTextShadowColor));
        canvas.drawOval(dotBounds, mPaint);
        mPaint.setShadowLayer(0, 0, 0, 0);

        mPaint.setStyle(Style.STROKE);
        mPaint.setColor(selected ? mSelectionTextFillColor : mCenterTextFillColor);
        mPaint.setStrokeCap(Cap.SQUARE);
        mPaint.setStrokeWidth(10.0f);
        canvas.drawLine((centerX - iconRadius), (centerY - iconRadius), (centerX + iconRadius),
                (centerY + iconRadius), mPaint);
        canvas.drawLine((centerX + iconRadius), (centerY - iconRadius), (centerX - iconRadius),
                (centerY + iconRadius), mPaint);
    }

    private void drawWedge(Canvas canvas, float center, int i,
            RadialMenu menu, float degrees) {
        final float offset = mSubMenu != null ? mSubMenuOffset : mRootMenuOffset;

        final RadialMenuItem wedge = menu.getItem(i);
        final String title = wedge.getTitle().toString();
        final float rotation = ((degrees * i) + offset);
        final boolean selected = wedge.equals(mFocusedItem);

        // Apply the appropriate color filters.
        if (wedge.hasSubMenu()) {
            mPaint.setColorFilter(mSubMenuFilter);
        } else {
            mPaint.setColorFilter(null);
        }

        wedge.offset = rotation;

        mTempMatrix.reset();
        mTempMatrix.setRotate(rotation, center, center);
        mTempMatrix.postTranslate((mCenter.x - center), (mCenter.y - center));
        canvas.setMatrix(mTempMatrix);

        mPaint.setStyle(Style.FILL);
        mPaint.setColor(selected ? mSelectionColor : mOuterFillColor);
        mPaint.setShadowLayer(mShadowRadius, 0, 0,
                (selected ? mSelectionShadowColor : mTextShadowColor));
        canvas.drawPath(mCachedOuterPath, mPaint);
        mPaint.setShadowLayer(0, 0, 0, 0);

        mPaint.setStyle(Style.FILL);
        mPaint.setColor(selected ? mSelectionTextFillColor : mTextFillColor);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setTextSize(mTextSize);
        mPaint.setShadowLayer(mTextShadowRadius, 0, 0, mTextShadowColor);

        final String renderText = getEllipsizedText(mPaint, title, mCachedOuterPathWidth);

        // Orient text differently depending on the angle.
        if ((rotation < 90) || (rotation > 270)) {
            canvas.drawTextOnPath(renderText, mCachedOuterPathReverse, 0, (2 * mTextSize), mPaint);
        } else {
            canvas.drawTextOnPath(renderText, mCachedOuterPath, 0, -mTextSize, mPaint);
        }

        mPaint.setShadowLayer(0, 0, 0, 0);
        mPaint.setColorFilter(null);
    }

    private void drawCorner(Canvas canvas, int width, int height, float center, int i) {
        final RadialMenuItem wedge = mRootMenu.getCorner(i);
        if (wedge == null || !wedge.isVisible()) {
            return;
        }

        final float rotation = RadialMenu.getCornerRotation(i);
        final PointF cornerLocation = RadialMenu.getCornerLocation(i);
        if (cornerLocation == null) return;
        final float cornerX = (cornerLocation.x * width);
        final float cornerY = (cornerLocation.y * height);
        final String title = wedge.getTitle().toString();
        final boolean selected = wedge.equals(mFocusedItem);

        // Apply the appropriate color filters.
        if (wedge.hasSubMenu()) {
            mPaint.setColorFilter(mSubMenuFilter);
        } else {
            mPaint.setColorFilter(null);
        }

        wedge.offset = rotation;

        mTempMatrix.reset();
        mTempMatrix.setRotate(rotation, center, center);
        mTempMatrix.postTranslate((cornerX - center), (cornerY - center));
        canvas.setMatrix(mTempMatrix);

        mPaint.setStyle(Style.FILL);
        mPaint.setColor(selected ? mSelectionColor : mCornerFillColor);
        mPaint.setShadowLayer(mShadowRadius, 0, 0,
                (selected ? mSelectionShadowColor : mTextShadowColor));
        canvas.drawPath(mCachedCornerPath, mPaint);
        mPaint.setShadowLayer(0, 0, 0, 0);

        mPaint.setStyle(Style.FILL);
        mPaint.setColor(selected ? mSelectionTextFillColor : mCornerTextFillColor);
        mPaint.setTextAlign(Align.CENTER);
        mPaint.setTextSize(mTextSize);
        mPaint.setShadowLayer(mTextShadowRadius, 0, 0, mTextShadowColor);

        final String renderText = getEllipsizedText(mPaint, title, mCachedCornerPathWidth);

        // Orient text differently depending on the angle.
        if (((rotation < 90) && (rotation > -90)) || (rotation > 270)) {
            canvas.drawTextOnPath(renderText, mCachedCornerPathReverse, 0, (2 * mTextSize), mPaint);
        } else {
            canvas.drawTextOnPath(renderText, mCachedCornerPath, 0, -mTextSize, mPaint);
        }

        mPaint.setShadowLayer(0, 0, 0, 0);
        mPaint.setColorFilter(null);
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

    @TargetApi(14)
    @Override
    public boolean onHoverEvent(@NonNull MotionEvent event) {
        if (mUseNodeProvider) {
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
                // Fall-through to movement events.
                onEnter(event.getX(), event.getY());
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

        mHandler.onTouch(this, event);

        return true;
    }

    /**
     * Computes and returns which menu item or corner the user is touching.
     *
     * @param x The touch X coordinate.
     * @param y The touch Y coordinate.
     * @return A pair containing the menu item that the user is touching and
     *         whether the user is touching the menu item directly.
     */
    private TouchedMenuItem getTouchedMenuItem(float x, float y) {
        final TouchedMenuItem result = new TouchedMenuItem();
        final float dX = (x - mCenter.x);
        final float dY = (y - mCenter.y);
        final float touchDistSq = (dX * dX) + (dY * dY);

        if (getClosestTouchedCorner(x, y, result)) {
            // First preference goes to corners.
        } else if (mDisplayWedges && getClosestTouchedWedge(dX, dY, touchDistSq, result)) {
            // Second preference goes to wedges, if displayed.
        }

        return result;
    }

    private boolean getClosestTouchedCorner(float x, float y, TouchedMenuItem result) {
        final int width = getWidth();
        final int height = getHeight();

        // How close is the user to a corner?
        for (int groupId = 0; groupId < 4; groupId++) {
            final RadialMenuItem corner = mRootMenu.getCorner(groupId);
            if (corner == null) {
                continue;
            }

            final PointF cornerLocation = RadialMenu.getCornerLocation(groupId);
            if (cornerLocation == null) continue;
            final float cornerDX = (x - (cornerLocation.x * width));
            final float cornerDY = (y - (cornerLocation.y * height));
            final float cornerTouchDistSq = (cornerDX * cornerDX) + (cornerDY * cornerDY);

            // If the user is touching within a corner's outer radius, consider
            // it a direct touch.
            if (cornerTouchDistSq < mExtremeRadiusSq) {
                result.item = corner;
                result.isDirectTouch = true;
                return true;
            }
        }

        return false;
    }

    private boolean getClosestTouchedWedge(
            float dX, float dY, float touchDistSq, TouchedMenuItem result) {
        if (touchDistSq <= mInnerRadiusSq) {
            // The user is touching the center dot.
            return false;
        }

        final RadialMenu menu = (mSubMenu != null) ? mSubMenu : mRootMenu;
        int menuSize = menu.size();
        if (menuSize == 0) return false;
        final float offset = (mSubMenu != null) ? mSubMenuOffset : mRootMenuOffset;

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
            LogUtils.log(this, Log.ERROR, "Invalid wedge index: %d", wedgeNum);
            return false;
        }

        result.item = menu.getItem(wedgeNum);
        result.isDirectTouch = (touchDistSq < mExtremeRadiusSq);
        return true;
    }

    /**
     * Called when the user's finger first touches the radial menu.
     *
     * @param x The touch X coordinate.
     * @param y The touch Y coordinate.
     */
    private void onEnter(float x, float y) {
        mEntryPoint.set(x, y);
        mMaybeSingleTap = true;
    }

    /**
     * Called when the user moves their finger. Focuses a menu item.
     *
     * @param x The touch X coordinate.
     * @param y The touch Y coordinate.
     */
    private void onMove(float x, float y) {
        if (mMaybeSingleTap && (distSq(mEntryPoint, x, y) >= mSingleTapRadiusSq)) {
            mMaybeSingleTap = false;
        }

        final TouchedMenuItem touchedItem = getTouchedMenuItem(x, y);

        // Only focus the item if this is definitely not a single tap or the
        // user is directly touching a menu item.
        if (!mMaybeSingleTap || (touchedItem.item == null) || touchedItem.isDirectTouch) {
            onItemFocused(touchedItem.item);
        }

        // Only display the wedges if we didn't just place focus on an item.
        if ((touchedItem.item == null) && !mDisplayWedges) {
            mDisplayWedges = true;
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
        if (!mMaybeSingleTap || (touchedItem.item == null) || touchedItem.isDirectTouch) {
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
        mSubMenu = subMenu;
        mSubMenuOffset = offset;

        invalidateCachedWedgeShapes();
        invalidate();
        subMenu.onShow();

        if ((subMenu != null) && (subMenu.size() > 0) && (mSubMenuMode == SubMenuMode.LONG_PRESS)) {
            onItemFocused(subMenu.getItem(0));
        }
    }

    /**
     * Called when an item is focused. If the newly focused item is the same as
     * the previously focused item, this is a no-op. Otherwise, the menu item's
     * select action is triggered and an accessibility select event is fired.
     *
     * @param item The item that the user focused.
     */
    private void onItemFocused(RadialMenuItem item) {
        if (mFocusedItem == item) {
            return;
        }

        final RadialMenu menu = (mSubMenu != null) ? mSubMenu : mRootMenu;

        mFocusedItem = item;

        invalidate();

        if (item == null) {
            menu.clearSelection(0);
        } else if (item.isCorner()) {
            // Currently only the root menu is allowed to have corners.
            mRootMenu.selectMenuItem(item, 0);
        } else {
            menu.selectMenuItem(item, 0);
        }

        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
    }

    /**
     * Called when the user stops over an item. If the user stops over the
     * "no selection" area and the current menu is a sub-menu, the sub-menu
     * closes. If the user stops over a sub-menu item, that sub-menu opens.
     *
     * @param item The menu item that the user stopped over.
     */
    private void onItemLongPressed(RadialMenuItem item) {
        if (mSubMenuMode == SubMenuMode.LONG_PRESS) {
            if (item != null) {
                if (item.hasSubMenu()) {
                    setSubMenu(item.getSubMenu(), item.offset);
                }
            } else if (mSubMenu != null) {
                // Switch back to the root menu.
                setSubMenu(null, 0);
            }
        }
    }

    /**
     * Called when a menu item is selected. The menu item's perform action is
     * triggered and a click accessibility event is fired.
     *
     * @param item The item that the used selected.
     */
    private void onItemSelected(RadialMenuItem item) {
        final RadialMenu menu = (mSubMenu != null) ? mSubMenu : mRootMenu;

        mFocusedItem = item;

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
                mRootMenu.performMenuItem(item, RadialMenu.FLAG_PERFORM_NO_CLOSE);
            } else {
                menu.performMenuItem(item, RadialMenu.FLAG_PERFORM_NO_CLOSE);
            }
        } else {
            if (item.isCorner()) {
                // Currently only the root menu is allowed to have corners.
                mRootMenu.performMenuItem(item, 0);
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
        private RadialMenuItem item = null;
        private boolean isDirectTouch = false;
    }

    private class RadialMenuHelper extends ExploreByTouchObjectHelper<RadialMenuItem> {
        private final Rect mTempRect = new Rect();

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

            getLocalVisibleRect(mTempRect);
            node.setBoundsInParent(mTempRect);

            getGlobalVisibleRect(mTempRect);
            node.setBoundsInScreen(mTempRect);
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
            return mRootMenu.getItem(id);
        }

        @Override
        protected int getVirtualViewIdForItem(RadialMenuItem item) {
            return mRootMenu.indexOf(item);
        }

        @Override
        protected RadialMenuItem getItemAt(float x, float y) {
            return getTouchedMenuItem(x, y).item;
        }

        @Override
        protected void getVisibleItems(List<RadialMenuItem> items) {
            for (int i = 0; i < mRootMenu.size(); i++) {
                final RadialMenuItem item = mRootMenu.getItem(i);
                items.add(item);
            }
        }
    }
}

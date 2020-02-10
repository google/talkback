/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.switchaccess.ui;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowManager.BadTokenException;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.DialogActivity;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.ScreenViewListener;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.menuitems.GlobalMenuItem;
import com.google.android.accessibility.switchaccess.menuitems.GroupedMenuItem;
import com.google.android.accessibility.switchaccess.menuitems.GroupedMenuItem.GroupedMenuItemHeader;
import com.google.android.accessibility.switchaccess.menuitems.GroupedMenuItemForVolumeAction;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem.SelectMenuItemListener;
import com.google.android.accessibility.switchaccess.menuitems.SimpleGroupedMenuItem;
import com.google.android.accessibility.switchaccess.menuitems.VolumeAdjustmentMenuItem;
import com.google.android.accessibility.switchaccess.menuitems.VolumeAdjustmentMenuItem.VolumeChangeListener;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuItemEnum;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuTypeEnum.MenuType;
import com.google.android.accessibility.switchaccess.utils.OverlayUtils;
import com.google.android.accessibility.utils.widget.SimpleOverlay;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.libraries.accessibility.utils.concurrent.ThreadUtils;
import com.google.android.libraries.accessibility.utils.device.ScreenUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.SideEffectFree;

/**
 * Controller for the Switch Access overlay. The controller handles two operations: it outlines
 * groups of Views, and it presents context menus (with Views that are outlined).
 */
public class OverlayController implements VolumeChangeListener {

  private static final String TAG = "OverlayController";

  // The padding between the tool tip placed above, below, or beside the Switch Access menu and the
  // edge of the item for which the menu is showing.
  public static final int PADDING_BETWEEN_ITEM_AND_MENU = 10;

  // In point scan, the radius of the circular cutout placed in the menu's background around the
  // selected point.
  public static final int POINT_SCAN_CUTOUT_RADIUS = 50;

  // Add a small delay to the time between adjusting the menu button position and showing the menu
  // button. This is only needed when point scan is enabled, as a separate delay is used with
  // OptionManager.
  private static final int POINT_SCAN_DELAY_BETWEEN_ADJUSTING_MENU_BUTTON_POSITION_AND_SHOWING_MS =
      50;

  private static final String SCREEN_NAME_GLOBAL_MENU = "Global menu";

  // Add a slight delay for configuring the overlays to ensure that the corresponding overlay has
  // been drawn.
  @VisibleForTesting protected static final int TIME_BETWEEN_DRAWING_OVERLAY_CONFIGURING_MS = 100;

  private final SimpleOverlay highlightOverlay;
  private final SimpleOverlay menuOverlay;
  private final SimpleOverlay globalMenuButtonOverlay;
  private final SimpleOverlay screenSwitchOverlay;

  private List<MenuItem> menuItems;
  private int firstMenuItemIndex;
  private int lastMenuItemIndex;

  // The parent menu items for a set of nested menu items.
  @Nullable private Stack<GroupedMenuItem> parentMenuItems;
  @Nullable private GroupedMenuItem currentMenuGrouping;

  @Nullable private ScreenViewListener screenViewListener;

  @Nullable private SelectMenuItemListener selectMenuItemListener;

  private final RelativeLayout relativeLayout;

  private final BroadcastReceiver broadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if (action != null) {
            if (action.equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
              configureOverlays();
            } else if (action.equals(DialogActivity.DO_NOT_DISTURB_DISMISSED)) {
              menuOverlay.show();
            }
          }
        }
      };

  private final List<MenuListener> menuListeners;

  // Receives updates when the Switch Access global menu button is shown.
  private GlobalMenuButtonListener globalMenuButtonListener;

  // Id of the Switch Access menu that is currently shown. This variable is used to make sure that
  // the menu being closed is the same menu whose opening was logged.
  private int menuId = 0;

  private int minDistanceFromToolTipToScreenEdge;

  // The view id of the last tool tip that was shown on the menuOverlay. The menu will always have
  // exactly one tool tip showing, so this is used to hide the previously shown tool tip and show
  // the new desired one or to check if the new tool tip is the same as the one already showing.
  private int lastToolTipViewIdShown = R.id.tooltip_up;

  // TODO: Ensure that bounds are used consistently.
  // The current bounds of the on-screen element to which the Switch Access menu is tethered. This
  // corresponds to the bounds in the most recent call to #drawMenu.
  private Rect currentMenuHighlightBounds = new Rect();

  /**
   * @param highlightOverlay The overlay on which to draw focus indications. Should not send {@link
   *     AccessibilityEvent}s
   * @param globalMenuButtonOverlay The overlay on which the menu button will be drawn. Should send
   *     {@link AccessibilityEvent}s
   * @param menuOverlay The overlay on which menus will be drawn. Should send {@link
   *     AccessibilityEvent}s
   */
  public OverlayController(
      SimpleOverlay highlightOverlay,
      SimpleOverlay globalMenuButtonOverlay,
      SimpleOverlay menuOverlay,
      SimpleOverlay screenSwitchOverlay) {
    menuListeners = new ArrayList<>();
    menuItems = new ArrayList<>();

    // Create the highlight overlay as a full-screen SimpleOverlay that does not send
    // AccessibilityEvents or accept touch.
    this.highlightOverlay = highlightOverlay;
    WindowManager.LayoutParams params = this.highlightOverlay.getParams();
    params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    // Make sure the screen stays on if this window is visible to the user
    params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
    params.x = 0;
    params.y = 0;
    this.highlightOverlay.setParams(params);
    this.highlightOverlay.setContentView(R.layout.switch_access_overlay_layout);
    relativeLayout =
        (RelativeLayout) this.highlightOverlay.findViewById(R.id.overlayRelativeLayout);

    this.menuOverlay = menuOverlay;
    // Create the menu overlay as a full-screen SimpleOverlay that sends AccessibilityEvents.
    OverlayUtils.setLayoutParamsForFullScreenAccessibilityOverlay(menuOverlay);

    // Create the menu button as a SimpleOverlay that sends AccessibilityEvents. Create it as a
    // separate overlay from the menu overlay as the menu overlay intercepts all touches and
    // the menu button only intercepts touches to the menu button area.
    this.globalMenuButtonOverlay = globalMenuButtonOverlay;
    params = this.globalMenuButtonOverlay.getParams();
    params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    params.format = PixelFormat.TRANSPARENT;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    params.flags |= WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
    params.y = 0;
    params.width = WindowManager.LayoutParams.WRAP_CONTENT;
    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
    params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
    this.globalMenuButtonOverlay.setParams(params);
    this.globalMenuButtonOverlay.setContentView(R.layout.switch_access_global_menu_button);

    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
    filter.addAction(DialogActivity.DO_NOT_DISTURB_DISMISSED);
    this.highlightOverlay.getContext().registerReceiver(broadcastReceiver, filter);

    // Create the screen switch as a SimpleOverlay that sends AccessibilityEvents and intercepts
    // all touches on the screen.
    this.screenSwitchOverlay = screenSwitchOverlay;
    WindowManager.LayoutParams screenSwitchParams = this.screenSwitchOverlay.getParams();
    screenSwitchParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    screenSwitchParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    screenSwitchParams.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    screenSwitchParams.x = 0;
    screenSwitchParams.y = 0;
    this.screenSwitchOverlay.setParams(screenSwitchParams);
    this.screenSwitchOverlay.setContentView(R.layout.switch_access_screen_switch_layout);
    this.screenSwitchOverlay.setRootViewClassName(ButtonSwitchAccessIgnores.class.getName());
  }

  /**
   * Get the highlight and menu overlays ready to show. This method displays the empty overlays and
   * tweaks them to make sure they are in the right location.
   */
  public void configureOverlays() {
    // Make sure we have the menu layout for the correct orientation.
    menuOverlay.setContentView(R.layout.switch_access_context_menu_layout);
    menuOverlay
        .findViewById(R.id.next_arrow_button)
        .setOnClickListener(view -> moveToNextMenuItemsOrClearOverlays());
    menuOverlay
        .findViewById(R.id.previous_arrow_button)
        .setOnClickListener(view -> moveToPreviousMenuItems());

    int toolTipLength = menuOverlay.findViewById(R.id.tooltip_up).getWidth();
    int menuPadding =
        getContext()
            .getResources()
            .getDimensionPixelSize(R.dimen.switch_access_horizontal_margin_to_menu_edge);
    int menuCornerRadius =
        getContext().getResources().getDimensionPixelSize(R.dimen.switch_access_menu_border_radius);
    minDistanceFromToolTipToScreenEdge = menuPadding + (toolTipLength / 2) + menuCornerRadius;

    drawNewMenuButtons();

    configureOverlayBeforeShow(highlightOverlay);
    configureOverlayBeforeShow(menuOverlay);
    ThreadUtils.runOnMainThread(SwitchAccessService::isActive, highlightOverlay::show);

    ThreadUtils.runOnMainThreadDelayed(
        SwitchAccessService::isActive,
        () -> {
          configureOverlayAfterShow(highlightOverlay);
          configureOverlayAfterShow(menuOverlay);
        },
        TIME_BETWEEN_DRAWING_OVERLAY_CONFIGURING_MS);
  }

  /** Override focus highlighting with a custom overlay */
  public void addViewAndShow(View view) {
    relativeLayout.addView(view);
    try {
      // TODO: Investigate removing this try/catch block. Exceptions occur in PointScan
      // when starting or shutting down. Since the exceptions occur after the service is connected
      // and before the service is unbound, the service isn't recognized as shutting down.
      ThreadUtils.runOnMainThread(SwitchAccessService::isActive, highlightOverlay::show);

      // The configuration has to happen after the overlay is drawn to ensure the dimensions are
      // measured properly which doesn't happen until the next frame, hence this runs after a delay.
      ThreadUtils.runOnMainThreadDelayed(
          SwitchAccessService::isActive,
          () -> configureOverlayAfterShow(highlightOverlay),
          TIME_BETWEEN_DRAWING_OVERLAY_CONFIGURING_MS);
    } catch (BadTokenException | IllegalStateException e) {
      // Do nothing, as this can happen when the service is starting or shutting down.
      LogUtils.d(TAG, "Couldn't show highlight overlay: %s", e);
    }
  }

  /** Show the screen switch overlay */
  public void showScreenSwitch() {
    try {
      ThreadUtils.runOnMainThread(SwitchAccessService::isActive, screenSwitchOverlay::show);
    } catch (BadTokenException | IllegalStateException e) {
      // TODO: Investigate the root cause of this issue.
      // Do nothing -- this prevents the Set-Up Wizard from crashing if there is an issue with
      // initially showing the screen switch. The BadTokenException is caused when the overlay
      // is shown while the fragments are transitioning while the IllegalStateException is thrown
      // when the overlay is added while the overlay already exists.
      LogUtils.d(TAG, "Couldn't show screen switch overlay: %s", e);
    }
  }

  /** Hide the screen switch overlay */
  public void hideScreenSwitch() {
    screenSwitchOverlay.hide();
  }

  /**
   * Set the onTouchListener of the screen switch.
   *
   * @param onTouchListener The listener that will be assigned to the screen switch.
   */
  public void setScreenSwitchOnTouchListener(View.OnTouchListener onTouchListener) {
    screenSwitchOverlay.setOnTouchListener(onTouchListener);
  }

  /** Clear all overlays. */
  public void clearAllOverlays() {
    clearMenuOverlay();
    clearHighlightOverlay();
    clearMenuButtonOverlay();
  }

  /** Clear highlighting overlay. */
  public void clearHighlightOverlay() {
    relativeLayout.removeAllViews();
    highlightOverlay.hide();
  }

  /** Clear the menu button overlay. */
  public void clearMenuButtonOverlay() {
    globalMenuButtonOverlay.hide();
  }

  /** Clear menu overlay. */
  public void clearMenuOverlay() {
    menuOverlay.hide();
    menuItems.clear();
    for (MenuListener menuListener : menuListeners) {
      menuListener.onMenuClosed(menuId);
    }
  }

  /** Is one of the menus visible? */
  @SideEffectFree
  public boolean isMenuVisible() {
    return menuOverlay.isVisible();
  }

  /** Is the highlight overlay visible? */
  public boolean isHighlightOverlayVisible() {
    return highlightOverlay.isVisible();
  }

  /**
   * Returns {@code true} if the volume slider on the volume adjustment menu page is visible, as
   * this means that a non-dynamic layout is being used.
   */
  public boolean isStaticMenuVisible() {
    VolumeSlider volumeSlider = (VolumeSlider) menuOverlay.findViewById(R.id.menu_slider_view);
    return (volumeSlider != null) && (volumeSlider.getVisibility() == View.VISIBLE);
  }

  /** Shut down nicely. */
  public void shutdown() {
    highlightOverlay.getContext().unregisterReceiver(broadcastReceiver);
    clearAllOverlays();
  }

  /**
   * A menu button is drawn at the top of the screen if a menu is not currently visible. This button
   * offers the user the possibility of clearing the focus or choosing global actions (i.e Home,
   * Back, Notifications, etc). This menu button should be displayed if the user is using group
   * selection or point scanning.
   */
  public void drawMenuButtonIfMenuNotVisible() {
    if (!isMenuVisible()) {
      Button menuButton = (Button) globalMenuButtonOverlay.findViewById(R.id.global_menu_button);
      if (!menuButton.hasOnClickListeners()) {
        menuButton.setOnClickListener(view -> drawGlobalMenu());
      }
      // Adjust the global menu button after it's drawn to ensure it's not obscured by a notch. Only
      // do so if the button is not already visible to avoid re-drawing the button every time
      // the screen changes or every time the highlight moves.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !globalMenuButtonOverlay.isVisible()) {
        // When switching from having a cutout to having no cutout (e.g. turning a notched phone
        // sideways to landscape mode), we change the gravity to CENTER_HORIZONTAL in
        // #adjustGlobalMenuButtonPosition.
        globalMenuButtonOverlay
            .getRootView()
            .post(
                () -> {
                  WindowInsets windowInsets =
                      this.globalMenuButtonOverlay.getRootView().getRootWindowInsets();
                  // Because of how View.getLayoutOnScreen renders in Q, we need to first check
                  // that there is an inset before adjusting the gravity. Otherwise, the highlight
                  // can be incorrect on screens without a display cutout.
                  if (windowInsets == null) {
                    return;
                  }
                  DisplayCutout displayCutout = windowInsets.getDisplayCutout();
                  if (displayCutout == null) {
                    adjustGlobalMenuButtonPosition(displayCutout, new ArrayList<>());
                  } else {
                    adjustGlobalMenuButtonForDisplayCutouts();
                  }
                });
      }
      showGlobalMenu();
    }
  }

  private void showGlobalMenu() {
    // TODO: Investigate if this try/catch can be removed. Putting this on the main
    // thread can still cause a BadTokenException before SwitchAccessService#onUnbind is called.
    try {
      // Only add a delay with point scan. Any delays needed by other scanning methods will be
      // handled in OptionManager.
      long timeToDelay =
          SwitchAccessPreferenceUtils.isPointScanEnabled(getContext())
              ? POINT_SCAN_DELAY_BETWEEN_ADJUSTING_MENU_BUTTON_POSITION_AND_SHOWING_MS
              : 0;
      ThreadUtils.runOnMainThreadDelayed(
          SwitchAccessService::isActive,
          () -> {
            globalMenuButtonOverlay.show();
            if (globalMenuButtonListener != null) {
              globalMenuButtonListener.onGlobalMenuButtonShown();
            }
          },
          timeToDelay);
    } catch (BadTokenException | IllegalStateException e) {
      // Do nothing, as this can happen when the service is starting or shutting down.
    }
  }

  private void adjustGlobalMenuButtonForDisplayCutouts() {
    // Having the gravity at CENTER_HORIZONTAL rather than START causes the display cutout's
    // bounding rects to be incorrect (the left coordinate is 0 even though the left of the cutout
    // is to the left of the center; it should be negative with CENTER_HORIZONTAL gravity). With
    // Gravity#START, the left coordinate is a positive number that accurately represents the
    // cutout's x position. We change the gravity back to START here so when this runnable is
    // executed in the next frame, it will calculate the bounding rects of the cutout correctly,
    // based on Gravity#START.
    LayoutParams params = globalMenuButtonOverlay.getParams();
    params.gravity = Gravity.TOP | Gravity.START;
    globalMenuButtonOverlay.setParams(params);
    // Hide the global menu button overlay before further adjusting the position to avoid a visual
    // lag when showing the menu button (i.e. moving the menu button and highlight to the correct
    // position immediately after showing them in the incorrect position).
    globalMenuButtonOverlay.hide();
    globalMenuButtonOverlay
        .getRootView()
        .post(
            () -> {
              // We need to get the root window insets again to ensure that the correct gravity is
              // used when calculating the cutouts.
              WindowInsets windowInsets =
                  this.globalMenuButtonOverlay.getRootView().getRootWindowInsets();
              if (windowInsets != null) {
                DisplayCutout displayCutout = windowInsets.getDisplayCutout();
                List<Rect> boundingRects =
                    (displayCutout == null) ? new ArrayList<>() : displayCutout.getBoundingRects();
                adjustGlobalMenuButtonPosition(displayCutout, boundingRects);
              }
            });
    showGlobalMenu();
  }

  /**
   * @return If the menu button is visible, gets the location of the menu button at the top of the
   *     screen. Otherwise null is returned.
   */
  @Nullable
  public Rect getMenuButtonLocation() {
    if (!menuOverlay.isVisible()) {
      Button menuButton = (Button) globalMenuButtonOverlay.findViewById(R.id.global_menu_button);
      Rect buttonLocation = new Rect();
      menuButton.getGlobalVisibleRect(buttonLocation);
      int[] overlayLocation = new int[2];
      menuButton.getLocationOnScreen(overlayLocation);
      buttonLocation.right += overlayLocation[0] - buttonLocation.left;
      buttonLocation.left += overlayLocation[0] - buttonLocation.left;
      return buttonLocation;
    }
    return null;
  }

  /**
   * @return If the cancel button is visible, gets the location of the cancel button in the menu
   *     overlay. Otherwise null is returned.
   */
  @Nullable
  public Rect getCancelButtonLocation() {
    MenuButton cancelButton = (MenuButton) menuOverlay.findViewById(R.id.cancel_button);
    if (menuOverlay.isVisible() && cancelButton != null && cancelButton.isEnabled()) {
      Rect buttonLocation = new Rect();
      cancelButton.getGlobalVisibleRect(buttonLocation);
      int[] overlayLocation = new int[2];
      cancelButton.getLocationOnScreen(overlayLocation);
      buttonLocation.right += overlayLocation[0] - buttonLocation.left;
      buttonLocation.left += overlayLocation[0] - buttonLocation.left;
      buttonLocation.bottom += overlayLocation[1] - buttonLocation.top;
      buttonLocation.top += overlayLocation[1] - buttonLocation.top;
      return buttonLocation;
    }
    return null;
  }

  /**
   * Draws a menu based on the provided menu items at a location based on the provided bounds.
   *
   * @param menuItems The list of items to draw in the menu
   * @param bounds A {@link Rect} representing the bounds of the item for which to draw the menu
   */
  public void drawMenu(List<MenuItem> menuItems, Rect bounds) {
    if (menuItems.isEmpty()) {
      // Don't do anything if we have no items.
      return;
    }

    // Set the class name of the AccessibilityNodeInfo of menuOverlay, so
    // SwitchAccessScreenFeedbackManager can provide customized spoken feedback for Switch Access
    // menus. If this is not manually set, the class name will be FrameLayout and no feedback will
    // be spoken.
    if (menuItems.get(0) instanceof GlobalMenuItem) {
      menuOverlay.setRootViewClassName(SwitchAccessGlobalMenuLayout.class.getName());
      for (MenuListener menuListener : menuListeners) {
        menuListener.onMenuShown(MenuType.TYPE_GLOBAL, ++menuId);
      }
    } else {
      menuOverlay.setRootViewClassName(SwitchAccessActionsMenuLayout.class.getName());
      for (MenuListener menuListener : menuListeners) {
        menuListener.onMenuShown(MenuType.TYPE_ACTION, ++menuId);
      }
    }

    // Store data about the new menu.
    this.menuItems = menuItems;
    firstMenuItemIndex = 0;
    lastMenuItemIndex = menuItems.size();

    // When the menu is opened at its first layer, the back button in the footer should never be
    // shown.
    parentMenuItems = null;
    currentMenuGrouping = null;
    useDynamicLayout(true);
    hideSubMenuBackButtonAndHeader();

    // Draw and show the menu.
    if (drawNewMenuButtons()) {
      showMenuOverlay();

      // Move the menu dynamically to be next to the selected item.
      menuOverlay
          .getRootView()
          .post(
              () -> {
                SwitchAccessMenuLayout menuLayout =
                    (SwitchAccessMenuLayout) menuOverlay.findViewById(R.id.menu_scrim);
                // If the menu layout has shifted up or down, the bounds should too.
                int[] location = new int[2];
                menuLayout.getLocationOnScreen(location);
                bounds.top -= location[1];
                bounds.bottom -= location[1];
                menuLayout.setLayoutCutout(bounds);
                if (resizeMenuToFitOnScreen(bounds)) {
                  drawNewMenuButtons();
                  showMenuOverlay();
                }
                currentMenuHighlightBounds = bounds;
                moveMenuNextToItemAndPadMenuToGrid(menuLayout);
              });
    }
  }

  /** Moves to the next set of menu items. Does nothing if no more items are present. */
  private void moveToNextMenuItemsOrClearOverlays() {
    if (lastMenuItemIndex == menuItems.size()) {
      return;
    }

    firstMenuItemIndex = lastMenuItemIndex;
    FlexboxLayout menuButtonsLayout = (FlexboxLayout) menuOverlay.findViewById(R.id.menu_buttons);
    lastMenuItemIndex =
        Math.min(menuItems.size(), lastMenuItemIndex + menuButtonsLayout.getFlexItemCount());
    updateVisibleMenuButtons();

    menuOverlay.findViewById(R.id.previous_arrow_button).setVisibility(View.VISIBLE);
    if (lastMenuItemIndex == menuItems.size()) {
      menuOverlay.findViewById(R.id.next_arrow_button).setVisibility(View.INVISIBLE);
    }

    if (selectMenuItemListener != null) {
      selectMenuItemListener.onMenuItemSelected(
          SwitchAccessMenuItemEnum.MenuItem.MENU_BUTTON_NEXT_SCREEN);
    }
  }

  /** Moves to the previous menu items in the menu, or does nothing if there are none. */
  public void moveToPreviousMenuItems() {
    if (firstMenuItemIndex == 0) {
      return;
    }

    lastMenuItemIndex = firstMenuItemIndex;
    FlexboxLayout menuButtonsLayout = (FlexboxLayout) menuOverlay.findViewById(R.id.menu_buttons);
    firstMenuItemIndex = Math.max(0, firstMenuItemIndex - menuButtonsLayout.getFlexItemCount());
    updateVisibleMenuButtons();

    menuOverlay.findViewById(R.id.next_arrow_button).setVisibility(View.VISIBLE);
    if (firstMenuItemIndex == 0) {
      menuOverlay.findViewById(R.id.previous_arrow_button).setVisibility(View.INVISIBLE);
    }

    if (selectMenuItemListener != null) {
      selectMenuItemListener.onMenuItemSelected(
          SwitchAccessMenuItemEnum.MenuItem.MENU_BUTTON_PREVIOUS_SCREEN);
    }
  }

  /**
   * Shift the menuOverlay vertically to align with the given bounds. If isMenuLeftOrRightOfItem is
   * true, the menu is placed on the left or right of the item. If isMenuLeftOrRightOfItem is false
   * and the item is larger than half of the screen height, the top of the menu should align with
   * the top of the item. If the item is smaller than that and there is more space above the item,
   * the bottom of the menu aligns with the top of the item. If there is more space below the item,
   * the top of the menu aligns with the bottom of the item.
   *
   * @param bounds The bounds of the item for which to show the menu
   * @param isMenuLeftOrRightOfItem {@code true} if the menu is placed left or right of the item
   * @return {@code true} if the menuOverlay has more space above the given bounds, or {@code false}
   *     if the menuOverlay has more space below or inside the given bounds
   */
  @VisibleForTesting
  boolean placeMenuOverlayAboveOrBelowBounds(Rect bounds, boolean isMenuLeftOrRightOfItem) {
    int verticalPadding = PADDING_BETWEEN_ITEM_AND_MENU;
    View adjustableView = menuOverlay.findViewById(R.id.menu_layout);
    Point screenSize = ScreenUtils.getRealScreenSize(menuOverlay.getContext());
    int distanceToOverlayTop = 0;
    boolean isMenuDrawnAboveBounds = false;

    // Ensure the menu is outside the circular cutout for Point Scan.
    boolean areBoundsAPoint = OverlayUtils.areBoundsAPoint(bounds);
    if (areBoundsAPoint) {
      verticalPadding += POINT_SCAN_CUTOUT_RADIUS;
    }

    int menuHeight = adjustableView.getHeight();
    // If the menu will extend past the screen when placed above or below the bounds, despite
    // pagination, we should anchor the menu to the top bounds. This reduces the likelihood that
    // the menu will overflow past the screen.
    boolean doesMenuExtendPastScreen =
        (menuHeight > bounds.top) && (menuHeight > (screenSize.y - bounds.bottom));
    if (OverlayUtils.areBoundsLargerThanHalfScreenHeight(bounds, screenSize)
        || doesMenuExtendPastScreen) {
      distanceToOverlayTop = OverlayUtils.getInsideTopBounds(bounds, verticalPadding);
    } else {
      int paddingFromMenuEdgeToItemEdge;
      if (isMenuLeftOrRightOfItem) {
        int paddingForPointsNearEdge =
            areBoundsAPoint ? (verticalPadding + POINT_SCAN_CUTOUT_RADIUS) : 0;
        paddingFromMenuEdgeToItemEdge = -bounds.height() - paddingForPointsNearEdge;
      } else {
        paddingFromMenuEdgeToItemEdge = verticalPadding;
      }
      // Draw the menu above or below the item, choosing the larger space of the two.
      if (OverlayUtils.isSpaceAboveBoundsGreater(bounds, screenSize)) {
        // Align the bottom of the menu with the top of the item. If the item is too close to the
        // edge, we are placing the menu next to the item, so align the bottom of the menu with the
        // bottom of the item. For point scan, shift down by POINT_SCAN_CUTOUT_RADIUS so the cutout
        // circle is within the menu's vertical bounds.
        paddingFromMenuEdgeToItemEdge += adjustableView.getHeight();
        distanceToOverlayTop = OverlayUtils.getAboveBounds(bounds, paddingFromMenuEdgeToItemEdge);
        isMenuDrawnAboveBounds = true;
      } else {
        // Align the top of the menu with the bottom of the item. If the item is too close to the
        // edge, we are placing the menu next to the item, so align the top of the menu with the top
        // of the item. For point scan, shift up by POINT_SCAN_CUTOUT_RADIUS so the cutout circle is
        // within the menu's vertical bounds.
        distanceToOverlayTop = OverlayUtils.getBelowBounds(bounds, paddingFromMenuEdgeToItemEdge);
      }
    }

    MarginLayoutParams marginLayoutParams = (MarginLayoutParams) adjustableView.getLayoutParams();
    marginLayoutParams.topMargin = distanceToOverlayTop;
    // Allow menu to extend below the bottom of the screen if necessary. We want to resize the menu
    // if it extends past the bottom edge, but if we don't allow this margin, the View will just be
    // clipped to the edge, and we won't be able to resize properly.
    marginLayoutParams.bottomMargin = -distanceToOverlayTop;
    adjustableView.setLayoutParams(marginLayoutParams);
    return isMenuDrawnAboveBounds;
  }

  /**
   * Displays the menuOverlay to the left or or right of the given bounds, depending on which has
   * more space.
   *
   * @param bounds The bounds of the item for which to show the menu
   * @return {@code true} if the overlay was drawn to the left given bounds, or {@code false} if the
   *     overlay was drawn to the right of the given bounds
   */
  @VisibleForTesting
  boolean placeMenuOverlayLeftOrRightOfBounds(Rect bounds) {
    Point screenSize = ScreenUtils.getRealScreenSize(menuOverlay.getContext());
    boolean shouldPlaceMenuLeftOfBounds = bounds.centerX() > (screenSize.x / 2);
    View fullMenu = menuOverlay.findViewById(R.id.menu_scrim);
    int paddingForItem = bounds.width() + PADDING_BETWEEN_ITEM_AND_MENU;

    // Ensure the menu is outside the circular cutout for Point Scan.
    if (OverlayUtils.areBoundsAPoint(bounds)) {
      int distanceFromItemCenterToScreenEdge =
          (bounds.left < (screenSize.x / 2)) ? bounds.left : (screenSize.x - bounds.left);
      paddingForItem += distanceFromItemCenterToScreenEdge + POINT_SCAN_CUTOUT_RADIUS;
    }

    // Leave horizontal space for the item.
    if (shouldPlaceMenuLeftOfBounds) {
      fullMenu.setPadding(fullMenu.getPaddingLeft(), 0, paddingForItem, 0);
    } else {
      fullMenu.setPadding(paddingForItem, 0, fullMenu.getPaddingRight(), 0);
    }

    // Align the menuOverlay vertically with the item.
    placeMenuOverlayAboveOrBelowBounds(bounds, true);
    return shouldPlaceMenuLeftOfBounds;
  }

  @VisibleForTesting
  void showToolTip(int toolTipToShowViewId, Rect itemBounds, SwitchAccessMenuLayout menuLayout) {
    View toolTipToShow = menuOverlay.findViewById(toolTipToShowViewId);
    if (toolTipToShow == null) {
      return;
    }

    // We don't need to hide the previous tool tip if it is the same one we want to show now.
    // However, we still need to adjust the margins of the tool tip (below) because the horizontal
    // position of the item may have changed even if the vertical position hasn't.
    if (lastToolTipViewIdShown != toolTipToShowViewId) {
      menuOverlay.findViewById(lastToolTipViewIdShown).setVisibility(View.GONE);
      toolTipToShow.setVisibility(View.VISIBLE);
      lastToolTipViewIdShown = toolTipToShowViewId;
    }

    // The length of the tool tip is the longer dimension between its height and width. Since all
    // tool tips are just transposed versions of each other, the width of the tool tip up
    // represents the longest dimension for all tool tips.
    int toolTipLength = menuOverlay.findViewById(R.id.tooltip_up).getWidth();
    MarginLayoutParams toolTipMargins = (MarginLayoutParams) toolTipToShow.getLayoutParams();
    toolTipMargins.setMargins(0, 0, 0, 0);
    if ((toolTipToShowViewId == R.id.tooltip_up) || (toolTipToShowViewId == R.id.tooltip_down)) {
      Point screenSize = ScreenUtils.getRealScreenSize(getContext());
      boolean isItemNearLeftEdge = itemBounds.centerX() < (screenSize.x / 2);
      int distanceFromItemCenterToScreenEdge =
          isItemNearLeftEdge ? itemBounds.centerX() : (screenSize.x - itemBounds.centerX());
      int distanceFromToolTipCenterToScreenEdge =
          (toolTipLength / 2) + menuOverlay.findViewById(R.id.menu_layout).getLeft();
      if (distanceFromItemCenterToScreenEdge > minDistanceFromToolTipToScreenEdge) {
        // Place the center of the tooltip at the center of the item.
        toolTipMargins.leftMargin = itemBounds.centerX() - distanceFromToolTipCenterToScreenEdge;
      } else {
        // Place the center of the tooltip at the item edge furthest from the screen edge.
        int itemEdge = isItemNearLeftEdge ? itemBounds.right : itemBounds.left;
        toolTipMargins.leftMargin = itemEdge - distanceFromToolTipCenterToScreenEdge;
      }
    } else {
      MarginLayoutParams menuMargins =
          (MarginLayoutParams) menuOverlay.findViewById(R.id.menu_layout).getLayoutParams();
      toolTipMargins.topMargin = itemBounds.centerY() - (toolTipLength / 2) - menuMargins.topMargin;
    }
    toolTipToShow.setLayoutParams(toolTipMargins);

    // Set the tooltip and content so a border can be drawn around the menu.
    menuLayout.setToolTipView(toolTipToShow);
  }

  /**
   * Replaces the menu items with the given menu items and then updates the menu with the new menu
   * items. This does not draw a new menu, but instead replaces the content of the already-displayed
   * menu buttons. If the new menu items don't fit on one page, adds the remaining items to the next
   * page.
   *
   * <p>This should be used to update the currently displayed menu items with nested menu items
   * (e.g. to display granularities after a text-editing action is selected).
   *
   * @param subMenu The submenu that should be shown.
   */
  public void showSubMenu(GroupedMenuItem subMenu) {
    if (parentMenuItems == null) {
      parentMenuItems = new Stack<>();
      currentMenuGrouping = new SimpleGroupedMenuItem(this, menuItems);
    }

    if (currentMenuGrouping != null) {
      parentMenuItems.push(currentMenuGrouping);
    }

    this.currentMenuGrouping = subMenu;
    if (subMenu.shouldPopulateLayoutDynamically()) {
      useDynamicLayout(true);
      updateMenuContent(subMenu.getSubMenuItems());
    } else {
      updateMenuContentForVolumePage(subMenu.getSubMenuItems());
    }

    showSubMenuBackButtonAndHeader(subMenu.getHeader());

    if (subMenu.shouldPopulateLayoutDynamically()) {
      updateMenuToFillAvailableSpace();
    }
  }

  private void updateMenuContentForVolumePage(List<MenuItem> newMenuItems) {
    useDynamicLayout(false);
    menuItems = newMenuItems;
    firstMenuItemIndex = 0;
    lastMenuItemIndex = newMenuItems.size();

    if (newMenuItems.size() == 2) {
      setIconTextAndOnClickListenerForMenuButton(
          (MenuButton) menuOverlay.findViewById(R.id.decrease_volume_button), newMenuItems.get(0));
      setIconTextAndOnClickListenerForMenuButton(
          (MenuButton) menuOverlay.findViewById(R.id.increase_volume_button), newMenuItems.get(1));
    }
  }

  private void useDynamicLayout(boolean shouldUseDynamicLayout) {
    if (shouldUseDynamicLayout) {
      menuOverlay.findViewById(R.id.flexbox_layout).setVisibility(View.VISIBLE);
      menuOverlay.findViewById(R.id.menu_slider_view).setVisibility(View.GONE);
    } else {
      menuOverlay.findViewById(R.id.flexbox_layout).setVisibility(View.GONE);
      VolumeSlider menuSlider = (VolumeSlider) menuOverlay.findViewById(R.id.menu_slider_view);
      menuSlider.setVisibility(View.VISIBLE);
      if (currentMenuGrouping instanceof GroupedMenuItemForVolumeAction) {
        menuSlider.setVolumeStreamType(
            ((GroupedMenuItemForVolumeAction) currentMenuGrouping).getVolumeStreamType());
      }
      menuSlider.setEnabled(true);
      menuOverlay.findViewById(R.id.previous_arrow_button).setVisibility(View.INVISIBLE);
      menuOverlay.findViewById(R.id.next_arrow_button).setVisibility(View.INVISIBLE);
    }
  }

  @VisibleForTesting
  void updateMenuContent(List<MenuItem> newMenuItems) {
    menuItems = newMenuItems;
    firstMenuItemIndex = 0;
    FlexboxLayout menuButtonsLayout = (FlexboxLayout) menuOverlay.findViewById(R.id.menu_buttons);
    int maxItemsPerPage = menuButtonsLayout.getFlexItemCount();
    View nextArrow = menuOverlay.findViewById(R.id.next_arrow_button);
    if (maxItemsPerPage < menuItems.size()) {
      nextArrow.setVisibility(View.VISIBLE);
      lastMenuItemIndex = maxItemsPerPage;
    } else {
      nextArrow.setVisibility(View.INVISIBLE);
      lastMenuItemIndex = menuItems.size();
    }
    menuOverlay.findViewById(R.id.previous_arrow_button).setVisibility(View.INVISIBLE);
    updateVisibleMenuButtons();
  }

  @VisibleForTesting
  int getLastMenuItemIndex() {
    return lastMenuItemIndex;
  }

  /**
   * Resizes the menu, so that only the max number of menu items that fit on the screen are shown on
   * the first page of the menu.
   *
   * <p>When this method is called, all menu items have already been added to the menu. If the menu
   * (containing all the items) doesn't fit anywhere as is, we measure how many pixels beyond the
   * edge of the screen the menu is, and we measure the height of each Flexbox row. Then we remove
   * as many rows as we need to for the menu to fit.
   *
   * @param bounds The bounds of the selected item on screen
   * @return {@code true} if this menu requires multiple pages
   */
  @VisibleForTesting
  boolean resizeMenuToFitOnScreen(Rect bounds) {
    menuOverlay.findViewById(R.id.previous_arrow_button).setVisibility(View.INVISIBLE);

    // Get the largest space available to place the menu.
    Point screenSize = ScreenUtils.getRealScreenSize(getContext());
    int spaceBelowItem = screenSize.y - bounds.bottom;
    int spaceAboveItem = bounds.top;
    int spaceForMenu =
        (bounds.height() > (screenSize.y / 2))
            ? (screenSize.y - bounds.top)
            : Math.max(spaceAboveItem, spaceBelowItem);
    int menuHeight = menuOverlay.findViewById(R.id.menu_layout).getHeight();

    // If the menu can fit in the largest available space, it only needs one page.
    if (spaceForMenu > menuHeight) {
      menuOverlay.findViewById(R.id.next_arrow_button).setVisibility(View.INVISIBLE);
      return false;
    }

    // Calculate how many rows beyond the edge of the screen the menu is.
    int pixelsBeyondEdge = menuHeight - spaceForMenu;
    FlexboxLayout menuButtonsLayout = (FlexboxLayout) menuOverlay.findViewById(R.id.menu_buttons);
    double rowHeight = menuButtonsLayout.getFlexLines().get(0).getCrossSize();
    int numRows = menuButtonsLayout.getFlexLines().size();

    // Set the last menu item to be the last one that fits on screen and indicate there's another
    // menu page. If a single row is too large to fit on the screen, allow overflow, as this is
    // preferred to no menu items displaying.
    long numRowsBeyondEdge = Math.max(1, (long) Math.ceil(pixelsBeyondEdge / rowHeight));
    if (numRowsBeyondEdge >= numRows) {
      // If none of the rows fit on the screen, just calculate lastMenuItemIndex as the last
      // item of the first row and allow overflow. Otherwise, no row items will be displayed.
      lastMenuItemIndex = menuButtonsLayout.getFlexLines().get(0).getItemCountNotGone();
    } else {
      for (int i = 0; i < numRowsBeyondEdge; i++) {
        lastMenuItemIndex -=
            menuButtonsLayout.getFlexLines().get(numRows - 1 - i).getItemCountNotGone();
      }
    }

    menuOverlay.findViewById(R.id.next_arrow_button).setVisibility(View.VISIBLE);
    return true;
  }

  /*
   * Adjust the positioning of the global menu button if the display has a notch. Move to the left
   * or right of the notch if possible. Otherwise, move it down below the notch.
   *
   * @param displayCutout The DisplayCutout from which to get the notch's dimensions. If this is
   *    {@code null}, sets the global menu button's gravity to top, center
   * @param cutoutRects A list of Rects representing the areas of the screen which are not
   *    functional for use (i.e. the notches)
   */
  @VisibleForTesting
  void adjustGlobalMenuButtonPosition(
      @Nullable DisplayCutout displayCutout, List<Rect> cutoutRects) {
    LayoutParams globalMenuButtonParams = globalMenuButtonOverlay.getParams();
    if (displayCutout == null) {
      globalMenuButtonParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
    } else {
      Point screenSize = ScreenUtils.getRealScreenSize(getContext());
      View menuButton = globalMenuButtonOverlay.findViewById(R.id.global_menu_button);
      int menuButtonWidth = menuButton.getWidth();
      int leftOfCenteredMenuButton = (screenSize.x / 2) - (menuButtonWidth / 2);
      Rect centeredMenuButton =
          new Rect(
              leftOfCenteredMenuButton,
              0,
              leftOfCenteredMenuButton + menuButtonWidth,
              menuButton.getHeight());

      // Clear the margins.
      MarginLayoutParams menuButtonMarginParams = (MarginLayoutParams) menuButton.getLayoutParams();
      menuButtonMarginParams.setMargins(0, 0, 0, 0);

      // Find the cutout that intersects the centered menu button. If there's space to the right of
      // the cutout, move there. Otherwise if there's space to the left, move there. If neither side
      // has space, move just below the cutout.
      int safeInsetLeft = displayCutout.getSafeInsetLeft();
      int safeInsetRight = displayCutout.getSafeInsetRight();
      boolean cutoutIntersectsCenteredMenuButton = false;
      for (Rect cutoutRect : cutoutRects) {
        if (Rect.intersects(centeredMenuButton, cutoutRect)) {
          cutoutIntersectsCenteredMenuButton = true;
          if ((screenSize.x - safeInsetRight - cutoutRect.right) > menuButtonWidth) {
            globalMenuButtonParams.gravity = Gravity.TOP | Gravity.END;
            menuButtonMarginParams.rightMargin = screenSize.x - cutoutRect.right - menuButtonWidth;
          } else if ((cutoutRect.left - safeInsetLeft) > menuButtonWidth) {
            globalMenuButtonParams.gravity = Gravity.TOP | Gravity.START;
            menuButtonMarginParams.leftMargin = cutoutRect.left - menuButtonWidth;
          } else {
            globalMenuButtonParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            menuButtonMarginParams.topMargin = displayCutout.getSafeInsetTop();
          }
        }
      }
      if (!cutoutIntersectsCenteredMenuButton) {
        globalMenuButtonParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
      }
    }
    globalMenuButtonOverlay.setParams(globalMenuButtonParams);
  }

  /**
   * Adds extra empty items to the bottom row of the menu overlay so spacing works. We want the
   * FlexboxLayout to justify items (i.e. center all rows that have the maximum number of items and
   * left-align the last row if it has fewer than the maximum number of items), but the closest
   * option is to center-align, so we artificially left-align the bottom row.
   */
  @VisibleForTesting
  void fillRemainingSpaceForMenuOverlay() {
    FlexboxLayout menuButtonsLayout = (FlexboxLayout) menuOverlay.findViewById(R.id.menu_buttons);
    int numActiveButtons = menuButtonsLayout.getFlexItemCount();
    int numRows = menuButtonsLayout.getFlexLines().size();
    if (numRows > 1) {
      int maxItemsPerRow = menuButtonsLayout.getFlexLines().get(0).getItemCountNotGone();
      int numButtonsInLastRow = numActiveButtons % maxItemsPerRow;
      if (numButtonsInLastRow > 0) {
        for (int i = numButtonsInLastRow; i < maxItemsPerRow; i++) {
          menuButtonsLayout.addView(new MenuButton(menuOverlay.getContext()));
        }
      }
    } else if (numRows == 1) {
      // TODO: Investigate if we can resize the menu horizontally when there's only one
      // row.
      // Hide the view until the spacing is figured out.
      menuOverlay.getRootView().setVisibility(View.INVISIBLE);
      fillOutSingleRow(menuOverlay, menuButtonsLayout);
    }
  }

  /*
   * Fills out a single row of the given FlexboxLayout with empty buttons so that spacing of the
   * visible buttons is the same as it would be for a fully visible row. Since the flexbox is not
   * updated until the next frame, we have to check if the view is correct after it's been drawn.
   */
  private static void fillOutSingleRow(SimpleOverlay overlay, FlexboxLayout menuButtonsLayout) {
    menuButtonsLayout.addView(new MenuButton(overlay.getContext()));
    overlay
        .getRootView()
        .post(
            () -> {
              if (menuButtonsLayout.getFlexLines().size() > 1) {
                menuButtonsLayout.removeViewAt(menuButtonsLayout.getFlexItemCount() - 1);
                overlay.getRootView().setVisibility(View.VISIBLE);
              } else {
                fillOutSingleRow(overlay, menuButtonsLayout);
              }
            });
  }

  private void showMenuOverlay() {
    menuOverlay.show();
    if (highlightOverlay.isVisible()) {
      // Make sure the highlight overlay is on top.
      highlightOverlay.hide();
      highlightOverlay.show();
      if (screenSwitchOverlay.isVisible()) {
        // TODO: Fix bug where the first screen press on the menu doesn't perform the
        // action.
        screenSwitchOverlay.hide();
        screenSwitchOverlay.show();
      }
    }
  }

  /* Updates the currently displayed menu buttons with the latest menu items. */
  private void updateVisibleMenuButtons() {
    FlexboxLayout menuButtonsLayout = (FlexboxLayout) menuOverlay.findViewById(R.id.menu_buttons);
    int numNewMenuItems = lastMenuItemIndex - firstMenuItemIndex;
    int numMenuButtons = menuButtonsLayout.getFlexItemCount();
    for (int i = 0; i < numMenuButtons; i++) {
      MenuButton button = (MenuButton) menuButtonsLayout.getFlexItemAt(i);
      if (i < numNewMenuItems) {
        // Replace existing menu buttons with new ones.
        MenuItem newMenuItem = menuItems.get(firstMenuItemIndex + i);
        setIconTextAndOnClickListenerForMenuButton(button, newMenuItem);
      } else if (button != null) {
        // Clear now-unused buttons.
        button.clearButton();
      }
    }
  }

  /*
   * Moves the menuOverlay next to the selected item on screen and pad the menu out to display like
   * a grid of buttons.
   */
  private void moveMenuNextToItemAndPadMenuToGrid(SwitchAccessMenuLayout menuLayout) {
    menuOverlay
        .getRootView()
        .post(
            () -> {
              int toolTipToShowViewId;
              if (shouldDrawHorizontalToolTip()) {
                toolTipToShowViewId =
                    placeMenuOverlayLeftOrRightOfBounds(currentMenuHighlightBounds)
                        ? R.id.tooltip_right
                        : R.id.tooltip_left;
              } else {
                toolTipToShowViewId =
                    placeMenuOverlayAboveOrBelowBounds(currentMenuHighlightBounds, false)
                        ? R.id.tooltip_down
                        : R.id.tooltip_up;
              }
              showToolTip(toolTipToShowViewId, currentMenuHighlightBounds, menuLayout);
              menuLayout.setMenuContentView(menuOverlay.findViewById(R.id.menu_content));
              fillRemainingSpaceForMenuOverlay();
            });
  }

  /*
   * Checks if the given bounds are too close to the left or right edge of the screen for the
   * up or down tooltips to fit nicely.
   *
   * @param menuHorizontalPadding The horizontal padding between the menu edge and the screen edge
   * @return {@code true} if the given item is too close to the screen edge for the tooltip to fit
   */
  private boolean shouldDrawHorizontalToolTip() {
    Point screenSize = ScreenUtils.getRealScreenSize(getContext());
    int distanceFromItemEdgeToScreenEdge =
        (currentMenuHighlightBounds.centerX() < (screenSize.x / 2))
            ? currentMenuHighlightBounds.right
            : (screenSize.x - currentMenuHighlightBounds.left);
    return distanceFromItemEdgeToScreenEdge < minDistanceFromToolTipToScreenEdge;
  }

  /*
   * Draws the cancel button and the menu buttons for each menu item between firstMenuItemIndex and
   * lastMenuItemIndex. Clears all overlays if the range of menu items is invalid.
   */
  private boolean drawNewMenuButtons() {
    if (firstMenuItemIndex < 0 || menuItems.size() <= firstMenuItemIndex) {
      clearAllOverlays();
      return false;
    }

    // Adds the new menu items into a newly drawn menu.
    FlexboxLayout menuButtonsLayout = (FlexboxLayout) menuOverlay.findViewById(R.id.menu_buttons);
    menuButtonsLayout.removeAllViews();
    for (int i = firstMenuItemIndex; i < lastMenuItemIndex; i++) {
      MenuItem menuItem = menuItems.get(i);
      MenuButton button = new MenuButton(getContext());
      setIconTextAndOnClickListenerForMenuButton(button, menuItem);
      menuButtonsLayout.addView(button);
    }

    // Cancel button. Always visible.
    setIconTextAndOnClickListenerForCancelButton();
    return true;
  }

  /*
   * Set the icon resource, text, and onClickListener for the given {@code menuButton} using the
   * given {@code menuItem}.
   */
  private void setIconTextAndOnClickListenerForMenuButton(
      MenuButton menuButton, MenuItem menuItem) {
    OnClickListener onClickListener =
        ((menuItem instanceof GroupedMenuItem) || (menuItem instanceof VolumeAdjustmentMenuItem))
            // This menu item will lead to more menu items or is associated with a repeated action,
            // so get the OnClickListener without clearing the overlay.
            ? menuItem.getOnClickListener()
            : getOnClickListenerForMenuItem(menuItem.getOnClickListener());
    menuButton.setIconTextAndOnClickListener(
        menuItem.getIconResource(), menuItem.getText(), onClickListener);
  }

  private void setIconTextAndOnClickListenerForCancelButton() {
    MenuButton cancelButton = (MenuButton) menuOverlay.findViewById(R.id.cancel_button);
    int cancelButtonStringId = R.string.switch_access_close_menu;
    cancelButton.setIconTextAndOnClickListener(
        R.drawable.ic_cancel, cancelButtonStringId, getOnClickListenerForCancelAction());
  }

  /**
   * Draw the global menu on the screen. Some menu items (e.g. Enable/Disable auto select) will
   * depend on the current configuration.
   */
  private void drawGlobalMenu() {
    List<MenuItem> globalMenuItems =
        GlobalMenuItem.getGlobalMenuItemList(
            (AccessibilityService) getContext(), selectMenuItemListener);
    Rect rect = getMenuButtonLocation();
    // The global menu button is not showing, so don't draw the menu
    if (rect == null) {
      return;
    }
    drawMenu(globalMenuItems, rect);
    if (screenViewListener != null) {
      screenViewListener.onScreenShown(SCREEN_NAME_GLOBAL_MENU);
    }
  }

  private View.OnClickListener getOnClickListenerForMenuItem(
      final View.OnClickListener actionResult) {
    return view -> {
      clearMenuOverlay();
      clearHighlightOverlay();
      // Don't clear the menu button overlay here, so we don't remove it in case the user
      // just selected that area during point scanning.
      if (actionResult != null) {
        actionResult.onClick(view);
      }
    };
  }

  private View.OnClickListener getOnClickListenerForCancelAction() {
    return view -> {
      clearAllOverlays();

      if (selectMenuItemListener != null) {
        selectMenuItemListener.onMenuItemSelected(
            SwitchAccessMenuItemEnum.MenuItem.MENU_BUTTON_CANCEL);
      }
    };
  }

  private View.OnClickListener getOnClickListenerForBackMenuAction() {
    return view -> {
      // Updates the currently displayed menu items to be the parent menu. If there are no more
      // layers, the back button and header are hidden. Otherwise, the header text is updated.
      if ((parentMenuItems != null) && !parentMenuItems.empty()) {
        GroupedMenuItem parentMenu = parentMenuItems.pop();
        currentMenuGrouping = parentMenu;
        useDynamicLayout(parentMenu.shouldPopulateLayoutDynamically());
        updateMenuContent(parentMenu.getSubMenuItems());
        // An extra null-check here is necessary for nullness checking, though parentMenuItems
        // should never be null here.
        if (parentMenuItems == null || parentMenuItems.empty()) {
          firstMenuItemIndex = 0;
          lastMenuItemIndex = menuItems.size();
          hideSubMenuBackButtonAndHeader();
          updateMenuToFillAvailableSpace();
          parentMenuItems = null;
        } else {
          showSubMenuBackButtonAndHeader(parentMenu.getHeader());
        }
      }

      if (selectMenuItemListener != null) {
        selectMenuItemListener.onMenuItemSelected(
            SwitchAccessMenuItemEnum.MenuItem.MENU_BUTTON_BACK);
      }
    };
  }

  /**
   * Show the back button and header, and set the text of the submenu header.
   *
   * <p>The back button and header should only be shown from inside a submenu.
   *
   * @param subMenuHeader Header to be displayed at the top of a submenu
   */
  private void showSubMenuBackButtonAndHeader(GroupedMenuItemHeader subMenuHeader) {
    MenuButton backButton = (MenuButton) menuOverlay.findViewById(R.id.back_button);
    View headerView = menuOverlay.findViewById(R.id.submenu_header);
      int backButtonStringId = R.string.switch_access_back_menu;
      backButton.setIconTextAndOnClickListener(
          R.drawable.quantum_ic_arrow_back_white_24,
          backButtonStringId,
          getOnClickListenerForBackMenuAction());
    headerView.setVisibility(View.VISIBLE);
    TextView headerTextView = (TextView) menuOverlay.findViewById(R.id.submenu_header_text_view);
    headerTextView.setText(subMenuHeader.getHeaderText());

    // Update any menu buttons placed in the header.
      MenuButton startHeaderButton =
          (MenuButton) menuOverlay.findViewById(R.id.start_header_button);
      MenuItem headerMenuItem = subMenuHeader.getHeaderMenuItem();
      if (headerMenuItem != null) {
        setIconTextAndOnClickListenerForMenuButton(startHeaderButton, headerMenuItem);
        if (headerMenuItem instanceof VolumeAdjustmentMenuItem) {
          // Allow header menu button to be toggled when there is a volume change.
          VolumeAdjustmentMenuItem.addVolumeChangeListener(this);
          return;
        }
      } else {
        startHeaderButton.clearButton();
      }
    VolumeAdjustmentMenuItem.removeVolumeChangeListener(this);
  }

  /**
   * Hide the back button and header.
   *
   * <p>The back button and header should be hidden when not inside a submenu.
   */
  private void hideSubMenuBackButtonAndHeader() {
    MenuButton backButton = (MenuButton) menuOverlay.findViewById(R.id.back_button);
    View headerView = menuOverlay.findViewById(R.id.submenu_header);
    backButton.setEnabled(false);
    headerView.setVisibility(View.GONE);
  }

  private void updateMenuToFillAvailableSpace() {
    // Draw and show the menu.
    if (drawNewMenuButtons()) {
      showMenuOverlay();

      menuOverlay
          .getRootView()
          .post(
              () -> {
                SwitchAccessMenuLayout menuLayout =
                    (SwitchAccessMenuLayout) menuOverlay.findViewById(R.id.menu_scrim);
                if (resizeMenuToFitOnScreen(currentMenuHighlightBounds)) {
                  drawNewMenuButtons();
                  showMenuOverlay();
                }
                moveMenuNextToItemAndPadMenuToGrid(menuLayout);
              });
    }
  }

  /** Obtain the context for drawing. */
  public Context getContext() {
    return highlightOverlay.getContext();
  }

  public void setScreenViewListener(@Nullable ScreenViewListener listener) {
    screenViewListener = listener;
  }

  public void setSelectMenuItemListener(@Nullable SelectMenuItemListener listener) {
    selectMenuItemListener = listener;
  }

  // List#add expects a fully initialized object, but we need to be able to add listeners during
  // their construction.
  @SuppressWarnings("initialization:argument.type.incompatible")
  public void addMenuListener(@UnknownInitialization MenuListener listener) {
    menuListeners.add(listener);
  }

  /**
   * Sets the global menu button listener.
   *
   * @param listener The listener to receive information on when the Switch Access global menu
   *     button is shown.
   */
  public void setGlobalMenuButtonListener(GlobalMenuButtonListener listener) {
    globalMenuButtonListener = listener;
  }

  private void configureOverlayBeforeShow(SimpleOverlay overlay) {
    /* The overlay covers the entire screen. However, there is a left, top, right, and
     * bottom margin.  */
    final WindowManager.LayoutParams params = overlay.getParams();
    final Point size = ScreenUtils.getRealScreenSize(getContext());
    params.height = size.y;
    params.width = size.x;

    overlay.setParams(params);
  }

  /*
   * For some reason, it's very difficult to create a layout that covers exactly the entire screen
   * and doesn't move when an unhandled key is pressed. The configuration we're using seems to
   * result in a layout that starts above the screen. So we split initialization into two
   * pieces, and here we find out where the overlay ended up and move it to be at the top
   * of the screen.
   */
  private void configureOverlayAfterShow(SimpleOverlay overlay) {
    int[] location = new int[2];
    relativeLayout.getLocationOnScreen(location);
    WindowManager.LayoutParams layoutParams = overlay.getParams();
    // Use both x and y coordinates to support both landscape and portrait orientations.
    layoutParams.x -= location[0];
    layoutParams.y -= location[1];
    overlay.setParams(layoutParams);
  }

  @Override
  public void onAudioStreamVolumeChanged(int volumeStreamType) {
    // Because volume has changed, update the text and icon of any menu buttons in the header,
    // as these may have changed (i.e. the mute / unmute button).
    if ((currentMenuGrouping != null) && (currentMenuGrouping.getHeader() != null)) {
      GroupedMenuItemHeader subMenuHeader = currentMenuGrouping.getHeader();
      MenuButton headerMenuButton = (MenuButton) menuOverlay.findViewById(R.id.start_header_button);
      MenuItem headerMenuItem = subMenuHeader.getHeaderMenuItem();
      // Toggle the header menu item with the updated text and icon from the menu button.
      if (headerMenuItem != null) {
        setIconTextAndOnClickListenerForMenuButton(headerMenuButton, headerMenuItem);
      } else {
        headerMenuButton.clearButton();
      }
    }
  }

  @Override
  public void onRequestDoNotDisturbPermission() {
    Intent intent = new Intent(getContext(), DialogActivity.class);
    intent.setAction(DialogActivity.ACTION_REQUEST_DO_NOT_DISTURB_PERMSISSION);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    getContext().getApplicationContext().startActivity(intent);

    // Due to how the menu overlay is configured, any dialogs shown will be displayed behind the
    // Switch Access menu. To workaround this, hide the menu while the dialog is shown. The menu's
    // current state will be restored after the dialog has been dismissed.
    menuOverlay.hide();
  }

  /** Interface that is notified when a Switch Access Menu is shown and closed. */
  public interface MenuListener {

    /**
     * Called when a Switch Access Menu is shown.
     *
     * @param type The type of the menu shown
     * @param menuId The Id of the menu shown. We assume that only one menu can be opened at a time.
     *     Therefore, the menu events which don't have {@link MenuListener#onMenuClosed} called with
     *     the same menu Id will be dropped in logging
     */
    void onMenuShown(MenuType type, int menuId);

    /**
     * Called when a Switch Access Menu is closed.
     *
     * @param menuId The Id of the menu closed. This Id must match the last Id of the menu that was
     *     opened in order for the current menu event to be logged
     */
    void onMenuClosed(int menuId);
  }
  /** Interface that is notified when the Switch Access global menu button is first shown. */
  public interface GlobalMenuButtonListener {
    /** Called when the Switch Access global menu is shown. */
    void onGlobalMenuButtonShown();
  }
}

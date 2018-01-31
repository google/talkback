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

package com.google.android.accessibility.switchaccess;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RelativeLayout;
import com.google.android.accessibility.utils.widget.SimpleOverlay;
import java.util.ArrayList;
import java.util.List;

/**
 * Controller for the Switch Access overlay. The controller handles two operations: it outlines
 * groups of Views, and it presents context menus (with Views that are outlined).
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
public class OverlayController {
  private static final List<Integer> MENU_BUTTON_IDS = new ArrayList<>();

  static {
    MENU_BUTTON_IDS.add(R.id.button_1);
    MENU_BUTTON_IDS.add(R.id.button_2);
    MENU_BUTTON_IDS.add(R.id.button_3);
    MENU_BUTTON_IDS.add(R.id.button_4);
    MENU_BUTTON_IDS.add(R.id.button_5);
    MENU_BUTTON_IDS.add(R.id.button_6);
    MENU_BUTTON_IDS.add(R.id.button_7);
    MENU_BUTTON_IDS.add(R.id.button_8);
  }

  private static final String SCREEN_NAME_GLOBAL_MENU = "Global menu";

  private final SimpleOverlay mHighlightOverlay;
  private final SimpleOverlay mMenuOverlay;
  private final SimpleOverlay mGlobalMenuButtonOverlay;

  private List<MenuItem> mMenuItems;
  private int mFirstMenuItemIndex;

  private ScreenViewListener mScreenViewListener;

  private final RelativeLayout mRelativeLayout;

  private final BroadcastReceiver mBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
            configureOverlays();
          }
        }
      };

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
      SimpleOverlay menuOverlay) {
    // Create the highlight overlay as a full-screen SimpleOverlay that does not send
    // AccessibilityEvents or accept touch.
    mHighlightOverlay = highlightOverlay;
    WindowManager.LayoutParams params = mHighlightOverlay.getParams();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    } else {
      params.type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
    }
    params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    params.x = 0;
    params.y = 0;
    mHighlightOverlay.setParams(params);
    mHighlightOverlay.setContentView(R.layout.switch_access_overlay_layout);
    mRelativeLayout = (RelativeLayout) mHighlightOverlay.findViewById(R.id.overlayRelativeLayout);

    // Create the menu overlay as a full-screen SimpleOverlay that sends AccessibilityEvents.
    mMenuOverlay = menuOverlay;
    params = mMenuOverlay.getParams();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    } else {
      params.type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
    }
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    mMenuOverlay.setParams(params);

    // Create the menu button as a SimpleOverlay that sends AccessibilityEvents. Create it as a
    // separate overlay from the menu overlay as the menu overlay intercepts all touches and
    // the menu button only intercepts touches to the menu button area.
    mGlobalMenuButtonOverlay = globalMenuButtonOverlay;
    params = mGlobalMenuButtonOverlay.getParams();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
      params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    } else {
      params.type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
    }
    params.format = PixelFormat.TRANSPARENT;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    params.flags |= WindowManager.LayoutParams.FLAG_SPLIT_TOUCH;
    params.y = 0;
    params.width = WindowManager.LayoutParams.WRAP_CONTENT;
    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
    params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
    mGlobalMenuButtonOverlay.setParams(params);
    mGlobalMenuButtonOverlay.setContentView(R.layout.switch_access_global_menu_button);

    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
    mHighlightOverlay.getContext().registerReceiver(mBroadcastReceiver, filter);
  }

  /**
   * Get the highlight and menu overlays ready to show. This method displays the empty overlays and
   * tweaks them to make sure they are in the right location.
   */
  public void configureOverlays() {
    // Make sure we have the menu layout for the correct orientation.
    mMenuOverlay.setContentView(R.layout.switch_access_context_menu_layout);

    updateMenuItems();

    configureOverlayBeforeShow(mHighlightOverlay);
    configureOverlayBeforeShow(mMenuOverlay);
    mHighlightOverlay.show();
    new Handler()
        .post(
            new Runnable() {
              @Override
              public void run() {
                configureOverlayAfterShow(mHighlightOverlay);
                configureOverlayAfterShow(mMenuOverlay);
              }
            });
  }

  /** Override focus highlighting with a custom overlay */
  public void addViewAndShow(View view) {
    mRelativeLayout.addView(view);
    mHighlightOverlay.show();
  }

  /** Clear all overlays. */
  public void clearAllOverlays() {
    clearMenuOverlay();
    clearHighlightOverlay();
    clearMenuButtonOverlay();
  }

  /** Clear highlighting overlay. */
  public void clearHighlightOverlay() {
    mRelativeLayout.removeAllViews();
    mHighlightOverlay.hide();
  }

  /** Clear the menu button overlay. */
  public void clearMenuButtonOverlay() {
    mGlobalMenuButtonOverlay.hide();
  }

  /** Clear menu overlay. */
  public void clearMenuOverlay() {
    mMenuOverlay.hide();
    mMenuItems = null;
  }

  /** Is one of the menus visible? */
  public boolean isMenuVisible() {
    return mMenuOverlay.isVisible();
  }

  /** Is the highlight overlay visible? */
  public boolean isHighlightOverlayVisible() {
    return mHighlightOverlay.isVisible();
  }

  /** Shut down nicely. */
  public void shutdown() {
    mHighlightOverlay.getContext().unregisterReceiver(mBroadcastReceiver);
    clearAllOverlays();
  }

  /**
   * A menu button is drawn at the top of the screen if a menu is not currently visible. This button
   * offers the user the possibility of clearing the focus or choosing global actions (i.e Home,
   * Back, Notifications, etc). This menu button should be displayed if the user is using option
   * scanning or point scanning.
   */
  public void drawMenuButtonIfMenuNotVisible() {
    if (!isMenuVisible()) {
      Button menuButton = (Button) mGlobalMenuButtonOverlay.findViewById(R.id.global_menu_button);
      if (!menuButton.hasOnClickListeners()) {
        menuButton.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View view) {
                clearMenuButtonOverlay();
                drawGlobalMenu();
              }
            });
      }
      mGlobalMenuButtonOverlay.show();
    }
  }

  /**
   * @return If the menu button is visible, gets the location of the menu button at the top of the
   *     screen. Otherwise null is returned.
   */
  public Rect getMenuButtonLocation() {
    Button menuButton = (Button) mGlobalMenuButtonOverlay.findViewById(R.id.global_menu_button);
    if (!mMenuOverlay.isVisible() && menuButton != null) {
      Rect buttonLocation = new Rect();
      menuButton.getGlobalVisibleRect(buttonLocation);
      int[] overlayLocation = new int[2];
      menuButton.getLocationOnScreen(overlayLocation);
      buttonLocation.left += overlayLocation[0];
      buttonLocation.right += overlayLocation[0];
      return buttonLocation;
    }
    return null;
  }

  /**
   * @return If the cancel button is visible, gets the location of the cancel button in the menu
   *     overlay. Otherwise null is returned.
   */
  public Rect getCancelButtonLocation() {
    MenuButton cancelButton = (MenuButton) mMenuOverlay.findViewById(R.id.cancel_button);
    if (mMenuOverlay.isVisible() && cancelButton != null && cancelButton.isEnabled()) {
      Rect buttonLocation = new Rect();
      cancelButton.getGlobalVisibleRect(buttonLocation);
      int[] overlayLocation = new int[2];
      cancelButton.getLocationOnScreen(overlayLocation);
      buttonLocation.right += overlayLocation[0] - buttonLocation.left;
      buttonLocation.left += overlayLocation[0] - buttonLocation.left;
      return buttonLocation;
    }
    return null;
  }

  /** Draw a menu based on the provided menu items. */
  public void drawMenu(List<MenuItem> menuItems) {
    if (menuItems == null || menuItems.isEmpty()) {
      // Don't do anything if we have no items.
      return;
    }

    // Store data about the new menu.
    mMenuItems = menuItems;
    mFirstMenuItemIndex = 0;

    updateMenuItems();
    showMenuOverlay();
  }

  /** Move to the next set of menu items. If no more items are present, removes the menu. */
  public void moveToNextMenuItemsOrClearOverlays() {
    mFirstMenuItemIndex = mFirstMenuItemIndex + MENU_BUTTON_IDS.size();
    if (updateMenuItems()) {
      showMenuOverlay();
    }
  }

  /**
   * Move to the previous menu items. If this is the first page of the menu, removes the menu.
   *
   * @return {@code true} if there was a previous menu page
   */
  public boolean moveToPreviousMenuItemsOrClearOverlays() {
    mFirstMenuItemIndex = mFirstMenuItemIndex - MENU_BUTTON_IDS.size();
    if (updateMenuItems()) {
      showMenuOverlay();
      return true;
    }
    return false;
  }

  private void showMenuOverlay() {
    mMenuOverlay.show();
    if (mHighlightOverlay.isVisible()) {
      // Make sure the highlight overlay is on top.
      mHighlightOverlay.hide();
      mHighlightOverlay.show();
    }
  }

  /**
   * Updates the menu if possible. Removes the menu otherwise.
   *
   * @return {@code true} if the menu was updated, {@code false} if the menu was removed
   */
  private boolean updateMenuItems() {
    if (mMenuItems == null || mFirstMenuItemIndex < 0 || mMenuItems.size() <= mFirstMenuItemIndex) {
      clearAllOverlays();
      return false;
    }

    // Display the menu items.
    int numMenuItemsToEnd = mMenuItems.size() - mFirstMenuItemIndex;
    int numActiveButtons = Math.min(numMenuItemsToEnd, MENU_BUTTON_IDS.size());
    for (int i = 0; i < numActiveButtons; i++) {
      MenuItem menuItem = mMenuItems.get(i + mFirstMenuItemIndex);
      MenuButton button = (MenuButton) mMenuOverlay.findViewById(MENU_BUTTON_IDS.get(i));
      button.setIconTextAndOnClickListener(
          menuItem.getIconResource(),
          menuItem.getText(),
          getOnClickListenerForMenuItem(menuItem.getOnClickListener()));
    }

    // If we have more buttons than menu items, clear the remaining buttons.
    for (int i = numActiveButtons; i < MENU_BUTTON_IDS.size(); i++) {
      ((MenuButton) mMenuOverlay.findViewById(MENU_BUTTON_IDS.get(i))).clearButton();
    }

    // More button. Only visible if all items don't fit on one screen.
    MenuButton moreButton = (MenuButton) mMenuOverlay.findViewById(R.id.more_button);
    if (numMenuItemsToEnd > MENU_BUTTON_IDS.size()) {
      moreButton.setIconTextAndOnClickListener(
          R.drawable.ic_more, R.string.more, getOnClickListenerForMoreAction());
    } else {
      moreButton.clearButton();
    }

    // Cancel button. Always visible.
    MenuButton cancelButton = (MenuButton) mMenuOverlay.findViewById(R.id.cancel_button);
    cancelButton.setIconTextAndOnClickListener(
        R.drawable.ic_cancel, android.R.string.cancel, getOnClickListenerForCancelAction());

    return true;
  }

  /**
   * Draw the global menu on the screen. Some menu items (e.g. Enable/Disable auto select) will
   * depend on the current configuration.
   */
  private void drawGlobalMenu() {
    drawMenu(GlobalMenuItem.getGlobalMenuItemList((AccessibilityService) getContext()));
    if (mScreenViewListener != null) {
      mScreenViewListener.onScreenShown(SCREEN_NAME_GLOBAL_MENU);
    }
  }

  private View.OnClickListener getOnClickListenerForMenuItem(
      final View.OnClickListener actionResult) {
    return new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        clearMenuOverlay();
        clearHighlightOverlay();
        // Don't clear the menu button overlay here, so we don't remove it in case the user
        // just selected that area during point scanning.
        if (actionResult != null) {
          actionResult.onClick(view);
        }
      }
    };
  }

  private View.OnClickListener getOnClickListenerForCancelAction() {
    return new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        clearAllOverlays();
      }
    };
  }

  private View.OnClickListener getOnClickListenerForMoreAction() {
    return new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        moveToNextMenuItemsOrClearOverlays();
      }
    };
  }

  /** Obtain the context for drawing. */
  public Context getContext() {
    return mHighlightOverlay.getContext();
  }

  public void setScreenViewListener(ScreenViewListener listener) {
    mScreenViewListener = listener;
  }

  private void configureOverlayBeforeShow(SimpleOverlay overlay) {
    /* The overlay covers the entire screen. However, there is a left, top, right, and
     * bottom margin.  */
    final WindowManager.LayoutParams params = overlay.getParams();
    final WindowManager wm =
        (WindowManager) overlay.getContext().getSystemService(Context.WINDOW_SERVICE);
    final Point size = new Point();
    wm.getDefaultDisplay().getRealSize(size);
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
   * TODO Separating the menu and highlighting should be a cleaner way to solve this
   * issue
   */
  private void configureOverlayAfterShow(SimpleOverlay overlay) {
    int[] location = new int[2];
    mRelativeLayout.getLocationOnScreen(location);
    WindowManager.LayoutParams layoutParams = overlay.getParams();
    layoutParams.y -= location[1];
    overlay.setParams(layoutParams);
  }
}

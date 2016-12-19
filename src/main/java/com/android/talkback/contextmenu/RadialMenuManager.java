/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.talkback.contextmenu;

import com.android.talkback.FeedbackItem;
import com.android.talkback.R;

import android.os.Build;
import android.os.Handler;
import android.util.SparseArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.WindowManager;
import com.android.talkback.SpeechController;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.talkback.controller.FeedbackController;
import com.android.utils.widget.SimpleOverlay;

public class RadialMenuManager implements MenuManager {
    /** Delay in milliseconds before speaking the radial menu usage hint. */
    /*package*/ static final int DELAY_RADIAL_MENU_HINT = 2000;

    /** The scales used to represent menus of various sizes. */
    private static final int[] SCALES = {R.raw.radial_menu_1, R.raw.radial_menu_2,
            R.raw.radial_menu_3, R.raw.radial_menu_4, R.raw.radial_menu_5, R.raw.radial_menu_6,
            R.raw.radial_menu_7, R.raw.radial_menu_8};

    /** Cached radial menus. */
    private final SparseArray<RadialMenuOverlay> mCachedRadialMenus = new SparseArray<>();

    private final TalkBackService mService;
    private final SpeechController mSpeechController;
    private final FeedbackController mFeedbackController;

    /** Client that responds to menu item selection and click. */
    private RadialMenuClient mClient;

    /** How many radial menus are showing. */
    private int mIsRadialMenuShowing;

    /** Whether we have queued hint speech and it has not completed yet. */
    private boolean mHintSpeechPending;

    private final boolean mIsTouchScreen;

    private MenuTransformer mMenuTransformer;
    private MenuActionInterceptor mMenuActionInterceptor;

    public RadialMenuManager(boolean isTouchScreen,
                             TalkBackService context) {
        mIsTouchScreen = isTouchScreen;
        mService = context;
        mSpeechController = context.getSpeechController();
        mFeedbackController = context.getFeedbackController();
        mClient = new TalkBackRadialMenuClient(mService);
    }

    /**
     * Shows the specified menu resource as a radial menu.
     *
     * @param menuId The identifier of the menu to display.
     * @return {@code true} if the menu could be shown.
     */
    @Override
    public boolean showMenu(int menuId) {
        if (!mIsTouchScreen) return false;

        RadialMenuOverlay overlay = mCachedRadialMenus.get(menuId);

        if (overlay == null) {
            overlay = new RadialMenuOverlay(mService, menuId, false);
            overlay.setListener(mOverlayListener);

            final WindowManager.LayoutParams params = overlay.getParams();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            } else {
                params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
            }
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            overlay.setParams(params);

            final RadialMenu menu = overlay.getMenu();
            menu.setDefaultSelectionListener(mOnSelection);
            menu.setDefaultListener(mOnClick);

            final RadialMenuView view = overlay.getView();
            view.setSubMenuMode(RadialMenuView.SubMenuMode.LIFT_TO_ACTIVATE);

            if (mClient != null) {
                mClient.onCreateRadialMenu(menuId, menu);
            }

            mCachedRadialMenus.put(menuId, overlay);
        }

        if ((mClient != null) && !mClient.onPrepareRadialMenu(menuId, overlay.getMenu())) {
            mFeedbackController.playAuditory(R.raw.complete);
            return false;
        }

        if (mMenuTransformer != null) {
            mMenuTransformer.transformMenu(overlay.getMenu(), menuId);
        }

        overlay.showWithDot();
        return true;
    }

    @Override
    public boolean isMenuShowing() {
        return (mIsRadialMenuShowing > 0);
    }

    @Override
    public void onGesture(int gestureId) {
        dismissAll();
    }

    @Override
    public void setMenuTransformer(MenuTransformer transformer) {
        mMenuTransformer = transformer;
    }

    @Override
    public void setMenuActionInterceptor(MenuActionInterceptor actionInterceptor) {
        mMenuActionInterceptor = actionInterceptor;
    }

    @Override
    public void dismissAll() {
        for (int i = 0; i < mCachedRadialMenus.size(); ++i) {
            final RadialMenuOverlay menu = mCachedRadialMenus.valueAt(i);

            if (menu.isVisible()) {
                menu.dismiss();
            }
        }
    }

    public void clearCache() {
        mCachedRadialMenus.clear();
    }

    /**
     * Plays an F# harmonic minor scale with a number of notes equal to the number of items in the
     * specified menu, up to 8 notes.
     *
     * @param menu to play scale for
     */
    private void playScaleForMenu(Menu menu) {
        final int size = menu.size();
        if (size <= 0) {
            return;
        }

        mFeedbackController.playAuditory(SCALES[Math.min(size - 1, 7)]);
    }

    /**
     * Handles selecting menu items.
     */
    private final RadialMenuItem.OnMenuItemSelectionListener mOnSelection =
            new RadialMenuItem.OnMenuItemSelectionListener() {
        @Override
        public boolean onMenuItemSelection(RadialMenuItem menuItem) {
            mHandler.removeCallbacks(mRadialMenuHint);

            mFeedbackController.playHaptic(R.array.view_actionable_pattern);
            mFeedbackController.playAuditory(R.raw.focus_actionable);

            final boolean handled = (mClient != null) && mClient.onMenuItemHovered();

            if (!handled) {
                final CharSequence text;
                if (menuItem == null) {
                    text = mService.getString(android.R.string.cancel);
                } else if (menuItem.hasSubMenu()) {
                    text = mService.getString(R.string.template_menu, menuItem.getTitle());
                } else {
                    text = menuItem.getTitle();
                }

                mSpeechController.speak(text, SpeechController.QUEUE_MODE_INTERRUPT,
                        FeedbackItem.FLAG_NO_HISTORY | FeedbackItem.FLAG_DURING_RECO, null);
            }

            return true;
        }
    };

    /**
     * Handles clicking on menu items.
     */
    private final OnMenuItemClickListener mOnClick = new OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem menuItem) {
            mHandler.removeCallbacks(mRadialMenuHint);

            mFeedbackController.playHaptic(R.array.view_clicked_pattern);
            mFeedbackController.playAuditory(R.raw.tick);

            boolean handled = mMenuActionInterceptor != null &&
                    mMenuActionInterceptor.onInterceptMenuClick(menuItem);

            if (!handled) {
                handled = mClient != null && mClient.onMenuItemClicked(menuItem);
            }

            if (!handled && (menuItem == null)) {
                mService.interruptAllFeedback();
            }

            if ((menuItem != null) && menuItem.hasSubMenu()) {
                playScaleForMenu(menuItem.getSubMenu());
            }

            return true;
        }
    };

    /**
     * Handles feedback from showing and hiding radial menus.
     */
    private final SimpleOverlay.SimpleOverlayListener mOverlayListener =
            new SimpleOverlay.SimpleOverlayListener() {
        @Override
        public void onShow(SimpleOverlay overlay) {
            final RadialMenu menu = ((RadialMenuOverlay) overlay).getMenu();

            mHandler.postDelayed(mRadialMenuHint, DELAY_RADIAL_MENU_HINT);

            // TODO: Find an alternative or just speak the number of items.
            // Play a note in a C major scale for each item in the menu.
            playScaleForMenu(menu);

            mIsRadialMenuShowing++;
        }

        @Override
        public void onHide(SimpleOverlay overlay) {
            mHandler.removeCallbacks(mRadialMenuHint);

            if (mHintSpeechPending) {
                mSpeechController.interrupt();
            }

            mIsRadialMenuShowing--;
        }
    };

    /**
     * Runnable that speaks a usage hint for the radial menu.
     */
    private final Runnable mRadialMenuHint = new Runnable() {
        @Override
        public void run() {
            final String hintText = mService.getString(R.string.hint_radial_menu);

            mHintSpeechPending = true;
            mSpeechController.speak(hintText, null, null, SpeechController.QUEUE_MODE_QUEUE,
                    FeedbackItem.FLAG_NO_HISTORY | FeedbackItem.FLAG_DURING_RECO,
                    SpeechController.UTTERANCE_GROUP_DEFAULT, null, null, mHintSpeechCompleted);
        }
    };

    /**
     * Runnable that confirms the hint speech has completed.
     */
    private final SpeechController.UtteranceCompleteRunnable mHintSpeechCompleted =
            new SpeechController.UtteranceCompleteRunnable() {
        @Override
        public void run(int status) {
            mHintSpeechPending = false;
        }
    };

    private final Handler mHandler = new Handler();

    public interface RadialMenuClient {
        public void onCreateRadialMenu(int menuId, RadialMenu menu);
        public boolean onPrepareRadialMenu(int menuId, RadialMenu menu);
        public boolean onMenuItemHovered();
        public boolean onMenuItemClicked(MenuItem menuItem);
    }
}

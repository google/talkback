/*
 * Copyright 2015 Google Inc.
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


package com.android.talkback.controller;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.CheckBox;
import android.widget.ScrollView;
import com.android.talkback.DimmingOverlayView;
import com.android.talkback.OrientationMonitor;
import com.android.talkback.R;
import com.google.android.marvin.talkback.TalkBackService;
import com.android.utils.SharedPreferencesUtils;

import java.util.concurrent.TimeUnit;

public class DimScreenControllerApp implements DimScreenController,
        OrientationMonitor.OnOrientationChangedListener {

    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR2;
    public static final boolean IS_SUPPORTED_PLATFORM = Build.VERSION.SDK_INT >= MIN_API_LEVEL &&
            !TalkBackService.isInArc();

    private static final float MAX_DIM_AMOUNT = 0.9f;
    private static final float MIN_BRIGHTNESS = 0.1f;
    private static final float MAX_BRIGHTNESS = 1.0f;

    private static final int START_DIMMING_MESSAGE = 1;
    private static final int UPDATE_TIMER_MESSAGE = 2;

    private static final int INSTRUCTION_VISIBLE_SECONDS = 180;

    private SharedPreferences mPrefs;
    private Context mContext;
    private boolean mIsDimmed;
    private WindowManager mWindowManager;
    private LayoutParams mViewParams;
    private DimmingOverlayView mView;
    private Dialog mDimDialog;
    private int mCurrentInstructionVisibleTime;
    private boolean mIsInstructionDisplayed;
    private Handler mDimmingHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case START_DIMMING_MESSAGE:
                    mCurrentInstructionVisibleTime = INSTRUCTION_VISIBLE_SECONDS;
                    mIsInstructionDisplayed = true;
                    sendEmptyMessage(UPDATE_TIMER_MESSAGE);
                    break;
                case UPDATE_TIMER_MESSAGE:
                    mCurrentInstructionVisibleTime--;
                    if (mCurrentInstructionVisibleTime > 0) {
                        updateText(mCurrentInstructionVisibleTime);
                        sendEmptyMessageDelayed(UPDATE_TIMER_MESSAGE, TimeUnit.SECONDS.toMillis(1));
                    } else {
                        hideInstructionAndTurnOnDimming();
                    }
                    break;
            }
        }
    };

    public DimScreenControllerApp(Context context) {
        mContext = context;
        mPrefs = SharedPreferencesUtils.getSharedPreferences(context);
    }

    @Override
    public boolean isDimmingEnabled() {
        return SharedPreferencesUtils.getBooleanPref(
                mPrefs, mContext.getResources(),
                R.string.pref_dim_when_talkback_enabled_key,
                R.bool.pref_dim_when_talkback_enabled_default);
    }

    /**
     * Turn on screen dimming without setting the shared preference.
     */
    private void makeScreenDim() {
        if (mIsDimmed) {
            return;
        }

        mIsDimmed = !mIsDimmed;

        initView();
        addExitInstructionView();
        startDimmingCount();

        announceScreenDimChanged(R.string.screen_dimmed);
    }

    private void initView() {
        if (mViewParams == null || mView == null) {
            mWindowManager = (WindowManager) mContext.getSystemService(
                    Context.WINDOW_SERVICE);

            mViewParams = new LayoutParams();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                mViewParams.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
            } else {
                mViewParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
            }
            mViewParams.flags |= LayoutParams.FLAG_DIM_BEHIND;
            mViewParams.flags |= LayoutParams.FLAG_NOT_FOCUSABLE;
            mViewParams.flags |= LayoutParams.FLAG_NOT_TOUCHABLE;
            mViewParams.flags |= LayoutParams.FLAG_FULLSCREEN;
            mViewParams.flags &= ~LayoutParams.FLAG_TURN_SCREEN_ON;
            mViewParams.flags &= ~LayoutParams.FLAG_KEEP_SCREEN_ON;
            mViewParams.format = PixelFormat.OPAQUE;

            mView = new DimmingOverlayView(mContext);
            mView.setTimerLimit(INSTRUCTION_VISIBLE_SECONDS);
        }

        initCurtainSize();
    }

    private void initCurtainSize() {
        Point point = new Point();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mWindowManager.getDefaultDisplay().getRealSize(point);
        } else {
            mWindowManager.getDefaultDisplay().getSize(point);
        }

        mViewParams.width = point.x;
        mViewParams.height = point.y;
    }

    private void addExitInstructionView() {
        mViewParams.dimAmount = MAX_DIM_AMOUNT;
        mViewParams.screenBrightness = getDeviceBrightness();
        mViewParams.buttonBrightness = MIN_BRIGHTNESS;
        mWindowManager.addView(mView, mViewParams);
        mView.showText();
    }

    private float getDeviceBrightness() {
        try {
            return android.provider.Settings.System.getInt(
                    mContext.getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            return MAX_BRIGHTNESS;
        }
    }

    private void startDimmingCount() {
        mDimmingHandler.sendEmptyMessage(START_DIMMING_MESSAGE);
    }

    private void updateText(int secondsLeft) {
        mView.updateSecondsText(secondsLeft);
    }

    private void hideInstructionAndTurnOnDimming() {
        mViewParams.dimAmount = MAX_DIM_AMOUNT;
        mViewParams.screenBrightness = MIN_BRIGHTNESS;
        mViewParams.buttonBrightness = mViewParams.screenBrightness;
        mIsInstructionDisplayed = false;
        mView.hideText();
        updateView();
    }

    @Override
    public boolean isInstructionDisplayed() {
        return mIsInstructionDisplayed;
    }

    private void updateView() {
        if (mIsDimmed) {
            mWindowManager.removeViewImmediate(mView);
            mWindowManager.addView(mView, mViewParams);
        }
    }

    @Override
    public void resume() {
        if (isDimmingEnabled()) {
            makeScreenDim();
        }
    }

    @Override
    public void suspend() {
        makeScreenBright();
        if (mDimDialog != null && mDimDialog.isShowing()) {
            mDimDialog.cancel();
        }
    }

    @Override
    public void shutdown() {
        suspend();
        mViewParams = null;
        mView = null;
    }

    /**
     * Turns off screen dimming without setting the shared preference.
     */
    private void makeScreenBright() {
        if (!mIsDimmed) {
            return;
        }

        mIsDimmed = !mIsDimmed;
        mIsInstructionDisplayed = false;

        mWindowManager.removeViewImmediate(mView);
        announceScreenDimChanged(R.string.screen_brightness_restored);
        mDimmingHandler.removeMessages(START_DIMMING_MESSAGE);
        mDimmingHandler.removeMessages(UPDATE_TIMER_MESSAGE);
    }

    @Override
    public void disableDimming() {
        makeScreenBright();
        SharedPreferencesUtils.putBooleanPref(mPrefs, mContext.getResources(),
                R.string.pref_dim_when_talkback_enabled_key, false);
    }

    @Override
    public void showDimScreenDialog() {
        // Only show one dim screen dialog at a time.
        if (mDimDialog != null && mDimDialog.isShowing()) {
            return;
        }

        boolean showConfirmDialog = mPrefs.getBoolean(
                mContext.getString(R.string.pref_show_dim_screen_confirmation_dialog), true);
        if (!showConfirmDialog) {
            makeScreenDim();
            SharedPreferencesUtils.putBooleanPref(mPrefs, mContext.getResources(),
                    R.string.pref_dim_when_talkback_enabled_key, true);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(mContext);
        @SuppressLint("InflateParams") final ScrollView root = (ScrollView) inflater.inflate(
                R.layout.dim_screen_confirmation_dialog, null);
        final CheckBox confirmCheckBox = (CheckBox) root.findViewById(R.id.show_warning_checkbox);

        final DialogInterface.OnClickListener okayClick = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (!confirmCheckBox.isChecked()) {
                        SharedPreferencesUtils.putBooleanPref(mPrefs, mContext.getResources(),
                                R.string.pref_show_dim_screen_confirmation_dialog, false);
                    }

                    // TalkBack should be active here, but let's check just in case.
                    if (TalkBackService.isServiceActive()) {
                        makeScreenDim();
                        SharedPreferencesUtils.putBooleanPref(mPrefs, mContext.getResources(),
                                R.string.pref_dim_when_talkback_enabled_key, true);
                    }
                    mDimDialog = null;
                }
            }
        };

        mDimDialog = new AlertDialog.Builder(TalkBackService.getInstance())
                .setTitle(R.string.dialog_title_dim_screen)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, okayClick)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            mDimDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        } else {
            mDimDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
        }

        mDimDialog.show();
    }

    private void announceScreenDimChanged(int announcementTextResId) {
        AccessibilityManager manager = (AccessibilityManager) mContext
                .getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager.isEnabled()) {
            AccessibilityEvent event = AccessibilityEvent.obtain();
            event.setEventType(AccessibilityEvent.TYPE_ANNOUNCEMENT);
            event.setClassName(getClass().getName());
            event.setPackageName(mContext.getPackageName());
            event.getText().add(mContext.getString(announcementTextResId));
            manager.sendAccessibilityEvent(event);
        }
    }

    @Override
    public void onOrientationChanged(int newOrientation) {
        if (mIsDimmed) {
            initCurtainSize();
            mWindowManager.removeViewImmediate(mView);
            mView = new DimmingOverlayView(mContext);
            mView.setTimerLimit(INSTRUCTION_VISIBLE_SECONDS);
            if (mIsInstructionDisplayed) {
                mView.showText();
            } else {
                mView.hideText();
            }
            mWindowManager.addView(mView, mViewParams);
        }
    }
}

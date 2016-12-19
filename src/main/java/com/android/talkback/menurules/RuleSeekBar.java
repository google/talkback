/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.talkback.menurules;

import com.google.android.marvin.talkback.TalkBackService;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.os.BuildCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.android.talkback.R;
import com.android.talkback.contextmenu.ContextMenuItem;
import com.android.talkback.contextmenu.ContextMenuItemBuilder;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.PerformActionUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Provides a LCM item to manually enter a percentage value for seek controls.
 * This functionality is only available on Android N and later.
 */
public class RuleSeekBar implements NodeMenuRule {

    @Override
    public boolean accept(TalkBackService service, AccessibilityNodeInfoCompat node) {
        if (!BuildCompat.isAtLeastN()) {
            return false;
        }

        return AccessibilityNodeInfoUtils.supportsAction(node,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId());
    }

    @Override
    public List<ContextMenuItem> getMenuItemsForNode(TalkBackService service,
            ContextMenuItemBuilder menuItemBuilder, AccessibilityNodeInfoCompat node) {
        List<ContextMenuItem> items = new LinkedList<>();

        if (node != null) {
            final ContextMenuItem setLevel = menuItemBuilder.createMenuItem(service,
                    Menu.NONE, R.id.seekbar_breakout_set_level, Menu.NONE,
                    service.getString(R.string.title_seek_bar_edit));
            setLevel.setOnMenuItemClickListener(new SeekBarDialogManager(service, node));
            setLevel.setSkipRefocusEvents(true);
            items.add(setLevel);
        }


        return items;
    }

    @Override
    public CharSequence getUserFriendlyMenuName(Context context) {
        return context.getString(R.string.title_seek_bar_controls);
    }

    @Override
    public boolean canCollapseMenu() {
        return true;
    }

    private static int realToPercent(float real, float min, float max) {
        return (int) (100.0f * (real - min) / (max - min));
    }

    private static float percentToReal(int percent, float min, float max) {
        return min + (percent / 100.0f) * (max - min);
    }

    // Separate package-private method so we can test the logic.
    static void setProgress(AccessibilityNodeInfoCompat node, int progress) {
        RangeInfoCompat rangeInfo = node.getRangeInfo();
        if (rangeInfo != null && progress >= 0 && progress <= 100) {
            Bundle args = new Bundle();
            args.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,
                    percentToReal(progress, rangeInfo.getMin(),
                            rangeInfo.getMax()));
            PerformActionUtils.performAction(node,
                    AccessibilityAction.ACTION_SET_PROGRESS.getId(),
                    args);
        }
    }

    // Deals with opening the dialog from the menu item and controlling the dialog lifecycle.
    private static class SeekBarDialogManager
            implements MenuItem.OnMenuItemClickListener, OnDismissListener {
        private static final int INVALID_VALUE = -1;

        private final TalkBackService mService;
        private AccessibilityNodeInfoCompat mSeekBar; // Note: not final so we can null it out.
        private int mOldValue = INVALID_VALUE;
        private int mValue = INVALID_VALUE;

        private View mRootView;
        private AlertDialog mDialog;

        public SeekBarDialogManager(TalkBackService service,
                AccessibilityNodeInfoCompat seekBar) {
            mService = service;
            mSeekBar = AccessibilityNodeInfoCompat.obtain(seekBar);
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            // Verify that node is OK and get the current seek control level first.
            final RangeInfoCompat rangeInfo = mSeekBar.getRangeInfo();
            if (rangeInfo == null) {
                return false;
            }

            mOldValue = realToPercent(rangeInfo.getCurrent(), rangeInfo.getMin(),
                    rangeInfo.getMax());
            mService.saveFocusedNode();

            LayoutInflater inflater = LayoutInflater.from(mService);
            mRootView = inflater.inflate(R.layout.seekbar_level_dialog, null);

            final AlertDialog.Builder builder = new AlertDialog.Builder(mService)
                    .setView(mRootView)
                    .setTitle(mService.getString(R.string.title_seek_bar_edit))
                    .setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setCancelable(true)
                    .setOnDismissListener(this);

            mDialog = builder.create();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            } else {
                mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
            }
            mDialog.show();
            mService.getRingerModeAndScreenMonitor().registerDialog(mDialog);

            // We'd like to keep focus off of the text field until the user activates it.
            final Button okButton = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            okButton.setFocusableInTouchMode(true);
            okButton.requestFocus();

            // Fill in the text field and restore normal input focus behavior when it gets focus.
            final EditText percentage = (EditText) mRootView.findViewById(R.id.seek_bar_level);
            percentage.setText(Integer.toString(mOldValue));
            percentage.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        okButton.setFocusableInTouchMode(false);
                    }
                }
            });
            percentage.setOnEditorActionListener(new OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        submitDialog();
                        return true;
                    }
                    return false;
                }
            });

            // Use our own custom listener to prevent the dialog from closing if there's an error.
            okButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    submitDialog();
                }
            });

            return true;
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            if (mSeekBar == null) {
                return;
            }

            // This will only set the value if the user clicked "OK" because only "OK" will
            // change the mValue field to not be INVALID_VALUE.
            if (mValue != INVALID_VALUE && mValue != mOldValue) {
                setProgress(mSeekBar, mValue);
            }

            mService.resetFocusedNode();
            mService.getRingerModeAndScreenMonitor().unregisterDialog(dialog);
            mSeekBar.recycle();
            mSeekBar = null;
        }

        private void submitDialog() {
            if (mRootView == null || mDialog == null) {
                return;
            }

            final EditText percentage = (EditText) mRootView.findViewById(R.id.seek_bar_level);
            try {
                int percentValue = Integer.parseInt(percentage.getText().toString());
                if (percentValue < 0 || percentValue > 100) {
                    throw new IndexOutOfBoundsException();
                }

                // Need to delay setting value until the dialog is dismissed.
                mValue = percentValue;
                mDialog.dismiss();
            } catch (NumberFormatException | IndexOutOfBoundsException ex) {
                // Set the error text popup.
                CharSequence instructions = mService.getString(
                        R.string.value_seek_bar_dialog_instructions);
                percentage.setError(instructions);
            }
        }
    }

}

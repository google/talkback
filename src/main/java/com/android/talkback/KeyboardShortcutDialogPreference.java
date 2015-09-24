/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.talkback;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.preference.Preference;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.marvin.talkback.TalkBackService;

public class KeyboardShortcutDialogPreference extends DialogPreference
        implements DialogInterface.OnKeyListener {

    private TextView mKeyAssignmentView;
    private KeyComboManager mKeyComboManager;
    private long mTemporaryKeyComboCode;
    private TextView mInstructionText;

    private View.OnClickListener mClearButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mInstructionText.setTextColor(Color.BLACK);
            mTemporaryKeyComboCode = KeyComboManager.KEY_COMBO_CODE_UNASSIGNED;
            updateKeyAssignmentText();
        }
    };

    private View.OnClickListener mOkButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!mKeyComboManager.isEligibleKeyCombo(mTemporaryKeyComboCode)) {
                mInstructionText.setTextColor(Color.RED);
                announceText(mInstructionText.getText().toString());
                return;
            }

            String key = mKeyComboManager.getKeyForKeyComboCode(mTemporaryKeyComboCode);
            if (key == null) {
                saveKeyCode();
                notifyChanged();
            } else if (!key.equals(getKey())) {
                showOverrideKeyComboDialog(key);
                return;
            }

            Dialog dialog = getDialog();
            if (dialog != null) {
                dialog.dismiss();
            }
        }
    };

    public KeyboardShortcutDialogPreference(Context context, AttributeSet attrs, int defStyleAttr,
                                            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public KeyboardShortcutDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public KeyboardShortcutDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public KeyboardShortcutDialogPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setPersistent(true);
        setDialogLayoutResource(R.layout.keyboard_shortcut_dialog);
        if (TalkBackService.getInstance() != null) {
            mKeyComboManager = TalkBackService.getInstance().getKeyComboManager();
        } else {
            mKeyComboManager = new KeyComboManager(getContext());
        }
        mTemporaryKeyComboCode = mKeyComboManager.getKeyComboCodeForKey(getKey());
    }

    private void announceText(String text) {
        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT);
        event.setContentDescription(text);
        AccessibilityManager accessibilityManager =
                (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        accessibilityManager.sendAccessibilityEvent(event);
    }

    @Override
    public void notifyChanged() {
        super.notifyChanged();
    }

    @Override
    public CharSequence getSummary() {
        return mKeyComboManager.getKeyComboStringRepresentation(mTemporaryKeyComboCode);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        mTemporaryKeyComboCode = mKeyComboManager.getKeyComboCodeForKey(getKey());
        mKeyComboManager.setMatchKeyCombo(true);
    }

    @Override
    protected void onBindDialogView(@NonNull View view) {
        super.onBindDialogView(view);

        mTemporaryKeyComboCode = mKeyComboManager.getKeyComboCodeForKey(getKey());
        mKeyAssignmentView = (TextView) view.findViewById(R.id.assigned_combination);
        mInstructionText = (TextView) view.findViewById(R.id.instruction);
        updateKeyAssignmentText();

        mKeyComboManager.setMatchKeyCombo(false);
    }

    private void updateKeyAssignmentText() {
        mKeyAssignmentView.setText(getSummary());
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);
        AlertDialog alertDialog = (AlertDialog) getDialog();
        if (alertDialog == null) {
            return;
        }

        View clear = alertDialog.findViewById(R.id.clear);
        clear.setOnClickListener(mClearButtonClickListener);
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener(mOkButtonClickListener);
        alertDialog.setOnKeyListener(this);

        Button okButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        okButton.setFocusableInTouchMode(true);
        okButton.requestFocus();
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        if (!processKeyEvent(event)) {
            return false;
        }

        mTemporaryKeyComboCode = KeyComboManager.getKeyComboCode(event);
        updateKeyAssignmentText();

        return true;
    }

    private boolean processKeyEvent(KeyEvent event) {
        if (event == null) {
            return false;
        }

        if (event.getRepeatCount() > 1) {
            return false;
        }

        //noinspection SimplifiableIfStatement
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK ||
                event.getKeyCode() == KeyEvent.KEYCODE_HOME ||
                event.getKeyCode() == KeyEvent.KEYCODE_CALL ||
                event.getKeyCode() == KeyEvent.KEYCODE_ENDCALL) {
            return false;
        }

        return event.getAction() == KeyEvent.ACTION_DOWN;
    }

    private void showOverrideKeyComboDialog(final String key) {
        final Preference currentActionPreference = getPreferenceManager().findPreference(key);
        if (currentActionPreference == null) {
            return;
        }

        final Preference newActionPreference = getPreferenceManager().findPreference(getKey());
        if (newActionPreference == null) {
            return;
        }

        CharSequence currentAction = currentActionPreference.getTitle();
        CharSequence newAction = newActionPreference.getTitle();
        showOverrideKeyComboDialog(currentAction, newAction, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which != DialogInterface.BUTTON_POSITIVE) {
                    return;
                }

                saveKeyCode();
                mKeyComboManager.clearKeyCombo(key);
                notifyListener(key, mKeyComboManager.getKeyComboCodeForKey(key));
                Dialog mainDialog = getDialog();
                if (mainDialog != null) {
                    mainDialog.dismiss();
                }
            }
        });
    }

    private void saveKeyCode() {
        mKeyComboManager.saveKeyCombo(getKey(), mTemporaryKeyComboCode);
        notifyListener(getKey(), mTemporaryKeyComboCode);
    }

    public void setKeyComboCode(long keyComboCode) {
        mTemporaryKeyComboCode = keyComboCode;
    }

    private void notifyListener(String key, Object newValue) {
        Preference preference = getPreferenceManager().findPreference(key);
        if (preference == null || !(preference instanceof KeyboardShortcutDialogPreference)) {
            return;
        }

        OnPreferenceChangeListener listener = preference.getOnPreferenceChangeListener();
        if (listener != null) {
            listener.onPreferenceChange(preference, newValue);
        }
    }

    private void showOverrideKeyComboDialog(CharSequence currentAction, CharSequence newAction,
                                            final DialogInterface.OnClickListener clickListener) {
        String message = getContext().getString(R.string.override_keycombo_message_two_params,
                currentAction, newAction);
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.override_keycombo)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        clickListener.onClick(dialog, which);
                    }
                })
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        saveKeyCode();
                        clickListener.onClick(dialog, which);
                    }
                })
                .show();
    }
}

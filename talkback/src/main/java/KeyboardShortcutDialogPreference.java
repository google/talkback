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

package com.google.android.accessibility.talkback;

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
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.TextView;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.keyboard.KeyComboModel;

public class KeyboardShortcutDialogPreference extends DialogPreference
    implements DialogInterface.OnKeyListener,
        ServiceKeyEventListener,
        AccessibilityManager.AccessibilityStateChangeListener {

  private static final int KEY_EVENT_SOURCE_ACTIVITY = 0;
  private static final int KEY_EVENT_SOURCE_ACCESSIBILITY_SERVICE = 1;

  private TextView mKeyAssignmentView;
  private KeyComboManager mKeyComboManager;
  private TextView mInstructionText;
  private int mKeyEventSource = KEY_EVENT_SOURCE_ACTIVITY;
  private AccessibilityManager mAccessibilityManager;
  private int mTemporaryModifier;
  private int mTemporaryKeyCode;

  private View.OnClickListener mClearButtonClickListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          mInstructionText.setTextColor(Color.BLACK);
          clearTemporaryKeyComboCode();
          updateKeyAssignmentText();
        }
      };

  private View.OnClickListener mOkButtonClickListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          long temporaryKeyComboCode = getTemporaryKeyComboCodeWithoutTriggerModifier();
          if (temporaryKeyComboCode == KeyComboModel.KEY_COMBO_CODE_INVALID
              || !mKeyComboManager
                  .getKeyComboModel()
                  .isEligibleKeyComboCode(temporaryKeyComboCode)) {
            mInstructionText.setTextColor(Color.RED);
            TalkBackKeyboardShortcutPreferencesActivity.announceText(
                mInstructionText.getText().toString(), getContext());
            return;
          }

          String key =
              mKeyComboManager
                  .getKeyComboModel()
                  .getKeyForKeyComboCode(getTemporaryKeyComboCodeWithoutTriggerModifier());
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

  public KeyboardShortcutDialogPreference(
      Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
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
      mKeyComboManager = KeyComboManager.create(getContext());
    }

    if (mKeyComboManager == null) {
      throw new IllegalStateException(
          "KeyboardShortcutDialogPreference should never appear "
              + "on systems where KeyComboManager is unavailable");
    }

    setTemporaryKeyComboCodeWithoutTriggerModifier(
        mKeyComboManager.getKeyComboModel().getKeyComboCodeForKey(getKey()));

    mAccessibilityManager =
        (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
    mAccessibilityManager.addAccessibilityStateChangeListener(this);

    updateAvailability();
  }

  public void onTriggerModifierChanged() {
    setTemporaryKeyComboCodeWithoutTriggerModifier(
        mKeyComboManager.getKeyComboModel().getKeyComboCodeForKey(getKey()));

    // Update summary since it will be changed when trigger modifier is changed.
    setSummary(getSummary());
  }

  /** Clears current temporary key combo code. */
  private void clearTemporaryKeyComboCode() {
    mTemporaryModifier = KeyComboModel.NO_MODIFIER;
    mTemporaryKeyCode = KeyEvent.KEYCODE_UNKNOWN;
  }

  /**
   * Sets temporary key combo code with trigger modifier. You can set key combo code which doesn't
   * contain trigger modifier.
   */
  private void setTemporaryKeyComboCodeWithTriggerModifier(long keyComboCode) {
    mTemporaryModifier = KeyComboManager.getModifier(keyComboCode);
    mTemporaryKeyCode = KeyComboManager.getKeyCode(keyComboCode);
  }

  /** Sets temporary key combo code without trigger modifier. */
  private void setTemporaryKeyComboCodeWithoutTriggerModifier(long keyComboCode) {
    mTemporaryModifier = KeyComboManager.getModifier(keyComboCode);
    mTemporaryKeyCode = KeyComboManager.getKeyCode(keyComboCode);

    int triggerModifier = mKeyComboManager.getKeyComboModel().getTriggerModifier();
    if (keyComboCode != KeyComboModel.KEY_COMBO_CODE_UNASSIGNED
        && triggerModifier != KeyComboModel.NO_MODIFIER) {
      mTemporaryModifier = mTemporaryModifier | triggerModifier;
    }
  }

  /** Gets temporary key combo code with trigger modifier. */
  private long getTemporaryKeyComboCodeWithTriggerModifier() {
    return KeyComboManager.getKeyComboCode(mTemporaryModifier, mTemporaryKeyCode);
  }

  /**
   * Gets temporary key combo code without trigger modifier. If current temporary key combo code
   * doesn't contain trigger modifier, KEY_COMBO_CODE_INVALID will be returned.
   */
  private long getTemporaryKeyComboCodeWithoutTriggerModifier() {
    if (getTemporaryKeyComboCodeWithTriggerModifier() == KeyComboModel.KEY_COMBO_CODE_UNASSIGNED) {
      return KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
    }

    int triggerModifier = mKeyComboManager.getKeyComboModel().getTriggerModifier();

    if (triggerModifier != KeyComboModel.NO_MODIFIER
        && (mTemporaryModifier & triggerModifier) == 0) {
      return KeyComboModel.KEY_COMBO_CODE_INVALID;
    }

    int modifier = mTemporaryModifier & ~triggerModifier;
    return KeyComboManager.getKeyComboCode(modifier, mTemporaryKeyCode);
  }

  @Override
  public void onAccessibilityStateChanged(boolean enabled) {
    updateAvailability();
  }

  @Override
  protected void onPrepareForRemoval() {
    mAccessibilityManager.removeAccessibilityStateChangeListener(this);

    super.onPrepareForRemoval();
  }

  private void updateAvailability() {
    int keyEventSource = getKeyEventSourceForCurrentKeyComboModel();

    if (keyEventSource == KEY_EVENT_SOURCE_ACTIVITY) {
      setEnabled(true);
      return;
    } else {
      setEnabled(TalkBackService.isServiceActive());
    }
  }

  private int getKeyEventSourceForCurrentKeyComboModel() {
    int triggerModifier = mKeyComboManager.getKeyComboModel().getTriggerModifier();

    if (triggerModifier == KeyComboModel.NO_MODIFIER) {
      return KEY_EVENT_SOURCE_ACTIVITY;
    } else {
      return KEY_EVENT_SOURCE_ACCESSIBILITY_SERVICE;
    }
  }

  private void setKeyEventSource(int keyEventSource) {
    if (mKeyEventSource == keyEventSource) {
      return;
    }

    mKeyEventSource = keyEventSource;

    if (keyEventSource == KEY_EVENT_SOURCE_ACCESSIBILITY_SERVICE) {
      mKeyComboManager.setKeyEventDelegate(this);
    } else {
      mKeyComboManager.setKeyEventDelegate(null);
    }
  }

  @Override
  public void notifyChanged() {
    super.notifyChanged();
  }

  @Override
  public CharSequence getSummary() {
    return mKeyComboManager.getKeyComboStringRepresentation(
        getTemporaryKeyComboCodeWithTriggerModifier());
  }

  @Override
  protected void onDialogClosed(boolean positiveResult) {
    super.onDialogClosed(positiveResult);
    setTemporaryKeyComboCodeWithoutTriggerModifier(
        mKeyComboManager.getKeyComboModel().getKeyComboCodeForKey(getKey()));
    mKeyComboManager.setMatchKeyCombo(true);
    setKeyEventSource(KEY_EVENT_SOURCE_ACTIVITY);
  }

  @Override
  protected void onBindDialogView(@NonNull View view) {
    super.onBindDialogView(view);

    setTemporaryKeyComboCodeWithoutTriggerModifier(
        mKeyComboManager.getKeyComboModel().getKeyComboCodeForKey(getKey()));
    mKeyAssignmentView = (TextView) view.findViewById(R.id.assigned_combination);
    mInstructionText = (TextView) view.findViewById(R.id.instruction);
    mInstructionText.setText(
        mKeyComboManager.getKeyComboModel().getDescriptionOfEligibleKeyCombo());
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
    alertDialog
        .getButton(DialogInterface.BUTTON_POSITIVE)
        .setOnClickListener(mOkButtonClickListener);
    alertDialog.setOnKeyListener(this);

    Button okButton = alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
    okButton.setFocusableInTouchMode(true);
    okButton.requestFocus();

    setKeyEventSource(getKeyEventSourceForCurrentKeyComboModel());
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    if (mKeyEventSource != KEY_EVENT_SOURCE_ACCESSIBILITY_SERVICE) {
      return false;
    }

    return onKeyEventInternal(event);
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return true;
  }

  @Override
  public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
    if (mKeyEventSource != KEY_EVENT_SOURCE_ACTIVITY) {
      return false;
    }

    return onKeyEventInternal(event);
  }

  private boolean onKeyEventInternal(KeyEvent event) {
    if (!processKeyEvent(event)) {
      return false;
    }

    // The plain backspace key clears the shortcut; anything else is treated as a new shortcut.
    if (event.getKeyCode() == KeyEvent.KEYCODE_DEL && event.hasNoModifiers()) {
      clearTemporaryKeyComboCode();
    } else {
      setTemporaryKeyComboCodeWithTriggerModifier(KeyComboManager.getKeyComboCode(event));
    }

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
    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
        || event.getKeyCode() == KeyEvent.KEYCODE_HOME
        || event.getKeyCode() == KeyEvent.KEYCODE_CALL
        || event.getKeyCode() == KeyEvent.KEYCODE_ENDCALL) {
      return false;
    }

    // Enter and Esc are used to accept/dismiss dialogs. However, the default shortcuts
    // involve Enter and Esc (with modifiers), so we should only trap Enter and Esc without
    // modifiers.
    boolean isDialogNavigation =
        event.getKeyCode() == KeyEvent.KEYCODE_ENTER
            || event.getKeyCode() == KeyEvent.KEYCODE_ESCAPE;
    if (isDialogNavigation && event.hasNoModifiers()) {
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
    setKeyEventSource(KEY_EVENT_SOURCE_ACTIVITY);
    showOverrideKeyComboDialog(
        currentAction,
        newAction,
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which != DialogInterface.BUTTON_POSITIVE) {
              setKeyEventSource(getKeyEventSourceForCurrentKeyComboModel());
              return;
            }

            saveKeyCode();
            mKeyComboManager.getKeyComboModel().clearKeyComboCode(key);
            notifyListener(key, mKeyComboManager.getKeyComboModel().getKeyComboCodeForKey(key));
            Dialog mainDialog = getDialog();
            if (mainDialog != null) {
              mainDialog.dismiss();
            }
          }
        });
  }

  private void saveKeyCode() {
    mKeyComboManager
        .getKeyComboModel()
        .saveKeyComboCode(getKey(), getTemporaryKeyComboCodeWithoutTriggerModifier());
    notifyListener(getKey(), getTemporaryKeyComboCodeWithoutTriggerModifier());
  }

  public void setKeyComboCode(long keyComboCodeWithoutModifier) {
    setTemporaryKeyComboCodeWithoutTriggerModifier(keyComboCodeWithoutModifier);
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

  private void showOverrideKeyComboDialog(
      CharSequence currentAction,
      CharSequence newAction,
      final DialogInterface.OnClickListener clickListener) {
    String message =
        getContext()
            .getString(R.string.override_keycombo_message_two_params, currentAction, newAction);
    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
    builder
        .setTitle(R.string.override_keycombo)
        .setMessage(message)
        .setNegativeButton(
            android.R.string.cancel,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                clickListener.onClick(dialog, which);
              }
            })
        .setPositiveButton(
            android.R.string.ok,
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                saveKeyCode();
                clickListener.onClick(dialog, which);
              }
            })
        .show();
  }
}

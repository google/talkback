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

package com.google.android.accessibility.talkback.preference.base;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import androidx.appcompat.app.AlertDialog;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.preference.Preference;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.keyboard.KeyComboManager;
import com.google.android.accessibility.talkback.keyboard.KeyComboModel;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper;
import com.google.android.accessibility.utils.material.MaterialComponentUtils;

/**
 * A {@link Preference} which contains two dialogs, setUpKeyComboDialog and keyAlreadyInUseDialog.
 * SetUpKeyComboDialog is for all keyboard combo assigned key, it provides a customized dialog for
 * combo assigned key setting. KeyAlreadyInUseDialog is for warning users that the input key
 * combination is already in use.
 */
public class KeyboardShortcutDialogPreference extends Preference
    implements DialogInterface.OnKeyListener, ServiceKeyEventListener {

  private static final int KEY_EVENT_SOURCE_ACTIVITY = 0;
  private static final int KEY_EVENT_SOURCE_ACCESSIBILITY_SERVICE = 1;

  private KeyComboManager keyComboManager;
  private int keyEventSource = KEY_EVENT_SOURCE_ACTIVITY;
  private int temporaryModifier;
  private int temporaryKeyCode;

  private A11yAlertDialogWrapper keyAlreadyInUseDialog;
  private AlertDialog setUpKeyComboDialog;
  private TextView keyAssignmentView;
  private TextView instructionText;

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

  @Override
  protected void onClick() {
    super.onClick();
    showSetUpKeyComboDialog();
  }

  static KeyComboManager getKeyComboManager(Context context) {
    KeyComboManager keyComboManager;
    if (TalkBackService.getInstance() != null) {
      keyComboManager = TalkBackService.getInstance().getKeyComboManager();
    } else {
      keyComboManager = KeyComboManager.create(context);
    }

    return keyComboManager;
  }

  private void init() {
    setPersistent(true);
    updateKeyComboManager();
    setTemporaryKeyComboCodeWithoutTriggerModifier(
        keyComboManager.getKeyComboModel().getKeyComboCodeForKey(getKey()));
  }

  public void updateKeyComboManager() {
    keyComboManager = getKeyComboManager(getContext());

    if (keyComboManager == null) {
      throw new IllegalStateException(
          "KeyboardShortcutDialogPreference should never appear "
              + "on systems where KeyComboManager is unavailable");
    }
  }

  public void onTriggerModifierChanged() {
    setTemporaryKeyComboCodeWithoutTriggerModifier(
        keyComboManager.getKeyComboModel().getKeyComboCodeForKey(getKey()));

    // Update summary since it will be changed when trigger modifier is changed.
    setSummary(getSummary());
  }

  public void setKeyComboCode(long keyComboCodeWithoutModifier) {
    setTemporaryKeyComboCodeWithoutTriggerModifier(keyComboCodeWithoutModifier);
  }

  @Override
  public void notifyChanged() {
    super.notifyChanged();
  }

  @Override
  public CharSequence getSummary() {
    return keyComboManager.getKeyComboStringRepresentation(
        getTemporaryKeyComboCodeWithTriggerModifier());
  }

  @Override
  protected void onPrepareForRemoval() {
    super.onPrepareForRemoval();
  }

  /** Clears current temporary key combo code. */
  private void clearTemporaryKeyComboCode() {
    temporaryModifier = KeyComboModel.NO_MODIFIER;
    temporaryKeyCode = KeyEvent.KEYCODE_UNKNOWN;
  }

  /**
   * Sets temporary key combo code with trigger modifier. You can set key combo code which doesn't
   * contain trigger modifier.
   */
  private void setTemporaryKeyComboCodeWithTriggerModifier(long keyComboCode) {
    temporaryModifier = KeyComboManager.getModifier(keyComboCode);
    temporaryKeyCode = KeyComboManager.getKeyCode(keyComboCode);
  }

  /** Sets temporary key combo code without trigger modifier. */
  private void setTemporaryKeyComboCodeWithoutTriggerModifier(long keyComboCode) {
    temporaryModifier = KeyComboManager.getModifier(keyComboCode);
    temporaryKeyCode = KeyComboManager.getKeyCode(keyComboCode);

    int triggerModifier = keyComboManager.getKeyComboModel().getTriggerModifier();
    if (keyComboCode != KeyComboModel.KEY_COMBO_CODE_UNASSIGNED
        && triggerModifier != KeyComboModel.NO_MODIFIER) {
      temporaryModifier = temporaryModifier | triggerModifier;
    }
  }

  /** Gets temporary key combo code with trigger modifier. */
  private long getTemporaryKeyComboCodeWithTriggerModifier() {
    return KeyComboManager.getKeyComboCode(temporaryModifier, temporaryKeyCode);
  }

  /**
   * Gets temporary key combo code without trigger modifier. If current temporary key combo code
   * doesn't contain trigger modifier, KEY_COMBO_CODE_INVALID will be returned.
   */
  private long getTemporaryKeyComboCodeWithoutTriggerModifier() {
    if (getTemporaryKeyComboCodeWithTriggerModifier() == KeyComboModel.KEY_COMBO_CODE_UNASSIGNED) {
      return KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
    }

    int triggerModifier = keyComboManager.getKeyComboModel().getTriggerModifier();

    if (triggerModifier != KeyComboModel.NO_MODIFIER
        && (temporaryModifier & triggerModifier) == 0) {
      return KeyComboModel.KEY_COMBO_CODE_INVALID;
    }

    int modifier = temporaryModifier & ~triggerModifier;
    return KeyComboManager.getKeyComboCode(modifier, temporaryKeyCode);
  }

  private int getKeyEventSourceForCurrentKeyComboModel() {
    int triggerModifier = keyComboManager.getKeyComboModel().getTriggerModifier();

    if (triggerModifier == KeyComboModel.NO_MODIFIER) {
      return KEY_EVENT_SOURCE_ACTIVITY;
    } else {
      return KEY_EVENT_SOURCE_ACCESSIBILITY_SERVICE;
    }
  }

  private void setKeyEventSource(int keyEventSource) {
    if (this.keyEventSource == keyEventSource) {
      return;
    }

    this.keyEventSource = keyEventSource;

    if (keyEventSource == KEY_EVENT_SOURCE_ACCESSIBILITY_SERVICE) {
      keyComboManager.setKeyEventDelegate(this);
    } else {
      keyComboManager.setKeyEventDelegate(null);
    }
  }

  /** Handles key combo when fragment closes. */
  private void onSetUpKeyComboDialogClosed() {
    setTemporaryKeyComboCodeWithoutTriggerModifier(
        keyComboManager.getKeyComboModel().getKeyComboCodeForKey(getKey()));
    keyComboManager.setMatchKeyCombo(true);
    setKeyEventSource(KEY_EVENT_SOURCE_ACTIVITY);
  }

  /**
   * Registers the key event listener to receive key event.
   *
   * @param dialog Dialog receives key event.
   */
  private void registerDialogKeyEvent(Dialog dialog) {
    if (dialog == null) {
      return;
    }

    dialog.setOnKeyListener(this);
    setKeyEventSource(getKeyEventSourceForCurrentKeyComboModel());
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    if (keyEventSource != KEY_EVENT_SOURCE_ACCESSIBILITY_SERVICE) {
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
    if (keyEventSource != KEY_EVENT_SOURCE_ACTIVITY) {
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

    // Uses Enter key to confirm the key combo change. If keyAlreadyInUseDialog which confirms
    // duplicate assigned key isn't null,
    // it will not process the button click function since onKey() will receive enter key twice.
    // keyAlreadyInUseDialog will be null when this dialog dismisses.
    if (event.hasNoModifiers()
        && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)
        && (keyAlreadyInUseDialog == null)) {
      processOKButtonClickListener();
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

  /** Shows dialog if there is duplicate key assigned. */
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
            keyComboManager.getKeyComboModel().clearKeyComboCode(key);
            notifyListener(key, keyComboManager.getKeyComboModel().getKeyComboCodeForKey(key));
            if (keyAlreadyInUseDialog != null) {
              keyAlreadyInUseDialog.dismiss();
            }
          }
        });
  }

  /** Saves key code to keyComboManager and notifies the listeners. */
  private void saveKeyCode() {
    keyComboManager
        .getKeyComboModel()
        .saveKeyComboCode(getKey(), getTemporaryKeyComboCodeWithoutTriggerModifier());
    notifyListener(getKey(), getTemporaryKeyComboCodeWithoutTriggerModifier());
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
    A11yAlertDialogWrapper.Builder builder =
        A11yAlertDialogWrapper.alertDialogBuilder(getContext())
            .setTitle(R.string.override_keycombo)
            .setMessage(message)
            .setNegativeButton(
                android.R.string.cancel,
                (dialog, which) -> {
                  dialog.dismiss();
                  clickListener.onClick(dialog, which);
                })
            .setPositiveButton(
                android.R.string.ok,
                (dialog, which) -> {
                  saveKeyCode();
                  clickListener.onClick(dialog, which);
                  if (setUpKeyComboDialog != null && setUpKeyComboDialog.isShowing()) {
                    setUpKeyComboDialog.dismiss();
                  }
                });
    keyAlreadyInUseDialog = builder.create();
    keyAlreadyInUseDialog.setOnDismissListener(
        (dialog) -> {
          keyAlreadyInUseDialog = null;
        });
    keyAlreadyInUseDialog.show();
  }

  private void showSetUpKeyComboDialog() {
    setUpKeyComboDialog =
        MaterialComponentUtils.alertDialogBuilder(getContext())
            .setView(getSetUpKeyComboDialogView())
            .create();
    setUpKeyComboDialog.setOnShowListener(
        (dialogInterface) -> {
          Button okButton = setUpKeyComboDialog.getButton(DialogInterface.BUTTON_POSITIVE);
          if (okButton != null) {
            okButton.setOnClickListener(
                (View v) -> {
                  processOKButtonClickListener();
                });
            okButton.setFocusableInTouchMode(true);
            okButton.requestFocus();
          }
          registerDialogKeyEvent(setUpKeyComboDialog);
        });
    setUpKeyComboDialog.setOnDismissListener(
        keyAlreadyInUseDialog -> onSetUpKeyComboDialogClosed());
    setUpKeyComboDialog.show();
  }

  /** Processes when OK buttons is clicked. */
  private void processOKButtonClickListener() {
    long temporaryKeyComboCode = getTemporaryKeyComboCodeWithoutTriggerModifier();
    if (temporaryKeyComboCode == KeyComboModel.KEY_COMBO_CODE_INVALID
        || !keyComboManager.getKeyComboModel().isEligibleKeyComboCode(temporaryKeyComboCode)) {
      instructionText.setTextColor(Color.RED);
      PreferencesActivityUtils.announceText(instructionText.getText().toString(), getContext());
      return;
    }

    String key =
        keyComboManager
            .getKeyComboModel()
            .getKeyForKeyComboCode(getTemporaryKeyComboCodeWithoutTriggerModifier());
    if (key == null) {
      saveKeyCode();
      notifyChanged();
    } else if (!key.equals(getKey())) {
      showOverrideKeyComboDialog(key);
      return;
    }
    if (setUpKeyComboDialog != null) {
      setUpKeyComboDialog.dismiss();
    }
  }

  private View getSetUpKeyComboDialogView() {
    final View dialogView =
        LayoutInflater.from(getContext())
            .inflate(R.layout.keyboard_shortcut_dialog, /* root= */ null);
    String key = getKey();

    updateKeyComboManager();

    setTemporaryKeyComboCodeWithoutTriggerModifier(
        keyComboManager.getKeyComboModel().getKeyComboCodeForKey(key));

    keyAssignmentView = (TextView) dialogView.findViewById(R.id.assigned_combination);
    instructionText = (TextView) dialogView.findViewById(R.id.instruction);
    instructionText.setText(keyComboManager.getKeyComboModel().getDescriptionOfEligibleKeyCombo());

    keyAssignmentView.setText(
        keyComboManager.getKeyComboStringRepresentation(
            getTemporaryKeyComboCodeWithTriggerModifier()));

    View clear = dialogView.findViewById(R.id.clear);
    clear.setOnClickListener(
        (View v) -> {
          instructionText.setTextColor(Color.BLACK);
          clearTemporaryKeyComboCode();
          updateKeyAssignmentText();
        });

    keyComboManager.setMatchKeyCombo(false);

    return dialogView;
  }

  /** Updates key assignment by getting the summary of th preference. */
  private void updateKeyAssignmentText() {
    keyAssignmentView.setText(getSummary());
  }
}

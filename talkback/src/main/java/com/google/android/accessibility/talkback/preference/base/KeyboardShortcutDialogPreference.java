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
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityManager;
import androidx.preference.DialogPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceDialogFragmentCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.keyboard.KeyComboManager;
import com.google.android.accessibility.talkback.keyboard.KeyComboModel;
import com.google.android.accessibility.utils.A11yAlertDialogWrapper;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A {@link DialogPreference} which contains a dialog for all keybaord combo assigned key. It works
 * with {@link KeyboardShortcutPreferenceFragmentCompat} to provide a customized dialog for combo
 * assigned key setting.
 *
 * <p><b>Use {@link #createDialogFragment()} to create the dialog fragment.<b/>
 */
public class KeyboardShortcutDialogPreference extends DialogPreference
    implements DialogInterface.OnKeyListener,
        ServiceKeyEventListener,
        AccessibilityManager.AccessibilityStateChangeListener {

  private static final int KEY_EVENT_SOURCE_ACTIVITY = 0;
  private static final int KEY_EVENT_SOURCE_ACCESSIBILITY_SERVICE = 1;

  private KeyComboManager keyComboManager;
  private int keyEventSource = KEY_EVENT_SOURCE_ACTIVITY;
  private AccessibilityManager accessibilityManager;
  private int temporaryModifier;
  private int temporaryKeyCode;
  private KeyboardShortcutPreferenceFragmentCompat keyboardShortcutPreferenceFragment;
  private A11yAlertDialogWrapper alertDialog;

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

  /** Creates the dialog fragment, which contains the list of supported actions. */
  public PreferenceDialogFragmentCompat createDialogFragment() {
    keyboardShortcutPreferenceFragment = KeyboardShortcutPreferenceFragmentCompat.create(this);
    return keyboardShortcutPreferenceFragment;
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
    setDialogLayoutResource(R.layout.keyboard_shortcut_dialog);

    keyComboManager = getKeyComboManager(getContext());

    if (keyComboManager == null) {
      throw new IllegalStateException(
          "KeyboardShortcutDialogPreference should never appear "
              + "on systems where KeyComboManager is unavailable");
    }

    setTemporaryKeyComboCodeWithoutTriggerModifier(
        keyComboManager.getKeyComboModel().getKeyComboCodeForKey(getKey()));

    accessibilityManager =
        (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
    accessibilityManager.addAccessibilityStateChangeListener(this);

    updateAvailability();
  }

  public void onTriggerModifierChanged() {
    setTemporaryKeyComboCodeWithoutTriggerModifier(
        keyComboManager.getKeyComboModel().getKeyComboCodeForKey(getKey()));

    // Update summary since it will be changed when trigger modifier is changed.
    setSummary(getSummary());
  }

  /** Clears current temporary key combo code. */
  void clearTemporaryKeyComboCode() {
    temporaryModifier = KeyComboModel.NO_MODIFIER;
    temporaryKeyCode = KeyEvent.KEYCODE_UNKNOWN;
  }

  /**
   * Sets temporary key combo code with trigger modifier. You can set key combo code which doesn't
   * contain trigger modifier.
   */
  void setTemporaryKeyComboCodeWithTriggerModifier(long keyComboCode) {
    temporaryModifier = KeyComboManager.getModifier(keyComboCode);
    temporaryKeyCode = KeyComboManager.getKeyCode(keyComboCode);
  }

  /** Sets temporary key combo code without trigger modifier. */
  void setTemporaryKeyComboCodeWithoutTriggerModifier(long keyComboCode) {
    temporaryModifier = KeyComboManager.getModifier(keyComboCode);
    temporaryKeyCode = KeyComboManager.getKeyCode(keyComboCode);

    int triggerModifier = keyComboManager.getKeyComboModel().getTriggerModifier();
    if (keyComboCode != KeyComboModel.KEY_COMBO_CODE_UNASSIGNED
        && triggerModifier != KeyComboModel.NO_MODIFIER) {
      temporaryModifier = temporaryModifier | triggerModifier;
    }
  }

  /** Gets temporary key combo code with trigger modifier. */
  long getTemporaryKeyComboCodeWithTriggerModifier() {
    return KeyComboManager.getKeyComboCode(temporaryModifier, temporaryKeyCode);
  }

  /**
   * Gets temporary key combo code without trigger modifier. If current temporary key combo code
   * doesn't contain trigger modifier, KEY_COMBO_CODE_INVALID will be returned.
   */
  long getTemporaryKeyComboCodeWithoutTriggerModifier() {
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

  @Override
  public void onAccessibilityStateChanged(boolean enabled) {
    updateAvailability();
  }

  @Override
  protected void onPrepareForRemoval() {
    accessibilityManager.removeAccessibilityStateChangeListener(this);

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

  @Override
  public void notifyChanged() {
    super.notifyChanged();
  }

  @Override
  public CharSequence getSummary() {
    return keyComboManager.getKeyComboStringRepresentation(
        getTemporaryKeyComboCodeWithTriggerModifier());
  }

  /** Handles key combo when fragment closes. */
  void onDialogClosed() {
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
  void registerDialogKeyEvent(Dialog dialog) {
    if (dialog == null) {
      return;
    }

    dialog.setOnKeyListener(this);
    setKeyEventSource(getKeyEventSourceForCurrentKeyComboModel());
  }

  /**
   * Gets the dialog of KeyboardShortcutDialogPreference. Returns null if
   * KeyboardShortcutPreferenceFragment isn't created.
   *
   * @return The dialog of KeyboardShortcutPreferenceFragment.
   */
  public @Nullable Dialog getDialog() {
    if (keyboardShortcutPreferenceFragment == null) {
      return null;
    }
    return keyboardShortcutPreferenceFragment.getDialog();
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

    keyboardShortcutPreferenceFragment.updateKeyAssignmentText();

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

    // Uses Enter key to replace of OK button from S since there is no button in the
    // DialogPreference. If alertDialog which confirms duplicate assigned key isn't null, it will
    // not process the button click function since onKey() will receive enter key twice. alertDialog
    // will be null when this dialog dismisses.
    if (FeatureSupport.supportSettingsTheme()
        && event.hasNoModifiers()
        && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)
        && (alertDialog == null)) {
      keyboardShortcutPreferenceFragment.processOKButtonClickListener();
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
  void showOverrideKeyComboDialog(final String key) {
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
            Dialog mainDialog = getDialog();
            if (mainDialog != null) {
              mainDialog.dismiss();
            }
          }
        });
  }

  /** Saves key code to keyComboManager and notifies the listeners. */
  void saveKeyCode() {
    keyComboManager
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
                });
    alertDialog = builder.create();
    alertDialog.setOnDismissListener(
        (dialog) -> {
          alertDialog = null;
        });
    alertDialog.show();
  }
}

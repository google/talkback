/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.google.android.accessibility.talkback.keyboard;

import static android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO;
import static android.content.res.Configuration.HARDKEYBOARDHIDDEN_UNDEFINED;
import static com.google.android.accessibility.utils.preference.PreferencesActivity.FRAGMENT_NAME;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.text.Layout;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import com.android.talkback.TalkBackPreferencesActivity;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.DirectionNavigationActor;
import com.google.android.accessibility.talkback.actor.FullScreenReadActor;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.preference.base.TalkBackKeyboardShortcutPreferenceFragment;
import com.google.android.accessibility.talkback.selector.SelectorController;
import com.google.android.accessibility.talkback.utils.NotificationUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.ServiceStateListener;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper;
import com.google.android.accessibility.utils.widget.DialogUtils;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Manages state related to detecting key combinations. */
public class KeyComboManager implements ServiceKeyEventListener, ServiceStateListener {
  private static final String TAG = "KeyComboManager";
  public static final int KEYMAP_DEFAULT = R.string.default_keymap_entry_value;
  @VisibleForTesting static final int KEYMAP_CHANGES_NOTIFICATION_ID = 6;
  public static final int NO_MATCH = -1;
  public static final int PARTIAL_MATCH = 1;
  public static final int EXACT_MATCH = 2;

  private static final int KEY_EVENT_MODIFIER_MASK =
      KeyEvent.META_SHIFT_ON | KeyEvent.META_CTRL_ON | KeyEvent.META_ALT_ON | KeyEvent.META_META_ON;

  public static final String CONCATENATION_STR = " + ";
  private static final String KEYCODE_PREFIX = "KEYCODE_";

  /** When user has pressed same key twice less than this interval, we handle them as double tap. */
  private static final long TIME_TO_DETECT_DOUBLE_TAP = 1000; // ms

  /** Returns keyComboCode that represent keyEvent. */
  public static long getKeyComboCode(KeyEvent keyEvent) {
    if (keyEvent == null) {
      return KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
    }

    int modifier = keyEvent.getModifiers() & KEY_EVENT_MODIFIER_MASK;
    return getKeyComboCode(modifier, getConvertedKeyCode(keyEvent));
  }

  /** Returns key combo code which is combination of modifier and keycode. */
  public static long getKeyComboCode(int modifier, int keycode) {
    return (((long) modifier) << 32) + keycode;
  }

  /** Returns modifier part of key combo code. */
  public static int getModifier(long keyComboCode) {
    return (int) (keyComboCode >> 32);
  }

  /** Returns key code part of key combo code. */
  public static int getKeyCode(long keyComboCode) {
    return (int) (keyComboCode);
  }

  /**
   * Returns converted key code. This method converts the following key events. - Convert
   * KEYCODE_HOME with meta to KEYCODE_ENTER. - Convert KEYCODE_BACK with meta to KEYCODE_DEL.
   *
   * @param event Key event to be converted.
   * @return Converted key code.
   */
  private static int getConvertedKeyCode(KeyEvent event) {
    // We care only when meta key is pressed with.
    if ((event.getModifiers() & KeyEvent.META_META_ON) == 0) {
      return event.getKeyCode();
    }

    if (event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
      return KeyEvent.KEYCODE_ENTER;
    } else if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
      return KeyEvent.KEYCODE_DEL;
    } else {
      return event.getKeyCode();
    }
  }

  /** Whether the user performed a combo during the current interaction. */
  private boolean performedCombo;

  /** Whether the user may be performing a combo and we should intercept keys. */
  private boolean hasPartialMatch;

  private Set<Integer> currentKeysDown = new HashSet<>();
  private Set<Integer> passedKeys = new HashSet<>();

  private long currentKeyComboCode = KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
  private long currentKeyComboTime = 0;
  private long previousKeyComboCode = KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
  private long previousKeyComboTime = 0;

  private Context context;
  private NotificationManager notificationManager;
  private SharedPreferences sharedPreferences;
  private boolean matchKeyCombo = true;
  private KeyComboModel keyComboModel;
  private int serviceState = SERVICE_STATE_INACTIVE;
  private ServiceKeyEventListener keyEventDelegate;
  private KeyComboMapper keyComboMapper;
  private TalkBackAnalytics analytics;
  private int hardwareKeyboardStatus = HARDKEYBOARDHIDDEN_UNDEFINED;
  private Notification keymapNotification;
  private A11yAlertDialogWrapper updateModifierKeysDialog;

  public static KeyComboManager create(Context context) {
    return new KeyComboManager(context);
  }

  private KeyComboManager(Context context) {
    this.context = context;
    initializeDefaultPreferenceValues();
    keyComboModel = createKeyComboModel();
  }

  // TODO: KeyComboManager would be separated into KeyComboManager
  // and KeyComboModelManager
  public KeyComboManager(
      Context context,
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      SelectorController selectorController,
      ListMenuManager menuManager,
      FullScreenReadActor fullScreenReadActor,
      TalkBackAnalytics analytics,
      DirectionNavigationActor.StateReader stateReader) {
    this(context);
    notificationManager =
        (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    this.analytics = analytics;
    hardwareKeyboardStatus = context.getResources().getConfiguration().hardKeyboardHidden;
    keyComboMapper =
        new KeyComboMapper(
            context,
            pipeline,
            actorState,
            selectorController,
            menuManager,
            fullScreenReadActor,
            stateReader);
    sharedPreferences = SharedPreferencesUtils.getSharedPreferences(context);
    sharedPreferences.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    updateKeymapChangesNotificationVisibility();
    showOrHideUpdateModifierKeysDialog();
  }

  /** Terminates instances to prevent leakage. */
  public void shutdown() {
    dismissUpdateModifierKeysDialog();
    notificationManager.cancel(KEYMAP_CHANGES_NOTIFICATION_ID);
    sharedPreferences.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
  }

  @Override
  public void onServiceStateChanged(int newState) {
    // Unfortunately, key events are lost when the TalkBackService becomes active. If a key-down
    // occurs that triggers TalkBack to resume, the corresponding key-up event will not be
    // sent, causing the partially-matched key history to become inconsistent.
    // The following method will cause the key history to be reset.
    setMatchKeyCombo(matchKeyCombo);

    if (newState == SERVICE_STATE_INACTIVE && hardwareKeyboardStatus == HARDKEYBOARDHIDDEN_NO) {
      analytics.onKeymapTypeUsed(keyComboModel);
      analytics.onModifierKeyUsed(keyComboModel.getTriggerModifier());
    }
    serviceState = newState;
  }

  /**
   * Handles incoming key events. May intercept keys if the user seems to be performing a key combo.
   *
   * @param event The key event.
   * @return {@code true} if the key was intercepted.
   */
  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    if (keyEventDelegate != null) {
      if (keyEventDelegate.onKeyEvent(event, eventId)) {
        return true;
      }
    }

    if (!hasPartialMatch && !performedCombo && !matchKeyCombo) {
      return false;
    }

    switch (event.getAction()) {
      case KeyEvent.ACTION_DOWN:
        return onKeyDown(event);
      case KeyEvent.ACTION_MULTIPLE:
        return hasPartialMatch;
      case KeyEvent.ACTION_UP:
        return onKeyUp(event);
      default:
        return false;
    }
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return true;
  }

  /**
   * Sets delegate for key events. If it's set, it can listen and consume key events before
   * KeyComboManager does. Sets null to remove current one.
   */
  public void setKeyEventDelegate(ServiceKeyEventListener delegate) {
    keyEventDelegate = delegate;
  }

  /**
   * Returns keymap by reading preference.
   *
   * @return key map. Returns classic key map as default.
   */
  public String getKeymap() {
    SharedPreferences preferences = SharedPreferencesUtils.getSharedPreferences(context);
    return preferences.getString(
        context.getString(R.string.pref_select_keymap_key), context.getString(KEYMAP_DEFAULT));
  }

  /** Refreshes key combo model after key map changes. */
  public void refreshKeyComboModel() {
    keyComboModel = createKeyComboModel();
  }

  /** Returns modifier part of key combo code. */
  public KeyComboModel getKeyComboModel() {
    return keyComboModel;
  }

  /** Set whether to process keycombo */
  public void setMatchKeyCombo(boolean value) {
    matchKeyCombo = value;
  }

  /** Returns user friendly string representations of key combo code */
  public String getKeyComboStringRepresentation(long keyComboCode) {
    if (keyComboCode == KeyComboModel.KEY_COMBO_CODE_UNASSIGNED) {
      return context.getString(R.string.keycombo_unassigned);
    }

    int triggerModifier = keyComboModel.getTriggerModifier();
    int modifier = getModifier(keyComboCode);
    int modifierWithoutTriggerModifier = modifier & ~triggerModifier;
    int keyCode = getKeyCode(keyComboCode);

    StringBuilder sb = new StringBuilder();

    // Append trigger modifier if key combo code contains it.
    if ((triggerModifier & modifier) != 0) {
      appendModifiers(triggerModifier, sb);
    }

    // Append modifier except trigger modifier.
    appendModifiers(modifierWithoutTriggerModifier, sb);

    // Append key code.
    if (keyCode > 0 && !KeyEvent.isModifierKey(keyCode)) {
      appendPlusSignIfNotEmpty(sb);

      switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_RIGHT:
          sb.append(context.getString(R.string.keycombo_key_arrow_right));
          break;
        case KeyEvent.KEYCODE_DPAD_LEFT:
          sb.append(context.getString(R.string.keycombo_key_arrow_left));
          break;
        case KeyEvent.KEYCODE_DPAD_UP:
          sb.append(context.getString(R.string.keycombo_key_arrow_up));
          break;
        case KeyEvent.KEYCODE_DPAD_DOWN:
          sb.append(context.getString(R.string.keycombo_key_arrow_down));
          break;
        case KeyEvent.KEYCODE_DEL:
          sb.append(context.getString(R.string.keycombo_key_backspace));
          break;
        default:
          String keyCodeString = KeyEvent.keyCodeToString(keyCode);
          if (keyCodeString != null) {
            String keyCodeNoPrefix;
            if (keyCodeString.startsWith(KEYCODE_PREFIX)) {
              keyCodeNoPrefix = keyCodeString.substring(KEYCODE_PREFIX.length());
            } else {
              keyCodeNoPrefix = keyCodeString;
            }
            sb.append(keyCodeNoPrefix.replace('_', ' '));
          }
          break;
      }
    }

    return sb.toString();
  }

  /** Notifies configuration changed. */
  public void onConfigurationChanged(Configuration newConfig) {
    if (newConfig.hardKeyboardHidden != hardwareKeyboardStatus) {
      hardwareKeyboardStatus = newConfig.hardKeyboardHidden;
      updateKeymapChangesNotificationVisibility();
      showOrHideUpdateModifierKeysDialog();
    }
  }

  /**
   * Make keymap default value consist with xml set up, which is the intended default keymap value.
   */
  private void initializeDefaultPreferenceValues() {
    SharedPreferences preferences = SharedPreferencesUtils.getSharedPreferences(context);
    if (preferences.contains(context.getString(R.string.pref_select_keymap_key))) {
      return;
    }

    preferences
        .edit()
        .putString(
            context.getString(R.string.pref_select_keymap_key), context.getString(KEYMAP_DEFAULT))
        .apply();
  }

  /**
   * Creates key combo model by keymap key.
   *
   * @return Key combo model. null will be returned if keymap is invalid.
   */
  @Nullable
  private KeyComboModel createKeyComboModel() {
    String keymap = getKeymap();
    if (keymap.equals(context.getString(R.string.classic_keymap_entry_value))) {
      return new ClassicKeyComboModel(context);
    } else if (keymap.equals(context.getString(R.string.default_keymap_entry_value))) {
      return new DefaultKeyComboModel(context);
    }
    return null;
  }

  /** Appends modifier. */
  private void appendModifiers(int modifier, StringBuilder sb) {
    appendModifier(
        modifier, KeyEvent.META_ALT_ON, context.getString(R.string.keycombo_key_modifier_alt), sb);
    appendModifier(
        modifier,
        KeyEvent.META_SHIFT_ON,
        context.getString(R.string.keycombo_key_modifier_shift),
        sb);
    appendModifier(
        modifier,
        KeyEvent.META_CTRL_ON,
        context.getString(R.string.keycombo_key_modifier_ctrl),
        sb);
    appendModifier(
        modifier,
        KeyEvent.META_META_ON,
        context.getString(R.string.keycombo_key_modifier_meta),
        sb);
  }

  /** Appends string representation of target modifier if modifier contains it. */
  private void appendModifier(
      int modifier, int targetModifier, String stringRepresentation, StringBuilder sb) {
    if ((modifier & targetModifier) != 0) {
      appendPlusSignIfNotEmpty(sb);
      sb.append(stringRepresentation);
    }
  }

  private void appendPlusSignIfNotEmpty(StringBuilder sb) {
    if (sb.length() > 0) {
      sb.append(CONCATENATION_STR);
    }
  }

  private boolean onKeyDown(KeyEvent event) {
    currentKeysDown.add(event.getKeyCode());
    currentKeyComboCode = getKeyComboCode(event);
    currentKeyComboTime = event.getDownTime();

    // Check modifier.
    int triggerModifier = keyComboModel.getTriggerModifier();
    event = convertMetaKeyCombo(triggerModifier, event);
    boolean hasModifier = triggerModifier != KeyComboModel.NO_MODIFIER;
    if (hasModifier && (triggerModifier & event.getModifiers()) != triggerModifier) {
      // Do nothing if condition of modifier is not met.
      passedKeys.addAll(currentKeysDown);
      return false;
    }

    boolean isServiceActive = (serviceState == SERVICE_STATE_ACTIVE);

    // If the current set of keys is a partial combo, consume the event.
    hasPartialMatch = false;

    for (Map.Entry<String, Long> entry : keyComboModel.getKeyComboCodeMap().entrySet()) {
      if (!isServiceActive) {
        continue;
      }

      final int match = matchKeyEventWith(event, triggerModifier, entry.getValue());
      if (match == EXACT_MATCH) {
        TalkBackPhysicalKeyboardShortcut action =
            TalkBackPhysicalKeyboardShortcut.getActionFromKey(
                context.getResources(), entry.getKey());
        String comboName = getKeyComboStringRepresentation(currentKeyComboCode);
        EventId eventId =
            Performance.getInstance().onKeyComboEventReceived(action.getKeyboardShortcutOrdinal());

        // Checks interrupt events if matches key combos. To prevent interrupting actions generated
        // by key combos, we should send interrupt events
        // before performing key combos.
        interrupt(action);

        if (keyComboMapper.performKeyComboAction(action, comboName, eventId)) {
          performedCombo = true;
        }

        analytics.onKeyboardShortcutUsed(action, triggerModifier, currentKeyComboCode);

        return true;
      }

      if (match == PARTIAL_MATCH) {
        hasPartialMatch = true;
      }
    }

    // Do not handle key event if user has pressed search key (meta key) twice to open search
    // app.
    if (hasModifier && triggerModifier == KeyEvent.META_META_ON) {
      if (previousKeyComboCode == currentKeyComboCode
          && currentKeyComboTime - previousKeyComboTime < TIME_TO_DETECT_DOUBLE_TAP
          && (currentKeyComboCode
                  == KeyComboManager.getKeyComboCode(
                      KeyEvent.META_META_ON, KeyEvent.KEYCODE_META_RIGHT)
              || currentKeyComboCode
                  == KeyComboManager.getKeyComboCode(
                      KeyEvent.META_META_ON, KeyEvent.KEYCODE_META_LEFT))) {
        // Set KEY_COMBO_CODE_UNASSIGNED not to open search app again with following search
        // key event.
        currentKeyComboCode = KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
        passedKeys.addAll(currentKeysDown);
        return false;
      }
    }

    if (!hasPartialMatch) {
      passedKeys.addAll(currentKeysDown);
    }
    return hasPartialMatch;
  }

  /**
   * Android converts META_META_ON + KEYCODE_ENTER and META_META_ON + KEYCODE_DEL to KEYCODE_HOME
   * and KEYCODE_BACK without META_META_ON. We recover it to the original event and add META_META_ON
   * to this key event to satisfy trigger modifier condition.
   */
  private KeyEvent convertMetaKeyCombo(int triggerModifier, KeyEvent keyEvent) {
    int keyCode = keyEvent.getKeyCode();
    if (triggerModifier == KeyEvent.META_META_ON
        && (keyCode == KeyEvent.KEYCODE_HOME || keyCode == KeyEvent.KEYCODE_BACK)
        && (currentKeysDown.contains(KeyEvent.KEYCODE_META_LEFT)
            || currentKeysDown.contains(KeyEvent.KEYCODE_META_RIGHT))) {

      // The converted KeyEvent has no value of metaState, we should add META_META_ON to satisfy
      // trigger modifier condition.
      int metaState = keyEvent.getMetaState() | KeyEvent.META_META_ON;
      switch (keyCode) {
        case KeyEvent.KEYCODE_HOME:
          keyCode = KeyEvent.KEYCODE_ENTER;
          break;
        case KeyEvent.KEYCODE_BACK:
          keyCode = KeyEvent.KEYCODE_DEL;
          break;
        default: // fall out
      }
      return new KeyEvent(
          keyEvent.getDownTime(),
          keyEvent.getEventTime(),
          keyEvent.getAction(),
          keyCode,
          keyEvent.getRepeatCount(),
          metaState);
    }
    return keyEvent;
  }

  private int matchKeyEventWith(KeyEvent event, int triggerModifier, long keyComboCode) {
    int keyCode = getConvertedKeyCode(event);
    int metaState = event.getModifiers() & KEY_EVENT_MODIFIER_MASK;

    int targetKeyCode = getKeyCode(keyComboCode);
    int targetMetaState = getModifier(keyComboCode) | triggerModifier;

    // Handle exact matches first.
    if (metaState == targetMetaState && keyCode == targetKeyCode) {
      return EXACT_MATCH;
    }

    if (targetMetaState != 0 && metaState == 0) {
      return NO_MATCH;
    }

    // Otherwise, all modifiers must be down.
    if (KeyEvent.isModifierKey(keyCode)
        && targetMetaState != 0
        && (targetMetaState & metaState) != 0) {
      // Partial match.
      return PARTIAL_MATCH;
    }

    // No match.
    return NO_MATCH;
  }

  private boolean shouldShowKeymapChangesNotification() {
    return hardwareKeyboardStatus == HARDKEYBOARDHIDDEN_NO
        && keyComboModel != null
        && keyComboModel instanceof ClassicKeyComboModel
        && !FormFactorUtils.getInstance().isAndroidTv();
  }

  private void updateKeymapChangesNotificationVisibility() {
    if (shouldShowKeymapChangesNotification()) {
      notificationManager.notify(KEYMAP_CHANGES_NOTIFICATION_ID, createKeymapChangesNotification());
    } else {
      notificationManager.cancel(KEYMAP_CHANGES_NOTIFICATION_ID);
    }
  }

  private Notification createKeymapChangesNotification() {
    if (keymapNotification == null) {
      NotificationCompat.Builder builder =
          NotificationUtils.createDefaultNotificationBuilder(context);
      Intent intent = new Intent(context, TalkBackKeymapChangesActivity.class);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      PendingIntent pendingIntent =
          PendingIntent.getActivity(
              context,
              /* requestCode= */ 0,
              intent,
              PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      keymapNotification =
          builder
              .setTicker(context.getString(R.string.keycombo_keymap_changes_instruction_title))
              .setContentTitle(
                  context.getString(R.string.keycombo_keymap_changes_instruction_title))
              .setStyle(
                  new NotificationCompat.BigTextStyle()
                      .bigText(
                          context.getString(
                              R.string.keycombo_keymap_changes_notifications_content)))
              .addAction(
                  /* icon= */ 0,
                  context.getString(R.string.keycombo_keymap_changes_notifications_action),
                  pendingIntent)
              .setContentIntent(pendingIntent)
              .setAutoCancel(true)
              .setOngoing(false)
              .setWhen(0)
              .build();
    }
    return keymapNotification;
  }

  private void showOrHideUpdateModifierKeysDialog() {
    if (!BuildVersionUtils.isAtLeastU()
        || keyComboModel.getTriggerModifier() != KeyEvent.META_META_ON
        || FormFactorUtils.getInstance().isAndroidTv()) {
      return;
    }
    if (hardwareKeyboardStatus != HARDKEYBOARDHIDDEN_NO) {
      dismissUpdateModifierKeysDialog();
      return;
    }
    boolean shouldShowDialogAgain =
        !sharedPreferences.getBoolean(
            context.getString(R.string.keycombo_update_modifier_keys_dialog_do_not_show_again),
            false);
    if (!shouldShowDialogAgain
        || (updateModifierKeysDialog != null && updateModifierKeysDialog.isShowing())) {
      return;
    }
    LayoutInflater inflater = LayoutInflater.from(context);
    final View root =
        inflater.inflate(R.layout.do_not_show_again_checkbox_dialog, /* root= */ null);
    CheckBox doNotShowAgainCheckBox = root.findViewById(R.id.dont_show_again);
    doNotShowAgainCheckBox.setOnCheckedChangeListener(
        (buttonView, isChecked) ->
            sharedPreferences
                .edit()
                .putBoolean(
                    context.getString(
                        R.string.keycombo_update_modifier_keys_dialog_do_not_show_again),
                    isChecked)
                .apply());
    TextView contentTextView = root.findViewById(R.id.dialog_content);
    contentTextView.setText(context.getString(R.string.keycombo_warning_dialog_message));
    updateModifierKeysDialog =
        A11yAlertDialogWrapper.materialDialogBuilder(context)
            .setView(root)
            .setTitle(R.string.keycombo_update_modifier_key_warning_title)
            .setPositiveButton(
                R.string.keycombo_go_to_settings,
                (dialog, which) -> {
                  Intent intent = new Intent(context, TalkBackPreferencesActivity.class);
                  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                  intent.putExtra(
                      FRAGMENT_NAME, TalkBackKeyboardShortcutPreferenceFragment.class.getName());
                  context.startActivity(intent);
                })
            .setNegativeButton(R.string.keycombo_update_modifier_keys_warning_negative_button, null)
            .create();
    DialogUtils.setWindowTypeToDialog(updateModifierKeysDialog.getWindow());
    updateModifierKeysDialog.show();
    // Starting from Android Q, the default value of hyphenation was changed from normal to none.
    TextView textView = updateModifierKeysDialog.getDialog().findViewById(androidx.appcompat.R.id.alertTitle);
    textView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
  }

  /**
   * Notifies the {@link KeyComboMapper} whether should interrupt or not by checking the action
   * enum.
   *
   * @param performedAction the action generating from key combos.
   */
  private void interrupt(TalkBackPhysicalKeyboardShortcut performedAction) {
    if (keyComboMapper != null) {
      keyComboMapper.interruptByKeyCombo(performedAction);
    }
  }

  private boolean onKeyUp(KeyEvent event) {
    currentKeysDown.remove(event.getKeyCode());
    boolean passed = passedKeys.remove(event.getKeyCode());

    if (currentKeysDown.isEmpty()) {
      // Checks interrupt events if no key combos performed in the interaction.
      if (!performedCombo) {
        interrupt(TalkBackPhysicalKeyboardShortcut.ACTION_UNKNOWN);
      }
      // The interaction is over, reset the state.
      performedCombo = false;
      hasPartialMatch = false;
      previousKeyComboCode = currentKeyComboCode;
      previousKeyComboTime = currentKeyComboTime;
      currentKeyComboCode = KeyComboModel.KEY_COMBO_CODE_UNASSIGNED;
      currentKeyComboTime = 0;
      passedKeys.clear();
    }

    return !passed;
  }

  private void dismissUpdateModifierKeysDialog() {
    if (updateModifierKeysDialog != null && updateModifierKeysDialog.isShowing()) {
      updateModifierKeysDialog.dismiss();
    }
  }

  private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          if (context.getString(R.string.pref_select_keymap_key).equals(key)) {
            updateKeymapChangesNotificationVisibility();
          }
        }
      };
}

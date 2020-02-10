/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.accessibility.switchaccess.setupwizard;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.FeatureFlags;
import com.google.android.accessibility.switchaccess.PerformanceMonitor;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.keyassignment.KeyAssignmentUtils;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessSetupScreenEnum.SetupScreen;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Setup Wizard screen for assigning a key to an action. */
public class SetupWizardConfigureSwitchFragment extends SetupWizardScreenFragment {

  private static final String TAG = "SUWConfigureSwitchFrag";

  private static final String ACTION_KEY = "action_key";
  /**
   * All possible actions to which a key can be assigned using this class. Each instance of this
   * class (i.e. each screen) corresponds to exactly one of these actions.
   */
  public enum Action {
    AUTO_SCAN("Autoscan"),
    SELECT("Select"),
    NEXT("Next"),
    GROUP_ONE("GroupOne"),
    GROUP_TWO("GroupTwo");

    private final String name;

    Action(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }
  }

  /** A set of longs which contains both the keys pressed and information about modifiers. */
  private Set<Long> keyCombos;

  /* Adapter to display list of keys assigned to this preference. */
  private ArrayAdapter<CharSequence> keyListAdapter;

  /* Name of the action having keys assigned to it. */
  private String actionName;

  /*  Action having keys assigned to it. */
  private Action actionToBeAssigned;

  /* Button to reset key adapter to empty */
  private Button resetButton;

  /* Switch to toggle screen as a switch for this action */
  private Switch screenSwitchToggle;

  private final CompoundButton.OnCheckedChangeListener switchListener =
      (buttonView, isChecked) -> dispatchKeyEvent(KeyAssignmentUtils.SCREEN_SWITCH_EVENT_UP);

  /*
   * Text view containing the word "None" to display when no keys are assigned to avoid human
   * confusion.
   */
  private TextView emptyKeysTextView;

  private final Handler updateScreenSwitchHandler = new Handler();

  /**
   * Initialize the ListView to show the adapter from the current key list. Also add the appropriate
   * action to the reset button.
   */
  @Override
  @SuppressWarnings("assignment.type.incompatible")
  public void onStart() {
    super.onStart();

    if (actionToBeAssigned == null && getArguments() != null) {
      actionToBeAssigned = (Action) getArguments().getSerializable(ACTION_KEY);
    }

    if (actionToBeAssigned == null) {
      // This should not happen.
      LogUtils.e(TAG, "Fragment was created before an action was assigned.");
      return;
    }

    actionName = SwitchActionInformationUtils.getActionName(getActivity(), actionToBeAssigned);
    ArrayList<CharSequence> keyAdapterList = new ArrayList<>();
    keyListAdapter =
        new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, keyAdapterList);
    keyCombos = KeyAssignmentUtils.getKeyCodesForPreference(getActivity(), actionName);

    KeyAssignmentUtils.updateKeyListAdapter(keyListAdapter, keyCombos, getActivity());

    ListView listView = getRootView().findViewById(R.id.assigned_switch_list);
    listView.setAdapter(keyListAdapter);
    emptyKeysTextView = getRootView().findViewById(R.id.no_switch_assigned_text_view);

    resetButton = getRootView().findViewById(R.id.reset_button);
    resetButton.setOnClickListener(
        view -> {
          // If the screen was being used as a switch, clear this "switch"
          if (screenSwitchToggle != null) {
            screenSwitchToggle.setChecked(false);
          }

          keyCombos.clear();
          KeyAssignmentUtils.updateKeyListAdapter(keyListAdapter, keyCombos, getActivity());
          updateViewForKeyAssignment();
        });

    // If screen-as-a-switch is enabled as a feature, place the corresponding switch on the screen
    if (FeatureFlags.screenSwitch()) {
      screenSwitchToggle = getRootView().findViewById(R.id.screen_switch_toggle);
      screenSwitchToggle.setVisibility(View.VISIBLE);
      screenSwitchToggle.setOnCheckedChangeListener(switchListener);
    }
    updateViewForKeyAssignment();

    String title =
        getActivity()
            .getString(
                R.string.setup_switch_assignment_heading,
                SwitchActionInformationUtils.getSwitchName(getActivity(), actionToBeAssigned));
    ((TextView) getRootView().findViewById(R.id.heading_text_view)).setText(title);

    if (isVisible()) {
      // While the fragment is first loading, it's possible for actionToBeAssigned to be null.
      // Because of this, we need to call #updateUiOnCreateOrRefresh after the fragment has loaded
      // to update headings once actionToBeAssigned is non-null. Since #onStart is called when
      // the activity is recreated, we need to check that the fragment is actually visible before
      // doing this to avoid overwriting another fragment's heading during an activity recreate.
      updateUiOnCreateOrRefresh();
    }
  }

  /** Take the updated key set and apply it to the preference that was being configured. */
  @Override
  public void onStop() {
    super.onStop();
  }

  @Override
  public void onHiddenChanged(boolean hidden) {
    super.onHiddenChanged(hidden);
    if (hidden) {
      storePrefs();
    } else {
      updateScreenSwitchHandler.post(
          () -> {
            if (screenSwitchToggle != null) {
              KeyAssignmentUtils.updateScreenAsASwitchToggle(
                  screenSwitchToggle, keyCombos, getActivity(), actionName);
            }
          });
    }
  }

  private void storePrefs() {
    if (keyCombos != null) {
      Set<String> longPrefStringSet = new HashSet<>(keyCombos.size());
      for (Long keyCombo : keyCombos) {
        longPrefStringSet.add(keyCombo.toString());
      }
      SharedPreferences sharedPreferences =
          SharedPreferencesUtils.getSharedPreferences(getActivity());
      SharedPreferences.Editor editor = sharedPreferences.edit();
      editor.putStringSet(actionName, longPrefStringSet);
      editor.apply();
    }
  }

  /**
   * Checks to see if any keys have been added to be assigned to the preference.
   *
   * @return {@code true} indicating if keys are currently set to be assigned
   */
  public boolean hasSwitchesAdded() {
    return (keyListAdapter != null) && !keyListAdapter.isEmpty();
  }

  /**
   * Accepts and handles key event from parent. KeyAssignmentUtils updates the keycombo list and
   * view. Displays a message if the key is assigned elsewhere, and indicates if the press was
   * consumed or not.
   *
   * @param event KeyEvent to process
   * @return {@code true} indicating if event was consumed
   */
  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getAction() == KeyEvent.ACTION_UP) {
      long keyCombo = KeyAssignmentUtils.keyEventToExtendedKeyCode(event);

      int keyPressResult =
          KeyAssignmentUtils.processKeyAssignment(
              getActivity(), event, keyCombos, actionName, keyListAdapter);

      if (keyPressResult == KeyAssignmentUtils.KEYCOMBO_ALREADY_ASSIGNED) {
        CharSequence toastText =
            getContext()
                .getString(
                    R.string.setup_toast_msg_key_already_assigned,
                    KeyAssignmentUtils.describeExtendedKeyCode(keyCombo, getContext()));
        Toast.makeText(getContext(), toastText, Toast.LENGTH_SHORT).show();
      } else {
        updateViewForKeyAssignment();

        if (keyPressResult == KeyAssignmentUtils.CONSUME_EVENT) {
          SwitchAccessService switchAccessService = SwitchAccessService.getInstance();
          if (switchAccessService != null) {
            PerformanceMonitor.getOrCreateInstance()
                .initializePerformanceMonitoringIfNotInitialized(
                    switchAccessService, switchAccessService.getApplication());
          }
        }

        /* If the event was ignored, return that the event was not consumed. */
        return keyPressResult != KeyAssignmentUtils.IGNORE_EVENT;
      }
    }
    return true;
  }

  private void updateViewForKeyAssignment() {
    emptyKeysTextView.setVisibility(hasSwitchesAdded() ? GONE : VISIBLE);
    resetButton.setEnabled(hasSwitchesAdded());
  }

  @Override
  public SetupScreen getNextScreen() {
    /*
     * Screen flows:
     *
     * One button:
     * <ul>
     * <li>Assign key to auto scan action
     * <li>Choose step speed
     * </ul>
     *
     * Two buttons with row-column and row-column (keyboard only) scanning:
     * <ul>
     * <li>Assign key to next
     * <li>Assign key to select
     * <li>Exit wizard
     * </ul>
     *
     * Two buttons with group selection:
     * <ul>
     * <li>Assign key to first group
     * <li>Assign key to second group
     * <li>Exit wizard
     * </ul>
     */
    switch (actionToBeAssigned) {
      case AUTO_SCAN:
        return SetupScreen.STEP_SPEED_SCREEN;
      case NEXT:
        return SetupScreen.SELECT_KEY_SCREEN;
      case GROUP_ONE:
        return SetupScreen.GROUP_TWO_KEY_SCREEN;
      case SELECT:
      case GROUP_TWO:
        Activity activity = getActivity();
        if (activity == null) {
          return SetupScreen.VIEW_NOT_CREATED;
        }

        if (KeyAssignmentUtils.isConfigurationFunctionalAfterSetup(
            SharedPreferencesUtils.getSharedPreferences(activity), activity)) {
          return SetupScreen.SWITCH_GAME_VALID_CONFIGURATION_SCREEN;
        } else {
          return SetupScreen.SWITCH_GAME_INVALID_CONFIGURATION_SCREEN;
        }
      default:
        throw new IndexOutOfBoundsException("Action does not have a specified next screen");
    }
  }

  @Override
  public String getScreenName() {
    return super.getScreenName() + actionToBeAssigned.getName();
  }

  public void setActionToBeAssigned(Action actionToBeAssigned) {
    this.actionToBeAssigned = actionToBeAssigned;
    Bundle args = new Bundle();
    args.putSerializable(ACTION_KEY, actionToBeAssigned);
    setArguments(args);
  }

  @Override
  protected int getLayoutResourceId() {
    return R.layout.switch_access_setup_configure_switch;
  }

  @Override
  protected void updateUiOnCreateOrRefresh() {
    if (actionToBeAssigned == null) {
      // This can happen when the fragment is first loading.
      return;
    }
    setHeadingText(SwitchActionInformationUtils.getHeading(getActivity(), actionToBeAssigned));
    setSubheadingText(
        SwitchActionInformationUtils.getSubheading(getActivity(), actionToBeAssigned));
    if (screenSwitchToggle != null) {
      KeyAssignmentUtils.updateScreenAsASwitchToggle(
          screenSwitchToggle, keyCombos, getActivity(), actionName);
    }
  }
}

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

import android.content.Context;
import android.content.SharedPreferences;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewStub;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.accessibility.switchaccess.KeyAssignmentUtils;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Setup Wizard screen for assigning a key to an action. */
public class SetupWizardConfigureSwitch extends SetupScreen {

  /**
   * All possible actions to which a key can be assigned using this class. Each instance of this
   * class (i.e. each screen) corresponds to exactly one of these actions.
   */
  public enum Action {
    AUTO_SCAN("Autoscan"),
    SELECT("Select"),
    NEXT("Next"),
    OPTION_ONE("GroupOne"),
    OPTION_TWO("GroupTwo");

    private final String mName;

    private Action(String name) {
      mName = name;
    }

    public String getName() {
      return mName;
    }
  }

  /** A set of longs which contains both the keys pressed and information about modifiers. */
  private final Set<Long> mKeyCombos;

  /* Adapter to display list of keys assigned to this preference. */
  private final ArrayAdapter<CharSequence> mKeyListAdapter;

  /* Name of the action having keys assigned to it. */
  private final String mActionName;

  /*  Action having keys assigned to it. */
  private final Action mActionToBeAssigned;

  /* Button to reset key adapter to empty */
  private Button mResetButton;

  /*
   * Text view containing the word "None" to display when no keys are assigned to avoid human
   * confusion.
   */
  private final TextView mEmptyKeysTextView;

  private final Context mContext;

  /**
   * Initializes a key assignment screen.
   *
   * @param context Activity context
   * @param actionToBeAssigned Action to which keys are currently being assigned
   * @param iterator Iterator for controlling setup flow
   */
  public SetupWizardConfigureSwitch(
      Context context, Action actionToBeAssigned, SetupWizardActivity.ScreenIterator iterator) {
    super(context, iterator);
    mContext = context;
    mActionToBeAssigned = actionToBeAssigned;
    mActionName = SwitchActionInformationUtils.getActionName(mContext, mActionToBeAssigned);

    ArrayList<CharSequence> keyAdapterList = new ArrayList<>();
    mKeyListAdapter =
        new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, keyAdapterList);
    mKeyCombos = KeyAssignmentUtils.getKeyCodesForPreference(context, mActionName);

    setInstructionText(actionToBeAssigned);
    KeyAssignmentUtils.updateKeyListAdapter(mKeyListAdapter, mKeyCombos, context);

    /* Sets the xml stub to visible for the first time. This by default inflates the stub. */
    ((ViewStub) findViewById(R.id.switch_access_setup_configure_switch_import))
        .setVisibility(VISIBLE);
    mEmptyKeysTextView = (TextView) findViewById(R.id.no_switch_assigned_text_view);
  }

  /**
   * Set the heading and subheading text to correspond to the preference being configured.
   *
   * @param action Action to which keys are currenlty being assigned
   */
  private void setInstructionText(Action action) {
    setHeadingText(SwitchActionInformationUtils.getHeading(mContext, action));
    setSubheadingText(SwitchActionInformationUtils.getSubheading(mContext, action));
  }

  /**
   * Initialize the ListView to show the adpater from the current key list. Also add the appropriate
   * action to the reset button.
   */
  @Override
  public void onStart() {
    super.onStart();
    setConfigViewVisible(true);

    ListView listView = (ListView) findViewById(R.id.assigned_switch_list);
    listView.setAdapter(mKeyListAdapter);

    mResetButton = (Button) findViewById(R.id.reset_button);
    mResetButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            mKeyCombos.clear();
            KeyAssignmentUtils.updateKeyListAdapter(mKeyListAdapter, mKeyCombos, getContext());
            updateViewForKeyAssignment();
          }
        });
    updateViewForKeyAssignment();

    String title =
        mContext.getString(
            R.string.setup_switch_assignment_heading,
            SwitchActionInformationUtils.getHeading(mContext, mActionToBeAssigned));
    ((TextView) findViewById(R.id.heading_text_view)).setText(title);
  }

  /** Take the updated key set and apply it to the preference that was being configured. */
  @Override
  public void onStop() {
    super.onStop();
    Set<String> longPrefStringSet = new HashSet<>(mKeyCombos.size());
    for (Long keyCombo : mKeyCombos) {
      longPrefStringSet.add(keyCombo.toString());
    }
    SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(getContext());
    SharedPreferences.Editor editor = sharedPreferences.edit();
    editor.putStringSet(mActionName, longPrefStringSet);
    editor.apply();

    setConfigViewVisible(false);
  }

  /**
   * Checks to see if any keys have been added to be assigned to the preference.
   *
   * @return {@code true} indicating if keys are currently set to be assigned
   */
  public boolean hasSwitchesAdded() {
    return !mKeyListAdapter.isEmpty();
  }

  private void setConfigViewVisible(boolean isVisible) {
    final RelativeLayout configView =
        (RelativeLayout) findViewById(R.id.switch_access_setup_configure_switch_inflated_import);
    configView.setVisibility(isVisible ? VISIBLE : GONE);
    configView.setFocusable(isVisible);
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
              getContext(), event, mKeyCombos, mActionName, mKeyListAdapter);

      if (keyPressResult == KeyAssignmentUtils.KEYCOMBO_ALREADY_ASSIGNED) {
        CharSequence toastText =
            getContext()
                .getString(
                    R.string.setup_toast_msg_key_already_assigned,
                    KeyAssignmentUtils.describeExtendedKeyCode(keyCombo, getContext()));
        Toast.makeText(getContext(), toastText, Toast.LENGTH_SHORT).show();
      } else {
        updateViewForKeyAssignment();
        /* If the event was ignored, return that the event was not consumed. */
        return keyPressResult != KeyAssignmentUtils.IGNORE_EVENT;
      }
    }
    return true;
  }

  private void updateViewForKeyAssignment() {
    mEmptyKeysTextView.setVisibility(hasSwitchesAdded() ? GONE : VISIBLE);
    mResetButton.setEnabled(hasSwitchesAdded());
  }

  @Override
  public int getNextScreen() {
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
     * Two buttons with option scanning:
     * <ul>
     * <li>Assign key to first group
     * <li>Assign key to second group
     * <li>Exit wizard
     * </ul>
     */
    switch (mActionToBeAssigned) {
      case AUTO_SCAN:
        return SetupWizardActivity.INDEX_STEP_SPEED_SCREEN;
      case NEXT:
        return SetupWizardActivity.INDEX_SELECT_KEY_SCREEN;
      case OPTION_ONE:
        return SetupWizardActivity.INDEX_OPTION_TWO_KEY_SCREEN;
      case SELECT:
      case OPTION_TWO:
        return SetupWizardActivity.INDEX_SWITCH_GAME_SCREEN;
      default:
        throw new IndexOutOfBoundsException("Action does not have a specified next screen");
    }
  }

  @Override
  public String getScreenName() {
    return SetupWizardConfigureSwitch.class.getSimpleName() + mActionToBeAssigned.getName();
  }
}

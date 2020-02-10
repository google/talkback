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

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.View.OnClickListener;
import android.widget.Button;
import com.google.android.accessibility.switchaccess.BuildConfig;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.ScreenViewListener;
import com.google.android.accessibility.switchaccess.SwitchAccessLogger;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceCache;
import com.google.android.accessibility.switchaccess.keyassignment.KeyAssignmentUtils;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessSetupScreenEnum.SetupScreen;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardConfigureSwitchFragment.Action;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardScanningMethodFragment.NumberOfSwitches;
import com.google.android.accessibility.switchaccess.setupwizard.bluetooth.BluetoothEventManager;
import java.util.ArrayDeque;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A short and simple wizard to configure a working one or two switch system. */
public class SetupWizardActivity extends AppCompatActivity {

  /* Keeps a history of previous fragment tags. */
  private ArrayDeque<SetupScreen> previousSetupScreens;

  private ScreenViewListener screenViewListener;

  private SetupScreenListener setupScreenListener;

  /* The currently displayed fragment. */
  private SetupWizardScreenFragment currentScreenFragment;

  /* The SetupScreen associated with the currently displayed fragment. */
  private SetupScreen currentSetupScreen;

  /* The Bluetooth event manager to pass to the {@link SetupWizardPairBluetoothFragment}. */
  private BluetoothEventManager bluetoothEventManager;

  /* The sdk version being used. This should only be changed during testing. */
  private static int sdkVersion = VERSION.SDK_INT;

  // Even though FragmentActivity#onActivityResult's Intent parameter is non-null the checker thinks
  // it should be @Nullable.
  @SuppressWarnings("nullness:override.param.invalid")
  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    /* Pass along to the current screen fragment so that fragments can change behavior based on
     * the result. This is needed so that a message can be shown if a request to enable Bluetooth
     * is denied. */
    currentScreenFragment.onActivityResult(requestCode, resultCode, data);
  }

  /**
   * Create the view animator which holds the setup wizard views. Populate it with the necessary
   * screens.
   *
   * @param savedInstanceState Saved state from prior execution
   */
  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // Hide the currently visible fragment. Otherwise, multiple fragments may be visible if the
    // activity is recreated in the middle of setup (e.g. in the dialog on the tic-tac-toe screen).
    // This needs to be called in #onCreate because the fragments are not hidden correctly in
    // #onStop or #onDestroy on an activity recreate.
    Fragment currentlyVisibleFragment =
        getSupportFragmentManager().findFragmentById(R.id.fragment_layout_container);
    if (currentlyVisibleFragment != null) {
      getSupportFragmentManager()
          .beginTransaction()
          .hide(currentlyVisibleFragment)
          .commitNowAllowingStateLoss();
    }

    previousSetupScreens = new ArrayDeque<>();
    setContentView(R.layout.switch_access_setup_layout);

    ActionBar actionBar = getSupportActionBar();
    // The Action Bar can be null during robolectric testing, so check.
    if (actionBar != null) {
      actionBar.setTitle(R.string.setup_wizard_heading);
    }

    warnUserIfSwitchesAssigned();
  }

  @Override
  public void onStart() {
    if (setupScreenListener == null) {
      SwitchAccessLogger logger = SwitchAccessLogger.getOrCreateInstance(this);
      screenViewListener = logger;
      setupScreenListener = logger;
      setButtonClickListener();

      // TODO: Display the switch type screen once Bluetooth pairing can be supported on
      // more versions of Android.
      /* Display the first screen in the setup wizard. */
      if ((sdkVersion >= VERSION_CODES.O) || BuildConfig.DEBUG) {
        displayScreen(SetupScreen.SWITCH_TYPE_SCREEN);
      } else {
        displayScreen(SetupScreen.NUMBER_OF_SWITCHES_SCREEN);
      }
    } else {
      setupScreenListener.onSetupScreenShown(currentSetupScreen);
    }
    super.onStart();
  }

  @Override
  public void onStop() {
    if (setupScreenListener != null) {
      setupScreenListener.onSetupScreenShown(SetupScreen.EXIT_SETUP);
    }
    super.onStop();
  }

  @Override
  public void onDestroy() {
    SwitchAccessLogger logger = SwitchAccessLogger.getInstanceIfExists();
    if (logger != null) {
      logger.stop(this);
    }

    SwitchAccessPreferenceCache.shutdownIfInitialized(this);
    super.onDestroy();
  }

  /* Sets the button click listener for the next and previous buttons shown for each view. */
  private void setButtonClickListener() {
    OnClickListener buttonListener =
        view -> {
          if (view.getId() == R.id.next_button) {
            displayNextScreenOrWarning();
          } else if (view.getId() == R.id.previous_button) {
            displayPreviousScreen();
          }
        };

    findViewById(R.id.previous_button).setOnClickListener(buttonListener);
    findViewById(R.id.next_button).setOnClickListener(buttonListener);
  }

  private void warnUserIfSwitchesAssigned() {
    if (KeyAssignmentUtils.areKeysAssigned(this)) {
      AlertDialog.Builder clearKeysBuilder =
          new AlertDialog.Builder(
              new ContextThemeWrapper(this, R.style.SetupGuideAlertDialogStyle));
      clearKeysBuilder.setTitle(getString(R.string.clear_keys_dialog_title));
      clearKeysBuilder.setMessage(getString(R.string.clear_keys_dialog_message));

      clearKeysBuilder.setPositiveButton(
          R.string.clear_keys_dialog_positive_button,
          (dialogInterface, viewId) -> {
            KeyAssignmentUtils.clearAllKeyPrefs(SetupWizardActivity.this);
            recreate();
            dialogInterface.cancel();
          });

      clearKeysBuilder.setNegativeButton(
          R.string.clear_keys_dialog_negative_button,
          (dialogInterface, viewId) -> dialogInterface.cancel());

      AlertDialog clearKeysDialog = clearKeysBuilder.create();
      clearKeysDialog.show();
    }
  }

  /*
   * Update the fragment manager to display the fragment that corresponds to the given SetupScreen.
   * Create the desired fragment if it doesn't already exist.
   */
  private void displayScreen(SetupScreen screen) {
    FragmentManager fragmentManager = getSupportFragmentManager();
    /* Hide the previous fragment. */
    if (currentScreenFragment != null) {
      /* In order to ensure that state information from the previous fragment has been saved, hide
       * the previous fragment with a separate fragment transaction than the one used to show the
       * new fragment. */
      fragmentManager
          .beginTransaction()
          .hide(currentScreenFragment)
          .setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out)
          .commitAllowingStateLoss();
      fragmentManager.executePendingTransactions();
    }

    SetupWizardScreenFragment fragment =
        (SetupWizardScreenFragment) fragmentManager.findFragmentByTag(screen.name());
    FragmentTransaction transaction = fragmentManager.beginTransaction();

    transaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);
    if (fragment == null) {
      /* Create a new fragment. */
      fragment = createScreenFragment(screen);
      if (currentScreenFragment != null) {
        transaction.add(R.id.fragment_layout_container, fragment, screen.name());
      } else {
        transaction.replace(R.id.fragment_layout_container, fragment, screen.name());
      }
    } else {
      /* The desired fragment has been previously created, so show it. */
      transaction.show(fragment);
    }

    /* Update the view to reflect the new screen. */
    transaction.commitAllowingStateLoss();
    currentScreenFragment = fragment;
    currentSetupScreen = screen;
    setNavigationButtonText(currentScreenFragment);

    if (screenViewListener != null) {
      screenViewListener.onScreenShown(currentScreenFragment.getScreenName());
    }

    if (setupScreenListener != null) {
      setupScreenListener.onSetupScreenShown(currentSetupScreen);
    }
  }

  private void displayNextScreenOrWarning() {
    /*
     * If we are currently on a switch configuration screen, display a dialog asking if the user
     * wishes to continue if they attempt to proceed to the next screen without assigning any
     * switches. This combats human error in completing the setup wizard.
     */
    if (currentScreenFragment instanceof SetupWizardConfigureSwitchFragment
        && !((SetupWizardConfigureSwitchFragment) currentScreenFragment).hasSwitchesAdded()) {
      AlertDialog.Builder noKeysBuilder =
          new AlertDialog.Builder(
              new ContextThemeWrapper(this, R.style.SetupGuideAlertDialogStyle));
      noKeysBuilder.setTitle(getString(R.string.setup_switch_assignment_nothing_assigned_title));
      noKeysBuilder.setMessage(
          getString(R.string.setup_switch_assignment_nothing_assigned_message));

      noKeysBuilder.setPositiveButton(
          android.R.string.ok,
          (dialogInterface, viewId) -> {
            displayNextScreen();
            dialogInterface.cancel();
          });

      noKeysBuilder.setNegativeButton(android.R.string.cancel, (dialog, viewId) -> dialog.cancel());

      AlertDialog noKeysDialog = noKeysBuilder.create();
      noKeysDialog.show();
    } else {
      displayNextScreen();
    }
  }

  /*
   * Finds and displays the next screen using the current screen index.
   */
  private void displayNextScreen() {
    SetupScreen nextScreen = currentScreenFragment.getNextScreen();
    if (nextScreen == SetupScreen.EXIT_SETUP) {
      finish();
      return;
    }

    if (nextScreen == SetupScreen.VIEW_NOT_CREATED) {
      // Wait until the fragment view has been created before getting the next screen.
      return;
    }

    previousSetupScreens.push(currentSetupScreen);
    displayScreen(nextScreen);
  }

  private void displayPreviousScreen() {
    if (previousSetupScreens.isEmpty()) {
      finish();
      return;
    }

    SetupScreen prevScreen = previousSetupScreens.pop();
    displayScreen(prevScreen);
  }

  /**
   * Sets the text on the bottom navigation buttons. "Previous" shown when the previous screen
   * exists, "Exit" otherwise. "Next" shown when the next screen exists, "Finish" otherwise.
   */
  private void setNavigationButtonText(SetupWizardScreenFragment screen) {
    /*
     * Don't check if screen.getNextScreen() is EXIT_SETUP here. If the view has
     * not yet been created for the fragment, getNextScreen() may throw errors.
     */
    if (screen instanceof SetupWizardCompletionScreenFragment) {
      /* Show the "Finish" button. */
      setButtonText(R.id.next_button, R.string.finish);
    } else {
      /* Show the "Next" button. */
      setButtonText(R.id.next_button, R.string.action_name_next);
    }
    if (previousSetupScreens.isEmpty()) {
      /* Show the "Exit" button. */
      setButtonText(R.id.previous_button, R.string.exit);
    } else {
      /* Show the "Previous" button. */
      setButtonText(R.id.previous_button, R.string.action_name_previous);
    }
  }

  private void setButtonText(int buttonId, int buttonTextId) {
    final Button button = findViewById(buttonId);
    button.setText(getApplicationContext().getString(buttonTextId));
  }

  /**
   * Get the view currently being displayed.
   *
   * @return SetupScreen active in the view animator
   */
  @VisibleForTesting
  public SetupWizardScreenFragment getCurrentScreen() {
    return currentScreenFragment;
  }

  /**
   * Sets the BluetoothEventManager to pass to the {@link SetupWizardPairBluetoothFragment} in
   * testing.
   */
  @VisibleForTesting
  public void setBluetoothEventManager(BluetoothEventManager bluetoothEventManager) {
    this.bluetoothEventManager = bluetoothEventManager;
  }

  /** Sets the ScreenViewListener to pass to the {@link ScreenViewListener} in testing. */
  @VisibleForTesting
  void setScreenViewListener(ScreenViewListener screenViewListener) {
    this.screenViewListener = screenViewListener;
  }

  /** Sets the SetupScreenListener to pass to the {@link SetupScreenListener} in testing. */
  @VisibleForTesting
  void setSetupScreenListener(SetupScreenListener setupScreenListener) {
    this.setupScreenListener = setupScreenListener;
  }

  @VisibleForTesting
  public static void setVersionSdk(int sdkVersion) {
    SetupWizardActivity.sdkVersion = sdkVersion;
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
      super.dispatchKeyEvent(event);
      return false;
    } else {
      /* Pass key event on to child screen */
      return currentScreenFragment.dispatchKeyEvent(event);
    }
  }

  /**
   * Creates a new fragment associated with the given setup screen.
   *
   * @param screen The fragment tag to get a fragment for
   * @return The fragment associated with the provided tag
   */
  // The returned fragment can only be null if screen is EXIT_SETUP or SCREEN_UNDEFINED. The former
  // cannot be reached because this method is only called in #displayScreen, and #displayScreen is
  // never called with screen == EXIT_SETUP. The latter cannot be reached because SCREEN_UNDEFINED
  // is never actually used.
  @SuppressWarnings("nullness:return.type.incompatible")
  private SetupWizardScreenFragment createScreenFragment(SetupScreen screen) {
    SetupWizardScreenFragment fragment = null;

    switch (screen) {
        /* Bluetooth- and usb-pairing prompting and screens, guarded by the {@link
         * FeatureFlags#bluetoothSetupWizard} feature flag. */
      case SWITCH_TYPE_SCREEN:
        fragment = new SetupWizardSwitchTypeFragment();
        break;
      case USB_DEVICE_LIST_SCREEN:
        fragment = new SetupWizardUsbDeviceListFragment();
        break;
      case PAIR_BLUETOOTH_SCREEN:
        fragment = new SetupWizardPairBluetoothFragment();
        if (bluetoothEventManager != null) {
          ((SetupWizardPairBluetoothFragment) fragment)
              .setBluetoothEventManager(bluetoothEventManager);
        }
        break;
        /* Initial switch preference screens. */
      case NUMBER_OF_SWITCHES_SCREEN:
        fragment = new SetupWizardNumberOfSwitchesFragment();
        break;
      case ONE_SWITCH_OPTION_SCREEN:
        fragment = new SetupWizardScanningMethodFragment();
        ((SetupWizardScanningMethodFragment) fragment).setNumberOfSwitches(NumberOfSwitches.ONE);
        break;
      case TWO_SWITCH_OPTION_SCREEN:
        fragment = new SetupWizardScanningMethodFragment();
        ((SetupWizardScanningMethodFragment) fragment).setNumberOfSwitches(NumberOfSwitches.TWO);
        break;
        /* Auto Scanning */
      case AUTO_SCAN_KEY_SCREEN:
        fragment = new SetupWizardConfigureSwitchFragment();
        ((SetupWizardConfigureSwitchFragment) fragment).setActionToBeAssigned(Action.AUTO_SCAN);
        break;
        /* Row Column Scanning */
      case NEXT_KEY_SCREEN:
        fragment = new SetupWizardConfigureSwitchFragment();
        ((SetupWizardConfigureSwitchFragment) fragment).setActionToBeAssigned(Action.NEXT);
        break;
      case SELECT_KEY_SCREEN:
        fragment = new SetupWizardConfigureSwitchFragment();
        ((SetupWizardConfigureSwitchFragment) fragment).setActionToBeAssigned(Action.SELECT);
        break;
        /* Group Selection */
      case GROUP_ONE_KEY_SCREEN:
        fragment = new SetupWizardConfigureSwitchFragment();
        ((SetupWizardConfigureSwitchFragment) fragment).setActionToBeAssigned(Action.GROUP_ONE);
        break;
      case GROUP_TWO_KEY_SCREEN:
        fragment = new SetupWizardConfigureSwitchFragment();
        ((SetupWizardConfigureSwitchFragment) fragment).setActionToBeAssigned(Action.GROUP_TWO);
        break;
        /* Wrap-up screens */
      case STEP_SPEED_SCREEN:
        fragment = new SetupWizardStepSpeedFragment();
        break;
      case SWITCH_GAME_INVALID_CONFIGURATION_SCREEN:
      case SWITCH_GAME_VALID_CONFIGURATION_SCREEN:
        fragment = new SetupWizardSwitchGameFragment();
        break;
      case COMPLETION_SCREEN:
        fragment = new SetupWizardCompletionScreenFragment();
        break;
        /* Exit tag. */
      case EXIT_SETUP:
        /* The fragment view hadn't been created yet when we attempted to get the next screen. We
         * should remain on the same setup screen until the view has been fully created. */
      case VIEW_NOT_CREATED:
        /* Unspecified screen tag. This should never be reached. */
      case SCREEN_UNDEFINED:
        break;
    }

    return fragment;
  }

  /** Interface that is notified when a setup screen is shown. */
  public interface SetupScreenListener {
    /**
     * Called when a setup screen is shown.
     *
     * @param setupScreen The setup screen that is shown
     */
    void onSetupScreenShown(SetupScreen setupScreen);
  }
}

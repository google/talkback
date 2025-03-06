/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.braille.brailledisplay.settings;

import static android.widget.Toast.LENGTH_LONG;
import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.Pair;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceClickListener;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.analytics.BrailleDisplayAnalytics;
import com.google.android.accessibility.braille.brailledisplay.controller.TranslatorManager;
import com.google.android.accessibility.braille.brailledisplay.controller.TranslatorManager.InputCodeChangedListener;
import com.google.android.accessibility.braille.brailledisplay.controller.TranslatorManager.OutputCodeChangedListener;
import com.google.android.accessibility.braille.brailledisplay.platform.ConnectibleDeviceInfo;
import com.google.android.accessibility.braille.brailledisplay.platform.Connectioneer;
import com.google.android.accessibility.braille.brailledisplay.platform.PersistentStorage;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.Utils;
import com.google.android.accessibility.braille.brailledisplay.settings.ConnectionDeviceActionButtonView.ActionButton;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.android.accessibility.braille.common.BrailleCommonTalkBackSpeaker;
import com.google.android.accessibility.braille.common.BraillePreferenceUtils;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.settings.BrailleLanguagesActivity;
import com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.material.MaterialComponentUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// LINT.IfChange(braille_display_settings)
/** Implements the user preferences UI for the Braille Display feature. */
@SuppressLint("ValidFragment")
public class BrailleDisplaySettingsFragment extends PreferenceFragmentCompat {
  private static final BrailleCharacter SHORTCUT_SWITCH_INPUT_CODE = new BrailleCharacter("2478");
  private static final BrailleCharacter SHORTCUT_SWITCH_OUTPUT_CODE = new BrailleCharacter("13578");
  private Connectioneer connectioneer;
  private Connectioneer.AspectEnablement aspectEnablement;
  private Connectioneer.AspectConnection aspectConnection;
  private Connectioneer.AspectDisplayProperties aspectDisplayProperties;

  private ComponentName controllingService;
  private Predicate<String> deviceNameFilter;

  private Preference bannerMessagePreference;
  private SwitchPreference enablerSwitch;

  private ProgressPreferenceCategory connectionPreferenceCategory;
  private Preference scanPreference;

  private SwitchPreference autoConnectPreference;

  private Preference preferredCodesPreference;
  private ListPreference currentActiveOutputCodePreference;
  private ListPreference currentActiveInputCodePreference;
  private Preference brailleGradePreference;
  private Preference keyBindingsPreference;

  private TranslatorManager translatorManager;
  private boolean systemPermissionDialogIsShowable = false;
  private boolean scanning = false;
  private final Set<ConnectableDevice> scannedDevicesCache = new HashSet<>();

  public BrailleDisplaySettingsFragment(
      ComponentName controllingService, Predicate<String> deviceNameFilter) {
    super();
    this.controllingService = controllingService;
    this.deviceNameFilter = deviceNameFilter;
  }

  @Override
  public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
    getPreferenceManager().setSharedPreferencesName(BRAILLE_SHARED_PREFS_FILENAME);
    PreferenceSettingsUtils.addPreferencesFromResource(this, R.xml.bd_preferences);
    getPreferenceManager()
        .getSharedPreferences()
        .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);

    bannerMessagePreference = findPreference(getString(R.string.pref_key_bd_banner));

    enablerSwitch = findPreference(getString(R.string.pref_key_bd_enabler));
    enablerSwitch.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          PersistentStorage.setConnectionEnabled(getContext(), (Boolean) newValue);
          onModelChanged();
          return false;
        });

    scanPreference = findPreference(getString(R.string.pref_key_bd_rescan));
    scanPreference.setOnPreferenceClickListener(
        new OnPreferenceClickListener() {
          @Override
          public boolean onPreferenceClick(Preference preference) {
            aspectConnection.onUserSelectedRescan();
            onModelChanged();
            return true;
          }
        });

    connectionPreferenceCategory =
        findPreference(getString(R.string.pref_key_bd_connection_category));

    preferredCodesPreference =
        findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_brailleime_translator_codes_preferred));
    preferredCodesPreference.setIntent(new Intent(getContext(), BrailleLanguagesActivity.class));

    currentActiveOutputCodePreference = findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_bd_output_code));
    currentActiveInputCodePreference =
        findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_brailleime_translator_code));
    brailleGradePreference = findPreference(getString(com.google.android.accessibility.braille.common.R.string.pref_braille_contracted_mode));
    brailleGradePreference.setIntent(new Intent(getContext(), BrailleGradeActivity.class));

    keyBindingsPreference = findPreference(getString(R.string.pref_key_bindings_key));
    keyBindingsPreference.setIntent(new Intent(getContext(), KeyBindingsActivity.class));

    autoConnectPreference = findPreference(getString(R.string.pref_key_bd_auto_connect));
    autoConnectPreference.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          PersistentStorage.setAutoConnect(getContext(), (Boolean) newValue);
          onModelChanged();
          return true;
        });

    Preference brailleElementPreference =
        findPreference(getString(R.string.pref_key_braille_elements));
    brailleElementPreference.setIntent(new Intent(getContext(), BrailleElementsActivity.class));

    Preference autoScrollPreference = findPreference(getString(R.string.pref_key_bd_auto_scroll));
    autoScrollPreference.setIntent(new Intent(getContext(), AutoScrollActivity.class));

    Preference advanceSettingsPreference = findPreference(getString(R.string.pref_key_bd_advanced));
    advanceSettingsPreference.setIntent(new Intent(getContext(), AdvancedSettingsActivity.class));

    connectioneer =
        Connectioneer.getInstance(
            new Connectioneer.CreationArguments(
                getContext().getApplicationContext(), deviceNameFilter));

    translatorManager = new TranslatorManager(getContext());

    showPermissionsDialogIfNecessary();

    populateFromPersistentStorage();
  }

  @Override
  public void onResume() {
    super.onResume();
    aspectEnablement = connectioneer.aspectEnablement.attach(enablementCallback);
    aspectConnection = connectioneer.aspectConnection.attach(connectionCallback);
    aspectDisplayProperties = connectioneer.aspectDisplayProperties.attach(displayPropertyCallback);
    aspectConnection.onSettingsEntered();
    translatorManager.addOnInputTablesChangedListener(onInputCodeChangedListener);
    translatorManager.addOnOutputTablesChangedListener(outputCodeChangedListener);
    onModelChanged();
  }

  @Override
  public void onPause() {
    super.onPause();
    connectioneer.aspectEnablement.detach(enablementCallback);
    connectioneer.aspectConnection.detach(connectionCallback);
    connectioneer.aspectDisplayProperties.detach(displayPropertyCallback);
    translatorManager.removeOnOutputTablesChangedListener(outputCodeChangedListener);
    translatorManager.removeOnInputTablesChangedListener(onInputCodeChangedListener);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    getPreferenceManager()
        .getSharedPreferences()
        .unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
  }

  private void constructBannerPreference(
      Preference bannerMessagePreference, boolean serviceEnabled) {
    String bannerMessage = "";
    String bannerButtonText = "";
    OnPreferenceClickListener bannerButtonClickListener = null;
    if (!serviceEnabled) {
      bannerMessage =
          getResources().getString(R.string.bd_preferences_banner_talkback_off_subtitle);
      bannerButtonText =
          getResources().getString(R.string.bd_preferences_banner_talkback_off_button);
      bannerButtonClickListener =
          v -> {
            Utils.launchAccessibilitySettings(getContext(), controllingService);
            return false;
          };
    } else if (!getNeededAppLevelPermissions().isEmpty()) {
      String applicationName = getResources().getString(R.string.bd_application_name);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        bannerMessage =
            getResources()
                .getString(
                    R.string.bd_preferences_banner_nearby_devices_permission_not_granted_subtitle,
                    applicationName);
      } else {
        bannerMessage =
            getResources()
                .getString(
                    R.string.bd_preferences_banner_location_permission_not_granted_subtitle,
                    applicationName);
      }
      if (systemPermissionDialogIsShowable) {
        bannerButtonText =
            getResources()
                .getString(
                    R.string.bd_preferences_banner_permission_not_granted_system_dialog_button);
        bannerButtonClickListener =
            preference -> {
              requestPermissionLauncher.launch(
                  getNeededAppLevelPermissions().stream().toArray(String[]::new));
              return false;
            };
      } else {
        bannerButtonText =
            getResources().getString(R.string.bd_preferences_banner_permission_not_granted_button);
        bannerButtonClickListener =
            v -> {
              Utils.launchAppDetailsActivity(getContext(), getActivity().getPackageName());
              return false;
            };
      }
    } else if (!aspectConnection.isBluetoothOn()) {
      bannerMessage =
          getResources().getString(R.string.bd_preferences_banner_bluetooth_off_subtitle);
      bannerButtonText =
          getResources().getString(R.string.bd_preferences_banner_bluetooth_off_button);
      bannerButtonClickListener =
          v -> {
            startRequestEnableBluetoothActivity(getActivity());
            return false;
          };
    } else if (isGlobalLocationRequiredAndNotEnabled()) {
      bannerMessage =
          getResources()
              .getString(
                  R.string.bd_preferences_banner_location_settings_not_enabled_subtitle,
                  getResources().getString(R.string.bd_application_name));
      bannerButtonText =
          getResources()
              .getString(R.string.bd_preferences_banner_location_settings_not_enabled_button);
      bannerButtonClickListener =
          v -> {
            Utils.launchLocationSettingsActivity(getContext());
            return false;
          };
    }

    if (!TextUtils.isEmpty(bannerMessage)) {
      bannerMessagePreference.setTitle(bannerMessage);
      bannerMessagePreference.setSummary(bannerButtonText);
      bannerMessagePreference.setOnPreferenceClickListener(bannerButtonClickListener);
      bannerMessagePreference.setVisible(true);
    } else {
      bannerMessagePreference.setVisible(false);
    }
  }

  private static void startRequestEnableBluetoothActivity(Activity activity) {
    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    activity.startActivity(intent);
  }

  private void populateFromPersistentStorage() {
    autoConnectPreference.setChecked(PersistentStorage.isAutoConnect(getContext()));
  }

  // Invoke this method whenever the model changes, to refresh the preferences info.
  private void onModelChanged() {
    boolean isServiceEnabled = aspectEnablement.isServiceEnabled();
    boolean isConnectionPossible =
        isServiceEnabled && PersistentStorage.isConnectionEnabled(getContext());

    // Banner preference.
    constructBannerPreference(bannerMessagePreference, isServiceEnabled);

    // Main switch
    enablerSwitch.setEnabled(isServiceEnabled);
    enablerSwitch.setChecked(PersistentStorage.isConnectionEnabled(getContext()));

    // Rescan
    boolean currentScanning = false;
    String scanPreferenceSummary = "";
    if (isConnectionPossible) {
      currentScanning = aspectConnection.isScanning();
    }
    scanPreference.setEnabled(
        aspectConnection.isBluetoothOn() && isConnectionPossible && !currentScanning);
    scanPreference.setVisible(!connectioneer.aspectConnection.useUsbConnection());
    scanPreference.setTitle(
        currentScanning
            ? R.string.bd_preference_scan_activated_title
            : R.string.bd_preference_scan_inactivated_title);
    scanPreference.setSummary(scanPreferenceSummary);
    connectionPreferenceCategory.setProgressActive(currentScanning);
    if (!currentScanning && scanning) {
      // Show toast when no devices found during the scanning cycle.
      if (scannedDevicesCache.isEmpty() && !aspectConnection.isConnectingOrConnected()) {
        Toast.makeText(getContext(), getString(R.string.bd_no_devices_found), LENGTH_LONG).show();
      }
    }
    scanning = currentScanning;

    // Device list
    if (isConnectionPossible) {
      Pair<List<ConnectibleDeviceInfo>, Boolean> devicesPair = buildDevicesPair();
      List<ConnectibleDeviceInfo> deviceInfosNew = devicesPair.first;
      boolean isStructurePreserved = devicesPair.second;

      if (isStructurePreserved) {
        getDevicePreferenceList().stream()
            .forEach(
                devicePreference ->
                    devicePreference.updateViews(deviceInfosNew.get(devicePreference.index)));
      } else {
        removeAllDevicePreferencesFromConnectionCategory();
        for (ListIterator<ConnectibleDeviceInfo> listIterator = deviceInfosNew.listIterator();
            listIterator.hasNext(); ) {
          int index = listIterator.nextIndex();
          ConnectibleDeviceInfo rowDevice = listIterator.next();
          DevicePreference devicePreference = new DevicePreference(getContext(), index, rowDevice);
          devicePreference.setKey(rowDevice.deviceAddress);
          connectionPreferenceCategory.addPreference(devicePreference);
        }
      }
    } else {
      removeAllDevicePreferencesFromConnectionCategory();
    }

    // Key bindings
    BrailleDisplayProperties displayProperties = aspectDisplayProperties.getDisplayProperties();
    Intent intent = keyBindingsPreference.getIntent();
    intent.putExtra(KeyBindingsActivity.PROPERTY_KEY, displayProperties);
    keyBindingsPreference.setIntent(intent);

    // Preferred braille codes
    BraillePreferenceUtils.setupPreferredCodePreference(
        getContext(),
        preferredCodesPreference,
        (preference, newValue) -> {
          onModelChanged();
          return false;
        });

    // Current active braille output code.
    // TODO: Gets back auto option.
    if (currentActiveOutputCodePreference != null) {
      BraillePreferenceUtils.setupLanguageListPreference(
          getContext(),
          currentActiveOutputCodePreference,
          BrailleUserPreferences::readCurrentActiveOutputCodeAndCorrect,
          BrailleUserPreferences::writeCurrentActiveOutputCode,
          (preference, newValue) -> {
            if (BrailleUserPreferences.readShowSwitchBrailleDisplayOutputCodeGestureTip(
                getContext())) {
              showSwitchOutputCodeGestureTipDialog();
            }
            return false;
          });
    }
    // Current active braille input code.
    if (currentActiveInputCodePreference != null) {
      BraillePreferenceUtils.setupLanguageListPreference(
          getContext(),
          currentActiveInputCodePreference,
          BrailleUserPreferences::readCurrentActiveInputCodeAndCorrect,
          BrailleUserPreferences::writeCurrentActiveInputCode,
          (preference, newValue) -> {
            if (BrailleUserPreferences.readShowSwitchBrailleDisplayInputCodeGestureTip(
                getContext())) {
              showSwitchInputCodeGestureTipDialog();
            }
            return false;
          });
    }

    // Braille grade
    updateBrailleGradeSummary();
  }

  private void showSwitchInputCodeGestureTipDialog() {
    BraillePreferenceUtils.createTipAlertDialog(
            getContext(),
            getString(R.string.bd_switch_input_code_gesture_tip_dialog_title),
            getString(
                R.string.bd_switch_input_code_gesture_tip_dialog_message,
                BrailleTranslateUtils.getDotsText(getResources(), SHORTCUT_SWITCH_INPUT_CODE)),
            BrailleUserPreferences::writeShowSwitchBrailleDisplayInputCodeGestureTip)
        .show();
  }

  private void showSwitchOutputCodeGestureTipDialog() {
    BraillePreferenceUtils.createTipAlertDialog(
            getContext(),
            getString(R.string.bd_switch_output_code_gesture_tip_dialog_title),
            getString(
                R.string.bd_switch_output_code_gesture_tip_dialog_message,
                BrailleTranslateUtils.getDotsText(getResources(), SHORTCUT_SWITCH_OUTPUT_CODE)),
            BrailleUserPreferences::writeShowSwitchBrailleDisplayOutputCodeGestureTip)
        .show();
  }

  // Returns a pair whose first member is a freshly figured list of DeviceInfo and whose second
  // member is a boolean indicating if the structure of the newly figured list of DeviceInfo matches
  // structurally the current list of device preferences.
  private Pair<List<ConnectibleDeviceInfo>, Boolean> buildDevicesPair() {
    List<ConnectibleDeviceInfo> rowDevices = new ArrayList<>();

    List<Pair<String, String>> rememberedDevices =
        PersistentStorage.getRememberedDevices(getContext());
    List<ConnectableDevice> scannedDevices = aspectConnection.getScannedDevicesCopy();

    for (Pair<String, String> rememberedDevice : rememberedDevices) {
      // Search for a scanned device that matches the rememberedDevice.  If found, add to the top
      // of the list we are building, and remove it from the local copy of the list of scanned
      // devices to avoid creating duplicated entries for it in the list we are building.
      // In what follows, device.getName() may result in null (in case the device is stale), so NPE
      // is avoided by invoking .equals() on rememberedDeviceName, which is non-null.
      Optional<ConnectableDevice> twinOptional =
          scannedDevices.stream()
              .filter(device -> rememberedDevice.second.equals(device.address()))
              .findFirst();
      if (twinOptional.isPresent()) {
        ConnectableDevice twinDevice = twinOptional.get();
        rowDevices.add(createInRangeDevice(twinDevice, /* isRemembered= */ true));
        scannedDevices.remove(twinDevice);
      } else if (!connectioneer.aspectConnection.useUsbConnection()) {
        ConnectibleDeviceInfo info =
            createOutOfRangeRememberedDevice(rememberedDevice.first, rememberedDevice.second);
        if (info != null) {
          rowDevices.add(info);
        }
      }
    }

    // Now dump the remaining scanned devices into the list we are building.
    rowDevices.addAll(
        scannedDevices.stream()
            .map(device -> createInRangeDevice(device, /* isRemembered= */ false))
            .collect(Collectors.toList()));

    // Variable isStructurePreserved is true if the newly built list of DeviceInfo has the same
    // length as the list of RowDevice pulled from the existing preferences, and the elements of
    // those two lists have the same deviceNames in the same order.
    boolean isStructurePreserved =
        getDevicePreferenceList().stream()
            .map(pref -> pref.rowDevice)
            .collect(Collectors.toList())
            .equals(rowDevices.stream().collect(Collectors.toList()));

    return new Pair<>(rowDevices, isStructurePreserved);
  }

  private ConnectibleDeviceInfo createInRangeDevice(
      ConnectableDevice device, boolean isRemembered) {
    boolean isConnecting = aspectConnection.isConnectingTo(device.address());
    boolean isConnected = aspectConnection.isConnectedTo(device.address());
    return new ConnectibleDeviceInfo(
        device.name(), device.address(), isRemembered, isConnecting, isConnected, device);
  }

  private @Nullable ConnectibleDeviceInfo createOutOfRangeRememberedDevice(
      String deviceName, String deviceAddress) {
    boolean isConnecting = aspectConnection.isConnectingTo(deviceAddress);
    boolean isConnected = aspectConnection.isConnectedTo(deviceAddress);
    Set<BluetoothDevice> devices = BluetoothAdapter.getDefaultAdapter().getBondedDevices();
    for (BluetoothDevice device : devices) {
      if (device.getAddress().equals(deviceAddress)) {
        return new ConnectibleDeviceInfo(
            deviceName, deviceAddress, true, isConnecting, isConnected, null);
      }
    }
    return null;
  }

  private List<DevicePreference> getDevicePreferenceList() {
    List<DevicePreference> result = new ArrayList<>();
    for (int i = 0; i < connectionPreferenceCategory.getPreferenceCount(); i++) {
      Preference preference = connectionPreferenceCategory.getPreference(i);
      if (preference instanceof DevicePreference) {
        result.add((DevicePreference) preference);
      }
    }
    return result;
  }

  private void removeAllDevicePreferencesFromConnectionCategory() {
    for (DevicePreference devicePreference : getDevicePreferenceList()) {
      connectionPreferenceCategory.removePreference(devicePreference);
    }
  }

  private class DevicePreference extends Preference {
    private final int index;
    private ConnectibleDeviceInfo rowDevice;
    private AlertDialog deviceDetailDialog;

    public DevicePreference(Context context, int index, ConnectibleDeviceInfo rowDevice) {
      super(context);
      this.index = index;
      setWidgetLayoutResource(R.layout.listitem_bt_device);
      this.rowDevice = rowDevice;
    }

    @Override
    public void onAttached() {
      super.onAttached();
      updateViewsInternal();
    }

    @Override
    protected void onClick() {
      super.onClick();
      deviceDetailDialog =
          MaterialComponentUtils.alertDialogBuilder(getContext())
              .setTitle(rowDevice.deviceName)
              .setView(new ConnectionDeviceActionButtonView(getContext(), createActionButtons()))
              .create();
      deviceDetailDialog.show();
    }

    @Override
    public void onPrepareForRemoval() {
      super.onPrepareForRemoval();
      dismissConnectionDeviceDetailDialog();
    }

    private List<ActionButton> createActionButtons() {
      List<ActionButton> actionButtons = new ArrayList<>();
      if (rowDevice.isConnectingOrConnected()) {
        actionButtons.add(
            new ActionButton(
                getString(R.string.bd_preference_device_item_button_disconnect),
                v -> {
                  onUserSelectedDisconnectFromDevice(rowDevice.deviceAddress);
                  dismissConnectionDeviceDetailDialog();
                }));
      } else if (rowDevice.hasConnectableDevice()) {
        actionButtons.add(
            new ActionButton(
                getString(R.string.bd_preference_device_item_button_connect),
                v -> {
                  onUserSelectedConnectDevice(rowDevice.device);
                  dismissConnectionDeviceDetailDialog();
                }));
      }
      if (rowDevice.isRemembered) {
        actionButtons.add(
            new ActionButton(
                getString(R.string.bd_preference_device_item_button_forget),
                v -> {
                  onUserSelectedForgetDevice(rowDevice.device);
                  dismissConnectionDeviceDetailDialog();
                }));
      }
      actionButtons.add(
          new ActionButton(
              getString(android.R.string.cancel), v -> dismissConnectionDeviceDetailDialog()));

      return actionButtons;
    }

    private void dismissConnectionDeviceDetailDialog() {
      if (deviceDetailDialog != null && deviceDetailDialog.isShowing()) {
        deviceDetailDialog.dismiss();
      }
    }

    private void updateViewsInternal() {
      setTitle(rowDevice.deviceName);
      boolean enabled = true;
      if (rowDevice.isConnected) {
        setSummary(R.string.bd_preference_device_item_summary_connected);
      } else if (rowDevice.isConnecting) {
        setSummary(R.string.bd_preference_device_item_summary_connecting);
      } else if (rowDevice.hasConnectableDevice()) {
        if (rowDevice.isRemembered) {
          setSummary(R.string.bd_preference_device_item_summary_saved_and_available);
        } else {
          setSummary(R.string.bd_preference_device_item_summary_available);
        }
      } else {
        setSummary(R.string.bd_preference_device_item_summary_saved_out_of_range);
        enabled = rowDevice.isRemembered;
      }
      setEnabled(enabled);
    }

    private void updateViews(ConnectibleDeviceInfo rowDevice) {
      this.rowDevice = rowDevice;
      updateViewsInternal();
    }

    private void onUserSelectedConnectDevice(ConnectableDevice device) {
      aspectConnection.onUserChoseConnectDevice(device);
      onModelChanged();
    }

    private void onUserSelectedDisconnectFromDevice(String deviceAddress) {
      aspectConnection.onUserChoseDisconnectFromDevice(deviceAddress);
      onModelChanged();
    }

    private void onUserSelectedForgetDevice(ConnectableDevice device) {
      PersistentStorage.deleteRememberedDevice(getContext(), device.address());
      aspectConnection.onUserChoseForgetDevice(device);
      onModelChanged();
    }
  }

  private List<String> getNeededAppLevelPermissions() {
    List<String> permissions = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      permissions.add(Manifest.permission.BLUETOOTH_SCAN);
      permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
    } else {
      permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
    }
    return permissions.stream()
        .filter(
            perm ->
                ContextCompat.checkSelfPermission(getContext(), perm)
                    == PackageManager.PERMISSION_DENIED)
        .collect(Collectors.toList());
  }

  private void showPermissionsDialogIfNecessary() {
    requestPermissionLauncher.launch(
        getNeededAppLevelPermissions().stream().toArray(String[]::new));
  }

  private boolean isGlobalLocationRequiredAndNotEnabled() {
    // Only Q and R need the global location setting enabled; we're not sure where this is
    // documented, but our testing revealed this.
    return BuildVersionUtils.isAtLeastQ()
        && !BuildVersionUtils.isAtLeastS()
        && !Utils.isGlobalLocationSettingEnabled(getContext());
  }

  private void updateBrailleGradeSummary() {
    brailleGradePreference.setSummary(
        getString(
            BrailleUserPreferences.readContractedMode(getContext())
                ? R.string.bd_preference_braille_contracted
                : R.string.bd_preference_braille_uncontracted));
  }

  private void showTroubleshootingDialog(String deviceName) {
    MaterialComponentUtils.alertDialogBuilder(getContext())
        .setTitle(
            TextUtils.isEmpty(deviceName)
                ? getContext().getString(R.string.bd_bt_connect_fail_dialog_title_without_name)
                : getContext()
                    .getString(R.string.bd_bt_connect_fail_dialog_title_with_name, deviceName))
        .setMessage(R.string.bd_bt_connect_fail_dialog_message)
        .setPositiveButton(
            R.string.bd_bt_connect_fail_positive_button, (dialog, which) -> launchHelpCenter())
        .setNegativeButton(R.string.bd_bt_connect_fail_negative_button, null)
        .create()
        .show();
  }

  private void launchHelpCenter() {
    // Open Help Center page.
  }

  private final ActivityResultLauncher<String[]> requestPermissionLauncher =
      registerForActivityResult(
          new RequestMultiplePermissions(),
          result -> {
            systemPermissionDialogIsShowable = false;
            for (Entry<String, Boolean> entry : result.entrySet()) {
              if (!entry.getValue() && shouldShowRequestPermissionRationale(entry.getKey())) {
                systemPermissionDialogIsShowable = true;
                break;
              }
            }
          });

  private final Connectioneer.AspectEnablement.Callback enablementCallback =
      new Connectioneer.AspectEnablement.Callback() {
        @Override
        public void onEnablementChange(
            boolean controllingServiceEnabled, boolean connectionEnabledByUser) {
          onModelChanged();
        }
      };

  private final Connectioneer.AspectConnection.Callback connectionCallback =
      new Connectioneer.AspectConnection.Callback() {
        @Override
        public void onScanningChanged() {
          onModelChanged();
        }

        @Override
        public void onDeviceListCleared() {
          scannedDevicesCache.clear();
          onModelChanged();
        }

        @Override
        public void onConnectHidStarted() {}

        @Override
        public void onConnectRfcommStarted() {}

        @Override
        public void onConnectableDeviceSeenOrUpdated(ConnectableDevice device) {
          // Inform the user of a newly seen device, if it is not remembered.
          if (!scannedDevicesCache.contains(device)
              && !PersistentStorage.getRememberedDevices(getContext()).stream()
                  .anyMatch(pair -> pair.second.equals(device.address()))) {
            BrailleCommonTalkBackSpeaker.getInstance()
                .speak(getString(R.string.bd_new_device_found_announcement));
          }
          // Cache scanned devices for finding new added device next time.
          scannedDevicesCache.clear();
          scannedDevicesCache.addAll(aspectConnection.getScannedDevicesCopy());
          onModelChanged();
        }

        @Override
        public void onConnectableDeviceDeleted(ConnectableDevice device) {
          onModelChanged();
        }

        @Override
        public void onConnectionStatusChanged(ConnectStatus status, ConnectableDevice device) {
          onModelChanged();
        }

        @Override
        public void onConnectFailed(boolean manual, @Nullable String deviceName) {
          onModelChanged();
          if (!manual) {
            return;
          }
          showTroubleshootingDialog(deviceName);
        }
      };

  private final Connectioneer.AspectDisplayProperties.Callback displayPropertyCallback =
      new Connectioneer.AspectDisplayProperties.Callback() {
        @Override
        public void onDisplayPropertiesArrived(BrailleDisplayProperties brailleDisplayProperties) {
          onModelChanged();
        }
      };

  private final InputCodeChangedListener onInputCodeChangedListener =
      new InputCodeChangedListener() {
        @Override
        public void onInputCodeChanged() {
          onModelChanged();
        }
      };

  private final OutputCodeChangedListener outputCodeChangedListener =
      new OutputCodeChangedListener() {
        @Override
        public void onOutputCodeChanged() {
          onModelChanged();
        }
      };

  private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          if (key.equals(getString(com.google.android.accessibility.braille.common.R.string.pref_bd_output_code))) {
            BrailleDisplayAnalytics.getInstance(getContext())
                .logBrailleOutputCodeSetting(
                    BrailleUserPreferences.readCurrentActiveOutputCodeAndCorrect(getContext()),
                    BrailleUserPreferences.readContractedMode(getContext()));
          } else if (key.equals(getString(com.google.android.accessibility.braille.common.R.string.pref_brailleime_translator_code))) {
            BrailleDisplayAnalytics.getInstance(getContext())
                .logBrailleInputCodeSetting(
                    BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(getContext()),
                    BrailleUserPreferences.readContractedMode(getContext()));
          } else if (key.equals(getString(R.string.pref_key_bd_auto_connect))) {
            BrailleDisplayAnalytics.getInstance(getContext())
                .logAutoConnectSetting(PersistentStorage.isAutoConnect(getContext()));
          } else if (key.equals(getString(R.string.pref_key_bd_enabler))) {
            BrailleDisplayAnalytics.getInstance(getContext())
                .logEnablerSetting(PersistentStorage.isConnectionEnabled(getContext()));
          } else if (key.equals(getString(com.google.android.accessibility.braille.common.R.string.pref_braille_contracted_mode))) {
            updateBrailleGradeSummary();
          }
        }
      };
}
// LINT.ThenChange(//depot/google3/java/com/google/android/accessibility/braille/brailledisplay/impl/src/com/google/android/accessibility/braille/brailledisplay/settings/BrailleDisplaySettingsFragment.java:braille_display_settings)

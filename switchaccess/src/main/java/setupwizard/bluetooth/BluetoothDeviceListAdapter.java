/*
 * Copyright (C) 2018 Google Inc.
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
package com.google.android.accessibility.switchaccess.setupwizard.bluetooth;

import android.Manifest.permission;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothClass.Device;
import android.bluetooth.BluetoothClass.Device.Major;
import android.bluetooth.BluetoothDevice;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.android.accessibility.switchaccess.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

/** {@link RecyclerView.Adapter} for displaying Bluetooth devices in a list. */
public class BluetoothDeviceListAdapter
    extends RecyclerView.Adapter<BluetoothDeviceListAdapter.ViewHolder> {

  private final HashSet<ComparableBluetoothDevice> bluetoothDevices = new HashSet<>();
  private ArrayList<ComparableBluetoothDevice> orderedBluetoothDevices = new ArrayList<>();

  /* Holds the content descriptions for the different types of Bluetooth devices, set by the
   * application initializing BluetoothDeviceListAdapter.
   */
  private final BluetoothDeviceDescriptionSet descriptionManager;

  private final int layoutViewId;
  private final int deviceNameViewId;
  private final int deviceIconViewId;

  /**
   * @param layoutViewId the id of the layout that should be used
   * @param deviceNameId the id of the view that will hold the device name; this id must correspond
   *     to a {@link TextView} inside the view corresponding to layoutViewId
   * @param deviceIconViewId the id of the view that will hold the device icon; this id must
   *     correspond to an {@link ImageView} inside of the view corresponding to layoutViewId
   */
  public BluetoothDeviceListAdapter(
      BluetoothDeviceDescriptionSet descriptionManager,
      int layoutViewId,
      int deviceNameId,
      int deviceIconViewId) {
    this.descriptionManager = descriptionManager;
    this.layoutViewId = layoutViewId;
    this.deviceNameViewId = deviceNameId;
    this.deviceIconViewId = deviceIconViewId;
  }

  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parentView, int viewType) {
    LayoutInflater layoutInflater = LayoutInflater.from(parentView.getContext());
    View bluetoothItemLayout = layoutInflater.inflate(layoutViewId, parentView, false);
    return new ViewHolder(bluetoothItemLayout);
  }

  @RequiresPermission(allOf = {permission.BLUETOOTH_ADMIN, permission.BLUETOOTH})
  @Override
  public void onBindViewHolder(ViewHolder viewHolder, int position) {
    final ComparableBluetoothDevice bluetoothDevice = getItem(position);
    viewHolder.setDeviceName(bluetoothDevice.getName());
    viewHolder.setDeviceIconResource(bluetoothDevice);
    viewHolder.updateConnectionDescriptionText(bluetoothDevice);
  }

  @Override
  public int getItemCount() {
    return bluetoothDevices.size();
  }

  /**
   * Adds a bluetooth device to the adapter list and updates the view.
   *
   * @param bluetoothDevice the bluetooth device to be added to the adapter
   */
  public void add(ComparableBluetoothDevice bluetoothDevice) {
    if (bluetoothDevices.add(bluetoothDevice)) {
      updateInternalDataAndNotifyDataSetChanged();
    } else {
      ComparableBluetoothDevice retrievedBluetoothDevice =
          getItem(getItemPosition(bluetoothDevice));
      /* Update the device if the name or Bluetooth class has changed. */
      if (retrievedBluetoothDevice.getName() != bluetoothDevice.getName()
          || retrievedBluetoothDevice.getBluetoothClass() != bluetoothDevice.getBluetoothClass()) {
        updateBluetoothDevice(bluetoothDevice);
      }
    }
  }

  /**
   * Removes a bluetooth device from the adapter list and updates the view.
   *
   * @param bluetoothDevice the bluetooth device to be removed from the view
   */
  public void remove(ComparableBluetoothDevice bluetoothDevice) {
    bluetoothDevices.remove(bluetoothDevice);
    updateInternalDataAndNotifyDataSetChanged();
  }

  /** Clears all devices from the adapter. */
  public void reset() {
    bluetoothDevices.clear();
    updateInternalDataAndNotifyDataSetChanged();
  }

  /**
   * Updates the Bluetooth device if it exists in this device list adapter. Use when some property
   * of the Bluetooth device has changed that requires the view to update, such as {@link
   * ComparableBluetoothDevice.BluetoothConnectionState}.
   *
   * @param bluetoothDevice the Bluetooth device that should be updated
   */
  public void updateBluetoothDevice(ComparableBluetoothDevice bluetoothDevice) {
    if (bluetoothDevices.remove(bluetoothDevice)) {
      bluetoothDevices.add(bluetoothDevice);
      updateInternalDataAndNotifyDataSetChanged();
    }
  }

  /**
   * Returns a device's position in the adapter's dataset.
   *
   * @param bluetoothDevice the bluetooth device for which to find the position in the adapter
   * @return the position of the bluetooth device in the adapter
   */
  @VisibleForTesting
  int getItemPosition(ComparableBluetoothDevice bluetoothDevice) {
    return orderedBluetoothDevices.indexOf(bluetoothDevice);
  }

  private ComparableBluetoothDevice getItem(int position) {
    return orderedBluetoothDevices.get(position);
  }

  private void updateInternalDataAndNotifyDataSetChanged() {
    orderedBluetoothDevices = new ArrayList<>(bluetoothDevices);
    Collections.sort(orderedBluetoothDevices);
    notifyDataSetChanged();
  }

  private class BluetoothClickListener implements OnClickListener {
    private final ViewHolder viewHolder;

    BluetoothClickListener(ViewHolder viewHolder) {
      this.viewHolder = viewHolder;
    }

    /**
     * When a view is clicked, attempt to pair the bluetooth device at that position with the phone.
     *
     * @param view the view containing the selected bluetooth device
     */
    @RequiresPermission(allOf = {permission.BLUETOOTH_ADMIN, permission.BLUETOOTH})
    @Override
    public void onClick(View view) {
      ComparableBluetoothDevice clickedDevice = getItem(viewHolder.getAdapterPosition());
      if (clickedDevice.getBondState() == BluetoothDevice.BOND_NONE) {
        clickedDevice.startPairing();
      } else {
        clickedDevice.reconnect(
            descriptionManager.reconnectingUnsupportedTitle(),
            descriptionManager.reconnectingUnsupportedMessage(),
            descriptionManager.launchBluetoothSettingsButtonText());
      }

      viewHolder.updateConnectionDescriptionText(clickedDevice);
    }
  }

  /** Custom view holder class for displaying the bluetooth device items in the recycler view. */
  @VisibleForTesting
  class ViewHolder extends RecyclerView.ViewHolder {
    private final TextView deviceNameView;
    private final ImageView iconView;
    private final TextView connectionDescriptionView;

    // The BluetoothClickListener constructor expects a fully initialized ViewHolder. We could
    // annotate it to accept something @UnderInitialization, but then BluetoothClickListener would
    // be considered @UnderInitialization and we can't annotate the View#setOnClickListener method
    // to accept that, so this method would throw a null checker error anyway.
    @SuppressWarnings("initialization:argument.type.incompatible")
    public ViewHolder(View view) {
      super(view);
      deviceNameView = view.findViewById(deviceNameViewId);
      iconView = view.findViewById(deviceIconViewId);
      connectionDescriptionView = view.findViewById(R.id.bluetooth_connection_description);
      view.setOnClickListener(new BluetoothClickListener(this));
    }

    void setDeviceName(String deviceName) {
      this.deviceNameView.setText(deviceName);
    }

    @RequiresPermission(allOf = {permission.BLUETOOTH_ADMIN, permission.BLUETOOTH})
    private void setDeviceIconResource(ComparableBluetoothDevice bluetoothDevice) {
      int imageResource;
      String description;
      /* Some bluetooth classes used to determine icon are inaccessible from outside of Android.
       * Because of this, the icons displayed here may not match the Android settings page exactly.
       */
      BluetoothClass bluetoothClass = bluetoothDevice.getBluetoothClass();
      /* Show the default Bluetooth icon if the Bluetooth class is null. */
      int majorDeviceClass =
          (bluetoothClass == null) ? Major.MISC : bluetoothClass.getMajorDeviceClass();
      switch (majorDeviceClass) {
          case Major.COMPUTER:
            imageResource = R.drawable.ic_bluetooth_computer;
            description = descriptionManager.computerContentDescription();
            break;
          case Major.PHONE:
            imageResource = R.drawable.ic_bluetooth_phone;
            description = descriptionManager.phoneContentDescription();
            break;
          case Major.PERIPHERAL:
            imageResource = R.drawable.ic_bluetooth_peripheral;
            description = descriptionManager.peripheralContentDescription();
            break;
          case Major.IMAGING:
            imageResource = R.drawable.ic_bluetooth_imaging;
            description = descriptionManager.imagingContentDescription();
            break;
          case Major.AUDIO_VIDEO:
          // We can't get into this case if bluetoothClass is null because majorDeviceClass would
          // be Major.MISC not Major.AUDIO_VIDEO. deviceClass is a separate statement to allow
          // the warning suppression here rather than on the whole method.
          @SuppressWarnings("nullness:dereference.of.nullable")
          int deviceClass = bluetoothClass.getDeviceClass();
          if (deviceClass == Device.AUDIO_VIDEO_HEADPHONES) {
              imageResource = R.drawable.ic_bluetooth_headphone;
              description = descriptionManager.headphoneContentDescription();
              break;
            }
            // Fall-through
          default:
            imageResource = R.drawable.ic_bluetooth_default;
            description = descriptionManager.defaultBluetoothDeviceContentDescription();
        }

        iconView.setImageResource(imageResource);
        iconView.setContentDescription(description);
    }

    private void hideConnectionDescriptionText() {
      connectionDescriptionView.setVisibility(View.GONE);
    }

    private void updateConnectionDescriptionText(ComparableBluetoothDevice bluetoothDevice) {
      switch (bluetoothDevice.getConnectionState()) {
        case CONNECTED:
          connectionDescriptionView.setText(descriptionManager.connectedDescription());
          break;
        case CONNECTING:
          connectionDescriptionView.setText(descriptionManager.connectingDescription());
          break;
        case UNAVAILABLE:
          connectionDescriptionView.setText(descriptionManager.unavailableDeviceDescription());
          break;
        case UNKNOWN:
          hideConnectionDescriptionText();
          return;
      }
      connectionDescriptionView.setVisibility(View.VISIBLE);
    }
  }
}

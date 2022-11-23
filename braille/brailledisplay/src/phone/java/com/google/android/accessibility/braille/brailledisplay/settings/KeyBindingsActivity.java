/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.settings;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplay;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.BrailleKeyBindingUtils.SupportedCommand;
import com.google.android.accessibility.braille.brailledisplay.platform.BrailleDisplayManager.RemoteDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.Connectioneer;
import com.google.android.accessibility.braille.brailledisplay.platform.Connectioneer.AspectConnection;
import com.google.android.accessibility.braille.brailledisplay.platform.Connectioneer.CreationArguments;
import com.google.android.accessibility.braille.brltty.BrailleDisplayProperties;
import com.google.android.accessibility.braille.brltty.BrailleKeyBinding;
import com.google.android.accessibility.utils.PreferencesActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Shows key bindings for the currently connected Braille display. */
public class KeyBindingsActivity extends PreferencesActivity {
  public static final String PROPERTY_KEY = "property_key";
  private static final String TAG = "KeyBindingsActivity";

  @Override
  protected PreferenceFragmentCompat createPreferenceFragment() {
    return new KeyBindingsFragment();
  }

  @Override
  protected String getFragmentTag() {
    return TAG;
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent); // Store the new intent so getIntent() will return the new intent.
    FragmentManager fragmentManager = getSupportFragmentManager();
    KeyBindingsFragment keyBindingsFragment =
        (KeyBindingsFragment) fragmentManager.findFragmentByTag(TAG);
    BrailleDisplayProperties props = getIntent().getParcelableExtra(PROPERTY_KEY);
    keyBindingsFragment.refreshAdapter(props);
  }

  /** Fragment that holds the key binding preference. */
  public static class KeyBindingsFragment extends PreferenceFragmentCompat {
    private RecyclerView list;
    private Connectioneer connectioneer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      connectioneer =
          Connectioneer.getInstance(
              new CreationArguments(
                  getContext().getApplicationContext(),
                  BrailleDisplay.ENCODER_FACTORY.getDeviceNameFilter()));
    }

    @Override
    public void onResume() {
      super.onResume();
      connectioneer.aspectDisplayProperties.attach(displayPropertyCallback);
      connectioneer.aspectConnection.attach(connectionCallback);
    }

    @Override
    public void onPause() {
      super.onPause();
      connectioneer.aspectDisplayProperties.detach(displayPropertyCallback);
      connectioneer.aspectConnection.detach(connectionCallback);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {}

    @Override
    public View onCreateView(
        LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
      View content = inflater.inflate(R.layout.key_bindings, container, false);
      list = content.findViewById(R.id.list);
      refreshAdapter(getActivity().getIntent().getParcelableExtra(PROPERTY_KEY));
      return content;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
      switch (item.getItemId()) {
        case android.R.id.home:
          Intent intent = new Intent(getContext(), BrailleDisplaySettingsActivity.class);
          intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
          startActivity(intent);
          return true;
        default:
          return super.onOptionsItemSelected(item);
      }
    }

    private void refreshAdapter(BrailleDisplayProperties props) {
      ArrayList<KeyBinding> result = new ArrayList<>();
      // Use default KeyBindings when display properties is null.
      if (props == null) {
        BrailleDisplayLog.v(TAG, "no property");
        for (SupportedCommand supportedCommand :
            BrailleKeyBindingUtils.getSupportedCommands(getContext())) {
          result.add(
              new KeyBinding(
                  supportedCommand.getCommandDescription(getResources()),
                  supportedCommand.getKeyDescription(getResources())));
        }
      } else {
        ArrayList<BrailleKeyBinding> sortedBindings =
            BrailleKeyBindingUtils.getSortedBindingsForDisplay(props);
        Map<String, String> friendlyKeyNames = props.getFriendlyKeyNames();
        for (SupportedCommand supportedCommand :
            BrailleKeyBindingUtils.getSupportedCommands(getContext())) {
          BrailleKeyBinding binding =
              BrailleKeyBindingUtils.getBrailleKeyBindingForCommand(
                  supportedCommand.getCommand(), sortedBindings);
          if (binding == null) {
            // No supported binding for this display. That's normal.
            continue;
          }
          result.add(
              createKeyBinding(
                  binding,
                  supportedCommand.getCommandDescription(getResources()),
                  friendlyKeyNames));
        }
      }
      KeyBindingsAdapter adapter = new KeyBindingsAdapter(getContext(), result);
      list.setLayoutManager(new LinearLayoutManager(getContext()));
      list.setAdapter(adapter);
    }

    private KeyBinding createKeyBinding(
        BrailleKeyBinding binding,
        String commandDescription,
        Map<String, String> friendlyKeyNames) {
      String keys =
          BrailleKeyBindingUtils.getFriendlyKeyNamesForCommand(
              binding, friendlyKeyNames, getContext());
      return new KeyBinding(commandDescription, keys);
    }

    private final Connectioneer.AspectDisplayProperties.Callback displayPropertyCallback =
        new Connectioneer.AspectDisplayProperties.Callback() {
          @Override
          public void onDisplayPropertiesArrived(
              BrailleDisplayProperties brailleDisplayProperties) {
            refreshAdapter(brailleDisplayProperties);
          }
        };

    private final Connectioneer.AspectConnection.Callback connectionCallback =
        new AspectConnection.Callback() {
          @Override
          public void onScanningChanged() {}

          @Override
          public void onDeviceListCleared() {}

          @Override
          public void onConnectStarted() {}

          @Override
          public void onConnectableDeviceSeenOrUpdated(BluetoothDevice bluetoothDevice) {}

          @Override
          public void onConnectionStatusChanged(boolean connected, RemoteDevice remoteDevice) {
            if (!connected) {
              refreshAdapter(null);
            }
          }

          @Override
          public void onConnectFailed(@Nullable String deviceName) {}
        };

    private static class KeyBindingsAdapter extends Adapter<KeyBindingViewHolder> {
      private final Context context;
      private final List<KeyBinding> keyBindings;

      public KeyBindingsAdapter(Context context, List<KeyBinding> keyBindings) {
        this.context = context;
        this.keyBindings = keyBindings;
      }

      @Override
      public KeyBindingViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(viewGroup.getContext());
        final View view = inflater.inflate(android.R.layout.simple_list_item_2, viewGroup, false);
        return new KeyBindingViewHolder(view);
      }

      @Override
      public void onBindViewHolder(KeyBindingViewHolder viewHolder, int position) {
        viewHolder.label.setText(keyBindings.get(position).label);
        viewHolder.binding.setText(keyBindings.get(position).binding);
        viewHolder.binding.setTextColor(context.getColor(R.color.settings_secondary_text));
      }

      @Override
      public int getItemCount() {
        return keyBindings.size();
      }
    }

    private static class KeyBindingViewHolder extends ViewHolder {
      private final TextView label;
      private final TextView binding;

      public KeyBindingViewHolder(View itemView) {
        super(itemView);
        this.label = itemView.findViewById(android.R.id.text1);
        this.binding = itemView.findViewById(android.R.id.text2);
      }
    }
  }

  private static class KeyBinding {
    private final String label;
    private final String binding;

    public KeyBinding(String label, String binding) {
      this.label = label;
      this.binding = binding;
    }

    @Override
    public String toString() {
      return label;
    }
  }
}

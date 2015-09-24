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

package com.android.utils.compat.bluetooth;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothProfile;

@TargetApi(11)
class BluetoothProfileCompatHoneycomb {

    interface ServiceListenerBridge {
        public void onServiceConnected(int profile, Object proxy);

        public void onServiceDisconnected(int profile);
    }

    private BluetoothProfileCompatHoneycomb() {
        // This class is non-instantiable.
    }

    public static Object newServiceListener(
            final ServiceListenerBridge bridge) {
        return new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                bridge.onServiceConnected(profile, proxy);
            }

            @Override
            public void onServiceDisconnected(int profile) {
                bridge.onServiceDisconnected(profile);
            }
        };
    }
}

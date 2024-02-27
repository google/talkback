/*
 * Copyright (C) 2015 Google Inc.
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

/*
 * Bluetooth functionality that works on the Android NDK.
 */

#ifndef JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_BRLTTY_JNI_BRLTTYWRAPPER_BLUETOOTH_ANDROID_H_
#define JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_BRLTTY_JNI_BRLTTYWRAPPER_BLUETOOTH_ANDROID_H_

#include <unistd.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct BluetoothAndroidConnectionStruct BluetoothAndroidConnection;

struct BluetoothAndroidConnectionStruct {
  /* This should be a file descriptor in non-blocking mode that can be read
   * to read more data from the bluetooth connection.
   */
  int read_fd;
  /* Arbitrary client-owned data. */
  void *data;
  /* Scale facto for polling timeout. */
  float timeoutFactor;
  /* Function that is used to write data to the bluetooth connection
   * with the usual posix semanics.
   */
  ssize_t (*writeData)(BluetoothAndroidConnection *conn,
                       const void* buffer,
                       size_t size);
};

/*
 * Store a connection struct that will be used when a bluetooth
 * connection is 'opened' by the brltty driver.  This is global
 * state: there can be only one connection at a time.
 */
void bluetoothAndroidSetConnection(
    BluetoothAndroidConnection* conn);

#ifdef __cplusplus
}
#endif /* __cplusplus */
#endif  // JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_BRLTTY_JNI_BRLTTYWRAPPER_BLUETOOTH_ANDROID_H_
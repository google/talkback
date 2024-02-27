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
 *
 * This class mentions a lot of bluetooth stuff, but its real purpose is to
 * override BRLTTY's existing bluetooth code so that data packets can be
 * forwarded to the Android application layer which handles the cross-device
 * packet traffic.
 *
 * TODO: clears this class to make it only be a role of forwarding.
 */

#include "third_party/brltty/Headers/prologue.h"  // NOLINT Must include first

#include "bluetooth_android.h"

#include <string.h>
#include <errno.h>
#include <jni.h>

#include "third_party/brltty/Headers/io_bluetooth.h"
#include "third_party/brltty/Headers/io_misc.h"
#include "third_party/brltty/Headers/log.h"
#include "third_party/brltty/Headers/system_java.h"
#include "third_party/brltty/Programs/bluetooth_internal.h"
#include "third_party/brltty/Headers/async_handle.h"
#include "third_party/brltty/Headers/async_io.h"

static BluetoothAndroidConnection* globalConnection = NULL;
static jclass connectionClass = NULL;

struct BluetoothConnectionExtensionStruct {
  BluetoothAndroidConnection *connection;
  AsyncHandle inputMonitor;
};

void
bluetoothAndroidSetConnection(BluetoothAndroidConnection* conn) {
  globalConnection = conn;
}

static int bthGetConnectionClass(JNIEnv *env) {
  return findJavaClass(
      env, &connectionClass,
      "com/google/android/accessibility/braille/brltty/BluetoothConnection");
}

//////////////////////////////////////////////////////////////////////
// Implementation of system-specific bluetooth functions required
// by brltty I/O functions.
//////////////////////////////////////////////////////////////////////
BluetoothConnectionExtension *
bthNewConnectionExtension (uint64_t bda) {
  BluetoothConnectionExtension *bcx = NULL;

  if (!globalConnection) {
    logMessage(LOG_ERR, "Opening bluetooth without an andorid bluetooth "
               "conection");
    goto out;
  }
  if ((bcx = malloc(sizeof(*bcx))) == NULL) {
    logMessage(LOG_ERR, "Can't allocate android bluetooth extension struct");
    goto out;
  }
  bcx->connection = globalConnection;
out:
  return bcx;
}

static void
bthCancelInputMonitor (BluetoothConnectionExtension *bcx) {
  if (bcx->inputMonitor) {
    asyncCancelRequest(bcx->inputMonitor);
    bcx->inputMonitor = NULL;
  }
}

void
bthReleaseConnectionExtension (BluetoothConnectionExtension *bcx) {
  if (bcx->connection != globalConnection) {
    logMessage(LOG_ERR, "Android bluetooth closed after a new connection "
               "was established");
  }
  free(bcx);
}

int
bthMonitorInput (BluetoothConnection *connection,
    AsyncMonitorCallback *callback, void *data) {
  BluetoothConnectionExtension *bcx = connection->extension;
  BluetoothAndroidConnection *conn = bcx->connection;

  bthCancelInputMonitor(bcx);
  if (!callback) return 1;
  return asyncMonitorFileInput(&bcx->inputMonitor, conn->read_fd, callback,
    data);
}

int
bthPollInput (BluetoothConnectionExtension *bcx,
    int timeout) {
  BluetoothAndroidConnection *conn = bcx->connection;
  return awaitFileInput(conn->read_fd, (int) (timeout * conn->timeoutFactor));
}

ssize_t
bthGetData (BluetoothConnectionExtension *bcx, void *buffer, size_t size,
    int initialTimeout, int subsequentTimeout) {
  BluetoothAndroidConnection *conn = bcx->connection;
  return readFile(conn->read_fd, buffer, size, initialTimeout,
                  subsequentTimeout);
}

ssize_t
bthPutData (BluetoothConnectionExtension *bcx,
    const void *buffer, size_t size) {
  BluetoothAndroidConnection *conn = bcx->connection;
  return (*conn->writeData)(conn, buffer, size);
}

int
bthOpenChannel (BluetoothConnectionExtension *bcx, uint8_t channel,
    int timeout) {
  /* We already opened the handle when creating the Bluetooth connection. */
  return 1;
}

int
bthDiscoverChannel (uint8_t *channel, BluetoothConnectionExtension *bcx,
    const void *uuidBytes, size_t uuidLength, int timeout) {
  *channel = 0;
  return 1;
}

char *bthObtainDeviceName(uint64_t bda, int timeout) {
  char *name = NULL;
  JNIEnv *env = getJavaNativeInterface();

  if (env) {
    if (bthGetConnectionClass(env)) {
      static jmethodID method = 0;

      if (findJavaStaticMethod(env, &method, connectionClass, "getName",
                               JAVA_SIG_METHOD(JAVA_SIG_STRING,
                                               JAVA_SIG_LONG  // address
                                               ))) {
        jstring jName =
            (*env)->CallStaticObjectMethod(env, connectionClass, method, bda);

        if (jName) {
          const char *cName = (*env)->GetStringUTFChars(env, jName, NULL);
          if (cName) {
            if (!(name = strdup(cName))) logMallocError();
            (*env)->ReleaseStringUTFChars(env, jName, cName);
          } else {
            logMallocError();
          }

          (*env)->DeleteLocalRef(env, jName);
        } else {
          logMallocError();
        }
      }
    }
  }

  return name;
}

static jmethodID getPairedDeviceCountMethod = 0;
static jmethodID getPairedDeviceAddressMethod = 0;
static jmethodID getPairedDeviceNameMethod = 0;

static int
bthGetPairedDeviceCountMethod (JNIEnv *env) {
  return findJavaStaticMethod(
    env, &getPairedDeviceCountMethod, connectionClass, "getPairedDeviceCount",
    JAVA_SIG_METHOD(JAVA_SIG_INT,
    )
  );
}

static int
bthGetPairedDeviceAddressMethod (JNIEnv *env) {
  return findJavaStaticMethod(
    env, &getPairedDeviceAddressMethod, connectionClass, "getPairedDeviceAddress",
    JAVA_SIG_METHOD(JAVA_SIG_STRING,
      JAVA_SIG_INT // index
    )
  );
}

static int
bthGetPairedDeviceNameMethod (JNIEnv *env) {
  return findJavaStaticMethod(
    env, &getPairedDeviceNameMethod, connectionClass, "getPairedDeviceName",
    JAVA_SIG_METHOD(JAVA_SIG_STRING,
      JAVA_SIG_INT // index
    )
  );
}

static JNIEnv *
bthGetPairedDeviceMethods (void) {
  JNIEnv *env = getJavaNativeInterface();

  if (env) {
    if (bthGetConnectionClass(env)) {
      if (bthGetPairedDeviceCountMethod(env)) {
        if (bthGetPairedDeviceAddressMethod(env)) {
          if (bthGetPairedDeviceNameMethod(env)) {
            return env;
          }
        }
      }
    }
  }

  return NULL;
}

void
bthProcessDiscoveredDevices (
  DiscoveredBluetoothDeviceTester *testDevice, void *data
) {
  JNIEnv *env = bthGetPairedDeviceMethods();

  if (env) {
    jint count = (*env)->CallStaticIntMethod(env, connectionClass, getPairedDeviceCountMethod);

    jint index=0;
    for (; index<count; index+=1) {
      jstring jAddress = (*env)->CallStaticObjectMethod(env, connectionClass, getPairedDeviceAddressMethod, index);

      if (jAddress) {
        const char *cAddress = (*env)->GetStringUTFChars(env, jAddress, NULL);

        if (cAddress) {
          uint64_t address;

          if (bthParseAddress(&address, cAddress)) {
            jstring jName = (*env)->CallStaticObjectMethod(env, connectionClass, getPairedDeviceNameMethod, index);
            const char *cName = jName? (*env)->GetStringUTFChars(env, jName, NULL): NULL;

            DiscoveredBluetoothDevice device = {
              .address = address,
              .name = cName,
              .paired = 1
            };

            int found = testDevice(&device, data);
            if (cName) (*env)->ReleaseStringUTFChars(env, jName, cName);
            (*env)->ReleaseStringUTFChars(env, jAddress, cAddress);
            if (found) break;
          }
        }
      }
    }
  }
}

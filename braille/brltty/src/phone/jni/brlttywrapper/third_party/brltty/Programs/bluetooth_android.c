/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2023 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.app/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

#include "prologue.h"

#include <string.h>
#include <errno.h>

#include "log.h"
#include "io_bluetooth.h"
#include "bluetooth_internal.h"
#include "async_handle.h"
#include "async_io.h"
#include "io_misc.h"
#include "thread.h"
#include "system_java.h"

static jclass connectionClass = NULL;
static jmethodID connectionConstructor = 0;
static jmethodID canDiscoverMethod = 0;
static jmethodID openMethod = 0;
static jmethodID closeMethod = 0;
static jmethodID writeMethod = 0;

static int
bthGetConnectionClass (JNIEnv *env) {
  return findJavaClass(
    env, &connectionClass, JAVA_OBJ_BRLTTY("BluetoothConnection")
  );
}

static int
bthGetConnectionConstructor (JNIEnv *env) {
  return findJavaConstructor(
    env, &connectionConstructor, connectionClass,
    JAVA_SIG_CONSTRUCTOR(
      JAVA_SIG_LONG // address
    )
  );
}

static int
bthGetCanDiscoverMethod (JNIEnv *env) {
  return findJavaInstanceMethod(
    env, &canDiscoverMethod, connectionClass, "canDiscover",
    JAVA_SIG_METHOD(JAVA_SIG_BOOLEAN,
    )
  );
}

static int
bthGetOpenMethod (JNIEnv *env) {
  return findJavaInstanceMethod(
    env, &openMethod, connectionClass, "open",
    JAVA_SIG_METHOD(JAVA_SIG_BOOLEAN,
      JAVA_SIG_INT // inputPipe
      JAVA_SIG_INT // channel
      JAVA_SIG_BOOLEAN // secure
    )
  );
}

static int
bthGetCloseMethod (JNIEnv *env) {
  return findJavaInstanceMethod(
    env, &closeMethod, connectionClass, "close",
    JAVA_SIG_METHOD(JAVA_SIG_VOID,
    )
  );
}

static int
bthGetWriteMethod (JNIEnv *env) {
  return (findJavaInstanceMethod(
    env, &writeMethod, connectionClass, "write",
    JAVA_SIG_METHOD(JAVA_SIG_BOOLEAN,
      JAVA_SIG_ARRAY(JAVA_SIG_BYTE)) // bytes
    )
  );
}

struct BluetoothConnectionExtensionStruct {
  JNIEnv *env;

  jobject connection;
  AsyncHandle inputMonitor;
  int inputPipe[2];
};

BluetoothConnectionExtension *
bthNewConnectionExtension (uint64_t bda) {
  BluetoothConnectionExtension *bcx;

  if ((bcx = malloc(sizeof(*bcx)))) {
    memset(bcx, 0, sizeof(*bcx));

    bcx->inputPipe[0] = INVALID_FILE_DESCRIPTOR;
    bcx->inputPipe[1] = INVALID_FILE_DESCRIPTOR;

    if ((bcx->env = getJavaNativeInterface())) {
      if (bthGetConnectionClass(bcx->env)) {
        if (bthGetConnectionConstructor(bcx->env)) {
          jobject localReference = (*bcx->env)->NewObject(bcx->env, connectionClass, connectionConstructor, bda);

          if (!clearJavaException(bcx->env, 1)) {
            jobject globalReference = (*bcx->env)->NewGlobalRef(bcx->env, localReference);

            (*bcx->env)->DeleteLocalRef(bcx->env, localReference);
            localReference = NULL;

            if (globalReference) {
              bcx->connection = globalReference;
              return bcx;
            } else {
              logMallocError();
              clearJavaException(bcx->env, 0);
            }
          }
        }
      }
    }

    free(bcx);
  } else {
    logMallocError();
  }

  return NULL;
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
  bthCancelInputMonitor(bcx);

  if (bcx->connection) {
    if (bthGetCloseMethod(bcx->env)) {
      (*bcx->env)->CallVoidMethod(bcx->env, bcx->connection, closeMethod);
    }

    (*bcx->env)->DeleteGlobalRef(bcx->env, bcx->connection);
    clearJavaException(bcx->env, 1);
  }

  closeFile(&bcx->inputPipe[0]);
  closeFile(&bcx->inputPipe[1]);

  free(bcx);
}

typedef struct {
  BluetoothConnectionExtension *const bcx;
  uint8_t const channel;
  int const timeout;

  int error;
} OpenBluetoothConnectionData;

THREAD_FUNCTION(runOpenBluetoothConnection) {
  OpenBluetoothConnectionData *obc = argument;
  JNIEnv *env;

  if ((env = getJavaNativeInterface())) {
    if (pipe(obc->bcx->inputPipe) != -1) {
      if (setBlockingIo(obc->bcx->inputPipe[0], 0)) {
        if (bthGetOpenMethod(env)) {
          jboolean result = (*env)->CallBooleanMethod(env, obc->bcx->connection, openMethod,
                                                      obc->bcx->inputPipe[1], obc->channel, JNI_FALSE);

          if (!clearJavaException(env, 1)) {
            if (result == JNI_TRUE) {
              closeFile(&obc->bcx->inputPipe[1]);
              obc->error = 0;
              goto done;
            }
          }

          errno = EIO;
        }
      }

      closeFile(&obc->bcx->inputPipe[0]);
      closeFile(&obc->bcx->inputPipe[1]);
    } else {
      logSystemError("pipe");
    }
  }

  obc->error = errno;
done:
  return NULL;
}

int
bthOpenChannel (BluetoothConnectionExtension *bcx, uint8_t channel, int timeout) {
  OpenBluetoothConnectionData obc = {
    .bcx = bcx,
    .channel = channel,
    .timeout = timeout,

    .error = EIO
  };

  if (callThreadFunction("bluetooth-open", runOpenBluetoothConnection, &obc, NULL)) {
    if (!obc.error) return 1;
    errno = obc.error;
  }

  return 0;
}

int
bthDiscoverChannel (
  uint8_t *channel, BluetoothConnectionExtension *bcx,
  const void *uuidBytes, size_t uuidLength,
  int timeout
) {
  JNIEnv *env = bcx->env;

  if (bthGetCanDiscoverMethod(env)) {
    jboolean result = (*env)->CallBooleanMethod(env, bcx->connection, canDiscoverMethod);

    if (!clearJavaException(env, 1)) {
      int yes = result == JNI_TRUE;

      if (yes) {
        logMessage(LOG_CATEGORY(BLUETOOTH_IO), "can discover serial port channel");
        *channel = 0;
      } else {
        errno = ENOENT;
      }

      return yes;
    }
  }

  errno = EIO;
  return 0;
}

int
bthMonitorInput (BluetoothConnection *connection, AsyncMonitorCallback *callback, void *data) {
  BluetoothConnectionExtension *bcx = connection->extension;

  bthCancelInputMonitor(bcx);
  if (!callback) return 1;
  return asyncMonitorFileInput(&bcx->inputMonitor, bcx->inputPipe[0], callback, data);
}

int
bthPollInput (BluetoothConnectionExtension *bcx, int timeout) {
  return awaitFileInput(bcx->inputPipe[0], timeout);
}

ssize_t
bthGetData (
  BluetoothConnectionExtension *bcx, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  return readFile(bcx->inputPipe[0], buffer, size, initialTimeout, subsequentTimeout);
}

ssize_t
bthPutData (BluetoothConnectionExtension *bcx, const void *buffer, size_t size) {
  if (bthGetWriteMethod(bcx->env)) {
    jbyteArray bytes = (*bcx->env)->NewByteArray(bcx->env, size);

    if (bytes) {
      jboolean result;

      (*bcx->env)->SetByteArrayRegion(bcx->env, bytes, 0, size, buffer);
      result = (*bcx->env)->CallBooleanMethod(bcx->env, bcx->connection, writeMethod, bytes);
      (*bcx->env)->DeleteLocalRef(bcx->env, bytes);

      if (!clearJavaException(bcx->env, 1)) {
        if (result == JNI_TRUE) {
          return size;
        }
      }

      errno = EIO;
    } else {
      errno = ENOMEM;
    }
  } else {
    errno = ENOSYS;
  }

  logSystemError("Bluetooth write");
  return -1;
}

char *
bthObtainDeviceName (uint64_t bda, int timeout) {
  char *name = NULL;
  JNIEnv *env = getJavaNativeInterface();

  if (env) {
    if (bthGetConnectionClass(env)) {
      static jmethodID method = 0;

      if (findJavaStaticMethod(env, &method, connectionClass, "getName",
                               JAVA_SIG_METHOD(JAVA_SIG_STRING,
                                               JAVA_SIG_LONG // address
                                              ))) {
        jstring jName = (*env)->CallStaticObjectMethod(env, connectionClass, method, bda);

        if (jName) {
          const char *cName = (*env)->GetStringUTFChars(env, jName, NULL);

          if (cName) {
            if (!(name = strdup(cName))) logMallocError();
            (*env)->ReleaseStringUTFChars(env, jName, cName);
          } else {
            logMallocError();
            clearJavaException(env, 0);
          }

          (*env)->DeleteLocalRef(env, jName);
        } else {
          logMallocError();
          clearJavaException(env, 0);
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

    for (jint index=0; index<count; index+=1) {
      int found = 0;
      jstring jAddress = (*env)->CallStaticObjectMethod(env, connectionClass, getPairedDeviceAddressMethod, index);

      if (jAddress) {
        const char *cAddress = (*env)->GetStringUTFChars(env, jAddress, NULL);

        if (cAddress) {
          uint64_t address;

          if (bthParseAddress(&address, cAddress)) {
            jstring jName = (*env)->CallStaticObjectMethod(env, connectionClass, getPairedDeviceNameMethod, index);
            const char *cName = jName? (*env)->GetStringUTFChars(env, jName, NULL): NULL;

            const DiscoveredBluetoothDevice device = {
              .address = address,
              .name = cName,
              .paired = 1
            };

            if (testDevice(&device, data)) found = 1;
            if (cName) (*env)->ReleaseStringUTFChars(env, jName, cName);
            if (jName) (*env)->DeleteLocalRef(env, jName);
          }

          (*env)->ReleaseStringUTFChars(env, jAddress, cAddress);
        }

        (*env)->DeleteLocalRef(env, jAddress);
      }

      if (found) break;
    }
  }
}

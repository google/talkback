/*
 * Copyright (C) 2024 Google Inc.
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
#include <stdlib.h>
#include <string.h>

#include "common_java.h"
#include "hid_types.h"
#include "log.h"
#include "system_java.h"
#include "hid_internal.h"

struct HidHandleStruct {};

static jclass hidHelperClass = NULL;

static int hidFindHelperClass(JNIEnv *env) {
  return findJavaClass(env, &hidHelperClass, JAVA_OBJ_BRLTTY("HidHelper");
}

static void hidAndroidDestroyHandle(HidHandle *handle) { free(handle); }

static const HidItemsDescriptor *hidAndroidGetItems(HidHandle *handle) {
  JNIEnv *env = getJavaNativeInterface();
  if (hidFindHelperClass(env)) {
    static jmethodID getReportDescriptorMethod = NULL;
    if (getReportDescriptorMethod == NULL) {
      if (!findJavaStaticMethod(
              env, &getReportDescriptorMethod, hidHelperClass,
              "getReportDescriptor",
              JAVA_SIG_METHOD(JAVA_SIG_ARRAY(JAVA_SIG_BYTE), ))) {
        logMessage(LOG_ERR, "getReportDescriptorMethod not found");
        return NULL;
      }
    }
    jbyteArray reportDescriptor = (jbyteArray)(*env)->CallStaticObjectMethod(
        env, hidHelperClass, getReportDescriptorMethod);
    int size = (*env)->GetArrayLength(env, reportDescriptor);
    HidItemsDescriptor *items;
    if ((items = malloc(sizeof(*items) + size))) {
      memset(items, 0, sizeof(*items));
      items->count = size;
      (*env)->GetByteArrayRegion(env, reportDescriptor, 0, size, items->bytes);
      return items;
    } else {
      logMallocError();
    }
  }
  return NULL;
}

static const char *getGenericHIDDeviceName(HidHandle *handle) {
  // Model name is not used to control driver behavior, so always provide "HID".
  return "HID";
}

static int hidAndroidWriteData(HidHandle *handle, const unsigned char *data,
                               size_t size) {
  JNIEnv *env = getJavaNativeInterface();
  if (hidFindHelperClass(env)) {
    static jmethodID writeMethod = NULL;
    if (writeMethod == NULL) {
      if (!findJavaStaticMethod(
              env, &writeMethod, hidHelperClass, "writeBrailleDisplay",
              JAVA_SIG_METHOD(JAVA_SIG_VOID, JAVA_SIG_ARRAY(JAVA_SIG_BYTE)))) {
        logMessage(LOG_ERR, "writeMethod not found");
        return 0;
      }
    }
    jbyteArray bytes = (*env)->NewByteArray(env, size);
    (*env)->SetByteArrayRegion(env, bytes, 0, size, (jbyte *)data);
    (*env)->CallStaticVoidMethod(env, hidHelperClass, writeMethod, bytes);
    return size;
  }
  return 0;
}

static ssize_t hidAndroidReadData(HidHandle *handle, unsigned char *buffer,
                                  size_t bufferSize, int initialTimeout,
                                  int subsequentTimeout) {
  JNIEnv *env = getJavaNativeInterface();
  if (hidFindHelperClass(env)) {
    static jmethodID readMethod = NULL;
    if (readMethod == NULL) {
      if (!findJavaStaticMethod(
              env, &readMethod, hidHelperClass, "readBrailleDisplay",
              JAVA_SIG_METHOD(JAVA_SIG_ARRAY(JAVA_SIG_BYTE), ))) {
        logMessage(LOG_ERR, "readMethod not found");
        return 0;
      }
    }
    jbyteArray bytes = (jbyteArray)(*env)->CallStaticObjectMethod(
        env, hidHelperClass, readMethod);
    if (bytes != NULL) {
      jsize inputSize = (*env)->GetArrayLength(env, bytes);
      (*env)->GetByteArrayRegion(env, bytes, 0, inputSize, buffer);
      return inputSize;
    }
  }
  return 0;
}

// vendor and product values are not necessary for setup, but the rest of
// brltty tries to call this method, so do nothing.
static int unused_GetDeviceIdentifiers(HidHandle *handle,
                                       HidDeviceIdentifier *vendor,
                                       HidDeviceIdentifier *product) {
  return 1;
}

static const HidHandleMethods hidLinuxHandleMethods = {
    .getDeviceName = getGenericHIDDeviceName,
    .getItems = hidAndroidGetItems,
    .getDeviceIdentifiers = unused_GetDeviceIdentifiers,
    .destroyHandle = hidAndroidDestroyHandle,
    .writeData = hidAndroidWriteData,
    .readData = hidAndroidReadData,
};

static HidHandle *hidAndroidNewHandle() {
  HidHandle *handle = malloc(sizeof(HidHandle));
  if (handle) {
    memset(handle, 0, sizeof(HidHandle));
    return handle;
  } else {
    logMallocError();
  }
  return NULL;
}

static HidHandle *hidLinuxNewHidrawHandle(const HidBluetoothFilter *filter) {
  return hidAndroidNewHandle();
}

const HidPackageDescriptor hidPackageDescriptor = {
    .packageName = "Android hidraw",
    .handleMethods = &hidLinuxHandleMethods,
    // This supports both USB and Bluetooth... brltty just thinks that this I/O
    // file only supports bluetooth based on the BrlttyHidParameterProvider
    // parameters passed to it.
    .newBluetoothHandle = hidLinuxNewHidrawHandle,
};

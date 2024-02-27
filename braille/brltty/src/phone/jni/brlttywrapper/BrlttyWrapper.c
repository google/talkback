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
 * Native code for the Java class
 * com.google.android.accessibility.braille.brltty.BrlttyEncoder.
 */

#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <stdarg.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <alog.h>

#include "bluetooth_android.h"
#include "usb_android.h"
#include "libbrltty.h"
#include "third_party/brltty/Headers/brl_cmds.h"
#include "third_party/brltty/Programs/brlapi_keycodes.h"
#include "third_party/brltty/Headers/io_usb.h"

#define LOG_TAG "BrlttyEncoder_native"

#define DISPLAY_PLATFORM_PACKAGE \
  "com/google/android/accessibility/braille/brltty/"
#define JNIMETHOD(fn) \
  Java_com_google_android_accessibility_braille_brltty_BrlttyEncoder_##fn
#define CUSTOM_KEY (BRL_KEY_FUNCTION+100)

// Data structures for command and key code mapping from the brltty constants
// to java constant fields.

typedef struct CommandMapEntry {
  int brlttyValue;
  jint javaValue;
} CommandMapEntry;

typedef struct CommandMap {
  struct CommandMapEntry* entries;
  size_t numEntries;
} CommandMap;

// Maps an integer to a java field name.
typedef struct NamedCommand {
  const char* fieldName;
  int brlttyValue;
} NamedCommand;

// Creates a map from brltty int constants to the corresponding java ints,
// given by a class name and names of static final int fields in
// the named java class.
static CommandMap* createCommandMap(JNIEnv* env,
                                    jclass cls,
                                    NamedCommand* namedCommands,
                                    size_t numNamedCommands);
static void freeCommandMap(CommandMap* commandMap);
// Returns the corresponding java int from the brltty constant given by
// key.
static jint commandMapGet(CommandMap* commandMap, int key);
// Maps a brltty command (including argument if applicable) into
// the corresponding java command and argument.
// *outCommand is set to -1 if there is no mapping and (outArg
// is set to 0 if there is no argument for this command. isUnifiedCommand
// tells if mapped command is a command available in all braille displays.
static jint mapBrlttyCommand(int brlttyCommand,
                             jint* outCommand,
                             jint* outArg,
                             jboolean* isUnifiedCommand);
// Callback used when listing the brltty keymap.
static int reportKeyBinding(int command, int keyCount, const char* keys[],
                            int isLongPress, void* data);

// Maps from brltty command codes (without arguments and flags)
// to constants in the BrailleInputEvent java class.
static CommandMap* brlttyCommandMap = NULL;
// Maps brltty special key constants to constants in the BrailleInputEvent
// java class.
static CommandMap* brlttyKeyMap = NULL;
// Maps for unified keystokes. Unified keystroke is the keystroke available in
// all knids of braille hardware display.
static CommandMap* unifiedCommandMap = NULL;
// Commands that are special-cased when mapping.
static jint cmdActivateCurrent = -1;
static jint cmdLongPressCurrent = -1;
static jint cmdRoute = -1;
static jint cmdLongPressRoute = -1;
static jlong nativeData;

static jclass class_BrlttyWrapper;
static jclass class_BrailleKeyBinding;
static jclass class_IndexOutOfBoundsException;
static jclass class_OutOfMemoryError;
static jclass class_NullPointerException;
static jclass class_RuntimeException;
static jclass class_IOException;
static jclass class_String;
static jfieldID field_tablesDirPath;
static jmethodID method_sendBytesToDevice;
static jmethodID method_readDelayed;
static jmethodID method_BrailleKeyBinding_ctor;

// Data for the reportKeyBinding callback.
typedef struct ListKeyMapData {
  JNIEnv* env;
  jobjectArray *bindings;
  jsize bindingsSize;
  jsize bindingsCapacity;
} ListKeyMapData;

// Returns an array of BrailleKeyBinding objects for the current display.
static jobjectArray listKeyMap(JNIEnv* env);

typedef struct NativeData {
  int pipefd[2];
  JavaVM* vm;
  int envVer;
  jobject me;
  BluetoothAndroidConnection bluetoothAndroidConnection;
} NativeData;

static ssize_t writeDataToDevice(BluetoothAndroidConnection* conn,
                                 const void* buffer,
                                 size_t size);
static jclass getGlobalClassRef(JNIEnv* env, const char *name);
static jboolean initCommandTables(JNIEnv* env);

jboolean JNIMETHOD(initNative)(JNIEnv* env, jobject thiz, jobject context) {
  NativeData *nat = calloc(1, sizeof(*nat));
  if (!nat) {
    (*env)->ThrowNew(env, class_OutOfMemoryError, NULL);
    return JNI_FALSE;
  }
  if (pipe(nat->pipefd) < 0) {
    LOGE("Can't create pipe");
    goto freenat;
  }
  // Make the reading end of the pipe non-blocking, which is what
  // brltty expects.
  if (fcntl(nat->pipefd[0], F_SETFL, O_NONBLOCK) == -1) {
    LOGE("Couldn't make read end of pipe non-blocking: %s", strerror(errno));
    goto closepipe;
  }
  (*env)->GetJavaVM(env, &(nat->vm));
  nat->envVer = (*env)->GetVersion(env);
  nat->me = (*env)->NewGlobalRef(env, thiz);
  nat->bluetoothAndroidConnection.read_fd = nat->pipefd[0];
  nat->bluetoothAndroidConnection.data = nat;
  // Bluetooth packet exchange is handled in our Java code; here, we reroute
  // a certain function pointer so that phone-to-display packets get sent to a
  // certain Java-side function (for eventual transmission via Android Bluetooth
  // APIs), instead of having BRLTTY send the packets.
  nat->bluetoothAndroidConnection.writeData = writeDataToDevice;
  bluetoothAndroidSetConnection(&nat->bluetoothAndroidConnection);
  usbAndroidSetContext((*env)->NewGlobalRef(env, context));
  nativeData = (jlong) nat;
  return JNI_TRUE;

closepipe:
  close(nat->pipefd[0]);
  close(nat->pipefd[1]);
freenat:
  free(nat);
  return JNI_FALSE;
}

jboolean JNIMETHOD(startNative)(JNIEnv* env, jobject thiz, jstring driverCode,
                                jstring brailleDevice, jfloat timeoutFactor) {
  jboolean result = JNI_FALSE;
  LOGI("Starting braille driver");
  NativeData *nat = (NativeData*) nativeData;
  nat->bluetoothAndroidConnection.timeoutFactor = timeoutFactor;
  if (!nat) {
    LOGE("Trying to start a destroyed object");
    goto out;
  }
  const char *driverCodeChars =
      (*env)->GetStringUTFChars(env, driverCode, NULL);
  if (!driverCodeChars) {
    // Out of memory already thrown.
    goto out;
  }
  const char *brailleDeviceChars =
      (*env)->GetStringUTFChars(env, brailleDevice, NULL);
  if (!brailleDeviceChars) {
    // Out of memory already thrown.
    goto releaseDriverCodeChars;
  }
  jstring tablesDir = (*env)->GetObjectField(env, thiz, field_tablesDirPath);
  if (!tablesDir) {
    (*env)->ThrowNew(env, class_NullPointerException, NULL);
    goto releaseBrailleDeviceChars;
  }
  const char *tablesDirChars = (*env)->GetStringUTFChars(env, tablesDir, NULL);
  if (!tablesDirChars) {
    // Out of memory already thrown.
    goto releaseBrailleDeviceChars;
  }
  if (!brltty_initialize(driverCodeChars, brailleDeviceChars,
                         tablesDirChars)) {
    LOGE("Couldn't initialize braille driver");
    goto releaseTablesDirChars;
  }
  LOGI("Braille driver initialized");
  result = JNI_TRUE;
releaseTablesDirChars:
  (*env)->ReleaseStringUTFChars(env, tablesDir, tablesDirChars);
releaseBrailleDeviceChars:
  (*env)->ReleaseStringUTFChars(env, brailleDevice, brailleDeviceChars);
releaseDriverCodeChars:
  (*env)->ReleaseStringUTFChars(env, driverCode, driverCodeChars);
out:
  return result;
}

void JNIMETHOD(stopNative)(JNIEnv* env, jobject thiz) {
  LOGI("Stopping braille driver");
  NativeData *nat = (NativeData*) nativeData;
  if (nat == NULL) {
    LOGE("Driver already stopped");
    return;
  }
  brltty_destroy();
  nativeData = 0;
  usbForgetDevices();
  bluetoothAndroidSetConnection(NULL);
  usbAndroidSetContext(NULL);
  close(nat->pipefd[0]);
  close(nat->pipefd[1]);
  (*env)->DeleteGlobalRef(env, nat->me);
  free(nat);
}

jint JNIMETHOD(getTextCellsNative)(JNIEnv* env, jobject thiz) {
  return brltty_getTextCells();
}

jint JNIMETHOD(getStatusCellsNative)(JNIEnv* env, jobject thiz) {
  return brltty_getStatusCells();
}

jobjectArray JNIMETHOD(getKeyMapNative)(JNIEnv* env, jobject thiz) {
  ListKeyMapData lkd = {
    .env = env,
    .bindings = NULL,
    .bindingsSize = 0,
    .bindingsCapacity = 0,
  };
  jobjectArray result = NULL;
  if ((*env)->PushLocalFrame(env, 128) < 0) {
    // Exception thrown.
    return NULL;
  }
  if (!brltty_listKeyMap(reportKeyBinding, &lkd)) {
    (*env)->ThrowNew(env, class_RuntimeException, "Couldn't list key bindings");
    goto out;
  }
  jobjectArray array = (*env)->NewObjectArray(
      env, lkd.bindingsSize, class_BrailleKeyBinding, NULL);
  if (array == NULL) {
    // Exception thrown.
    goto out;
  }
  int i;
  for (i = 0; i < lkd.bindingsSize; ++i) {
    (*env)->SetObjectArrayElement(env, array, i, lkd.bindings[i]);
  }
  result = array;
out:
  free(lkd.bindings);
  return (*env)->PopLocalFrame(env, result);
}

jboolean JNIMETHOD(writeWindowNative)(
    JNIEnv* env, jobject thiz, jbyteArray pattern) {
  jboolean ret = JNI_FALSE;
  jsize patternLen = (*env)->GetArrayLength(env, pattern);
  jbyte *bytes = (*env)->GetByteArrayElements(env, pattern, NULL);
  if (!bytes) {
    goto out;
  }
  if (!brltty_writeWindow(bytes, patternLen)) {
    goto releasebytes;
  }
  ret = JNI_TRUE;
releasebytes:
  (*env)->ReleaseByteArrayElements(env, pattern, bytes, JNI_ABORT);
out:
  return ret;
}

jint JNIMETHOD(readCommandNative)(JNIEnv* env, jobject thiz) {
  int ret = -1;
  int readDelayMillis = -1;
  while (ret < 0) {
    int innerDelayMillis = -1;
    int brlttyCommand = brltty_readCommand(&innerDelayMillis);
    if (readDelayMillis < 0 ||
        (innerDelayMillis > 0 && innerDelayMillis < readDelayMillis)) {
      readDelayMillis = innerDelayMillis;
    }
    if (brlttyCommand == EOF || brlttyCommand == BRL_CMD_RESTARTBRL) {
      ret = -1;
      break;
    }
    jint mappedCommand, mappedArg;
    jboolean isUnifiedCommand;
    mapBrlttyCommand(brlttyCommand, &mappedCommand, &mappedArg, &isUnifiedCommand);
    if (mappedCommand < 0) {
      // Filter out commands that we don't handle, including BRL_NOOP.
      // Get the next command, until we get a valid command or EOF, in both
      // of which cases the loop will terminate.
      continue;
    }
    ret = (mappedArg << 16) | mappedCommand;
  }
  if (readDelayMillis > 0) {
    (*env)->CallVoidMethod(env, thiz, method_readDelayed,
                           (jlong) readDelayMillis);
  }
  return ret;
}

void JNIMETHOD(addBytesFromDeviceNative)(
    JNIEnv* env, jobject thiz, jbyteArray bytes, jint size) {
  // TODO: Get rid of the race condition here.
  NativeData *nat = (NativeData*) nativeData;
  if (!nat) {
    LOGE("Writing to destoyed driver, ignoring");
    return;
  }
  jsize bytesLen = (*env)->GetArrayLength(env, bytes);
  if (size < 0 || size > bytesLen) {
    (*env)->ThrowNew(env, class_IndexOutOfBoundsException, NULL);
    return;
  }
  jbyte *b = (*env)->GetByteArrayElements(env, bytes, NULL);
  if (!b) {
    // Out of memory thrown.
    return;
  }
  char *writeptr = b;
  while (size > 0) {
    ssize_t res = write(nat->pipefd[1], writeptr, size);
    if (res < 0) {
      if (errno == EINTR) {
        continue;
      }
      LOGE("Can't write to driver: %s", strerror(errno));
      (*env)->ThrowNew(env, class_IOException, strerror(errno));
      goto releasebytes;
    } else if (res == 0) {
      LOGE("Can't write to driver");
      (*env)->ThrowNew(env, class_IOException, NULL);
      goto releasebytes;
    }
    size -= res;
    writeptr += res;
  }
releasebytes:
  (*env)->ReleaseByteArrayElements(env, bytes, b, JNI_ABORT);
}

void JNIMETHOD(classInitNative)(JNIEnv* env, jclass clazz) {
  if (!(class_BrlttyWrapper = (*env)->NewGlobalRef(env, clazz))) {
    LOGE("Couldn't get global ref for BrlttyWrapper class");
    return;
  }
  if (!(method_sendBytesToDevice = (*env)->GetMethodID(
          env, clazz, "sendBytesToDevice", "([B)Z"))) {
    LOGE("Couldn't find sendBytesToDevice method");
    return;
  }
  if (!(method_readDelayed = (*env)->GetMethodID(
          env, clazz, "readDelayed", "(J)V"))) {
    LOGE("Couldn't find readDelayed method");
    return;
  }
  if (!(field_tablesDirPath = (*env)->GetFieldID(
          env, clazz, "tablesDirPath", "Ljava/lang/String;"))) {
    LOGE("Couldn't find tablesDirPath field");
    return;
  }
  if (!(class_BrailleKeyBinding = getGlobalClassRef(
          env, DISPLAY_PLATFORM_PACKAGE "BrailleKeyBinding"))) {
    return;
  }
  if (!(method_BrailleKeyBinding_ctor =
        (*env)->GetMethodID(
            env, class_BrailleKeyBinding, "<init>",
            "(I[Ljava/lang/String;ZZ)V"))) {
    return;
  }
  if (!(class_OutOfMemoryError =
        getGlobalClassRef(env, "java/lang/OutOfMemoryError"))) {
    return;
  }
  if (!(class_NullPointerException =
        getGlobalClassRef(env, "java/lang/NullPointerException"))) {
    return;
  }
  if (!(class_IndexOutOfBoundsException =
        getGlobalClassRef(env, "java/lang/IndexOutOfBoundsException"))) {
    return;
  }
  if (!(class_RuntimeException =
        getGlobalClassRef(env, "java/lang/RuntimeException"))) {
    return;
  }
  if (!(class_IOException = getGlobalClassRef(env, "java/io/IOException"))) {
    return;
  }
  if (!(class_String =
        getGlobalClassRef(env, "java/lang/String"))) {
    return;
  }
  if (!initCommandTables(env)) {
    LOGE("Couldn't initialize command tables");
    return;
  }
}

//////////////////////////////////////////////////////////////////////

static ssize_t writeDataToDevice(BluetoothAndroidConnection* conn,
                                 const void* buffer,
                                 size_t size) {
  //LOGV("Writing %zu bytes to bluetooth", size);
  NativeData *nat = conn->data;
  JNIEnv* env;
  (*nat->vm)->GetEnv(nat->vm, (void**)&env, nat->envVer);
  jbyteArray byteArray = (*env)->NewByteArray(env, size);
  if (!byteArray) {
    errno = ENOMEM;
    return -1;
  }
  (*env)->SetByteArrayRegion(env, byteArray, 0, size, buffer);
  jboolean result = (*env)->CallBooleanMethod(env, nat->me,
                                              method_sendBytesToDevice,
                                              byteArray);
  if (!result || (*env)->ExceptionCheck(env)) {
    errno = EIO;
    return -1;
  }
  return size;
}

static jclass
getGlobalClassRef(JNIEnv* env, const char *name) {
  jclass localRef = (*env)->FindClass(env, name);
  if (!localRef) {
    LOGE("Couldn't find class %s", name);
    return NULL;
  }
  jclass globalRef = (*env)->NewGlobalRef(env, localRef);
  if (globalRef == NULL) {
    LOGE("Couldn't create global ref for class %s", name);
  }
  return globalRef;
}

// Gets the value of a static (presumably final) int field of the
// given class.  Returns the value of the field, or -1 if the field can't
// be found, in which case an exception is thrown as well.
static jint
getStaticIntField(JNIEnv* env, jclass clazz, const char* fieldName) {
  jfieldID id = (*env)->GetStaticFieldID(env, clazz, fieldName, "I");
  if (!id) {
    LOGE("Can't find field: %s", fieldName);
    return -1;
  }
  return (*env)->GetStaticIntField(env, clazz, id);
}

static jboolean
initCommandTables(JNIEnv* env) {
  jboolean ret = JNI_FALSE;
  jclass cls = (*env)->FindClass(env, DISPLAY_PLATFORM_PACKAGE "BrailleInputEvent");
  if (!cls) {
    // Exception thrown by the JVM.
    goto cleanup;
  }
  NamedCommand namesToCommands[] = {
    {"CMD_NAV_LINE_PREVIOUS", BRL_CMD_LNUP},
    {"CMD_NAV_LINE_NEXT", BRL_CMD_LNDN},
    {"CMD_NAV_ITEM_PREVIOUS", BRL_CMD_CHRLT},
    {"CMD_NAV_ITEM_NEXT", BRL_CMD_CHRRT},
    {"CMD_NAV_PAN_UP", BRL_CMD_FWINLT},
    {"CMD_NAV_PAN_DOWN", BRL_CMD_FWINRT},
    {"CMD_NAV_TOP", BRL_CMD_TOP},
    {"CMD_NAV_BOTTOM", BRL_CMD_BOT},
    {"CMD_SCROLL_BACKWARD", BRL_CMD_WINUP},
    {"CMD_SCROLL_FORWARD", BRL_CMD_WINDN},
    {"CMD_SELECTION_START", BRL_CMD_BLK(CLIP_NEW)},
    {"CMD_SELECTION_END", BRL_CMD_BLK(COPY_LINE)},
    {"CMD_SELECTION_PASTE", BRL_CMD_PASTE},
    {"CMD_BRAILLE_KEY", BRL_CMD_BLK(PASSDOTS)},
    {"CMD_HELP", BRL_CMD_LEARN },
    {"CMD_NAV_TOP_OR_KEY_ACTIVATE", BRL_CMD_LNBEG },
    {"CMD_NAV_BOTTOM_OR_KEY_ACTIVATE", BRL_CMD_LNEND },
  };
  brlttyCommandMap = createCommandMap(
      env, cls, namesToCommands,
      sizeof(namesToCommands) / sizeof(namesToCommands[0]));
  if (brlttyCommandMap == NULL) {
    goto cleanup;
  }
  NamedCommand namesToKeys[] = {
    {"CMD_NAV_ITEM_PREVIOUS", BRL_KEY_CURSOR_LEFT},
    {"CMD_NAV_ITEM_NEXT", BRL_KEY_CURSOR_RIGHT},
    {"CMD_NAV_LINE_PREVIOUS", BRL_KEY_CURSOR_UP},
    {"CMD_NAV_LINE_NEXT", BRL_KEY_CURSOR_DOWN},
    {"CMD_KEY_ENTER", BRL_KEY_ENTER},
    {"CMD_KEY_DEL", BRL_KEY_BACKSPACE},
  };
  brlttyKeyMap = createCommandMap(
      env, cls, namesToKeys,
      sizeof(namesToKeys) / sizeof(namesToKeys[0]));
  if (brlttyKeyMap == NULL) {
    goto cleanup;
  }
  NamedCommand unifiedCommands[] = {
      // Defines the unified command across all devices.
      {"CMD_NAV_ITEM_NEXT", CUSTOM_KEY + 0},
      {"CMD_NAV_ITEM_PREVIOUS", CUSTOM_KEY + 1},
      {"CMD_NAV_LINE_NEXT", CUSTOM_KEY + 2},
      {"CMD_NAV_LINE_PREVIOUS", CUSTOM_KEY + 3},
      {"CMD_SCROLL_FORWARD", CUSTOM_KEY + 4},
      {"CMD_SCROLL_BACKWARD", CUSTOM_KEY + 5},
      {"CMD_NAV_TOP", CUSTOM_KEY + 6},
      {"CMD_NAV_BOTTOM", CUSTOM_KEY + 7},
      {"CMD_GLOBAL_BACK", CUSTOM_KEY + 8},
      {"CMD_GLOBAL_HOME", CUSTOM_KEY + 9},
      {"CMD_GLOBAL_RECENTS", CUSTOM_KEY + 10},
      {"CMD_GLOBAL_NOTIFICATIONS", CUSTOM_KEY + 11},
      {"CMD_HELP", CUSTOM_KEY + 12},
      {"CMD_HEADING_NEXT", CUSTOM_KEY + 13},
      {"CMD_HEADING_PREVIOUS", CUSTOM_KEY + 14},
      {"CMD_CONTROL_NEXT", CUSTOM_KEY + 15},
      {"CMD_CONTROL_PREVIOUS", CUSTOM_KEY + 16},
      {"CMD_LINK_NEXT", CUSTOM_KEY + 17},
      {"CMD_LINK_PREVIOUS", CUSTOM_KEY + 18},
      {"CMD_TOGGLE_SCREEN_SEARCH", CUSTOM_KEY + 19},
      {"CMD_EDIT_CUSTOM_LABEL", CUSTOM_KEY + 20},
      {"CMD_SWITCH_TO_NEXT_INPUT_LANGUAGE", CUSTOM_KEY + 21},
      {"CMD_SWITCH_TO_NEXT_OUTPUT_LANGUAGE", CUSTOM_KEY + 22},
      {"CMD_BRAILLE_DISPLAY_SETTINGS", CUSTOM_KEY + 23},
      {"CMD_TALKBACK_SETTINGS", CUSTOM_KEY + 24},
      {"CMD_QUICK_SETTINGS", CUSTOM_KEY + 25},
      {"CMD_ALL_APPS", CUSTOM_KEY + 26},
      {"CMD_OPEN_TALKBACK_MENU", CUSTOM_KEY + 27},
      {"CMD_KEY_DEL", CUSTOM_KEY + 28},
      {"CMD_KEY_ENTER", CUSTOM_KEY + 29},
      {"CMD_TURN_OFF_BRAILLE_DISPLAY", CUSTOM_KEY + 30},
      {"CMD_CHARACTER_PREVIOUS", CUSTOM_KEY + 31},
      {"CMD_CHARACTER_NEXT", CUSTOM_KEY + 32},
      {"CMD_WORD_PREVIOUS", CUSTOM_KEY + 33},
      {"CMD_WORD_NEXT", CUSTOM_KEY + 34},
      {"CMD_WINDOW_PREVIOUS", CUSTOM_KEY + 35},
      {"CMD_WINDOW_NEXT", CUSTOM_KEY + 36},
      {"CMD_DEL_WORD", CUSTOM_KEY + 37},
      {"CMD_TOGGLE_VOICE_FEEDBACK", CUSTOM_KEY + 38},
      {"CMD_PREVIOUS_READING_CONTROL", CUSTOM_KEY + 39},
      {"CMD_NEXT_READING_CONTROL", CUSTOM_KEY + 40},
      {"CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_BACKWARD",
       CUSTOM_KEY + 41},
      {"CMD_NAVIGATE_BY_READING_GRANULARITY_OR_ADJUST_READING_CONTROL_FORWARD",
       CUSTOM_KEY + 42},
      {"CMD_TOGGLE_BRAILLE_GRADE", CUSTOM_KEY + 43},
      {"CMD_LONG_PRESS_CURRENT", CUSTOM_KEY + 44},
      {"CMD_STOP_READING", CUSTOM_KEY + 45},
      {"CMD_SELECTION_CUT", CUSTOM_KEY + 46},
      {"CMD_SELECTION_COPY", CUSTOM_KEY + 47},
      {"CMD_SELECTION_PASTE", CUSTOM_KEY + 48},
      {"CMD_SELECTION_SELECT_ALL", CUSTOM_KEY + 49},
      {"CMD_SELECT_PREVIOUS_CHARACTER", CUSTOM_KEY + 50},
      {"CMD_SELECT_NEXT_CHARACTER", CUSTOM_KEY + 51},
      {"CMD_SELECT_PREVIOUS_WORD", CUSTOM_KEY + 52},
      {"CMD_SELECT_NEXT_WORD", CUSTOM_KEY + 53},
      {"CMD_SELECT_PREVIOUS_LINE", CUSTOM_KEY + 54},
      {"CMD_SELECT_NEXT_LINE", CUSTOM_KEY + 55},
      {"CMD_TOGGLE_AUTO_SCROLL", CUSTOM_KEY + 56},
      {"CMD_PLAY_PAUSE_MEDIA", CUSTOM_KEY + 57}};
  unifiedCommandMap = createCommandMap(
      env, cls, unifiedCommands,
      sizeof(unifiedCommands) / sizeof(unifiedCommands[0]));
  if (unifiedCommandMap == NULL) {
    goto cleanup;
  }
  cmdActivateCurrent = getStaticIntField(env, cls, "CMD_ACTIVATE_CURRENT");
  if ((*env)->ExceptionCheck(env)) {
    goto cleanup;
  }
  cmdLongPressCurrent = getStaticIntField(env, cls, "CMD_LONG_PRESS_CURRENT");
  if ((*env)->ExceptionCheck(env)) {
    goto cleanup;
  }
  cmdRoute = getStaticIntField(env, cls, "CMD_ROUTE");
  if ((*env)->ExceptionCheck(env)) {
    goto cleanup;
  }
  cmdLongPressRoute = getStaticIntField(env, cls, "CMD_LONG_PRESS_ROUTE");
  if ((*env)->ExceptionCheck(env)) {
    goto cleanup;
  }
  ret = JNI_TRUE;
  goto out;

cleanup:
  freeCommandMap(brlttyKeyMap);
  freeCommandMap(brlttyCommandMap);
  freeCommandMap(unifiedCommandMap);
out:
  (*env)->DeleteLocalRef(env, cls);
  return ret;
}

static int
commandMapEntryComp(const void* a, const void* b) {
  CommandMapEntry* aEntry = (CommandMapEntry*) a;
  CommandMapEntry* bEntry = (CommandMapEntry*) b;
  return aEntry->brlttyValue - bEntry->brlttyValue;
}

static CommandMap* createCommandMap(JNIEnv* env,
                                    jclass cls,
                                    NamedCommand* namedCommands,
                                    size_t numNamedCommands) {
  CommandMap* commandMap = calloc(1, sizeof(*commandMap));
  if (!commandMap) {
    (*env)->ThrowNew(env, class_OutOfMemoryError, NULL);
    goto cleanup;
  }
  CommandMapEntry* entries = calloc(numNamedCommands, sizeof(*entries));
  if (!entries) {
    (*env)->ThrowNew(env, class_OutOfMemoryError, NULL);
    goto cleanup;
  }
  commandMap->entries = entries;
  commandMap->numEntries = numNamedCommands;
  int i;
  for (i = 0; i < numNamedCommands; ++i) {
    entries[i].brlttyValue = namedCommands[i].brlttyValue;
    entries[i].javaValue = getStaticIntField(
        env, cls, namedCommands[i].fieldName);
    if ((*env)->ExceptionCheck(env)) {
      goto cleanup;
    }
  }
  qsort(entries, numNamedCommands, sizeof(*entries), commandMapEntryComp);
  return commandMap;

cleanup:
  freeCommandMap(commandMap);
  return NULL;
}

static void
freeCommandMap(CommandMap* commandMap) {
  if (commandMap != NULL) {
    free(commandMap->entries);
    free(commandMap);
  }
}

static jint
commandMapGet(CommandMap* commandMap, int key) {
  CommandMapEntry keyEntry = { .brlttyValue = key };
  CommandMapEntry* found = bsearch(&keyEntry, commandMap->entries,
                                   commandMap->numEntries,
                                   sizeof(keyEntry), commandMapEntryComp);
  return found != NULL ? found->javaValue : -1;
}

static jint
mapBrlttyCommand(int brlttyCommand,
                 jint* outCommand, jint* outArg, jboolean* isUnifiedCommand) {
  // Mask away some flags and bits we don't care about.
  int maskedCommand;
  int brlttyArg;
  if ((brlttyCommand & BRL_MSK_BLK) != 0) {
    maskedCommand = (brlttyCommand & BRL_MSK_BLK);
    brlttyArg = BRL_ARG_GET(brlttyCommand);
  } else {
    maskedCommand = (brlttyCommand & BRL_MSK_CMD);
    brlttyArg = 0;
  }
  if (maskedCommand == BRL_CMD_BLK(PASSKEY)) {
    jboolean unifiedCommand =
        0 <= brlttyArg - CUSTOM_KEY &&
        brlttyArg - CUSTOM_KEY <= unifiedCommandMap->numEntries;
    if (unifiedCommand) {
      *outCommand = commandMapGet(unifiedCommandMap, brlttyArg);
      *isUnifiedCommand = JNI_TRUE;
    } else {
      *outCommand = commandMapGet(brlttyKeyMap, brlttyArg);
    }
    *outArg = 0;
  } else if (maskedCommand == BRL_CMD_BLK(ROUTE)) {
    int longPress = (brlttyArg & BRLTTY_ROUTE_ARG_FLG_LONG_PRESS);
    brlttyArg &= ~BRLTTY_ROUTE_ARG_FLG_LONG_PRESS;
    if (brlttyArg >= brltty_getTextCells()) {
      // Treat a routing command outside of the display as a distinct command.
      *outArg = 0;
      *outCommand = longPress ? cmdLongPressCurrent : cmdActivateCurrent;
    } else {
      *outArg = brlttyArg;
      *outCommand = longPress ? cmdLongPressRoute : cmdRoute;
    }
  } else {
    *outCommand = commandMapGet(brlttyCommandMap, maskedCommand);
    *outArg = brlttyArg;
  }
  return 0;
}

static int
reportKeyBinding(int command, int keyNameCount, const char* keyNames[],
                 int isLongPress,
                 void* data) {
  int mappedCommand, mappedArg;
  jboolean isUnifiedCommand = JNI_FALSE;
  mapBrlttyCommand(command, &mappedCommand, &mappedArg, &isUnifiedCommand);
  if (mappedCommand < 0) {
    // Unsupported command, don't report it.
    return 1;
  }
  ListKeyMapData *lkd = data;
  JNIEnv* env = lkd->env;
  if (lkd->bindingsSize >= lkd->bindingsCapacity) {
    int newCapacity = (lkd->bindingsCapacity == 0)
        ? 64
        : lkd->bindingsCapacity * 2;
    jobjectArray *newBindings = realloc(
        lkd->bindings, sizeof(*newBindings) * newCapacity);
    if (newBindings == NULL) {
      return 0;
    }
    lkd->bindings = newBindings;
    lkd->bindingsCapacity = newCapacity;
    if ((*env)->EnsureLocalCapacity(env, newCapacity + 16) < 0) {
      return 0;
    }
  }
  jobjectArray keys = (*env)->NewObjectArray(
      env, keyNameCount, class_String, NULL);
  if (keys == NULL) {
    return 0;
  }
  int i;
  for (i = 0; i < keyNameCount; ++i) {
    jobject name = (*env)->NewStringUTF(env, keyNames[i]);
    if (name == NULL) {
      return 0;
    }
    (*env)->SetObjectArrayElement(env, keys, i, name);
    (*env)->DeleteLocalRef(env, name);
  }
  jobject binding = (*env)->NewObject(
      env, class_BrailleKeyBinding, method_BrailleKeyBinding_ctor,
      mappedCommand, keys, isLongPress, isUnifiedCommand == JNI_TRUE);
  if (binding == NULL) {
    return 0;
  }
  (*env)->DeleteLocalRef(env, keys);
  lkd->bindings[lkd->bindingsSize++] = binding;
  return 1;
}

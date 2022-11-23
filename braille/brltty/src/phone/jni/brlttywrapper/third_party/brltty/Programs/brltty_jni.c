/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2019 by The BRLTTY Developers.
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

#include <jni.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <errno.h>
#include <dlfcn.h>

#include "embed.h"
#include "system_java.h"

#if defined(__ANDROID__)
#include <android/log.h>
#define LOG(...) __android_log_print(ANDROID_LOG_DEBUG, PACKAGE_TARNAME, __VA_ARGS__)

#else /* LOG() */
#warning logging not supported
#define LOG(...)
#endif /* LOG() */

SYMBOL_POINTER(brlttyConstruct);
SYMBOL_POINTER(setJavaClassLoader);
SYMBOL_POINTER(brlttyDestruct);

SYMBOL_POINTER(brlttyEnableInterrupt);
SYMBOL_POINTER(brlttyDisableInterrupt);

SYMBOL_POINTER(brlttyInterrupt);
SYMBOL_POINTER(brlttyWait);

SYMBOL_POINTER(changeLogLevel);
SYMBOL_POINTER(changeLogCategories);

SYMBOL_POINTER(changeTextTable);
SYMBOL_POINTER(changeAttributesTable);
SYMBOL_POINTER(changeContractionTable);
SYMBOL_POINTER(changeKeyboardTable);

SYMBOL_POINTER(restartBrailleDriver);
SYMBOL_POINTER(changeBrailleDriver);
SYMBOL_POINTER(changeBrailleParameters);
SYMBOL_POINTER(changeBrailleDevice);

SYMBOL_POINTER(restartSpeechDriver);
SYMBOL_POINTER(changeSpeechDriver);
SYMBOL_POINTER(changeSpeechParameters);

SYMBOL_POINTER(restartScreenDriver);
SYMBOL_POINTER(changeScreenDriver);
SYMBOL_POINTER(changeScreenParameters);

SYMBOL_POINTER(showMessage);

typedef struct {
  const char *name;
  void *pointer;
} SymbolEntry;

#define BEGIN_SYMBOL_TABLE static const SymbolEntry symbolTable[] = {
#define END_SYMBOL_TABLE {.name=NULL} };
#define SYMBOL_ENTRY(symbol) {.name=#symbol, .pointer=&symbol##_p}

BEGIN_SYMBOL_TABLE
  SYMBOL_ENTRY(brlttyConstruct),
  SYMBOL_ENTRY(setJavaClassLoader),
  SYMBOL_ENTRY(brlttyDestruct),

  SYMBOL_ENTRY(brlttyEnableInterrupt),
  SYMBOL_ENTRY(brlttyDisableInterrupt),

  SYMBOL_ENTRY(brlttyInterrupt),
  SYMBOL_ENTRY(brlttyWait),

  SYMBOL_ENTRY(changeLogLevel),
  SYMBOL_ENTRY(changeLogCategories),

  SYMBOL_ENTRY(changeTextTable),
  SYMBOL_ENTRY(changeAttributesTable),
  SYMBOL_ENTRY(changeContractionTable),
  SYMBOL_ENTRY(changeKeyboardTable),

  SYMBOL_ENTRY(restartBrailleDriver),
  SYMBOL_ENTRY(changeBrailleDriver),
  SYMBOL_ENTRY(changeBrailleParameters),
  SYMBOL_ENTRY(changeBrailleDevice),

  SYMBOL_ENTRY(restartSpeechDriver),
  SYMBOL_ENTRY(changeSpeechDriver),
  SYMBOL_ENTRY(changeSpeechParameters),

  SYMBOL_ENTRY(restartScreenDriver),
  SYMBOL_ENTRY(changeScreenDriver),
  SYMBOL_ENTRY(changeScreenParameters),

  SYMBOL_ENTRY(showMessage),
END_SYMBOL_TABLE

static void *coreHandle = NULL;

static jobject jArgumentArray = NULL;
static const char **cArgumentArray = NULL;
static int cArgumentCount;

static void reportProblem (
  JNIEnv *env, const char *throwable,
  const char *format, ...
) PRINTF(3, 4);

static void
reportProblem (
  JNIEnv *env, const char *throwable,
  const char *format, ...
) {
  char message[0X100];

  {
    va_list arguments;

    va_start(arguments, format);
    vsnprintf(message, sizeof(message), format, arguments);
    va_end(arguments);
  }

  if (0) {
    FILE *stream = stderr;

    fprintf(stream, "%s\n", message);
    fflush(stream);
  }

  {
    jclass object = (*env)->FindClass(env, throwable);

    if (object) {
      (*env)->ThrowNew(env, object, message);
      (*env)->DeleteLocalRef(env, object);
    }
  }
}

static void
reportOutOfMemory (JNIEnv *env, const char *description) {
  reportProblem(env, JAVA_OBJ_OUT_OF_MEMORY_ERROR, "cannot allocate %s", description);
}

static int
prepareProgramArguments (JNIEnv *env, jstring arguments) {
  jsize count = (*env)->GetArrayLength(env, arguments);

  if ((jArgumentArray = (*env)->NewGlobalRef(env, arguments))) {
    if ((cArgumentArray = malloc((count + 2) * sizeof(*cArgumentArray)))) {
      cArgumentArray[0] = PACKAGE_TARNAME;
      cArgumentArray[count+1] = NULL;

      {
        unsigned int i;

        for (i=1; i<=count; i+=1) cArgumentArray[i] = NULL;

        for (i=1; i<=count; i+=1) {
          jstring jArgument = (*env)->GetObjectArrayElement(env, arguments, i-1);
          jboolean isCopy;
          const char *cArgument = (*env)->GetStringUTFChars(env, jArgument, &isCopy);

          (*env)->DeleteLocalRef(env, jArgument);
          jArgument = NULL;

          if (!cArgument) {
            reportOutOfMemory(env, "C argument string");
            break;
          }

          cArgumentArray[i] = cArgument;
        }

        if (i > count) {
          cArgumentCount = count + 1;
          return 1;
        }
      }
    } else {
      reportOutOfMemory(env, "C argument array");
    }
  } else {
    reportOutOfMemory(env, "Java arguments array global reference");
  }

  return 0;
}

static int
loadCoreLibrary (JNIEnv *env) {
  if (coreHandle) return 1;

  if ((coreHandle = dlopen("libbrltty_core.so", RTLD_NOW | RTLD_GLOBAL))) {
    int allFound = 1;
    const SymbolEntry *symbol = symbolTable;

    while (symbol->name) {
      const void **pointer = symbol->pointer;

      if ((*pointer = dlsym(coreHandle, symbol->name))) {
        LOG("core symbol: %s -> %p", symbol->name, *pointer);
      } else {
        LOG("core symbol not found: %s", symbol->name);
        allFound = 0;
      }

      symbol += 1;
    }

    if (allFound) return 1;
  }

  reportProblem(env, JAVA_OBJ_UNSATISFIED_LINK_ERROR, "%s", dlerror());
  return 0;
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, coreConstruct, jint,
  jobjectArray arguments, jobject classLoader
) {
  if (prepareProgramArguments(env, arguments)) {
    if (loadCoreLibrary(env)) {
      setJavaClassLoader_p(env, classLoader);
      return brlttyConstruct_p(cArgumentCount, (char **)cArgumentArray);
    }
  }

  return PROG_EXIT_FATAL;
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, coreDestruct, jboolean
) {
  jboolean result = brlttyDestruct_p()? JNI_TRUE: JNI_FALSE;

/*
  {
    const SymbolEntry *symbol = symbolTable;

    while (symbol->name) {
      const void **pointer = symbol->pointer;
      *pointer = NULL;
      symbol += 1;
    }
  }

  if (coreHandle) {
    dlclose(coreHandle);
    coreHandle = NULL;
  }
*/

  if (jArgumentArray) {
    if (cArgumentArray) {
      unsigned int i = 0;

      while (cArgumentArray[++i]) {
        jstring jArgument = (*env)->GetObjectArrayElement(env, jArgumentArray, i-1);
        (*env)->ReleaseStringUTFChars(env, jArgument, cArgumentArray[i]);
        (*env)->DeleteLocalRef(env, jArgument);
        jArgument = NULL;
      }

      free(cArgumentArray);
      cArgumentArray = NULL;
    }

    (*env)->DeleteGlobalRef(env, jArgumentArray);
    jArgumentArray = NULL;
  }

  return result;
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, coreEnableInterrupt, jboolean
) {
  return brlttyEnableInterrupt_p()? JNI_TRUE: JNI_FALSE;
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, coreDisableInterrupt, jboolean
) {
  return brlttyDisableInterrupt_p()? JNI_TRUE: JNI_FALSE;
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, coreInterrupt, jboolean,
  jboolean stop
) {
  return brlttyInterrupt_p((stop != JNI_FALSE)? WAIT_STOP: WAIT_CONTINUE)? JNI_TRUE: JNI_FALSE;
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, coreWait, jboolean,
  jint duration
) {
  return (brlttyWait_p(duration) != WAIT_STOP)? JNI_TRUE: JNI_FALSE;
}

static jboolean
changeStringValue (JNIEnv *env, int (*change) (const char *cValue), jstring jValue) {
  jboolean result = JNI_FALSE;
  const char *cValue = (*env)->GetStringUTFChars(env, jValue, NULL);

  if (cValue) {
    if (change(cValue)) result = JNI_TRUE;
    (*env)->ReleaseStringUTFChars(env, jValue, cValue);
  } else {
    reportOutOfMemory(env, "C new value string");
  }

  return result;
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeLogLevel, jboolean,
  jstring level
) {
  return changeStringValue(env, changeLogLevel_p, level);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeLogCategories, jboolean,
  jstring categories
) {
  return changeStringValue(env, changeLogCategories_p, categories);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeTextTable, jboolean,
  jstring name
) {
  return changeStringValue(env, changeTextTable_p, name);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeAttributesTable, jboolean,
  jstring name
) {
  return changeStringValue(env, changeAttributesTable_p, name);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeContractionTable, jboolean,
  jstring name
) {
  return changeStringValue(env, changeContractionTable_p, name);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeKeyboardTable, jboolean,
  jstring name
) {
  return changeStringValue(env, changeKeyboardTable_p, name);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, restartBrailleDriver, jboolean
) {
  restartBrailleDriver_p();
  return JNI_TRUE;
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeBrailleDriver, jboolean,
  jstring driver
) {
  return changeStringValue(env, changeBrailleDriver_p, driver);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeBrailleParameters, jboolean,
  jstring parameters
) {
  return changeStringValue(env, changeBrailleParameters_p, parameters);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeBrailleDevice, jboolean,
  jstring device
) {
  return changeStringValue(env, changeBrailleDevice_p, device);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, restartSpeechDriver, jboolean
) {
  restartSpeechDriver_p();
  return JNI_TRUE;
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeSpeechDriver, jboolean,
  jstring driver
) {
  return changeStringValue(env, changeSpeechDriver_p, driver);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeSpeechParameters, jboolean,
  jstring parameters
) {
  return changeStringValue(env, changeSpeechParameters_p, parameters);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, restartScreenDriver, jboolean
) {
  restartScreenDriver_p();
  return JNI_TRUE;
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeScreenDriver, jboolean,
  jstring driver
) {
  return changeStringValue(env, changeScreenDriver_p, driver);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, changeScreenParameters, jboolean,
  jstring parameters
) {
  return changeStringValue(env, changeScreenParameters_p, parameters);
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, showMessage, void,
  jstring jMessage
) {
  const char *cMessage = (*env)->GetStringUTFChars(env, jMessage, NULL);

  if (cMessage) {
    showMessage_p(cMessage);
    (*env)->ReleaseStringUTFChars(env, jMessage, cMessage);
  } else {
    reportOutOfMemory(env, "C new value string");
  }
}

JAVA_STATIC_METHOD (
  org_a11y_brltty_core_CoreWrapper, setEnvironmentVariable, jboolean,
  jstring jName, jstring jValue
) {
  jboolean isCopy;
  const char *cName = (*env)->GetStringUTFChars(env, jName, &isCopy);
  const char *cValue = (*env)->GetStringUTFChars(env, jValue, &isCopy);

  int cResult = setenv(cName, cValue, 1) != -1;
  jboolean jResult = cResult? JNI_TRUE: JNI_FALSE;

  if (cResult) {
    LOG("environment variable set: %s: %s", cName, cValue);
  } else {
    LOG("environment variable not set: %s: %s", cName, strerror(errno));
  }

  (*env)->ReleaseStringUTFChars(env, jName, cName);
  (*env)->ReleaseStringUTFChars(env, jValue, cValue);
  return jResult;
}

JNIEXPORT jint
JNI_OnLoad (JavaVM *vm, void *reserved) {
  JNIEnv *env;

  if ((*vm)->GetEnv(vm, (void **)&env, JAVA_JNI_VERSION) == JNI_OK) {
    loadCoreLibrary(env);
  }

  return JAVA_JNI_VERSION;
}

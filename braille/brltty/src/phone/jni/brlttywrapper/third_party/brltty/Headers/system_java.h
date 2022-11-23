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

#ifndef BRLTTY_INCLUDED_SYSTEM_JAVA
#define BRLTTY_INCLUDED_SYSTEM_JAVA

#include "prologue.h"
#include "common_java.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#ifdef __ANDROID__
#define JAVA_JNI_VERSION JNI_VERSION_1_6
#define JAVA_OBJ_BRLTTY(name) "org/a11y/brltty/android/" name
#endif /* __ANDROID__ */

extern JavaVM *getJavaInvocationInterface (void);
extern JNIEnv *getJavaNativeInterface (void);
extern int clearJavaException (JNIEnv *env, int describe);

FUNCTION_DECLARE(setJavaClassLoader, int, (JNIEnv *env, jobject instance));
extern int findJavaClass (JNIEnv *env, jclass *class, const char *path);

extern int findJavaConstructor (
  JNIEnv *env, jmethodID *constructor,
  jclass class, const char *signature
);

extern int findJavaInstanceMethod (
  JNIEnv *env, jmethodID *method,
  jclass class, const char *name, const char *signature
);

extern int findJavaStaticMethod (
  JNIEnv *env, jmethodID *method,
  jclass class, const char *name, const char *signature
);

extern int findJavaInstanceField (
  JNIEnv *env, jfieldID *field,
  jclass class, const char *name, const char *signature
);

extern int findJavaStaticField (
  JNIEnv *env, jfieldID *field,
  jclass class, const char *name, const char *signature
);

extern char *getJavaLocaleName (void);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SYSTEM_JAVA */

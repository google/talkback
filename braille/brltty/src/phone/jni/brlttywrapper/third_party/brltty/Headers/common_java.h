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

#ifndef BRLTTY_INCLUDED_COMMON_JAVA
#define BRLTTY_INCLUDED_COMMON_JAVA

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define JAVA_METHOD(object, name, type) \
  JNIEXPORT type JNICALL Java_ ## object ## _ ## name (JNIEnv *env,

#define JAVA_INSTANCE_METHOD(object, name, type, ...) \
  JAVA_METHOD(object, name, type) jobject this, ## __VA_ARGS__)

#define JAVA_STATIC_METHOD(object, name, type, ...) \
  JAVA_METHOD(object, name, type) jclass class, ## __VA_ARGS__)

#define JAVA_SIG_VOID                      "V"
#define JAVA_SIG_BOOLEAN                   "Z"
#define JAVA_SIG_BYTE                      "B"
#define JAVA_SIG_CHAR                      "C"
#define JAVA_SIG_SHORT                     "S"
#define JAVA_SIG_INT                       "I"
#define JAVA_SIG_LONG                      "J"
#define JAVA_SIG_FLOAT                     "F"
#define JAVA_SIG_DOUBLE                    "D"
#define JAVA_SIG_OBJECT(path)              "L" path ";"
#define JAVA_SIG_ARRAY(element)            "[" element
#define JAVA_SIG_METHOD(returns,arguments) "(" arguments ")" returns

#define JAVA_CONSTRUCTOR_NAME "<init>"
#define JAVA_SIG_CONSTRUCTOR(arguments)    JAVA_SIG_METHOD(JAVA_SIG_VOID, arguments)

#define JAVA_OBJ_LANG(name) "java/lang/" name
#define JAVA_OBJ_IO(name) "java/io/" name
#define JAVA_OBJ_UTIL(name) "java/util/" name
#define JAVA_OBJ_CONCURRENT(name) JAVA_OBJ_UTIL("concurrent/" name)

#define JAVA_OBJ_CHAR_SEQUENCE JAVA_OBJ_LANG("CharSequence")
#define JAVA_OBJ_CLASS JAVA_OBJ_LANG("Class")
#define JAVA_OBJ_EOF_EXCEPTION JAVA_OBJ_IO("EOFException")
#define JAVA_OBJ_ILLEGAL_ARGUMENT_EXCEPTION JAVA_OBJ_LANG("IllegalArgumentException")
#define JAVA_OBJ_ILLEGAL_STATE_EXCEPTION JAVA_OBJ_LANG("IllegalStateException")
#define JAVA_OBJ_INTERRUPTED_IO_EXCEPTION JAVA_OBJ_IO("InterruptedIOException")
#define JAVA_OBJ_ITERATOR JAVA_OBJ_UTIL("Iterator")
#define JAVA_OBJ_LOCALE JAVA_OBJ_UTIL("Locale")
#define JAVA_OBJ_NULL_POINTER_EXCEPTION JAVA_OBJ_LANG("NullPointerException")
#define JAVA_OBJ_OBJECT JAVA_OBJ_LANG("Object")
#define JAVA_OBJ_OUT_OF_MEMORY_ERROR JAVA_OBJ_LANG("OutOfMemoryError")
#define JAVA_OBJ_STRING JAVA_OBJ_LANG("String")
#define JAVA_OBJ_THREAD JAVA_OBJ_LANG("Thread")
#define JAVA_OBJ_TIMEOUT_EXCEPTION JAVA_OBJ_CONCURRENT("TimeoutException")
#define JAVA_OBJ_UNSATISFIED_LINK_ERROR JAVA_OBJ_LANG("UnsatisfiedLinkError")

#define JAVA_SIG_CHAR_SEQUENCE JAVA_SIG_OBJECT(JAVA_OBJ_CHAR_SEQUENCE)
#define JAVA_SIG_CLASS JAVA_SIG_OBJECT(JAVA_OBJ_CLASS)
#define JAVA_SIG_ITERATOR JAVA_SIG_OBJECT(JAVA_OBJ_ITERATOR)
#define JAVA_SIG_LOCALE JAVA_SIG_OBJECT(JAVA_OBJ_LOCALE)
#define JAVA_SIG_STRING JAVA_SIG_OBJECT(JAVA_OBJ_STRING)
#define JAVA_SIG_THREAD JAVA_SIG_OBJECT(JAVA_OBJ_THREAD)

#define JAVA_CLASS_VARIABLE(name) jclass name = NULL
#define JAVA_METHOD_VARIABLE(name) jmethodID name = 0;

static inline int
javaFindClass (JNIEnv *env, jclass *class, const char *name) {
  if (*class) return 1;
  return !!(*class = (*env)->FindClass(env, name));
}

static inline int
javaFindMethod (
  JNIEnv *env, jmethodID *method, jclass class,
  const char *name, const char *signature
) {
  if (*method) return 1;
  return !!(*method = (*env)->GetMethodID(env, class, name, signature));
}

#define JAVA_FIND_METHOD(env, method, class, name, arguments, returns) \
(javaFindMethod(env, method, class, name, JAVA_SIG_METHOD(returns, arguments)))

#define JAVA_FIND_CONSTRUCTOR(env, constructor, class, arguments) \
(javaFindMethod(env, constructor, class, JAVA_CONSTRUCTOR_NAME, JAVA_SIG_CONSTRUCTOR(arguments)))

#define javaPtrToLong(p) ((jlong)(intptr_t)(p))
#define javaPtrFromLong(l) ((void *)(intptr_t)(l))

static inline int
javaFindClassAndMethod (
  JNIEnv *env,
  jclass *class, const char *className,
  jmethodID *method, const char *methodName,
  const char *signature
) {
  return javaFindClass(env, class, className)
      && javaFindMethod(env, method, *class, methodName, signature);
}

static inline jboolean
javaHasExceptionOccurred (JNIEnv *env) {
  return (*env)->ExceptionCheck(env);
}

static inline jthrowable
javaGetException (JNIEnv *env) {
  return (*env)->ExceptionOccurred(env);
}

static inline void
javaDescribeException (JNIEnv *env) {
  return (*env)->ExceptionDescribe(env);
}

static inline void
javaClearException (JNIEnv *env) {
  return (*env)->ExceptionClear(env);
}

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_COMMON_JAVA */

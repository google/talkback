/*
 * Copyright (C) 2019 Google Inc.
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

#ifndef JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_TRANSLATE_LIBLOUIS_JNI_LIBLOUISWRAPPER_LOUIS_TRANSLATION_H_
#define JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_TRANSLATE_LIBLOUIS_JNI_LIBLOUISWRAPPER_LOUIS_TRANSLATION_H_

#include <jni.h>

#define JNI_METHOD(fn) \
  Java_com_google_android_accessibility_braille_translate_liblouis_LouisTranslation_##fn  // NOLINT

extern "C" {

JNIEXPORT jboolean JNICALL JNI_METHOD(checkTableNative)(JNIEnv* env,
                                                        jclass clazz,
                                                        jstring tableName);

JNIEXPORT jobject JNICALL JNI_METHOD(translateNative)(JNIEnv* env, jclass clazz,
                                                      jobject text,
                                                      jstring tableName,
                                                      jint cursorPosition);

JNIEXPORT jstring JNICALL JNI_METHOD(backTranslateNative)(
    JNIEnv* env, jclass clazz, jbyteArray cells, jstring tableName, jint mode);

JNIEXPORT jboolean JNICALL JNI_METHOD(setTablesDirNative)(JNIEnv* env,
                                                          jclass clazz,
                                                          jstring pat);

JNIEXPORT void JNICALL JNI_METHOD(classInitNative)(JNIEnv* env, jclass clazz);
}  // extern "C"
#endif  // JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_TRANSLATE_LIBLOUIS_JNI_LIBLOUISWRAPPER_LOUIS_TRANSLATION_H_

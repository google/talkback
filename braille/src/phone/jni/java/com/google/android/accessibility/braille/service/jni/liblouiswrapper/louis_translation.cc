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

#include "java/com/google/android/accessibility/braille/service/jni/liblouiswrapper/louis_translation.h"

#include <vector>

#include "java/com/google/android/accessibility/braille/service/jni/alog.h"
#include "third_party/liblouis/liblouis/internal.h"  // for MAXSTRING
#include "third_party/liblouis/liblouis/liblouis.h"
#define TRANSLATE_PACKAGE \
  "com/google/android/accessibility/braille/service/translate/"
#define LOG_TAG "LibLouisWrapper_Native"

static jclass class_TranslationResult;
static jmethodID method_TranslationResult_ctor;

JNIEXPORT jboolean JNICALL JNI_METHOD(checkTableNative)(JNIEnv* env,
                                                        jclass clazz,
                                                        jstring tableName) {
  const char* table_name_utf8 = env->GetStringUTFChars(tableName, nullptr);
  jboolean ret = lou_getTable(table_name_utf8) ? JNI_TRUE : JNI_FALSE;
  env->ReleaseStringUTFChars(tableName, table_name_utf8);
  return ret;
}

// Translates print-characters to braille-cells. It returns a TranslationResult
// object.
JNIEXPORT jobject JNICALL JNI_METHOD(translateNative)(
    JNIEnv* env, jclass clazz, jstring text, jstring tableName,
    jint cursorPosition, jboolean computerBrailleAtCursor) {
  const jchar* text_utf16 = env->GetStringChars(text, nullptr);
  const char* table_name_utf8 = env->GetStringUTFChars(tableName, nullptr);
  const int in_len = env->GetStringLength(text);
  std::vector<int> output_pos(in_len);  // Maps char -> cell pos.

  int cursor_out_pos = -1;
  int* cursor_pos_pointer = nullptr;
  if (cursorPosition < in_len && cursorPosition >= 0) {
    cursor_out_pos = cursorPosition;
    cursor_pos_pointer = &cursor_out_pos;
  }

  // See <https://crrev.com/243251> for equivalent ChromeVox implementation.
  // Invoke liblouis.  Do this in a loop since we can't precalculate the
  // translated size.  We start with the min allocation size (8 jchars or 16
  // bytes); for a larger input length, we start at double the input length.
  // We also set an arbitrary upper bound for the allocation to make sure the
  // loop exits without running out of memory. For non-small input lengths, the
  // loop runs up to 4 times (in_len * 2, in_len * 4, in_len * 8, in_len * 16).
  int out_used = 0;
  std::vector<widechar> out_buf;
  // The opposite of output_pos: maps cell -> char pos.
  std::vector<int> input_pos;

  // Min buffer size is 8 jchars or 16 bytes.
  // For non-small values of in_len, the loop repeats up to 4 times (in_len * 2,
  // in_len * 4, in_len * 8, in_len * 16).
  const int max_out_len = in_len * 16;
  int out_len = std::max(8, in_len * 2);
  for (; out_len <= max_out_len; out_len *= 2) {
    int in_used = in_len;
    out_used = out_len;
    out_buf.assign(out_len, 0);
    input_pos.assign(out_len, 0);
    int result = lou_translate(
        table_name_utf8, text_utf16, &in_used, out_buf.data(), &out_used,
        /* typeform= */ nullptr, /* spacing= */ nullptr, output_pos.data(),
        input_pos.data(), cursor_pos_pointer,
        computerBrailleAtCursor ? compbrlAtCursor | dotsIO : dotsIO);
    if (result == 0) {
      LOGE("Translation failed.");
      return nullptr;
    }

    // If not all of in_buf was consumed, the output buffer must be too small
    // and we have to retry with a larger buffer.
    // In addition, if all of out_buf was exhausted, there's no way to know if
    // more space was needed, so we'll have to retry the translation in that
    // corner case as well.
    if (in_used == in_len && out_used < out_len) {
      LOGI(
          "Successfully translated %d characters to %d cells, "
          "consuming %d characters",
          env->GetStringLength(text), out_used, in_used);
      break;
    }
  }
  jbyteArray cells_array = env->NewByteArray(out_used);
  if (cells_array == nullptr) {
    return nullptr;
  }
  jbyte* cells = env->GetByteArrayElements(cells_array, nullptr);
  if (cells == nullptr) {
    return nullptr;
  }
  for (int i = 0; i < out_used; ++i) {
    // We only support 8 dots.
    cells[i] = out_buf[i] & 0xff;
  }
  env->ReleaseByteArrayElements(cells_array, cells, 0);
  jintArray output_pos_array = env->NewIntArray(in_len);
  if (output_pos_array == nullptr) {
    return nullptr;
  }
  env->SetIntArrayRegion(output_pos_array, 0, in_len, output_pos.data());
  jintArray input_pos_array = env->NewIntArray(out_used);
  if (input_pos_array == nullptr) {
    return nullptr;
  }
  env->SetIntArrayRegion(input_pos_array, 0, out_used, input_pos.data());
  if (cursor_pos_pointer == nullptr && cursorPosition >= 0) {
    // The cursor position was past-the-end of the input, normalize to
    // past-the-end of the output.
    cursor_out_pos = out_used;
  }
  return env->NewObject(class_TranslationResult, method_TranslationResult_ctor,
                        cells_array, output_pos_array, input_pos_array,
                        cursor_out_pos);
}

// Translates braille-cells to print-characters.
JNIEXPORT jstring JNICALL JNI_METHOD(backTranslateNative)(
    JNIEnv* env, jclass clazz, jbyteArray cells, jstring tableName, jint mode) {
  const char* table_name_utf8 = env->GetStringUTFChars(tableName, nullptr);
  if (!table_name_utf8) {
    return nullptr;
  }

  const int in_len = env->GetArrayLength(cells);
  jbyte* cells_bytes = env->GetByteArrayElements(cells, nullptr);
  std::vector<widechar> in_buf(in_len);
  for (int i = 0; i < in_len; ++i) {
    // The "| 0x8000" tells LibLouis that this is a dot pattern.
    in_buf[i] = (static_cast<unsigned char>(cells_bytes[i])) | 0x8000;
  }
  env->ReleaseByteArrayElements(cells, cells_bytes, JNI_ABORT);

  // See <https://crrev.com/254023> for equivalent ChromeVox implementation.
  // Invoke liblouis.  Do this in a loop since we can't precalculate the
  // translated size.  We start with the min allocation size (8 jchars or 16
  // bytes); for a larger input length, we start at double the input length.
  // We also set an arbitrary upper bound for the allocation to make sure the
  // loop exits without running out of memory. For non-small input lengths,
  // the loop runs up to 4 times (in_len * 2, in_len * 4, in_len * 8, in_len *
  // 16).
  int out_used = 0;
  std::vector<widechar> out_buf;
  const int max_out_len = in_len * 16;
  int out_len = std::max(8, in_len * 2);
  for (; out_len <= max_out_len; out_len *= 2) {
    int in_used = in_len;
    out_used = out_len;
    out_buf.assign(out_len, 0);
    int result = lou_backTranslateString(table_name_utf8, in_buf.data(),
                                         &in_used, out_buf.data(), &out_used,
                                         /* typeform= */ nullptr,
                                         /* spacing= */ nullptr, mode);
    if (result == 0) {
      LOGE("Back translation failed.");
      return nullptr;
    }

    // If not all of in_buf was consumed, the output buffer must be too small
    // and we have to retry with a larger buffer.
    // In addition, if all of outbuf was exhausted, there's no way to know if
    // more space was needed, so we'll have to retry the translation in that
    // corner case as well.
    // Example: 0x1f -> "quite"; we initially allocate space for 4 chars, but
    // we need 5. After lou_backTranslateString, inused = 1 and outused = 4.
    // So it appears that the translation finished, but we're missing a char.
    if (in_used == in_len && out_used < out_len) {
      LOGI(
          "Successfully translated %d cells into %d characters, "
          "consuming %d cells",
          env->GetArrayLength(cells), out_used, in_used);
      break;
    }
  }
  return env->NewString(out_buf.data(), out_used);
}

JNIEXPORT jboolean JNICALL JNI_METHOD(setTablesDirNative)(JNIEnv* env,
                                                          jclass clazz,
                                                          jstring path) {
  // liblouis has a static buffer, which we don't want to overflow.
  if (env->GetStringUTFLength(path) >= MAXSTRING) {
    LOGE("Braille table path too long");
    return JNI_FALSE;
  }
  const char* path_utf8 = env->GetStringUTFChars(path, nullptr);
  if (!path_utf8) {
    return JNI_FALSE;
  }
  LOGI("Setting tables path to: %s", path_utf8);
  lou_setDataPath(const_cast<char*>(path_utf8));
  env->ReleaseStringUTFChars(path, path_utf8);
  return JNI_TRUE;
}

static jclass getGlobalClassRef(JNIEnv* env, const char* name) {
  jclass localRef = env->FindClass(name);
  if (!localRef) {
    LOGE("Couldn't find class %s", name);
    return nullptr;
  }
  jclass globalRef = static_cast<jclass>(env->NewGlobalRef(localRef));
  if (globalRef == nullptr) {
    LOGE("Couldn't create global ref for class %s", name);
  }
  return globalRef;
}

JNIEXPORT void JNICALL JNI_METHOD(classInitNative)(JNIEnv* env, jclass clazz) {
  if (!(class_TranslationResult =
            getGlobalClassRef(env, TRANSLATE_PACKAGE "TranslationResult"))) {
    return;
  }
  method_TranslationResult_ctor =
      env->GetMethodID(class_TranslationResult, "<init>", "([B[I[II)V");
}

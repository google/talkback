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
 * Logging macros.  A file that includes this file is expected
 * to define LOG_TAG to be used when logging to the Android logging
 * system.
 */

#ifndef JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_COMMON_JNI_ALOG_H_
#define JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_COMMON_JNI_ALOG_H_

#ifdef __ANDROID__
#include <android/log.h>
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#include "base/logging.h"
#include "third_party/absl/strings/str_format.h"
// --v is default=0. Specify --v=-1 or lower would silence it.
#define LOGV(fstr, ...) VLOG(0) << ::absl::StreamFormat(fstr, ##__VA_ARGS__);
#define LOGI(fstr, ...) LOG(INFO) << ::absl::StreamFormat(fstr, ##__VA_ARGS__);
#define LOGE(fstr, ...) LOG(ERROR) << ::absl::StreamFormat(fstr, ##__VA_ARGS__);
#endif

#endif  // JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_COMMON_JNI_ALOG_H_

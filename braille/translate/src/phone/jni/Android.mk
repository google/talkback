# Copyright 2015 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

WRAPPER_PATH := $(call my-dir)/liblouiswrapper
LIBLOUIS_PATH := $(call my-dir)/third_party/liblouis
BRAILLE_ROOT_PATH := $(call my-dir)/../../../..

#----------------------------------------------------------------
# liblouiswrap

include $(CLEAR_VARS)

LOCAL_PATH := $(WRAPPER_PATH)
LOCAL_LDLIBS := -llog -landroid
LOCAL_MODULE := louiswrap
LOCAL_C_INCLUDES := $(WRAPPER_PATH)/.. $(LIBLOUIS_PATH)
LOCAL_CFLAGS := -I$(BRAILLE_ROOT_PATH)/common/src/phone/jni
LOCAL_SRC_FILES := louis_translation.cc
LOCAL_WHOLE_STATIC_LIBRARIES := liblouis

include $(BUILD_SHARED_LIBRARY)

#----------------------------------------------------------------
# liblouis

include $(CLEAR_VARS)

LOCAL_PATH := $(LIBLOUIS_PATH)
LOCAL_LDLIBS := -llog -landroid
LOCAL_CFLAGS += -DTABLESDIR='"__non_existent_path__"'
LOCAL_MODULE := louis
LOCAL_SRC_FILES := \
  liblouis/compileTranslationTable.c \
  liblouis/logging.c \
  liblouis/lou_backTranslateString.c \
  liblouis/lou_translateString.c \
  liblouis/commonTranslationFunctions.c \
  liblouis/maketable.c \
  liblouis/metadata.c \
  liblouis/pattern.c \
  liblouis/utils.c
LOCAL_C_INCLUDES :=  $(LIBLOUIS_PATH)

include $(BUILD_SHARED_LIBRARY)

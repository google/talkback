#
# Copyright (C) 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_PACKAGE_NAME := TalkBack

# Value of ${applicationId}
MANIFEST_PACKAGE := com.android.talkback
MANIFEST_VERSION_NAME := 5.0.7_aosp

# Replace ${applicationId} in AndroidManifest.xml
MANIFEST_LOCAL := $(LOCAL_PATH)/src/main/AndroidManifest.xml
MANIFEST_GENERATED := $(call intermediates-dir-for,APPS,$(LOCAL_PACKAGE_NAME))/AndroidManifest.xml
$(MANIFEST_GENERATED): PRIVATE_CUSTOM_TOOL = sed -e 's/$${applicationId}/$(MANIFEST_PACKAGE)/g' $< > $@
$(MANIFEST_GENERATED): $(MANIFEST_LOCAL)
	$(transform-generated-source)

# Generate BuildConfig.java and replace ${applicationId}
BUILDCONFIG_LOCAL := $(LOCAL_PATH)/src/compat/java/BuildConfig.java.in
BUILDCONFIG_GENERATED := $(call intermediates-dir-for,APPS,$(LOCAL_PACKAGE_NAME))/BuildConfig.java
$(BUILDCONFIG_GENERATED): PRIVATE_CUSTOM_TOOL = sed -e 's/$${applicationId}/$(MANIFEST_PACKAGE)/g' $< > $@
$(BUILDCONFIG_GENERATED): $(BUILDCONFIG_LOCAL)
	$(transform-generated-source)
LOCAL_GENERATED_SOURCES += $(BUILDCONFIG_GENERATED)

LOCAL_MODULE_TAGS := optional
LOCAL_FULL_MANIFEST_FILE := $(MANIFEST_GENERATED)
LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4
LOCAL_SRC_FILES := $(call all-java-files-under, src/main/java src/aosp/java src/compat/java)
LOCAL_RESOURCE_DIR := $(LOCAL_PATH)/src/main/res $(LOCAL_PATH)/src/compat/res
LOCAL_PROGUARD_FLAG_FILES := src/main/proguard-project.txt
LOCAL_AAPT_FLAGS := \
	--auto-add-overlay \
	--version-name $(MANIFEST_VERSION_NAME) \
	--rename-manifest-package $(MANIFEST_PACKAGE)

include $(BUILD_PACKAGE)

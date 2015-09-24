LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)
# We only want this apk build for tests.
LOCAL_MODULE_TAGS := tests
# Include all test java files.
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_RESOURCE_DIR += $(LOCAL_PATH)/res
LOCAL_CERTIFICATE := vendor/unbundled_google/libraries/certs/talkback
LOCAL_PACKAGE_NAME := TalkbackTests
LOCAL_SDK_VERSION := current
LOCAL_INSTRUMENTATION_FOR := TalkBack
LOCAL_STATIC_JAVA_LIBRARIES := android-support-test
#LOCAL_STATIC_JAVA_LIBRARIES += mockito-target
#LOCAL_STATIC_JAVA_LIBRARIES += ub-uiautomator
#LOCAL_STATIC_JAVA_LIBRARIES += instrumentation-memoryhelpers
ifneq ($(TARGET_BUILD_VARIANT),eng)
LOCAL_PROGUARD_ENABLED := obfuscation
endif
include $(BUILD_PACKAGE)
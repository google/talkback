# Copyright 2022 Google Inc. All Rights Reserved.
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

# This file defines the make function build-braille-drivers to  build a
# set of modules for a list of brltty braille drivers.

DRIVER_BASE_PATH := Drivers/Braille

# This gets appended the name of each driver module.
DRIVER_MODULES := $(empty)

# Used by the included driver make files to locate the braille.mk file.
# The slash at the end is required.
SRC_TOP := $(WRAPPER_PATH)/driver/

# Internal template to generate the code to build a driver module.
define ev-build-braille-driver
include $$(CLEAR_VARS)
LOCAL_PATH := $$(BRLTTY_PATH)
DRIVER_PATH := $$(DRIVER_BASE_PATH)/$1
LOCAL_C_INCLUDES := \
	$$(BRLTTY_PATH) \
	$$(BRLTTY_PATH)/Programs \
	$$(BRLTTY_PATH)/Headers \
	$$(BRLTTY_PATH)/for_talkback \
	$$(WRAPPER_PATH)
# Set by the included make file if the sources are not the standard one.
SRC_FILES := $$(empty)
# Clear variables that should be set by the included make file.
DRIVER_NAME := $$(empty)
DRIVER_CODE := $$(empty)
DRIVER_COMMENT := $$(empty)
DRIVER_VERSION := $$(empty)
DRIVER_DEVELOPERS := $$(empty)
include $$(LOCAL_PATH)/$$(DRIVER_PATH)/Makefile.in
ifeq ($$(SRC_FILES),$$(empty))
LOCAL_SRC_FILES := $$(DRIVER_PATH)/braille.c
else
LOCAL_SRC_FILES := $$(patsubst %,$$(DRIVER_PATH)/%,$$(SRC_FILES))
endif
LOCAL_MODULE := libbr$$(DRIVER_CODE)
LOCAL_CFLAGS := $$(VISIBILITY) \
	'-DDRIVER_NAME=$$(DRIVER_NAME)' '-DDRIVER_CODE=$$(DRIVER_CODE)' \
	'-DDRIVER_COMMENT="$$(DRIVER_COMMENT)"' \
	'-DDRIVER_VERSION="$$(DRIVER_VERSION)"' \
	'-DDRIVER_DEVELOPERS="$$(DRIVER_DEVELOPERS)"' \
	-DHAVE_CONFIG_H
LOCAL_CFLAGS+=-std=c99
include $$(BUILD_STATIC_LIBRARY)
DRIVER_MODULES += $$(LOCAL_MODULE)
endef

# Builds a braille driver given the directory (relative to
# brltty/Drivers/Braille) of the driver code.
build-braille-driver = $(eval $(call ev-build-braille-driver,$1))

# Builds the specified list of braille drivers.
build-braille-drivers = $(foreach dir,$1,$(call build-braille-driver,$(dir)))

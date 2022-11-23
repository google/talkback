# Copyright 2022 Google Inc.
#
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

LOCAL_PATH := $(call my-dir)
WRAPPER_PATH := $(LOCAL_PATH)/brlttywrapper
BRLTTY_PATH := $(WRAPPER_PATH)/third_party/brltty
BRAILLE_ROOT_PATH := $(call my-dir)/../../../..


include $(WRAPPER_PATH)/driver/driver.mk

# Uncomment the second line below and comment out the first one
# to get a smaller binary with less symbols.
VISIBILITY=
#VISIBILITY=-fvisibility=hidden

#----------------------------------------------------------------
# List of brltty drivers that are included.  If adding a new driver,
# include the directory name of the driver in the below list.
# ADD_DEVICE_SUPPORT
#
# TODO: Gets back EuroBraille.

$(call build-braille-drivers,\
	Voyager \
	FreedomScientific \
	HumanWare \
	Baum \
	Papenmeier \
	HIMS \
	Alva \
	Seika \
	HandyTech \
       )

#----------------------------------------------------------------
# brlttywrap

include $(CLEAR_VARS)

LOCAL_PATH := $(WRAPPER_PATH)
LOCAL_MODULE    := brlttywrap
LOCAL_LDFLAGS := $(BRLTTY_LDFLAGS)
LOCAL_LDLIBS := -llog
LOCAL_C_INCLUDES := $(BRLTTY_PATH)/Programs $(BRLTTY_PATH)/Headers
LOCAL_CFLAGS := -I$(BRAILLE_ROOT_PATH)/common/src/phone/jni
LOCAL_SRC_FILES := BrlttyWrapper.c
LOCAL_WHOLE_STATIC_LIBRARIES := libbrltty-android

include $(BUILD_SHARED_LIBRARY)

#----------------------------------------------------------------
# libbrltty-android

include $(CLEAR_VARS)

LOCAL_PATH := $(WRAPPER_PATH)

LOCAL_C_INCLUDES := $(BRLTTY_PATH) \
	$(BRLTTY_PATH)/Programs \
	$(BRLTTY_PATH)/Headers \
	$(BRLTTY_PATH)/for_talkback \
	$(LOCAL_PATH)

LOCAL_CFLAGS+=-DHAVE_CONFIG_H $(VISIBILITY)
LOCAL_CFLAGS+=-D__ANDROID__

LOCAL_SRC_FILES:= \
	libbrltty.c \
	bluetooth_android.c \
	sys_android.c \
	prefs.c

LOCAL_MODULE := brltty-android
LOCAL_WHOLE_STATIC_LIBRARIES := libbrltty
include $(BUILD_STATIC_LIBRARY)

#----------------------------------------------------------------
# libbrltty
include $(CLEAR_VARS)

LOCAL_PATH := $(BRLTTY_PATH)

LOCAL_C_INCLUDES:= $(BRLTTY_PATH) \
	$(BRLTTY_PATH)/Programs \
	$(BRLTTY_PATH)/Headers \
  $(BRLTTY_PATH)/for_talkback \
	$(WRAPPER_PATH)

LOCAL_CFLAGS+=-DHAVE_CONFIG_H $(VISIBILITY)
LOCAL_CFLAGS+=-D__ANDROID__
LOCAL_CFLAGS+=-std=c99

LOCAL_SRC_FILES:= \
	Programs/cmd.c \
	Programs/charset.c \
	Programs/charset_none.c \
	Programs/lock.c \
	Programs/drivers.c \
	Programs/driver.c \
	Programs/ttb_translate.c \
	Programs/ttb_compile.c \
	Programs/ttb_native.c

# Base objects
LOCAL_SRC_FILES+= \
	Programs/addresses.c \
	Programs/dynld_none.c \
	Programs/log_history.c \
	Programs/system_java.c \
	Programs/log.c \
	Programs/file.c \
	Programs/device.c \
	Programs/parse.c \
	Programs/timing.c \
	Programs/variables.c \
	Programs/thread.c \
	Programs/report.c

# Braille objects
LOCAL_SRC_FILES+= \
	Programs/brl.c \
	Programs/brl_base.c \
	Programs/brl_driver.c \
	Programs/brl_utils.c

# IO objects
LOCAL_SRC_FILES+= \
	Programs/io_misc.c \
	Programs/gio.c \
	Programs/gio_null.c \
	Programs/gio_serial.c \
	Programs/gio_usb.c \
	Programs/gio_bluetooth.c

# Bluetooth objects
LOCAL_SRC_FILES+= \
	Programs/bluetooth.c \
	Programs/bluetooth_names.c

# Other, not sure where they come from.
LOCAL_SRC_FILES+= \
  Programs/unicode.c \
	Programs/queue.c \
	Programs/serial.c \
	Programs/serial_none.c \
	Programs/usb.c \
	Programs/usb_devices.c \
	Programs/usb_none.c \
	Programs/usb_hid.c \
	Programs/usb_serial.c \
	Programs/usb_ftdi.c \
	Programs/usb_belkin.c \
	Programs/usb_cp2101.c \
	Programs/usb_cp2110.c \
	Programs/usb_cdc_acm.c \
	Programs/usb_adapters.c \
	Programs/ktb_translate.c \
	Programs/ktb_compile.c \
	Programs/ktb_list.c \
	Programs/ktb_cmds.c \
	Programs/async_io.c \
	Programs/async_alarm.c \
	Programs/async_event.c \
	Programs/async_handle.c \
	Programs/async_wait.c \
	Programs/async_task.c \
	Programs/async_data.c \
	Programs/async_signal.c \
	Programs/datafile.c \
	Programs/dataarea.c \
	Programs/cmd_queue.c \
	Programs/hidkeys.c

LOCAL_MODULE := brltty
LOCAL_WHOLE_STATIC_LIBRARIES := $(DRIVER_MODULES)
include $(BUILD_STATIC_LIBRARY)
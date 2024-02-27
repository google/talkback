/*
 * Copyright (C) 2022 Google Inc.
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
 * Usb functionality that works on the Android NDK.
 */
#ifndef JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_BRLTTY_IMPL_JNI_BRLTTYWRAPPER_USB_ANDROID_H_
#define JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_BRLTTY_IMPL_JNI_BRLTTYWRAPPER_USB_ANDROID_H_

#include <unistd.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/*
 * Store a context struct that will be used when a usb
 * connection is 'opened' by the brltty driver.  This is global
 * state: there can be only one connection at a time.
 */
void usbAndroidSetContext(jobject context);

#ifdef __cplusplus
}
#endif  /* __cplusplus */
#endif  // JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_BRLTTY_IMPL_JNI_BRLTTYWRAPPER_USB_ANDROID_H_

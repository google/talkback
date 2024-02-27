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
 * This is a library to expose a brlapi-like interface that can be linked
 * into another binary.  The intended use is on Android, compiled under
 * the NDK, meaning that some system and I/O abstractons must be
 * provided by the user of the library.
 *
 * Usage:
 *
 * All this must be called from one and only one thread from initialization to
 * destruction.  There is global state maintianed by this library internally,
 * meaning there can only be one driver active at a time.  This is why there
 * is no 'handle' object for the driver.  Each initialization call should be
 * followed at some point by a matching destroy call.
 */

#ifndef JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_BRLTTY_JNI_BRLTTYWRAPPER_LIBBRLTTY_H_
#define JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_BRLTTY_JNI_BRLTTYWRAPPER_LIBBRLTTY_H_

#include <stddef.h>
#include <string.h>
#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/*
 * If this flag is set in the argument of CMD_ROUTE, this is a long
 * press route.  The flag is put in the argument instead of the separate
 * flag bits, because it can be included in the keymap (by using
 * CMD_ROUTE+128).
 */
#define BRLTTY_ROUTE_ARG_FLG_LONG_PRESS 0X80

/*
 * Maximum number text cells that are supported.  Since the high
 * order bit is used to indicate long press, and the maximum 7-bit value is
 * used for the 'activate current' command, we end up with this value.
 */
#define BRLTTY_MAX_TEXT_CELLS 0X7F

/*
 * Initializes a given braille driver, trying to connect to a given
 * device.  Returns non-zero on success.
 */
int
brltty_initialize(const char* driverCode, const char* brailleDevice,
                  const char* tablesDir);

/*
 * Closes the connection and deallocates resources for a braille
 * driver.
 */
int
brltty_destroy(void);

/*
 * Polls the driver for a single key command.  This call is non-blocking.
 * If no command is available, EOF is returned.
 * If readDelay is >0 on return, a new call will be scheduled after that
 * number of milliseconds, even if no more input data has been detected at that
 * time.
 */
int
brltty_readCommand(int *readDelayMillis);

/*
 * Updates the display with a dot pattern.  dotPattern should contain
 * at least size bytes, one for each braille cell.
 * Further, size should match the size of the display.
 * If it doesn't, the patterns will be silently truncated or padded
 * with blank cells.
 */
int
brltty_writeWindow(unsigned char *dotPattern, size_t size);

/*
 * Returns the number of cells that are present on the display.
 * This does not include any status cells that are separate from the
 * main display.
 */
int
brltty_getTextCells(void);


/*
 * Returns the total number of dedicated status cells, that is cells that are
 * separate from the main display.  This is 0 if the display lacks status
 * cells.
 */
int
brltty_getStatusCells(void);

/*
 * Handles a command that resulted from a keypress.
 */
int
brltty_handleCommand(int command, void *data);

/*
 * Pops the top command off of the command queue, or returns EOF if the queue
 * is empty.
 */
int
brltty_popCommand();

/*
 * Callback used with brltty_listKeyMap.
 */
typedef int (*KeyMapEntryCallback)(int command, int keyCount,
                                   const char *keys[],
                                   int isLongPress,
                                   void *data);

/*
 * List the keyboard bindings loaded for the currently connected
 * display.  Invokes the callback for each key binding.
 * data is part of the closure for the callback.
 */
int
brltty_listKeyMap(KeyMapEntryCallback callback, void* data);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif  // JAVA_COM_GOOGLE_ANDROID_ACCESSIBILITY_BRAILLE_BRLTTY_JNI_BRLTTYWRAPPER_LIBBRLTTY_H_
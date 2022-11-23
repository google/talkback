/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2019 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.app/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

#ifndef BRLTTY_INCLUDED_DEVICE
#define BRLTTY_INCLUDED_DEVICE

#include "prologue.h"

#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern FILE *getConsole (void);
extern int writeConsole (const unsigned char *bytes, size_t count);
extern int ringBell (void);

extern const char *getDeviceDirectory (void);
extern char *getDevicePath (const char *device);
extern const char *resolveDeviceName (const char *const *names, const char *description);

#define DEVICE_PARAMETER_SEPARATOR '+'
extern char **getDeviceParameters (const char *const *names, const char *identifier);

#undef ALLOW_DOS_DEVICE_NAMES
#if defined(__MSDOS__) || (defined(WINDOWS) && !defined(__CYGWIN__))
#define ALLOW_DOS_DEVICE_NAMES 1
extern int isDosDevice (const char *identifier, const char *prefix);
#endif /* DOS or Windows (but not Cygwin) */

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_DEVICE */

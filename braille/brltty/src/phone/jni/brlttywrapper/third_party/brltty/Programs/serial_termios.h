/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2023 by The BRLTTY Developers.
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

#ifndef BRLTTY_INCLUDED_SERIAL_TERMIOS
#define BRLTTY_INCLUDED_SERIAL_TERMIOS

#include <termios.h>
#include <sys/ioctl.h>

#ifdef HAVE_SYS_MODEM_H
#include <sys/modem.h>
#endif /* HAVE_SYS_MODEM_H */

#include "async_handle.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef speed_t SerialSpeed;
typedef struct termios SerialAttributes;

typedef int SerialLines;
#define SERIAL_LINE_RTS TIOCM_RTS
#define SERIAL_LINE_DTR TIOCM_DTR
#define SERIAL_LINE_CTS TIOCM_CTS
#define SERIAL_LINE_DSR TIOCM_DSR
#define SERIAL_LINE_RNG TIOCM_RNG
#define SERIAL_LINE_CAR TIOCM_CAR

typedef struct {
  AsyncHandle inputMonitor;
} SerialPackageFields;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SERIAL_TERMIOS */

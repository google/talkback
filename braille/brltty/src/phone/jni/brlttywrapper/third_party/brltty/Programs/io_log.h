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

#ifndef BRLTTY_INCLUDED_IO_LOG
#define BRLTTY_INCLUDED_IO_LOG

#include "serial_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern void logUnsupportedBaud (unsigned int baud);
extern void logUnsupportedDataBits (unsigned int dataBits);
extern void logUnsupportedStopBits (SerialStopBits stopBits);
extern void logUnsupportedParity (SerialParity parity);
extern void logUnsupportedFlowControl (SerialFlowControl flowControl);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_IO_LOG */

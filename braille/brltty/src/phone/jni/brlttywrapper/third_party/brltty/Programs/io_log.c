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

#include "prologue.h"

#include "io_log.h"
#include "log.h"

void
logUnsupportedBaud (unsigned int baud) {
  logMessage(LOG_WARNING, "unsupported baud: %u", baud);
}

void
logUnsupportedDataBits (unsigned int dataBits) {
  logMessage(LOG_WARNING, "unsupported data bits: %u", dataBits);
}

void
logUnsupportedStopBits (SerialStopBits stopBits) {
  logMessage(LOG_WARNING, "unsupported stop bits: %u", stopBits);
}

void
logUnsupportedParity (SerialParity parity) {
  logMessage(LOG_WARNING, "unsupported parity: %u", parity);
}

void
logUnsupportedFlowControl (SerialFlowControl flowControl) {
  logMessage(LOG_WARNING, "unsupported flow control: %02X", flowControl);
}

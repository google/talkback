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

/* BrailleLite/braille.h - Configurable definitions for the Braille Lite driver
 * N. Nair, 6 September 1998
 *
 * Edit as necessary for your system.
 */

/* used by speech.c */
#include "io_serial.h"
extern SerialDevice *BL_serialDevice;

/* We always expect 8 data bits, no parity, 1 stop bit. */
/* Select baudrate to use */
#define BAUDRATE 9600
//#define BAUDRATE 38400

/* Define the following for dots to character mapping for input to use 
   the same (user-defined) table as is used for output, instead of the
   hard-coded US table. */
#define USE_TEXTTRANS

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

/* MultiBraille/braille.h - Configurable definitions for the
 * following Tieman B.V. braille terminals
 * (infos out of a techn. product description sent to me from tieman by fax):
 *
 * - Brailleline 125 (no explicit description in tech. docs)
 * - Brailleline PICO II or MB145CR (45 braille modules + 1 dummy)
 * - Brailleline MB185CR (85 braille modules + 1 dummy)
 *
 * Wolfgang Astleitner, March/April 2000
 * Email: wolfgang.astleitner@liwest.at
 * brlconf.h,v 1.0
 *
 * Based on CombiBraille/brlconf.h by Nikhil Nair
 *
 * Edit as necessary for your system.
 */

/* used by braille.c */
#include "io_serial.h"
extern SerialDevice *MB_serialDevice;

#define BAUDRATE 38400		/* baud rate for Braille display */

/* The following sequences are sent at initialisation time, at termination
 * and before and after Braille data.  The first byte is the length of the
 * sequence.
 *
 * Initialisation is treated specially, as there may not be a Braille
 * display connected.  This relies on a reply from the display.
 */
#define ACK_TIMEOUT 5000	/* acknowledgement timeout in milliseconds */
#define MAX_ATTEMPTS 100		/* total tiimeout = timeout * attempts */
				/* try forever if MAX_ATTEMPTS = 0 */

#define MB_CR_EXTRAKEYS 6  /* amount of extra cursor routing keys. */
                           /* should always be 6 (-> tech. docs) */ 

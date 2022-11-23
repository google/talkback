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

/* EcoBraille/braille.h - Configurable definitions for the Eco Braille series
 * Copyright (C) 1999 by Oscar Fernandez <ofa@once.es>
 *
 * Edit as necessary for your system.
 */

/* Device Identification Numbers (not to be changed) */
#define ECO_AUTO	-1
#define ECO_20		1
#define ECO_40		2
#define ECO_80     	3
#define NB_MODEL        4


/***** User Settings *****/
#define MODEL   ECO_AUTO

/* serial line baudrate... 
 * Note that default braille device is defined in ../Makefile
 */
#define BAUDRATE 19200

/* typematic settings */
#define TYPEMATIC_DELAY 10	/* nbr of cycles before a key is repeated */
#define TYPEMATIC_REPEAT 2	/* nbr of cycles between each key repeat */

/* Delay in miliseconds between forced full refresh of the display.
 * This is to minimize garbage effects due to noise on the serial line.
 */
#define REFRESH_RATE 1000

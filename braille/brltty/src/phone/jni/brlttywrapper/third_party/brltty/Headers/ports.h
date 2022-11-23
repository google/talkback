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

#ifndef BRLTTY_INCLUDED_PORTS
#define BRLTTY_INCLUDED_PORTS

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int enablePorts (int errorLevel, unsigned short int base, unsigned short int count);
extern int disablePorts (unsigned short int base, unsigned short int count);

extern unsigned char readPort1 (unsigned short int port);
extern void writePort1 (unsigned short int port, unsigned char value);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_PORTS */

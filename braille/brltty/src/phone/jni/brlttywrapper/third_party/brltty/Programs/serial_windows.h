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

#ifndef BRLTTY_INCLUDED_SERIAL_WINDOWS
#define BRLTTY_INCLUDED_SERIAL_WINDOWS

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef DWORD SerialSpeed;
typedef DCB SerialAttributes;

typedef DWORD SerialLines;
#define SERIAL_LINE_RTS 0X01
#define SERIAL_LINE_DTR 0X02
#define SERIAL_LINE_CTS MS_CTS_ON
#define SERIAL_LINE_DSR MS_DSR_ON
#define SERIAL_LINE_RNG MS_RING_ON
#define SERIAL_LINE_CAR MS_RLSD_ON

typedef struct {
  HANDLE fileHandle;
  int pendingCharacter;
} SerialPackageFields;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SERIAL_WINDOWS */

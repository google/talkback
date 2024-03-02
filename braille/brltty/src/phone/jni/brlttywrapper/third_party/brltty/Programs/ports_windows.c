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

#include "ports.h"
#include "system_windows.h"

#define USE_PORTS_X86

static int portsEnabled = 0;

int
enablePorts (int errorLevel, unsigned short int base, unsigned short int count) {
  if (!portsEnabled && NtSetInformationProcessProc) {
    ULONG Iopl=3;
    if (NtSetInformationProcessProc(GetCurrentProcess(), ProcessUserModeIOPL,
                                &Iopl, sizeof(Iopl)) != STATUS_SUCCESS) {
      return 0;
    }
    portsEnabled = 1;
  }
  return 1;
}

int
disablePorts (unsigned short int base, unsigned short int count) {
  return 1;
}

#include "ports_x86.h"

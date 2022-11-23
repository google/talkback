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

#include "prologue.h"

#include <grub/cpu/io.h>

#include "ports.h"

int
enablePorts (int errorLevel, unsigned short int base, unsigned short int count) {
  return 1;
}

int
disablePorts (unsigned short int base, unsigned short int count) {
  return 1;
}

unsigned char
readPort1 (unsigned short int port) {
  return grub_inb(port);
}

void
writePort1 (unsigned short int port, unsigned char value) {
  grub_outb(value, port);
}

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

#include "log.h"
#include "driver.h"

void
unsupportedDeviceIdentifier (const char *identifier) {
  logMessage(LOG_WARNING, "unsupported device identifier: %s", identifier);
}

void
logOutputPacket (const void *packet, size_t size) {
  logBytes(LOG_CATEGORY(OUTPUT_PACKETS), "sent", packet, size);
}

void
logInputPacket (const void *packet, size_t size) {
  logBytes(LOG_CATEGORY(INPUT_PACKETS), NULL, packet, size);
}

void
logInputProblem (const char *problem, const unsigned char *bytes, size_t count) {
  logBytes(LOG_WARNING, "%s", bytes, count, problem);
}

void
logIgnoredByte (unsigned char byte) {
  logInputProblem("Ignored Byte", &byte, 1);
}

void
logDiscardedByte (unsigned char byte) {
  logInputProblem("Discarded Byte", &byte, 1);
}

void
logUnknownPacket (unsigned char byte) {
  logInputProblem("Unknown Packet", &byte, 1);
}

void
logPartialPacket (const void *packet, size_t size) {
  logInputProblem("Partial Packet", packet, size);
}

void
logTruncatedPacket (const void *packet, size_t size) {
  logInputProblem("Truncated Packet", packet, size);
}

void
logShortPacket (const void *packet, size_t size) {
  logInputProblem("Short Packet", packet, size);
}

void
logUnexpectedPacket (const void *packet, size_t size) {
  logInputProblem("Unexpected Packet", packet, size);
}

void
logCorruptPacket (const void *packet, size_t size) {
  logInputProblem("Corrupt Packet", packet, size);
}

void
logDiscardedBytes (const unsigned char *bytes, size_t count) {
  logInputProblem("Discarded Bytes", bytes, count);
}

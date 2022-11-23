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
#include "pcm.h"

PcmDevice *
openPcmDevice (int errorLevel, const char *device) {
  logMessage(errorLevel, "PCM device not supported.");
  return NULL;
}

void
closePcmDevice (PcmDevice *pcm) {
}

int
writePcmData (PcmDevice *pcm, const unsigned char *buffer, int count) {
  return 0;
}

int
getPcmBlockSize (PcmDevice *pcm) {
  return 0X100;
}

int
getPcmSampleRate (PcmDevice *pcm) {
  return 8000;
}

int
setPcmSampleRate (PcmDevice *pcm, int rate) {
  return getPcmSampleRate(pcm);
}

int
getPcmChannelCount (PcmDevice *pcm) {
  return 1;
}

int
setPcmChannelCount (PcmDevice *pcm, int channels) {
  return getPcmChannelCount(pcm);
}

PcmAmplitudeFormat
getPcmAmplitudeFormat (PcmDevice *pcm) {
  return PCM_FMT_UNKNOWN;
}

PcmAmplitudeFormat
setPcmAmplitudeFormat (PcmDevice *pcm, PcmAmplitudeFormat format) {
  return getPcmAmplitudeFormat(pcm);
}

void
pushPcmOutput (PcmDevice *pcm) {
}

void
awaitPcmOutput (PcmDevice *pcm) {
}

void
cancelPcmOutput (PcmDevice *pcm) {
}

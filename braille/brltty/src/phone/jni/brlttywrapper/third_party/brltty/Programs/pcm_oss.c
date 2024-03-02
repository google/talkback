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

#include <string.h>
#include <errno.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/soundcard.h>

#include "log.h"
#include "io_misc.h"
#include "pcm.h"

#define PCM_OSS_DEVICE_PATH "/dev/dsp"

#ifndef SNDCTL_DSP_SPEED
#define SNDCTL_DSP_SPEED SOUND_PCM_WRITE_RATE
#endif /* SNDCTL_DSP_SPEED */

#ifndef SNDCTL_DSP_CHANNELS
#define SNDCTL_DSP_CHANNELS SOUND_PCM_WRITE_CHANNELS
#endif /* SNDCTL_DSP_CHANNELS */

struct PcmDeviceStruct {
  int fileDescriptor;
  int driverVersion;
  int sampleRate;
  int channelCount;
};

PcmDevice *
openPcmDevice (int errorLevel, const char *device) {
  PcmDevice *pcm;
  if ((pcm = malloc(sizeof(*pcm)))) {
    if (!*device) device = PCM_OSS_DEVICE_PATH;
    if ((pcm->fileDescriptor = open(device, O_WRONLY|O_NONBLOCK)) != -1) {
      /* Nonblocking if snd_seq_oss is loaded with nonblock_open=1.
       * There appears to be a bug in this case as write() always
       * returns the full count even though large chunks of sound are
       * missing. For now, therefore, force blocking output.
       */
      setBlockingIo(pcm->fileDescriptor, 1);

      pcm->driverVersion = 0X030000;
#ifdef OSS_GETVERSION
      if (ioctl(pcm->fileDescriptor, OSS_GETVERSION, &pcm->driverVersion) == -1)
        logMessage(errorLevel, "cannot get OSS driver version");
#endif /* OSS_GETVERSION */
      logMessage(LOG_DEBUG, "OPSS driver version: %06X", pcm->driverVersion);

      setPcmSampleRate(pcm, 8000);
      setPcmChannelCount(pcm, 1);
      return pcm;
    } else {
      logMessage(errorLevel, "cannot open PCM device: %s: %s", device, strerror(errno));
    }
    free(pcm);
  } else {
    logSystemError("PCM device allocation");
  }
  return NULL;
}

void
closePcmDevice (PcmDevice *pcm) {
  close(pcm->fileDescriptor);
  free(pcm);
}

int
writePcmData (PcmDevice *pcm, const unsigned char *buffer, int count) {
  return writeFile(pcm->fileDescriptor, buffer, count) != -1;
}

int
getPcmBlockSize (PcmDevice *pcm) {
  int fragmentCount = (1 << 0X10) - 1;
  int fragmentShift = 7;
  int fragmentSize = 1 << fragmentShift;
  int fragmentSetting = (fragmentCount << 0X10) | fragmentShift;
  ioctl(pcm->fileDescriptor, SNDCTL_DSP_SETFRAGMENT, &fragmentSetting);

  {
    int blockSize;
    if (ioctl(pcm->fileDescriptor, SNDCTL_DSP_GETBLKSIZE, &blockSize) != -1) return blockSize;
  }
  return fragmentSize;
}

int
getPcmSampleRate (PcmDevice *pcm) {
  return pcm->sampleRate;
}

int
setPcmSampleRate (PcmDevice *pcm, int rate) {
  if (ioctl(pcm->fileDescriptor, SNDCTL_DSP_SPEED, &rate) != -1) pcm->sampleRate = rate;
  return getPcmSampleRate(pcm);
}

int
getPcmChannelCount (PcmDevice *pcm) {
  return pcm->channelCount;
}

int
setPcmChannelCount (PcmDevice *pcm, int channels) {
  if (ioctl(pcm->fileDescriptor, SNDCTL_DSP_CHANNELS, &channels) != -1) pcm->channelCount = channels;
  return getPcmChannelCount(pcm);
}

typedef struct {
  PcmAmplitudeFormat internal;
  int external;
} AmplitudeFormatEntry;
static const AmplitudeFormatEntry amplitudeFormatTable[] = {
  {PCM_FMT_U8     , AFMT_U8    },
  {PCM_FMT_S8     , AFMT_S8    },
  {PCM_FMT_U16B   , AFMT_U16_BE},
  {PCM_FMT_S16B   , AFMT_S16_BE},
  {PCM_FMT_U16L   , AFMT_U16_LE},
  {PCM_FMT_S16L   , AFMT_S16_LE},
  {PCM_FMT_ULAW   , AFMT_MU_LAW},
  {PCM_FMT_ALAW   , AFMT_A_LAW},
  {PCM_FMT_UNKNOWN, AFMT_QUERY }
};

static PcmAmplitudeFormat
doPcmAmplitudeFormat (PcmDevice *pcm, int format) {
  if (ioctl(pcm->fileDescriptor, SNDCTL_DSP_SETFMT, &format) != -1) {
    const AmplitudeFormatEntry *entry = amplitudeFormatTable;
    while (entry->internal != PCM_FMT_UNKNOWN) {
      if (entry->external == format) return entry->internal;
      ++entry;
    }
  }
  return PCM_FMT_UNKNOWN;
}

PcmAmplitudeFormat
getPcmAmplitudeFormat (PcmDevice *pcm) {
  return doPcmAmplitudeFormat(pcm, AFMT_QUERY);
}

PcmAmplitudeFormat
setPcmAmplitudeFormat (PcmDevice *pcm, PcmAmplitudeFormat format) {
  const AmplitudeFormatEntry *entry = amplitudeFormatTable;
  while (entry->internal != PCM_FMT_UNKNOWN) {
    if (entry->internal == format) break;
    ++entry;
  }
  return doPcmAmplitudeFormat(pcm, entry->external);
}

void
pushPcmOutput (PcmDevice *pcm) {
  ioctl(pcm->fileDescriptor, SNDCTL_DSP_POST, 0);
}

void
awaitPcmOutput (PcmDevice *pcm) {
  ioctl(pcm->fileDescriptor, SNDCTL_DSP_SYNC, 0);
}

void
cancelPcmOutput (PcmDevice *pcm) {
  ioctl(pcm->fileDescriptor, SNDCTL_DSP_RESET, 0);
}

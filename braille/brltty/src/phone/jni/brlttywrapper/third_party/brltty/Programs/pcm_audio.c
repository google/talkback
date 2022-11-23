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

#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/audio.h>
#include <stropts.h>

#include "log.h"
#include "io_misc.h"
#include "pcm.h"

#define PCM_AUDIO_DEVICE_PATH "/dev/audio"

struct PcmDeviceStruct {
  int fileDescriptor;
};

PcmDevice *
openPcmDevice (int errorLevel, const char *device) {
  PcmDevice *pcm;
  if ((pcm = malloc(sizeof(*pcm)))) {
    if (!*device) device = getenv("AUDIODEV");
    if (!device || !*device) device = PCM_AUDIO_DEVICE_PATH;
    if ((pcm->fileDescriptor = open(device, O_WRONLY|O_NONBLOCK)) != -1) {
      audio_info_t info;
      AUDIO_INITINFO(&info);
#ifdef AUMODE_PLAY
      info.mode = AUMODE_PLAY;
#endif /* AUMODE_PLAY */
#ifdef AUDIO_ENCODING_SLINEAR
      info.play.encoding = AUDIO_ENCODING_SLINEAR;
#else /* AUDIO_ENCODING_SLINEAR */
      info.play.encoding = AUDIO_ENCODING_LINEAR;
#endif /* AUDIO_ENCODING_SLINEAR */
      info.play.sample_rate = 16000;
      info.play.channels = 1;
      info.play.precision = 16;
      info.play.gain = AUDIO_MAX_GAIN;
      if (ioctl(pcm->fileDescriptor, AUDIO_SETINFO, &info) == -1)
        logMessage(errorLevel, "Cannot set audio info: %s", strerror(errno));
      return pcm;
    } else {
      logMessage(errorLevel, "Cannot open PCM device: %s: %s", device, strerror(errno));
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

static int
getPcmAudioInfo (PcmDevice *pcm, audio_info_t *info) {
  if (ioctl(pcm->fileDescriptor, AUDIO_GETINFO, info) != -1) return 1;
  logSystemError("AUDIO_GETINFO");
  return 0;
}

int
getPcmBlockSize (PcmDevice *pcm) {
  audio_info_t info;
  if (getPcmAudioInfo(pcm, &info)) return (info.play.precision / 8 * info.play.channels) * 0X400;
  return 0X100;
}

int
getPcmSampleRate (PcmDevice *pcm) {
  audio_info_t info;
  if (getPcmAudioInfo(pcm, &info)) return info.play.sample_rate;
  return 8000;
}

int
setPcmSampleRate (PcmDevice *pcm, int rate) {
  return getPcmSampleRate(pcm);
}

int
getPcmChannelCount (PcmDevice *pcm) {
  audio_info_t info;
  if (getPcmAudioInfo(pcm, &info)) return info.play.channels;
  return 1;
}

int
setPcmChannelCount (PcmDevice *pcm, int channels) {
  return getPcmChannelCount(pcm);
}

PcmAmplitudeFormat
getPcmAmplitudeFormat (PcmDevice *pcm) {
  audio_info_t info;
  if (getPcmAudioInfo(pcm, &info)) {
    switch (info.play.encoding) {
      default:
        break;

#ifdef AUDIO_ENCODING_SLINEAR_BE
      case AUDIO_ENCODING_SLINEAR_BE:
        if (info.play.precision == 16) return PCM_FMT_S16B;
        goto testLinearSigned8;
#endif /* AUDIO_ENCODING_SLINEAR_BE */

#ifdef AUDIO_ENCODING_SLINEAR_LE
      case AUDIO_ENCODING_SLINEAR_LE:
        if (info.play.precision == 16) return PCM_FMT_S16L;
        goto testLinearSigned8;
#endif /* AUDIO_ENCODING_SLINEAR_LE */

#ifdef AUDIO_ENCODING_LINEAR
      case AUDIO_ENCODING_LINEAR:
#ifdef WORDS_BIGENDIAN
        if (info.play.precision == 16) return PCM_FMT_S16B;
#else /* WORDS_BIGENDIAN */
        if (info.play.precision == 16) return PCM_FMT_S16L;
#endif /* WORDS_BIGENDIAN */
        goto testLinearSigned8;
#endif /* AUDIO_ENCODING_LINEAR */

      testLinearSigned8:
        if (info.play.precision == 8) return PCM_FMT_S8;
        break;

      case AUDIO_ENCODING_LINEAR8:
        return PCM_FMT_U8;

      case AUDIO_ENCODING_ULAW:
        return PCM_FMT_ULAW;

      case AUDIO_ENCODING_ALAW:
        return PCM_FMT_ALAW;
    }
  }
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
  ioctl(pcm->fileDescriptor, AUDIO_DRAIN);
}

void
cancelPcmOutput (PcmDevice *pcm) {
  ioctl(pcm->fileDescriptor, I_FLUSH);
}

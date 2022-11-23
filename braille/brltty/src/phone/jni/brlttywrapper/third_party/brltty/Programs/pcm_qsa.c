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

#include <sys/asoundlib.h>

#include "log.h"
#include "io_misc.h"
#include "pcm.h"

struct PcmDeviceStruct {
  int card;
  int device;
  snd_pcm_t *handle;
  snd_pcm_channel_params_t parameters;
};

static void
logPcmError (int level, const char *action, int code) {
  logMessage(level, "QSA PCM %s error: %s", action, snd_strerror(code));
}

static int
reconfigurePcmChannel (PcmDevice *pcm, int errorLevel) {
  int code;
  if ((code = snd_pcm_channel_params(pcm->handle, &pcm->parameters)) >= 0) {
    snd_pcm_channel_setup_t setup;
    setup.channel = pcm->parameters.channel;
    if ((code = snd_pcm_channel_setup(pcm->handle, &setup)) >= 0) {
      pcm->parameters.mode = setup.mode;
      pcm->parameters.format = setup.format;
      pcm->parameters.buf.block.frag_size = setup.buf.block.frag_size;
      pcm->parameters.buf.block.frags_min = setup.buf.block.frags_min;
      pcm->parameters.buf.block.frags_max = setup.buf.block.frags_max;
      return 1;
    } else {
      logPcmError(errorLevel, "get channel setup", code);
    }
  } else {
    logPcmError(errorLevel, "set channel parameters", code);
  }
  return 0;
}

PcmDevice *
openPcmDevice (int errorLevel, const char *device) {
  PcmDevice *pcm;
  if ((pcm = malloc(sizeof(*pcm)))) {
    int code;

    if (*device) {
      {
	int ok = 0;
	long number;
	char *end;
	const char *component = device;

	number = strtol(component, &end, 0);
	if ((*end && (*end != ':')) || (number < 0) || (number > 0XFF)) {
	  logMessage(errorLevel, "Invalid QSA card number: %s", device);
	} else if (end == component) {
	  logMessage(errorLevel, "Missing QSA card number: %s", device);
	} else {
	  pcm->card = number;

	  if (*end) {
	    component = end + 1;
	    number = strtol(component, &end, 0);
	    if (*end || (number < 0) || (number > 0XFF)) {
	      logMessage(errorLevel, "Invalid QSA device number: %s", device);
	    } else if (end == component) {
	      logMessage(errorLevel, "Missing QSA device number: %s", device);
	    } else {
	      pcm->device = number;
	      ok = 1;
	    }
	  } else {
	    pcm->device = 0;
	    ok = 1;
	  }
	}

	if (!ok) goto openError;
      }

      if ((code = snd_pcm_open(&pcm->handle, pcm->card, pcm->device, SND_PCM_OPEN_PLAYBACK)) < 0) {
	logPcmError(errorLevel, "open", code);
	goto openError;
      }
    } else if ((code = snd_pcm_open_preferred(&pcm->handle, &pcm->card, &pcm->device, SND_PCM_OPEN_PLAYBACK)) < 0) {
      logPcmError(errorLevel, "preferred open", code);
      goto openError;
    }
    logMessage(LOG_DEBUG, "QSA PCM device opened: %d:%d", pcm->card, pcm->device);

    {
      snd_pcm_channel_info_t info;
      info.channel = SND_PCM_CHANNEL_PLAYBACK;
      if ((code = snd_pcm_channel_info(pcm->handle, &info)) >= 0) {
	logMessage(LOG_DEBUG, "QSA PCM Info: Frag=%d-%d Rate=%d-%d Chan=%d-%d",
	           info.min_fragment_size, info.max_fragment_size,
	           info.min_rate, info.max_rate,
	           info.min_voices, info.max_voices);
	memset(&pcm->parameters, 0, sizeof(pcm->parameters));

	pcm->parameters.channel = info.channel;
	pcm->parameters.start_mode = SND_PCM_START_DATA;
	pcm->parameters.stop_mode = SND_PCM_STOP_ROLLOVER;

	switch (pcm->parameters.mode = SND_PCM_MODE_BLOCK) {
	  case SND_PCM_MODE_BLOCK:
	    pcm->parameters.buf.block.frag_size = MIN(MAX(0X400, info.min_fragment_size), info.max_fragment_size);
	    pcm->parameters.buf.block.frags_min = 1;
	    pcm->parameters.buf.block.frags_max = 0X40;
	    break;

	  default:
	    logMessage(LOG_WARNING, "Unsupported QSA PCM mode: %d", pcm->parameters.mode);
	    goto openError;
	}

	pcm->parameters.format.interleave = 1;
	pcm->parameters.format.rate = info.max_rate;
	pcm->parameters.format.voices = MIN(MAX(1, info.min_voices), info.max_voices);
	pcm->parameters.format.format = SND_PCM_SFMT_S16;

	if (reconfigurePcmChannel(pcm, errorLevel)) {
	  if ((code = snd_pcm_channel_prepare(pcm->handle, pcm->parameters.channel)) >= 0) {
	    return pcm;
	  } else {
	    logPcmError(errorLevel, "prepare channel", code);
	  }
	}
      } else {
        logPcmError(errorLevel, "get channel information", code);
      }
    }

  openError:
    free(pcm);
  } else {
    logSystemError("PCM device allocation");
  }

  return NULL;
}

void
closePcmDevice (PcmDevice *pcm) {
  int code;
  if ((code = snd_pcm_close(pcm->handle)) < 0) {
    logPcmError(LOG_WARNING, "close", code);
  }

  free(pcm);
}

int
writePcmData (PcmDevice *pcm, const unsigned char *buffer, int count) {
  return writeFile(snd_pcm_file_descriptor(pcm->handle, pcm->parameters.channel), buffer, count);
}

int
getPcmBlockSize (PcmDevice *pcm) {
  return pcm->parameters.buf.block.frag_size;
}

int
getPcmSampleRate (PcmDevice *pcm) {
  return pcm->parameters.format.rate;
}

int
setPcmSampleRate (PcmDevice *pcm, int rate) {
  pcm->parameters.format.rate = rate;
  reconfigurePcmChannel(pcm, LOG_WARNING);
  return getPcmSampleRate(pcm);
}

int
getPcmChannelCount (PcmDevice *pcm) {
  return pcm->parameters.format.voices;
}

int
setPcmChannelCount (PcmDevice *pcm, int channels) {
  pcm->parameters.format.voices = channels;
  reconfigurePcmChannel(pcm, LOG_WARNING);
  return getPcmChannelCount(pcm);
}

typedef struct {
  PcmAmplitudeFormat internal;
  int external;
} AmplitudeFormatEntry;
static const AmplitudeFormatEntry amplitudeFormatTable[] = {
  {PCM_FMT_U8     , SND_PCM_SFMT_U8     },
  {PCM_FMT_S8     , SND_PCM_SFMT_S8     },
  {PCM_FMT_U16B   , SND_PCM_SFMT_U16_BE },
  {PCM_FMT_S16B   , SND_PCM_SFMT_S16_BE },
  {PCM_FMT_U16L   , SND_PCM_SFMT_U16_LE },
  {PCM_FMT_S16L   , SND_PCM_SFMT_S16_LE },
  {PCM_FMT_ULAW   , SND_PCM_SFMT_MU_LAW },
  {PCM_FMT_UNKNOWN, SND_PCM_SFMT_SPECIAL}
};

PcmAmplitudeFormat
getPcmAmplitudeFormat (PcmDevice *pcm) {
  const AmplitudeFormatEntry *entry = amplitudeFormatTable;
  while (entry->internal != PCM_FMT_UNKNOWN) {
    if (entry->external == pcm->parameters.format.format) break;
    ++entry;
  }
  return entry->internal;
}

PcmAmplitudeFormat
setPcmAmplitudeFormat (PcmDevice *pcm, PcmAmplitudeFormat format) {
  const AmplitudeFormatEntry *entry = amplitudeFormatTable;
  while (entry->internal != PCM_FMT_UNKNOWN) {
    if (entry->internal == format) {
      pcm->parameters.format.format = format;
      reconfigurePcmChannel(pcm, LOG_WARNING);
      break;
    }
    ++entry;
  }
  return getPcmAmplitudeFormat(pcm);
}

void
pushPcmOutput (PcmDevice *pcm) {
}

void
awaitPcmOutput (PcmDevice *pcm) {
  int code;
  if ((code = snd_pcm_playback_flush(pcm->handle)) < 0) {
    logPcmError(LOG_WARNING, "flush", code);
  }
}

void
cancelPcmOutput (PcmDevice *pcm) {
  int code;
  if ((code = snd_pcm_playback_drain(pcm->handle)) < 0) {
    logPcmError(LOG_WARNING, "drain", code);
  }
}

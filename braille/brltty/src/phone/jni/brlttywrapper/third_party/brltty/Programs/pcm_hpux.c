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

#ifdef HAVE_HPUX_AUDIO
#include <Alib.h>
#include <sys/socket.h>
#include <netinet/in.h>
#endif /* HAVE_HPUX_AUDIO */

#include "log.h"
#include "pcm.h"

#ifdef HAVE_HPUX_AUDIO
static Audio *audioServer = NULL;

struct PcmDeviceStruct {
  ATransID transaction;
  SStream stream;
  int socket;
};

static void
logAudioError (int level, long status, const char *action) {
  char message[132];
  AGetErrorText(audioServer, status, message, sizeof(message)-1);
  logMessage(level, "%s error %ld: %s", action, status, message);
}

static const AudioAttributes *
getAudioAttributes (PcmDevice *pcm) {
  return &pcm->stream.audio_attr;
}
#endif /* HAVE_HPUX_AUDIO */

PcmDevice *
openPcmDevice (int errorLevel, const char *device) {
#ifdef HAVE_HPUX_AUDIO
  PcmDevice *pcm;
  if ((pcm = malloc(sizeof(*pcm)))) {
    long status;
    AudioAttrMask mask = 0;
    AudioAttributes attributes;
    SSPlayParams parameters;
  
    if (!audioServer) {
      char *server = "";
      audioServer = AOpenAudio(server, &status);
      if (status != AENoError) {
        logAudioError(errorLevel, status, "AOpenAudio");
        audioServer = NULL;
        goto noServer;
      }
      logMessage(LOG_DEBUG, "connected to audio server: %s", AAudioString(audioServer));
  
      ASetCloseDownMode(audioServer, AKeepTransactions, &status);
      if (status != AENoError) {
        logAudioError(errorLevel, status, "ASetCloseDownMode");
      }
    }
  
    memset(&attributes, 0, sizeof(attributes));
  
    parameters.gain_matrix = *ASimplePlayer(audioServer);
    parameters.play_volume = AUnityGain;
    parameters.priority = APriorityUrgent;
    parameters.event_mask = 0;
  
    pcm->transaction = APlaySStream(audioServer, mask, &attributes, &parameters, &pcm->stream, &status);
    if (status == AENoError) {
      if ((pcm->socket = socket(AF_INET, SOCK_STREAM, 0)) != -1) {
        if (connect(pcm->socket, (struct sockaddr *)&pcm->stream.tcp_sockaddr, sizeof(pcm->stream.tcp_sockaddr)) != -1) {
          return pcm;
        } else {
          logSystemError("PCM socket connection");
        }
        close(pcm->socket);
      } else {
        logSystemError("PCM socket creation");
      }
    } else {
      logAudioError(errorLevel, status, "APlaySStream");
    }

  noServer:
    free(pcm);
  } else {
    logSystemError("PCM device allocation");
  }
#endif /* HAVE_HPUX_AUDIO */
  return NULL;
}

void
closePcmDevice (PcmDevice *pcm) {
  close(pcm->socket);
  free(pcm);
}

int
writePcmData (PcmDevice *pcm, const unsigned char *buffer, int count) {
  return safe_write(pcm->socket, buffer, count) != -1;
}

int
getPcmBlockSize (PcmDevice *pcm) {
  int size = 0X100;
#ifdef HAVE_HPUX_AUDIO
  size = MIN(size, pcm->stream.max_block_size);
#endif /* HAVE_HPUX_AUDIO */
  return size;
}

int
getPcmSampleRate (PcmDevice *pcm) {
#ifdef HAVE_HPUX_AUDIO
  return getAudioAttributes(pcm)->attr.sampled_attr.sampling_rate;
#else /* HAVE_HPUX_AUDIO */
  return 8000;
#endif /* HAVE_HPUX_AUDIO */
}

int
setPcmSampleRate (PcmDevice *pcm, int rate) {
  return getPcmSampleRate(pcm);
}

int
getPcmChannelCount (PcmDevice *pcm) {
#ifdef HAVE_HPUX_AUDIO
  return getAudioAttributes(pcm)->attr.sampled_attr.channels;
#else /* HAVE_HPUX_AUDIO */
  return 1;
#endif /* HAVE_HPUX_AUDIO */
}

int
setPcmChannelCount (PcmDevice *pcm, int channels) {
  return getPcmChannelCount(pcm);
}

PcmAmplitudeFormat
getPcmAmplitudeFormat (PcmDevice *pcm) {
#ifdef HAVE_HPUX_AUDIO
  switch (getAudioAttributes(pcm)->attr.sampled_attr.data_format) {
    default:
      break;
    case ADFLin8:
      return PCM_FMT_S8;
    case ADFLin8Offset:
      return PCM_FMT_U8;
    case ADFLin16:
      return PCM_FMT_S16B;
    case ADFMuLaw:
      return PCM_FMT_ULAW;
  }
#endif /* HAVE_HPUX_AUDIO */
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

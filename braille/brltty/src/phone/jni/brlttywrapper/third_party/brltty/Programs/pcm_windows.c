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

#include "log.h"
#include "parse.h"
#include "pcm.h"

struct PcmDeviceStruct {
  HWAVEOUT handle;
  UINT deviceID;
  WAVEFORMATEX format;
  HANDLE done;
  WAVEHDR waveHdr;
  size_t bufSize;
};

static WAVEFORMATEX defaultFormat = { WAVE_FORMAT_PCM, 1, 11025, 11025, 1, 8, 0 };

static void
recomputeWaveOutFormat(WAVEFORMATEX *format) {
  format->nBlockAlign = format->nChannels * ((format->wBitsPerSample + 7) / 8);
  format->nAvgBytesPerSec = format->nBlockAlign * format->nSamplesPerSec;
}

static WAVEHDR initWaveHdr = { NULL, 0, 0, 0, 0, 1, NULL, 0 };

static void
LogWaveOutError(MMRESULT error, int errorLevel, const char *action) {
  char msg[MAXERRORLENGTH];
  waveOutGetErrorText(error, msg, sizeof(msg));
  logMessage(errorLevel, "%s error %d: %s.", action, error, msg);
}

PcmDevice *
openPcmDevice (int errorLevel, const char *device) {
  PcmDevice *pcm;
  MMRESULT mmres;
  WAVEOUTCAPS caps;
  int id = 0;

  if (*device) {
    if (!isInteger(&id, device) || (id < 0) || (id >= waveOutGetNumDevs())) {
      logMessage(errorLevel, "invalid PCM device number: %s", device);
      return NULL;
    }
  }

  if (!(pcm = malloc(sizeof(*pcm)))) {
    logSystemError("PCM device allocation");
    return NULL;
  }
  pcm->deviceID = id;

  if ((waveOutGetDevCaps(pcm->deviceID, &caps, sizeof(caps))) != MMSYSERR_NOERROR)
    pcm->format = defaultFormat;
  else {
    logMessage(errorLevel, "PCM device %d is %s", pcm->deviceID, caps.szPname);
    pcm->format.wFormatTag = WAVE_FORMAT_PCM;
    if (caps.dwFormats & 
	(WAVE_FORMAT_1S08
	|WAVE_FORMAT_1S16
	|WAVE_FORMAT_2S08
	|WAVE_FORMAT_2S16
	|WAVE_FORMAT_4S08
	|WAVE_FORMAT_4S16))
      pcm->format.nChannels = 2;
    else
      pcm->format.nChannels = 1;
    if (caps.dwFormats &
	(WAVE_FORMAT_4M08
	|WAVE_FORMAT_4M16
	|WAVE_FORMAT_4S08
	|WAVE_FORMAT_4S16))
      pcm->format.nSamplesPerSec = 44100;
    else if (caps.dwFormats &
	(WAVE_FORMAT_2M08
	|WAVE_FORMAT_2M16
	|WAVE_FORMAT_2S08
	|WAVE_FORMAT_2S16))
      pcm->format.nSamplesPerSec = 22050;
    else if (caps.dwFormats &
	(WAVE_FORMAT_1M08
	|WAVE_FORMAT_1M16
	|WAVE_FORMAT_1S08
	|WAVE_FORMAT_1S16))
      pcm->format.nSamplesPerSec = 11025;
    else {
      logMessage(errorLevel, "unknown PCM capability %#lx", caps.dwFormats);
      goto out;
    }
    if (caps.dwFormats &
	(WAVE_FORMAT_1M16
	|WAVE_FORMAT_1S16
	|WAVE_FORMAT_2M16
	|WAVE_FORMAT_2S16
	|WAVE_FORMAT_4M16
	|WAVE_FORMAT_4S16))
      pcm->format.wBitsPerSample = 16;
    else if (caps.dwFormats &
	(WAVE_FORMAT_1M08
	|WAVE_FORMAT_1S08
	|WAVE_FORMAT_2M08
	|WAVE_FORMAT_2S08
	|WAVE_FORMAT_4M08
	|WAVE_FORMAT_4S08))
      pcm->format.wBitsPerSample = 8;
    else {
      logMessage(LOG_ERR, "unknown PCM capability %#lx", caps.dwFormats);
      goto out;
    }
    recomputeWaveOutFormat(&pcm->format);
    pcm->format.cbSize = 0;
  }

  if (!(pcm->done = CreateEvent(NULL, FALSE, TRUE, NULL))) {
    logWindowsSystemError("creating PCM completion event");
    goto out;
  }

  pcm->waveHdr = initWaveHdr;
  pcm->bufSize = 0;

  if ((mmres = waveOutOpen(&pcm->handle, pcm->deviceID,
	  &pcm->format, (DWORD) pcm->done, 0, CALLBACK_EVENT)) != MMSYSERR_NOERROR) {
    LogWaveOutError(mmres, errorLevel, "opening PCM device");
    goto outEvent;
  }
  return pcm;

outEvent:
  CloseHandle(pcm->done);
out:
  free(pcm);
  return NULL;
}

static int
unprepareHeader(PcmDevice *pcm) {
  MMRESULT mmres;
  awaitPcmOutput(pcm);
  if ((mmres = waveOutUnprepareHeader(pcm->handle, &pcm->waveHdr, sizeof(pcm->waveHdr))) != MMSYSERR_NOERROR) {
    LogWaveOutError(mmres, LOG_ERR, "unpreparing PCM data header");
    return 0;
  }
  return 1;
}

static int
updateWaveOutFormat(PcmDevice *pcm, WAVEFORMATEX *format, const char *errmsg) {
  MMRESULT mmres;
  recomputeWaveOutFormat(format);
  if (!(unprepareHeader(pcm))) return 0;
  if (waveOutOpen(NULL, pcm->deviceID, format, 0, 0, WAVE_FORMAT_QUERY) == MMSYSERR_NOERROR) {
    waveOutClose(pcm->handle);
    pcm->handle = INVALID_HANDLE_VALUE;
    if ((mmres = waveOutOpen(&pcm->handle, pcm->deviceID, format,
	    (DWORD) pcm->done, 0, CALLBACK_EVENT)) == MMSYSERR_NOERROR) {
      pcm->format = *format;
      return 1;
    }
    LogWaveOutError(mmres, LOG_ERR, errmsg);
  }
  return 0;
}

void
closePcmDevice (PcmDevice *pcm) {
  CloseHandle(pcm->done);
  unprepareHeader(pcm);
  waveOutClose(pcm->handle);
  free(pcm->waveHdr.lpData);
  free(pcm);
}

int
writePcmData (PcmDevice *pcm, const unsigned char *buffer, int count) {
  MMRESULT mmres;
  void *newBuf;
  if (!count) return 1;
  if (count > pcm->bufSize) {
    if (!(unprepareHeader(pcm))) return 0;
    if (!(newBuf = realloc(pcm->waveHdr.lpData, 2 * count))) {
      logSystemError("allocating PCM data buffer");
      return 0;
    }
    pcm->waveHdr.lpData = newBuf;
    pcm->waveHdr.dwFlags = 0;
    pcm->waveHdr.dwBufferLength = pcm->bufSize = 2 * count;
  }
  awaitPcmOutput(pcm);
  if (!(pcm->waveHdr.dwFlags & WHDR_PREPARED))
    if ((mmres = waveOutPrepareHeader(pcm->handle, &pcm->waveHdr, sizeof(pcm->waveHdr))) != MMSYSERR_NOERROR) {
      LogWaveOutError(mmres, LOG_ERR, "preparing PCM data header");
      return 0;
    }
  pcm->waveHdr.dwBufferLength = count;
  memcpy(pcm->waveHdr.lpData, buffer, count);
  ResetEvent(pcm->done);
  if ((mmres = waveOutWrite(pcm->handle, &pcm->waveHdr, sizeof(pcm->waveHdr))) != MMSYSERR_NOERROR) {
    SetEvent(pcm->done);
    LogWaveOutError(mmres, LOG_ERR, "writing PCM data");
    return 0;
  }
  return 1;
}

int
getPcmBlockSize (PcmDevice *pcm) {
  return 0X10000;
}

int
getPcmSampleRate (PcmDevice *pcm) {
  return pcm->format.nSamplesPerSec;
}

int
setPcmSampleRate (PcmDevice *pcm, int rate) {
  WAVEFORMATEX format = pcm->format;
  format.nSamplesPerSec = rate;
  if (!updateWaveOutFormat(pcm, &format, "setting PCM sample rate"))
    return getPcmSampleRate(pcm);
  else
    return rate;
}

int
getPcmChannelCount (PcmDevice *pcm) {
  return pcm->format.nChannels;
}

int
setPcmChannelCount (PcmDevice *pcm, int channels) {
  WAVEFORMATEX format = pcm->format;
  format.nChannels = channels;
  if (!updateWaveOutFormat(pcm, &format, "setting PCM channel count"))
    return getPcmChannelCount(pcm);
  else
    return channels;
}

PcmAmplitudeFormat
getPcmAmplitudeFormat (PcmDevice *pcm) {
  if (pcm->format.wBitsPerSample == 8) return PCM_FMT_U8;
  if (pcm->format.wBitsPerSample == 16) return PCM_FMT_S16L;
  return PCM_FMT_UNKNOWN;
}

PcmAmplitudeFormat
setPcmAmplitudeFormat (PcmDevice *pcm, PcmAmplitudeFormat format) {
  WAVEFORMATEX newFormat = pcm->format;
  if (format == PCM_FMT_U8) newFormat.wBitsPerSample = 8;
  else if (format == PCM_FMT_S16L) newFormat.wBitsPerSample = 16;
  else return getPcmAmplitudeFormat(pcm);
  if (!updateWaveOutFormat(pcm, &newFormat, "setting PCM amplitude format"))
    return getPcmAmplitudeFormat(pcm);
  else
    return format;
}

void
pushPcmOutput (PcmDevice *pcm) {
}

void
awaitPcmOutput (PcmDevice *pcm) {
  while ((pcm->waveHdr.dwFlags & WHDR_PREPARED)
		  && !(pcm->waveHdr.dwFlags & WHDR_DONE))
    WaitForSingleObject(pcm->done, INFINITE);
  SetEvent(pcm->done);
}

void
cancelPcmOutput (PcmDevice *pcm) {
  waveOutReset(pcm->handle);
}

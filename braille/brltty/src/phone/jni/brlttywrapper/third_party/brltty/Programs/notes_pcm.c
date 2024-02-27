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

#include "prefs.h"
#include "log.h"
#include "pcm.h"
#include "notes.h"

char *opt_pcmDevice;

struct NoteDeviceStruct {
  PcmDevice *pcm;

  int blockSize;
  int sampleRate;
  int channelCount;
  PcmAmplitudeFormat amplitudeFormat;

  unsigned char *blockAddress;
  int blockUsed;

  PcmSampleMaker makeSample;
};

static int
pcmFlushBytes (NoteDevice *device) {
  int ok = writePcmData(device->pcm, device->blockAddress, device->blockUsed);
  if (ok) device->blockUsed = 0;
  return ok;
}

static int
pcmWriteSample (NoteDevice *device, int16_t amplitude) {
  PcmSample *sample = (PcmSample *)&device->blockAddress[device->blockUsed];
  PcmSampleSize size = device->makeSample(sample, amplitude);
  device->blockUsed += size;

  for (int channel=1; channel<device->channelCount; channel+=1) {
    for (int byte=0; byte<size; byte+=1) {
      device->blockAddress[device->blockUsed++] = sample->bytes[byte];
    }
  }

  if (device->blockUsed == device->blockSize) {
    if (!pcmFlushBytes(device)) {
      return 0;
    }
  }

  return 1;
}

static int
pcmFlushBlock (NoteDevice *device) {
  while (device->blockUsed)
    if (!pcmWriteSample(device, 0))
      return 0;

  return 1;
}

static NoteDevice *
pcmConstruct (int errorLevel) {
  NoteDevice *device;

  if ((device = malloc(sizeof(*device)))) {
    memset(device, 0, sizeof(*device));

    if ((device->pcm = openPcmDevice(errorLevel, opt_pcmDevice))) {
      device->blockSize = getPcmBlockSize(device->pcm);
      device->sampleRate = getPcmSampleRate(device->pcm);
      device->channelCount = getPcmChannelCount(device->pcm);
      device->amplitudeFormat = getPcmAmplitudeFormat(device->pcm);

      device->blockUsed = 0;
      device->makeSample = getPcmSampleMaker(device->amplitudeFormat);

      PcmSample sample;
      PcmSampleSize sampleSize = device->makeSample(&sample, 0);
      sampleSize *= device->channelCount;

      if (sampleSize && device->blockSize &&
          !(device->blockSize % sampleSize)) {
        if ((device->blockAddress = malloc(device->blockSize))) {
          logMessage(LOG_DEBUG, "PCM enabled: BlkSz:%d Rate:%d ChnCt:%d Fmt:%d",
                     device->blockSize, device->sampleRate, device->channelCount, device->amplitudeFormat);
          return device;
        } else {
          logMallocError();
        }
      } else {
        logMessage(LOG_ERR,
                   "PCM block size not multiple of sample size:"
                   " BlkSz:%d" " SmpSz:%u",
                   device->blockSize, sampleSize);
      }

      closePcmDevice(device->pcm);
    }

    free(device);
  } else {
    logMallocError();
  }

  logMessage(LOG_DEBUG, "PCM not available");
  return NULL;
}

static void
pcmDestruct (NoteDevice *device) {
  pcmFlushBlock(device);
  free(device->blockAddress);
  closePcmDevice(device->pcm);
  free(device);
  logMessage(LOG_DEBUG, "PCM disabled");
}

static int
pcmTone (NoteDevice *device, unsigned int duration, NoteFrequency frequency) {
  int32_t sampleCount = device->sampleRate * duration / 1000;

  logMessage(LOG_DEBUG, "tone: MSecs:%u SmpCt:%"PRId32 " Freq:%"PRIfreq,
             duration, sampleCount, frequency);

  if (frequency) {
    /* A triangle waveform sounds nice, is lightweight, and avoids
     * relying too much on floating-point performance and/or on
     * expensive math functions like sin(). Considerations like
     * these are especially important on PDAs without any FPU.
     */ 

    /* We need to know the maximum amplitude based on the currently set
     * volume percentage. This percentage then needs to be squared because
     * we perceive loudness exponentially.
     */
    const unsigned char fullVolume = 100;
    const unsigned char currentVolume = MIN(fullVolume, prefs.pcmVolume);
    const int32_t maximumAmplitude = INT16_MAX
                                   * (currentVolume * currentVolume)
                                   / (fullVolume * fullVolume);

    /* The calculations for triangle wave generation work out nicely and
     * efficiently if we map a full period onto a 32-bit unsigned range.
     */

    /* The two high-order bits specify which quarter wave a sample is for.
     *   00 -> ascending from the negative peak to zero
     *   01 -> ascending from zero to the positive peak
     *   10 -> descending from the positive peak to zero
     *   11 -> descending from zero to the negative peak
     * The higher bit is 0 for the ascending segment and 1 for the
     * descending segment. The lower bit is 0 when going from a peak to
     * zero and 1 when going from zero to a peak.
     */
    const uint8_t magnitudeWidth = 32 - 2;

    /* The amplitude is 0 when the lower bit of the quarter wave indicator
     * is 1 and the rest of the (magnitude) bits are all 0.
     */
    const uint32_t zeroValue = UINT32_C(1) << magnitudeWidth;

    /* We need to know how many steps to make from one sample to the next.
     * stepsPerSample = stepsPerWave * wavesPerSecond / samplesPerSecond
     *                = stepsPerWave * frequency / sampleRate
     *                = stepsPerWave / sampleRate * frequency
     */
    const uint32_t stepsPerSample = (NoteFrequency)UINT32_MAX 
                                  / (NoteFrequency)device->sampleRate
                                  * frequency;

    /* The current value needs to be a signed value so that the >> operator
     * will extend its sign bit. We start by initializing it to the value
     * that corresponds to the start of the first logical quarter wave
     * (the one that ascends from zero to the positive peak).
     */
    int32_t currentValue = zeroValue;

    /* Round the number of samples up to a whole number of periods:
     * partialSteps = (sampleCount * stepsPerSample) % stepsPerWave
     *
     * With stepsPerWave being (1 << 32), we simply let the product
     * overflow. The modulus corresponds to the remaining 32 low bits:
     * partialSteps = (uint32_t)(sampleCount * stepsPerSample)
     *
     * missingSteps = stepsPerWave - partialSteps
     *              = (uint32_t) -partialSteps

     * extraSamples = missingSteps / stepsPerSample
     */
    sampleCount += (uint32_t)(sampleCount * -stepsPerSample) / stepsPerSample;

    while (sampleCount > 0) {
      /* Convert the current 32-bit unsigned linear value to a 31-bit
       * triangular amplitude by inverting its low-order 31 bits if its
       * high-order (sign) bit is set.
       */
      int32_t amplitude = currentValue ^ (currentValue >> 31);

      /* Convert the 31-bit amplitude from unsigned to signed. */
      amplitude -= zeroValue;

      /* Convert the amplitude's magnitude from 30 bits to 16 bits. */
      amplitude >>= magnitudeWidth - 16;

      /* Adjust the 17-bit signed amplitude (sign bit + 16-bit value) by
       * the currently set volume (15-bit value):
       * (16-bit value) * (15-bit value) + (sign bit) = 32-bit signed value
       */
      amplitude *= maximumAmplitude;

      /* Convert the signed amplitude from 32 bits to 16 bits. */
      amplitude >>= 16;

      if (!pcmWriteSample(device, amplitude)) break;
      currentValue += stepsPerSample;
      sampleCount -= 1;
    }
  } else {
    /* generate silence */
    while (sampleCount > 0) {
      if (!pcmWriteSample(device, 0)) break;
      sampleCount -= 1;
    }
  }

  return (sampleCount > 0) ? 0 : 1;
}

static int
pcmNote (NoteDevice *device, unsigned int duration, unsigned char note) {
  return pcmTone(device, duration, getNoteFrequency(note));
}

static int
pcmFlush (NoteDevice *device) {
  int ok = pcmFlushBlock(device);
  if (ok) pushPcmOutput(device->pcm);
  return ok;
}

const NoteMethods pcmNoteMethods = {
  .construct = pcmConstruct,
  .destruct = pcmDestruct,

  .tone = pcmTone,
  .note = pcmNote,
  .flush = pcmFlush
};

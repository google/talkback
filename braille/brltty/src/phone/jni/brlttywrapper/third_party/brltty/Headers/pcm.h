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

#ifndef BRLTTY_INCLUDED_PCM
#define BRLTTY_INCLUDED_PCM

#include "prologue.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  PCM_FMT_S8,   // signed, 8 bits, linear
  PCM_FMT_U8,   // unsigned, 8 bits, linear

  PCM_FMT_S16B, // signed, 16 bits, linear, big endian
  PCM_FMT_U16B, // unsigned, 16 bits, linear, big endian

  PCM_FMT_S16L, // signed, 16 bits, linear, little endian
  PCM_FMT_U16L, // unsigned, 16 bits, linear, little endian

  PCM_FMT_ULAW, // signed, 8 bits, logarithmic
  PCM_FMT_ALAW, // signed, 8 bits, logarithmic

  PCM_FMT_UNKNOWN
} PcmAmplitudeFormat;

#ifdef WORDS_BIGENDIAN
#define PCM_FMT_S16N PCM_FMT_S16B
#define PCM_FMT_U16N PCM_FMT_U16B
#else /* WORDS_BIGENDIAN */
#define PCM_FMT_S16N PCM_FMT_S16L
#define PCM_FMT_U16N PCM_FMT_U16L
#endif /* WORDS_BIGENDIAN */

#define PCM_MAX_SAMPLE_SIZE 2

typedef union {
  uint8_t bytes[PCM_MAX_SAMPLE_SIZE];
} PcmSample;

typedef uint8_t PcmSampleSize;
typedef PcmSampleSize (*PcmSampleMaker) (PcmSample *sample, int16_t amplitude);
extern PcmSampleMaker getPcmSampleMaker (PcmAmplitudeFormat format);

typedef struct PcmDeviceStruct PcmDevice;

extern PcmDevice *openPcmDevice (int errorLevel, const char *device);
extern void closePcmDevice (PcmDevice *pcm);

extern int getPcmBlockSize (PcmDevice *pcm);

extern int getPcmSampleRate (PcmDevice *pcm);
extern int setPcmSampleRate (PcmDevice *pcm, int rate);

extern int getPcmChannelCount (PcmDevice *pcm);
extern int setPcmChannelCount (PcmDevice *pcm, int channels);

extern PcmAmplitudeFormat getPcmAmplitudeFormat (PcmDevice *pcm);
extern PcmAmplitudeFormat setPcmAmplitudeFormat (PcmDevice *pcm, PcmAmplitudeFormat format);

extern int writePcmData (PcmDevice *pcm, const unsigned char *buffer, int count);
extern void pushPcmOutput (PcmDevice *pcm);
extern void awaitPcmOutput (PcmDevice *pcm);
extern void cancelPcmOutput (PcmDevice *pcm);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_PCM */

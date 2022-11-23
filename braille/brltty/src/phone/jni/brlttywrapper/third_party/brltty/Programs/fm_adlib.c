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

/*
 * Miscellaneous FM chip soundcard routines for BRLTTY.
 * Implemented by Dave Mielke <dave@mielke.cc>.
 * Method gleaned from sccw, a morse code program written
 * by Steven J. Merrifield <sjm@ee.latrobe.edu.au> (VK3ESM).
 * Must compile with -O2.
 * Must link with -lm.
 * May compile with -DDEBUG_ADLIB.
 */

#include "prologue.h"

#include "log.h"
#include "async_wait.h"
#include "timing.h"
#include "ports.h"
#include "fm.h"
#include "fm_adlib.h"

const unsigned char AL_channelOffsets[] = {
  /* 1     2     3     4     5     6     7     8     9 */
  0X00, 0X01, 0X02, 0X08, 0X09, 0X0A, 0X10, 0X11, 0X12
};
const unsigned char AL_channelCount = ARRAY_COUNT(AL_channelOffsets);

static unsigned int portsEnabledCount = 0;

int
fmEnablePorts (int errorLevel) {
  if (portsEnabledCount) return 1;

  if (enablePorts(errorLevel, ALP_REGISTER, 1)) {
    if (enablePorts(errorLevel, ALP_DATA, 1)) {
      portsEnabledCount++;
      return 1;
    }

    disablePorts(ALP_REGISTER, 1);
  }

  return 0;
}

void
fmDisablePorts (void) {
  if (!--portsEnabledCount) {
    disablePorts(ALP_REGISTER, 1);
    disablePorts(ALP_DATA, 1);
  }
}

unsigned char
AL_readStatus (void) {
  return readPort1(ALP_STATUS);
}

static void
AL_writeDelay (int delay) {
  while (delay-- > 0) {
    AL_readStatus();
  }
}

void
AL_writeRegister (int number, unsigned char data) {
  /* logMessage(LOG_DEBUG, "AL_writeRegister: %2.2X=%2.2X", number, data); */
  writePort1(ALP_REGISTER, number);
  AL_writeDelay(6);
  writePort1(ALP_DATA, data);
  AL_writeDelay(35);
}

void
fmResetCard (void) {
  int number;
  for (number=ALR_FIRST; number<=ALR_LAST; ++number) {
    AL_writeRegister(number, 0);
  }
}

static void
AL_resetTimers (void) {
  AL_writeRegister(ALR_TCTL, AL_TCTL_T1MASK|AL_TCTL_T2MASK);
  AL_writeRegister(ALR_TCTL, AL_TCTL_RESET);
}

int
fmTestCard (int errorLevel) {
  const unsigned char mask = AL_STAT_EXP | AL_STAT_EXP1 | AL_STAT_EXP2;

  AL_resetTimers();
  if (!(AL_readStatus() & mask)) {
    unsigned char status;

    AL_writeRegister(ALR_T1DATA, 0xFF);
    AL_writeRegister(ALR_TCTL, AL_TCTL_T1START|AL_TCTL_T2MASK);

    {
      const TimeValue duration = {
        .seconds = 0,
        .nanoseconds = 80 * NSECS_PER_USEC
      };

      accurateDelay(&duration);
    }

    status = AL_readStatus();
    AL_resetTimers(); 

    if ((status & mask) == (AL_STAT_EXP | AL_STAT_EXP1)) return 1; 
  }

  logMessage(errorLevel, "FM synthesizer initialization failure");
  return 0;
}

static void
AL_evaluatePitch (int pitch, int *exponent, int *mantissa) {
  int shift = 21;
  while ((*mantissa = (int)((float)pitch * (1 << --shift) / 50000.0)) > 0X3FF);
  *exponent = 20 - shift;
}

static void
AL_initiateTone (int channel, int exponent, int mantissa) {
  /* logMessage(LOG_DEBUG, "AL_initiateTone: %1.1X[%3.3X]", exponent, mantissa); */
  AL_writeRegister(ALR_FREQUENCY_LSB(channel),
                   (mantissa & 0XFF));
  AL_writeRegister(ALR_FREQUENCY_MSB(channel),
                   (((mantissa >> 8) & 0X3) |
                    ((exponent & 0X7) << AL_OCTAVE_SHIFT) |
                    AL_FREQ_ON));
}

void
fmStartTone (int channel, int pitch) {
  int exponent;
  int mantissa;
  AL_evaluatePitch(pitch, &exponent, &mantissa);
  /* logMessage(LOG_DEBUG, "fmStartTone: %d", pitch); */
  AL_initiateTone(channel, exponent, mantissa);
}

void
fmStopTone (int channel) {
  AL_writeRegister(ALR_FREQUENCY_MSB(channel), 0);
}

void
fmPlayTone (int channel, unsigned int pitch, unsigned long int duration, unsigned int volume) {
  /* Play tone at fundamental frequency. */
  AL_writeRegister(ALR_MODULATOR(ALG_EFFECT, channel),
                   (AL_HARMONIC_1 << AL_HARMONIC_SHIFT));

  /* Set the carrier to the fundamental frequency. */
  AL_writeRegister(ALR_CARRIER(ALG_EFFECT, channel),
                   (AL_HARMONIC_1 << AL_HARMONIC_SHIFT));

  /* Set the volume (passed in as 0-100) */
  AL_writeRegister(ALR_CARRIER(ALG_LEVEL, channel),
                   ((AL_VOLUME_SOFT - ((AL_VOLUME_SOFT * volume) / 100)) << AL_VOLUME_SHIFT));

  /* Set fast attack and slow decay. */
  AL_writeRegister(ALR_CARRIER(ALG_ATTDEC, channel),
                   ((AL_ATTACK_FAST << AL_ATTACK_SHIFT) |
                    (AL_DECAY_SLOW << AL_DECAY_SHIFT)));

  /* Set soft sustain and fast release. */
  AL_writeRegister(ALR_CARRIER(ALG_SUSREL, channel),
                   ((AL_SUSTAIN_SOFT << AL_SUSTAIN_SHIFT) |
                    (AL_RELEASE_FAST << AL_RELEASE_SHIFT)));
      
  fmStartTone(channel, pitch);
  asyncWait(duration);
  fmStopTone(channel);
}

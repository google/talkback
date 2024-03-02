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

#ifndef BRLTTY_INCLUDED_FM_ADLIB
#define BRLTTY_INCLUDED_FM_ADLIB

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/*
 * Miscellaneous FM chip soundcard routines for BRLTTY.
 * Implemented by Dave Mielke <dave@mielke.cc>.
 * Method gleaned from sccw, a morse code program written
 * by Steven J. Merrifield <sjm@ee.latrobe.edu.au> (VK3ESM).
 * Must compile with -O2.
 * Must link with -lm.
 * May compile with -DDEBUG_ADLIB.
 */

extern void AL_writeRegister (int number, unsigned char data);
extern unsigned char AL_readStatus (void);

/* I/O ports. */
#define ALP_REGISTER 0X388 /* for writing the register number */
#define ALP_DATA (ALP_REGISTER + 1) /* for writing data to the selected register */
#define ALP_STATUS ALP_REGISTER /* for reading the status of the card */

/* Status register bits. */
#define AL_STAT_EXP 0X80 /* a timer has expired */
#define AL_STAT_EXP1 0X40 /* timer 1 has expired */
#define AL_STAT_EXP2 0X20 /* timer 2 has expired */

#define ALR_FIRST 0X01
#define ALR_LAST 0XF5

#define ALR_INIT 0X01
#define ALR_T1DATA 0X02
#define ALR_T2DATA 0X03
#define ALR_TCTL 0X04

#define AL_INIT_WFSLCT 0x20 /* wave form select */

#define AL_TCTL_RESET 0X80
#define AL_TCTL_T1MASK 0X40
#define AL_TCTL_T2MASK 0X20
#define AL_TCTL_T2START 0X02
#define AL_TCTL_T1START 0X01

extern const unsigned char AL_channelOffsets[];
extern const unsigned char AL_channelCount;
#define ALR_MODULATOR(group,channel) ((group) + AL_channelOffsets[(channel)])
#define ALR_CARRIER(group,channel) (ALR_MODULATOR((group),(channel)) + 3)

#define ALG_EFFECT 0X20
#define AL_EFCT_AMPLMOD 0X80 /* apply amplitude modulation */
#define AL_EFCT_VIBRATO 0X40 /* apply vibrato */
#define AL_EFCT_SUSTAIN 0X20 /* do not release */
#define AL_EFCT_KSR     0X10 /* keyboard scaling rate (faster decay at higher freq) */
#define AL_HARMONIC_SHIFT 0
#define AL_SUBHARMONIC_1  0X00 /* 1 octave lower */
#define AL_HARMONIC_1     0X01 /* the specified frequency */
#define AL_HARMONIC_2     0X02 /* 1 octave higher */
#define AL_HARMONIC_3     0X03 /* 1 octave and a 5th higher */
#define AL_HARMONIC_4     0X04 /* 2 octaves higher */
#define AL_HARMONIC_5     0X05 /* 2 octaves and a 3rd higher */
#define AL_HARMONIC_6     0X06 /* 2 octaves and a 5th higher */
#define AL_HARMONIC_7     0X07 /* 2 octaves and a diminished 7th higher */
#define AL_HARMONIC_8     0X08 /* 3 octaves higher */
#define AL_HARMONIC_9     0X09 /* 3 octaves and a 2nd higher */
#define AL_HARMONIC_10    0X0A /* 3 octaves and a 3rd higher */
#define AL_HARMONIC_11    0X0B /* 3 octaves and ? higher */
#define AL_HARMONIC_12    0X0C /* 3 octaves and a 5th higher */
#define AL_HARMONIC_13    0X0D /* 3 octaves and ? higher */
#define AL_HARMONIC_14    0X0E /* 3 octaves and a diminished 7th higher */
#define AL_HARMONIC_15    0X0F /* 3 octaves and ? higher */

#define ALG_LEVEL 0X40
#define AL_LKS_SHIFT 6 /* level key scaling (softer at higher freq) */
#define AL_LKS_0   0X00 /* 0 dB per octave */
#define AL_LKS_1p5 0X02 /* 1.5 dB per octave */
#define AL_LKS_3   0X01 /* 3 dB per octave */
#define AL_LKS_6   0X03 /* 6 dB per octave */
#define AL_VOLUME_SHIFT 0 /* each bit attenuates by specific amount */
#define AL_VOLUME_24   0X20 /* 24 dB */
#define AL_VOLUME_12   0X10 /* 12 dB */
#define AL_VOLUME_6    0X08 /* 6 dB */
#define AL_VOLUME_3    0X04 /* 3 dB */
#define AL_VOLUME_1p5  0X02 /* 1.5 dB */
#define AL_VOLUME_0p75 0X01 /* 0.75 dB */
#define AL_VOLUME_LOUD 0X00 /* loudest (no attenuation) */
#define AL_VOLUME_SOFT 0X3F /* softest (47.25 dB attenuation) */

#define ALG_ATTDEC 0X60
#define AL_ATTACK_SHIFT 4
#define AL_ATTACK_SLOW 0X0
#define AL_ATTACK_FAST 0XF
#define AL_DECAY_SHIFT 0
#define AL_DECAY_SLOW 0X0
#define AL_DECAY_FAST 0XF

#define ALG_SUSREL 0X80
#define AL_SUSTAIN_SHIFT 4
#define AL_SUSTAIN_24   0X8 /* after 24 dB decay */
#define AL_SUSTAIN_12   0X4 /* after 12 dB decay */
#define AL_SUSTAIN_6    0X2 /* after 6 dB decay */
#define AL_SUSTAIN_3    0X1 /* after 3 dB decay */
#define AL_SUSTAIN_LOUD 0X0 /* the loudest (right after attack) */
#define AL_SUSTAIN_SOFT 0XF /* the softest (after 45 dB decay) */
#define AL_RELEASE_SHIFT 0
#define AL_RELEASE_SLOW 0X0
#define AL_RELEASE_FAST 0XF

#define ALR_FREQUENCY_LSB(channel) (0XA0 + (channel))
#define ALR_FREQUENCY_MSB(channel) (0XB0 + (channel))
#define AL_OCTAVE_SHIFT 2
#define AL_FREQ_ON 0X20

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_FM_ADLIB */

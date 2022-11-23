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

#include "notes.h"

#define NOTE_FREQUENCY_FACTOR 1000

static const uint32_t scaledNoteFrequencies[] = {
  /*   0 rest */        0,
  /*   1 -5C# */     8662,
  /*   2 -5D  */     9177,
  /*   3 -5D# */     9723,
  /*   4 -5E  */    10301,
  /*   5 -5F  */    10913,
  /*   6 -5F# */    11562,
  /*   7 -5G  */    12250,
  /*   8 -5G# */    12978,
  /*   9 -5A  */    13750,
  /*  10 -5A# */    14568,
  /*  11 -5B  */    15434,
  /*  12 -4C  */    16352,
  /*  13 -4C# */    17324,
  /*  14 -4D  */    18354,
  /*  15 -4D# */    19445,
  /*  16 -4E  */    20602,
  /*  17 -4F  */    21827,
  /*  18 -4F# */    23125,
  /*  19 -4G  */    24500,
  /*  20 -4G# */    25957,
  /*  21 -4A  */    27500,
  /*  22 -4A# */    29135,
  /*  23 -4B  */    30868,
  /*  24 -3C  */    32703,
  /*  25 -3C# */    34648,
  /*  26 -3D  */    36708,
  /*  27 -3D# */    38891,
  /*  28 -3E  */    41203,
  /*  29 -3F  */    43654,
  /*  30 -3F# */    46249,
  /*  31 -3G  */    48999,
  /*  32 -3G# */    51913,
  /*  33 -3A  */    55000,
  /*  34 -3A# */    58270,
  /*  35 -3B  */    61735,
  /*  36 -2C  */    65406,
  /*  37 -2C# */    69296,
  /*  38 -2D  */    73416,
  /*  39 -2D# */    77782,
  /*  40 -2E  */    82407,
  /*  41 -2F  */    87307,
  /*  42 -2F# */    92499,
  /*  43 -2G  */    97999,
  /*  44 -2G# */   103826,
  /*  45 -2A  */   110000,
  /*  46 -2A# */   116541,
  /*  47 -2B  */   123471,
  /*  48 -1C  */   130813,
  /*  49 -1C# */   138591,
  /*  50 -1D  */   146832,
  /*  51 -1D# */   155563,
  /*  52 -1E  */   164814,
  /*  53 -1F  */   174614,
  /*  54 -1F# */   184997,
  /*  55 -1G  */   195998,
  /*  56 -1G# */   207652,
  /*  57 -1A  */   220000,
  /*  58 -1A# */   233082,
  /*  59 -1B  */   246942,
  /*  60  0C  */   261626,
  /*  61  0C# */   277183,
  /*  62  0D  */   293665,
  /*  63  0D# */   311127,
  /*  64  0E  */   329628,
  /*  65  0F  */   349228,
  /*  66  0F# */   369994,
  /*  67  0G  */   391995,
  /*  68  0G# */   415305,
  /*  69  0A  */   440000,
  /*  70  0A# */   466164,
  /*  71  0B  */   493883,
  /*  72 +1C  */   523251,
  /*  73 +1C# */   554365,
  /*  74 +1D  */   587330,
  /*  75 +1D# */   622254,
  /*  76 +1E  */   659255,
  /*  77 +1F  */   698456,
  /*  78 +1F# */   739989,
  /*  79 +1G  */   783991,
  /*  80 +1G# */   830609,
  /*  81 +1A  */   880000,
  /*  82 +1A# */   932328,
  /*  83 +1B  */   987767,
  /*  84 +2C  */  1046502,
  /*  85 +2C# */  1108731,
  /*  86 +2D  */  1174659,
  /*  87 +2D# */  1244508,
  /*  88 +2E  */  1318510,
  /*  89 +2F  */  1396913,
  /*  90 +2F# */  1479978,
  /*  91 +2G  */  1567982,
  /*  92 +2G# */  1661219,
  /*  93 +2A  */  1760000,
  /*  94 +2A# */  1864655,
  /*  95 +2B  */  1975533,
  /*  96 +3C  */  2093005,
  /*  97 +3C# */  2217461,
  /*  98 +3D  */  2349318,
  /*  99 +3D# */  2489016,
  /* 100 +3E  */  2637020,
  /* 101 +3F  */  2793826,
  /* 102 +3F# */  2959955,
  /* 103 +3G  */  3135963,
  /* 104 +3G# */  3322438,
  /* 105 +3A  */  3520000,
  /* 106 +3A# */  3729310,
  /* 107 +3B  */  3951066,
  /* 108 +4C  */  4186009,
  /* 109 +4C# */  4434922,
  /* 110 +4D  */  4698636,
  /* 111 +4D# */  4978032,
  /* 112 +4E  */  5274041,
  /* 113 +4F  */  5587652,
  /* 114 +4F# */  5919911,
  /* 115 +4G  */  6271927,
  /* 116 +4G# */  6644875,
  /* 117 +4A  */  7040000,
  /* 118 +4A# */  7458620,
  /* 119 +4B  */  7902133,
  /* 120 +5C  */  8372018,
  /* 121 +5C# */  8869844,
  /* 122 +5D  */  9397273,
  /* 123 +5D# */  9956063,
  /* 124 +5E  */ 10548082,
  /* 125 +5F  */ 11175303,
  /* 126 +5F# */ 11839822,
  /* 127 +5G  */ 12543854
};

unsigned char
getLowestNote (void) {
  return 1;
}

unsigned char
getHighestNote (void) {
  return ARRAY_COUNT(scaledNoteFrequencies) - 1;
}

static inline uint32_t
getScaledNoteFrequency (unsigned char note) {
  unsigned char highestNote = getHighestNote();

  if (note > highestNote) note = highestNote;
  return scaledNoteFrequencies[note];
}

uint32_t
getIntegerNoteFrequency (unsigned char note) {
  return getScaledNoteFrequency(note) / NOTE_FREQUENCY_FACTOR;
}

#ifndef NO_FLOAT
float
getRealNoteFrequency (unsigned char note) {
  return (float)getScaledNoteFrequency(note) / (float)NOTE_FREQUENCY_FACTOR;
}
#endif /* NO_FLOAT */

unsigned char
getNearestNote (NoteFrequency frequency) {
  if (!frequency) return 0;

  unsigned char lowestNote = getLowestNote();
  if (frequency <= getNoteFrequency(lowestNote)) return lowestNote;

  unsigned char highestNote = getHighestNote();
  if (frequency >= getNoteFrequency(highestNote)) return highestNote;

  while (lowestNote <= highestNote) {
    unsigned char currentNote = (lowestNote + highestNote) / 2;

    if (frequency < getNoteFrequency(currentNote)) {
      highestNote = currentNote - 1;
    } else {
      lowestNote = currentNote + 1;
    }
  }

  unsigned char lowerNote = highestNote;
  unsigned char higherNote = lowerNote + 1;

  NoteFrequency lowerFrequency = getNoteFrequency(lowerNote);
  NoteFrequency higherFrequency = getNoteFrequency(higherNote);

  return ((frequency - lowerFrequency) < (higherFrequency - frequency))?
         lowerNote: higherNote;
}

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

#include "notes.h"

#define NOTE_FREQUENCY_FACTOR 1000

static const uint32_t scaledNoteFrequencies[] = {
  /*   0 rest */        0,
  /*   1 -1C# */     8662,
  /*   2 -1D  */     9177,
  /*   3 -1D# */     9723,
  /*   4 -1E  */    10301,
  /*   5 -1F  */    10913,
  /*   6 -1F# */    11562,
  /*   7 -1G  */    12250,
  /*   8 -1G# */    12978,
  /*   9 -1A  */    13750,
  /*  10 -1A# */    14568,
  /*  11 -1B  */    15434,
  /*  12  0C  */    16352,
  /*  13  0C# */    17324,
  /*  14  0D  */    18354,
  /*  15  0D# */    19445,
  /*  16  0E  */    20602,
  /*  17  0F  */    21827,
  /*  18  0F# */    23125,
  /*  19  0G  */    24500,
  /*  20  0G# */    25957,
  /*  21  0A  */    27500,
  /*  22  0A# */    29135,
  /*  23  0B  */    30868,
  /*  24  1C  */    32703,
  /*  25  1C# */    34648,
  /*  26  1D  */    36708,
  /*  27  1D# */    38891,
  /*  28  1E  */    41203,
  /*  29  1F  */    43654,
  /*  30  1F# */    46249,
  /*  31  1G  */    48999,
  /*  32  1G# */    51913,
  /*  33  1A  */    55000,
  /*  34  1A# */    58270,
  /*  35  1B  */    61735,
  /*  36  2C  */    65406,
  /*  37  2C# */    69296,
  /*  38  2D  */    73416,
  /*  39  2D# */    77782,
  /*  40  2E  */    82407,
  /*  41  2F  */    87307,
  /*  42  2F# */    92499,
  /*  43  2G  */    97999,
  /*  44  2G# */   103826,
  /*  45  2A  */   110000,
  /*  46  2A# */   116541,
  /*  47  2B  */   123471,
  /*  48  3C  */   130813,
  /*  49  3C# */   138591,
  /*  50  3D  */   146832,
  /*  51  3D# */   155563,
  /*  52  3E  */   164814,
  /*  53  3F  */   174614,
  /*  54  3F# */   184997,
  /*  55  3G  */   195998,
  /*  56  3G# */   207652,
  /*  57  3A  */   220000,
  /*  58  3A# */   233082,
  /*  59  3B  */   246942,
  /*  60  4C  */   261626,
  /*  61  4C# */   277183,
  /*  62  4D  */   293665,
  /*  63  4D# */   311127,
  /*  64  4E  */   329628,
  /*  65  4F  */   349228,
  /*  66  4F# */   369994,
  /*  67  4G  */   391995,
  /*  68  4G# */   415305,
  /*  69  4A  */   440000,
  /*  70  4A# */   466164,
  /*  71  4B  */   493883,
  /*  72  5C  */   523251,
  /*  73  5C# */   554365,
  /*  74  5D  */   587330,
  /*  75  5D# */   622254,
  /*  76  5E  */   659255,
  /*  77  5F  */   698456,
  /*  78  5F# */   739989,
  /*  79  5G  */   783991,
  /*  80  5G# */   830609,
  /*  81  5A  */   880000,
  /*  82  5A# */   932328,
  /*  83  5B  */   987767,
  /*  84  6C  */  1046502,
  /*  85  6C# */  1108731,
  /*  86  6D  */  1174659,
  /*  87  6D# */  1244508,
  /*  88  6E  */  1318510,
  /*  89  6F  */  1396913,
  /*  90  6F# */  1479978,
  /*  91  6G  */  1567982,
  /*  92  6G# */  1661219,
  /*  93  6A  */  1760000,
  /*  94  6A# */  1864655,
  /*  95  6B  */  1975533,
  /*  96  7C  */  2093005,
  /*  97  7C# */  2217461,
  /*  98  7D  */  2349318,
  /*  99  7D# */  2489016,
  /* 100  7E  */  2637020,
  /* 101  7F  */  2793826,
  /* 102  7F# */  2959955,
  /* 103  7G  */  3135963,
  /* 104  7G# */  3322438,
  /* 105  7A  */  3520000,
  /* 106  7A# */  3729310,
  /* 107  7B  */  3951066,
  /* 108  8C  */  4186009,
  /* 109  8C# */  4434922,
  /* 110  8D  */  4698636,
  /* 111  8D# */  4978032,
  /* 112  8E  */  5274041,
  /* 113  8F  */  5587652,
  /* 114  8F# */  5919911,
  /* 115  8G  */  6271927,
  /* 116  8G# */  6644875,
  /* 117  8A  */  7040000,
  /* 118  8A# */  7458620,
  /* 119  8B  */  7902133,
  /* 120  9C  */  8372018,
  /* 121  9C# */  8869844,
  /* 122  9D  */  9397273,
  /* 123  9D# */  9956063,
  /* 124  9E  */ 10548082,
  /* 125  9F  */ 11175303,
  /* 126  9F# */ 11839822,
  /* 127  9G  */ 12543854
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

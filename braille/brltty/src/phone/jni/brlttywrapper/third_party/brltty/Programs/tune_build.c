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

#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <limits.h>
#include <errno.h>

#include "log.h"
#include "tune_build.h"
#include "notes.h"
#include "charset.h"

typedef unsigned int TuneNumber;

typedef struct {
  const char *name;
  TuneNumber minimum;
  TuneNumber maximum;
  TuneNumber current;
} TuneParameter;

struct TuneBuilderStruct {
  TuneStatus status;

  struct {
    ToneElement *array;
    unsigned int size;
    unsigned int count;
  } tones;

  signed char accidentals[NOTES_PER_SCALE];
  TuneParameter duration;
  TuneParameter note;
  TuneParameter octave;
  TuneParameter percentage;
  TuneParameter tempo;

  struct {
    const wchar_t *text;
    const char *name;
    unsigned int index;
  } source;
};

static const wchar_t *noteLetters = WS_C("cdefgab");
static const unsigned char noteOffsets[] = {0, 2, 4, 5, 7, 9, 11};
static const signed char scaleAccidentals[] = {0, 2, 4, -1, 1, 3, 5};
static const unsigned char accidentalTable[] = {3, 0, 4, 1, 5, 2, 6};

typedef struct {
  const wchar_t *name;
  signed char accidentals;
} ModeEntry;

static const ModeEntry modeTable[] = {
  {.name=WS_C("major"), .accidentals=0},
  {.name=WS_C("minor"), .accidentals=-3},

  {.name=WS_C("ionian"), .accidentals=0},
  {.name=WS_C("dorian"), .accidentals=-2},
  {.name=WS_C("phrygian"), .accidentals=-4},
  {.name=WS_C("lydian"), .accidentals=1},
  {.name=WS_C("mixolydian"), .accidentals=-1},
  {.name=WS_C("aeolian"), .accidentals=-3},
  {.name=WS_C("locrian"), .accidentals=-5},
};
static const unsigned char modeCount = ARRAY_COUNT(modeTable);

static void
logSyntaxError (TuneBuilder *tb, const char *message) {
  tb->status = TUNE_STATUS_SYNTAX;

  logMessage(LOG_ERR, "tune error: %s[%u]: %s: %" PRIws,
             tb->source.name, tb->source.index,
             message, tb->source.text);
}

int
addTone (TuneBuilder *tb, const ToneElement *tone) {
  if (tb->tones.count == tb->tones.size) {
    unsigned int newSize = tb->tones.size? (tb->tones.size << 1): 1;
    ToneElement *newArray;

    if (!(newArray = realloc(tb->tones.array, ARRAY_SIZE(newArray, newSize)))) {
      tb->status = TUNE_STATUS_FATAL;
      logMallocError();
      return 0;
    }

    tb->tones.array = newArray;
    tb->tones.size = newSize;
  }

  tb->tones.array[tb->tones.count++] = *tone;
  return 1;
}

int
addNote (TuneBuilder *tb, unsigned char note, int duration) {
  if (!duration) return 1;

  ToneElement tone = TONE_PLAY(duration, getNoteFrequency(note));
  return addTone(tb, &tone);
}

static int
parseNumber (
  TuneBuilder *tb,
  TuneNumber *number, const wchar_t **operand, int required,
  const TuneNumber minimum, const TuneNumber maximum,
  const char *name
) {
  const wchar_t *const start = *operand;
  unsigned long value = 0;
  const char *problem = "invalid";

  while (1) {
    if (!value && (*operand > start)) goto PROBLEM_ENCOUNTERED;

    long int digit = **operand - WC_C('0');
    if (digit < 0) break;
    if (digit > 9) break;

    value *= 10;
    value += digit;
    if (value > UINT_MAX) goto PROBLEM_ENCOUNTERED;

    *operand += 1;
  }

  if (*operand > start) {
    if (value < minimum) goto PROBLEM_ENCOUNTERED;
    if (value > maximum) goto PROBLEM_ENCOUNTERED;
    *number = value;
  } else if (required) {
    problem = "missing";
    goto PROBLEM_ENCOUNTERED;
  }

  return 1;

PROBLEM_ENCOUNTERED:
  if (name) {
    char message[0X80];
    snprintf(message, sizeof(message), "%s %s", problem, name);
    logSyntaxError(tb, message);
  }

  return 0;
}

static int
parseParameter (
  TuneBuilder *tb, TuneParameter *parameter,
  const wchar_t **operand, int required
) {
  return parseNumber(tb, &parameter->current, operand, required,
                     parameter->minimum, parameter->maximum, parameter->name);
}

static int
parseOptionalParameter (TuneBuilder *tb, TuneParameter *parameter, const wchar_t **operand) {
  return parseParameter(tb, parameter, operand, 0);
}

static int
parseRequiredParameter (TuneBuilder *tb, TuneParameter *parameter, const wchar_t **operand) {
  return parseParameter(tb, parameter, operand, 1);
}

static int
parsePercentage (TuneBuilder *tb, const wchar_t **operand) {
  return parseRequiredParameter(tb, &tb->percentage, operand);
}

static int
parseTempo (TuneBuilder *tb, const wchar_t **operand) {
  return parseRequiredParameter(tb, &tb->tempo, operand);
}

static void
setCurrentDuration (TuneBuilder *tb, TuneNumber multiplier, TuneNumber divisor) {
  tb->duration.current = (60000 * multiplier) / (tb->tempo.current * divisor);
}

static void
setBaseDuration (TuneBuilder *tb) {
  setCurrentDuration(tb, 1, 1);
}

static int
parseDuration (TuneBuilder *tb, const wchar_t **operand, int *duration) {
  if (**operand == '@') {
    *operand += 1;

    TuneParameter parameter = tb->duration;
    if (!parseRequiredParameter(tb, &parameter, operand)) return 0;
    *duration = parameter.current;
  } else {
    const wchar_t *durationOperand = *operand;

    TuneNumber multiplier;
    TuneNumber divisor;

    if (**operand == '*') {
      *operand += 1;

      if (!parseNumber(tb, &multiplier, operand, 1, 1, 16, "duration multiplier")) {
        return 0;
      }
    } else {
      multiplier = 1;
    }

    if (**operand == '/') {
      *operand += 1;

      if (!parseNumber(tb, &divisor, operand, 1, 1, 128, "duration divisor")) {
        return 0;
      }
    } else {
      divisor = 1;
    }

    if (*operand != durationOperand) setCurrentDuration(tb, multiplier, divisor);
    *duration = tb->duration.current;
  }

  tb->duration.current = *duration;

  {
    int increment = *duration;

    while (**operand == '.') {
      *duration += (increment /= 2);
      *operand += 1;
    }
  }

  return 1;
}

static TuneNumber
toOctave (TuneNumber note) {
  return note / NOTES_PER_OCTAVE;
}

static void
setOctave (TuneBuilder *tb) {
  tb->octave.current = toOctave(tb->note.current);
}

static void
setAccidentals (TuneBuilder *tb, int accidentals) {
  int quotient = accidentals / NOTES_PER_SCALE;
  int remainder = accidentals % NOTES_PER_SCALE;

  for (unsigned int index=0; index<ARRAY_COUNT(tb->accidentals); index+=1) {
    tb->accidentals[index] = quotient;
  }

  while (remainder > 0) {
    tb->accidentals[accidentalTable[--remainder]] += 1;
  }

  while (remainder < 0) {
    tb->accidentals[accidentalTable[NOTES_PER_SCALE + remainder++]] -= 1;
  }
}

static int
parseNoteLetter (unsigned char *index, const wchar_t **operand) {
  const wchar_t *letter = wcschr(noteLetters, **operand);

  if (!letter) return 0;
  if (!*letter) return 0;

  *index = letter - noteLetters;
  *operand += 1;
  return 1;
}

static int
parseMode (TuneBuilder *tb, int *accidentals, const wchar_t **operand) {
  const wchar_t *from = *operand;
  if (!isalpha(*from)) return 1;

  const wchar_t *to = from;
  while (iswalpha(*++to));
  unsigned int length = to - from;

  const ModeEntry *mode = NULL;
  const ModeEntry *current = modeTable;
  const ModeEntry *end = current + modeCount;

  while (current < end) {
    if (wcsncmp(current->name, from, length) == 0) {
      if (mode) {
        logSyntaxError(tb, "ambiguous mode");
        return 0;
      }

      mode = current;
    }

    current += 1;
  }

  if (!mode) {
    logSyntaxError(tb, "unrecognized mode");
    return 0;
  }

  *accidentals += mode->accidentals;
  *operand = to;
  return 1;
}

static int
parseKeySignature (TuneBuilder *tb, const wchar_t **operand) {
  int accidentals;
  int increment;

  {
    unsigned char index;

    if (parseNoteLetter(&index, operand)) {
      accidentals = scaleAccidentals[index];
      increment = NOTES_PER_SCALE;
      if (!parseMode(tb, &accidentals, operand)) return 0;
    } else {
      accidentals = 0;
      increment = 1;
    }
  }

  TuneNumber count = 0;
  if (!parseNumber(tb, &count, operand, 0, 1, NOTES_PER_OCTAVE-1, "accidental count")) {
    return 0;
  }

  int haveCount = count != 0;
  wchar_t accidental = **operand;

  switch (accidental) {
    case '-':
      increment = -increment;
    case '+':
      if (haveCount) {
        *operand += 1;
      } else {
        do {
          count += 1;
        } while (*++*operand == accidental);
      }
      break;

    default:
      if (!haveCount) break;
      logSyntaxError(tb, "accidental not specified");
      return 0;
  }

  accidentals += increment * count;
  setAccidentals(tb, accidentals);
  return 1;
}

static int
parseNote (TuneBuilder *tb, const wchar_t **operand, unsigned char *note) {
  int noteNumber;

  if (**operand == 'r') {
    *operand += 1;
    noteNumber = 0;
  } else {
    int defaultAccidentals = 0;

    if (**operand == 'n') {
      *operand += 1;
      TuneParameter parameter = tb->note;
      if (!parseRequiredParameter(tb, &parameter, operand)) return 0;
      noteNumber = parameter.current;
    } else {
      unsigned char noteIndex;
      if (!parseNoteLetter(&noteIndex, operand)) return 0;

      const wchar_t *octaveOperand = *operand;
      TuneParameter octave = tb->octave;
      if (!parseOptionalParameter(tb, &octave, operand)) return 0;

      noteNumber = (octave.current * NOTES_PER_OCTAVE) + noteOffsets[noteIndex];
      defaultAccidentals = tb->accidentals[noteIndex];

      if (*operand == octaveOperand) {
        int adjustOctave = 0;
        TuneNumber previousNote = tb->note.current;
        TuneNumber currentNote = noteNumber;

        if (currentNote < previousNote) {
          currentNote += NOTES_PER_OCTAVE;
          if ((currentNote - previousNote) <= 3) adjustOctave = 1;
        } else if (currentNote > previousNote) {
          currentNote -= NOTES_PER_OCTAVE;
          if ((previousNote - currentNote) <= 3) adjustOctave = 1;
        }

        if (adjustOctave) noteNumber = currentNote;
      }
    }

    tb->note.current = noteNumber;
    setOctave(tb);

    {
      wchar_t accidental = **operand;

      switch (accidental) {
        {
          int increment;

        case '+':
          increment = 1;
          goto doAccidental;

        case '-':
          increment = -1;
          goto doAccidental;

        doAccidental:
          do {
            noteNumber += increment;
          } while (*++*operand == accidental);

          break;
        }

        case '=':
          *operand += 1;
          break;

        default:
          noteNumber += defaultAccidentals;
          break;
      }
    }

    {
      const unsigned char lowestNote = getLowestNote();
      const unsigned char highestNote = getHighestNote();

      if (noteNumber < lowestNote) {
        logSyntaxError(tb, "note too low");
        return 0;
      }

      if (noteNumber > highestNote) {
        logSyntaxError(tb, "note too high");
        return 0;
      }
    }
  }

  *note = noteNumber;
  return 1;
}

static int
parseTone (TuneBuilder *tb, const wchar_t **operand) {
  while (1) {
    tb->source.text = *operand;
    unsigned char note;

    {
      const wchar_t *noteOperand = *operand;
      if (!parseNote(tb, operand, &note)) return *operand == noteOperand;
    }

    int duration;
    if (!parseDuration(tb, operand, &duration)) return 0;

    if (note) {
      int onDuration = (duration * tb->percentage.current) / 100;
      if (!addNote(tb, note, onDuration)) return 0;
      duration -= onDuration;
    }

    if (!addNote(tb, 0, duration)) return 0;
  }

  return 1;
}

static int
parseTuneOperand (TuneBuilder *tb, const wchar_t *operand) {
  tb->source.text = operand;

  switch (*operand) {
    case 'k':
      operand += 1;
      if (!parseKeySignature(tb, &operand)) return 0;
      break;

    case 'p':
      operand += 1;
      if (!parsePercentage(tb, &operand)) return 0;
      break;

    case 't':
      operand += 1;
      if (!parseTempo(tb, &operand)) return 0;
      setBaseDuration(tb);
      break;

    default:
      if (!parseTone(tb, &operand)) return 0;
      break;
  }

  if (*operand) {
    logSyntaxError(tb, "extra data");
    return 0;
  }

  return 1;
}

int
parseTuneText (TuneBuilder *tb, const wchar_t *text) {
  tb->source.text = text;

  wchar_t buffer[wcslen(text) + 1];
  wcscpy(buffer, text);

  static const wchar_t *delimiters = WS_C(" \t\r\n");
  wchar_t *string = buffer;
  wchar_t *operand;

#if !defined(__MINGW32__) && !defined(__MSDOS__)
  wchar_t *next;
#endif /* __MINGW32__ */

  while ((operand = wcstok(string, delimiters
#ifndef __MINGW32__
                           , &next
#endif /* __MINGW32__ */
                          ))) {
    if (*operand == '#') break;
    if (!parseTuneOperand(tb, operand)) return 0;
    string = NULL;
  }

  return 1;
}

int
parseTuneString (TuneBuilder *tb, const char *string) {
  const size_t size = strlen(string) + 1;
  wchar_t characters[size];

  const char *byte = string;
  wchar_t *character = characters;

  convertUtf8ToWchars(&byte, &character, size);

  return parseTuneText(tb, characters);
}

ToneElement *
getTune (TuneBuilder *tb) {
  if (tb->status == TUNE_STATUS_OK) {
    unsigned int count = tb->tones.count;
    ToneElement *tune;

    if ((tune = malloc(ARRAY_SIZE(tune, (count + 1))))) {
      memcpy(tune, tb->tones.array, ARRAY_SIZE(tune, count));

      static const ToneElement tone = TONE_STOP();
      tune[count] = tone;

      return tune;
    } else {
      logMallocError();
    }
  }

  return NULL;
}

TuneStatus
getTuneStatus (TuneBuilder *tb) {
  return tb->status;
}

void
setTuneSourceName (TuneBuilder *tb, const char *name) {
  tb->source.name = name;
}

void
setTuneSourceIndex (TuneBuilder *tb, unsigned int index) {
  tb->source.index = index;
}

void
incrementTuneSourceIndex (TuneBuilder *tb) {
  tb->source.index += 1;
}

static inline void
setParameter (
  TuneParameter *parameter, const char *name,
  TuneNumber minimum, TuneNumber maximum, TuneNumber current
) {
  parameter->name = name;
  parameter->minimum = minimum;
  parameter->maximum = maximum;
  parameter->current = current;
}

void
resetTuneBuilder (TuneBuilder *tb) {
  tb->status = TUNE_STATUS_OK;

  tb->tones.count = 0;

  setParameter(&tb->duration, "note duration", 1, UINT16_MAX, 0);
  setParameter(&tb->note, "MIDI note number", getLowestNote(), getHighestNote(), NOTE_MIDDLE_C+noteOffsets[2]);
  setParameter(&tb->octave, "octave number", 0, 10, 0);
  setParameter(&tb->percentage, "percentage", 1, 100, 80);
  setParameter(&tb->tempo, "tempo", 40, UINT8_MAX, (60 * 2));

  setAccidentals(tb, 0);
  setBaseDuration(tb);
  setOctave(tb);

  tb->source.text = WS_C("");
  tb->source.name = "";
  tb->source.index = 0;
}

TuneBuilder *
newTuneBuilder (void) {
  TuneBuilder *tb;

  if ((tb = malloc(sizeof(*tb)))) {
    memset(tb, 0, sizeof(*tb));

    tb->tones.array = NULL;
    tb->tones.size = 0;

    resetTuneBuilder(tb);
    return tb;
  } else {
    logMallocError();
  }

  return NULL;
}

void
destroyTuneBuilder (TuneBuilder *tb) {
  if (tb->tones.array) free(tb->tones.array);
  free(tb);
}

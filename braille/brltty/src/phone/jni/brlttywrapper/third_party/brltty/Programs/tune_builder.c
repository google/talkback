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

#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <limits.h>
#include <errno.h>

#include "log.h"
#include "tune_builder.h"
#include "notes.h"
#include "utf8.h"

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

  TuneNumber durationMultiplier;
  TuneNumber durationDivisor;

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

  while (**operand) {
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
setCurrentDuration (TuneBuilder *tb) {
  tb->duration.current = (60000 * tb->durationMultiplier) / (tb->tempo.current * tb->durationDivisor);
}

static int
parseDuration (TuneBuilder *tb, const wchar_t **operand, int *duration) {
  if (**operand == '@') {
    *operand += 1;
    if (!parseRequiredParameter(tb, &tb->duration, operand)) return 0;
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

    if (*operand != durationOperand) {
      tb->durationMultiplier = multiplier;
      tb->durationDivisor = divisor;
      tb->duration.current = 0;
    }

    if (!tb->duration.current) setCurrentDuration(tb);
  }
  *duration = tb->duration.current;

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
setCurrentOctave (TuneBuilder *tb) {
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
  while (isalpha(*++to));
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
parseKey (TuneBuilder *tb, const wchar_t **operand) {
  int noteSpecified = 0;
  int accidentals;

  {
    unsigned char index;

    if (parseNoteLetter(&index, operand)) {
      noteSpecified = 1;
      accidentals = scaleAccidentals[index];
      if (!parseMode(tb, &accidentals, operand)) return 0;
    }
  }

  if (!noteSpecified) {
    TuneNumber count = 0;
    int increment = 1;

    if (!parseNumber(tb, &count, operand, 0, 1, NOTES_PER_OCTAVE-1, "accidental count")) {
      return 0;
    }

    int haveCount = count != 0;
    wchar_t accidental = **operand;

    switch (accidental) {
      case '-':
        increment = -increment;
        /* fall through */
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

    accidentals = increment * count;
  }

logMessage(LOG_NOTICE, "ccc=%d", accidentals);
  setAccidentals(tb, accidentals);
  return 1;
}

static int
parseNote (TuneBuilder *tb, const wchar_t **operand, unsigned char *note) {
  int noteNumber;

  if (**operand == 'r') {
    *operand += 1;
    noteNumber = 0;
  } else if (**operand == 'm') {
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

    int octaveSpecified = *operand != octaveOperand;
    if (octaveSpecified) octave.current += 1;

    noteNumber = (octave.current * NOTES_PER_OCTAVE) + noteOffsets[noteIndex];
    int defaultAccidentals = tb->accidentals[noteIndex];

    if (!octaveSpecified) {
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

    tb->note.current = noteNumber;
    setCurrentOctave(tb);

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

    if (noteNumber < getLowestNote()) {
      logSyntaxError(tb, "note too low");
      return 0;
    }

    if (noteNumber > getHighestNote()) {
      logSyntaxError(tb, "note too high");
      return 0;
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
parseCommand (TuneBuilder *tb, const wchar_t *operand) {
  tb->source.text = operand;

  switch (*operand) {
    case 'k':
      operand += 1;
      if (!parseKey(tb, &operand)) return 0;
      break;

    case 'p':
      operand += 1;
      if (!parsePercentage(tb, &operand)) return 0;
      break;

    case 't':
      operand += 1;
      if (!parseTempo(tb, &operand)) return 0;
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
    if (!parseCommand(tb, operand)) return 0;
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
  setParameter(&tb->octave, "octave number", 0, 9, 0);
  setParameter(&tb->percentage, "percentage", 1, 100, 80);
  setParameter(&tb->tempo, "tempo", 40, UINT8_MAX, (60 * 2));

  tb->durationMultiplier = 1;
  tb->durationDivisor = 1;

  setAccidentals(tb, 0);
  setCurrentOctave(tb);

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

BEGIN_USAGE_NOTES(tuneBuilderUsageNotes)
  "A command group is zero or more commands separated from one another by whitespace.",
  "A number sign [#] at the beginning or after whitespace begins a comment.",
  "",
  "Each command is a letter immediately followed by its parameter(s).",
  "In the following descriptions,",
  "<angle brackets> are used to show that a parameter is required",
  "and [square brackets] are used to show that it's optional.",
  "While a command doesn't contain any spaces, some of the descriptions include them for clarity.",
  "When there is a choice, {curly brackets} combined with vertical bar [|] separators are used.",
  "These commands are recognized:",
  "  a-g  the seven standard note letters",
  "  k    change the key",
  "  m    a MIDI note number",
  "  p    change the note period",
  "  r    a rest",
  "  t    change the tempo",
  "",
  "A note command begins with any of the seven standard note letters (a, b, c, d, e, f, g).",
  "Its general syntax is:",
  "",
  "  <letter> [octave] [accidental] [duration]",
  "",
  "The m<number>[duration] command specifies a note by its MIDI number.",
  "The number must be within the range 1 through 127.",
  "MIDI stands for Musical Instrument Digital Interface.",
  "It specifies that Middle-C is note 60, ",
  "that a higher number represents a higher pitch,",
  "and that adjacent numbers represent notes that differ in pitch by 1 semitone.",
  "",
  "The r[duration] command specifies a rest - the musical way of saying \"no note\".",
  "",
  "Octaves are numbered according to International Pitch Notation,",
  "so the scale starting with Middle-C is octave 4.",
  "Octaves 0 through 9 may be specified, although notes above g9 can't be played (this is a MIDI limitation).",
  "If the octave of the first note of the tune isn't specified then octave 4 is assumed.",
  "If it isn't specified for any other note then the technique used in braille music is used.",
  "Normally, the octave of the previous note is assumed.",
  "If, however, the note in an adjacent octave is three semitones or less away from the previous one then the new octave is assumed.",
  "",
  "If the accidental (sharp, flat, or natural) isn't specified then the one defined by the current key is assumed.",
  "It may be specified as",
  "a plus sign [+] for sharp,",
  "a minus sign [-] for flat,",
  "or an equal sign [=] for natural.",
  "More than one sharp or flat (+ or -) may be specified.",
  "",
  "If the duration of a ntoe isn't specified then the duration of the previous note is assumed.",
  "If the duration of the first note isn't specified then the length of one beat at the default tempo is assumed.",
  "A duration may be specified in two ways:",
  "",
  "@<number>:",
  "It may be explicitly set by prefixing the number of milliseconds with an at sign [@].",
  "",
  "[*<multiplier>] [/<divisor>]:",
  "It may be calculated by applying a multiplier and/or a divisor, in that order, to the length of one beat at the current tempo.",
  "The multiplier is a number prefixed with an asterisk [*] and must be within the range 1 through 16.",
  "The divisor is a number prefixed with a slash [/] and must be within the range 1 through 128.",
  "Both default to 1.",
  "",
  "Both ways of specifying the duration allow any number of dots [.] to be appended.",
  "These dots modify the duration of the note in the same way that adding dots to a note does in print (and braille) music.",
  "For example:",
  "At a tempo of 120 (beats per minute), a whole note (4 beats) has a duration of 2 seconds. So:",
  "  #dots  seconds  beats",
  "    0     2       4",
  "    1     3       6",
  "    2     3.5     7",
  "    3     3.75    7+1/2",
  "   etc",
  "",
  "The k command changes the key.",
  "The initial key is C Major, i.e. it has no accidentals.",
  "This command has two forms:",
  "",
  "k<root>[mode]:",
  "The root note must be one of the seven standard note letters (a, b, c, d, e, f, g).",
  "The mode may also be specified.",
  "Any Unambiguous abbreviation of its name may be used.",
  "The recognized mode names are:",
  "major,",
  "minor,",
  "ionian,",
  "dorian,",
  "phrygian,",
  "lydian,",
  "mixolydian,",
  "aeolian,",
  "locrian.",
  "",
  "k[count]<accidental>:",
  "The key may also be implied by specifying how many accidentals (sharps or flats) it has.",
  "The count must be a number within the range 1 through 12 (the number of semitones within a scale).",
  "The accidental must be either a plus sign [+] for sharp or a minus sign [-] for flat.",
  "If the count is specified then there must be one accidental indicator.",
  "If it isn't specified then more than one accidental indicator may be specified.",
  "",
  "The p<number> command changes the note period - the amount of time within its duration that a note is on.",
  "It's a percentage, and must be within the range 1 through 100.",
  "The initial note period is 80 percent.",
  "",
  "The t<number> command changes the tempo (speed).",
  "It's the number of beats per minute, and must be within the range 40 through 255.",
  "The initial tempo is 120 beats per minute.",
END_USAGE_NOTES

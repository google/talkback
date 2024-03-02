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

#include "alert.h"
#include "program.h"
#include "prefs.h"
#include "tune.h"
#include "tune_builder.h"
#include "message.h"
#include "brl_dots.h"
#include "utf8.h"
#include "core.h"

#ifdef ENABLE_SPEECH_SUPPORT
#include "spk.h"
#endif /* ENABLE_SPEECH_SUPPORT */

typedef struct {
  unsigned char duration;
  BrlDots pattern;
} TactileAlert;

typedef struct {
  const char *tune;
  const char *message;
  TactileAlert tactile;
} AlertEntry;

#define ALERT_TACTILE(d,p) {.duration=(d), .pattern=(p)}

static const AlertEntry alertTable[] = {
  [ALERT_BRAILLE_ON] = {
    .tune = "m64@60 m69@100"
  },

  [ALERT_BRAILLE_OFF] = {
    .tune = "m64@60 m57@60"
  },

  [ALERT_COMMAND_DONE] = {
    .message = strtext("Done"),
    .tune = "m74@40 r@30 m74@40 r@40 m74@140 r@20 m79@50"
  },

  [ALERT_COMMAND_REJECTED] = {
    .tactile = ALERT_TACTILE(50, BRL_DOT_1 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_6),
    .tune = "m78@100"
  },

  [ALERT_MARK_SET] = {
    .tune = "m83@20 m81@15 m79@15 m84@25"
  },

  [ALERT_CLIPBOARD_BEGIN] = {
    .tune = "m74@40 m86@20"
  },

  [ALERT_CLIPBOARD_END] = {
    .tune = "m86@50 m74@30"
  },

  [ALERT_NO_CHANGE] = {
    .tactile = ALERT_TACTILE(30, BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_5 | BRL_DOT_6),
    .tune = "m79@30 r@30 m79@30 r@30 m79@30"
  },

  [ALERT_TOGGLE_ON] = {
    .tactile = ALERT_TACTILE(30, BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_5),
    .tune = "m74@30 r@30 m79@30 r@30 m86@30"
  },

  [ALERT_TOGGLE_OFF] = {
    .tactile = ALERT_TACTILE(30, BRL_DOT_3 | BRL_DOT_7 | BRL_DOT_6 | BRL_DOT_8),
    .tune = "m86@30 r@30 m79@30 r@30 m74@30"
  },

  [ALERT_CURSOR_LINKED] = {
    .tune = "m80@7 m79@7 m76@12"
  },

  [ALERT_CURSOR_UNLINKED] = {
    .tune = "m78@7 m79@7 m83@20"
  },

  [ALERT_SCREEN_FROZEN] = {
    .message = strtext("Frozen"),
    .tune = "m58@5 m59 m60 m61 m62 m63 m64 m65 m66 m67 m68 m69 m70 m71 m72 m73 m74 m76 m78 m80 m83 m86 m90 m95"
  },

  [ALERT_SCREEN_UNFROZEN] = {
    .message = strtext("Unfrozen"),
    .tune = "m95@5 m90 m86 m83 m80 m78 m76 m74 m73 m72 m71 m70 m69 m68 m67 m66 m65 m64 m63 m62 m61 m60 m59 m58"
  },

  [ALERT_FREEZE_REMINDER] = {
    .tune = "m60@50 r@30 m60@50"
  },

  [ALERT_WRAP_DOWN] = {
    .tactile = ALERT_TACTILE(20, BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6 | BRL_DOT_8),
    .tune = "m86@6 m74@6 m62@6 m50@10"
  },

  [ALERT_WRAP_UP] = {
    .tactile = ALERT_TACTILE(20, BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_7),
    .tune = "m50@6 m62@6 m74@6 m86@10"
  },

  [ALERT_SKIP_FIRST] = {
    .tactile = ALERT_TACTILE(30, BRL_DOT_1 | BRL_DOT_4 | BRL_DOT_7 | BRL_DOT_8),
    .tune = "r@40 m62@4 m67@6 m74@8 r@25"
  },

  [ALERT_SKIP_ONE] = {
    .tune = "m74@10 r@18"
  },

  [ALERT_SKIP_SEVERAL] = {
    .tune = "m73@20 r@1"
  },

  [ALERT_BOUNCE] = {
    .tactile = ALERT_TACTILE(50, BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6 | BRL_DOT_7 | BRL_DOT_8),
    .tune = "m98@6 m86@6 m74@6 m62@6 m50@10"
  },

  [ALERT_ROUTING_STARTED] = {
    .tune = "m55@10 r@60 m60@15"
  },

  [ALERT_ROUTING_SUCCEEDED] = {
    .tune = "m64@60 m76@20"
  },

  [ALERT_ROUTING_FAILED] = {
    .tune = "m80@80 m79@90 m78@100 m77@100 r@20 m77@100 r@20 m77@150"
  },

  [ALERT_MODIFIER_ONCE] = {
    .tune = "m70@60 m74@60 m77@90"
  },

  [ALERT_MODIFIER_LOCK] = {
    .tune = "m70@60 m74@60 m77@60 m82@90"
  },

  [ALERT_MODIFIER_OFF] = {
    .tune = "m82@60 m77@60 m74@60 m70@90"
  },

  [ALERT_CONSOLE_BELL] = {
    .message = strtext("Console Bell"),
    .tune = "m78@100"
  },

  [ALERT_KEYS_AUTORELEASED] = {
    .message = strtext("Autorelease"),
    .tune = "c6@50 b- g e- p50 c@100 c c"
  },

  [ALERT_SCROLL_UP] = {
    .tune = "b6@10 d7"
  },

  [ALERT_CONTEXT_DEFAULT] = {
    .tune = "m76@60 m73@60 m69@60 m66@90"
  },

  [ALERT_CONTEXT_PERSISTENT] = {
    .tune = "m66@60 m69@60 m73@60 m76@90"
  },

  [ALERT_CONTEXT_TEMPORARY] = {
    .tune = "m66@60 m69@60 m73@90"
  },
};

static ToneElement *tuneTable[ARRAY_COUNT(alertTable)] = {NULL};
static TuneBuilder *tuneBuilder = NULL;
static ToneElement emptyTune[] = {TONE_STOP()};

static void
exitAlertTunes (void *data) {
  tuneSynchronize();

  {
    ToneElement **tune = tuneTable;
    ToneElement **end = tune + ARRAY_COUNT(tuneTable);

    while (tune < end) {
      if (*tune) {
        if (*tune != emptyTune) free(*tune);
        *tune = NULL;
      }

      tune += 1;
    }
  }

  if (tuneBuilder) {
    destroyTuneBuilder(tuneBuilder);
    tuneBuilder = NULL;
  }
}

static TuneBuilder *
getTuneBuilder (void) {
  if (!tuneBuilder) {
    if (!(tuneBuilder = newTuneBuilder())) {
      return NULL;
    }

    onProgramExit("alert-tunes", exitAlertTunes, NULL);
  }

  return tuneBuilder;
}

void
alert (AlertIdentifier identifier) {
  if (identifier < ARRAY_COUNT(alertTable)) {
    const AlertEntry *alert = &alertTable[identifier];

    if (prefs.alertTunes && alert->tune && *alert->tune) {
      ToneElement **tune = &tuneTable[identifier];

      if (!*tune) {
        TuneBuilder *tb = getTuneBuilder();

        if (tb) {
          setTuneSourceName(tuneBuilder, "alert");
          setTuneSourceIndex(tb, identifier);

          if (parseTuneString(tb, "p100")) {
            if (parseTuneString(tb, alert->tune)) {
              *tune = getTune(tb);
            }
          }

          resetTuneBuilder(tb);
        }

        if (!*tune) *tune = emptyTune;
      }

      tunePlayTones(*tune);
    } else if (prefs.alertDots && alert->tactile.duration) {
      showDotPattern(alert->tactile.pattern, alert->tactile.duration);
    } else if (prefs.alertMessages && alert->message) {
      message(NULL, gettext(alert->message), 0);
    }
  }
}

void
speakAlertMessage (const char *message) {
#ifdef ENABLE_SPEECH_SUPPORT
  sayString(&spk, message, SAY_OPT_MUTE_FIRST);
#endif /* ENABLE_SPEECH_SUPPORT */
}

void
speakAlertText (const wchar_t *text) {
  char *message = getUtf8FromWchars(text, wcslen(text), NULL);

  if (message) {
    speakAlertMessage(message);
    free(message);
  }
}

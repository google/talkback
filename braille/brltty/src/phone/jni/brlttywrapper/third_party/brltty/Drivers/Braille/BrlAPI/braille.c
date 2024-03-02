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

#include "log.h"
#include "parse.h"
#include "scr.h"
#include "cmd_brlapi.h"
#include "charset.h"

#define BRLAPI_NO_DEPRECATED
#include "brlapi.h"

typedef enum {
  PARM_HOST,
  PARM_AUTH,
  PARM_SPEECH_CHANGES,
} DriverParameter;
#define BRLPARMS "host", "auth", "speechChanges"

#include "brl_driver.h"

#define CHECK(cond, label) \
  do { \
    if (!(cond)) { \
      logMessage(LOG_ERR, "%s", brlapi_strerror(&brlapi_error)); \
      goto label; \
    } \
  } while (0);

static brlapi_param_clientPriority_t currentPriority;
static const brlapi_param_clientPriority_t qualityPriorities[] = {
  [SCQ_NONE] = BRLAPI_PARAM_CLIENT_PRIORITY_DISABLE,
  [SCQ_LOW]  = BRLAPI_PARAM_CLIENT_PRIORITY_DEFAULT - 40,
  [SCQ_POOR] = BRLAPI_PARAM_CLIENT_PRIORITY_DEFAULT - 25,
  [SCQ_FAIR] = BRLAPI_PARAM_CLIENT_PRIORITY_DEFAULT - 10,
  [SCQ_GOOD] = BRLAPI_PARAM_CLIENT_PRIORITY_DEFAULT + 10,
  [SCQ_HIGH] = BRLAPI_PARAM_CLIENT_PRIORITY_DEFAULT + 30,
};

static int displaySize;
static unsigned char *prevData;
static wchar_t *prevText;
static int prevCursor;
static int prevShown;

static int restart;

static int
ignoreSpeechChangeCommands (void) {
  static const brlapi_keyCode_t commands[] = {
    BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_LOUDER,
    BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_SOFTER,
    BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_FASTER,
    BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_SLOWER,
    BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_HIGHER,
    BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_LOWER,
  };

  CHECK(brlapi_ignoreKeys(brlapi_rangeType_command, commands, ARRAY_COUNT(commands))>=0, failed);
  return 1;

failed:
  return 0;
}

/* Function : brl_construct */
/* Opens a connection with BrlAPI's server */
static int brl_construct(BrailleDisplay *brl, char **parameters, const char *device)
{
  currentPriority = BRLAPI_PARAM_CLIENT_PRIORITY_DEFAULT;

  brlapi_connectionSettings_t settings;
  settings.host = parameters[PARM_HOST];
  settings.auth = parameters[PARM_AUTH];

  CHECK((brlapi_openConnection(&settings, &settings)>=0), out);
  logMessage(LOG_CATEGORY(BRAILLE_DRIVER),
    "connected to %s using %s", settings.host, settings.auth
  );

  CHECK((brlapi_enterTtyModeWithPath(NULL, 0, NULL)>=0), out0);
  logMessage(LOG_CATEGORY(BRAILLE_DRIVER),
    "got tty successfully"
  );

  CHECK((brlapi_getDisplaySize(&brl->textColumns, &brl->textRows)==0), out1);
  displaySize = brl->textColumns * brl->textRows;
  logMessage(LOG_CATEGORY(BRAILLE_DRIVER),
    "got display size: %dx%d", brl->textColumns, brl->textRows
  );

  {
    unsigned int speechChanges = 1;
    const char *parameter = parameters[PARM_SPEECH_CHANGES];

    if (*parameter) {
      if (!validateYesNo(&speechChanges, parameter)) {
         logMessage(LOG_WARNING, "%s: %s", "invalid speech changes setting", parameter);
      } else if (!speechChanges) {
        ignoreSpeechChangeCommands();
      }
    }
  }

  brl->hideCursor = 1;

  prevData = malloc(displaySize);
  CHECK((prevData!=NULL), out1);
  memset(prevData, 0, displaySize);

  prevText = malloc(displaySize * sizeof(wchar_t));
  CHECK((prevText!=NULL), out2);
  wmemset(prevText, WC_C(' '), displaySize);

  prevShown = 0;
  prevCursor = BRL_NO_CURSOR;
  restart = 0;

  logMessage(LOG_CATEGORY(BRAILLE_DRIVER),
             "Memory allocated, returning 1");
  return 1;
  
out2:
  free(prevData);
out1:
  brlapi_leaveTtyMode();
out0:
  brlapi_closeConnection();
out:
  logMessage(LOG_CATEGORY(BRAILLE_DRIVER),
             "Something went wrong, returning 0");
  return 0;
}

/* Function : brl_destruct */
/* Frees memory and closes the connection with BrlAPI */
static void brl_destruct(BrailleDisplay *brl)
{
  free(prevData);
  free(prevText);
  brlapi_closeConnection();
}

static int
setClientPriority (BrailleDisplay *brl) {
  unsigned char worst = ARRAY_COUNT(qualityPriorities) - 1;
  unsigned char quality = MIN(brl->quality, worst);
  brlapi_param_clientPriority_t priority = qualityPriorities[quality];

  if (priority != currentPriority) {
    int result = brlapi_setParameter(
      BRLAPI_PARAM_CLIENT_PRIORITY, 0,
      BRLAPI_PARAMF_LOCAL, &priority, sizeof(priority)
    );

    if (result < 0) return 0;
    currentPriority = priority;
  }

  return 1;
}

/* function : brl_writeWindow */
/* Displays a text on the braille window, only if it's different from */
/* the one already displayed */
static int brl_writeWindow(BrailleDisplay *brl, const wchar_t *text)
{
  setClientPriority(brl);

  brlapi_writeArguments_t arguments = BRLAPI_WRITEARGUMENTS_INITIALIZER;
  int vt = currentVirtualTerminal();

  if (vt == SCR_NO_VT) {
    /* should leave display */
    if (prevShown) {
      brlapi_write(&arguments);
      prevShown = 0;
    }
  } else {
    if (prevShown &&
        (memcmp(prevData,brl->buffer,displaySize) == 0) &&
        (!text || (wmemcmp(prevText,text,displaySize) == 0)) &&
        (brl->cursor == prevCursor)) {
      return 1;
    }

    unsigned char and[displaySize];
    memset(and, 0, sizeof(and));
    arguments.andMask = and;
    arguments.orMask = brl->buffer;

    if (text) {
      arguments.text = (char*) text;
      arguments.textSize = displaySize * sizeof(wchar_t);
      arguments.charset = (char*) getWcharCharset();
    }

    arguments.regionBegin = 1;
    arguments.regionSize = displaySize;
    arguments.cursor = (brl->cursor != BRL_NO_CURSOR)? (brl->cursor + 1): BRLAPI_CURSOR_OFF;

    if (brlapi_write(&arguments)==0) {
      memcpy(prevData,brl->buffer,displaySize);
      if (text)
	wmemcpy(prevText,text,displaySize);
      else
	wmemset(prevText,0,displaySize);
      prevCursor = brl->cursor;
      prevShown = 1;
    } else {
      logMessage(LOG_ERR, "write: %s", brlapi_strerror(&brlapi_error));
      restart = 1;
    }
  }

  return 1;
}

/* Function : brl_readCommand */
/* Reads a command from the braille keyboard */
static int brl_readCommand(BrailleDisplay *brl, KeyTableCommandContext context)
{
  brlapi_keyCode_t keycode;
  if (restart) return BRL_CMD_RESTARTBRL;
  switch (brlapi_readKey(0, &keycode)) {
    case 0: return EOF;
    case 1: return cmdBrlapiToBrltty(keycode);
    default: return BRL_CMD_RESTARTBRL;
  }
}

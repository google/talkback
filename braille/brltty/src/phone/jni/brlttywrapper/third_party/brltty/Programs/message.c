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

#include "embed.h"
#include "log.h"
#include "log_history.h"
#include "message.h"
#include "defaults.h"
#include "async_wait.h"
#include "async_task.h"
#include "utf8.h"
#include "brl_utils.h"
#include "brl_cmds.h"
#include "spk.h"
#include "ktb_types.h"
#include "update.h"
#include "cmd_queue.h"
#include "api_control.h"
#include "core.h"

int messageHoldTimeout = DEFAULT_MESSAGE_HOLD_TIMEOUT;

typedef struct {
  const char *mode;
  MessageOptions options;
  unsigned presented:1;
  unsigned deallocate:1;
  char text[0];
} MessageParameters;

typedef struct {
  const wchar_t *start;
  size_t length;
} MessageSegment;

typedef struct {
  const MessageParameters *parameters;

  struct {
    const MessageSegment *first;
    const MessageSegment *current;
    const MessageSegment *last;
  } segments;

  int timeout;
  unsigned endWait:1;
  unsigned hold:1;
  unsigned touch:1;
} MessageData;

ASYNC_CONDITION_TESTER(testEndMessageWait) {
  const MessageData *mgd = data;
  return mgd->endWait;
}

static int
handleMessageCommands (int command, void *data) {
  MessageData *mgd = data;

  switch (command & BRL_MSK_CMD) {
    case BRL_CMD_LNUP:
    case BRL_CMD_PRDIFLN:
    case BRL_CMD_FWINLTSKIP:
    case BRL_CMD_FWINLT: {
      if (mgd->segments.current > mgd->segments.first) {
        mgd->segments.current -= 1;
        mgd->endWait = 1;
      }

      mgd->hold = 1;
      return 1;
    }

    case BRL_CMD_LNDN:
    case BRL_CMD_NXDIFLN:
    case BRL_CMD_FWINRTSKIP:
    case BRL_CMD_FWINRT: {
      if ((mgd->hold = mgd->segments.current < mgd->segments.last)) {
        mgd->segments.current += 1;
      }

      break;
    }

    default: {
      int arg = command & BRL_MSK_ARG;

      switch (command & BRL_MSK_BLK) {
        case BRL_CMD_BLK(TOUCH_AT):
          if ((mgd->touch = arg != BRL_MSK_ARG)) return 1;
          mgd->timeout = 1000;
          break;

        default:
          mgd->hold = 0;
          break;
      }

      break;
    }
  }

  mgd->endWait = 1;
  return 1;
}

ASYNC_TASK_CALLBACK(presentMessage) {
  MessageParameters *mgp = data;

#ifdef ENABLE_SPEECH_SUPPORT
  if (!(mgp->options & MSG_SILENT)) {
    if (isAutospeakActive()) {
      sayString(&spk, mgp->text, SAY_OPT_MUTE_FIRST);
    }
  }
#endif /* ENABLE_SPEECH_SUPPORT */

  if (canBraille()) {
    MessageData mgd = {
      .hold = 0,
      .touch = 0,
      .parameters = mgp
    };

    const size_t characterCount = countUtf8Characters(mgp->text);
    MessageSegment messageSegments[characterCount];
    wchar_t characters[characterCount + 1];
    makeWcharsFromUtf8(mgp->text, characters, ARRAY_COUNT(characters));

    const size_t brailleSize = textCount * brl.textRows;
    wchar_t brailleBuffer[brailleSize];

    {
      const wchar_t *character = characters;
      const wchar_t *const end = character + characterCount;

      MessageSegment *segment = messageSegments;
      mgd.segments.current = mgd.segments.first = segment;

      while (*character) {
        /* strip leading spaces */
        while ((character < end) && iswspace(*character)) character += 1;

        const size_t charactersLeft = end - character;
        if (!charactersLeft) break;

        segment->start = character;
        segment->length = MIN(charactersLeft, brailleSize);

        character += segment->length;
        segment += 1;
      }

      mgd.segments.last = segment - 1;
    }

    int wasLinked = api.isServerLinked();
    if (wasLinked) api.unlinkServer();

    suspendUpdates();
    pushCommandEnvironment("message", NULL, NULL);
    pushCommandHandler("message", KTB_CTX_WAITING,
                       handleMessageCommands, NULL, &mgd);

    while (1) {
      const MessageSegment *segment = mgd.segments.current;
      size_t cellCount = segment->length;
      int lastSegment = segment == mgd.segments.last;

      wmemcpy(brailleBuffer, segment->start, cellCount);
      brl.cursor = BRL_NO_CURSOR;

      if (!writeBrailleCharacters(mgp->mode, brailleBuffer, cellCount)) {
        mgp->presented = 0;
        break;
      }

      mgd.timeout = messageHoldTimeout - brl.writeDelay;
      drainBrailleOutput(&brl, 0);
      if (!mgd.hold && lastSegment && (mgp->options & MSG_NODELAY)) break;
      mgd.timeout = MAX(mgd.timeout, 0);

      while (1) {
        int timeout = mgd.timeout;
        mgd.timeout = -1;

        mgd.endWait = 0;
        int timedOut = !asyncAwaitCondition(timeout, testEndMessageWait, &mgd);
        if (mgd.segments.current != segment) break;

        if (mgd.hold || mgd.touch) {
          mgd.timeout = 1000000;
        } else if (timedOut) {
          if (lastSegment) goto DONE;
          mgd.segments.current += 1;
          mgd.timeout = messageHoldTimeout;
          break;
        } else if (mgd.timeout < 0) {
          goto DONE;
        }
      }
    }

  DONE:
    popCommandEnvironment();
    resumeUpdates(1);
    if (wasLinked) api.linkServer();
  }

  if (mgp->deallocate) free(mgp);
}

int 
message (const char *mode, const char *text, MessageOptions options) {
  if (options & MSG_LOG) pushLogMessage(text);

  int presented = 0;
  MessageParameters *mgp;
  size_t size = sizeof(*mgp) + strlen(text) + 1;

  if ((mgp = malloc(size))) {
    memset(mgp, 0, size);
    mgp->mode = mode? mode: "";
    mgp->options = options;
    mgp->presented = 1;
    strcpy(mgp->text, text);

    if (mgp->options & MSG_SYNC) {
      mgp->deallocate = 0;
      presentMessage(mgp);
      if (mgp->presented) presented = 1;
    } else {
      mgp->deallocate = 1;
      if (asyncAddTask(NULL, presentMessage, mgp)) return 1;
    }

    free(mgp);
  }

  return presented;
}

void
showMessage (const char *text) {
  message(NULL, text, 0);
}

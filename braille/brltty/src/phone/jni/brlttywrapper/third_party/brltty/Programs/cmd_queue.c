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

#include "log.h"
#include "cmd_queue.h"
#include "cmd_enqueue.h"
#include "brl_cmds.h"
#include "queue.h"
#include "async_alarm.h"
#include "prefs.h"
#include "ktb_types.h"
#include "scr.h"
#include "core.h"

#define LOG_LEVEL LOG_DEBUG

typedef struct CommandEnvironmentStruct CommandEnvironment;
typedef struct CommandHandlerLevelStruct CommandHandlerLevel;

struct CommandHandlerLevelStruct {
  CommandHandlerLevel *previousLevel;
  const char *levelName;

  CommandHandler *handleCommand;
  CommandDataDestructor *destroyData;
  void *handlerData;
  KeyTableCommandContext commandContext;
};

struct CommandEnvironmentStruct {
  CommandEnvironment *previousEnvironment;
  const char *environmentName;

  CommandHandlerLevel *handlerStack;
  CommandPreprocessor *preprocessCommand;
  CommandPostprocessor *postprocessCommand;
  unsigned handlingCommand:1;
};

static CommandEnvironment *commandEnvironmentStack = NULL;
static unsigned int commandQueueSuspendCount = 0;

static CommandHandlerLevel **
getCommandHandlerTop (void) {
  if (!commandEnvironmentStack) return NULL;
  return &commandEnvironmentStack->handlerStack;
}

KeyTableCommandContext
getCurrentCommandContext (void) {
  CommandHandlerLevel **top = getCommandHandlerTop();
  KeyTableCommandContext context = top && *top? (*top)->commandContext: KTB_CTX_DEFAULT;

  if (context == KTB_CTX_DEFAULT) context = getScreenCommandContext();
  return context;
}

int
handleCommand (int command) {
  {
    int real = command;

    if (prefs.skipIdenticalLines) {
      switch (command & BRL_MSK_CMD) {
        case BRL_CMD_LNUP:
          real = BRL_CMD_PRDIFLN;
          break;

        case BRL_CMD_LNDN:
          real = BRL_CMD_NXDIFLN;
          break;

        case BRL_CMD_PRDIFLN:
          real = BRL_CMD_LNUP;
          break;

        case BRL_CMD_NXDIFLN:
          real = BRL_CMD_LNDN;
          break;

        default:
          break;
      }
    }

    if (prefs.skipBlankBrailleWindows) {
      switch (command & BRL_MSK_CMD) {
        case BRL_CMD_FWINLT:
          real = BRL_CMD_FWINLTSKIP;
          break;

        case BRL_CMD_FWINRT:
          real = BRL_CMD_FWINRTSKIP;
          break;

        case BRL_CMD_FWINLTSKIP:
          real = BRL_CMD_FWINLT;
          break;

        case BRL_CMD_FWINRTSKIP:
          real = BRL_CMD_FWINRT;
          break;

        default:
          break;
      }
    }

    if (real == command) {
      logCommand(command);
    } else {
      real |= (command & ~BRL_MSK_CMD);
      logTransformedCommand(command, real);
      command = real;
    }
  }

  {
    const CommandEnvironment *env = commandEnvironmentStack;
    const CommandHandlerLevel *chl = env->handlerStack;

    while (chl) {
      if (chl->handleCommand(command, chl->handlerData)) return 1;
      chl = chl->previousLevel;
    }
  }

  logMessage(LOG_WARNING, "%s: %04X", gettext("unhandled command"), command);
  return 0;
}

typedef struct {
  int command;
} CommandQueueItem;

static void
deallocateCommandQueueItem (void *item, void *data) {
  CommandQueueItem *cmd = item;

  free(cmd);
}

static Queue *
createCommandQueue (void *data) {
  return newQueue(deallocateCommandQueueItem, NULL);
}

static Queue *
getCommandQueue (int create) {
  static Queue *commands = NULL;

  return getProgramQueue(&commands, "command-queue", create,
                         createCommandQueue, NULL);
}

static int
dequeueCommand (Queue *queue) {
  CommandQueueItem *item;

  if ((item = dequeueItem(queue))) {
    int command = item->command;

    free(item);
    item = NULL;

    return command;
  }

  return EOF;
}

static void setCommandAlarm (void *data);
static AsyncHandle commandAlarm = NULL;

ASYNC_ALARM_CALLBACK(handleCommandAlarm) {
  Queue *queue = getCommandQueue(0);

  asyncDiscardHandle(commandAlarm);
  commandAlarm = NULL;

  if (queue) {
    int command = dequeueCommand(queue);

    if (command != EOF) {
      CommandEnvironment *env = commandEnvironmentStack;
      void *state;
      int handled;

      env->handlingCommand = 1;
      state = env->preprocessCommand? env->preprocessCommand(): NULL;
      handled = handleCommand(command);
      if (env->postprocessCommand) env->postprocessCommand(state, command, handled);
      env->handlingCommand = 0;
    }
  }

  setCommandAlarm(parameters->data);
}

static void
setCommandAlarm (void *data) {
  if (!commandAlarm && !commandQueueSuspendCount) {
    const CommandEnvironment *env = commandEnvironmentStack;

    if (env && !env->handlingCommand) {
      Queue *queue = getCommandQueue(0);

      if (queue && (getQueueSize(queue) > 0)) {
        asyncNewRelativeAlarm(&commandAlarm, 0, handleCommandAlarm, data);
      }
    }
  }
}

static void
cancelCommandAlarm (void) {
  if (commandAlarm) {
    asyncCancelRequest(commandAlarm);
    commandAlarm = NULL;
  }
}

int
enqueueCommand (int command) {
  if (command == EOF) return 1;

  {
    Queue *queue = getCommandQueue(1);

    if (queue) {
      CommandQueueItem *item = malloc(sizeof(CommandQueueItem));

      if (item) {
        item->command = command;

        if (enqueueItem(queue, item)) {
          setCommandAlarm(NULL);
          return 1;
        }

        free(item);
      } else {
        logMallocError();
      }
    }
  }

  return 0;
}

int
pushCommandHandler (
  const char *name,
  KeyTableCommandContext context,
  CommandHandler *handler,
  CommandDataDestructor *destructor,
  void *data
) {
  CommandHandlerLevel *chl;

  if ((chl = malloc(sizeof(*chl)))) {
    memset(chl, 0, sizeof(*chl));
    chl->levelName = name;
    chl->handleCommand = handler;
    chl->destroyData = destructor;
    chl->handlerData = data;
    chl->commandContext = context;

    {
      CommandHandlerLevel **top = getCommandHandlerTop();

      chl->previousLevel = *top;
      *top = chl;
    }

    logMessage(LOG_LEVEL, "pushed command handler: %s", chl->levelName);
    return 1;
  } else {
    logMallocError();
  }

  return 0;
}

int
popCommandHandler (void) {
  CommandHandlerLevel **top = getCommandHandlerTop();
  CommandHandlerLevel *chl = *top;

  if (!chl) return 0;
  *top = chl->previousLevel;

  logMessage(LOG_LEVEL, "popped command handler: %s", chl->levelName);
  if (chl->destroyData) chl->destroyData(chl->handlerData);
  free(chl);
  return 1;
}

int
pushCommandEnvironment (
  const char *name,
  CommandPreprocessor *preprocessCommand,
  CommandPostprocessor *postprocessCommand
) {
  CommandEnvironment *env;

  if ((env = malloc(sizeof(*env)))) {
    memset(env, 0, sizeof(*env));
    env->environmentName = name;
    env->handlerStack = NULL;
    env->preprocessCommand = preprocessCommand;
    env->postprocessCommand = postprocessCommand;
    env->handlingCommand = 0;

    env->previousEnvironment = commandEnvironmentStack;
    commandEnvironmentStack = env;
    setCommandAlarm(NULL);

    logMessage(LOG_LEVEL, "pushed command environment: %s", env->environmentName);
    return 1;
  } else {
    logMallocError();
  }

  return 0;
}

int
popCommandEnvironment (void) {
  CommandEnvironment *env = commandEnvironmentStack;

  if (!env) return 0;
  while (popCommandHandler());
  commandEnvironmentStack = env->previousEnvironment;

  {
    const CommandEnvironment *env = commandEnvironmentStack;

    if (!env || env->handlingCommand) {
      cancelCommandAlarm();
    }
  }

  logMessage(LOG_LEVEL, "popped command environment: %s", env->environmentName);
  free(env);
  return 1;
}

int
beginCommandQueue (void) {
  commandEnvironmentStack = NULL;
  commandQueueSuspendCount = 0;

  return pushCommandEnvironment("initial", NULL, NULL);
}

void
endCommandQueue (void) {
  while (popCommandEnvironment());
}

void
suspendCommandQueue (void) {
  if (!commandQueueSuspendCount++) cancelCommandAlarm();
}

void
resumeCommandQueue (void) {
  if (!--commandQueueSuspendCount) setCommandAlarm(NULL);
}

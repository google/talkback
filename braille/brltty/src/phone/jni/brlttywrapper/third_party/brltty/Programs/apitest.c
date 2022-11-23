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

/* apitest provides a small test utility for BRLTTY's API */

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <signal.h>
#ifdef __MINGW32__
#include "win_pthread.h"
#else
#include <pthread.h>
#endif

#include "options.h"
#include "brl_cmds.h"
#include "brl_dots.h"
#include "cmd.h"
#include "cmd_brlapi.h"
#include "async_wait.h"

#define BRLAPI_NO_DEPRECATED
#include "brlapi.h"

static brlapi_connectionSettings_t settings;
static char *opt_host;
static char *opt_auth;

static int opt_learnMode;
static int opt_showDots;
static int opt_showName;
static int opt_showModelIdentifier;
static int opt_showSize;
static int opt_showKeyCodes;
static int opt_suspendMode;
static int opt_threadMode;

BEGIN_OPTION_TABLE(programOptions)
  { .letter = 'n',
    .word = "name",
    .setting.flag = &opt_showName,
    .description = "Show the name of the braille driver."
  },

  { .letter = 'm',
    .word = "model",
    .setting.flag = &opt_showModelIdentifier,
    .description = "Show the model identifier of the braille device."
  },


  { .letter = 'w',
    .word = "window",
    .setting.flag = &opt_showSize,
    .description = "Show the dimensions of the braille window."
  },

  { .letter = 'd',
    .word = "dots",
    .setting.flag = &opt_showDots,
    .description = "Show dot pattern."
  },

  { .letter = 'l',
    .word = "learn",
    .setting.flag = &opt_learnMode,
    .description = "Enter interactive command learn mode."
  },

  { .letter = 'k',
    .word = "keycodes",
    .setting.flag = &opt_showKeyCodes,
    .description = "Enter interactive keycode learn mode."
  },

  { .letter = 's',
    .word = "suspend",
    .setting.flag = &opt_suspendMode,
    .description = "Suspend the braille driver (press ^C or send SIGUSR1 to resume)."
  },

  { .letter = 't',
    .word = "thread",
    .setting.flag = &opt_threadMode,
    .description = "Exercise threaded use"
  },

  { .letter = 'b',
    .word = "brlapi",
    .argument = "[host][:port]",
    .setting.string = &opt_host,
    .description = "BrlAPIa host and/or port to connect to."
  },

  { .letter = 'a',
    .word = "auth",
    .argument = "file",
    .setting.string = &opt_auth,
    .description = "BrlAPI authorization/authentication string."
  },
END_OPTION_TABLE

static void showDisplaySize(void)
{
  unsigned int x, y;
  fprintf(stderr,"Getting display size: ");
  if (brlapi_getDisplaySize(&x, &y)<0) {
    brlapi_perror("failed");
    exit(PROG_EXIT_FATAL);
  }
  fprintf(stderr, "%dX%d\n", x, y);
}

static void showDriverName(void)
{
  char name[30];
  fprintf(stderr, "Getting driver name: ");
  if (brlapi_getDriverName(name, sizeof(name))<0) {
    brlapi_perror("failed");
    exit(PROG_EXIT_FATAL);
  }
  fprintf(stderr, "%s\n", name);
}

static void showModelIdentifier(void)
{
  char identifier[30];
  fprintf(stderr, "Getting model identifier: ");
  if (brlapi_getModelIdentifier(identifier, sizeof(identifier))<0) {
    brlapi_perror("failed");
    exit(PROG_EXIT_FATAL);
  }
  fprintf(stderr, "%s\n", identifier);
}

#define DOTS_TEXT "dots: "
#define DOTS_TEXTLEN (strlen(DOTS_TEXT))
#define DOTS_LEN 8
#define DOTS_TOTALLEN (DOTS_TEXTLEN+DOTS_LEN)
static void showDots(void)
{
  unsigned int x, y;
  brlapi_keyCode_t k;
  if (brlapi_getDisplaySize(&x, &y)<0) {
    brlapi_perror("failed");
    exit(PROG_EXIT_FATAL);
  }
  if (brlapi_enterTtyMode(-1, NULL)<0) {
    brlapi_perror("enterTtyMode");
    exit(PROG_EXIT_FATAL);
  }
  if (x*y<DOTS_TOTALLEN) {
    fprintf(stderr,"can't show dots with a braille display with less than %d cells\n",(int)DOTS_TOTALLEN);
    exit(PROG_EXIT_SEMANTIC);
  }
  {
    char text[x*y+1];
    unsigned char or[x*y];
    brlapi_writeArguments_t wa = BRLAPI_WRITEARGUMENTS_INITIALIZER;
    fprintf(stderr,"Showing dot patterns\n");
    memcpy(text,DOTS_TEXT,DOTS_TEXTLEN);
    memset(text+DOTS_TEXTLEN,' ',sizeof(text)-DOTS_TEXTLEN);
    text[x*y] = 0;
    wa.regionBegin = 1;
    wa.regionSize = sizeof(or);
    wa.text = text;
    memset(or,0,sizeof(or));
    or[DOTS_TEXTLEN+0] = BRL_DOT_1;
    or[DOTS_TEXTLEN+1] = BRL_DOT_2;
    or[DOTS_TEXTLEN+2] = BRL_DOT_3;
    or[DOTS_TEXTLEN+3] = BRL_DOT_4;
    or[DOTS_TEXTLEN+4] = BRL_DOT_5;
    or[DOTS_TEXTLEN+5] = BRL_DOT_6;
    or[DOTS_TEXTLEN+6] = BRL_DOT_7;
    or[DOTS_TEXTLEN+7] = BRL_DOT_8;
    wa.orMask = or;
    if (brlapi_write(&wa)<0) {
      brlapi_perror("brlapi_write");
      exit(PROG_EXIT_FATAL);
    }
  }
  brlapi_readKey(1, &k);
}

static void enterLearnMode(void)
{
  int res;
  brlapi_keyCode_t code;
  int cmd;
  char buf[0X100];

  fprintf(stderr,"Entering command learn mode\n");
  if (brlapi_enterTtyMode(-1, NULL)<0) {
    brlapi_perror("enterTtyMode");
    return;
  }

  if (brlapi_writeText(BRLAPI_CURSOR_OFF, "command learn mode")<0) {
    brlapi_perror("brlapi_writeText");
    exit(PROG_EXIT_FATAL);
  }

  while ((res = brlapi_readKey(1, &code)) != -1) {
    fprintf(stderr, "got key %016"BRLAPI_PRIxKEYCODE"\n",code);
    cmd = cmdBrlapiToBrltty(code);
    describeCommand(buf, sizeof(buf), cmd,
                    (CDO_IncludeName | CDO_IncludeOperand));
    brlapi_writeText(BRLAPI_CURSOR_OFF, buf);
    fprintf(stderr, "%s\n", buf);
    if (cmd==BRL_CMD_LEARN) return;
  }
  brlapi_perror("brlapi_readKey");
}

static void showKeyCodes(void)
{
  int res;
  brlapi_keyCode_t cmd;
  char buf[0X100];

  fprintf(stderr,"Entering keycode learn mode\n");
  if (brlapi_getDriverName(buf, sizeof(buf))==-1) {
    brlapi_perror("getDriverName");
    return;
  }
  if (brlapi_enterTtyMode(-1, buf)<0) {
    brlapi_perror("enterTtyMode");
    return;
  }

  if (brlapi_acceptAllKeys()==-1) {
    brlapi_perror("acceptAllKeys");
    return;
  }

  if (brlapi_writeText(BRLAPI_CURSOR_OFF, "show key codes")<0) {
    brlapi_perror("brlapi_writeText");
    exit(PROG_EXIT_FATAL);
  }

  while ((res = brlapi_readKey(1, &cmd)) != -1) {
    sprintf(buf, "0X%" BRLAPI_PRIxKEYCODE " (%" BRLAPI_PRIuKEYCODE ")",cmd, cmd);
    brlapi_writeText(BRLAPI_CURSOR_OFF, buf);
    fprintf(stderr, "%s\n", buf);
  }
  brlapi_perror("brlapi_readKey");
}

#ifdef SIGUSR1
static void emptySignalHandler(int sig) { }
#endif /* SIGUSR1 */

static void suspendDriver(void)
{
  char name[30];
  fprintf(stderr, "Getting driver name: ");
  if (brlapi_getDriverName(name, sizeof(name))<0) {
    brlapi_perror("failed");
    exit(PROG_EXIT_FATAL);
  }
  fprintf(stderr, "%s\n", name);
  fprintf(stderr, "Suspending\n");
  if (brlapi_suspendDriver(name)) {
    brlapi_perror("suspend");
  } else {
#ifdef SIGUSR1
    signal(SIGUSR1,emptySignalHandler);
#endif /* SIGUSR1 */
    fprintf(stderr, "Sleeping\n");
#ifdef HAVE_PAUSE
    pause();
#endif /* HAVE_PAUSE */
    fprintf(stderr, "Resuming\n");
#ifdef SIGUSR1
    signal(SIGUSR1,SIG_DFL);
#endif /* SIGUSR1 */
    if (brlapi_resumeDriver())
      brlapi_perror("resumeDriver");
  }
}

volatile int thread_done;
static void *thread_fun(void *foo)
{
  brlapi_keyCode_t code;
  do {
    brlapi_readKey(1, &code);
    printf("got key %"PRIx64"\n", code);
    if (brlapi_readKeyWithTimeout(1000, &code) != 1) {
      printf("didn't get a key within the 1s delay\n");
    } else {
      printf("got key %"PRIx64" within the 1s delay\n", code);
    }
  } while (code != (BRLAPI_KEY_TYPE_CMD | BRL_CMD_HOME));
  thread_done = 1;
  return NULL;
}

static void exerciseThreads(void)
{
  pthread_t thread;
  unsigned x, y;
  unsigned i = 0;
  if (brlapi_getDisplaySize(&x, &y)<0) {
    brlapi_perror("failed");
    exit(PROG_EXIT_FATAL);
  }
  if (brlapi_enterTtyMode(-1, NULL)<0) {
    brlapi_perror("enterTtyMode");
    exit(PROG_EXIT_FATAL);
  }
  pthread_create(&thread, NULL, thread_fun, NULL);
  while (!thread_done) {
    char buf[x+1];
    snprintf(buf, sizeof(buf), "counting %d", i);
    buf[x] = 0;
    brlapi_writeText(BRLAPI_CURSOR_OFF, buf);
    asyncWait(1000);
    i++;
  }
  pthread_join(thread, NULL);
}

int
main (int argc, char *argv[]) {
  ProgramExitStatus exitStatus = PROG_EXIT_SUCCESS;
  brlapi_fileDescriptor fd;

  {
    static const OptionsDescriptor descriptor = {
      OPTION_TABLE(programOptions),
      .applicationName = "apitest"
    };
    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  settings.host = opt_host;
  settings.auth = opt_auth;
  fprintf(stderr, "Connecting to BrlAPI... ");
  if ((fd=brlapi_openConnection(&settings, &settings)) != (brlapi_fileDescriptor)(-1)) {
    fprintf(stderr, "done (fd=%"PRIfd")\n", fd);
    fprintf(stderr,"Connected to %s using auth %s\n", settings.host, settings.auth);

    if (opt_showName) {
      showDriverName();
    }

    if (opt_showModelIdentifier) {
      showModelIdentifier();
    }

    if (opt_showSize) {
      showDisplaySize();
    }

    if (opt_showDots) {
      showDots();
    }

    if (opt_learnMode) {
      enterLearnMode();
    }

    if (opt_showKeyCodes) {
      showKeyCodes();
    }

    if (opt_suspendMode) {
      suspendDriver();
    }

    if (opt_threadMode) {
      exerciseThreads();
    }

    brlapi_closeConnection();
    fprintf(stderr, "Disconnected\n");
  } else {
    fprintf(stderr, "failed to connect to %s using auth %s",settings.host, settings.auth);
    brlapi_perror("");
    exitStatus = PROG_EXIT_FATAL;
  }
  return exitStatus;
}

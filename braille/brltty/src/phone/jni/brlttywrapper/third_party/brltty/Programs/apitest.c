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

#include "cmdline.h"
#include "pid.h"
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

static int opt_showName;
static int opt_showModelIdentifier;
static int opt_showSize;

static int opt_showDots;
static int opt_showKeyCodes;
static int opt_learnMode;
static int opt_parameters;

static int opt_suspendMode;
static int opt_threadMode;

BEGIN_OPTION_TABLE(programOptions)
  { .word = "brlapi",
    .letter = 'b',
    .argument = "[host][:port]",
    .setting.string = &opt_host,
    .description = "BrlAPIa host and/or port to connect to."
  },

  { .word = "auth",
    .letter = 'a',
    .argument = "scheme+...",
    .setting.string = &opt_auth,
    .description = "BrlAPI authorization/authentication schemes."
  },

  { .word = "name",
    .letter = 'n',
    .setting.flag = &opt_showName,
    .description = "Show the name of the braille driver."
  },

  { .word = "model",
    .letter = 'm',
    .setting.flag = &opt_showModelIdentifier,
    .description = "Show the model identifier of the braille device."
  },

  { .word = "window",
    .letter = 'w',
    .setting.flag = &opt_showSize,
    .description = "Show the dimensions of the braille window."
  },

  { .word = "dots",
    .letter = 'd',
    .setting.flag = &opt_showDots,
    .description = "Show dot pattern."
  },

  { .word = "keycodes",
    .letter = 'k',
    .setting.flag = &opt_showKeyCodes,
    .description = "Enter interactive keycode learn mode."
  },

  { .word = "learn",
    .letter = 'l',
    .setting.flag = &opt_learnMode,
    .description = "Enter interactive command learn mode."
  },

  { .word = "parameters",
    .letter = 'p',
    .setting.flag = &opt_parameters,
    .description = "Test parameters"
  },

  { .word = "suspend",
    .letter = 's',
    .setting.flag = &opt_suspendMode,
    .description = "Suspend the braille driver (press ^C or send SIGUSR1 to resume)."
  },

  { .word = "thread",
    .letter = 't',
    .setting.flag = &opt_threadMode,
    .description = "Exercise threaded use"
  },
END_OPTION_TABLE(programOptions)

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
#define DOTS_TOTALLEN (DOTS_TEXTLEN + DOTS_LEN)
static void showDots(void)
{
  unsigned int size;

  {
    unsigned int columns, rows;

    if (brlapi_getDisplaySize(&columns, &rows) < 0) {
      brlapi_perror("failed");
      exit(PROG_EXIT_FATAL);
    }

    size = columns * rows;
    unsigned int minimum = DOTS_TOTALLEN;

    if (size < minimum) {
      fprintf(stderr, "can't show dots on a braille display with less than %u cells\n", minimum);
      exit(PROG_EXIT_SEMANTIC);
    }
  }

  if (brlapi_enterTtyMode(-1, NULL) < 0) {
    brlapi_perror("enterTtyMode");
    exit(PROG_EXIT_FATAL);
  }

  {
    fprintf(stderr, "Showing dot patterns\n");

    char text[size + 1];
    memset(text, ' ', size);
    text[size] = 0;
    memcpy(text, DOTS_TEXT, DOTS_TEXTLEN);

    unsigned char or[size];
    memset(or, 0, size);
    or[DOTS_TEXTLEN+0] = BRL_DOT_1;
    or[DOTS_TEXTLEN+1] = BRL_DOT_2;
    or[DOTS_TEXTLEN+2] = BRL_DOT_3;
    or[DOTS_TEXTLEN+3] = BRL_DOT_4;
    or[DOTS_TEXTLEN+4] = BRL_DOT_5;
    or[DOTS_TEXTLEN+5] = BRL_DOT_6;
    or[DOTS_TEXTLEN+6] = BRL_DOT_7;
    or[DOTS_TEXTLEN+7] = BRL_DOT_8;

    brlapi_writeArguments_t wa = BRLAPI_WRITEARGUMENTS_INITIALIZER;
    wa.regionBegin = 1;
    wa.regionSize = size;
    wa.text = text;
    wa.orMask = or;

    if (brlapi_write(&wa) < 0) {
      brlapi_perror("brlapi_write");
      exit(PROG_EXIT_FATAL);
    }
  }

  {
    brlapi_keyCode_t key;
    brlapi_readKey(1, &key);
  }
}

static char *getKeyName (brlapi_keyCode_t key)
{
  return brlapi_getParameterAlloc(BRLAPI_PARAM_KEY_SHORT_NAME, (key & ~BRLAPI_DRV_KEY_PRESS), BRLAPI_PARAMF_GLOBAL, NULL);
}

static void listKeys(void)
{
  size_t length;
  brlapi_param_driverKeycode_t *keys = brlapi_getParameterAlloc(BRLAPI_PARAM_DEVICE_KEY_CODES, 0, BRLAPI_PARAMF_GLOBAL, &length);

  if (keys) {
    length /= sizeof(*keys);
    printf("%zu keys\n", length);

    for (int i=0; i<length; i+=1) {
      printf("key %04"BRLAPI_PRIxKEYCODE":", keys[i]);

      {
        char *name = getKeyName(keys[i]);

        if (name) {
          printf(" name %s", name);
          free(name);
        }
      }

      printf("\n");
    }

    free(keys);
  }
}

static void showKeyCodes(void)
{
  char buf[0X100];

  fprintf(stderr, "Entering keycode learn mode\n");

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

  if (brlapi_writeText(BRLAPI_CURSOR_OFF, "showing key codes")<0) {
    brlapi_perror("brlapi_writeText");
    exit(PROG_EXIT_FATAL);
  }

  int res;
  brlapi_keyCode_t key;

  while ((res = brlapi_readKeyWithTimeout(10000, &key)) > 0) {
    const char *action = (key & BRLAPI_DRV_KEY_PRESS)? "press": "release";
    size_t length = snprintf(buf, sizeof(buf), "%04" BRLAPI_PRIxKEYCODE " (%" BRLAPI_PRIuKEYCODE ") %s", key, key, action);

    {
      char *name = getKeyName(key);

      if (name) {
        snprintf(&buf[length], (sizeof(buf) - length), ": %s", name);
        free(name);
      }
    }

    brlapi_writeText(BRLAPI_CURSOR_OFF, buf);
    fprintf(stderr, "%s\n", buf);
  }

  if (res < 0) brlapi_perror("brlapi_readKey");
}

static void enterLearnMode(void)
{
  int res;
  brlapi_keyCode_t code;
  int cmd;
  char buf[0X100], *val;

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
    val = brlapi_getParameterAlloc(BRLAPI_PARAM_COMMAND_LONG_NAME, cmd, BRLAPI_PARAMF_GLOBAL, NULL);
    fprintf(stderr, "%s\n", val);
    free(val);
    if (cmd==BRL_CMD_LEARN) return;
  }
  brlapi_perror("brlapi_readKey");
}

static void brailleRetainDotsChanged(brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, void *priv, const void *data, size_t len)
{
  const brlapi_param_retainDots_t *d = data;
  if (parameter != BRLAPI_PARAM_RETAIN_DOTS)
  {
    printf("handler called for %x, another parameter than retaindot parameter?!\n", parameter);
    return;
  }
  printf("new retain dots %zd: %d\n", len, *d);
}

static void testParameters(void)
{
  brlapi_param_retainDots_t val;
  if (brlapi_getParameter(BRLAPI_PARAM_RETAIN_DOTS, 0, BRLAPI_PARAMF_LOCAL, &val, sizeof(val)) < 0) {
    brlapi_perror("getParameter");
  }
  printf("retain dots was %d\n", val);
  printf("now watching retain dots parameter\n");
  if (brlapi_watchParameter(BRLAPI_PARAM_RETAIN_DOTS, 0, BRLAPI_PARAMF_LOCAL, brailleRetainDotsChanged, NULL, NULL, 0) == 0) {
    brlapi_perror("watchParameter");
  }
  val = 0;
  printf("setting retain dots parameter to %d\n", val);
  if (brlapi_setParameter(BRLAPI_PARAM_RETAIN_DOTS, 0, BRLAPI_PARAMF_LOCAL, &val, sizeof(val)) < 0) {
    brlapi_perror("setParameter");
  }
  if (brlapi_getParameter(BRLAPI_PARAM_RETAIN_DOTS, 0, BRLAPI_PARAMF_LOCAL, &val, sizeof(val)) < 0) {
    brlapi_perror("getParameter");
  }
  printf("retain dots now %d\n", val);
  val = 1;
  printf("setting retain dots parameter to %d\n", val);
  if (brlapi_setParameter(BRLAPI_PARAM_RETAIN_DOTS, 0, BRLAPI_PARAMF_LOCAL, &val, sizeof(val)) < 0) {
    brlapi_perror("setParameter");
  }
  if (brlapi_getParameter(BRLAPI_PARAM_RETAIN_DOTS, 0, BRLAPI_PARAMF_LOCAL, &val, sizeof(val)) < 0) {
    brlapi_perror("getParameter");
  }
  printf("retain dots now %d\n", val);

  listKeys();
}

#ifdef SIGUSR1
static void emptySignalHandler(int sig) { }
#endif /* SIGUSR1 */

static void suspendDriver(void)
{
  char driver[30];
  fprintf(stderr, "Getting driver name: ");

  if (brlapi_getDriverName(driver, sizeof(driver))<0) {
    brlapi_perror("failed");
    exit(PROG_EXIT_FATAL);
  }
  fprintf(stderr, "%s\n", driver);

  fprintf(stderr, "Suspending driver\n");
  if (brlapi_suspendDriver(driver)) {
    brlapi_perror("suspend");
  } else {
#ifdef SIGUSR1
    signal(SIGUSR1,emptySignalHandler);
#endif /* SIGUSR1 */

    {
      ProcessIdentifier pid = getProcessIdentifier();
      fprintf(stderr, "Waiting (to resume, send SIGUSR1 to process %"PRIpid")\n", pid);
    }

    brlapi_pause(-1);

#ifdef SIGUSR1
    signal(SIGUSR1,SIG_DFL);
#endif /* SIGUSR1 */

    fprintf(stderr, "Resuming driver\n");
    if (brlapi_resumeDriver()) {
      brlapi_perror("resumeDriver");
    }
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
    const CommandLineDescriptor descriptor = {
      .options = &programOptions,
      .applicationName = "apitest",

      .usage = {
        .purpose = strtext("Test BrlAPI functions."),
      }
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

    if (opt_showKeyCodes) {
      showKeyCodes();
    }

    if (opt_learnMode) {
      enterLearnMode();
    }

    if (opt_parameters) {
      testParameters();
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

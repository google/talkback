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

#ifndef BRLTTY_INCLUDED_LOG
#define BRLTTY_INCLUDED_LOG

#include "prologue.h"

#include <stdarg.h>

#ifdef HAVE_SYSLOG_H
#include <syslog.h>
#endif /* HAVE_SYSLOG_H */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern unsigned char systemLogLevel;
extern unsigned char stderrLogLevel;

#ifndef LOG_INFO
typedef enum {
  LOG_EMERG,
  LOG_ALERT,
  LOG_CRIT,
  LOG_ERR,
  LOG_WARNING,
  LOG_NOTICE,
  LOG_INFO,
  LOG_DEBUG
} SyslogLevel;
#endif /* log level definitions */

extern const char *const logLevelNames[];
extern const unsigned int logLevelCount;

#define LOG_LEVEL_WIDTH 8
#define LOG_LEVEL_MASK (LOG_LEVEL_WIDTH - 1)

#define LOG_CATEGORY_INDEX(name) LOG_CTG_##name
#define LOG_CATEGORY(name) ((LOG_CATEGORY_INDEX(name) + 1) << LOG_LEVEL_WIDTH)

typedef enum {
  LOG_CATEGORY_INDEX(INPUT_PACKETS),
  LOG_CATEGORY_INDEX(OUTPUT_PACKETS),

  LOG_CATEGORY_INDEX(BRAILLE_KEYS),
  LOG_CATEGORY_INDEX(KEYBOARD_KEYS),

  LOG_CATEGORY_INDEX(CURSOR_TRACKING),
  LOG_CATEGORY_INDEX(CURSOR_ROUTING),

  LOG_CATEGORY_INDEX(UPDATE_EVENTS),
  LOG_CATEGORY_INDEX(SPEECH_EVENTS),
  LOG_CATEGORY_INDEX(ASYNC_EVENTS),
  LOG_CATEGORY_INDEX(SERVER_EVENTS),

  LOG_CATEGORY_INDEX(GENERIC_IO),
  LOG_CATEGORY_INDEX(SERIAL_IO),
  LOG_CATEGORY_INDEX(USB_IO),
  LOG_CATEGORY_INDEX(BLUETOOTH_IO),
  LOG_CATEGORY_INDEX(HID_IO),

  LOG_CATEGORY_INDEX(BRAILLE_DRIVER),
  LOG_CATEGORY_INDEX(SPEECH_DRIVER),
  LOG_CATEGORY_INDEX(SCREEN_DRIVER),

  LOG_CATEGORY_COUNT /* must be last */
} LogCategoryIndex;

extern const char *getLogCategoryName (LogCategoryIndex index);
extern const char *getLogCategoryTitle (LogCategoryIndex index);

extern void disableAllLogCategories (void);
extern int setLogCategory (const char *name);
extern const char logCategoryName_all[];
extern const char logCategoryPrefix_disable;

extern unsigned char categoryLogLevel;
extern unsigned char logCategoryFlags[LOG_CATEGORY_COUNT];
#define LOG_CATEGORY_FLAG(name) logCategoryFlags[LOG_CATEGORY_INDEX(name)]

extern void openLogFile (const char *path);
extern void closeLogFile (void);

extern void openSystemLog (void);
extern void closeSystemLog (void);

extern int pushLogPrefix (const char *prefix);
extern int popLogPrefix (void);

typedef size_t LogDataFormatter (char *buffer, size_t size, const void *data);
extern int logData (int level, LogDataFormatter *formatLogData, const void *data);

extern int logMessage (int level, const char *format, ...) PRINTF(2, 3);
extern int vlogMessage (int level, const char *format, va_list *arguments);

extern int logBytes (int level, const char *label, const void *data, size_t length, ...) PRINTF(2, 5);
extern int logSymbol (int level, void *address, const char *format, ...) PRINTF(3, 4);

extern int logActionProblem (int level, int error, const char *action);
extern int logActionError (int error, const char *action);

extern int logSystemProblem (int level, const char *action);
extern int logSystemError (const char *action);
extern int logMallocError (void);

extern int logUnsupportedFeature (const char *name);
extern int logUnsupportedOperation (const char *name);
#define logUnsupportedFunction() logUnsupportedOperation(__func__)
extern int logPossibleCause (const char *cause);

#ifdef WINDOWS
extern int logWindowsError (DWORD code, const char *action);
extern int logWindowsSystemError (const char *action);

#ifdef __MINGW32__
extern int logWindowsSocketError (const char *action);
#endif /* __MINGW32__ */
#endif /* WINDOWS */

extern int logBacktrace (void);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_LOG */

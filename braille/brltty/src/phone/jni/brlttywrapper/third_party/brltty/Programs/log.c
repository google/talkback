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
#include <stdarg.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>

#ifdef __ANDROID__
#include <android/log.h>
#endif /* __ANDROID__ */

#include "log.h"
#include "log_history.h"
#include "strfmt.h"
#include "file.h"
#include "timing.h"
#include "addresses.h"
#include "stdiox.h"
#include "thread.h"

const char logCategoryName_all[] = "all";
const char logCategoryPrefix_disable = '-';

const char *const logLevelNames[] = {
  "emergency", "alert", "critical", "error",
  "warning", "notice", "information", "debug"
};
const unsigned int logLevelCount = ARRAY_COUNT(logLevelNames);

unsigned char systemLogLevel = LOG_NOTICE;
unsigned char stderrLogLevel = LOG_NOTICE;

typedef struct {
  const char *name;
  const char *title;
  const char *prefix;
} LogCategoryEntry;

static const LogCategoryEntry logCategoryTable[LOG_CATEGORY_COUNT] = {
  [LOG_CATEGORY_INDEX(GENERIC_INPUT)] = {
    .name = "ingio",
    .title = strtext("Generic Input"),
    .prefix = "generic input"
  },

  [LOG_CATEGORY_INDEX(INPUT_PACKETS)] = {
    .name = "inpkts",
    .title = strtext("Input Packets"),
    .prefix = "input packet"
  },

  [LOG_CATEGORY_INDEX(OUTPUT_PACKETS)] = {
    .name = "outpkts",
    .title = strtext("Output Packets"),
    .prefix = "output packet"
  },

  [LOG_CATEGORY_INDEX(BRAILLE_KEYS)] = {
    .name = "brlkeys",
    .title = strtext("Braille Key Events"),
    .prefix = "braille key"
  },

  [LOG_CATEGORY_INDEX(KEYBOARD_KEYS)] = {
    .name = "kbdkeys",
    .title = strtext("Keyboard Key Events"),
    .prefix = "keyboard key"
  },

  [LOG_CATEGORY_INDEX(CURSOR_TRACKING)] = {
    .name = "csrtrk",
    .title = strtext("Cursor Tracking"),
    .prefix = "cursor tracking"
  },

  [LOG_CATEGORY_INDEX(CURSOR_ROUTING)] = {
    .name = "csrrtg",
    .title = strtext("Cursor Routing"),
    .prefix = "cursor routing"
  },

  [LOG_CATEGORY_INDEX(UPDATE_EVENTS)] = {
    .name = "update",
    .title = strtext("Update Events"),
    .prefix = "update"
  },

  [LOG_CATEGORY_INDEX(SPEECH_EVENTS)] = {
    .name = "speech",
    .title = strtext("Speech Events"),
    .prefix = "speech"
  },

  [LOG_CATEGORY_INDEX(ASYNC_EVENTS)] = {
    .name = "async",
    .title = strtext("Async Events"),
    .prefix = "async"
  },

  [LOG_CATEGORY_INDEX(SERVER_EVENTS)] = {
    .name = "server",
    .title = strtext("Server Events"),
    .prefix = "server"
  },

  [LOG_CATEGORY_INDEX(SERIAL_IO)] = {
    .name = "serial",
    .title = strtext("Serial I/O"),
    .prefix = "serial"
  },

  [LOG_CATEGORY_INDEX(USB_IO)] = {
    .name = "usb",
    .title = strtext("USB I/O"),
    .prefix = "USB"
  },

  [LOG_CATEGORY_INDEX(BLUETOOTH_IO)] = {
    .name = "bluetooth",
    .title = strtext("Bluetooth I/O"),
    .prefix = "Bluetooth"
  },

  [LOG_CATEGORY_INDEX(BRAILLE_DRIVER)] = {
    .name = "brldrv",
    .title = strtext("Braille Driver Events"),
    .prefix = "braille driver"
  },

  [LOG_CATEGORY_INDEX(SPEECH_DRIVER)] = {
    .name = "spkdrv",
    .title = strtext("Speech Driver Events"),
    .prefix = "speech driver"
  },

  [LOG_CATEGORY_INDEX(SCREEN_DRIVER)] = {
    .name = "scrdrv",
    .title = strtext("Screen Driver Events"),
    .prefix = "screen driver"
  },
};

unsigned char categoryLogLevel = LOG_WARNING;
unsigned char logCategoryFlags[LOG_CATEGORY_COUNT];

#if defined(WINDOWS)
static HANDLE windowsEventLog = INVALID_HANDLE_VALUE;

static WORD
toWindowsEventType (int level) {
  if (level <= LOG_ERR) return EVENTLOG_ERROR_TYPE;
  if (level <= LOG_WARNING) return EVENTLOG_WARNING_TYPE;
  return EVENTLOG_INFORMATION_TYPE;
}

#elif defined(__MSDOS__)

#elif defined(__ANDROID__)
static int
toAndroidLogPriority (int level) {
  switch (level) {
    case LOG_EMERG:   return ANDROID_LOG_FATAL;
    case LOG_ALERT:   return ANDROID_LOG_FATAL;
    case LOG_CRIT:    return ANDROID_LOG_FATAL;
    case LOG_ERR:     return ANDROID_LOG_ERROR;
    case LOG_WARNING: return ANDROID_LOG_WARN;
    case LOG_NOTICE:  return ANDROID_LOG_INFO;
    case LOG_INFO:    return ANDROID_LOG_INFO;
    case LOG_DEBUG:   return ANDROID_LOG_DEBUG;
    default:          return ANDROID_LOG_UNKNOWN;
  }
}

#elif defined(HAVE_SYSLOG_H)
static int syslogOpened = 0;
#endif /* system log internal definitions */

static LogEntry *logPrefixStack = NULL;
static FILE *logFile = NULL;

static inline const LogCategoryEntry *
getLogCategoryEntry (LogCategoryIndex index) {
  return (index < LOG_CATEGORY_COUNT)? &logCategoryTable[index]: NULL;
}

const char *
getLogCategoryName (LogCategoryIndex index) {
  const LogCategoryEntry *ctg = getLogCategoryEntry(index);

  return (ctg && ctg->name)? ctg->name: "";
}

const char *
getLogCategoryTitle (LogCategoryIndex index) {
  const LogCategoryEntry *ctg = getLogCategoryEntry(index);

  return (ctg && ctg->title)? ctg->title: "";
}

static inline void
setLogCategoryFlag (const LogCategoryEntry *ctg, unsigned char state) {
  logCategoryFlags[ctg - logCategoryTable] = state;
}

void
disableAllLogCategories (void) {
  const LogCategoryEntry *ctg = logCategoryTable;
  const LogCategoryEntry *end = ctg + LOG_CATEGORY_COUNT;

  while (ctg < end) setLogCategoryFlag(ctg++, 0);
}

int
setLogCategory (const char *name) {
  const LogCategoryEntry *ctg = logCategoryTable;
  const LogCategoryEntry *end = ctg + LOG_CATEGORY_COUNT;

  int on = 1;
  int all;

  if (*name == logCategoryPrefix_disable) {
    on = 0;
    name += 1;
  }

  all = strcasecmp(name, logCategoryName_all) == 0;

  while (ctg < end) {
    if (all || (ctg->name && (strcasecmp(name, ctg->name) == 0))) {
      setLogCategoryFlag(ctg, on);
      if (!all) return 1;
    }

    ctg += 1;
  }

  return all;
}

int
pushLogPrefix (const char *prefix) {
  if (!prefix) prefix = "";
  return pushLogEntry(&logPrefixStack, prefix, 0);
}

int
popLogPrefix (void) {
  return popLogEntry(&logPrefixStack);
}

void
closeLogFile (void) {
  if (logFile) {
    fclose(logFile);
    logFile = NULL;
  }
}

void
openLogFile (const char *path) {
  closeLogFile();
  logFile = fopen(path, "w");
}

static void
writeLogRecord (const char *record) {
  if (logFile) {
    lockStream(logFile);

    {
      TimeValue now;
      char buffer[0X20];
      size_t length;
      unsigned int milliseconds;

      getCurrentTime(&now);
      length = formatSeconds(buffer, sizeof(buffer), "%Y-%m-%d@%H:%M:%S", now.seconds);
      milliseconds = now.nanoseconds / NSECS_PER_MSEC;

      fprintf(logFile, "%.*s.%03u ", (int)length, buffer, milliseconds);
    }

    {
      char name[0X40];
      size_t length = formatThreadName(name, sizeof(name));

      if (length) fprintf(logFile, "[%s] ", name);
    }

    fputs(record, logFile);
    fputc('\n', logFile);
    flushStream(logFile);
    unlockStream(logFile);
  }
}

void
openSystemLog (void) {
#if defined(WINDOWS)
  if (windowsEventLog == INVALID_HANDLE_VALUE) {
    windowsEventLog = RegisterEventSource(NULL, PACKAGE_TARNAME);
  }

#elif defined(__MSDOS__)
  if (!logFile) {
    char *path = makeWritablePath(PACKAGE_TARNAME ".log");

    if (path) {
      openLogFile(path);
      free(path);
    }
  }

#elif defined(__ANDROID__)

#elif defined(HAVE_SYSLOG_H)
  if (!syslogOpened) {
    openlog(PACKAGE_TARNAME, LOG_PID, LOG_DAEMON);
    syslogOpened = 1;
  }
#endif /* open system log */
}

void
closeSystemLog (void) {
#if defined(WINDOWS)
  if (windowsEventLog != INVALID_HANDLE_VALUE) {
    DeregisterEventSource(windowsEventLog);
    windowsEventLog = INVALID_HANDLE_VALUE;
  }

#elif defined(__MSDOS__)
  closeLogFile();

#elif defined(__ANDROID__)

#elif defined(HAVE_SYSLOG_H)
  if (syslogOpened) {
    closelog();
    syslogOpened = 0;
  }
#endif /* close system log */
}

void
logData (int level, LogDataFormatter *formatLogData, const void *data) {
  const char *prefix = NULL;
  int push = 0;

  if (level & LOG_FLG_CATEGORY) {
    int category = level & LOG_MSK_CATEGORY;
    if (!logCategoryFlags[category]) return;
    const LogCategoryEntry *ctg = &logCategoryTable[category];

    prefix = ctg->prefix;
    level = categoryLogLevel;
  } else {
    push = level <= LOG_WARNING;
  }

  {
    int write = level <= systemLogLevel;
    int print = level <= stderrLogLevel;

    if (write || print || push) {
      int oldErrno = errno;

      char record[0X1000];
      STR_BEGIN(record, sizeof(record));
      if (prefix) STR_PRINTF("%s: ", prefix);
      STR_FORMAT(formatLogData, data);
      STR_END;

      if (write) {
        writeLogRecord(record);

#if defined(WINDOWS)
        if (windowsEventLog != INVALID_HANDLE_VALUE) {
          const char *strings[] = {record};

          ReportEvent(
            windowsEventLog, toWindowsEventType(level), 0, 0, NULL,
            ARRAY_COUNT(strings), 0, strings, NULL
          );
        }

#elif defined(__MSDOS__)

#elif defined(__ANDROID__)
        __android_log_write(
          toAndroidLogPriority(level), PACKAGE_TARNAME, record
        );

#elif defined(HAVE_SYSLOG_H)
        if (syslogOpened) syslog(level, "%s", record);
#endif /* write system log */
      }

      if (print) {
        FILE *stream = stderr;
        lockStream(stream);

        if (logPrefixStack) {
          const char *prefix = getLogEntryText(logPrefixStack);

          if (*prefix) {
            fputs(prefix, stream);
            fputs(": ", stream);
          }
        }

        fputs(record, stream);
        fputc('\n', stream);

        flushStream(stream);
        unlockStream(stream);
      }

      if (push) pushLogMessage(record);
      errno = oldErrno;
    }
  }
}

static size_t
formatLogArguments (char *buffer, size_t size, const char *format, va_list *arguments) {
  int length = vsnprintf(buffer, size, format, *arguments);
  if (length < 0) return 0;
  if (length < size) return length;
  return size;
}

typedef struct {
  const char *format;
  va_list *arguments;
} LogMessageData;

static size_t
formatLogMessageData (char *buffer, size_t size, const void *data) {
  const LogMessageData *msg = data;
  return formatLogArguments(buffer, size, msg->format, msg->arguments);
}

void
vlogMessage (int level, const char *format, va_list *arguments) {
  const LogMessageData msg = {
    .format = format,
    .arguments = arguments
  };

  logData(level, formatLogMessageData, &msg);
}

void
logMessage (int level, const char *format, ...) {
  va_list arguments;

  va_start(arguments, format);
  vlogMessage(level, format, &arguments);
  va_end(arguments);
}

typedef struct {
  const char *label;
  va_list *arguments;
  const void *data;
  size_t length;
} LogBytesData;

static
STR_BEGIN_FORMATTER(formatLogBytesData, const void *data)
  const LogBytesData *bytes = data;
  const unsigned char *byte = bytes->data;
  const unsigned char *end = byte + bytes->length;

  if (bytes->label) {
    STR_FORMAT(formatLogArguments, bytes->label, bytes->arguments);
    STR_PRINTF(": ");
  }

  while (byte < end) {
    if (byte != bytes->data) STR_PRINTF(" ");
    STR_PRINTF("%2.2X", *byte++);
  }
STR_END_FORMATTER

void
logBytes (int level, const char *label, const void *data, size_t length, ...) {
  va_list arguments;
  va_start(arguments, length);

  {
    const LogBytesData bytes = {
      .label = label,
      .arguments = &arguments,
      .data = data,
      .length = length
    };

    logData(level, formatLogBytesData, &bytes);
  }

  va_end(arguments);
}

typedef struct {
  void *address;
  const char *format;
  va_list *arguments;
} LogSymbolData;

static
STR_BEGIN_FORMATTER(formatLogSymbolData, const void *data)
  const LogSymbolData *symbol = data;
  ptrdiff_t offset = 0;
  const char *name = getAddressName(symbol->address, &offset);

  STR_FORMAT(formatLogArguments, symbol->format, symbol->arguments);
  STR_PRINTF(": ");

  if (name && *name) {
    STR_PRINTF("%s", name);
    if (offset) STR_PRINTF("+%"PRIXPTR, (uintptr_t)offset);
  } else {
    STR_PRINTF("%p", symbol->address);
  }
STR_END_FORMATTER

void
logSymbol (int level, void *address, const char *format, ...) {
  va_list arguments;
  va_start(arguments, format);

  {
    const LogSymbolData symbol = {
      .address = address,
      .format = format,
      .arguments = &arguments
    };

    logData(level, formatLogSymbolData, &symbol);
  }

  va_end(arguments);
}

void
logActionProblem (int level, int error, const char *action) {
  logMessage(level, "%s error %d: %s", action, error, strerror(error));
}

void
logActionError (int error, const char *action) {
  logActionProblem(LOG_ERR, error, action);
}

void
logSystemProblem (int level, const char *action) {
  logActionProblem(level, errno, action);
}

void
logSystemError (const char *action) {
  logSystemProblem(LOG_ERR, action);
}

void
logMallocError (void) {
  logSystemError("malloc");
}

void
logUnsupportedFeature (const char *name) {
  logMessage(LOG_WARNING, "feature not supported: %s", name);
}

void
logUnsupportedOperation (const char *name) {
  errno = ENOSYS;
  logSystemError(name);
}

void
logPossibleCause (const char *cause) {
  logMessage(LOG_WARNING, "possible cause: %s", cause);
}

#ifdef WINDOWS
void
logWindowsError (DWORD error, const char *action) {
  char *message;
  DWORD count = FormatMessage(FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM,
                              NULL, error,
                              MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
                              (char *)&message, 0, NULL);

  if (count) {
    char *end = strpbrk(message, "\r\n");

    if (end) *end = 0;
  } else {
    message = "unknown";
  }

  logMessage(LOG_ERR, "%s error %d: %s", action, (int)error, message);
  if (count) LocalFree(message);
}

void
logWindowsSystemError (const char *action) {
  DWORD error = GetLastError();

  logWindowsError(error, action);
}

#ifdef __MINGW32__
void
logWindowsSocketError (const char *action) {
  DWORD error = WSAGetLastError();

  logWindowsError(error, action);
}
#endif /* __MINGW32__ */
#endif /* WINDOWS */

static void
logBacktraceString (const char *string) {
  logMessage(LOG_DEBUG, "backtrace: %s", string);
}

#if defined(HAVE_EXECINFO_H)
#include <execinfo.h>

void
logBacktrace (void) {
  const int limit = 30;
  void *frames[limit];
  int count = backtrace(frames, limit);

  if (count > 0) {
    char **strings;

    if ((strings = backtrace_symbols(frames, count))) {
      char **string = strings;
      char **end = string + count;

      while (string < end) {
        logBacktraceString(*string);
        string += 1;
      }

      if (count == limit) {
        logBacktraceString("...");
      }

      free(strings);
    } else {
      logSystemError("backtrace_symbols");
    }
  } else {
    logBacktraceString("no frames");
  }
}

#else /* log backtrace */
void
logBacktrace (void) {
  logBacktraceString("not supported");
}
#endif /* log backtrace */

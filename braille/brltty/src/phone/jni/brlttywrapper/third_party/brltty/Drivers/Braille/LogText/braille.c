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

/* LogText/braille.c - Braille display library
 * For Tactilog's LogText
 * Author: Dave Mielke <dave@mielke.cc>
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>

#include "log.h"
#include "file.h"
#include "device.h"
#include "async_wait.h"
#include "ascii.h"

#define BRL_STATUS_FIELDS sfGeneric
#define BRL_HAVE_STATUS_CELLS
#include "brl_driver.h"
#include "braille.h"
#include "io_serial.h"

static SerialDevice *serialDevice = NULL;

#define screenHeight 25
#define screenWidth 80
typedef unsigned char ScreenImage[screenHeight][screenWidth];
static ScreenImage sourceImage;
static ScreenImage targetImage;

static const char *downloadPath = "logtext-download";

typedef enum {
   DEV_OFFLINE,
   DEV_ONLINE,
   DEV_READY
} DeviceStatus;
static DeviceStatus deviceStatus;

static KeyTableCommandContext currentContext;
static unsigned char currentLine;
static unsigned char cursorRow;
static unsigned char cursorColumn;

#ifndef __MINGW32__
static int
makeFifo (const char *path, mode_t mode) {
   struct stat status;
   if (lstat(path, &status) != -1) {
      if (S_ISFIFO(status.st_mode)) return 1;
      logMessage(LOG_ERR, "Download object not a FIFO: %s", path);
   } else if (errno == ENOENT) {
      lockUmask();
      mode_t mask = umask(0);
      int result = mkfifo(path, mode);
      int error = errno;
      umask(mask);
      unlockUmask();

      if (result != -1) return 1;
      errno = error;
      logSystemError("Download FIFO creation");
   }
   return 0;
}
#endif /* __MINGW32__ */

static int
makeDownloadFifo (void) {
#ifdef __MINGW32__
   return 0;
#else /* __MINGW32__ */
   return makeFifo(downloadPath, S_IRUSR|S_IWUSR|S_IWGRP|S_IWOTH);
#endif /* __MINGW32__ */
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
   {
      static TranslationTable outputTable = {
#include "brl-out.h"
      };

      setOutputTable(outputTable);
      makeInputTable();

      {
         const unsigned char byte = 0XFF;

         if (memchr(outputTable, byte, sizeof(outputTable))) {
            outputTable[translateInputCell(byte)] = ASCII_SUB;
         }
      }
   }

   if (!isSerialDeviceIdentifier(&device)) {
      unsupportedDeviceIdentifier(device);
      return 0;
   }

   makeDownloadFifo();
   if ((serialDevice = serialOpenDevice(device))) {
      if (serialRestartDevice(serialDevice, 9600)) {
         brl->textRows = screenHeight;
         brl->textColumns = screenWidth;
         brl->buffer = &sourceImage[0][0];
         memset(sourceImage, 0, sizeof(sourceImage));
         deviceStatus = DEV_ONLINE;
         return 1;
      }
      serialCloseDevice(serialDevice);
      serialDevice = NULL;
   }
   return 0;
}

static void
brl_destruct (BrailleDisplay *brl) {
   serialCloseDevice(serialDevice);
   serialDevice = NULL;
}

static int
checkData (const unsigned char *data, unsigned int length) {
   if ((length < 5) || (length != (data[4] + 5))) {
      logMessage(LOG_ERR, "Bad length: %d", length);
   } else if (data[0] != 255) {
      logMessage(LOG_ERR, "Bad header: %d", data[0]);
   } else if ((data[1] < 1) || (data[1] > screenHeight)) {
      logMessage(LOG_ERR, "Bad line: %d", data[1]);
   } else if (data[2] > screenWidth) {
      logMessage(LOG_ERR, "Bad cursor: %d", data[2]);
   } else if ((data[3] < 1) || (data[3] > screenWidth)) {
      logMessage(LOG_ERR, "Bad column: %d", data[3]);
   } else if (data[4] > (screenWidth - (data[3] - 1))) {
      logMessage(LOG_ERR, "Bad count: %d", data[4]);
   } else {
      return 1;
   }
   return 0;
}

static int
sendBytes (const unsigned char *bytes, size_t count) {
   if (serialWriteData(serialDevice, bytes, count) == -1) {
      logSystemError("LogText write");
      return 0;
   }
   return 1;
}

static int
sendData (unsigned char line, unsigned char column, unsigned char count) {
   unsigned char data[5 + count];
   unsigned char *target = data;
   unsigned char *source = &targetImage[line][column];
   *target++ = 0XFF;
   *target++ = line + 1;
   *target++ = (line == cursorRow)? cursorColumn+1: 0;
   *target++ = column + 1;
   *target++ = count;
   logBytes(LOG_DEBUG, "Output dots", source, count);
   target = translateOutputCells(target, source, count);
   count = target - data;
   logBytes(LOG_DEBUG, "LogText write", data, count);
   if (checkData(data, count)) {
      if (sendBytes(data, count)) {
         return 1;
      }
   }
   return 0;
}

static int
sendLine (unsigned char line, int force) {
   unsigned char *source = &sourceImage[line][0];
   unsigned char *target = &targetImage[line][0];
   unsigned char start = 0;
   unsigned char count = screenWidth;
   while (count > 0) {
      if (source[count-1] != target[count-1]) break;
      --count;
   }
   while (start < count) {
      if (source[start] != target[start]) break;
      ++start;
   }
   if ((count -= start) || force) {
      logMessage(LOG_DEBUG, "LogText line: line=%d, column=%d, count=%d", line, start, count);
      memcpy(&target[start], &source[start], count);
      if (!sendData(line, start, count)) {
         return 0;
      }
   }
   return 1;
}

static int
sendCurrentLine (void) {
   return sendLine(currentLine, 0);
}

static int
sendCursorRow (void) {
   return sendLine(cursorRow, 1);
}

static int
handleUpdate (unsigned char line) {
   logMessage(LOG_DEBUG, "Request line: (0X%2.2X) 0X%2.2X dec=%d", KEY_UPDATE, line, line);
   if (!line) return sendCursorRow();
   if (line <= screenHeight) {
      currentLine = line - 1;
      return sendCurrentLine();
   }
   logMessage(LOG_WARNING, "Invalid line request: %d", line);
   return 1;
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
   if (deviceStatus == DEV_READY) {
      sendCurrentLine();
   }
   return 1;
}

static int
isOnline (void) {
   int online = serialTestLineDSR(serialDevice);
   if (online) {
      if (deviceStatus < DEV_ONLINE) {
         deviceStatus = DEV_ONLINE;
         logMessage(LOG_WARNING, "LogText online.");
      }
   } else {
      if (deviceStatus > DEV_OFFLINE) {
         deviceStatus = DEV_OFFLINE;
         logMessage(LOG_WARNING, "LogText offline.");
      }
   }
   return online;
}

static int
brl_writeStatus (BrailleDisplay *brl, const unsigned char *status) {
   if (isOnline()) {
      if (status[GSC_FIRST] == GSC_MARKER) {
         unsigned char row = status[gscScreenCursorRow];
         unsigned char column = status[gscScreenCursorColumn];
         row = MAX(1, MIN(row, screenHeight)) - 1;
         column = MAX(1, MIN(column, screenWidth)) - 1;
         if (deviceStatus < DEV_READY) {
            memset(targetImage, 0, sizeof(targetImage));
            currentContext = KTB_CTX_DEFAULT;
            currentLine = row;
            cursorRow = screenHeight;
            cursorColumn = screenWidth;
            deviceStatus = DEV_READY;
         }
         if ((row != cursorRow) || (column != cursorColumn)) {
            logMessage(LOG_DEBUG, "cursor moved: [%d,%d] -> [%d,%d]", cursorColumn, cursorRow, column, row);
            cursorRow = row;
            cursorColumn = column;
            sendCursorRow();
         }
      }
   }
   return 1;
}

static int
readKey (void) {
   unsigned char key;
   unsigned char arg;
   if (serialReadData(serialDevice, &key, 1, 0, 0) != 1) return EOF;
   switch (key) {
      default:
         arg = 0;
         break;
      case KEY_FUNCTION:
      case KEY_FUNCTION2:
      case KEY_UPDATE:
         while (serialReadData(serialDevice, &arg, 1, 0, 0) != 1) asyncWait(1);
         break;
   }
   {
      int result = COMPOUND_KEY(key, arg);
      logMessage(LOG_DEBUG, "Key read: %4.4X", result);
      return result;
   }
}

/*askUser
static unsigned char *selectedLine;

static void
replaceCharacters (const unsigned char *address, size_t count) {
   translateInputCells(&selectedLine[cursorColumn], address, count);
   cursorColumn += count;
}

static void
insertCharacters (const unsigned char *address, size_t count) {
   memmove(&selectedLine[cursorColumn+count], &selectedLine[cursorColumn], screenWidth-cursorColumn-count);
   replaceCharacters(address, count);
}

static void
deleteCharacters (size_t count) {
   memmove(&selectedLine[cursorColumn], &selectedLine[cursorColumn+count], screenWidth-cursorColumn-count);
   memset(&selectedLine[screenWidth-count], translateInputCell(' '), count);
}

static void
clearCharacters (void) {
   cursorColumn = 0;
   deleteCharacters(screenWidth);
}

static void
selectLine (unsigned char line) {
   selectedLine = &sourceImage[cursorRow = line][0];
   clearCharacters();
   deviceStatus = DEV_ONLINE;
}

static unsigned char *
askUser (const unsigned char *prompt) {
   unsigned char from;
   unsigned char to;
   selectLine(screenHeight-1);
   logMessage(LOG_DEBUG, "Prompt: %s", prompt);
   replaceCharacters(prompt, strlen(prompt));
   from = to = ++cursorColumn;
   sendCursorRow();
   while (1) {
      int key = readKey();
      if (key == EOF) {
         asyncWait(1);
         continue;
      }
      if ((key & KEY_MASK) == KEY_UPDATE) {
         handleUpdate(key >> KEY_SHIFT);
         continue;
      }
      if (isgraph(key)) {
         if (to < screenWidth) {
            unsigned char character = key & KEY_MASK;
            insertCharacters(&character, 1);
            ++to;
         } else {
            ringConsoleBell();
         }
      } else {
         switch (key) {
            case CR:
               if (to > from) {
                  size_t length = to - from;
                  unsigned char *response = malloc(length+1);
                  if (response) {
                     translateOutputCells(response, &selectedLine[from], length);
                     response[length] = 0;
                     logMessage(LOG_DEBUG, "Response: %s", response);
                     return response;
                  } else {
                     logSystemError("Download file path allocation");
                  }
               }
               return NULL;
            case BS:
               if (cursorColumn > from) {
                  --cursorColumn;
                  deleteCharacters(1);
                  --to;
               } else {
                  ringConsoleBell();
               }
               break;
            case DEL:
               if (cursorColumn < to) {
                  deleteCharacters(1);
                  --to;
               } else {
                  ringConsoleBell();
               }
               break;
            case KEY_FUNCTION_CURSOR_LEFT:
               if (cursorColumn > from) {
                  --cursorColumn;
               } else {
                  ringConsoleBell();
               }
               break;
            case KEY_FUNCTION_CURSOR_LEFT_JUMP:
               if (cursorColumn > from) {
                  cursorColumn = from;
               } else {
                  ringConsoleBell();
               }
               break;
            case KEY_FUNCTION_CURSOR_RIGHT:
               if (cursorColumn < to) {
                  ++cursorColumn;
               } else {
                  ringConsoleBell();
               }
               break;
            case KEY_FUNCTION_CURSOR_RIGHT_JUMP:
               if (cursorColumn < to) {
                  cursorColumn = to;
               } else {
                  ringConsoleBell();
               }
               break;
            default:
               ringConsoleBell();
               break;
         }
      }
      sendCursorRow();
   }
}
*/

static void
downloadFile (void) {
   if (makeDownloadFifo()) {
      int file = open(downloadPath, O_RDONLY);
      if (file != -1) {
         struct stat status;
         if (fstat(file, &status) != -1) {
            unsigned char buffer[0X400];
            const unsigned char *address = buffer;
            int count = 0;
            while (1) {
               const unsigned char *newline;
               if (!count) {
                  count = read(file, buffer, sizeof(buffer));
                  if (!count) {
                     static const unsigned char fileTrailer[] = {0X1A};
                     sendBytes(fileTrailer, sizeof(fileTrailer));
                     break;
                  }
                  if (count == -1) {
                     logSystemError("Download file read");
                     break;
                  }
                  address = buffer;
               }
               if ((newline = memchr(address, '\n', count))) {
                  static const unsigned char lineTrailer[] = {ASCII_CR, ASCII_LF};
                  size_t length = newline - address;
                  if (!sendBytes(address, length++)) break;
                  if (!sendBytes(lineTrailer, sizeof(lineTrailer))) break;
                  address += length;
                  count -= length;
               } else {
                  if (!sendBytes(address, count)) break;
                  count = 0;
               }
            }
         } else {
            logSystemError("Download file status");
         }
         if (close(file) == -1) {
            logSystemError("Download file close");
         }
      } else {
         logSystemError("Download file open");
      }
   } else {
      logMessage(LOG_WARNING, "Download path not specified.");
   }
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
   int key = readKey();
   if (context != currentContext) {
      logMessage(LOG_DEBUG, "Context switch: %d -> %d", currentContext, context);
      switch (currentContext = context) {
         case KTB_CTX_DEFAULT:
            deviceStatus = DEV_ONLINE;
            break;
         default:
            break;
      }
   }
   if (key != EOF) {
      switch (key) {
         case KEY_FUNCTION_ENTER:
            return BRL_CMD_KEY(ENTER);
         case KEY_FUNCTION_TAB:
            return BRL_CMD_KEY(TAB);
         case KEY_FUNCTION_CURSOR_UP:
            return BRL_CMD_KEY(CURSOR_UP);
         case KEY_FUNCTION_CURSOR_DOWN:
            return BRL_CMD_KEY(CURSOR_DOWN);
         case KEY_FUNCTION_CURSOR_LEFT:
            return BRL_CMD_KEY(CURSOR_LEFT);
         case KEY_FUNCTION_CURSOR_RIGHT:
            return BRL_CMD_KEY(CURSOR_RIGHT);
         case KEY_FUNCTION_CURSOR_UP_JUMP:
            return BRL_CMD_KEY(HOME);
         case KEY_FUNCTION_CURSOR_DOWN_JUMP:
            return BRL_CMD_KEY(END);
         case KEY_FUNCTION_CURSOR_LEFT_JUMP:
            return BRL_CMD_KEY(PAGE_UP);
         case KEY_FUNCTION_CURSOR_RIGHT_JUMP:
            return BRL_CMD_KEY(PAGE_DOWN);
         case KEY_FUNCTION_F1:
            return BRL_CMD_KFN(1);
         case KEY_FUNCTION_F2:
            return BRL_CMD_KFN(2);
         case KEY_FUNCTION_F3:
            return BRL_CMD_KFN(3);
         case KEY_FUNCTION_F4:
            return BRL_CMD_KFN(4);
         case KEY_FUNCTION_F5:
            return BRL_CMD_KFN(5);
         case KEY_FUNCTION_F6:
            return BRL_CMD_KFN(6);
         case KEY_FUNCTION_F7:
            return BRL_CMD_KFN(7);
         case KEY_FUNCTION_F9:
            return BRL_CMD_KFN(9);
         case KEY_FUNCTION_F10:
            return BRL_CMD_KFN(10);
         case KEY_COMMAND: {
            int command;
            while ((command = readKey()) == EOF) asyncWait(1);
            logMessage(LOG_DEBUG, "Received command: (0x%2.2X) 0x%4.4X", KEY_COMMAND, command);
            switch (command) {
               case KEY_COMMAND:
                  /* pressing the escape command twice will pass it through */
                  return BRL_CMD_BLK(PASSDOTS) + translateInputCell(KEY_COMMAND);
               case KEY_COMMAND_SWITCHVT_PREV:
                  return BRL_CMD_SWITCHVT_PREV;
               case KEY_COMMAND_SWITCHVT_NEXT:
                  return BRL_CMD_SWITCHVT_NEXT;
               case KEY_COMMAND_SWITCHVT_1:
                  return BRL_CMD_BLK(SWITCHVT) + 0;
               case KEY_COMMAND_SWITCHVT_2:
                  return BRL_CMD_BLK(SWITCHVT) + 1;
               case KEY_COMMAND_SWITCHVT_3:
                  return BRL_CMD_BLK(SWITCHVT) + 2;
               case KEY_COMMAND_SWITCHVT_4:
                  return BRL_CMD_BLK(SWITCHVT) + 3;
               case KEY_COMMAND_SWITCHVT_5:
                  return BRL_CMD_BLK(SWITCHVT) + 4;
               case KEY_COMMAND_SWITCHVT_6:
                  return BRL_CMD_BLK(SWITCHVT) + 5;
               case KEY_COMMAND_SWITCHVT_7:
                  return BRL_CMD_BLK(SWITCHVT) + 6;
               case KEY_COMMAND_SWITCHVT_8:
                  return BRL_CMD_BLK(SWITCHVT) + 7;
               case KEY_COMMAND_SWITCHVT_9:
                  return BRL_CMD_BLK(SWITCHVT) + 8;
               case KEY_COMMAND_SWITCHVT_10:
                  return BRL_CMD_BLK(SWITCHVT) + 9;
               case KEY_COMMAND_PAGE_UP:
                  return BRL_CMD_KEY(PAGE_UP);
               case KEY_COMMAND_PAGE_DOWN:
                  return BRL_CMD_KEY(PAGE_DOWN);
               case KEY_COMMAND_PREFMENU:
                  currentLine = 0;
                  cursorRow = 0;
                  cursorColumn = 31;
                  sendCursorRow();
                  return BRL_CMD_PREFMENU;
               case KEY_COMMAND_PREFSAVE:
                  return BRL_CMD_PREFSAVE;
               case KEY_COMMAND_PREFLOAD:
                  return BRL_CMD_PREFLOAD;
               case KEY_COMMAND_FREEZE_ON:
                  return BRL_CMD_FREEZE | BRL_FLG_TOGGLE_ON;
               case KEY_COMMAND_FREEZE_OFF:
                  return BRL_CMD_FREEZE | BRL_FLG_TOGGLE_OFF;
               case KEY_COMMAND_RESTARTBRL:
                  return BRL_CMD_RESTARTBRL;
               case KEY_COMMAND_DOWNLOAD:
                  downloadFile();
                  break;
               default:
                  logMessage(LOG_WARNING, "Unknown command: (0X%2.2X) 0X%4.4X", KEY_COMMAND, command);
                  break;
            }
            break;
         }
         default:
            switch (key & KEY_MASK) {
               case KEY_UPDATE:
                  handleUpdate(key >> KEY_SHIFT);
                  break;
               case KEY_FUNCTION:
                  logMessage(LOG_WARNING, "Unknown function: (0X%2.2X) 0X%4.4X", KEY_COMMAND, key>>KEY_SHIFT);
                  break;
               default: {
                  unsigned char dots = translateInputCell(key);
                  logMessage(LOG_DEBUG, "Received character: 0X%2.2X dec=%d dots=%2.2X", key, key, dots);
                  return BRL_CMD_BLK(PASSDOTS) + dots;
               }
            }
            break;
      }
   }
   return EOF;
}

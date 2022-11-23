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

#ifndef BRLTTY_INCLUDED_BRL_TYPES
#define BRLTTY_INCLUDED_BRL_TYPES

#include "driver.h"
#include "ktb_types.h"
#include "api_types.h"
#include "gio_types.h"
#include "queue.h"
#include "async.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define BRL_NO_CURSOR -1

typedef enum {
  BRL_FIRMNESS_MINIMUM,
  BRL_FIRMNESS_LOW,
  BRL_FIRMNESS_MEDIUM,
  BRL_FIRMNESS_HIGH,
  BRL_FIRMNESS_MAXIMUM
} BrailleFirmness;

typedef enum {
  BRL_SENSITIVITY_MINIMUM,
  BRL_SENSITIVITY_LOW,
  BRL_SENSITIVITY_MEDIUM,
  BRL_SENSITIVITY_HIGH,
  BRL_SENSITIVITY_MAXIMUM
} TouchSensitivity;

typedef enum {
  BRL_INPUT_TEXT,
  BRL_INPUT_DOTS
} BrailleInputMode;

typedef enum {
  BRL_ORIENTATION_NORMAL,
  BRL_ORIENTATION_ROTATED
} BrailleDisplayOrientation;

typedef struct BrailleDisplayStruct BrailleDisplay;
typedef struct BrailleDataStruct BrailleData;

typedef int SetBrailleFirmnessMethod (BrailleDisplay *brl, BrailleFirmness setting);
typedef int SetTouchSensitivityMethod (BrailleDisplay *brl, TouchSensitivity setting);
typedef int SetBrailleAutorepeatMethod (BrailleDisplay *brl, int on, int delay, int interval);

typedef void SetRotateInputMethod (BrailleDisplay *brl, KeyGroup *group, KeyNumber *number);

struct BrailleDisplayStruct {
  BrailleData *data;

  SetBrailleFirmnessMethod *setFirmness;
  SetTouchSensitivityMethod *setSensitivity;
  SetBrailleAutorepeatMethod *setAutorepeat;

  unsigned int textColumns;
  unsigned int textRows;
  unsigned int statusColumns;
  unsigned int statusRows;
  unsigned hideCursor:1;

  const char *keyBindings;
  KEY_NAME_TABLES_REFERENCE keyNames;
  KeyTable *keyTable;

  GioEndpoint *gioEndpoint;
  unsigned int writeDelay;

  unsigned char *buffer;
  unsigned isCoreBuffer:1;

  void (*bufferResized) (unsigned int rows, unsigned int columns);
  unsigned resizeRequired:1;

  int cursor;

  unsigned noDisplay:1;
  unsigned hasFailed:1;
  unsigned isOffline:1;
  unsigned isSuspended:1;

  SetRotateInputMethod *rotateInput;
  const ApiMethods *api;

  struct {
    Queue *messages;
    AsyncHandle alarm;

    struct {
      int timeout;
      unsigned int count;
      unsigned int limit;
    } missing;
  } acknowledgements;
};

typedef struct {
  DRIVER_DEFINITION_DECLARATION;

  const char *const *parameters;
  const unsigned char *statusFields;

  int (*construct) (BrailleDisplay *brl, char **parameters, const char *device);
  void (*destruct) (BrailleDisplay *brl);

  int (*readCommand) (BrailleDisplay *brl, KeyTableCommandContext context);
  int (*writeWindow) (BrailleDisplay *brl, const wchar_t *characters);
  int (*writeStatus) (BrailleDisplay *brl, const unsigned char *cells);

  ssize_t (*readPacket) (BrailleDisplay *brl, void *buffer, size_t size);
  ssize_t (*writePacket) (BrailleDisplay *brl, const void *packet, size_t size);
  int (*reset) (BrailleDisplay *brl);
  
  int (*readKey) (BrailleDisplay *brl);
  int (*keyToCommand) (BrailleDisplay *brl, KeyTableCommandContext context, int key);
} BrailleDriver;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_BRL_TYPES */

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
 * Web Page: http://mielke.cc/brltty/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

#include "prologue.h"

#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>

#include "log.h"
#include "async_handle.h"
#include "async_io.h"

#include "brl_driver.h"
#include "brldefs-bg.h"

#define KEYBOARD_DEVICE_NAME "cp430_keypad"
#define BRAILLE_DEVICE_PATH "/dev/braille0"
#define TEXT_CELL_COUNT 20

BEGIN_KEY_NAME_TABLE(navigation)
  KEY_NAME_ENTRY(BG_NAV_Dot1, "Dot1"),
  KEY_NAME_ENTRY(BG_NAV_Dot2, "Dot2"),
  KEY_NAME_ENTRY(BG_NAV_Dot3, "Dot3"),
  KEY_NAME_ENTRY(BG_NAV_Dot4, "Dot4"),
  KEY_NAME_ENTRY(BG_NAV_Dot5, "Dot5"),
  KEY_NAME_ENTRY(BG_NAV_Dot6, "Dot6"),
  KEY_NAME_ENTRY(BG_NAV_Dot7, "Dot7"),
  KEY_NAME_ENTRY(BG_NAV_Dot8, "Dot8"),

  KEY_NAME_ENTRY(BG_NAV_Space, "Space"),
  KEY_NAME_ENTRY(BG_NAV_Backward, "Backward"),
  KEY_NAME_ENTRY(BG_NAV_Forward, "Forward"),

  KEY_NAME_ENTRY(BG_NAV_Center, "Center"),
  KEY_NAME_ENTRY(BG_NAV_Left, "Left"),
  KEY_NAME_ENTRY(BG_NAV_Right, "Right"),
  KEY_NAME_ENTRY(BG_NAV_Up, "Up"),
  KEY_NAME_ENTRY(BG_NAV_Down, "Down"),

  KEY_NAME_ENTRY(BG_NAV_Louder, "Louder"),
  KEY_NAME_ENTRY(BG_NAV_Softer, "Softer"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(routing)
  KEY_GROUP_ENTRY(BG_GRP_RoutingKeys, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(all)
  KEY_NAME_TABLE(navigation),
  KEY_NAME_TABLE(routing),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(all)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(all),
END_KEY_TABLE_LIST

struct BrailleDataStruct {
  struct {
    int fileDescriptor;
    AsyncHandle inputHandler;
  } keyboard;

  struct {
    int fileDescriptor;
  } braille;

  struct {
    unsigned char rewrite;
    unsigned char cells[TEXT_CELL_COUNT];
  } text;
};

#ifdef HAVE_LINUX_INPUT_H
#include <linux/input.h>

#ifndef KEY_BRL_DOT9
#define KEY_BRL_DOT9 0X1F9
#endif /* KEY_BRL_DOT9 */

#include <dirent.h>
#include <sys/ioctl.h>
#include "metec_flat20_ioctl.h"

static int
handleKeyEvent (BrailleDisplay *brl, int code, int press) {
  KeyNumber number;

  switch(code) {
#define NAV(CODE,KEY) case KEY_##CODE: number = BG_NAV_##KEY; break;
    NAV(UP, Up)
    NAV(LEFT, Left)
    NAV(RIGHT, Right)
    NAV(DOWN, Down)
    NAV(OK, Center)

    NAV(NEXT, Forward)
    NAV(PREVIOUS, Backward)

    NAV(VOLUMEUP, Louder)
    NAV(VOLUMEDOWN, Softer)

    NAV(BRL_DOT1, Dot7)
    NAV(BRL_DOT2, Dot3)
    NAV(BRL_DOT3, Dot2)
    NAV(BRL_DOT4, Dot1)
    NAV(BRL_DOT5, Dot4)
    NAV(BRL_DOT6, Dot5)
    NAV(BRL_DOT7, Dot6)
    NAV(BRL_DOT8, Dot8)
    NAV(BRL_DOT9, Space)
#undef NAV

    default:
      {
        int key = code - 0X2D0;

        if ((key >= 0) && (key < TEXT_CELL_COUNT)) {
          return enqueueKeyEvent(brl, BG_GRP_RoutingKeys, key, press);
        }
      }

      return 0;
  }

  return enqueueKeyEvent(brl, BG_GRP_NavigationKeys, number, press);
}

ASYNC_INPUT_CALLBACK(handleKeyboardEvent) {
  BrailleDisplay *brl = parameters->data;
  static const char label[] = "keyboard";

  if (parameters->error) {
    logMessage(LOG_DEBUG, "%s read error: fd=%d: %s",
               label, brl->data->keyboard.fileDescriptor, strerror(parameters->error));
  } else if (parameters->end) {
    logMessage(LOG_DEBUG, "%s end-of-file: fd=%d", 
               label, brl->data->keyboard.fileDescriptor);
  } else {
    const struct input_event *event = parameters->buffer;

    if (parameters->length >= sizeof(*event)) {
      logInputPacket(event, sizeof(*event));

      switch (event->type) {
        case EV_KEY: {
          int release = event->value == 0;
          int press   = event->value == 1;

          if (release || press) handleKeyEvent(brl, event->code, press);
          break;
        }

        default:
          break;
      }

      return sizeof(*event);
    }
  }

  return 0;
}

static char *
findEventDevice (const char *deviceName) {
  char *devicePath = NULL;
  char directoryPath[0X80];
  DIR *directory;

  snprintf(directoryPath, sizeof(directoryPath),
           "/sys/bus/platform/devices/%s/input", deviceName);

  if ((directory = opendir(directoryPath))) {
    struct dirent *entry;

    while ((entry = readdir(directory))) {
      unsigned int eventNumber;
      char extra;

      if (sscanf(entry->d_name, "input%u%c", &eventNumber, &extra) == 1) {
        char path[0X80];

        snprintf(path, sizeof(path), "/dev/input/event%u", eventNumber);
        if (!(devicePath = strdup(path))) logMallocError();
        break;
      }
    }

    closedir(directory);
  } else {
    logMessage(LOG_ERR, "event device input directory open error: %s: %s",
               directoryPath, strerror(errno));
  }

  return devicePath;
}

static int
openEventDevice (const char *deviceName) {
  char *devicePath = findEventDevice(deviceName);

  if (devicePath) {
    int deviceDescriptor = open(devicePath, O_RDONLY);

    if (deviceDescriptor != -1) {
      if (ioctl(deviceDescriptor, EVIOCGRAB, 1) != -1) {
        logMessage(LOG_INFO, "Event Device Opened: %s: %s: fd=%d",
                   deviceName, devicePath, deviceDescriptor);

        free(devicePath);
        return deviceDescriptor;
      } else {
        logSystemError("ioctl[EVIOCGRAB]");
      }

      close(deviceDescriptor);
    } else {
      logMessage(LOG_ERR, "event device open error: %s: %s",
                 devicePath, strerror(errno));
    }

    free(devicePath);
  }

  return -1;
}
#endif /* HAVE_LINUX_INPUT_H */

static int
openKeyboardDevice (BrailleDisplay *brl) {
#ifdef HAVE_LINUX_INPUT_H
  if ((brl->data->keyboard.fileDescriptor = openEventDevice(KEYBOARD_DEVICE_NAME)) != -1) {
    if (asyncReadFile(&brl->data->keyboard.inputHandler,
                      brl->data->keyboard.fileDescriptor,
                      sizeof(struct input_event),
                      handleKeyboardEvent, brl)) {
      return 1;
    }

    close(brl->data->keyboard.fileDescriptor);
    brl->data->keyboard.fileDescriptor = -1;
  } else {
    logSystemError("open[keyboard]");
  }
#endif /* HAVE_LINUX_INPUT_H */

  return 0;
}

static void
closeKeyboardDevice (BrailleDisplay *brl) {
  if (brl->data->keyboard.inputHandler) {
    asyncCancelRequest(brl->data->keyboard.inputHandler);
    brl->data->keyboard.inputHandler = NULL;
  }

  if (brl->data->keyboard.fileDescriptor != -1) {
    close(brl->data->keyboard.fileDescriptor);
    brl->data->keyboard.fileDescriptor = -1;
  }
}

static int
openBrailleDevice (BrailleDisplay *brl) {
  if ((brl->data->braille.fileDescriptor = open(BRAILLE_DEVICE_PATH, O_WRONLY)) != -1) {
    return 1;
  } else {
    logSystemError("open[braille]");
  }

  return 0;
}

static void
closeBrailleDevice (BrailleDisplay *brl) {
  if (brl->data->braille.fileDescriptor != -1) {
    close(brl->data->braille.fileDescriptor);
    brl->data->braille.fileDescriptor = -1;
  }
}

static int
writeBrailleCells (BrailleDisplay *brl, const unsigned char *cells, size_t count) {
  logOutputPacket(cells, count);
  if (write(brl->data->braille.fileDescriptor, cells, count) != -1) return 1;

  logSystemError("write[braille]");
  return 0;
}

static int
connectResource (BrailleDisplay *brl, const char *identifier) {
  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);

  if (connectBrailleResource(brl, "null:", &descriptor, NULL)) {
    return 1;
  }

  return 0;
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));
    brl->data->keyboard.fileDescriptor = -1;
    brl->data->keyboard.inputHandler = NULL;
    brl->data->braille.fileDescriptor = -1;

    if (connectResource(brl, device)) {
      if (openBrailleDevice(brl)) {
        if (openKeyboardDevice(brl)) {
          brl->textColumns = TEXT_CELL_COUNT;

          setBrailleKeyTable(brl, &KEY_TABLE_DEFINITION(all));
          makeOutputTable(dotsTable_ISO11548_1);
          brl->data->text.rewrite = 1;

          return 1;
        }

        closeBrailleDevice(brl);
      }

      disconnectBrailleResource(brl, NULL);
    }

    free(brl->data);
  } else {
    logMallocError();
  }

  return 0;
}

static void
brl_destruct (BrailleDisplay *brl) {
  disconnectBrailleResource(brl, NULL);

  if (brl->data) {
    closeKeyboardDevice(brl);
    closeBrailleDevice(brl);

    free(brl->data);
    brl->data = NULL;
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  if (cellsHaveChanged(brl->data->text.cells, brl->buffer, brl->textColumns, NULL, NULL, &brl->data->text.rewrite)) {
    unsigned char cells[brl->textColumns];

    translateOutputCells(cells, brl->data->text.cells, brl->textColumns);
    if (!writeBrailleCells(brl, cells, sizeof(cells))) return 0;
  }

  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  return EOF;
}

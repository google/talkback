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

#include "log.h"
#include "parse.h"
#include "bitmask.h"
#include "kbd.h"
#include "kbd_internal.h"

const KeyboardProperties anyKeyboard = {
  .type = KBD_TYPE_ANY,
  .vendor = 0,
  .product = 0
};

int
parseKeyboardProperties (KeyboardProperties *properties, const char *string) {
  enum {
    KBD_PARM_TYPE,
    KBD_PARM_VENDOR,
    KBD_PARM_PRODUCT
  };

  static const char *const names[] = {"type", "vendor", "product", NULL};
  char **parameters = getParameters(names, NULL, string);
  int ok = 1;

  logParameters(names, parameters, "Keyboard Property");
  *properties = anyKeyboard;

  if (*parameters[KBD_PARM_TYPE]) {
    static const KeyboardType types[] = {
      KBD_TYPE_ANY, KBD_TYPE_PS2, KBD_TYPE_USB, KBD_TYPE_BLUETOOTH, KBD_TYPE_INTERNAL
    };

    static const char *choices[] = {"any", "ps2", "usb", "bluetooth", "internal", NULL};
    unsigned int choice;

    if (validateChoice(&choice, parameters[KBD_PARM_TYPE], choices)) {
      properties->type = types[choice];
    } else {
      logMessage(LOG_WARNING, "invalid keyboard type: %s", parameters[KBD_PARM_TYPE]);
      ok = 0;
    }
  }

  if (*parameters[KBD_PARM_VENDOR]) {
    static const int minimum = 0;
    static const int maximum = 0XFFFF;
    int value;

    if (validateInteger(&value, parameters[KBD_PARM_VENDOR], &minimum, &maximum)) {
      properties->vendor = value;
    } else {
      logMessage(LOG_WARNING, "invalid keyboard vendor code: %s", parameters[KBD_PARM_VENDOR]);
      ok = 0;
    }
  }

  if (*parameters[KBD_PARM_PRODUCT]) {
    static const int minimum = 0;
    static const int maximum = 0XFFFF;
    int value;

    if (validateInteger(&value, parameters[KBD_PARM_PRODUCT], &minimum, &maximum)) {
      properties->product = value;
    } else {
      logMessage(LOG_WARNING, "invalid keyboard product code: %s", parameters[KBD_PARM_PRODUCT]);
      ok = 0;
    }
  }

  deallocateStrings(parameters);
  return ok;
}

int
checkKeyboardProperties (const KeyboardProperties *actual, const KeyboardProperties *required) {
  if (!required) return 1;
  if (!actual)  actual = &anyKeyboard;

  if (required->type != KBD_TYPE_ANY) {
    if (required->type != actual->type) return 0;
  }

  if (required->vendor) {
    if (required->vendor != actual->vendor) return 0;
  }

  if (required->product) {
    if (required->product != actual->product) return 0;
  }

  return 1;
}

static void
logKeyEvent (const char *action, int code, int press) {
  logMessage(LOG_CATEGORY(KEYBOARD_KEYS),
             "%s %d: %s",
             (press? "press": "release"), code, action);
}

static void
flushKeyEvents (KeyboardInstanceObject *kio) {
  const KeyEventEntry *event = kio->events.buffer;

  while (kio->events.count) {
    logKeyEvent("flushing", event->code, event->press);
    forwardKeyEvent(kio, event->code, event->press);

    event += 1;
    kio->events.count -= 1;
  }

  memset(kio->deferred.mask, 0, kio->deferred.size);
  kio->deferred.modifiersOnly = 0;
}

KeyboardInstanceObject *
newKeyboardInstanceObject (KeyboardMonitorObject *kmo) {
  KeyboardInstanceObject *kio;
  unsigned int count = BITMASK_ELEMENT_COUNT(keyCodeCount, BITMASK_ELEMENT_SIZE(unsigned char));
  size_t size = sizeof(*kio) + count;

  if ((kio = malloc(size))) {
    memset(kio, 0, size);
    kio->kmo = kmo;

    kio->actualProperties = anyKeyboard;

    kio->events.buffer = NULL;
    kio->events.size = 0;
    kio->events.count = 0;

    kio->deferred.modifiersOnly = 0;
    kio->deferred.size = count;

    if (newKeyboardInstanceExtension(&kio->kix)) {
      if (enqueueItem(kmo->instanceQueue, kio)) {
        return kio;
      }

      destroyKeyboardInstanceExtension(kio->kix);
    }

    free(kio);
  } else {
    logMallocError();
  }

  return NULL;
}

void
destroyKeyboardInstanceObject (KeyboardInstanceObject *kio) {
  flushKeyEvents(kio);
  if (kio->events.buffer) free(kio->events.buffer);

  deleteItem(kio->kmo->instanceQueue, kio);
  if (kio->kix) destroyKeyboardInstanceExtension(kio->kix);
  free(kio);
}

void
destroyKeyboardMonitorObject (KeyboardMonitorObject *kmo) {
  kmo->isActive = 0;

  while (getQueueSize(kmo->instanceQueue) > 0) {
    Element *element = getQueueHead(kmo->instanceQueue);
    KeyboardInstanceObject *kio = getElementItem(element);

    destroyKeyboardInstanceObject(kio);
  }

  if (kmo->instanceQueue) deallocateQueue(kmo->instanceQueue);
  if (kmo->kmx) destroyKeyboardMonitorExtension(kmo->kmx);
  free(kmo);
}

KeyboardMonitorObject *
newKeyboardMonitorObject (const KeyboardProperties *properties, KeyEventHandler handleKeyEvent) {
  KeyboardMonitorObject *kmo;

  if ((kmo = malloc(sizeof(*kmo)))) {
    memset(kmo, 0, sizeof(*kmo));

    kmo->requiredProperties = *properties;
    kmo->handleKeyEvent = handleKeyEvent;

    if (newKeyboardMonitorExtension(&kmo->kmx)) {
      if ((kmo->instanceQueue = newQueue(NULL, NULL))) {
        if (monitorKeyboards(kmo)) {
          kmo->isActive = 1;
          return kmo;
        }

        deallocateQueue(kmo->instanceQueue);
      }

      destroyKeyboardMonitorExtension(kmo->kmx);
    }

    free(kmo);
  } else {
    logMallocError();
  }

  return NULL;
}

void
handleKeyEvent (KeyboardInstanceObject *kio, int code, int press) {
  KeyTableState state = KTS_UNBOUND;

  logKeyEvent("received", code, press);

  if (kio->kmo->isActive) {
    if ((code >= 0) && (code < keyCodeCount)) {
      const KeyValue *kv = &keyCodeMap[code];

      if ((kv->group != KBD_GROUP(SPECIAL)) || (kv->number != KBD_KEY(SPECIAL, Unmapped))) {
        if ((kv->group == KBD_GROUP(SPECIAL)) && (kv->number == KBD_KEY(SPECIAL, Ignore))) return;
        state = kio->kmo->handleKeyEvent(kv->group, kv->number, press);
      }
    }
  }

  if (state == KTS_HOTKEY) {
    logKeyEvent("ignoring", code, press);
  } else {
    typedef enum {
      WKA_NONE,
      WKA_CURRENT,
      WKA_ALL
    } WriteKeysAction;
    WriteKeysAction action = WKA_NONE;

    if (press) {
      kio->deferred.modifiersOnly = state == KTS_MODIFIERS;

      if (state == KTS_UNBOUND) {
        action = WKA_ALL;
      } else {
        if (kio->events.count == kio->events.size) {
          unsigned int newSize = kio->events.size? kio->events.size<<1: 0X1;
          KeyEventEntry *newBuffer = realloc(kio->events.buffer, (newSize * sizeof(*newBuffer)));

          if (newBuffer) {
            kio->events.buffer = newBuffer;
            kio->events.size = newSize;
          } else {
            logMallocError();
          }
        }

        if (kio->events.count < kio->events.size) {
          KeyEventEntry *event = &kio->events.buffer[kio->events.count++];

          event->code = code;
          event->press = press;
          BITMASK_SET(kio->deferred.mask, code);

          logKeyEvent("deferring", code, press);
        } else {
          logKeyEvent("discarding", code, press);
        }
      }
    } else if (kio->deferred.modifiersOnly) {
      kio->deferred.modifiersOnly = 0;
      action = WKA_ALL;
    } else if (BITMASK_TEST(kio->deferred.mask, code)) {
      KeyEventEntry *to = kio->events.buffer;
      const KeyEventEntry *from = to;
      unsigned int count = kio->events.count;

      while (count) {
        if (from->code == code) {
          logKeyEvent("dropping", from->code, from->press);
        } else if (to != from) {
          *to++ = *from;
        } else {
          to += 1;
        }

        from += 1, count -= 1;
      }

      kio->events.count = to - kio->events.buffer;
      BITMASK_CLEAR(kio->deferred.mask, code);
    } else {
      action = WKA_CURRENT;
    }

    switch (action) {
      case WKA_ALL:
        flushKeyEvents(kio);
        /* fall through */
      case WKA_CURRENT:
        logKeyEvent("forwarding", code, press);
        forwardKeyEvent(kio, code, press);

      case WKA_NONE:
        break;
    }
  }
}

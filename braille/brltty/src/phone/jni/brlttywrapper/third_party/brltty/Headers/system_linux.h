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

#ifndef BRLTTY_INCLUDED_SYSTEM_LINUX
#define BRLTTY_INCLUDED_SYSTEM_LINUX

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  const char *path;
  void *data;
} PathProcessorParameters;

typedef int PathProcessor (const PathProcessorParameters *parameters);
extern int processPathTree (const char *path, PathProcessor *processPath, void *data);

extern int compareGroups (gid_t group1, gid_t group2);
extern void sortGroups (gid_t *groups, size_t count);
extern void removeDuplicateGroups (gid_t *groups, size_t *count);

typedef void GroupsProcessor (const gid_t *groups, size_t count, void *data);
extern void processSupplementaryGroups (GroupsProcessor *processGroups, void *data);
extern int haveSupplementaryGroups (const gid_t *groups, size_t count);

extern int installKernelModule (const char *name, unsigned char *status);
extern int installSpeakerModule (void);
extern int installUinputModule (void);

extern int openCharacterDevice (const char *name, int flags, int major, int minor);

typedef struct UinputObjectStruct UinputObject;
extern UinputObject *newUinputObject (const char *name);
extern void destroyUinputObject (UinputObject *uinput);

extern int getUinputFileDescriptor (UinputObject *uinput);
extern int createUinputDevice (UinputObject *uinput);

extern int enableUinputEventType (UinputObject *uinput, int type);
extern int writeInputEvent (UinputObject *uinput, uint16_t type, uint16_t code, int32_t value);

extern int enableUinputKey (UinputObject *uinput, int key);
extern int writeKeyEvent (UinputObject *uinput, int key, int press);
extern int releasePressedKeys (UinputObject *uinput);

extern int writeRepeatDelay (UinputObject *uinput, int delay);
extern int writeRepeatPeriod (UinputObject *uinput, int period);

extern int enableUinputSound (UinputObject *uinput, int sound);
extern int enableUinputLed (UinputObject *uinput, int led);

extern UinputObject *newUinputKeyboard (const char *name);

typedef struct InputEventMonitorStruct InputEventMonitor;
typedef struct input_event InputEvent;
typedef int UinputObjectPreparer (UinputObject *uinput);
typedef void InputEventHandler (const InputEvent *event);

extern InputEventMonitor *newInputEventMonitor (
  const char *name,
  UinputObjectPreparer *prepareUinputObject,
  InputEventHandler *handleInputEvent
);

extern void destroyInputEventMonitor (
  InputEventMonitor *monitor
);

typedef uint8_t LinuxKeyCode;
#define LINUX_KEY_MAP_NAME(type) linuxKeyMap_ ## type
#define LINUX_KEY_MAP(type) const LinuxKeyCode LINUX_KEY_MAP_NAME(type)[0X100]

extern LINUX_KEY_MAP(xt00);
extern LINUX_KEY_MAP(xtE0);
extern LINUX_KEY_MAP(xtE1);
extern LINUX_KEY_MAP(at00);
extern LINUX_KEY_MAP(atE0);
extern LINUX_KEY_MAP(atE1);
extern LINUX_KEY_MAP(ps2);
extern LINUX_KEY_MAP(hid);

typedef struct {
  const char *name;
  const LinuxKeyCode *keys;
  unsigned int count;
} LinuxKeyMapDescriptor;

extern const LinuxKeyMapDescriptor linuxKeyMapDescriptors[];
extern const unsigned char linuxKeyMapCount;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SYSTEM_LINUX */

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

#ifndef BRLTTY_INCLUDED_LOCK
#define BRLTTY_INCLUDED_LOCK

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct LockDescriptorStruct LockDescriptor;

typedef enum {
  LOCK_Exclusive = 0X1,
  LOCK_NoWait    = 0X2
} LockOptions;

extern LockDescriptor *newLockDescriptor (void);
extern LockDescriptor *getLockDescriptor (LockDescriptor **lock, const char *name);
extern void freeLockDescriptor (LockDescriptor *lock);

extern int obtainLock (LockDescriptor *lock, LockOptions options);
extern void releaseLock (LockDescriptor *lock);

static inline void obtainExclusiveLock (LockDescriptor *lock) {
  obtainLock(lock, LOCK_Exclusive);
}

static inline void obtainSharedLock (LockDescriptor *lock) {
  obtainLock(lock, 0);
}

static inline int tryExclusiveLock (LockDescriptor *lock) {
  return obtainLock(lock, LOCK_Exclusive | LOCK_NoWait);
}

static inline int trySharedLock (LockDescriptor *lock) {
  return obtainLock(lock, LOCK_NoWait);
}

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_LOCK */

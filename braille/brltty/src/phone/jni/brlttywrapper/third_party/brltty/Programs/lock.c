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

#include <string.h>
#include <errno.h>

#include "lock.h"
#include "log.h"
#include "get_pthreads.h"
 
#undef CAN_LOCK
#if defined(PTHREAD_RWLOCK_INITIALIZER)
#define CAN_LOCK

struct LockDescriptorStruct {
  pthread_rwlock_t lock;
};

static int
constructLockDescriptor (LockDescriptor *lock) {
  int error;

  if (!(error = pthread_rwlock_init(&lock->lock, NULL))) {
    return 1;
  } else {
    logActionError(error, "pthread_rwlock_init");
  }

  return 0;
}

static void
destructLockDescriptor (LockDescriptor *lock) {
  pthread_rwlock_destroy(&lock->lock);
}

int
obtainLock (LockDescriptor *lock, LockOptions options) {
  if (options & LOCK_Exclusive) {
    if (options & LOCK_NoWait) return !pthread_rwlock_trywrlock(&lock->lock);
    pthread_rwlock_wrlock(&lock->lock);
  } else {
    if (options & LOCK_NoWait) return !pthread_rwlock_tryrdlock(&lock->lock);
    pthread_rwlock_rdlock(&lock->lock);
  }

  return 1;
}

void
releaseLock (LockDescriptor *lock) {
  pthread_rwlock_unlock(&lock->lock);
}

#elif defined(PTHREAD_MUTEX_INITIALIZER)
#define CAN_LOCK

struct LockDescriptorStruct {
  pthread_mutex_t mutex;
  pthread_cond_t read;
  pthread_cond_t write;
  int count;
  unsigned int writers;
};

static int
constructLockDescriptor (LockDescriptor *lock) {
  int error;

  if (!(error = pthread_cond_init(&lock->read, NULL))) {
    if (!(error = pthread_cond_init(&lock->write, NULL))) {
      if (!(error = pthread_mutex_init(&lock->mutex, NULL))) {
        lock->count = 0;
        lock->writers = 0;
        return 1;
      } else {
        logActionError(error, "pthread_mutex_init");
      }

      pthread_cond_destroy(&lock->write);
    } else {
      logActionError(error, "pthread_cond_init");
    }

    pthread_cond_destroy(&lock->read);
  } else {
    logActionError(error, "pthread_cond_init");
  }

  return 0;
}

static void
destructLockDescriptor (LockDescriptor *lock) {
  pthread_mutex_destroy(&lock->mutex);
  pthread_cond_destroy(&lock->read);
  pthread_cond_destroy(&lock->write);
}

int
obtainLock (LockDescriptor *lock, LockOptions options) {
  int locked = 0;

  pthread_mutex_lock(&lock->mutex);

  if (options & LOCK_Exclusive) {
    while (lock->count) {
      if (options & LOCK_NoWait) goto done;
      lock->writers += 1;
      pthread_cond_wait(&lock->write, &lock->mutex);
      lock->writers -= 1;
    }

    lock->count = -1;
  } else {
    while (lock->count < 0) {
      if (options & LOCK_NoWait) goto done;
      pthread_cond_wait(&lock->read, &lock->mutex);
    }

    lock->count += 1;
  }

  locked = 1;
done:
  pthread_mutex_unlock(&lock->mutex);
  return locked;
}

void
releaseLock (LockDescriptor *lock) {
  pthread_mutex_lock(&lock->mutex);

  if (lock->count < 0) {
    lock->count = 0;
  } else if (--lock->count) {
    goto done;
  }

  if (lock->writers) {
    pthread_cond_signal(&lock->write);
  } else {
    pthread_cond_broadcast(&lock->read);
  }

done:
  pthread_mutex_unlock(&lock->mutex);
}

#endif /* lock paradigm */

#ifdef CAN_LOCK
LockDescriptor *
newLockDescriptor (void) {
  LockDescriptor *lock;

  if ((lock = malloc(sizeof(*lock)))) {
    memset(lock, 0, sizeof(*lock));
    if (constructLockDescriptor(lock)) return lock;

    free(lock);
    lock = NULL;
  } else {
    logMallocError();
  }

  return lock;
}

void
freeLockDescriptor (LockDescriptor *lock) {
  destructLockDescriptor(lock);
  free(lock);
}

LockDescriptor *
getLockDescriptor (LockDescriptor **lock, const char *name) {
  static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
  int isNew = 0;

  pthread_mutex_lock(&mutex);
    if (!*lock) {
      if ((*lock = newLockDescriptor())) {
        isNew = 1;
      }
    }
  pthread_mutex_unlock(&mutex);

  if (isNew) {
    logMessage(LOG_DEBUG, "lock descriptor allocated: %s", name);
  }

  return *lock;
}
#else /* CAN_LOCK */
#warning thread lock support not available on this platform

int
obtainLock (LockDescriptor *lock, LockOptions options) {
  return 1;
}

void
releaseLock (LockDescriptor *lock) {
}

LockDescriptor *
newLockDescriptor (void) {
  errno = ENOSYS;
  return NULL;
}

void
freeLockDescriptor (LockDescriptor *lock) {
}

LockDescriptor *
getLockDescriptor (LockDescriptor **lock, const char *name) {
  return *lock;
}
#endif /* CAN_LOCK */

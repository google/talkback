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

/* This is a minimal pthread implementation based on windows functions.
 * It is *not* intended to be complete - just complete enough to get
 * BRLTTY running.
 */

#ifndef BRLTTY_INCLUDED_WIN_PTHREAD
#define BRLTTY_INCLUDED_WIN_PTHREAD

#include "prologue.h"

#include <windows.h>
#include <stdio.h>
#include <errno.h>
#include <sys/time.h>

#include "timing.h"

#ifndef ETIMEDOUT
#define ETIMEDOUT EAGAIN
#endif

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define winPthreadAssertWindows(expr) do { if (!(expr)) { setSystemErrno(); return errno; } } while (0)
#define winPthreadAssertPthread(expr) do { int ret = (expr); if (ret) return ret; } while (0)
#define winPthreadAssert(expr) do { if (!(expr)) return EIO; } while (0)

#if !defined(__struct_timespec_defined) && !defined(__MINGW64_VERSION_MAJOR)
struct timespec {
  time_t  tv_sec;  /* Seconds */
  long    tv_nsec; /* Nanoseconds */
};
#endif /* struct timespec */

static inline DWORD pthread_gettimeout_np(const struct timespec *abs_timeout) {
  struct timeval now;
  LONG timeout;
  gettimeofday(&now, NULL);
  timeout = (abs_timeout->tv_sec  - now.tv_sec) * 1000 +
            (abs_timeout->tv_nsec - now.tv_usec * 1000) / 1000000;
  if (timeout < 0)
    timeout = 0;
  return timeout;
}

/***********
 * threads *
 ***********/

typedef DWORD pthread_attr_t;
typedef HANDLE pthread_t;

static inline pthread_t pthread_self(void) {
  return GetCurrentThread();
}

static inline int pthread_equal(pthread_t t1, pthread_t t2) {
  return t1 == t2;
}

static inline int pthread_attr_init (pthread_attr_t *attr) {
  *attr = 0;
  return 0;
}

#define PTHREAD_CREATE_DETACHED 1
static inline int pthread_attr_setdetachstate (pthread_attr_t *attr, int yes) {
  /* not supported, ignore */
  return 0;
}

static inline int pthread_attr_setstacksize (pthread_attr_t *attr, size_t stacksize) {
  /* not supported, ignore */
  return 0;
}

static inline int pthread_attr_destroy (pthread_attr_t *attr) {
  return 0;
}

/* "real" cleanup handling not yet implemented */
typedef struct {
  void (*routine) (void *);
  void *arg;
} __pthread_cleanup_handler;

void pthread_cleanup_push (void (*routine) (void *), void *arg);
#define pthread_cleanup_push(routine, arg) do { \
  __pthread_cleanup_handler __cleanup_handler = {routine, arg};

void pthread_cleanup_pop (int execute);
#define pthread_cleanup_pop(execute) \
  if (execute) __cleanup_handler.routine(__cleanup_handler.arg); \
} while (0);

static inline int pthread_create (
  pthread_t *thread, const pthread_attr_t *attr,
  void * (*fun) (void *), void *arg
) {
  if (attr && *attr)
    return EINVAL;
  winPthreadAssertWindows(*thread = CreateThread(NULL, 0, (LPTHREAD_START_ROUTINE) fun, arg, 0, NULL));
  return 0;
}

static inline int pthread_setcancelstate (int state, int *oldstate) {
  /* not yet implemented :( */
  return 0;
}

static inline int pthread_cancel (pthread_t thread) {
  /* This is quite harsh :( */
  winPthreadAssertWindows(TerminateThread(thread, 0));
  return 0;
}

static inline void pthread_exit (void *res) {
  ExitThread((DWORD) (DWORD_PTR) res);
}

static inline int pthread_join (pthread_t thread, void **res) {
again:
  switch (WaitForSingleObject(thread, INFINITE)) {
    default:
    case WAIT_FAILED:
      setSystemErrno();
      return errno;
    case WAIT_ABANDONED:
    case WAIT_OBJECT_0:
      break;
    case WAIT_TIMEOUT:
      goto again;
    }
  if (res) {
    DWORD _res;
    if (GetExitCodeThread(thread, &_res))
      *res = (void *)(DWORD_PTR)_res;
  }
  return 0;
}

/***********
 * mutexes *
 ***********/

#define PTHREAD_MUTEX_INITIALIZER NULL
typedef HANDLE pthread_mutex_t;
#define PTHREAD_MUTEX_RECURSIVE 1
typedef int pthread_mutexattr_t;

static inline int pthread_mutexattr_init(pthread_mutexattr_t *attr) {
  *attr = PTHREAD_MUTEX_RECURSIVE;
  return 0;
}

static inline int pthread_mutexattr_settype(pthread_mutexattr_t *attr, int type) {
  if (type != PTHREAD_MUTEX_RECURSIVE)
    return EINVAL;
  *attr = type;
  return 0;
}

static inline int pthread_mutex_init (pthread_mutex_t *mutex, pthread_mutexattr_t *attr) {
  if (attr && *attr!=PTHREAD_MUTEX_RECURSIVE)
    return EINVAL;
  winPthreadAssertWindows(*mutex = CreateMutex(NULL, FALSE, NULL));
  return 0;
}

static inline int pthread_mutex_unlock (pthread_mutex_t *mutex) {
  winPthreadAssertWindows(ReleaseMutex(*mutex));
  return 0;
}

static inline int pthread_mutex_lock (pthread_mutex_t *mutex);
static inline int __pthread_mutex_alloc_concurrently (pthread_mutex_t *mutex) {
    HANDLE mutex_init_mutex;
  /* Get access to one global named mutex to serialize mutex initialization */
  winPthreadAssertWindows((mutex_init_mutex = CreateMutex(NULL, FALSE, "StarPU mutex init")));
  winPthreadAssertPthread(pthread_mutex_lock(&mutex_init_mutex));
  /* Now we are the one that can initialize it */
  if (!*mutex)
    winPthreadAssertPthread(pthread_mutex_init(mutex,NULL));
  winPthreadAssertPthread(pthread_mutex_unlock(&mutex_init_mutex));
    winPthreadAssertWindows(CloseHandle(mutex_init_mutex));
  return 0;
}

static inline int pthread_mutex_lock (pthread_mutex_t *mutex) {
  if (!*mutex)
    __pthread_mutex_alloc_concurrently (mutex);
again:
  switch (WaitForSingleObject(*mutex, INFINITE)) {
    default:
    case WAIT_FAILED:
      setSystemErrno();
      return errno;
    case WAIT_ABANDONED:
    case WAIT_OBJECT_0:
      return 0;
    case WAIT_TIMEOUT:
      goto again;
  }
}

static inline int pthread_mutex_trylock (pthread_mutex_t *mutex) {
  if (!*mutex)
    __pthread_mutex_alloc_concurrently (mutex);
  switch (WaitForSingleObject(*mutex, 0)) {
    default:
    case WAIT_FAILED:
      setSystemErrno();
      return errno;
    case WAIT_ABANDONED:
    case WAIT_OBJECT_0:
      return 0;
    case WAIT_TIMEOUT:
      return EBUSY;
  }
}

static inline int pthread_mutex_destroy (pthread_mutex_t *mutex) {
  winPthreadAssertWindows(CloseHandle(*mutex));
  *mutex = INVALID_HANDLE_VALUE;
  return 0;
}

/**************
 * semaphores *
 **************/

typedef HANDLE sem_t;

static inline int sem_init(sem_t *sem, int pshared, unsigned int value) {
  winPthreadAssertWindows(*sem = CreateSemaphore(NULL, value, MAXLONG, NULL));
  return 0;
}

static inline int do_sem_wait(sem_t *sem, DWORD timeout) {
  switch (WaitForSingleObject(*sem, timeout)) {
    default:
    case WAIT_FAILED:
      setSystemErrno();
      return -1;
    case WAIT_TIMEOUT:
      errno = EAGAIN;
      return -1;
    case WAIT_ABANDONED:
    case WAIT_OBJECT_0:
      return 0;
  }
}

#define sem_wait(sem) do_sem_wait(sem, INFINITE)
#define sem_trywait(sem) do_sem_wait(sem, 0)

static inline int sem_timedwait(sem_t *sem, const struct timespec *abs_timeout) {
  return do_sem_wait(sem, pthread_gettimeout_np(abs_timeout));
}

static inline int sem_post(sem_t *sem) {
  winPthreadAssertWindows(ReleaseSemaphore(*sem, 1, NULL));
  return 0;
}

static inline int sem_destroy(sem_t *sem) {
  winPthreadAssertWindows(CloseHandle(*sem));
  return 0;
}

/**************
 * conditions *
 **************/

typedef struct {
  HANDLE sem;
  volatile unsigned nbwait;
} pthread_cond_t;
#define PTHREAD_COND_INITIALIZER { NULL, 0}

typedef unsigned pthread_condattr_t;

static inline int pthread_cond_init (pthread_cond_t *cond, const pthread_condattr_t *attr) {
  if (attr)
    return EINVAL;
  winPthreadAssertWindows(cond->sem = CreateSemaphore(NULL, 0, MAXLONG, NULL));
  cond->nbwait = 0;
  return 0;
}

static inline int pthread_cond_timedwait (pthread_cond_t *cond, pthread_mutex_t *mutex, const struct timespec *time) {
  if (!cond->sem)
    winPthreadAssertPthread(pthread_cond_init(cond,NULL));
  cond->nbwait++;
  winPthreadAssertPthread(pthread_mutex_unlock(mutex));
  switch (WaitForSingleObject(cond->sem, pthread_gettimeout_np(time))) {
    default:
    case WAIT_FAILED:
      setSystemErrno();
      winPthreadAssertPthread(pthread_mutex_lock(mutex));
      return errno;
    case WAIT_TIMEOUT:
      winPthreadAssertPthread(pthread_mutex_lock(mutex));
      return ETIMEDOUT;
    case WAIT_ABANDONED:
    case WAIT_OBJECT_0:
      break;
  }
  winPthreadAssertPthread(pthread_mutex_lock(mutex));
  cond->nbwait--;
  return 0;
}

static inline int pthread_cond_wait (pthread_cond_t *cond, pthread_mutex_t *mutex) {
  if (!cond->sem)
    winPthreadAssertPthread(pthread_cond_init(cond,NULL));
  cond->nbwait++;
  winPthreadAssertPthread(pthread_mutex_unlock(mutex));
again:
  switch (WaitForSingleObject(cond->sem, INFINITE)) {
    case WAIT_FAILED:
      setSystemErrno();
      winPthreadAssert(!pthread_mutex_lock(mutex));
      return errno;
    case WAIT_TIMEOUT:
      goto again;
    case WAIT_ABANDONED:
    case WAIT_OBJECT_0:
      break;
  }
  winPthreadAssertPthread(pthread_mutex_lock(mutex));
  cond->nbwait--;
  return 0;
}

static inline int pthread_cond_signal (pthread_cond_t *cond) {
  if (!cond->sem)
    winPthreadAssertPthread(pthread_cond_init(cond,NULL));
  if (cond->nbwait)
    ReleaseSemaphore(cond->sem, 1, NULL);
  return 0;
}

static inline int pthread_cond_broadcast (pthread_cond_t *cond) {
  if (!cond->sem)
    winPthreadAssertPthread(pthread_cond_init(cond,NULL));
  ReleaseSemaphore(cond->sem, cond->nbwait, NULL);
  return 0;
}

static inline int pthread_cond_destroy (pthread_cond_t *cond) {
  if (cond->sem) {
  winPthreadAssertWindows(CloseHandle(cond->sem));
    cond->sem = NULL;
  }
  return 0;
}

/*******
 * TLS *
 *******/

typedef DWORD pthread_key_t;
#define PTHREAD_ONCE_INIT {PTHREAD_MUTEX_INITIALIZER, 0}
typedef struct {
  pthread_mutex_t mutex;
  unsigned done;
} pthread_once_t;

static inline int pthread_once (pthread_once_t *once, void (*oncefun)(void)) {
  winPthreadAssertPthread(pthread_mutex_lock(&once->mutex));
  if (!once->done) {
    oncefun();
    once->done = 1;
  }
  winPthreadAssertPthread(pthread_mutex_unlock(&once->mutex));
  return 0;
}

static inline int pthread_key_create (pthread_key_t *key, void (*freefun)(void *)) {
  DWORD res;
  winPthreadAssertWindows((res = TlsAlloc()) != 0xFFFFFFFF);
  *key = res;
  return 0;
}

static inline int pthread_key_delete (pthread_key_t key) {
  winPthreadAssertWindows(TlsFree(key));
  return 0;
}

static inline void *pthread_getspecific (pthread_key_t key) {
  return TlsGetValue(key);
}

static inline int pthread_setspecific (pthread_key_t key, const void *data) {
  winPthreadAssertWindows(TlsSetValue(key, (LPVOID) data));
  return 0;
}

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_WIN_PTHREAD */

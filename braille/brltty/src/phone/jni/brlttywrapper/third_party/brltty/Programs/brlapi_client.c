/*
 * libbrlapi - A library providing access to braille terminals for applications.
 *
 * Copyright (C) 2002-2023 by
 *   Samuel Thibault <Samuel.Thibault@ens-lyon.org>
 *   Sébastien Hinderer <Sebastien.Hinderer@ens-lyon.org>
 *
 * libbrlapi comes with ABSOLUTELY NO WARRANTY.
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

/* api_client.c handles connection with BrlApi */

#ifdef __ANDROID__
#define __ANDROID_API__ 21
#endif /* __ANDROID__ */

#define WIN_ERRNO_STORAGE_CLASS static
#include "prologue.h"

#include <stdio.h>
#include <stddef.h>
#include <stdarg.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <locale.h>
#include <assert.h>
#include <limits.h>
#include <unistd.h>

#ifndef __MINGW32__
#ifdef HAVE_LANGINFO_H
#include <langinfo.h>
#endif /* HAVE_LANGINFO_H */
#endif /* __MINGW32__ */

#ifdef __MINGW32__
#include <ws2tcpip.h>
#include "win_pthread.h"

#include "win_errno.h"

#define syslog(level,fmt,...) fprintf(stderr,#level ": " fmt, ## __VA_ARGS__)

#else /* __MINGW32__ */
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>

#include <pthread.h>
#include <syslog.h>

#if defined(__APPLE__)
#include <mach/mach.h>
#include <mach/semaphore.h>

#else /* semaphore support */
#include <semaphore.h>
#endif /* semaphore support */

#ifdef HAVE_SYS_SELECT_H
#include <sys/select.h>
#endif /* HAVE_SYS_SELECT_H */
#ifdef HAVE_SYS_POLL_H
#include <poll.h>
#endif /* HAVE_SYS_POLL_H */
#endif /* __MINGW32__ */

#include <sys/time.h>
#include "gettime.h"

#ifdef linux
#include <sys/sysmacros.h>
#include <linux/major.h>
#include <linux/tty.h>
#include <linux/vt.h>
#define MAXIMUM_VIRTUAL_CONSOLE MAX_NR_CONSOLES
#endif /* linux */

#ifdef __OpenBSD__
#define MAXIMUM_VIRTUAL_CONSOLE 16
#endif /* __OpenBSD__ */

#ifndef MAXIMUM_VIRTUAL_CONSOLE
#define MAXIMUM_VIRTUAL_CONSOLE 1
#endif /* MAXIMUM_VIRTUAL_CONSOLE */

#ifdef HAVE_SD_SESSION_GET_VT
#include <systemd/sd-login.h>
#endif /* HAVE_SD_SESSION_GET_VT */

#define BRLAPI_NO_DEPRECATED
#include "brlapi.h"
#include "brlapi_protocol.h"

#define BRLAPI(fun) brlapi_ ## fun
#include "brlapi_common.h"

#ifndef MIN
#define MIN(a, b) (((a) < (b))? (a): (b))
#endif /* MIN */

#ifndef MAX
#define MAX(a, b) (((a) > (b))? (a): (b))
#endif /* MAX */

/* API states */
#define STCONNECTED 1
#define STRAW 2
#define STSUSPEND 4
#define STCONTROLLINGTTY 8

#ifdef WINDOWS
#ifdef __MINGW32__
static WSADATA wsadata;
#endif /* __MINGW32__ */

static void* GetProc(const char *library, const char *fun) {
  HMODULE module;
  void *ret;
  if (!(module = LoadLibrary(library))) return (void*)(-1);
  if ((ret = GetProcAddress(module, fun))) return ret;
  FreeLibrary(module);
  return (void*)(-1);
}

#define CHECKPROC(library, name) \
  (name##Proc && name##Proc != (void*)(-1))
#define CHECKGETPROC(library, name) \
  (name##Proc != (void*)(-1) && (name##Proc || (name##Proc = GetProc(library,#name)) != (void*)(-1)))
#define WIN_PROC_STUB(name) typeof(name) (*name##Proc);


static WIN_PROC_STUB(GetConsoleWindow);

#ifdef __MINGW32__
static WIN_PROC_STUB(wcslen);

static WIN_PROC_STUB(getaddrinfo);
#define getaddrinfo(host,port,hints,res) getaddrinfoProc(host,port,hints,res)
static WIN_PROC_STUB(freeaddrinfo);
#define freeaddrinfo(res) freeaddrinfoProc(res)
#endif /* __MINGW32__ */
#endif /* WINDOWS */

/* We need to declare these as weak external references to determine
 * at runtime whether libpthread is used or not.
 * We also can't rely on the functions prototypes.
 */
#if defined(WINDOWS)

#elif defined(__APPLE__)
#pragma weak pthread_key_create
#pragma weak pthread_once
#pragma weak pthread_getspecific
#pragma weak pthread_setspecific

typedef semaphore_t sem_t;

static int
sem_init (sem_t *sem, int shared, int value) {
  return semaphore_create(mach_task_self(), sem, SYNC_POLICY_FIFO, value);
}

static int
sem_post (sem_t *sem) {
  return semaphore_signal(*sem);
}

static int
sem_wait (sem_t *sem) {
  return semaphore_wait(*sem);
}

static int
sem_timedwait (sem_t *sem, const struct timespec *abs) {
  struct timeval now;
  getRealTime(&now);

  mach_timespec_t rel = {
    .tv_sec = abs->tv_sec - now.tv_sec,
    .tv_nsec = abs->tv_nsec - (now.tv_usec * 1000)
  };

  if (rel.tv_nsec < 0) rel.tv_sec -= 1, rel.tv_nsec += 1000000000;
  if (rel.tv_sec < 0) rel.tv_sec = 0, rel.tv_nsec = 0;

  return semaphore_timedwait(*sem, rel);
}

static int
sem_trywait (sem_t *sem) {
  mach_timespec_t rel = { .tv_sec=0, .tv_nsec=0 };
  return semaphore_timedwait(*sem, rel);
}

static int
sem_destroy (sem_t *sem) {
  return semaphore_destroy(mach_task_self(), *sem);
}

#elif defined(__GNUC__) || defined(__sun__)
#pragma weak pthread_key_create
#pragma weak pthread_once
#pragma weak pthread_getspecific
#pragma weak pthread_setspecific
#pragma weak sem_init
#pragma weak sem_post
#pragma weak sem_wait
#pragma weak sem_destroy
#endif /* weak external references */

/** key presses buffer size
 *
 * key presses won't be lost provided no more than BRL_KEYBUF_SIZE key presses
 * are done between two calls to brlapi_read* if a call to another function is
 * done in the meanwhile (which needs somewhere to put them before being able
 * to get responses from the server)
*/
#define BRL_KEYBUF_SIZE 256

struct brlapi_parameterCallback_t {
  brlapi_param_t parameter;
  brlapi_param_subparam_t subparam;
  brlapi_param_flags_t flags;
  brlapi_paramCallback_t func;
  void *priv;
  struct brlapi_parameterCallback_t *prev, *next;
};

struct brlapi_handle_t { /* Connection-specific information */
  uint32_t serverVersion;
  unsigned int brlx;
  unsigned int brly;
  brlapi_fileDescriptor fileDescriptor; /* Descriptor of the socket connected to BrlApi */
  int addrfamily; /* Address family of the socket */
  /* to protect concurrent fd write operations */
  pthread_mutex_t fileDescriptor_mutex;
  /* to protect concurrent fd requests */
  pthread_mutex_t req_mutex;
  /* to protect concurrent key reading */
  pthread_mutex_t key_mutex;
  /* to protect concurrent fd key events and request answers, also protects the
   * key buffer */
  /* Only two threads might want to take it: one that already got
  * brlapi_req_mutex, or one that got key_mutex */
  pthread_mutex_t read_mutex;
  Packet packet;
  /* when someone is already reading (reading==1), put expected type,
   * address/size of buffer, and address of return value, then wait on semaphore,
   * the buffer gets filled, altRes too */
  int reading;
  brlapi_packetType_t altExpectedPacketType;
  unsigned char *altPacket;
  size_t altSize;
  ssize_t *altRes;
  sem_t *altSem;
  int state;
  pthread_mutex_t state_mutex;

#ifdef LC_GLOBAL_LOCALE
  locale_t default_locale;
#endif /* LC_GLOBAL_LOCALE */

  /* key presses buffer, for when key presses are received instead of
   * acknowledgements for instance
   *
   * every function must hence be able to read at least sizeof(brlapi_keyCode_t) */
  brlapi_keyCode_t keybuf[BRL_KEYBUF_SIZE];
  unsigned keybuf_next;
  unsigned keybuf_nb;
  union {
    brlapi_exceptionHandler_t withoutHandle;
    brlapi__exceptionHandler_t withHandle;
  } exceptionHandler;
  int exception_sync;
  int exception_error;
  pthread_mutex_t exceptionHandler_mutex;

  struct brlapi_parameterCallback_t *parameterCallbacks;
  /* This protects the callback list, but also callback calling conventions in
   * general:
   * callbacks are called in the update order, and not after unregistration */
  pthread_mutex_t callbacks_mutex;
  /* The callback might want to unregister itself, while we are processing the
   * list of callbacks. This records where we are in the list, to skip the
   * deleted item. */
  struct brlapi_parameterCallback_t *nextCallback;

  void *clientData; /* Private client data */
};

/* Function brlapi_getLibraryVersion */
void brlapi_getLibraryVersion(int *major, int *minor, int *revision)
{
  *major = BRLAPI_MAJOR;
  *minor = BRLAPI_MINOR;
  *revision = BRLAPI_REVISION;
}

/* Function brlapi_getHandleSize */
size_t BRLAPI_STDCALL brlapi_getHandleSize(void)
{
  return sizeof(brlapi_handle_t);
}

static brlapi_handle_t defaultHandle;

/* brlapi_initializeHandle */
/* Initialize a BrlAPI handle */
static void brlapi_initializeHandle(brlapi_handle_t *handle)
{
  handle->brlx = 0;
  handle->brly = 0;
  handle->fileDescriptor = BRLAPI_INVALID_FILE_DESCRIPTOR;
  handle->addrfamily = 0;
  pthread_mutex_init(&handle->fileDescriptor_mutex, NULL);
  pthread_mutex_init(&handle->req_mutex, NULL);
  pthread_mutex_init(&handle->key_mutex, NULL);
  pthread_mutex_init(&handle->read_mutex, NULL);
  brlapi_initializePacket(&handle->packet);
  handle->reading = 0;
  handle->altExpectedPacketType = 0;
  handle->altPacket = NULL;
  handle->altSize = 0;
  handle->altRes = NULL;
  handle->altSem = NULL;
  handle->state = 0;
  pthread_mutex_init(&handle->state_mutex, NULL);

#ifdef LC_GLOBAL_LOCALE
  handle->default_locale = LC_GLOBAL_LOCALE;
#endif /* LC_GLOBAL_LOCALE */

  memset(handle->keybuf, 0, sizeof(handle->keybuf));
  handle->keybuf_next = 0;
  handle->keybuf_nb = 0;
  if (handle == &defaultHandle)
    handle->exceptionHandler.withoutHandle = brlapi_defaultExceptionHandler;
  else
    handle->exceptionHandler.withHandle = brlapi__defaultExceptionHandler;
  handle->exception_sync = 0;
  handle->exception_error = BRLAPI_ERROR_SUCCESS;
  pthread_mutex_init(&handle->exceptionHandler_mutex, NULL);
  handle->parameterCallbacks = NULL;
  {
    pthread_mutexattr_t mattr;
    pthread_mutexattr_init(&mattr);
    pthread_mutexattr_settype(&mattr, PTHREAD_MUTEX_RECURSIVE);
    pthread_mutex_init(&handle->callbacks_mutex, &mattr);
  }
  handle->nextCallback = NULL;
  handle->clientData = NULL;
}

/* brlapi_doWaitForPacket */
/* Waits for the specified type of packet: must be called with brlapi_req_mutex locked */
/* deadline can be used to stop waiting after a given date, or wait forever (NULL) */
/* If the right packet type arrives, returns its size */
/* Returns -1 if a non-fatal error is encountered */
/* Returns -2 on end of file */
/* Returns -3 if the available packet was not for us */
/* Returns -4 on timeout (if deadline is not NULL) */
/* Calls the exception handler if an exception is encountered */
static ssize_t brlapi__doWaitForPacket(brlapi_handle_t *handle, brlapi_packetType_t expectedPacketType, void *packet, size_t packetSize, struct timeval *deadline)
{
  uint32_t *uint32Packet = handle->packet.content;
  const brlapi_packet_t *localPacket = (brlapi_packet_t *) uint32Packet;
  const brlapi_errorPacket_t *errorPacket = &localPacket->error;

  uint32_t size;
  brlapi_packetType_t type;
  int ret = 0;

  struct timeval now;
  long delay = 0;
  int polled = 0; /* Whether we have polled at least once */

  do {
    if (deadline) {
      getRealTime(&now);
      delay = (deadline->tv_sec  - now.tv_sec ) * 1000 +
	      (deadline->tv_usec - now.tv_usec) / 1000;
      if (delay < 0) {
	if (polled) {
	  /* The deadline has expired, don't wait more */
	  return -4;
	}
	/* Poll at least once */
	delay = 0;
      }
    }
    polled = 1;
#ifdef __MINGW32__
    DWORD dw;
    dw = WaitForSingleObject(handle->packet.overl.hEvent, deadline ? delay : INFINITE);
    if (dw == WAIT_FAILED) {
      setSystemErrno();
      LibcError("waiting for packet");
      return -2;
    }

    if (dw == WAIT_OBJECT_0)
#else /* __MINGW32__ */
#ifdef HAVE_POLL
    struct pollfd pollfd;

    pollfd.fd = handle->fileDescriptor;
    pollfd.events = POLLIN;
    pollfd.revents = 0;

    if (poll(&pollfd, 1, deadline ? delay : -1) < 0) {
      LibcError("waiting for packet");
      return -2;
    }

    if (pollfd.revents & POLLIN)
#else /* HAVE_POLL */
    fd_set sockset;
    struct timeval timeout, *ptimeout = NULL;
    if (deadline) {
      timeout.tv_sec = delay / 1000;
      timeout.tv_usec = (delay % 1000) * 1000;
      ptimeout = &timeout;
    }

    FD_ZERO(&sockset);
    FD_SET(handle->fileDescriptor, &sockset);
    if (select(handle->fileDescriptor+1, &sockset, NULL, NULL, ptimeout) < 0) {
      LibcError("waiting for packet");
      return -2;
    }

    if (FD_ISSET(handle->fileDescriptor, &sockset))
#endif /* !HAVE_POLL */
#endif /* __MINGW32__ */
    {
      /* Some data is here, read it */
      ret = brlapi__readPacket(&handle->packet, handle->fileDescriptor);
      if (ret == -1) {
	LibcError("reading packet");
      }
      if (ret < 0) {
	/* error or end of file */
	return -2;
      }
    }
  } while (ret == 0);

  /* Got a packet, process it.  */
  size = handle->packet.header.size;
  type = handle->packet.header.type;

  if (type==expectedPacketType)
  {
    /* For us, just copy */
    memcpy(packet, handle->packet.content, MIN(packetSize, size));
    return size;
  }

  /* Not for us. For alternate reader? */
  pthread_mutex_lock(&handle->read_mutex);
  if (handle->altSem && type==handle->altExpectedPacketType) {
    /* Yes, put packet content there */
    memcpy(handle->altPacket, handle->packet.content, MIN(handle->altSize, size));
    *handle->altRes = size;
#ifndef WINDOWS
    if (sem_post)
#endif /* WINDOWS */
      sem_post(handle->altSem);
    handle->altSem = NULL;
    pthread_mutex_unlock(&handle->read_mutex);
    return -3;
  }
  /* No alternate reader, read it locally... */
  if ((type==BRLAPI_PACKET_KEY) && (handle->state & STCONTROLLINGTTY) && (size==sizeof(brlapi_keyCode_t))) {
    /* keypress, buffer it */
    if (handle->keybuf_nb>=BRL_KEYBUF_SIZE) {
      syslog(LOG_WARNING,"lost key: 0X%8lx%8lx\n",(unsigned long)ntohl(uint32Packet[0]),(unsigned long)ntohl(uint32Packet[1]));
    } else {
      handle->keybuf[(handle->keybuf_next+handle->keybuf_nb++)%BRL_KEYBUF_SIZE]
          = brlapi_packetToKeyCode(uint32Packet);
    }
    pthread_mutex_unlock(&handle->read_mutex);
    return -3;
  }
  if (type==BRLAPI_PACKET_PARAM_UPDATE) {
    /* Parameter update, find handler */
    brlapi_paramValuePacket_t *value = (void*) handle->packet.content;
    brlapi_param_flags_t flags = ntohl(value->flags);
    brlapi_param_t param = ntohl(value->param);
    brlapi_param_subparam_t subparam = ((brlapi_param_subparam_t)ntohl(value->subparam_hi) << 32) | ntohl(value->subparam_lo);
    size_t rlen = size - sizeof(flags) - sizeof(param) - sizeof(subparam);
    _brlapi_ntohParameter(param, value, rlen);
    pthread_mutex_lock(&handle->callbacks_mutex);
    for(handle->nextCallback = handle->parameterCallbacks;
	handle->nextCallback; ) {
      struct brlapi_parameterCallback_t *callback = handle->nextCallback;
      handle->nextCallback = handle->nextCallback->next;
      if (callback->parameter == param &&
	  callback->subparam == subparam &&
	  (callback->flags & BRLAPI_PARAMF_GLOBAL) == (flags & BRLAPI_PARAMF_GLOBAL)) {
	callback->func(param, subparam, callback->flags, callback->priv, value->data, rlen);
	/* Note: the callback might have removed this entry */
      }
    }
    handle->nextCallback = NULL;
    pthread_mutex_unlock(&handle->callbacks_mutex);
  }
  pthread_mutex_unlock(&handle->read_mutex);

  /* otherwise this is an error */

  if (type==BRLAPI_PACKET_ERROR) {
    brlapi_errno = ntohl(errorPacket->code);
    return -1;
  }

  if (type==BRLAPI_PACKET_EXCEPTION) {
    size_t esize;
    int hdrSize = sizeof(errorPacket->code)+sizeof(errorPacket->type);
    int err = ntohl(errorPacket->code);

    if (size<hdrSize)
      esize = 0;
    else
      esize = size-hdrSize;

    pthread_mutex_lock(&handle->exceptionHandler_mutex);
    if (handle->exception_sync) {
      handle->exception_error = err;
      pthread_mutex_unlock(&handle->exceptionHandler_mutex);
      return -3;
    }
    if (handle==&defaultHandle)
      defaultHandle.exceptionHandler.withoutHandle(err, ntohl(errorPacket->type), &errorPacket->packet, esize);
    else
      handle->exceptionHandler.withHandle(handle, err, ntohl(errorPacket->type), &errorPacket->packet, esize);
    pthread_mutex_unlock(&handle->exceptionHandler_mutex);

    pthread_mutex_lock(&handle->fileDescriptor_mutex);
    closeFileDescriptor(handle->fileDescriptor);
    handle->fileDescriptor = BRLAPI_INVALID_FILE_DESCRIPTOR;
    pthread_mutex_unlock(&handle->fileDescriptor_mutex);

    return -2;
  }

  syslog(LOG_ERR,"(brlapi_waitForPacket) Received unexpected packet of type %s and size %ld\n",brlapi_getPacketTypeName(type),(long)size);
  return -3;
}

#define TRY_WAIT_FOR_EXPECTED_PACKET 0
#define WAIT_FOR_EXPECTED_PACKET 1

#define POLL 0
#define WAIT_FOREVER (-1)

/* brlapi_waitForPacket */
/* same as brlapi_doWaitForPacket, but sleeps instead of reading if another
 * thread is already reading, and timeout_ms expressed as relative ms delay
 * instead of absolute deadline.
 * timeout_ms set to WAIT_FOREVER means no deadline.
 * Never returns -2. If loop is WAIT_FOR_EXPECTED_PACKET, never returns -3.
 */
static ssize_t brlapi__waitForPacket(brlapi_handle_t *handle, brlapi_packetType_t expectedPacketType, void *packet, size_t size, int loop, int timeout_ms) {
  int doread;
  ssize_t res;
  sem_t sem;
  struct timeval deadline, *pdeadline = NULL;
  if (timeout_ms >= 0) {
    getRealTime(&deadline);
    deadline.tv_sec += timeout_ms / 1000;
    deadline.tv_usec += (timeout_ms % 1000) * 1000;
    if (deadline.tv_usec >= 1000000) {
      deadline.tv_sec++;
      deadline.tv_usec -= 1000000;
    }
    pdeadline = &deadline;
  }
again:
  doread = 0;
  pthread_mutex_lock(&handle->read_mutex);
  if (!handle->reading) {
    doread = handle->reading = 1;
  } else {
    if (
#ifndef WINDOWS
      !sem_init || !sem_post || !sem_wait || !sem_destroy ||
#endif /* WINDOWS */
	handle->altSem) {
      /* This can't happen without threads */
      pthread_mutex_unlock(&handle->read_mutex);
      syslog(LOG_ERR,"third call to brlapi_waitForPacket !");
      brlapi_errno = BRLAPI_ERROR_ILLEGAL_INSTRUCTION;
      return -1;
    }

    handle->altExpectedPacketType = expectedPacketType;
    handle->altPacket = packet;
    handle->altSize = size;
    handle->altRes = &res;
    sem_init(&sem, 0, 0);
    handle->altSem = &sem;
  }
  pthread_mutex_unlock(&handle->read_mutex);
  if (doread) {
    do {
      res = brlapi__doWaitForPacket(handle, expectedPacketType, packet, size, pdeadline);
    } while (loop && (res == -3 || (res == -1 && brlapi_errno == BRLAPI_ERROR_LIBCERR && (
	  brlapi_libcerrno == EINTR ||
#ifdef EWOULDBLOCK
	  brlapi_libcerrno == EWOULDBLOCK ||
#endif /* EWOULDBLOCK */
	  brlapi_libcerrno == EAGAIN))));
    pthread_mutex_lock(&handle->read_mutex);
    if (handle->altSem) {
      *handle->altRes = -3; /* no packet for him */
#ifndef WINDOWS
      if (sem_post)
#endif /* WINDOWS */
        sem_post(handle->altSem);
      handle->altSem = NULL;
    }
    handle->reading = 0;
    pthread_mutex_unlock(&handle->read_mutex);
  } else {
    int ret;
    if (timeout_ms == 0) {
      ret = sem_trywait(&sem);
    } else if (timeout_ms > 0) {
      struct timespec abs_timeout;
      abs_timeout.tv_sec = deadline.tv_sec;
      abs_timeout.tv_nsec = deadline.tv_usec * 1000;
      ret = sem_timedwait(&sem, &abs_timeout);
    } else {
      do {
	ret = sem_wait(&sem);
      } while (ret && errno == EINTR);
    }
    if (ret) {
      /* Timeout, drop our semaphore */
      pthread_mutex_lock(&handle->read_mutex);
      handle->altSem = NULL;
      pthread_mutex_unlock(&handle->read_mutex);
      res = -4;
    }
    sem_destroy(&sem);
    if (res == -1 /* He got an error, we want to read it too */
	|| (res == -3 && loop)) /* reader hadn't any packet for us */
      goto again;
  }
  if (res==-2) {
    res = -1;
    brlapi_errno = BRLAPI_ERROR_EOF;
  }
  return res;
}

/* brlapi_waitForAck */
/* Wait for an acknowledgement, must be called with brlapi_req_mutex locked */
static int brlapi__waitForAck(brlapi_handle_t *handle)
{
  return brlapi__waitForPacket(handle, BRLAPI_PACKET_ACK, NULL, 0, WAIT_FOR_EXPECTED_PACKET, WAIT_FOREVER);
}

/* brlapi_writePacketWaitForAck */
/* write a packet and wait for an acknowledgement */
static int brlapi__writePacketWaitForAck(brlapi_handle_t *handle, brlapi_packetType_t type, const void *buf, size_t size)
{
  ssize_t res;
  pthread_mutex_lock(&handle->req_mutex);
  if ((res=brlapi_writePacket(handle->fileDescriptor, type,buf,size))<0) {
    pthread_mutex_unlock(&handle->req_mutex);
    return res;
  }
  res=brlapi__waitForAck(handle);
  pthread_mutex_unlock(&handle->req_mutex);
  return res;
}

/* brlapi__pause */
/* Wait for an event to be received */
int BRLAPI_STDCALL brlapi__pause(brlapi_handle_t *handle, int timeout_ms) {
  ssize_t res = brlapi__waitForPacket(handle, 0, NULL, 0, TRY_WAIT_FOR_EXPECTED_PACKET, timeout_ms);
  if (res == -3) {
    brlapi_libcerrno = EINTR;
    brlapi_errno = BRLAPI_ERROR_LIBCERR;
    brlapi_errfun = "waitForPacket";
    res = -1;
  }
  if (res == -4) {
    /* Timeout */
    res = 0;
  }
  return res;
}

/* brlapi_pause */
int BRLAPI_STDCALL brlapi_pause(int timeout_ms) {
  return brlapi__pause(&defaultHandle, timeout_ms);
}


/* brlapi__sync */
int BRLAPI_STDCALL brlapi__sync(brlapi_handle_t *handle) {
  int res, error;

  pthread_mutex_lock(&handle->exceptionHandler_mutex);
  handle->exception_sync++;
  error = handle->exception_error;
  handle->exception_error = BRLAPI_ERROR_SUCCESS;
  pthread_mutex_unlock(&handle->exceptionHandler_mutex);

  if (error != BRLAPI_ERROR_SUCCESS) {
    pthread_mutex_lock(&handle->exceptionHandler_mutex);
    handle->exception_sync--;
    pthread_mutex_unlock(&handle->exceptionHandler_mutex);
    brlapi_errno = error;
    return -1;
  }

  res = brlapi__writePacketWaitForAck(handle,BRLAPI_PACKET_SYNCHRONIZE,NULL,0);

  if (res == -1) {
    pthread_mutex_lock(&handle->exceptionHandler_mutex);
    handle->exception_sync--;
    pthread_mutex_unlock(&handle->exceptionHandler_mutex);
    return -1;
  }

  pthread_mutex_lock(&handle->exceptionHandler_mutex);
  handle->exception_sync--;
  error = handle->exception_error;
  handle->exception_error = BRLAPI_ERROR_SUCCESS;
  pthread_mutex_unlock(&handle->exceptionHandler_mutex);

  if (error != BRLAPI_ERROR_SUCCESS) {
    brlapi_errno = error;
    return -1;
  }
  return 0;
}

/* brlapi_sync */
int BRLAPI_STDCALL brlapi_sync(void) {
  return brlapi__sync(&defaultHandle);
}

/* Function: tryHost */
/* Tries to connect to the given host. */
static int tryHost(brlapi_handle_t *handle, const char *hostAndPort) {
  char *host = NULL;
  char *port;
  SocketDescriptor sockfd = -1;

#ifdef __MINGW32__
  if (WSAStartup(
#ifdef HAVE_GETADDRINFO
	  MAKEWORD(2,0),
#else /* HAVE_GETADDRINFO */
	  MAKEWORD(1,1),
#endif /* HAVE_GETADDRINFO */
	  &wsadata))
    return -1;
#endif /* __MINGW32__ */

  handle->addrfamily = brlapi_expandHost(hostAndPort,&host,&port);

#if defined(PF_LOCAL)
  if (handle->addrfamily == PF_LOCAL) {
    int lpath = strlen(BRLAPI_SOCKETPATH),lport;
    lport = strlen(port);

#ifdef __MINGW32__
    {
      HANDLE pipefd;
      char path[lpath+lport+1];

      memcpy(path, BRLAPI_SOCKETPATH, lpath);
      memcpy(path+lpath, port, lport+1);

      while ((pipefd = CreateFile(path,GENERIC_READ|GENERIC_WRITE,
	      FILE_SHARE_READ|FILE_SHARE_WRITE,NULL,OPEN_EXISTING,SECURITY_IMPERSONATION|FILE_FLAG_OVERLAPPED,NULL))
	  == INVALID_HANDLE_VALUE) {
	if (GetLastError() != ERROR_PIPE_BUSY) {
	  brlapi_errfun = "CreateFile";
	  goto outlibc;
	}

	WaitNamedPipe(path, NMPWAIT_WAIT_FOREVER);
      }

      sockfd = (SocketDescriptor)pipefd;
    }
#else /* __MINGW32__ */
    {
      struct sockaddr_un sa;

      if (lpath+1+lport+1 > sizeof(sa.sun_path)) {
	brlapi_libcerrno=ENAMETOOLONG;
	brlapi_errfun="path";
	brlapi_errno = BRLAPI_ERROR_LIBCERR;
	goto out;
      }

      if ((sockfd = socket(PF_LOCAL, SOCK_STREAM, 0))<0) {
        brlapi_errfun="socket";
        setSocketErrno();
        goto outlibc;
      }

#if !defined(HAVE_POLL)
      if (sockfd >= FD_SETSIZE) {
	/* Will not be able to call select() on this */
	closeFileDescriptor(sockfd);
	brlapi_errfun="socket";
	setErrno(EMFILE);
	goto outlibc;
      }
#endif /* !HAVE_POLL */

      sa.sun_family = AF_LOCAL;
      memcpy(sa.sun_path,BRLAPI_SOCKETPATH "/",lpath+1);
      memcpy(sa.sun_path+lpath+1,port,lport+1);

      if (connect(sockfd, (struct sockaddr *) &sa, sizeof(sa))<0) {
        brlapi_errfun="connect";
        setSocketErrno();
        goto outlibc;
      }
    }
#endif /* __MINGW32__ */

    handle->fileDescriptor = (FileDescriptor) sockfd;
  } else {
#else /* PF_LOCAL */
  if (0) {} else {
#endif /* PF_LOCAL */

#ifdef __MINGW32__
    if (CHECKGETPROC("ws2_32.dll",getaddrinfo)
	&& CHECKGETPROC("ws2_32.dll",freeaddrinfo)) {
#endif /* __MINGW32__ */
#if defined(HAVE_GETADDRINFO) || defined(__MINGW32__)

    struct addrinfo *res,*cur;
    struct addrinfo hints;

    memset(&hints, 0, sizeof(hints));
    hints.ai_family = PF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;

    brlapi_gaierrno = getaddrinfo(host, port, &hints, &res);
    if (brlapi_gaierrno) {
      brlapi_errno=BRLAPI_ERROR_GAIERR;
      brlapi_libcerrno=errno;
      goto out;
    }
    for(cur = res; cur; cur = cur->ai_next) {
      sockfd = socket(cur->ai_family, cur->ai_socktype, cur->ai_protocol);
      if (sockfd<0) continue;

#if !defined(__MINGW32__) && !defined(HAVE_POLL)
      if (sockfd >= FD_SETSIZE) {
	/* Will not be able to call select() on this */
	closeFileDescriptor(sockfd);
	brlapi_errfun="socket";
	setErrno(EMFILE);
	goto outlibc;
      }
#endif /* !__MINGW32__ && !HAVE_POLL */

      if (connect(sockfd, cur->ai_addr, cur->ai_addrlen)<0) {
        closeSocketDescriptor(sockfd);
        continue;
      }
      break;
    }
    freeaddrinfo(res);
    if (!cur) {
      brlapi_errno=BRLAPI_ERROR_CONNREFUSED;
      goto out;
    }

#endif /* HAVE_GETADDRINFO */
#ifdef __MINGW32__
    } else {
#endif /* __MINGW32__ */
#if !defined(HAVE_GETADDRINFO) || defined(__MINGW32__)

    struct sockaddr_in addr;
    struct hostent *he;

    memset(&addr,0,sizeof(addr));
    addr.sin_family = AF_INET;
    if (!port)
      addr.sin_port = htons(BRLAPI_SOCKETPORTNUM);
    else {
      char *c;
      addr.sin_port = htons(strtol(port, &c, 0));
      if (*c) {
	struct servent *se;

	if (!(se = getservbyname(port,"tcp"))) {
	  brlapi_gaierrno = h_errno;
          brlapi_errno=BRLAPI_ERROR_GAIERR;
	  goto out;
	}
	addr.sin_port = se->s_port;
      }
    }

    if (!host) {
      addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    } else if ((addr.sin_addr.s_addr = inet_addr(host)) == htonl(INADDR_NONE)) {
      if (!(he = gethostbyname(host))) {
	brlapi_gaierrno = h_errno;
	brlapi_errno = BRLAPI_ERROR_GAIERR;
	goto out;
      }
      if (he->h_addrtype != AF_INET) {
#ifdef EAFNOSUPPORT
        errno = EAFNOSUPPORT;
#else /* EAFNOSUPPORT */
        errno = EINVAL;
#endif /* EAFNOSUPPORT */
	brlapi_errfun = "gethostbyname";
	goto outlibc;
      }
      if (he->h_length > sizeof(addr.sin_addr)) {
        errno = EINVAL;
	brlapi_errfun = "gethostbyname";
	goto outlibc;
      }
      memcpy(&addr.sin_addr,he->h_addr,he->h_length);
    }

    sockfd = socket(addr.sin_family, SOCK_STREAM, 0);
    if (sockfd<0) {
      brlapi_errfun = "socket";
      setSocketErrno();
      goto outlibc;
    }

#if !defined(__MINGW32__) && !defined(HAVE_POLL)
    if (sockfd >= FD_SETSIZE) {
      /* Will not be able to call select() on this */
      closeFileDescriptor(sockfd);
      brlapi_errfun="socket";
      setErrno(EMFILE);
      goto outlibc;
    }
#endif /* !__MINGW32__ && !HAVE_POLL */

    if (connect(sockfd, (struct sockaddr *) &addr, sizeof(addr))<0) {
      brlapi_errfun = "connect";
      setSocketErrno();
      goto outlibc;
    }

#endif /* !HAVE_GETADDRINFO */
#ifdef __MINGW32__
    }
#endif /* __MINGW32__ */

#if defined(IPPROTO_TCP) && defined(TCP_NODELAY)
    {
      int yes=1;
      setsockopt(sockfd,IPPROTO_TCP,TCP_NODELAY,(void*)&yes,sizeof(yes));
    }
#endif /* defined(IPPROTO_TCP) && defined(TCP_NODELAY) */
    handle->fileDescriptor = (FileDescriptor) sockfd;
  }
  free(host);
  free(port);
  return 0;

outlibc:
  brlapi_errno = BRLAPI_ERROR_LIBCERR;
  brlapi_libcerrno = errno;
  if (sockfd>=0)
    closeSocketDescriptor(sockfd);
out:
  free(host);
  free(port);
  return -1;
}

/* Function : updateSettings */
/* Updates the content of a brlapi_connectionSettings_t structure according to */
/* another structure of the same type */
static void updateSettings(brlapi_connectionSettings_t *s1, const brlapi_connectionSettings_t *s2)
{
  if (s2==NULL) return;
  if ((s2->auth) && (*s2->auth))
    s1->auth = s2->auth;
  if ((s2->host) && (*s2->host))
    s1->host = s2->host;
}

/* Function: brlapi_openConnection
 * Creates a socket to connect to BrlApi */
brlapi_fileDescriptor BRLAPI_STDCALL brlapi__openConnection(brlapi_handle_t *handle, const brlapi_connectionSettings_t *clientSettings, brlapi_connectionSettings_t *usedSettings)
{
  brlapi_packet_t packet;
  brlapi_packet_t serverPacket;
  brlapi_authClientPacket_t *auth = &packet.authClient;
  brlapi_authServerPacket_t *authServer = &serverPacket.authServer;
  brlapi_versionPacket_t *version = &serverPacket.version;
  uint32_t *type;
  int len;

  brlapi_connectionSettings_t settings = { BRLAPI_DEFAUTH, ":0" };
  brlapi_connectionSettings_t envsettings = { getenv("BRLAPI_AUTH"), getenv("BRLAPI_HOST") };

  /* Here update settings with the parameters from misc sources (files, env...) */
  updateSettings(&settings, &envsettings);
  updateSettings(&settings, clientSettings);
  if (usedSettings!=NULL) updateSettings(usedSettings, &settings);
  brlapi_initializeHandle(handle);

  if (tryHost(handle, settings.host)<0) {
    const char *port = strrchr(settings.host, ':');
    if (port != settings.host) goto out;
    if (!isPortNumber(port+1, NULL)) goto out;

    brlapi_error_t originalError = brlapi_error;
    size_t portLength = strlen(port);

    static const char *const localhostAddressTable[] = {
      LOCALHOST_ADDRESS_IPV4,

      #ifdef AF_INET6
      LOCALHOST_ADDRESS_IPV6,
      #endif /* AF_INET6 */
    };

    const char *const *lha = localhostAddressTable;
    const char *const *end = lha + ARRAY_COUNT(localhostAddressTable);

    while (lha < end) {
      size_t hostLength = strlen(*lha);
      char host[hostLength + portLength + 1];
      snprintf(host, sizeof(host), "%s%s", *lha, port);

      if (tryHost(handle, host) != -1) {
        if (usedSettings) usedSettings->host = strdup(host);
        break;
      }

      lha += 1;
    }

    if (lha == end) {
      brlapi_error = originalError;
      goto out;
    }
  }

  if ((len = brlapi__waitForPacket(handle, BRLAPI_PACKET_VERSION, &serverPacket, sizeof(serverPacket), WAIT_FOR_EXPECTED_PACKET, WAIT_FOREVER)) < 0)
    goto outfd;

  handle->serverVersion = ntohl(version->protocolVersion);
  if (handle->serverVersion < 8) {
    /* We only provide compatibility with version 8 and later. */
    brlapi_errno = BRLAPI_ERROR_PROTOCOL_VERSION;
    goto outfd;
  }

  version->protocolVersion = htonl(BRLAPI_PROTOCOL_VERSION);
  if (brlapi_writePacket(handle->fileDescriptor, BRLAPI_PACKET_VERSION, version, sizeof(*version)) < 0)
    goto outfd;

  if ((len = brlapi__waitForPacket(handle, BRLAPI_PACKET_AUTH, &serverPacket, sizeof(serverPacket), WAIT_FOR_EXPECTED_PACKET, WAIT_FOREVER)) < 0)
    return BRLAPI_INVALID_FILE_DESCRIPTOR;

  for (type = &authServer->type[0]; type < &authServer->type[len / sizeof(authServer->type[0])]; type++) {
    auth->type = *type;
    switch (ntohl(*type)) {
      case BRLAPI_AUTH_NONE:
	if (usedSettings) usedSettings->auth = "none";
	goto done;
      case BRLAPI_AUTH_KEY: {
        size_t authKeyLength;
	int res;
	char *keyfile = brlapi_getKeyFile(settings.auth);
	if (!keyfile)
	  continue;
	res = brlapi_loadAuthKey(keyfile, &authKeyLength, (void *) &auth->key);
	free(keyfile);
        if (res < 0)
	  continue;
        res = brlapi_writePacket(handle->fileDescriptor, BRLAPI_PACKET_AUTH, auth,
	  sizeof(auth->type)+authKeyLength);
	memset(&auth->key, 0, authKeyLength);
	if (res < 0)
	  goto outfd;
	if (usedSettings) usedSettings->auth = settings.auth;
	break;
      }
      default:
        /* unsupported authorization type */
	continue;
    }
    if ((brlapi__waitForAck(handle)) == 0) goto done;
  }

  /* no suitable authorization method */
  brlapi_errno = BRLAPI_ERROR_AUTHENTICATION;
outfd:
  closeFileDescriptor(handle->fileDescriptor);
  handle->fileDescriptor = BRLAPI_INVALID_FILE_DESCRIPTOR;
out:
  return BRLAPI_INVALID_FILE_DESCRIPTOR;

done:
  pthread_mutex_lock(&handle->state_mutex);
  handle->state = STCONNECTED;
  pthread_mutex_unlock(&handle->state_mutex);
  return handle->fileDescriptor;
}

brlapi_fileDescriptor BRLAPI_STDCALL brlapi_openConnection(const brlapi_connectionSettings_t *clientSettings, brlapi_connectionSettings_t *usedSettings)
{
  return brlapi__openConnection(&defaultHandle, clientSettings, usedSettings);
}

brlapi_fileDescriptor BRLAPI_STDCALL brlapi__getFileDescriptor(brlapi_handle_t *handle)
{
  return handle->fileDescriptor;
}

brlapi_fileDescriptor BRLAPI_STDCALL brlapi_getFileDescriptor(void)
{
  return brlapi__getFileDescriptor(&defaultHandle);
}

/* brlapi_closeConnection */
/* Cleanly close the socket */
void BRLAPI_STDCALL brlapi__closeConnection(brlapi_handle_t *handle)
{
  pthread_mutex_lock(&handle->state_mutex);
  handle->state = 0;
  pthread_mutex_unlock(&handle->state_mutex);
  pthread_mutex_lock(&handle->fileDescriptor_mutex);
  closeFileDescriptor(handle->fileDescriptor);
  handle->fileDescriptor = BRLAPI_INVALID_FILE_DESCRIPTOR;
  pthread_mutex_unlock(&handle->fileDescriptor_mutex);

#ifdef LC_GLOBAL_LOCALE
  if (handle->default_locale != LC_GLOBAL_LOCALE) {
    freelocale(handle->default_locale);
  }
#endif /* LC_GLOBAL_LOCALE */

#ifdef __MINGW32__
  WSACleanup();
#endif /* __MINGW32__ */
}

void BRLAPI_STDCALL brlapi_closeConnection(void)
{
  brlapi__closeConnection(&defaultHandle);
}

/* brlapi__setClientData */
void BRLAPI_STDCALL brlapi__setClientData(brlapi_handle_t *handle, void *data)
{
  handle->clientData = data;
}

void BRLAPI_STDCALL brlapi_setClientData(void *data)
{
  brlapi__setClientData(&defaultHandle, data);
}

/* brlapi__getClientData */
void * BRLAPI_STDCALL brlapi__getClientData(brlapi_handle_t *handle)
{
  return handle->clientData;
}

void * BRLAPI_STDCALL brlapi_getClientData(void)
{
  return brlapi__getClientData(&defaultHandle);
}

/* brlapi_getDriverSpecific */
/* Switch to device specific mode */
static int brlapi__getDriverSpecific(brlapi_handle_t *handle, const char *driver, brlapi_packetType_t type, int st)
{
  int res;
  brlapi_packet_t packet;
  brlapi_getDriverSpecificModePacket_t *driverPacket = &packet.getDriverSpecificMode;
  unsigned int n = strlen(driver);
  if (n>BRLAPI_MAXNAMELENGTH) {
    brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
    return -1;
  }
  pthread_mutex_lock(&defaultHandle.state_mutex);
  if ((defaultHandle.state & st)) {
    brlapi_errno = BRLAPI_ERROR_ILLEGAL_INSTRUCTION;
    res = -1;
    goto out;
  }
  driverPacket->magic = htonl(BRLAPI_DEVICE_MAGIC);
  driverPacket->nameLength = n;
  memcpy(&driverPacket->name, driver, n);
  res = brlapi__writePacketWaitForAck(handle, type, &packet, sizeof(uint32_t)+1+n);
  if (res!=-1)
    handle->state |= st;
out:
  pthread_mutex_unlock(&handle->state_mutex);
  return res;
}

/* brlapi_leaveDriverSpecific */
/* Leave device specific mode */
static int brlapi__leaveDriverSpecific(brlapi_handle_t *handle, brlapi_packetType_t type, int st)
{
  int res;
  pthread_mutex_lock(&handle->state_mutex);
  if (!(handle->state & st)) {
    brlapi_errno = BRLAPI_ERROR_ILLEGAL_INSTRUCTION;
    res = -1;
    goto out;
  }
  res = brlapi__writePacketWaitForAck(handle, type, NULL, 0);
  if (!res)
    handle->state &= ~st;
out:
  pthread_mutex_unlock(&handle->state_mutex);
  return res;
}

/* brlapi_enterRawMode */
/* Switch to Raw mode */
int BRLAPI_STDCALL brlapi__enterRawMode(brlapi_handle_t *handle, const char *driver)
{
  return brlapi__getDriverSpecific(handle, driver, BRLAPI_PACKET_ENTERRAWMODE, STRAW);
}

int BRLAPI_STDCALL brlapi_enterRawMode(const char *driver)
{
  return brlapi__enterRawMode(&defaultHandle, driver);
}

/* brlapi_leaveRawMode */
/* Leave Raw mode */
int BRLAPI_STDCALL brlapi__leaveRawMode(brlapi_handle_t *handle)
{
  return brlapi__leaveDriverSpecific(handle, BRLAPI_PACKET_LEAVERAWMODE, STRAW);
}

int BRLAPI_STDCALL brlapi_leaveRawMode(void)
{
  return brlapi__leaveRawMode(&defaultHandle);
}

/* brlapi_sendRaw */
/* Send a Raw Packet */
ssize_t BRLAPI_STDCALL brlapi__sendRaw(brlapi_handle_t *handle, const void *buf, size_t size)
{
  ssize_t res;
  pthread_mutex_lock(&handle->fileDescriptor_mutex);
  res=brlapi_writePacket(handle->fileDescriptor, BRLAPI_PACKET_PACKET, buf, size);
  pthread_mutex_unlock(&handle->fileDescriptor_mutex);
  return res;
}

ssize_t BRLAPI_STDCALL brlapi_sendRaw(const void *buf, size_t size)
{
  return brlapi__sendRaw(&defaultHandle, buf, size);
}

/* brlapi_recvRaw */
/* Get a Raw packet */
ssize_t BRLAPI_STDCALL brlapi__recvRaw(brlapi_handle_t *handle, void *buf, size_t size)
{
  ssize_t res;
  if (!(handle->state & STRAW)) {
    brlapi_errno = BRLAPI_ERROR_ILLEGAL_INSTRUCTION;
    return -1;
  }
  res = brlapi__waitForPacket(handle, BRLAPI_PACKET_PACKET, buf, size, TRY_WAIT_FOR_EXPECTED_PACKET, WAIT_FOREVER);
  if (res == -3) {
    brlapi_libcerrno = EINTR;
    brlapi_errno = BRLAPI_ERROR_LIBCERR;
    brlapi_errfun = "waitForPacket";
    res = -1;
  }
  return res;
}

ssize_t BRLAPI_STDCALL brlapi_recvRaw(void *buf, size_t size)
{
  return brlapi__recvRaw(&defaultHandle, buf, size);
}

/* brlapi_suspendDriver */
int BRLAPI_STDCALL brlapi__suspendDriver(brlapi_handle_t *handle, const char *driver)
{
  return brlapi__getDriverSpecific(handle, driver, BRLAPI_PACKET_SUSPENDDRIVER, STSUSPEND);
}

int BRLAPI_STDCALL brlapi_suspendDriver(const char *driver)
{
  return brlapi__suspendDriver(&defaultHandle, driver);
}

/* brlapi_resumeDriver */
int BRLAPI_STDCALL brlapi__resumeDriver(brlapi_handle_t *handle)
{
  return brlapi__leaveDriverSpecific(handle, BRLAPI_PACKET_RESUMEDRIVER, STSUSPEND);
}

int BRLAPI_STDCALL brlapi_resumeDriver(void)
{
  return brlapi__resumeDriver(&defaultHandle);
}

/* Function brlapi_request */
/* Sends a request to the API and waits for the answer */
/* The answer is put in the given packet */
static ssize_t brlapi__request(brlapi_handle_t *handle, brlapi_packetType_t request, void *packet, size_t size)
{
  ssize_t res;
  pthread_mutex_lock(&handle->req_mutex);
  res = brlapi_writePacket(handle->fileDescriptor, request, NULL, 0);
  if (res==-1) {
    pthread_mutex_unlock(&handle->req_mutex);
    return -1;
  }
  res = brlapi__waitForPacket(handle, request, packet, size, WAIT_FOR_EXPECTED_PACKET, WAIT_FOREVER);
  pthread_mutex_unlock(&handle->req_mutex);
  return res;
}

/* Function : brlapi_getDriverName */
/* Name of the driver used by brltty */
int BRLAPI_STDCALL brlapi__getDriverName(brlapi_handle_t *handle, char *name, size_t n)
{
  ssize_t res = brlapi__request(handle, BRLAPI_PACKET_GETDRIVERNAME, name, n);
  if ((res>0) && (res<=n)) name[res-1] = '\0';
  return res;
}

int BRLAPI_STDCALL brlapi_getDriverName(char *name, size_t n)
{
  return brlapi__getDriverName(&defaultHandle, name, n);
}

/* Function : brlapi_getModelIdentifier */
/* An identifier for the model of the device used by brltty */
int BRLAPI_STDCALL brlapi__getModelIdentifier(brlapi_handle_t *handle, char *identifier, size_t n)
{
  ssize_t res = brlapi__request(handle, BRLAPI_PACKET_GETMODELID, identifier, n);
  if ((res>0) && (res<=n)) identifier[res-1] = '\0';
  return res;
}

int BRLAPI_STDCALL brlapi_getModelIdentifier(char *identifier, size_t n)
{
  return brlapi__getModelIdentifier(&defaultHandle, identifier, n);
}

/* Function : brlapi_getDisplaySize */
/* Returns the size of the braille display */
int BRLAPI_STDCALL brlapi__getDisplaySize(brlapi_handle_t *handle, unsigned int *x, unsigned int *y)
{
  uint32_t displaySize[2];
  ssize_t res;

  if (handle->brlx&&handle->brly) { *x = handle->brlx; *y = handle->brly; return 0; }
  res = brlapi__request(handle, BRLAPI_PACKET_GETDISPLAYSIZE, displaySize, sizeof(displaySize));
  if (res==-1) { return -1; }
  handle->brlx = ntohl(displaySize[0]);
  handle->brly = ntohl(displaySize[1]);
  *x = handle->brlx; *y = handle->brly;
  return 0;
}

int BRLAPI_STDCALL brlapi_getDisplaySize(unsigned int *x, unsigned int *y)
{
  return brlapi__getDisplaySize(&defaultHandle, x, y);
}

/* Function: brlapi_getParameter */

/* Internal version, returns the reply value packet and the length of the value */
static ssize_t _brlapi__getParameter(brlapi_handle_t *handle, brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, brlapi_paramValuePacket_t *reply)
{
  brlapi_paramRequestPacket_t request;
  int res;
  ssize_t rlen;

  request.flags = htonl(flags);
  request.param = htonl(parameter);
  request.subparam_hi = htonl(subparam >> 32);
  request.subparam_lo = htonl(subparam & 0xfffffffful);

  pthread_mutex_lock(&handle->req_mutex);
  res = brlapi_writePacket(handle->fileDescriptor, BRLAPI_PACKET_PARAM_REQUEST, &request, sizeof(request));
  if (res < 0) {
    pthread_mutex_unlock(&handle->req_mutex);
    return -1;
  }
  if (flags & BRLAPI_PARAMF_GET)
    rlen = brlapi__waitForPacket(handle, BRLAPI_PACKET_PARAM_VALUE, reply, sizeof(*reply), 1, -1);
  else
    rlen = brlapi__waitForAck(handle);
  pthread_mutex_unlock(&handle->req_mutex);

  if (rlen < 0) {
    return -1;
  }

  if (flags & BRLAPI_PARAMF_GET) {
    rlen -= sizeof(brlapi_param_flags_t) + sizeof(brlapi_param_t) + sizeof(brlapi_param_subparam_t);
    if (rlen < 0) {
      brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
      return -1;
    }
  }

  return rlen;
}

ssize_t BRLAPI_STDCALL brlapi__getParameter(brlapi_handle_t *handle, brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, void* data, size_t len)
{
  brlapi_paramValuePacket_t reply;
  ssize_t rlen;

  if (flags & ~BRLAPI_PARAMF_GLOBAL) {
    brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
    return -1;
  }

  rlen = _brlapi__getParameter(handle, parameter, subparam, flags | BRLAPI_PARAMF_GET, &reply);
  if (rlen < 0)
    return -1;

  if (rlen < len) {
    len = rlen;
  }
  _brlapi_ntohParameter(parameter, &reply, rlen);
  memcpy(data, &reply.data, len);

  return rlen;
}

ssize_t BRLAPI_STDCALL brlapi_getParameter(brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, void* data, size_t len)
{
  return brlapi__getParameter(&defaultHandle, parameter, subparam, flags, data, len);
}

void* BRLAPI_STDCALL brlapi__getParameterAlloc(brlapi_handle_t *handle, brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, size_t *len)
{
  brlapi_paramValuePacket_t reply;
  ssize_t rlen;
  void *data;

  if (flags & ~BRLAPI_PARAMF_GLOBAL) {
    brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
    return NULL;
  }

  rlen = _brlapi__getParameter(handle, parameter, subparam, flags | BRLAPI_PARAMF_GET, &reply);
  if (rlen < 0)
    return NULL;

  data = malloc(rlen + 1);
  if (!data)
    return NULL;

  _brlapi_ntohParameter(parameter, &reply, rlen);
  memcpy(data, &reply.data, rlen);
  ((char*)data)[rlen] = 0;
  if (len)
    *len = rlen;
  return data;
}

void* BRLAPI_STDCALL brlapi_getParameterAlloc(brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, size_t *len)
{
  return brlapi__getParameterAlloc(&defaultHandle, parameter, subparam, flags, len);
}

/* Function: brlapi_setParameter */
int BRLAPI_STDCALL brlapi__setParameter(brlapi_handle_t *handle, brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, const void* data, size_t len)
{
  brlapi_paramValuePacket_t packet;
  int res;

  if (flags & ~BRLAPI_PARAMF_GLOBAL) {
    brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
    return -1;
  }

  if (len > sizeof(packet.data)) {
    brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
    return -1;
  }

  packet.flags = htonl(flags);
  packet.param = htonl(parameter);
  packet.subparam_hi = htonl(subparam >> 32);
  packet.subparam_lo = htonl(subparam & 0xfffffffful);
  memcpy(packet.data, data, len);
  _brlapi_htonParameter(parameter, &packet, len);

  res = brlapi__writePacketWaitForAck(handle, BRLAPI_PACKET_PARAM_VALUE, &packet, sizeof(packet.flags) + sizeof(parameter) + sizeof(subparam) + len);
  return res;
}

int BRLAPI_STDCALL brlapi_setParameter(brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, const void* data, size_t len)
{
  return brlapi__setParameter(&defaultHandle, parameter, subparam, flags, data, len);
}

/* Function: brlapi_watchParameter */
brlapi_paramCallbackDescriptor_t BRLAPI_STDCALL brlapi__watchParameter(brlapi_handle_t *handle, brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, brlapi_paramCallback_t func, void *priv, void* data, size_t len)
{
  brlapi_paramValuePacket_t reply;
  ssize_t rlen;
  struct brlapi_parameterCallback_t *callback;

  if (flags & ~(BRLAPI_PARAMF_GLOBAL|BRLAPI_PARAMF_SELF)) {
    brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
    return NULL;
  }

  pthread_mutex_lock(&handle->callbacks_mutex);
  rlen = _brlapi__getParameter(handle, parameter, subparam, flags | BRLAPI_PARAMF_GET | BRLAPI_PARAMF_SUBSCRIBE, &reply);
  if (rlen < 0) {
    pthread_mutex_unlock(&handle->callbacks_mutex);
    return NULL;
  }

  callback = malloc(sizeof(*callback));
  if (callback == NULL) {
    brlapi_errno = BRLAPI_ERROR_NOMEM;
    return NULL;
  }
  callback->parameter = parameter;
  callback->subparam = subparam;
  callback->flags = flags;
  callback->func = func;
  callback->priv = priv;

  callback->next = handle->parameterCallbacks;
  if (callback->next)
    callback->next->prev = callback;
  callback->prev = NULL;
  handle->parameterCallbacks = callback;

  _brlapi_ntohParameter(parameter, &reply, rlen);
  if (data) {
    if (rlen < len) {
      len = rlen;
    }
    memcpy(data, &reply.data, len);
  } else {
    func(parameter, subparam, flags, priv, &reply.data, rlen);
  }
  pthread_mutex_unlock(&handle->callbacks_mutex);

  return callback;
}

brlapi_paramCallbackDescriptor_t BRLAPI_STDCALL brlapi_watchParameter(brlapi_param_t parameter, brlapi_param_subparam_t subparam, brlapi_param_flags_t flags, brlapi_paramCallback_t func, void *priv, void* data, size_t len)
{
  return brlapi__watchParameter(&defaultHandle, parameter, subparam, flags, func, priv, data, len);
}

/* Function: brlapi_unwatchParameter */
int BRLAPI_STDCALL brlapi__unwatchParameter(brlapi_handle_t *handle, brlapi_paramCallbackDescriptor_t descriptor)
{
  brlapi_paramValuePacket_t reply;
  ssize_t rlen;
  struct brlapi_parameterCallback_t *callback = descriptor;

  pthread_mutex_lock(&handle->callbacks_mutex);
  rlen = _brlapi__getParameter(handle, callback->parameter, callback->subparam, callback->flags | BRLAPI_PARAMF_UNSUBSCRIBE, &reply);
  if (rlen < 0) {
    pthread_mutex_unlock(&handle->callbacks_mutex);
    return -1;
  }

  if (handle->nextCallback == callback) {
    /* We might be removing an entry which brlapi__doWaitForPacket was planning
     * to have a look at next, make it skip it. */
    handle->nextCallback = handle->nextCallback->next;
  }

  if (callback->next) {
    callback->next->prev = callback->prev;
  }
  if (callback->prev) {
    callback->prev->next = callback->next;
  } else {
    handle->parameterCallbacks = callback->next;
  }
  pthread_mutex_unlock(&handle->callbacks_mutex);
  free(callback);
  return 0;
}

int BRLAPI_STDCALL brlapi_unwatchParameter(brlapi_paramCallbackDescriptor_t descriptor)
{
  return brlapi__unwatchParameter(&defaultHandle, descriptor);
}


/* Function : getControllingTty */
/* Returns the number of the caller's controlling terminal */
/* -1 if error or unknown */
static int getControllingTty(void)
{
  int tty;
  const char *env;

  /*if ((env = getenv("WINDOW")) && sscanf(env, "%u", &tty) == 1) return tty;*/
  if ((env = getenv("WINDOWID")) && sscanf(env, "%u", &tty) == 1) return tty;
  if ((env = getenv("CONTROLVT")) && sscanf(env, "%u", &tty) == 1) return tty;
  if (!getenv("DISPLAY") && !getenv("WAYLAND_DISPLAY"))
    if ((env = getenv("XDG_VTNR")) && sscanf(env, "%u", &tty) == 1) return tty;

#ifdef WINDOWS
  if (CHECKGETPROC("kernel32.dll", GetConsoleWindow))
    /* really good guess */
    if ((tty = (LONG_PTR) GetConsoleWindowProc())) return tty;
  if ((tty = (LONG_PTR) GetActiveWindow()) || (tty = (LONG_PTR) GetFocus())) {
    /* good guess, but need to get back up to parent window */
    HWND root = GetDesktopWindow();
    HWND tmp = (HWND) (LONG_PTR) tty;
    while (1) {
      tmp = GetParent(tmp);
      if (!tmp || tmp == root) return tty;
      tty = (LONG_PTR) tmp;
    }
  }
  /* poor guess: assumes that focus is here */
  if ((tty = (LONG_PTR) GetForegroundWindow())) return tty;
#endif /* WINDOWS */

#ifdef linux
  do {
    int fd = open("/proc/self/stat", O_RDONLY, 0);
    if (fd < 0) {
      break;
    }

    char buf[256];
    ssize_t read_bytes = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (read_bytes < 0) {
      break;
    }
    /* Append null at the end of buffer to ensure that sscanf doesn't
     * overflows.*/
    buf[read_bytes] = '\0';

    /* The line format is "$pid ($comm) $state ...
     * where comm may contain space and `)` as part of its name, ex:
     * 12345 (some proc (foo)) R ...
     * So to process valid identified after comm, we should skip comm by
     * getting the 1st `)` from end.
     */
    char *ptr = buf + read_bytes - 1;
    while (ptr > buf && *ptr != ')') {
      ptr -= 1;
    }
    if (*ptr != ')') {
      break;
    }
    /* Point to space after ($comm). */
    ptr += 1;

    int vt;
    int ok = sscanf(ptr, " %*c %*d %*d %*d %d", &tty) == 1;

    if (ok && (major(tty) == TTY_MAJOR)) {
      vt = minor(tty);
      if ((vt >= 1) && (vt <= MAXIMUM_VIRTUAL_CONSOLE)) return vt;
    }
  } while (0);
#endif /* linux */

  return -1;
}

/* Function : brlapi_enterTtyMode */
/* Takes control of a tty */
int BRLAPI_STDCALL brlapi__enterTtyMode(brlapi_handle_t *handle, int tty, const char *driverName)
{
  /* Determine which tty to take control of */
  if (tty<0) tty = getControllingTty();
  /* 0 can be a valid screen WINDOW
  0xffffffff can not be a valid WINDOWID (top 3 bits guaranteed to be zero) */
  if (tty<0) { brlapi_errno=BRLAPI_ERROR_UNKNOWNTTY; return -1; }

  if (brlapi__enterTtyModeWithPath(handle, &tty, 1, driverName)) return -1;

  return tty;
}

int BRLAPI_STDCALL brlapi_enterTtyMode(int tty, const char *how)
{
  return brlapi__enterTtyMode(&defaultHandle, tty, how);
}

/* Function : brlapi_enterTtyModeWithPath */
/* Takes control of a tty path */
int BRLAPI_STDCALL brlapi__enterTtyModeWithPath(brlapi_handle_t *handle, const int *ttys, int nttys, const char *driverName)
{
  int res;
  void *packet;
  unsigned char *p;
  uint32_t *nbTtys, *t;
  char *ttytreepath,*cur,*next;
  int ttypath;
  unsigned int n, nttytreepath;
  size_t size;

  pthread_mutex_lock(&handle->state_mutex);
  if ((handle->state & STCONTROLLINGTTY)) {
    pthread_mutex_unlock(&handle->state_mutex);
    brlapi_errno = BRLAPI_ERROR_ILLEGAL_INSTRUCTION;
    return -1;
  }

  if (brlapi__getDisplaySize(handle, &handle->brlx, &handle->brly)<0){
    pthread_mutex_unlock(&handle->state_mutex);
    return -1;
  }

  /* Clear key buffer before taking the tty, just in case... */
  pthread_mutex_lock(&handle->read_mutex);
  handle->keybuf_next = handle->keybuf_nb = 0;
  pthread_mutex_unlock(&handle->read_mutex);

  /* OK, Now we know where we are, so get the effective control of the terminal! */
  ttytreepath = getenv("WINDOWPATH");
  if (!ttytreepath && (getenv("DISPLAY") || getenv("WAYLAND_DISPLAY"))) {
    /* Cope with some DMs which are not setting WINDOWPATH (e.g. gdm 3.12) */
    ttytreepath = getenv("XDG_VTNR");

#ifdef HAVE_SD_SESSION_GET_VT
    if (!ttytreepath) {
      /* Fallback to asking logind */
      char *id;
      unsigned vtnr = 0;
      int ret;

      ret = sd_pid_get_session(0, &id);
      if (ret == 0 && id != NULL) {
	sd_session_get_vt(id, &vtnr);
	free(id);
      } else {
	char **sessions;
	/* Not even logind knows :/ we are probably logged in from gdm */
	int nsessions = sd_uid_get_sessions(getuid(), 0, &sessions);

	if (nsessions > 0) {
	  int i, chosen = -1;

	  for (i = 0; i < nsessions; i++) {
	    char *type;

	    ret = sd_session_get_type(sessions[i], &type);
	    if (ret == 0) {
	      if (strcmp(type, "tty") != 0 &&
	          strcmp(type, "unspecified") != 0) {
		/* x11, wayland or mir, so graphical.
		 * User normally has only one of them */
		if (chosen >= 0) {
		  /* Oops, several sessions? That is not supposed to happen, better
		   * choose none of them. */
		  chosen = -1;
		  break;
		}
		chosen = i;
	      }
	    }
	  }

	  if (chosen >= 0) sd_session_get_vt(sessions[i], &vtnr);

	  for (i = 0; i < nsessions; i++) free(sessions[i]);
	  free(sessions);
	}
      }

      if (vtnr) {
	size_t size = sizeof(vtnr)*3 + 1;
	ttytreepath=alloca(size);
	snprintf(ttytreepath, size, "%u", vtnr);
      }
    }
#endif /* HAVE_SD_SESSION_GET_VT */
  }

  nttytreepath = 0;
  if (ttytreepath) {
    /* Count ttys from path */
    for(cur = ttytreepath; *cur; nttytreepath++, cur = next+1) {
      strtol(cur,&next,0);
      if (next==cur) {
        syslog(LOG_WARNING, "Erroneous window path %s", ttytreepath);
        pthread_mutex_unlock(&handle->state_mutex);
        brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
        return -1;
      }
      if (*next==0) { nttytreepath++; break; }
    }
  }

  size = (1 + nttytreepath + nttys) * sizeof(uint32_t);
  size += 1 + (driverName ? strlen(driverName) : 0);
  packet = alloca(size);
  nbTtys = packet;
  t = nbTtys+1;
  *nbTtys = htonl(nttytreepath + nttys);

  if (ttytreepath) {
    /* First add ttys from environment path */
    for(cur=ttytreepath; *cur; nttytreepath++, cur=next+1) {
      ttypath=strtol(cur,&next,0);
      *t++ = htonl(ttypath);
      if (*next==0) break;
    }
  }

  /* Then add ttys from caller path */
  for (n=0; n<nttys; n++) *t++ = htonl(ttys[n]);

  p = (unsigned char *) t;
  if (driverName==NULL) n = 0; else n = strlen(driverName);
  if (n>BRLAPI_MAXNAMELENGTH) {
    pthread_mutex_unlock(&handle->state_mutex);
    brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
    return -1;
  }
  *p = n;
  p++;
  if (n) p = mempcpy(p, driverName, n);

  assert(p-(unsigned char *)packet == size);
  if ((res=brlapi__writePacketWaitForAck(handle,BRLAPI_PACKET_ENTERTTYMODE,packet,size)) == 0) {
    handle->state |= STCONTROLLINGTTY;
  }

  pthread_mutex_unlock(&handle->state_mutex);

  /* Determine default charset if application did not call setlocale.  */
#ifdef LC_GLOBAL_LOCALE
  const char *locale = setlocale(LC_CTYPE, NULL);

  if (!locale || !strcmp(locale, "C")) {
    /* Application did not call setlocale, try to load the current locale.  */
    locale_t default_locale = newlocale(LC_CTYPE_MASK, "", 0);
    if (default_locale) handle->default_locale = default_locale;
  }
#endif /* LC_GLOBAL_LOCALE */

  return res;
}

int BRLAPI_STDCALL brlapi_enterTtyModeWithPath(const int *ttys, int nttys, const char *how)
{
  return brlapi__enterTtyModeWithPath(&defaultHandle, ttys, nttys, how);
}

/* Function : brlapi_leaveTtyMode */
/* Gives back control of our tty to brltty */
int BRLAPI_STDCALL brlapi__leaveTtyMode(brlapi_handle_t *handle)
{
  int res;
  pthread_mutex_lock(&handle->state_mutex);
  if (!(handle->state & STCONTROLLINGTTY)) {
    brlapi_errno = BRLAPI_ERROR_ILLEGAL_INSTRUCTION;
    res = -1;
    goto out;
  }
  handle->brlx = 0; handle->brly = 0;
  res = brlapi__writePacketWaitForAck(handle,BRLAPI_PACKET_LEAVETTYMODE,NULL,0);
  handle->state &= ~STCONTROLLINGTTY;
out:
  pthread_mutex_unlock(&handle->state_mutex);
  return res;
}

int BRLAPI_STDCALL brlapi_leaveTtyMode(void)
{
  return brlapi__leaveTtyMode(&defaultHandle);
}

/* Function : brlapi_setFocus */
/* sends the current focus to brltty */
int BRLAPI_STDCALL brlapi__setFocus(brlapi_handle_t *handle, int tty)
{
  uint32_t utty;
  int res;
  utty = htonl(tty);
  pthread_mutex_lock(&handle->fileDescriptor_mutex);
  res = brlapi_writePacket(handle->fileDescriptor, BRLAPI_PACKET_SETFOCUS, &utty, sizeof(utty));
  pthread_mutex_unlock(&handle->fileDescriptor_mutex);
  return res;
}

int BRLAPI_STDCALL brlapi_setFocus(int tty)
{
  return brlapi__setFocus(&defaultHandle, tty);
}

static size_t getCharset(brlapi_handle_t *handle, void *buffer, int wide) {
  char *p = buffer;
  const char *start = p;
  const char *locale = setlocale(LC_CTYPE, NULL);

  if (wide
#ifdef __MINGW32__
      && CHECKPROC("ntdll.dll", wcslen)
#endif /* __MINGW32__ */
  ) {
    size_t length = strlen(WCHAR_CHARSET);
    *p++ = length;
    p = mempcpy(p, WCHAR_CHARSET, length);
  } else if ((locale && strcmp(locale, "C"))
#ifdef LC_GLOBAL_LOCALE
             || (handle->default_locale != LC_GLOBAL_LOCALE)
#endif /* LC_GLOBAL_LOCALE */
            ) {
    /* not default locale, tell charset to server */
#ifdef WINDOWS
    UINT CP = GetACP();
    if (!CP) CP = GetOEMCP();

    if (CP) {
      size_t length = sprintf(p+1, "CP%u", CP);
      *p++ = length;
      p += length;
    }
#elif defined(HAVE_NL_LANGINFO)
    char *language = nl_langinfo(CODESET);
    size_t length = strlen(language);

    *p++ = length;
    p = mempcpy(p, language, length);
#endif /* get non-wide character set */
  }

  return p-start;
}

/* Function : brlapi_writeText */
/* Writes a string to the braille display */
static int brlapi___writeText(brlapi_handle_t *handle, int cursor, const void *str, int wide)
{
  int dispSize = handle->brlx * handle->brly;
  brlapi_packet_t packet;
  brlapi_writeArgumentsPacket_t *wa = &packet.writeArguments;
  unsigned char *p = &wa->data;
  int res;
  size_t len;

#ifdef LC_GLOBAL_LOCALE
  locale_t old_locale = 0;

  if (handle->default_locale != LC_GLOBAL_LOCALE) {
    /* Temporarily load the default locale.  */
    old_locale = uselocale(handle->default_locale);
  }

#else /* LC_GLOBAL_LOCALE */
  setlocale(LC_CTYPE,NULL);
#endif /* LC_GLOBAL_LOCALE */

  wa->flags = BRLAPI_WF_REGION;
  *((uint32_t *) p) = htonl(1); p += sizeof(uint32_t);
  *((uint32_t *) p) = htonl(-dispSize); p += sizeof(uint32_t);

  if (str) {
    wa->flags |= BRLAPI_WF_TEXT;
#if defined(__MINGW32__)
    if (CHECKGETPROC("ntdll.dll", wcslen) && wide)
      len = sizeof(wchar_t) * wcslenProc(str);
#else /* __MINGW32__ */
    if (wide)
      len = sizeof(wchar_t) * wcslen(str);
#endif /* __MINGW32__ */
    else
      len = strlen(str);
    *((uint32_t *) p) = htonl(len); p += sizeof(uint32_t);
    memcpy(p, str, len);
    p += len;
  }
  if (cursor!=BRLAPI_CURSOR_LEAVE) {
    wa->flags |= BRLAPI_WF_CURSOR;
    *((uint32_t *) p) = htonl(cursor);
    p += sizeof(uint32_t);
  }

  if ((len = getCharset(handle, p , wide))) {
    wa->flags |= BRLAPI_WF_CHARSET;
    p += len;
  }

  wa->flags = htonl(wa->flags);
  pthread_mutex_lock(&handle->fileDescriptor_mutex);
  res = brlapi_writePacket(handle->fileDescriptor,BRLAPI_PACKET_WRITE,&packet,sizeof(wa->flags)+(p-&wa->data));
  pthread_mutex_unlock(&handle->fileDescriptor_mutex);

#ifdef LC_GLOBAL_LOCALE
  if (handle->default_locale != LC_GLOBAL_LOCALE) {
    /* Restore application locale */
    uselocale(old_locale);
  }
#endif /* LC_GLOBAL_LOCALE */

  return res;
}

#ifdef WINDOWS
int BRLAPI_STDCALL brlapi__writeTextWin(brlapi_handle_t *handle, int cursor, const void *str, int wide)
{
  return brlapi___writeText(handle, cursor, str, wide);
}

int BRLAPI_STDCALL brlapi_writeTextWin(int cursor, const void *str, int wide)
{
  return brlapi___writeText(&defaultHandle, cursor, str, wide);
}
#else /* WINDOWS */
int BRLAPI_STDCALL brlapi__writeText(brlapi_handle_t *handle, int cursor, const char *str)
{
  return brlapi___writeText(handle, cursor, str, 0);
}

int BRLAPI_STDCALL brlapi__writeWText(brlapi_handle_t *handle, int cursor, const wchar_t *str)
{
  return brlapi___writeText(handle, cursor, str, 1);
}

int BRLAPI_STDCALL brlapi_writeText(int cursor, const char *str)
{
  return brlapi__writeText(&defaultHandle, cursor, str);
}

int BRLAPI_STDCALL brlapi_writeWText(int cursor, const wchar_t *str)
{
  return brlapi__writeWText(&defaultHandle, cursor, str);
}
#endif /* WINDOWS */

/* Function : brlapi_writeDots */
/* Writes dot-matrix to the braille display */
int BRLAPI_STDCALL brlapi__writeDots(brlapi_handle_t *handle, const unsigned char *dots)
{
  brlapi_writeArguments_t wa = BRLAPI_WRITEARGUMENTS_INITIALIZER;
  unsigned int size = handle->brlx * handle->brly;

  if (size == 0) {
    brlapi_errno=BRLAPI_ERROR_INVALID_PARAMETER;
    return -1;
  }

  unsigned char andMask[size];
  memset(andMask, 0, size);
  wa.andMask = andMask;

  unsigned char orMask[size];
  memcpy(orMask, dots, size);
  wa.orMask = orMask;

  char text[(size * 3) + 1];
  wa.text = text;
  wa.charset = "utf-8";

  {
    /* Pass a UTF8-encoded string of the braille characters as the text.
     *
     * The Unicode row for the braille pattern characters is U+2800.
     * Each of the eight dots is represented by a bit in the low-order byte:
     * Dot1 by 0X01, Dot2 by 0X02, ..., Dot7 by 0X40, and Dot8 by 0X80.
     *
     * The UTF-8 template for the Unicode braille row is: 0XE2, 0XA0, 0X80.
     * Dots 1-6 are the low-order six bits of the last (0X80) byte.
     * Dots 7-8 are the low-order two bits of the middle (0XA0) byte.
     */

    char *byte = text;
    const unsigned char *cell = dots;
    const unsigned char *end = cell + size;

    while (cell < end) {
      *byte++ = 0XE2;
      *byte++ = 0XA0 | ((*cell >> 6) & 0X3); // dots 7-8
      *byte++ = 0X80 | (*cell & 0X3F); // dots 1-6
      cell += 1;
    }

    *byte = 0;
    wa.textSize = byte - text;
  }

  wa.regionBegin = 1;
  wa.regionSize = -size;
  wa.cursor = BRLAPI_CURSOR_OFF;

  return brlapi__write(handle, &wa);
}

int BRLAPI_STDCALL brlapi_writeDots(const unsigned char *dots)
{
  return brlapi__writeDots(&defaultHandle, dots);
}

/* Function : brlapi_write */
/* Extended writes on braille displays */
#ifdef WINDOWS
int BRLAPI_STDCALL brlapi__writeWin(brlapi_handle_t *handle, const brlapi_writeArguments_t *s, int wide)
#else /* WINDOWS */
int brlapi__write(brlapi_handle_t *handle, const brlapi_writeArguments_t *s)
#endif /* WINDOWS */
{
  int dispSize = handle->brlx * handle->brly;
  unsigned int rbeg, strLen;
  int rsiz;
  brlapi_packet_t packet;
  brlapi_writeArgumentsPacket_t *wa = &packet.writeArguments;
  unsigned char *p = &wa->data;
  unsigned char *end = (unsigned char*) &packet.data[sizeof(packet)];
  int res;
#ifndef WINDOWS
  int wide = 0;
#endif /* WINDOWS */
  wa->flags = 0;
  if (s==NULL) goto send;
  rbeg = s->regionBegin;
  rsiz = s->regionSize;
  if (rbeg || rsiz) {
    if (rsiz == 0) return 0;
    wa->flags |= BRLAPI_WF_REGION;
    *((uint32_t *) p) = htonl(rbeg); p += sizeof(uint32_t);
    *((uint32_t *) p) = htonl((int32_t) rsiz); p += sizeof(uint32_t);
  } else {
    /* DEPRECATED */
    rsiz = -dispSize;
  }
  if (rsiz < 0)
    rsiz = -rsiz;
  if (s->text) {
    if (s->textSize != -1)
      strLen = s->textSize;
    else
#if defined(__CYGWIN__)
      if (wide)
	strLen = sizeof(wchar_t) * wcslen((wchar_t *) s->text);
      else
#elif defined(__MINGW32__)
      if (CHECKGETPROC("ntdll.dll", wcslen) && wide)
	strLen = sizeof(wchar_t) * wcslenProc((wchar_t *) s->text);
      else
#endif /* windows wide string length */
	strLen = strlen(s->text);
    *((uint32_t *) p) = htonl(strLen); p += sizeof(uint32_t);
    wa->flags |= BRLAPI_WF_TEXT;
    if (p + strLen > end) {
      brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
      return -1;
    }
    memcpy(p, s->text, strLen);
    p += strLen;
  }
  if (s->andMask) {
    wa->flags |= BRLAPI_WF_ATTR_AND;
    if (p + rsiz > end) {
      brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
      return -1;
    }
    memcpy(p, s->andMask, rsiz);
    p += rsiz;
  }
  if (s->orMask) {
    wa->flags |= BRLAPI_WF_ATTR_OR;
    if (p + rsiz > end) {
      brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
      return -1;
    }
    memcpy(p, s->orMask, rsiz);
    p += rsiz;
  }
  if ((s->cursor>=0) && (s->cursor<=dispSize)) {
    wa->flags |= BRLAPI_WF_CURSOR;
    if (p + sizeof(uint32_t) > end) {
      brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
      return -1;
    }
    *((uint32_t *) p) = htonl(s->cursor);
    p += sizeof(uint32_t);
  } else if (s->cursor != BRLAPI_CURSOR_LEAVE) {
    brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
    return -1;
  }

  if (s->charset) {
    if (!*s->charset) {
#ifdef LC_GLOBAL_LOCALE
      locale_t old_locale = 0;

      if (handle->default_locale != LC_GLOBAL_LOCALE) {
	/* Temporarily load the default locale.  */
	old_locale = uselocale(handle->default_locale);
      }
#endif /* LC_GLOBAL_LOCALE */

      if ((strLen = getCharset(handle, p, wide))) {
	wa->flags |= BRLAPI_WF_CHARSET;
	p += strLen;
      }

#ifdef LC_GLOBAL_LOCALE
      if (handle->default_locale != LC_GLOBAL_LOCALE) {
	/* Restore application locale */
	uselocale(old_locale);
      }
#endif /* LC_GLOBAL_LOCALE */
    } else {
      strLen = strlen(s->charset);
      *p++ = strLen;
      wa->flags |= BRLAPI_WF_CHARSET;
      if (p + strLen > end) {
	brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
	return -1;
      }

      memcpy(p, s->charset, strLen);
      p += strLen;
    }
  }

send:
  wa->flags = htonl(wa->flags);
  pthread_mutex_lock(&handle->fileDescriptor_mutex);
  res = brlapi_writePacket(handle->fileDescriptor,BRLAPI_PACKET_WRITE,&packet,sizeof(wa->flags)+(p-&wa->data));
  pthread_mutex_unlock(&handle->fileDescriptor_mutex);
  return res;
}

#ifdef WINDOWS
int BRLAPI_STDCALL brlapi_writeWin(const brlapi_writeArguments_t *s, int wide)
{
  return brlapi__writeWin(&defaultHandle, s, wide);
}
#else /* WINDOWS */
int brlapi_write(const brlapi_writeArguments_t *s)
{
  return brlapi__write(&defaultHandle, s);
}
#endif /* WINDOWS */

/* Function : brlapi_readKey */
/* Reads a key from the braille keyboard */
int BRLAPI_STDCALL brlapi__readKeyWithTimeout(brlapi_handle_t *handle, int timeout_ms, brlapi_keyCode_t *code)
{
  ssize_t res;
  uint32_t buf[2];

  pthread_mutex_lock(&handle->state_mutex);
  if (!(handle->state & STCONTROLLINGTTY)) {
    pthread_mutex_unlock(&handle->state_mutex);
    brlapi_errno = BRLAPI_ERROR_ILLEGAL_INSTRUCTION;
    return -1;
  }
  pthread_mutex_unlock(&handle->state_mutex);

  pthread_mutex_lock(&handle->read_mutex);
  if (handle->keybuf_nb>0) {
    *code=handle->keybuf[handle->keybuf_next];
    handle->keybuf_next=(handle->keybuf_next+1)%BRL_KEYBUF_SIZE;
    handle->keybuf_nb--;
    pthread_mutex_unlock(&handle->read_mutex);
    return 1;
  }
  pthread_mutex_unlock(&handle->read_mutex);

  pthread_mutex_lock(&handle->key_mutex);
  res = brlapi__waitForPacket(handle,BRLAPI_PACKET_KEY, buf, sizeof(buf), TRY_WAIT_FOR_EXPECTED_PACKET, timeout_ms);
  pthread_mutex_unlock(&handle->key_mutex);
  if (res == -3) {
    if (timeout_ms == 0) return 0;
    brlapi_libcerrno = EINTR;
    brlapi_errno = BRLAPI_ERROR_LIBCERR;
    brlapi_errfun = "waitForPacket";
    return -1;
  }
  if (res == -4) {
    /* Timeout */
    return 0;
  }
  if (res < 0) return -1;
  *code = brlapi_packetToKeyCode(buf);
  return 1;
}

int BRLAPI_STDCALL brlapi_readKeyWithTimeout(int timeout_ms, brlapi_keyCode_t *code)
{
  return brlapi__readKeyWithTimeout(&defaultHandle, timeout_ms, code);
}

int BRLAPI_STDCALL brlapi__readKey(brlapi_handle_t *handle, int block, brlapi_keyCode_t *code)
{
  return brlapi__readKeyWithTimeout(handle, block ? -1 : 0, code);
}

int BRLAPI_STDCALL brlapi_readKey(int block, brlapi_keyCode_t *code)
{
  return brlapi__readKeyWithTimeout(&defaultHandle, block ? -1 : 0, code);
}

typedef struct {
  brlapi_keyCode_t code;
  const char *name;
} brlapi_keyEntry_t;

static const brlapi_keyEntry_t brlapi_keyTable[] = {
#include "brlapi_keytab.auto.h"

  {.code=0X0000U, .name="LATIN1"},
  {.code=0X0100U, .name="LATIN2"},
  {.code=0X0200U, .name="LATIN3"},
  {.code=0X0300U, .name="LATIN4"},
  {.code=0X0400U, .name="KATAKANA"},
  {.code=0X0500U, .name="ARABIC"},
  {.code=0X0600U, .name="CYRILLIC"},
  {.code=0X0700U, .name="GREEK"},
  {.code=0X0800U, .name="TECHNICAL"},
  {.code=0X0900U, .name="SPECIAL"},
  {.code=0X0A00U, .name="PUBLISHING"},
  {.code=0X0B00U, .name="APL"},
  {.code=0X0C00U, .name="HEBREW"},
  {.code=0X0D00U, .name="THAI"},
  {.code=0X0E00U, .name="KOREAN"},
  {.code=0X1200U, .name="LATIN8"},
  {.code=0X1300U, .name="LATIN9"},
  {.code=0X1400U, .name="ARMENIAN"},
  {.code=0X1500U, .name="GEORGIAN"},
  {.code=0X1600U, .name="CAUCASUS"},
  {.code=0X1E00U, .name="VIETNAMESE"},
  {.code=0X2000U, .name="CURRENCY"},
  {.code=0XFD00U, .name="3270"},
  {.code=0XFE00U, .name="XKB"},
  {.code=0XFF00U, .name="MISCELLANY"},
  {.code=0X01000000U, .name="UNICODE"},

  {.name=NULL}
};

int BRLAPI_STDCALL
brlapi_expandKeyCode (brlapi_keyCode_t keyCode, brlapi_expandedKeyCode_t *ekc) {
  int argumentWidth = brlapi_getArgumentWidth(keyCode);

  if (argumentWidth != -1) {
    unsigned int argumentMask = (1 << argumentWidth) - 1;
    brlapi_keyCode_t type = keyCode & BRLAPI_KEY_TYPE_MASK;
    brlapi_keyCode_t code = keyCode & BRLAPI_KEY_CODE_MASK;

    ekc->type = type;
    ekc->command = (code & ~argumentMask);
    ekc->argument = code & argumentMask;
    ekc->flags = (keyCode & BRLAPI_KEY_FLAGS_MASK) >> BRLAPI_KEY_FLAGS_SHIFT;

    return 0;
  }

  return -1;
}

int BRLAPI_STDCALL
brlapi_describeKeyCode (brlapi_keyCode_t keyCode, brlapi_describedKeyCode_t *dkc) {
  brlapi_expandedKeyCode_t ekc;
  int result = brlapi_expandKeyCode(keyCode, &ekc);

  if (result != -1) {
    unsigned int argument = ekc.argument;
    unsigned int codeWithoutArgument = ekc.type | ekc.command;
    unsigned int codeWithArgument = codeWithoutArgument | argument;
    const brlapi_keyEntry_t *keyWithoutArgument = NULL;
    const brlapi_keyEntry_t *key = brlapi_keyTable;

    while (key->name) {
      if (codeWithArgument == key->code) {
        argument = 0;
        goto found;
      }

      if (codeWithoutArgument == key->code)
        if (!keyWithoutArgument)
          keyWithoutArgument = key;

      ++key;
    }

    if (keyWithoutArgument) {
      key = keyWithoutArgument;
      goto found;
    }

    brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
    result = -1;
    goto done;

  found:
    dkc->command = key->name;
    dkc->argument = argument;
    dkc->values = ekc;

    switch (ekc.type) {
      case BRLAPI_KEY_TYPE_SYM: dkc->type = "SYM";     break;
      case BRLAPI_KEY_TYPE_CMD: dkc->type = "CMD";     break;
      default:                  dkc->type = "UNKNOWN"; break;
    }

#define FLAG(name) if (keyCode & BRLAPI_KEY_FLG_##name) dkc->flag[dkc->flags++] = #name
    dkc->flags = 0;

    FLAG(SHIFT);
    FLAG(UPPER);
    FLAG(CONTROL);
    FLAG(META);
    FLAG(ALTGR);
    FLAG(GUI);

    switch (ekc.type) {
      case BRLAPI_KEY_TYPE_CMD:
        switch (ekc.command & BRLAPI_KEY_CMD_BLK_MASK) {
          case BRLAPI_KEY_CMD_PASSDOTS:
            break;

          case BRLAPI_KEY_CMD_PASSXT:
          case BRLAPI_KEY_CMD_PASSAT:
          case BRLAPI_KEY_CMD_PASSPS2:
            FLAG(KBD_RELEASE);
            FLAG(KBD_EMUL0);
            FLAG(KBD_EMUL1);
            break;

          default:
            FLAG(TOGGLE_ON);
            FLAG(TOGGLE_OFF);
            FLAG(MOTION_ROUTE);
            FLAG(MOTION_SCALED);
            FLAG(MOTION_TOLEFT);
            break;
        }
        break;
    }
#undef FLAG
  }

done:
  return result;
}

/* Function : ignore_accept_key_range */
/* Common tasks for ignoring and unignoring key ranges */
/* what = 0 for ignoring !0 for unignoring */
static int ignore_accept_key_ranges(brlapi_handle_t *handle, int what, const brlapi_range_t ranges[], unsigned int n)
{
  uint32_t ints[n][4];
  unsigned int i;

  for (i=0; i<n; i++) {
    ints[i][0] = htonl(ranges[i].first >> 32);
    ints[i][1] = htonl(ranges[i].first & 0xffffffff);
    ints[i][2] = htonl(ranges[i].last >> 32);
    ints[i][3] = htonl(ranges[i].last & 0xffffffff);
  };

  if (brlapi__writePacketWaitForAck(handle,(what ? BRLAPI_PACKET_ACCEPTKEYRANGES : BRLAPI_PACKET_IGNOREKEYRANGES),ints,n*2*sizeof(brlapi_keyCode_t)))
    return -1;
  return 0;
}

/* Function : ignore_accept_keys */
/* Common tasks for ignoring and unignoring keys */
/* what = 0 for ignoring !0 for unignoring */
static int ignore_accept_keys(brlapi_handle_t *handle, int what, brlapi_rangeType_t r, const brlapi_keyCode_t *code, unsigned int n)
{
  if (!n) {
    if (r != brlapi_rangeType_all) {
      brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
      return -1;
    }
    brlapi_range_t range = { .first = 0, .last = BRLAPI_KEY_MAX };
    return ignore_accept_key_ranges(handle, what, &range, 1);
  } else {
    brlapi_range_t ranges[n];
    unsigned int i;
    brlapi_keyCode_t mask;

    for (i=0; i<n; i++) {
      if (brlapi_getKeyrangeMask(r, code[i], &mask))
	return -1;
      if (code[i] & mask) {
	brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
	return -1;
      }
      ranges[i].first = code[i];
      ranges[i].last = code[i] | mask;
    }
    return ignore_accept_key_ranges(handle, what, ranges, n);
  }
}

/* Function : brlapi_acceptKeyRanges */
int BRLAPI_STDCALL brlapi__acceptKeyRanges(brlapi_handle_t *handle, const brlapi_range_t ranges[], unsigned int n)
{
  return ignore_accept_key_ranges(handle, !0, ranges, n);
}

int BRLAPI_STDCALL brlapi_acceptKeyRanges(const brlapi_range_t ranges[], unsigned int n)
{
  return brlapi__acceptKeyRanges(&defaultHandle, ranges, n);
}

/* Function : brlapi_acceptKeys */
int BRLAPI_STDCALL brlapi__acceptKeys(brlapi_handle_t *handle, brlapi_rangeType_t r, const brlapi_keyCode_t *code, unsigned int n)
{
  return ignore_accept_keys(handle, !0, r, code, n);
}

int BRLAPI_STDCALL brlapi_acceptKeys(brlapi_rangeType_t r, const brlapi_keyCode_t *code, unsigned int n)
{
  return brlapi__acceptKeys(&defaultHandle, r, code, n);
}

/* Function : brlapi_ignoreKeyRanges */
int BRLAPI_STDCALL brlapi__ignoreKeyRanges(brlapi_handle_t *handle, const brlapi_range_t ranges[], unsigned int n)
{
  return ignore_accept_key_ranges(handle, 0, ranges, n);
}

int BRLAPI_STDCALL brlapi_ignoreKeyRanges(const brlapi_range_t ranges[], unsigned int n)
{
  return brlapi__ignoreKeyRanges(&defaultHandle, ranges, n);
}

/* Function : brlapi_ignoreKeys */
int BRLAPI_STDCALL brlapi__ignoreKeys(brlapi_handle_t *handle, brlapi_rangeType_t r, const brlapi_keyCode_t *code, unsigned int n)
{
  return ignore_accept_keys(handle, 0, r, code, n);
}

int BRLAPI_STDCALL brlapi_ignoreKeys(brlapi_rangeType_t r, const brlapi_keyCode_t *code, unsigned int n)
{
  return brlapi__ignoreKeys(&defaultHandle, r, code, n);
}

/* Error code handling */

/* brlapi_errlist: error messages */
const char *brlapi_errlist[] = {
  [0]                                = "Success",
  [BRLAPI_ERROR_NOMEM]               = "Insufficient memory",
  [BRLAPI_ERROR_TTYBUSY]             = "Tty is busy",
  [BRLAPI_ERROR_DEVICEBUSY]          = "Device is busy",
  [BRLAPI_ERROR_UNKNOWN_INSTRUCTION] = "Unknown instruction",
  [BRLAPI_ERROR_ILLEGAL_INSTRUCTION] = "Illegal instruction",
  [BRLAPI_ERROR_INVALID_PARAMETER]   = "Invalid parameter",
  [BRLAPI_ERROR_INVALID_PACKET]      = "Invalid packet",
  [BRLAPI_ERROR_CONNREFUSED]         = "Connection refused",
  [BRLAPI_ERROR_OPNOTSUPP]           = "Operation not supported",
  [BRLAPI_ERROR_GAIERR]              = "getaddrinfo() error",
  [BRLAPI_ERROR_LIBCERR]             = "libc error",
  [BRLAPI_ERROR_UNKNOWNTTY]          = "Can't determine tty number",
  [BRLAPI_ERROR_PROTOCOL_VERSION]    = "Bad protocol version",
  [BRLAPI_ERROR_EOF]                 = "Unexpected end of file",
  [BRLAPI_ERROR_EMPTYKEY]            = "Key file is empty",
  [BRLAPI_ERROR_DRIVERERROR]         = "Driver error",
  [BRLAPI_ERROR_AUTHENTICATION]      = "Authentication failed",
  [BRLAPI_ERROR_READONLY_PARAMETER]  = "Parameter can not be changed",
};

/* brlapi_nerr: number of error codes */
const int brlapi_nerr = (sizeof(brlapi_errlist)/sizeof(char*));

/* _brlapi_strerror_cpy: store constant-string error message */
static size_t _brlapi_strerror_cpy(char *buf, size_t buflen, const char *msg)
{
  size_t totsize = strlen(msg);

  if (buflen == 0)
    return totsize;

  if (buflen > totsize+1) {
    buflen = totsize+1;
  }
  memcpy(buf, msg, buflen-1);
  buf[buflen-1] = '\0';

  return totsize;
}

/* _brlapi_strerror_snprintf: store formatted error message */
static size_t _brlapi_strerror_snprintf(char *buf, size_t buflen, const char *fmt, ...)
{
  va_list va;
  int totsize;

  va_start(va, fmt);
  totsize = vsnprintf(buf, buflen, fmt, va);
  va_end(va);

  return totsize;
}

/* brlapi_strerror_r: store error message */
size_t BRLAPI_STDCALL brlapi_strerror_r(const brlapi_error_t *error, char *buf, size_t buflen)
{
  if (error->brlerrno>=brlapi_nerr)
    return _brlapi_strerror_cpy(buf, buflen, "Unknown error");
  else if (error->brlerrno==BRLAPI_ERROR_GAIERR) {
#if defined(EAI_SYSTEM)
    if (error->gaierrno == EAI_SYSTEM)
      return _brlapi_strerror_snprintf(buf,buflen,"resolve: %s", strerror(error->libcerrno));
    else
#endif /* EAI_SYSTEM */
      return _brlapi_strerror_snprintf(buf,buflen,"resolve: "
#if defined(HAVE_GETADDRINFO)
#if defined(HAVE_GAI_STRERROR)
	"%s", gai_strerror(error->gaierrno)
#else
	"%d", error->gaierrno
#endif
#elif defined(HAVE_HSTRERROR) && !defined(__MINGW32__)
	"%s", hstrerror(error->gaierrno)
#else
	"%x", error->gaierrno
#endif
	);
  }
  else if (error->brlerrno==BRLAPI_ERROR_LIBCERR) {
    return _brlapi_strerror_snprintf(buf,buflen,"%s: %s", error->errfun?error->errfun:"(null)", strerror(error->libcerrno));
  } else
    return _brlapi_strerror_cpy(buf, buflen, brlapi_errlist[error->brlerrno]);
}

/* brlapi_strerror: return error message */
const char * BRLAPI_STDCALL brlapi_strerror(const brlapi_error_t *error)
{
  static char errmsg[128];
  brlapi_strerror_r(error, errmsg, sizeof(errmsg));
  return errmsg;
}

/* brlapi_perror: error message printing */
void BRLAPI_STDCALL brlapi_perror(const char *s)
{
  fprintf(stderr,"%s: %s\n",s,brlapi_strerror(&brlapi_error));
}

/* XXX functions mustn't use brlapi_errno after this since it #undefs it XXX */

#ifdef brlapi_error
#undef brlapi_error
#endif /* brlapi_error */

brlapi_error_t brlapi_error;
static int pthread_error_ok;

/* we need a per-thread errno variable, thanks to pthread_keys */
static pthread_key_t error_key;

/* the key must be created at most once */
static pthread_once_t error_key_once = PTHREAD_ONCE_INIT;

static void error_key_free(void *key)
{
  free(key);
}

static void error_key_alloc(void)
{
  pthread_error_ok=!pthread_key_create(&error_key, error_key_free);
}

/* how to get per-thread errno variable. This will be called by the macro
 * brlapi_errno */
brlapi_error_t * BRLAPI_STDCALL brlapi_error_location(void)
{
  brlapi_error_t *errorp;
#ifndef WINDOWS
  if (pthread_once && pthread_key_create) {
#endif /* WINDOWS */
    pthread_once(&error_key_once, error_key_alloc);
    if (pthread_error_ok) {
      if ((errorp=(brlapi_error_t *) pthread_getspecific(error_key)))
        /* normal case */
        return errorp;
      else
        /* on the first time, must allocate it */
        if ((errorp=malloc(sizeof(*errorp))) && !pthread_setspecific(error_key,errorp)) {
	  memset(errorp,0,sizeof(*errorp));
          return errorp;
	}
    }
#ifndef WINDOWS
  }
#endif /* WINDOWS */
  /* fall-back: shared errno :/ */
  return &brlapi_error;
}

brlapi__exceptionHandler_t BRLAPI_STDCALL brlapi__setExceptionHandler(brlapi_handle_t *handle, brlapi__exceptionHandler_t new)
{
  brlapi__exceptionHandler_t tmp;
  pthread_mutex_lock(&handle->exceptionHandler_mutex);
  tmp = handle->exceptionHandler.withHandle;
  if (new!=NULL) handle->exceptionHandler.withHandle = new;
  pthread_mutex_unlock(&handle->exceptionHandler_mutex);
  return tmp;
}

brlapi_exceptionHandler_t BRLAPI_STDCALL brlapi_setExceptionHandler(brlapi_exceptionHandler_t new)
{
  brlapi_exceptionHandler_t tmp;
  pthread_mutex_lock(&defaultHandle.exceptionHandler_mutex);
  tmp = defaultHandle.exceptionHandler.withoutHandle;
  if (new!=NULL) defaultHandle.exceptionHandler.withoutHandle = new;
  pthread_mutex_unlock(&defaultHandle.exceptionHandler_mutex);
  return tmp;
}

int BRLAPI_STDCALL brlapi__strexception(brlapi_handle_t *handle, char *buf, size_t n, int err, brlapi_packetType_t type, const void *packet, size_t size)
{
  int chars = 128; /* Number of bytes to dump */
  char hexString[3*chars+1];
  int i, nbChars = MIN(chars, size);
  char *p = hexString;
  brlapi_error_t error = { .brlerrno = err };
  for (i=0; i<nbChars; i++)
    p += sprintf(p, "%02x ", ((unsigned char *) packet)[i]);
  p--; /* Don't keep last space */
  *p = '\0';
  return snprintf(buf, n, "%s on %s request of size %d (%s)",
    brlapi_strerror(&error), brlapi_getPacketTypeName(type), (int)size, hexString);
}

int BRLAPI_STDCALL brlapi_strexception(char *buf, size_t n, int err, brlapi_packetType_t type, const void *packet, size_t size)
{
  return brlapi__strexception(&defaultHandle, buf, n, err, type, packet, size);
}

void BRLAPI_STDCALL brlapi__defaultExceptionHandler(brlapi_handle_t *handle, int err, brlapi_packetType_t type, const void *packet, size_t size)
{
  char str[0X100];
  brlapi_strexception(str,0X100, err, type, packet, size);
  fprintf(stderr, "BrlAPI exception: %s\nYou may wish to add the -ldebug option to the brltty command line in order to get additional information in the system log\n", str);
  fprintf(stderr, "Crashing the client now. You may want to use brlapi_setExceptionHandler to define your own exception handling.\n");
  abort();
}

void BRLAPI_STDCALL brlapi_defaultExceptionHandler(int err, brlapi_packetType_t type, const void *packet, size_t size)
{
  brlapi__defaultExceptionHandler(&defaultHandle, err, type, packet, size);
}

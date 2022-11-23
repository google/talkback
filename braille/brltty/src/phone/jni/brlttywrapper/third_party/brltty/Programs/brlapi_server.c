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

/* api_server.c : Main file for BrlApi server */

#define SERVER_SOCKET_LIMIT 4
#define SERVER_SELECT_TIMEOUT 1
#define UNAUTH_LIMIT 5
#define UNAUTH_TIMEOUT 30
#define OUR_STACK_MIN 0X10000

#ifndef PTHREAD_STACK_MIN
#define PTHREAD_STACK_MIN OUR_STACK_MIN
#endif /* PTHREAD_STACK_MIN */

#define RELEASE "BrlAPI Server: release " BRLAPI_RELEASE
#define COPYRIGHT "   Copyright (C) 2002-2019 by Sébastien Hinderer <Sebastien.Hinderer@ens-lyon.org>, \
Samuel Thibault <samuel.thibault@ens-lyon.org>"

#include "prologue.h"

#include <stdio.h>
#include <stddef.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <time.h>

#ifdef HAVE_ICONV_H
#include <iconv.h>
#endif /* HAVE_ICONV_H */

#ifdef __MINGW32__
#include "system_windows.h"
#include "win_pthread.h"

#else /* __MINGW32__ */
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>

#ifdef __ANDROID__
#ifndef PAGE_SIZE
#include <asm/page.h>
#endif /* PAGE_SIZE */
#endif /* __ANDROID__ */

#include <pthread.h>

#ifdef HAVE_SYS_SELECT_H
#include <sys/select.h>
#else /* HAVE_SYS_SELECT_H */
#include <sys/time.h>
#endif /* HAVE_SYS_SELECT_H */
#endif /* __MINGW32__ */

#define BRLAPI_NO_DEPRECATED
#include "brlapi.h"
#include "brlapi_protocol.h"
#include "brlapi_keyranges.h"

#include "cmd_brlapi.h"
#include "brl_cmds.h"
#include "brl_utils.h"
#include "embed.h"
#include "ttb.h"
#include "core.h"
#include "api_server.h"
#include "report.h"
#include "log.h"
#include "addresses.h"
#include "prefs.h"
#include "file.h"
#include "parse.h"
#include "timing.h"
#include "auth.h"
#include "io_misc.h"
#include "scr.h"
#include "charset.h"
#include "async_event.h"
#include "async_signal.h"
#include "thread.h"
#include "blink.h"

#ifdef __MINGW32__
#define LogSocketError(msg) logWindowsSocketError(msg)
#else /* __MINGW32__ */
#define LogSocketError(msg) logSystemError(msg)
#endif /* __MINGW32__ */

#ifdef __CYGWIN__
#undef PF_LOCAL
#endif /* __CYGWIN__ */

typedef enum {
  PARM_AUTH,
  PARM_HOST,
  PARM_STACKSIZE
} Parameters;

const char *const api_parameters[] = { "auth", "host", "stacksize", NULL };

static size_t stackSize;
static AsyncEvent *flushEvent;

#define WERR(x, y, ...) do { \
  logMessage(LOG_ERR, "writing error %d to %"PRIfd, y, x); \
  logMessage(LOG_ERR, __VA_ARGS__); \
  writeError(x, y); \
} while(0)
#define WEXC(x, y, type, packet, size, ...) do { \
  logMessage(LOG_ERR, "writing exception %d to %"PRIfd, y, x); \
  logMessage(LOG_ERR, __VA_ARGS__); \
  writeException(x, y, type, packet, size); \
} while(0)

/* These CHECK* macros check whether a condition is true, and, if not, */
/* send back either a non-fatal error, or an exception */
#define CHECKERR(condition, error, msg) \
if (!( condition )) { \
  WERR(c->fd, error, "%s not met: " msg, #condition); \
  return 0; \
} else { }
#define CHECKEXC(condition, error, msg) \
if (!( condition )) { \
  WEXC(c->fd, error, type, packet, size, "%s not met: " msg, #condition); \
  return 0; \
} else { }

#ifdef brlapi_error
#undef brlapi_error
#endif

static brlapi_error_t brlapiserver_error;
#define brlapi_error brlapiserver_error

#define BRLAPI(fun) brlapiserver_ ## fun
#include "brlapi_common.h"

/** ask for \e brltty commands */
#define BRL_COMMANDS 0
/** ask for raw driver keycodes */
#define BRL_KEYCODES 1

/****************************************************************************/
/** GLOBAL TYPES AND VARIABLES                                              */
/****************************************************************************/

extern char *opt_brailleParameters;
extern char *cfg_brailleParameters;

typedef struct {
  unsigned int cursor;
  wchar_t *text;
  unsigned char *andAttr;
  unsigned char *orAttr;
} BrailleWindow;

typedef enum { TODISPLAY, EMPTY } BrlBufState;

typedef struct Connection {
  struct Connection *prev, *next;
  FileDescriptor fd;
  int auth;
  struct Tty *tty;
  int raw, suspend;
  unsigned int how; /* how keys must be delivered to clients */
  BrailleWindow brailleWindow;
  BrlBufState brlbufstate;
  pthread_mutex_t brailleWindowMutex;
  KeyrangeList *acceptedKeys;
  pthread_mutex_t acceptedKeysMutex;
  time_t upTime;
  Packet packet;
} Connection;

typedef struct Tty {
  int focus;
  int number;
  struct Connection *connections;
  struct Tty *father; /* father */
  struct Tty **prevnext,*next; /* siblings */
  struct Tty *subttys; /* children */
} Tty;

/* Pointer to the connection accepter thread */
static pthread_t serverThread; /* server */
static pthread_t socketThreads[SERVER_SOCKET_LIMIT]; /* socket binding threads */
static int running; /* should threads be running? */
static char **socketHosts = NULL; /* socket local hosts */
static struct socketInfo {
  int addrfamily;
  FileDescriptor fd;
  char *host;
  char *port;
#ifdef __MINGW32__
  OVERLAPPED overl;
#endif /* __MINGW32__ */
} socketInfo[SERVER_SOCKET_LIMIT]; /* information for cleaning sockets */

static int serverSocketCount; /* number of sockets */
static int serverSocketsPending; /* number of sockets not opened yet */
pthread_mutex_t serverSocketsMutex;

/* Protects from connection addition / remove from the server thread */
pthread_mutex_t apiConnectionsMutex;

/* Protects the real driver's functions */
pthread_mutex_t apiDriverMutex;

/* Which connection currently has raw mode */
pthread_mutex_t apiRawMutex;
static Connection *rawConnection = NULL;
static Connection *suspendConnection = NULL;

/* mutex lock order is as follows:
 * 1. apiConnectionsMutex
 * 2. apiRawMutex
 * 3. acceptedKeysMutex or brailleWindowMutex
 * 4. apiDriverMutex
*/

static Tty notty;
static Tty ttys;

static unsigned int unauthConnections;
static unsigned int unauthConnLog = 0;

/*
 * API states are
 * - stopped: No thread is running (hence no connection allowed).
 *   started: The server thread is running, accepting connections.
 * - unlinked: TrueBraille == &noBraille: API has no control on the driver.
 *   linked: TrueBraille != &noBraille: API controls the driver.
 * - core suspended: The core asked to keep the device closed.
 *   core active: The core asked has opened the device.
 * - device closed: API keeps the device closed.
 *   device opened: API has really opened the device.
 *
 * Combinations can be:
 * - initial: API stopped, unlinked, core suspended and device closed.
 * - started: API started, unlinked, core suspended and device closed.
 * - normal: API started, linked, core active and device opened.
 * - core suspend: API started, linked, core suspended but device opened.
 *   (BrlAPI-only output).
 * - full suspend: API started, linked, core suspended and device closed.
 * - brltty control: API started, core active and device opened, but unlinked.
 *
 * Other states don't make sense, since
 * - api needs to be started before being linked,
 * - the device can't remain closed if core is active,
 * - the core must resume before unlinking api (so as to let the api re-open
 *   the driver if necessary)
 */

/* Pointer to subroutines of the real braille driver, &noBraille when API is
 * unlinked  */
static const BrailleDriver *trueBraille;
static BrailleDriver ApiBraille;

/* Identication of the REAL braille driver currently used */

/* The following variable contains the size of the braille display */
/* stored as a pair of _network_-formatted integers */
static uint32_t displayDimensions[2] = { 0, 0 };
static unsigned int displaySize = 0;

static BrailleDisplay *disp; /* Parameter to pass to braille drivers */

static int coreActive; /* Whether core is active */
static int offline; /* Whether device is offline */
static int driverConstructed; /* Whether device is really opened, protected by apiDriverMutex */
static int driverConstructing; /* Whether device being constructed, protected by apiDriverMutex */
static wchar_t *coreWindowText; /* Last text written by the core */
static unsigned char *coreWindowDots; /* Last dots written by the core */
static int coreWindowCursor; /* Last cursor position set by the core */
pthread_mutex_t apiSuspendMutex; /* Protects use of driverConstructed state */

static const char *auth = BRLAPI_DEFAUTH;
static AuthDescriptor *authDescriptor;

#ifdef __MINGW32__
static WSADATA wsadata;
#endif /* __MINGW32__ */

static unsigned char cursorOverlay = 0;

/****************************************************************************/
/** SOME PROTOTYPES                                                        **/
/****************************************************************************/

extern void processParameters(char ***values, const char *const *names, const char *description, char *optionParameters, char *configuredParameters, const char *environmentVariable);
static int initializeAcceptedKeys(Connection *c, int how);
static void brlResize(BrailleDisplay *brl);

/****************************************************************************/
/** DRIVER CAPABILITIES                                                    **/
/****************************************************************************/

/* Function : isRawCapable */
/* Returns !0 if the specified driver is raw capable, 0 if it is not. */
static int isRawCapable(const BrailleDriver *brl)
{
  return ((brl->readPacket!=NULL) && (brl->writePacket!=NULL) && (brl->reset!=NULL));
}

/* Function : isKeyCapable */
/* Returns !0 if driver can return specific keycodes, 0 if not. */
static int isKeyCapable(const BrailleDriver *brl)
{
  int ret;
  lockMutex(&apiDriverMutex);
  ret = ((brl->readKey!=NULL) && (brl->keyToCommand!=NULL)) || (disp && disp->keyNames!=NULL);
  unlockMutex(&apiDriverMutex);
  return ret;
}

/* Function : suspendDriver */
/* Close driver */
static void suspendDriver(BrailleDisplay *brl) {
  if (trueBraille == &noBraille) return; /* core unlinked api */
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "driver suspended");
  lockMutex(&apiSuspendMutex);
  driverConstructed = 0;
  destructBrailleDriver();
  unlockMutex(&apiSuspendMutex);
}

/* Function : resumeDriver */
/* Re-open driver */
static int resumeDriver(BrailleDisplay *brl) {
  if (trueBraille == &noBraille) return 0; /* core unlinked api */
  driverConstructing = 1;
  lockMutex(&apiSuspendMutex);
  driverConstructed = constructBrailleDriver();
  if (driverConstructed) {
    logMessage(LOG_CATEGORY(SERVER_EVENTS), "driver resumed");
    brlResize(brl);
  }
  unlockMutex(&apiSuspendMutex);
  driverConstructing = 0;
  return driverConstructed;
}

/****************************************************************************/
/** PACKET HANDLING                                                        **/
/****************************************************************************/

/* Function : writeAck */
/* Sends an acknowledgement on the given socket */
static inline void writeAck(FileDescriptor fd)
{
  brlapiserver_writePacket(fd,BRLAPI_PACKET_ACK,NULL,0);
}

/* Function : writeError */
/* Sends the given non-fatal error on the given socket */
static void writeError(FileDescriptor fd, unsigned int err)
{
  uint32_t code = htonl(err);
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "error %u on fd %"PRIfd, err, fd);
  brlapiserver_writePacket(fd,BRLAPI_PACKET_ERROR,&code,sizeof(code));
}

/* Function : writeException */
/* Sends the given error code on the given socket */
static void writeException(FileDescriptor fd, unsigned int err, brlapi_packetType_t type, const brlapi_packet_t *packet, size_t size)
{
  int hdrsize, esize;
  brlapi_packet_t epacket;
  brlapi_errorPacket_t * errorPacket = &epacket.error;
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "exception %u for packet type %lu on fd %"PRIfd, err, (unsigned long)type, fd);
  hdrsize = sizeof(errorPacket->code)+sizeof(errorPacket->type);
  errorPacket->code = htonl(err);
  errorPacket->type = htonl(type);
  esize = MIN(size, BRLAPI_MAXPACKETSIZE-hdrsize);
  if ((packet!=NULL) && (size!=0)) memcpy(&errorPacket->packet, &packet->data, esize);
  brlapiserver_writePacket(fd,BRLAPI_PACKET_EXCEPTION,&epacket.data, hdrsize+esize);
}

static void writeKey(FileDescriptor fd, brlapi_keyCode_t key) {
  uint32_t buf[2];
  buf[0] = htonl(key >> 32);
  buf[1] = htonl(key & 0xffffffff);
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "writing key %08"PRIx32" %08"PRIx32" to fd %"PRIfd,buf[0],buf[1],fd);
  brlapiserver_writePacket(fd,BRLAPI_PACKET_KEY,&buf,sizeof(buf));
}

typedef int(*PacketHandler)(Connection *, brlapi_packetType_t, brlapi_packet_t *, size_t);

typedef struct { /* packet handlers */
  PacketHandler getDriverName;
  PacketHandler getModelIdentifier;
  PacketHandler getDisplaySize;
  PacketHandler enterTtyMode;
  PacketHandler setFocus;
  PacketHandler leaveTtyMode;
  PacketHandler ignoreKeyRanges;
  PacketHandler acceptKeyRanges;
  PacketHandler write;
  PacketHandler enterRawMode;
  PacketHandler leaveRawMode;
  PacketHandler packet;
  PacketHandler suspendDriver;
  PacketHandler resumeDriver;
} PacketHandlers;

/****************************************************************************/
/** BRAILLE WINDOWS MANAGING                                               **/
/****************************************************************************/

/* Function : allocBrailleWindow */
/* Allocates and initializes the members of a BrailleWindow structure */
/* Uses displaySize to determine size of allocated buffers */
/* Returns to report success, -1 on errors */
static int allocBrailleWindow(BrailleWindow *brailleWindow)
{
  if (!(brailleWindow->text = malloc(displaySize*sizeof(wchar_t)))) goto out;
  if (!(brailleWindow->andAttr = malloc(displaySize))) goto outText;
  if (!(brailleWindow->orAttr = malloc(displaySize))) goto outAnd;

  wmemset(brailleWindow->text, WC_C(' '), displaySize);
  memset(brailleWindow->andAttr, 0xFF, displaySize);
  memset(brailleWindow->orAttr, 0x00, displaySize);
  brailleWindow->cursor = 0;
  return 0;

outAnd:
  free(brailleWindow->andAttr);

outText:
  free(brailleWindow->text);

out:
  return -1;
}

/* Function: freeBrailleWindow */
/* Frees the fields of a BrailleWindow structure */
static void freeBrailleWindow(BrailleWindow *brailleWindow)
{
  free(brailleWindow->text); brailleWindow->text = NULL;
  free(brailleWindow->andAttr); brailleWindow->andAttr = NULL;
  free(brailleWindow->orAttr); brailleWindow->orAttr = NULL;
}

static unsigned char
getCursorOverlay (BrailleDisplay *brl) {
  if (prefs.showScreenCursor && !brl->hideCursor) {
    BlinkDescriptor *blink = &screenCursorBlinkDescriptor;
    requireBlinkDescriptor(blink);

    if (isBlinkVisible(blink)) {
      return getScreenCursorDots();
    }
  }

  return 0;
}

/* Function: getDots */
/* Returns the braille dots corresponding to a BrailleWindow structure */
/* No allocation of buf is performed */
static void getDots(const BrailleWindow *brailleWindow, unsigned char *buf)
{
  int i;
  unsigned char c;
  for (i=0; i<displaySize; i++) {
    c = convertCharacterToDots(textTable, brailleWindow->text[i]);
    buf[i] = (c & brailleWindow->andAttr[i]) | brailleWindow->orAttr[i];
  }

  if (brailleWindow->cursor) {
    buf[brailleWindow->cursor-1] |= cursorOverlay;
  }
}

static void handleResize(BrailleDisplay *brl)
{
  /* TODO: handle resize */
  logMessage(LOG_INFO,"BrlAPI resize");
}

/****************************************************************************/
/** CONNECTIONS MANAGING                                                   **/
/****************************************************************************/

/* Function : createConnection */
/* Creates a connection */
static Connection *createConnection(FileDescriptor fd, time_t currentTime)
{
  Connection *c =  malloc(sizeof(Connection));
  if (c==NULL) goto out;

  c->auth = -1;
  c->fd = fd;
  c->tty = NULL;
  c->raw = 0;
  c->suspend = 0;
  c->brlbufstate = EMPTY;

  {
    pthread_mutexattr_t mattr;

    pthread_mutexattr_init(&mattr);
    pthread_mutexattr_settype(&mattr, PTHREAD_MUTEX_RECURSIVE);

    pthread_mutex_init(&c->brailleWindowMutex,&mattr);
    setAddressName(&c->brailleWindowMutex, "apiBrailleWindowMutex[" PRIfd "]", fd);

    pthread_mutex_init(&c->acceptedKeysMutex,&mattr);
    setAddressName(&c->acceptedKeysMutex, "apiAcceptedKeysMutex[" PRIfd "]", fd);
  }

  c->how = 0;
  c->acceptedKeys = NULL;
  c->upTime = currentTime;
  c->brailleWindow.text = NULL;
  c->brailleWindow.andAttr = NULL;
  c->brailleWindow.orAttr = NULL;
  if (brlapi_initializePacket(&c->packet))
    goto outmalloc;
  return c;

outmalloc:
  free(c);
out:
  if (fd != INVALID_FILE_DESCRIPTOR) {
    writeError(fd,BRLAPI_ERROR_NOMEM);
    closeFileDescriptor(fd);
  }
  return NULL;
}

/* Function : freeConnection */
/* Frees all resources associated to a connection */
static void freeConnection(Connection *c)
{
  if (c->fd != INVALID_FILE_DESCRIPTOR) {
    if (c->auth != 1) unauthConnections--;
    closeFileDescriptor(c->fd);
  }

  pthread_mutex_destroy(&c->brailleWindowMutex);
  unsetAddressName(&c->brailleWindowMutex);

  pthread_mutex_destroy(&c->acceptedKeysMutex);
  unsetAddressName(&c->acceptedKeysMutex);

  freeBrailleWindow(&c->brailleWindow);
  freeKeyrangeList(&c->acceptedKeys);
  free(c);
}

/* Function : addConnection */
/* Creates a connection and adds it to the connection list */
static void __addConnection(Connection *c, Connection *connections)
{
  c->next = connections->next;
  c->prev = connections;
  connections->next->prev = c;
  connections->next = c;
}
static void addConnection(Connection *c, Connection *connections)
{
  lockMutex(&apiConnectionsMutex);
  __addConnection(c,connections);
  unlockMutex(&apiConnectionsMutex);
}

/* Function : removeConnection */
/* Removes the connection from the list */
static void __removeConnection(Connection *c)
{
  c->prev->next = c->next;
  c->next->prev = c->prev;
}
static void removeConnection(Connection *c)
{
  lockMutex(&apiConnectionsMutex);
  __removeConnection(c);
  unlockMutex(&apiConnectionsMutex);
}

/* Function: removeFreeConnection */
/* Removes the connection from the list and frees its resources */
static void removeFreeConnection(Connection *c)
{
  removeConnection(c);
  freeConnection(c);
}

/****************************************************************************/
/** TTYs MANAGING                                                          **/
/****************************************************************************/

/* Function: newTty */
/* creates a new tty and inserts it in the hierarchy */
static inline Tty *newTty(Tty *father, int number)
{
  Tty *tty;
  if (!(tty = calloc(1,sizeof(*tty)))) goto out;
  if (!(tty->connections = createConnection(INVALID_FILE_DESCRIPTOR,0))) goto outtty;
  tty->connections->next = tty->connections->prev = tty->connections;
  tty->number = number;
  tty->focus = SCR_NO_VT;
  tty->father = father;
  tty->prevnext = &father->subttys;
  if ((tty->next = father->subttys))
    tty->next->prevnext = &tty->next;
  father->subttys = tty;
  return tty;

outtty:
  free(tty);
out:
  return NULL;
}

/* Function: removeTty */
/* removes an unused tty from the hierarchy */
static inline void removeTty(Tty *toremove)
{
  if (toremove->next)
    toremove->next->prevnext = toremove->prevnext;
  *(toremove->prevnext) = toremove->next;
}

/* Function: freeTty */
/* frees a tty */
static inline void freeTty(Tty *tty)
{
  freeConnection(tty->connections);
  free(tty);
}

/****************************************************************************/
/** COMMUNICATION PROTOCOL HANDLING                                        **/
/****************************************************************************/

/* Function logRequest */
/* Logs the given request */
static inline void logRequest(brlapi_packetType_t type, FileDescriptor fd)
{
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "received %s request on fd %"PRIfd, brlapiserver_getPacketTypeName(type), fd);
}

static int handleGetDriver(Connection *c, brlapi_packetType_t type, size_t size, const char *str)
{
  int len = strlen(str);
  CHECKERR(size==0,BRLAPI_ERROR_INVALID_PACKET,"packet should be empty");
  CHECKERR(!c->raw,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed in raw mode");
  brlapiserver_writePacket(c->fd, type, str, len+1);
  return 0;
}

static int handleGetDriverName(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  return handleGetDriver(c, type, size, braille->definition.name);
}

static int handleGetModelIdentifier(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  return handleGetDriver(c, type, size, disp->keyBindings);
}

static int handleGetDisplaySize(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  CHECKERR(size==0,BRLAPI_ERROR_INVALID_PACKET,"packet should be empty");
  CHECKERR(!c->raw,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed in raw mode");
  brlapiserver_writePacket(c->fd,BRLAPI_PACKET_GETDISPLAYSIZE,&displayDimensions[0],sizeof(displayDimensions));
  return 0;
}

static int handleEnterTtyMode(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  uint32_t * ints = &packet->uint32;
  uint32_t nbTtys;
  int how;
  unsigned int n;
  unsigned char *p = packet->data;
  char name[BRLAPI_MAXNAMELENGTH+1];
  Tty *tty,*tty2,*tty3;
  uint32_t *ptty;
  size_t remaining = size;
  CHECKERR((!c->raw),BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed in raw mode");
  CHECKERR(remaining>=sizeof(uint32_t), BRLAPI_ERROR_INVALID_PACKET, "packet too small");
  p += sizeof(uint32_t); remaining -= sizeof(uint32_t);
  nbTtys = ntohl(ints[0]);
  CHECKERR(remaining>=nbTtys*sizeof(uint32_t), BRLAPI_ERROR_INVALID_PACKET, "packet too small for provided number of ttys");
  p += nbTtys*sizeof(uint32_t); remaining -= nbTtys*sizeof(uint32_t);
  CHECKERR(*p<=BRLAPI_MAXNAMELENGTH, BRLAPI_ERROR_INVALID_PARAMETER, "driver name too long");
  n = *p; p++; remaining--;
  CHECKERR(remaining==n, BRLAPI_ERROR_INVALID_PACKET,"packet size doesn't match format");
  memcpy(name, p, n);
  name[n] = '\0';
  if (!*name) how = BRL_COMMANDS; else {
    CHECKERR(!strcmp(name, trueBraille->definition.name), BRLAPI_ERROR_INVALID_PARAMETER, "wrong driver name");
    CHECKERR(isKeyCapable(trueBraille), BRLAPI_ERROR_OPNOTSUPP, "driver doesn't support raw keycodes");
    how = BRL_KEYCODES;
  }
  freeBrailleWindow(&c->brailleWindow); /* In case of multiple enterTtyMode requests */

  if ((initializeAcceptedKeys(c, how)==-1) || (allocBrailleWindow(&c->brailleWindow)==-1)) {
    logMessage(LOG_WARNING,"Failed to allocate some resources");
    freeKeyrangeList(&c->acceptedKeys);
    WERR(c->fd,BRLAPI_ERROR_NOMEM, "no memory for accepted keys");
    return 0;
  }

  lockMutex(&apiConnectionsMutex);
  tty = tty2 = &ttys;

  for (ptty=ints+1; ptty<=ints+nbTtys; ptty++) {
    for (tty2=tty->subttys; tty2; tty2=tty2->next) {
      if (tty2->number == ntohl(*ptty)) break;
    }

    if (!tty2) break;
    tty = tty2;
    logMessage(LOG_CATEGORY(SERVER_EVENTS), "tty %#010lx ok",(unsigned long)ntohl(*ptty));
  }

  if (!tty2) {
    /* we were stopped at some point because the path doesn't exist yet */
    if (c->tty) {
      /* uhu, we already got a tty, but not this one, since the path
       * doesn't exist yet. This is forbidden. */
      unlockMutex(&apiConnectionsMutex);
      WERR(c->fd, BRLAPI_ERROR_INVALID_PARAMETER, "already having another tty");
      freeBrailleWindow(&c->brailleWindow);
      return 0;
    }
    /* ok, allocate path */
    /* we lock the entire subtree for easier cleanup */
    if (!(tty2 = newTty(tty,ntohl(*ptty)))) {
      unlockMutex(&apiConnectionsMutex);
      WERR(c->fd,BRLAPI_ERROR_NOMEM, "no memory for new tty");
      freeBrailleWindow(&c->brailleWindow);
      return 0;
    }
    ptty++;
    logMessage(LOG_CATEGORY(SERVER_EVENTS), "allocated tty %#010lx",(unsigned long)ntohl(*(ptty-1)));
    for (; ptty<=ints+nbTtys; ptty++) {
      if (!(tty2 = newTty(tty2,ntohl(*ptty)))) {
        /* gasp, couldn't allocate :/, clean tree */
        for (tty2 = tty->subttys; tty2; tty2 = tty3) {
          tty3 = tty2->subttys;
          freeTty(tty2);
        }
        unlockMutex(&apiConnectionsMutex);
        WERR(c->fd,BRLAPI_ERROR_NOMEM, "no memory for new tty");
        freeBrailleWindow(&c->brailleWindow);
        return 0;
      }
      logMessage(LOG_CATEGORY(SERVER_EVENTS), "allocated tty %#010lx",(unsigned long)ntohl(*ptty));
    }
    tty = tty2;
  }
  if (c->tty) {
    unlockMutex(&apiConnectionsMutex);
    if (c->tty == tty) {
      if (c->how==how) {
	WERR(c->fd, BRLAPI_ERROR_ILLEGAL_INSTRUCTION, "already controlling tty %#010x", c->tty->number);
      } else {
        /* Here one is in the case where the client tries to change */
        /* from BRL_KEYCODES to BRL_COMMANDS, or something like that */
        /* For the moment this operation is not supported */
        /* A client that wants to do that should first LeaveTty() */
        /* and then get it again, risking to lose it */
        WERR(c->fd,BRLAPI_ERROR_OPNOTSUPP, "Switching from BRL_KEYCODES to BRL_COMMANDS not supported yet");
      }
      return 0;
    } else {
      /* uhu, we already got a tty, but not this one: this is forbidden. */
      WERR(c->fd, BRLAPI_ERROR_INVALID_PARAMETER, "already having a tty");
      return 0;
    }
  }
  c->tty = tty;
  c->how = how;
  __removeConnection(c);
  __addConnection(c,tty->connections);
  unlockMutex(&apiConnectionsMutex);
  writeAck(c->fd);
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "fd %"PRIfd" taking control of tty %#010x (how=%d)",c->fd,tty->number,how);
  return 0;
}

static int handleSetFocus(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  uint32_t * ints = &packet->uint32;
  CHECKEXC(!c->raw,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed in raw mode");
  CHECKEXC(c->tty,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed out of tty mode");
  c->tty->focus = ntohl(ints[0]);
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "focus on window %#010x from fd%"PRIfd,c->tty->focus,c->fd);
  asyncSignalEvent(flushEvent, NULL);
  return 0;
}

/* Function doLeaveTty */
/* handles a connection leaving its tty */
static void doLeaveTty(Connection *c)
{
  Tty *tty = c->tty;
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "fd %"PRIfd" releasing tty %#010x",c->fd,tty->number);
  c->tty = NULL;
  lockMutex(&apiConnectionsMutex);
  __removeConnection(c);
  __addConnection(c,notty.connections);
  unlockMutex(&apiConnectionsMutex);
  freeKeyrangeList(&c->acceptedKeys);
  freeBrailleWindow(&c->brailleWindow);
}

static int handleLeaveTtyMode(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  CHECKERR(!c->raw,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed in raw mode");
  CHECKERR(c->tty,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed out of tty mode");
  doLeaveTty(c);
  writeAck(c->fd);
  return 0;
}

static int handleKeyRanges(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  int res = 0;
  brlapi_keyCode_t x,y;
  uint32_t (*ints)[4] = (uint32_t (*)[4]) packet;
  unsigned int i;
  CHECKERR(!c->raw,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed in raw mode");
  CHECKERR(c->tty,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed out of tty mode");
  CHECKERR(!(size%(2*sizeof(brlapi_keyCode_t))),BRLAPI_ERROR_INVALID_PACKET,"wrong packet size");
  lockMutex(&c->acceptedKeysMutex);
  for (i=0; i<size/(2*sizeof(brlapi_keyCode_t)); i++) {
    x = ((brlapi_keyCode_t)ntohl(ints[i][0]) << 32) | ntohl(ints[i][1]);
    y = ((brlapi_keyCode_t)ntohl(ints[i][2]) << 32) | ntohl(ints[i][3]);
    logMessage(LOG_CATEGORY(SERVER_EVENTS), "fd %"PRIfd" range: [%016"BRLAPI_PRIxKEYCODE"..%016"BRLAPI_PRIxKEYCODE"]",c->fd,x,y);
    if (type==BRLAPI_PACKET_IGNOREKEYRANGES) res = removeKeyrange(x,y,&c->acceptedKeys);
    else res = addKeyrange(x,y,&c->acceptedKeys);
    if (res==-1) {
      /* XXX: humf, in the middle of keycode updates :( */
      WERR(c->fd,BRLAPI_ERROR_NOMEM,"no memory for key range");
      break;
    }
  }
  unlockMutex(&c->acceptedKeysMutex);
  if (!res) writeAck(c->fd);
  return 0;
}

static int handleWrite(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  brlapi_writeArgumentsPacket_t *wa = &packet->writeArguments;
  unsigned char *text = NULL, *orAttr = NULL, *andAttr = NULL;
  unsigned int rbeg, rsiz, textLen = 0;
  int cursor = -1;
  unsigned char *p = &wa->data;
  int remaining = size;
  char *charset = NULL;
  unsigned int charsetLen = 0;
#ifdef HAVE_ICONV_H
  char *coreCharset = NULL;
#endif /* HAVE_ICONV_H */
  CHECKEXC(remaining>=sizeof(wa->flags), BRLAPI_ERROR_INVALID_PACKET, "packet too small for flags");
  CHECKERR(!c->raw,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed in raw mode");
  CHECKERR(c->tty,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed out of tty mode");
  wa->flags = ntohl(wa->flags);
  if ((remaining==sizeof(wa->flags))&&(wa->flags==0)) {
    c->brlbufstate = EMPTY;
    return 0;
  }
  remaining -= sizeof(wa->flags); /* flags */
  CHECKEXC((wa->flags & BRLAPI_WF_DISPLAYNUMBER)==0, BRLAPI_ERROR_OPNOTSUPP, "display number not yet supported");
  if (wa->flags & BRLAPI_WF_REGION) {
    CHECKEXC(remaining>2*sizeof(uint32_t), BRLAPI_ERROR_INVALID_PACKET, "packet too small for region");
    rbeg = ntohl( *((uint32_t *) p) );
    p += sizeof(uint32_t); remaining -= sizeof(uint32_t); /* region begin */
    rsiz = ntohl( *((uint32_t *) p) );
    p += sizeof(uint32_t); remaining -= sizeof(uint32_t); /* region size */

    CHECKEXC(
      (rbeg >= 1) && (rbeg <= displaySize),
      BRLAPI_ERROR_INVALID_PARAMETER, "invalid region start"
    );

    CHECKEXC(
      (rsiz > 0) && (rsiz <= displaySize),
      BRLAPI_ERROR_INVALID_PARAMETER, "invalid region size"
    );

    CHECKEXC(
      ((rbeg + rsiz - 1) <= displaySize),
      BRLAPI_ERROR_INVALID_PARAMETER, "invalid region"
    );
  } else {
    logMessage(LOG_CATEGORY(SERVER_EVENTS), "warning: fd %"PRIfd" uses deprecated regionBegin=0 and regionSize = 0",c->fd);
    rbeg = 1;
    rsiz = displaySize;
  }
  if (wa->flags & BRLAPI_WF_TEXT) {
    CHECKEXC(remaining>=sizeof(uint32_t), BRLAPI_ERROR_INVALID_PACKET, "packet too small for text length");
    textLen = ntohl( *((uint32_t *) p) );
    p += sizeof(uint32_t); remaining -= sizeof(uint32_t); /* text size */
    CHECKEXC(remaining>=textLen, BRLAPI_ERROR_INVALID_PACKET, "packet too small for text");
    text = p;
    p += textLen; remaining -= textLen; /* text */
  }
  if (wa->flags & BRLAPI_WF_ATTR_AND) {
    CHECKEXC(remaining>=rsiz, BRLAPI_ERROR_INVALID_PACKET, "packet too small for And mask");
    andAttr = p;
    p += rsiz; remaining -= rsiz; /* and attributes */
  }
  if (wa->flags & BRLAPI_WF_ATTR_OR) {
    CHECKEXC(remaining>=rsiz, BRLAPI_ERROR_INVALID_PACKET, "packet too small for Or mask");
    orAttr = p;
    p += rsiz; remaining -= rsiz; /* or attributes */
  }
  if (wa->flags & BRLAPI_WF_CURSOR) {
    uint32_t u32;
    CHECKEXC(remaining>=sizeof(uint32_t), BRLAPI_ERROR_INVALID_PACKET, "packet too small for cursor");
    memcpy(&u32, p, sizeof(uint32_t));
    cursor = ntohl(u32);
    p += sizeof(uint32_t); remaining -= sizeof(uint32_t); /* cursor */
    CHECKEXC(cursor<=displaySize, BRLAPI_ERROR_INVALID_PACKET, "wrong cursor");
  }
  if (wa->flags & BRLAPI_WF_CHARSET) {
    CHECKEXC(wa->flags & BRLAPI_WF_TEXT, BRLAPI_ERROR_INVALID_PACKET, "charset requires text");
    CHECKEXC(remaining>=1, BRLAPI_ERROR_INVALID_PACKET, "packet too small for charset length");
    charsetLen = *p++; remaining--; /* charset length */
    CHECKEXC(remaining>=charsetLen, BRLAPI_ERROR_INVALID_PACKET, "packet too small for charset");
    charset = (char *) p;
    p += charsetLen; remaining -= charsetLen; /* charset name */
  }
  CHECKEXC(remaining==0, BRLAPI_ERROR_INVALID_PACKET, "packet too big");
  /* Here the whole packet has been checked */
  if (text) {
    if (charset) {
      charset[charsetLen] = 0; /* we have room for this */
#ifndef HAVE_ICONV_H
      CHECKEXC(!strcasecmp(charset, "iso-8859-1"), BRLAPI_ERROR_OPNOTSUPP, "charset conversion not supported (enable iconv?)");
#endif /* !HAVE_ICONV_H */
    }
#ifdef HAVE_ICONV_H
    else {
      lockCharset(0);
      charset = coreCharset = (char *) getCharset();
      if (!coreCharset) {
        unlockCharset();
      }
    }
    if (charset) {
      iconv_t conv;
      wchar_t textBuf[rsiz];
      char *in = (char *) text, *out = (char *) textBuf;
      size_t sin = textLen, sout = sizeof(textBuf), res;
      logMessage(LOG_CATEGORY(SERVER_EVENTS), "fd %"PRIfd" charset %s",c->fd,charset);
      CHECKEXC((conv = iconv_open(getWcharCharset(),charset)) != (iconv_t)(-1), BRLAPI_ERROR_INVALID_PACKET, "invalid charset");
      res = iconv(conv,&in,&sin,&out,&sout);
      iconv_close(conv);
      CHECKEXC(res != (size_t) -1, BRLAPI_ERROR_INVALID_PACKET, "invalid charset conversion");
      CHECKEXC(!sin, BRLAPI_ERROR_INVALID_PACKET, "text too big");
      CHECKEXC(!sout, BRLAPI_ERROR_INVALID_PACKET, "text too small");
      if (coreCharset) unlockCharset();
      lockMutex(&c->brailleWindowMutex);
      memcpy(c->brailleWindow.text+rbeg-1,textBuf,rsiz*sizeof(wchar_t));
      logMessage(LOG_CATEGORY(SERVER_EVENTS), "fd %"PRIfd" wrote %d characters %d bytes",c->fd,rsiz,textLen);
    } else
#endif /* HAVE_ICONV_H */
    {
      int i;
      lockMutex(&c->brailleWindowMutex);
      for (i=0; i<rsiz; i++) {
	/* assume latin1 */
        c->brailleWindow.text[rbeg-1+i] = text[i];
      }
      logMessage(LOG_CATEGORY(SERVER_EVENTS), "fd %"PRIfd" wrote %d characters %d bytes",c->fd,rsiz,rsiz);
    }
    if (!andAttr) memset(c->brailleWindow.andAttr+rbeg-1,0xFF,rsiz);
    if (!orAttr)  memset(c->brailleWindow.orAttr+rbeg-1,0x00,rsiz);
  } else lockMutex(&c->brailleWindowMutex);
  if (andAttr) memcpy(c->brailleWindow.andAttr+rbeg-1,andAttr,rsiz);
  if (orAttr) memcpy(c->brailleWindow.orAttr+rbeg-1,orAttr,rsiz);
  if (cursor>=0) c->brailleWindow.cursor = cursor;
  c->brlbufstate = TODISPLAY;
  unlockMutex(&c->brailleWindowMutex);
  asyncSignalEvent(flushEvent, NULL);
  return 0;
}

static int checkDriverSpecificModePacket(Connection *c, brlapi_packet_t *packet, size_t size)
{
  brlapi_getDriverSpecificModePacket_t *getDevicePacket = &packet->getDriverSpecificMode;
  int remaining = size;
  CHECKERR(remaining>sizeof(uint32_t), BRLAPI_ERROR_INVALID_PACKET, "packet too small");
  remaining -= sizeof(uint32_t);
  CHECKERR(ntohl(getDevicePacket->magic)==BRLAPI_DEVICE_MAGIC,BRLAPI_ERROR_INVALID_PARAMETER, "wrong magic number");
  remaining--;
  CHECKERR(getDevicePacket->nameLength<=BRLAPI_MAXNAMELENGTH && getDevicePacket->nameLength == strlen(trueBraille->definition.name), BRLAPI_ERROR_INVALID_PARAMETER, "wrong driver length");
  CHECKERR(remaining==getDevicePacket->nameLength, BRLAPI_ERROR_INVALID_PACKET, "wrong packet size");
  CHECKERR(((!strncmp(&getDevicePacket->name, trueBraille->definition.name, remaining))), BRLAPI_ERROR_INVALID_PARAMETER, "wrong driver name");
  return 1;
}

static int handleEnterRawMode(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  CHECKERR(!c->raw, BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed in raw mode");
  if (!checkDriverSpecificModePacket(c, packet, size)) return 0;
  CHECKERR(isRawCapable(trueBraille), BRLAPI_ERROR_OPNOTSUPP, "driver doesn't support Raw mode");
  lockMutex(&apiRawMutex);
  if (rawConnection || suspendConnection) {
    WERR(c->fd,BRLAPI_ERROR_DEVICEBUSY,"driver busy (%s)", rawConnection?"raw":"suspend");
    unlockMutex(&apiRawMutex);
    return 0;
  }
  lockMutex(&apiDriverMutex);
  if (!driverConstructed && !driverConstructing && (!disp || !resumeDriver(disp))) {
    WERR(c->fd, BRLAPI_ERROR_DRIVERERROR,"driver resume error");
    unlockMutex(&apiDriverMutex);
    unlockMutex(&apiRawMutex);
    return 0;
  }
  unlockMutex(&apiDriverMutex);
  c->raw = 1;
  rawConnection = c;
  unlockMutex(&apiRawMutex);
  writeAck(c->fd);
  return 0;
}

static int handleLeaveRawMode(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  CHECKERR(c->raw,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed out of raw mode");
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "fd %"PRIfd" going out of raw mode",c->fd);
  lockMutex(&apiRawMutex);
  c->raw = 0;
  rawConnection = NULL;
  unlockMutex(&apiRawMutex);
  writeAck(c->fd);
  return 0;
}

static int handlePacket(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  CHECKEXC(c->raw,BRLAPI_ERROR_ILLEGAL_INSTRUCTION,"not allowed out of raw mode");
  lockMutex(&apiDriverMutex);
  trueBraille->writePacket(disp,&packet->data,size);
  unlockMutex(&apiDriverMutex);
  return 0;
}

static int handleSuspendDriver(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  if (!checkDriverSpecificModePacket(c, packet, size)) return 0;
  CHECKERR(!c->suspend,BRLAPI_ERROR_ILLEGAL_INSTRUCTION, "not allowed in suspend mode");
  lockMutex(&apiRawMutex);
  if (suspendConnection || rawConnection) {
    WERR(c->fd, BRLAPI_ERROR_DEVICEBUSY,"driver busy (%s)", rawConnection?"raw":"suspend");
    unlockMutex(&apiRawMutex);
    return 0;
  }
  c->suspend = 1;
  suspendConnection = c;
  unlockMutex(&apiRawMutex);
  lockMutex(&apiDriverMutex);
  if (driverConstructed) suspendDriver(disp);
  unlockMutex(&apiDriverMutex);
  writeAck(c->fd);
  return 0;
}

static int handleResumeDriver(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  CHECKERR(c->suspend,BRLAPI_ERROR_ILLEGAL_INSTRUCTION, "not allowed out of suspend mode");
  lockMutex(&apiRawMutex);
  c->suspend = 0;
  suspendConnection = NULL;
  unlockMutex(&apiRawMutex);
  lockMutex(&apiDriverMutex);
  if (!driverConstructed && !driverConstructing) resumeDriver(disp);
  unlockMutex(&apiDriverMutex);
  writeAck(c->fd);
  return 0;
}

static PacketHandlers packetHandlers = {
  handleGetDriverName, handleGetModelIdentifier, handleGetDisplaySize,
  handleEnterTtyMode, handleSetFocus, handleLeaveTtyMode,
  handleKeyRanges, handleKeyRanges, handleWrite,
  handleEnterRawMode, handleLeaveRawMode, handlePacket,
  handleSuspendDriver, handleResumeDriver,
};

static void handleNewConnection(Connection *c)
{
  brlapi_packet_t versionPacket;
  versionPacket.version.protocolVersion = htonl(BRLAPI_PROTOCOL_VERSION);

  brlapiserver_writePacket(c->fd,BRLAPI_PACKET_VERSION,&versionPacket.data,sizeof(versionPacket.version));
}

static int
hasKeyFile(const char *auth)
{
  if (isAbsolutePath(auth))
    return 1;
  if (!strncmp(auth,"keyfile:", 8))
    return 1;
  if (strstr(auth,"+keyfile:"))
    return 1;
  return 0;
}

/* Function : handleUnauthorizedConnection */
/* Returns 1 if connection has to be removed */
static int handleUnauthorizedConnection(Connection *c, brlapi_packetType_t type, brlapi_packet_t *packet, size_t size)
{
  if (c->auth == -1) {
    if (type != BRLAPI_PACKET_VERSION) {
      WERR(c->fd, BRLAPI_ERROR_PROTOCOL_VERSION, "wrong packet type (should be version)");
      return 1;
    }

    {
      brlapi_versionPacket_t *versionPacket = &packet->version;
      brlapi_packet_t serverPacket;
      brlapi_authServerPacket_t *authPacket = &serverPacket.authServer;
      int nbmethods = 0;

      if (size<sizeof(*versionPacket) || ntohl(versionPacket->protocolVersion)!=BRLAPI_PROTOCOL_VERSION) {
	WERR(c->fd, BRLAPI_ERROR_PROTOCOL_VERSION, "wrong protocol version");
	return 1;
      }

      /* TODO: move this inside auth.c */
      if (authDescriptor && authPerform(authDescriptor, c->fd)) {
	authPacket->type[nbmethods++] = htonl(BRLAPI_AUTH_NONE);
	unauthConnections--;
	c->auth = 1;
      } else {
	if (hasKeyFile(auth))
	  authPacket->type[nbmethods++] = htonl(BRLAPI_AUTH_KEY);
	c->auth = 0;
      }

      brlapiserver_writePacket(c->fd,BRLAPI_PACKET_AUTH,&serverPacket,nbmethods*sizeof(authPacket->type));

      return 0;
    }
  }

  if (type!=BRLAPI_PACKET_AUTH) {
    WERR(c->fd, BRLAPI_ERROR_PROTOCOL_VERSION, "wrong packet type (should be auth)");
    return 1;
  }

  {
    size_t authKeyLength = 0;
    brlapi_packet_t authKey;
    int authCorrect = 0;
    brlapi_authClientPacket_t *authPacket = &packet->authClient;
    int remaining = size;
    uint32_t authType;

    if (!strcmp(auth,"none"))
      authCorrect = 1;
    else {
      authType = ntohl(authPacket->type);
      remaining -= sizeof(authPacket->type);

    /* TODO: move this inside auth.c */
      switch(authType) {
	case BRLAPI_AUTH_NONE:
	  if (authDescriptor) authCorrect = authPerform(authDescriptor, c->fd);
	  break;
	case BRLAPI_AUTH_KEY:
	  if (hasKeyFile(auth)) {
	    char *path = brlapiserver_getKeyFile(auth);
	    int ret = brlapiserver_loadAuthKey(path,&authKeyLength,&authKey);
	    if (ret==-1) {
	      logMessage(LOG_WARNING,"Unable to load API authorization key from %s: %s in %s. You may use parameter auth=none if you don't want any authorization (dangerous)", path, strerror(brlapi_libcerrno), brlapi_errfun);
	      free(path);
	      break;
	    }
	    free(path);
	    logMessage(LOG_CATEGORY(SERVER_EVENTS), "authorization key loaded");
	    authCorrect = (remaining==authKeyLength) && (!memcmp(&authPacket->key, &authKey, authKeyLength));
	    memset(&authKey, 0, authKeyLength);
	    memset(&authPacket->key, 0, remaining);
	  }
	  break;
	default:
	  logMessage(LOG_CATEGORY(SERVER_EVENTS), "unsupported authorization method %"PRId32, authType);
	  break;
      }
    }

    if (!authCorrect) {
      writeError(c->fd, BRLAPI_ERROR_AUTHENTICATION);
      logMessage(LOG_WARNING, "BrlAPI connection fd=%"PRIfd" failed authorization", c->fd);
      return 0;
    }

    unauthConnections--;
    writeAck(c->fd);
    c->auth = 1;
    return 0;
  }
}

/* Function : processRequest */
/* Reads a packet fro c->fd and processes it */
/* Returns 1 if connection has to be removed */
/* If EOF is reached, closes fd and frees all associated resources */
static int processRequest(Connection *c, PacketHandlers *handlers)
{
  PacketHandler p = NULL;
  int res;
  ssize_t size;
  brlapi_packet_t *packet = (brlapi_packet_t *) c->packet.content;
  brlapi_packetType_t type;
  res = brlapi__readPacket(&c->packet, c->fd);
  if (res==0) return 0; /* No packet ready */
  if (res<0) {
    if (res==-1) logMessage(LOG_WARNING,"read : %s (connection on fd %"PRIfd")",strerror(errno),c->fd);
    else {
      logMessage(LOG_CATEGORY(SERVER_EVENTS), "closing connection on fd %"PRIfd,c->fd);
    }
    if (c->raw) {
      lockMutex(&apiRawMutex);
      c->raw = 0;
      rawConnection = NULL;
      logMessage(LOG_WARNING,"Client on fd %"PRIfd" did not give up raw mode properly",c->fd);
      lockMutex(&apiDriverMutex);
      logMessage(LOG_WARNING,"Trying to reset braille terminal");
      if (!trueBraille->reset || !disp || !trueBraille->reset(disp)) {
	if (trueBraille->reset)
          logMessage(LOG_WARNING,"Reset failed. Restarting braille driver");
        restartBrailleDriver();
      }
      unlockMutex(&apiDriverMutex);
      unlockMutex(&apiRawMutex);
    } else if (c->suspend) {
      lockMutex(&apiRawMutex);
      c->suspend = 0;
      suspendConnection = NULL;
      logMessage(LOG_WARNING,"Client on fd %"PRIfd" did not give up suspended mode properly",c->fd);
      lockMutex(&apiDriverMutex);
      if (!driverConstructed && !driverConstructing && (!disp || !resumeDriver(disp)))
	logMessage(LOG_WARNING,"Couldn't resume braille driver");
      if (driverConstructed && trueBraille->reset) {
        logMessage(LOG_CATEGORY(SERVER_EVENTS), "trying to reset braille terminal");
	if (!trueBraille->reset(disp))
	  logMessage(LOG_WARNING,"Resetting braille terminal failed, hoping it's ok");
      }
      unlockMutex(&apiDriverMutex);
      unlockMutex(&apiRawMutex);
    }
    if (c->tty) {
      logMessage(LOG_CATEGORY(SERVER_EVENTS), "client on fd %"PRIfd" did not give up control of tty %#010x properly",c->fd,c->tty->number);
      doLeaveTty(c);
    }
    return 1;
  }
  size = c->packet.header.size;
  type = c->packet.header.type;

  if (c->auth!=1) return handleUnauthorizedConnection(c, type, packet, size);

  if (size>BRLAPI_MAXPACKETSIZE) {
    logMessage(LOG_WARNING, "Discarding too large packet of type %s on fd %"PRIfd,brlapiserver_getPacketTypeName(type), c->fd);
    return 0;
  }
  switch (type) {
    case BRLAPI_PACKET_GETDRIVERNAME: p = handlers->getDriverName; break;
    case BRLAPI_PACKET_GETMODELID: p = handlers->getModelIdentifier; break;
    case BRLAPI_PACKET_GETDISPLAYSIZE: p = handlers->getDisplaySize; break;
    case BRLAPI_PACKET_ENTERTTYMODE: p = handlers->enterTtyMode; break;
    case BRLAPI_PACKET_SETFOCUS: p = handlers->setFocus; break;
    case BRLAPI_PACKET_LEAVETTYMODE: p = handlers->leaveTtyMode; break;
    case BRLAPI_PACKET_IGNOREKEYRANGES: p = handlers->ignoreKeyRanges; break;
    case BRLAPI_PACKET_ACCEPTKEYRANGES: p = handlers->acceptKeyRanges; break;
    case BRLAPI_PACKET_WRITE: p = handlers->write; break;
    case BRLAPI_PACKET_ENTERRAWMODE: p = handlers->enterRawMode; break;
    case BRLAPI_PACKET_LEAVERAWMODE: p = handlers->leaveRawMode; break;
    case BRLAPI_PACKET_PACKET: p = handlers->packet; break;
    case BRLAPI_PACKET_SUSPENDDRIVER: p = handlers->suspendDriver; break;
    case BRLAPI_PACKET_RESUMEDRIVER: p = handlers->resumeDriver; break;
  }
  if (p!=NULL) {
    logRequest(type, c->fd);
    p(c, type, packet, size);
  } else WEXC(c->fd,BRLAPI_ERROR_UNKNOWN_INSTRUCTION, type, packet, size, "unknown packet type");
  return 0;
}

/****************************************************************************/
/** SOCKETS AND CONNECTIONS MANAGING                                       **/
/****************************************************************************/

/*
 * There is one server thread which first launches binding threads and then
 * enters infinite loop trying to accept connections, read packets, etc.
 *
 * Binding threads loop trying to establish some socket, waiting for
 * filesystems to be read/write or network to be configured.
 *
 * On windows, WSAEventSelect() is emulated by a standalone thread.
 */

/* Function: loopBind */
/* tries binding while temporary errors occur */
static int loopBind(SocketDescriptor fd, const struct sockaddr *address, socklen_t length)
{
  char buffer[0X100] = {0};
  const int maximum = 100;
  int delay = 1;
  int res;

  while ((res = bind(fd, address, length)) == -1) {
    if (!running) break;

    if (
#ifdef EADDRNOTAVAIL
        (errno != EADDRNOTAVAIL) &&
#endif /* EADDRNOTAVAIL */
#ifdef EADDRINUSE
        (errno != EADDRINUSE) &&
#endif /* EADDRINUSE */
        (errno != EROFS)) {
      break;
    }

    if (!buffer[0]) {
      formatAddress(buffer, sizeof(buffer), address, length);
    }

    logMessage(LOG_CATEGORY(SERVER_EVENTS), "bind waiting: %s: %s", buffer, strerror(errno));
    approximateDelay(delay * MSECS_PER_SEC);

    delay <<= 1;
    delay = MIN(delay, maximum);
  }

  return res;
}

static SocketDescriptor newTcpSocket(int family, int type, int protocol, const struct sockaddr *addr, socklen_t len)
{
  static const int yes = 1;
  SocketDescriptor fd = socket(family, type, protocol);

  if (fd != INVALID_SOCKET_DESCRIPTOR) {
#ifdef SO_REUSEADDR
    if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR,
                   (const void *)&yes, sizeof(yes)) == -1) {
      LogSocketError("setsockopt[SOCKET,REUSEADDR]");
    }
#endif /* SO_REUSEADDR */

#ifdef SOL_TCP
#ifdef TCP_NODELAY
    if (setsockopt(fd, SOL_TCP, TCP_NODELAY,
                   (const void *)&yes, sizeof(yes)) == -1) {
      LogSocketError("setsockopt[TCP,NODELAY]");
    }
#endif /* TCP_NODELAY */
#endif /* SOL_TCP */

    if (loopBind(fd, addr, len) != -1) {
      if (listen(fd, 1) != -1) {
        return fd;
      } else {
        LogSocketError("listen");
      }
    } else {
      LogSocketError("bind");
    }

    closeSocketDescriptor(fd);
  } else {
    setSocketErrno();

#ifdef EAFNOSUPPORT
    if (errno != EAFNOSUPPORT)
#endif /* EAFNOSUPPORT */
      logMessage(LOG_WARNING, "socket allocation error: %s", strerror(errno));
  }

  return INVALID_SOCKET_DESCRIPTOR;
}

/* Function : createTcpSocket */
/* Creates the listening socket for in-connections */
/* Returns the descriptor, or -1 if an error occurred */
static FileDescriptor createTcpSocket(struct socketInfo *info)
{
  SocketDescriptor fd = INVALID_SOCKET_DESCRIPTOR;

#ifdef __MINGW32__
  if (getaddrinfoProc) {
#endif /* __MINGW32__ */

#if defined(HAVE_GETADDRINFO) || defined(__MINGW32__)
  static const struct addrinfo hints = {
    .ai_flags = AI_PASSIVE,
    .ai_family = PF_UNSPEC,
    .ai_socktype = SOCK_STREAM
  };

  struct addrinfo *res, *cur;
  int err = getaddrinfo(info->host, info->port, &hints, &res);

  if (err) {
    logMessage(LOG_WARNING,
               "getaddrinfo(%s,%s): "
#ifdef HAVE_GAI_STRERROR
               "%s"
#else /* HAVE_GAI_STRERROR */
               "%d"
#endif /* HAVE_GAI_STRERROR */
               , info->host
               , info->port
#ifdef HAVE_GAI_STRERROR
               ,
#ifdef EAI_SYSTEM
                 (err == EAI_SYSTEM)? strerror(errno):
#endif /* EAI_SYSTEM */
                 gai_strerror(err)
#else /* HAVE_GAI_STRERROR */
               , err
#endif /* HAVE_GAI_STRERROR */
    );
    return INVALID_FILE_DESCRIPTOR;
  }

  for (cur = res; cur; cur = cur->ai_next) {
    fd = newTcpSocket(cur->ai_family, cur->ai_socktype, cur->ai_protocol,
                      cur->ai_addr, cur->ai_addrlen);

    if (fd != INVALID_SOCKET_DESCRIPTOR) break;
  }

  if (!cur) fd = INVALID_SOCKET_DESCRIPTOR;
  freeaddrinfo(res);
#endif /* HAVE_GETADDRINFO */

#ifdef __MINGW32__
  } else {
#endif /* __MINGW32__ */

#if !defined(HAVE_GETADDRINFO) || defined(__MINGW32__)
  struct hostent *he;

  struct sockaddr_in addr = {
    .sin_family = AF_INET
  };

  if (!info->port) {
    addr.sin_port = htons(BRLAPI_SOCKETPORTNUM);
  } else {
    unsigned int port;

    if (!isUnsignedInteger(&port, info->port)) {
      struct servent *se;

      if (!(se = getservbyname(info->port, "tcp"))) {
        logMessage(LOG_ERR,
                   "TCP port %s: "
#ifdef __MINGW32__
                   "%d"
#else /* __MINGW32__ */
                   "%s"
#endif /* __MINGW32__ */
                   , info->port
#ifdef __MINGW32__
                   , WSAGetLastError()
#else /* __MINGW32__ */
                   , hstrerror(h_errno)
#endif /* __MINGW32__ */
	);

	return INVALID_FILE_DESCRIPTOR;
      }

      addr.sin_port = se->s_port;
    } else if ((port > 0) && (port <= UINT16_MAX)) {
      addr.sin_port = htons(port);
    } else {
      logMessage(LOG_ERR, "invalid TCP port: %s", info->port);
      return INVALID_FILE_DESCRIPTOR;
    }
  }

  if (!info->host) {
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  } else if ((addr.sin_addr.s_addr = inet_addr(info->host)) == htonl(INADDR_NONE)) {
    if (!(he = gethostbyname(info->host))) {
      logMessage(LOG_ERR,
                 "gethostbyname(%s): "
#ifdef __MINGW32__
                 "%d"
#else /* __MINGW32__ */
                 "%s"
#endif /* __MINGW32__ */
                 , info->host
#ifdef __MINGW32__
                 , WSAGetLastError()
#else /* __MINGW32__ */
                 , hstrerror(h_errno)
#endif /* __MINGW32__ */
      );

      return INVALID_FILE_DESCRIPTOR;
    }

    if (he->h_addrtype != AF_INET) {
#ifdef EAFNOSUPPORT
      errno = EAFNOSUPPORT;
#else /* EAFNOSUPPORT */
      errno = EINVAL;
#endif /* EAFNOSUPPORT */

      logMessage(LOG_ERR, "unknown address type %d", he->h_addrtype);
      return INVALID_FILE_DESCRIPTOR;
    }

    if (he->h_length > sizeof(addr.sin_addr)) {
      errno = EINVAL;
      logMessage(LOG_ERR, "address too big: %d", he->h_length);
      return INVALID_FILE_DESCRIPTOR;
    }

    memcpy(&addr.sin_addr, he->h_addr, he->h_length);
  }

  fd = newTcpSocket(PF_INET, SOCK_STREAM, IPPROTO_TCP,
                    (struct sockaddr *)&addr, sizeof(addr));
#endif /* !HAVE_GETADDRINFO */

#ifdef __MINGW32__
  }
#endif /* __MINGW32__ */

  if (fd == INVALID_SOCKET_DESCRIPTOR) {
    logMessage(LOG_WARNING,"unable to find a local TCP port %s:%s !",info->host,info->port);
  }

  if (info->host) {
    free(info->host);
    info->host = NULL;
  }

  if (info->port) {
    free(info->port);
    info->port = NULL;
  }

  if (fd == INVALID_SOCKET_DESCRIPTOR) {
    return INVALID_FILE_DESCRIPTOR;
  }

#ifdef __MINGW32__
  if (!(info->overl.hEvent = CreateEvent(NULL, TRUE, FALSE, NULL))) {
    logWindowsSystemError("CreateEvent");
    closeSocketDescriptor(fd);
    return INVALID_FILE_DESCRIPTOR;
  }

  logMessage(LOG_CATEGORY(SERVER_EVENTS), "event -> %p", info->overl.hEvent);
  WSAEventSelect(fd, info->overl.hEvent, FD_ACCEPT);
#endif /* __MINGW32__ */

  return (FileDescriptor)fd;
}

#if defined(PF_LOCAL)

#ifndef __MINGW32__
static int readPid(char *path)
  /* read pid from specified file. Return 0 on any error */
{
  char pids[16], *ptr;
  pid_t pid;
  int n;
  FileDescriptor fd;
  fd = open(path, O_RDONLY);
  if (fd == -1) return 0;
  n = read(fd, pids, sizeof(pids)-1);
  closeFileDescriptor(fd);
  if (n == -1) return 0;
  pids[n] = 0;
  pid = strtol(pids, &ptr, 10);
  if (ptr != &pids[n]) return 0;
  return pid;
}

static int
adjustPermissions (const char *path) {
  if (!getegid()) {
    struct stat status;

    if (stat(path, &status) == -1) {
      logSystemError("stat");
      return 0;
    }

    {
      mode_t oldPermissions = status.st_mode & ~S_IFMT;
      mode_t newPermissions = oldPermissions;

#ifdef S_IRGRP
      if (oldPermissions & S_IRUSR) newPermissions |= S_IRGRP | S_IROTH;
      if (oldPermissions & S_IWUSR) newPermissions |= S_IWGRP | S_IWOTH;
      if (oldPermissions & S_IXUSR) newPermissions |= S_IXGRP | S_IXOTH;
#endif

      if (newPermissions != oldPermissions) {
        if (chmod(path, newPermissions) == -1) {
          logSystemError("chmod");
          return 0;
        }
      }
    }
  }

  return 1;
}
#endif /* __MINGW32__ */

/* Function : createLocalSocket */
/* Creates the listening socket for in-connections */
/* Returns 1, or 0 if an error occurred */
static FileDescriptor createLocalSocket(struct socketInfo *info)
{
  int lpath=strlen(BRLAPI_SOCKETPATH),lport=strlen(info->port);
  FileDescriptor fd;
#ifdef __MINGW32__
  char path[lpath+lport+1];
  memcpy(path,BRLAPI_SOCKETPATH,lpath);
  memcpy(path+lpath,info->port,lport+1);
  if ((fd = CreateNamedPipe(path,
	  PIPE_ACCESS_DUPLEX | FILE_FLAG_OVERLAPPED,
	  PIPE_TYPE_BYTE | PIPE_READMODE_BYTE,
	  PIPE_UNLIMITED_INSTANCES, 0, 0, 0, NULL)) == INVALID_HANDLE_VALUE) {
    if (GetLastError() != ERROR_CALL_NOT_IMPLEMENTED)
      logWindowsSystemError("CreateNamedPipe");
    goto out;
  }
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "CreateFile -> %"PRIfd,fd);
  if (!info->overl.hEvent) {
    if (!(info->overl.hEvent = CreateEvent(NULL, TRUE, FALSE, NULL))) {
      logWindowsSystemError("CreateEvent");
      goto outfd;
    }
    logMessage(LOG_CATEGORY(SERVER_EVENTS), "event -> %p",info->overl.hEvent);
  }
  if (!(ResetEvent(info->overl.hEvent))) {
    logWindowsSystemError("ResetEvent");
    goto outfd;
  }
  if (ConnectNamedPipe(fd, &info->overl)) {
    logMessage(LOG_CATEGORY(SERVER_EVENTS), "already connected !");
    return fd;
  }

  switch(GetLastError()) {
    case ERROR_IO_PENDING: return fd;
    case ERROR_PIPE_CONNECTED: SetEvent(info->overl.hEvent); return fd;
    default: logWindowsSystemError("ConnectNamedPipe");
  }
  CloseHandle(info->overl.hEvent);
#else /* __MINGW32__ */
  struct sockaddr_un sa;
  char tmppath[lpath+lport+4];
  char lockpath[lpath+lport+3];
  struct stat st;
  char pids[16];
  pid_t pid;
  int lock,n,done,res;
  mode_t permissions = S_IRWXU | S_IRWXG | S_IRWXO;

  if ((fd = socket(PF_LOCAL, SOCK_STREAM, 0))==-1) {
    logSystemError("socket");
    goto out;
  }

  sa.sun_family = AF_LOCAL;

  if (lpath+lport+1>sizeof(sa.sun_path)) {
    logMessage(LOG_ERR, "Unix path too long");
    goto outfd;
  }

  while (mkdir(BRLAPI_SOCKETPATH, (permissions | S_ISVTX)) == -1) {
    if (errno == EEXIST) {
      break;
    }

    if ((errno != EROFS) && (errno != ENOENT)) {
      logSystemError("making socket directory");
      goto outfd;
    }

    /* read-only, or not mounted yet, wait */
    approximateDelay(1000);
  }

  if (!adjustPermissions(BRLAPI_SOCKETPATH)) {
    goto outfd;
  }

  memcpy(sa.sun_path,BRLAPI_SOCKETPATH "/",lpath+1);
  memcpy(sa.sun_path+lpath+1,info->port,lport+1);
  memcpy(tmppath, BRLAPI_SOCKETPATH "/", lpath+1);
  tmppath[lpath+1]='.';
  memcpy(tmppath+lpath+2, info->port, lport);
  memcpy(lockpath, tmppath, lpath+2+lport);
  tmppath[lpath+2+lport]='_';
  tmppath[lpath+2+lport+1]=0;
  lockpath[lpath+2+lport]=0;

  while ((lock = open(tmppath, O_WRONLY|O_CREAT|O_EXCL,
                      (permissions & (S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH)))) == -1) {
    if (errno == EROFS) {
      approximateDelay(1000);
      continue;
    }

    if (errno != EEXIST) {
      logSystemError("opening local socket lock");
      goto outfd;
    }

    if ((pid = readPid(tmppath)) && pid != getpid()
	&& (kill(pid, 0) != -1 || errno != ESRCH)) {
      logMessage(LOG_ERR,"another BrlAPI server is already listening on %s (file %s exists)",info->port, tmppath);
      goto outfd;
    }

    /* bogus file, myself or non-existent process, remove */
    while (unlink(tmppath)) {
      if (errno != EROFS) {
	logSystemError("removing stale local socket lock");
	goto outfd;
      }

      approximateDelay(1000);
    }
  }

  n = snprintf(pids,sizeof(pids),"%d",getpid());
  done = 0;

  while ((res = write(lock,pids+done,n)) < n) {
    if (res == -1) {
      if (errno != ENOSPC) {
	logSystemError("writing pid in local socket lock");
	goto outlockfd;
      }

      approximateDelay(1000);
    } else {
      done += res;
      n -= res;
    }
  }

  while (1) {
    if (link(tmppath, lockpath) == -1) {
      logMessage(LOG_CATEGORY(SERVER_EVENTS), "linking local socket lock: %s", strerror(errno));
      /* but no action: link() might erroneously return errors, see manpage */
    }

    if (fstat(lock, &st) == -1) {
      logSystemError("checking local socket lock");
      goto outtmp;
    }

    if (st.st_nlink == 2) {
      /* success */
      break;
    }

    /* failed to link */
    if ((pid = readPid(lockpath)) && pid != getpid()
	&& (kill(pid, 0) != -1 || errno != ESRCH)) {
      logMessage(LOG_ERR,"another BrlAPI server is already listening on %s (file %s exists)",info->port, lockpath);
      goto outtmp;
    }

    /* bogus file, myself or non-existent process, remove */
    if (unlink(lockpath)) {
      logSystemError("removing stale local socket lock");
      goto outtmp;
    }
  }

  closeFileDescriptor(lock);

  if (unlink(tmppath) == -1) {
    logSystemError("removing temp local socket lock");
  }

  if (unlink(sa.sun_path) && errno != ENOENT) {
    logSystemError("removing old socket");
    goto outtmp;
  }

  if (loopBind(fd, (struct sockaddr *) &sa, sizeof(sa)) == -1) {
    logMessage(LOG_WARNING, "bind: %s", strerror(errno));
    goto outlock;
  }

  if (!adjustPermissions(sa.sun_path)) {
    goto outlock;
  }

  if (listen(fd,1)<0) {
    logSystemError("listen");
    goto outlock;
  }
  return fd;

outlock:
  unlink(lockpath);
outtmp:
  unlink(tmppath);
outlockfd:
  closeFileDescriptor(lock);
#endif /* __MINGW32__ */
outfd:
  closeFileDescriptor(fd);
out:
  return INVALID_FILE_DESCRIPTOR;
}
#endif /* PF_LOCAL */

static void createSocket(int num)
{
  struct socketInfo *cinfo = &socketInfo[num];

  logMessage(LOG_CATEGORY(SERVER_EVENTS), "creating socket: %d (%s:%s)",
             num,
             (cinfo->host? cinfo->host: "LOCAL"),
             (cinfo->port? cinfo->port: "DEFAULT")
  );

  cinfo->fd =
#if defined(PF_LOCAL)
    (cinfo->addrfamily == PF_LOCAL)? createLocalSocket(cinfo):
#endif /* PF_LOCAL */
    createTcpSocket(cinfo);

  if (cinfo->fd == INVALID_FILE_DESCRIPTOR) {
    logMessage(LOG_WARNING, "error while creating socket %d", num);
  } else {
    logMessage(LOG_CATEGORY(SERVER_EVENTS), "socket %d created (fd %"PRIfd")", num, cinfo->fd);
  }
}

static void closeSockets(void *arg)
{
  int i;
  struct socketInfo *info;

  for (i=0;i<serverSocketCount;i++) {
#ifdef __MINGW32__
    pthread_cancel(socketThreads[i]);
#else /* __MINGW32__ */
    pthread_kill(socketThreads[i], SIGUSR2);
#endif /* __MINGW32__ */
    pthread_join(socketThreads[i], NULL);

    info=&socketInfo[i];

    if (info->fd>=0) {
      if (closeFileDescriptor(info->fd)) {
        logSystemError("closing socket");
      }
      info->fd=INVALID_FILE_DESCRIPTOR;

#ifdef __MINGW32__
      if ((info->overl.hEvent)) {
	CloseHandle(info->overl.hEvent);
	info->overl.hEvent = NULL;
      }
#else /* __MINGW32__ */

#if defined(PF_LOCAL)
      if (info->addrfamily==PF_LOCAL) {
	char *path;
	int lpath=strlen(BRLAPI_SOCKETPATH),lport=strlen(info->port);

	if ((path=malloc(lpath+lport+3))) {
	  memcpy(path,BRLAPI_SOCKETPATH "/",lpath+1);
	  memcpy(path+lpath+1,info->port,lport+1);

	  if (unlink(path) == -1) {
	    logSystemError("unlinking local socket");
          }

	  path[lpath+1]='.';
	  memcpy(path+lpath+2,info->port,lport+1);

	  if (unlink(path) == -1) {
	    logSystemError("unlinking local socket lock");
          }

	  free(path);
	}
      }
#endif /* PF_LOCAL */
#endif /* __MINGW32__ */
    }

    free(info->port);
    info->port = NULL;

    free(info->host);
    info->host = NULL;
  }
}

/* Function: addTtyFds */
/* recursively add fds of ttys */
#ifdef __MINGW32__
static void addTtyFds(HANDLE **lpHandles, int *nbAlloc, int *nbHandles, Tty *tty) {
#else /* __MINGW32__ */
static void addTtyFds(fd_set *fds, int *fdmax, Tty *tty) {
#endif /* __MINGW32__ */
  {
    Connection *c;
    for (c = tty->connections->next; c != tty->connections; c = c -> next) {
#ifdef __MINGW32__
      if (*nbHandles == *nbAlloc) {
	*nbAlloc *= 2;
	*lpHandles = realloc(*lpHandles,*nbAlloc*sizeof(**lpHandles));
      }
      (*lpHandles)[(*nbHandles)++] = c->packet.overl.hEvent;
#else /* __MINGW32__ */
      if (c->fd>*fdmax) *fdmax = c->fd;
      FD_SET(c->fd,fds);
#endif /* __MINGW32__ */
    }
  }
  {
    Tty *t;
    for (t = tty->subttys; t; t = t->next)
#ifdef __MINGW32__
      addTtyFds(lpHandles, nbAlloc, nbHandles, t);
#else /* __MINGW32__ */
      addTtyFds(fds,fdmax,t);
#endif /* __MINGW32__ */
  }
}

/* Function: handleTtyFds */
/* recursively handle ttys' fds */
static void handleTtyFds(fd_set *fds, time_t currentTime, Tty *tty) {
  {
    Connection *c,*next;
    c = tty->connections->next;

    while (c != tty->connections) {
      int remove = 0;
      next = c->next;

#ifdef __MINGW32__
      if (WaitForSingleObject(c->packet.overl.hEvent, 0) == WAIT_OBJECT_0)
#else /* __MINGW32__ */
      if (FD_ISSET(c->fd, fds))
#endif /* __MINGW32__ */
      {
	remove = processRequest(c, &packetHandlers);
      } else {
        remove = (c->auth != 1) && ((currentTime - c->upTime) > UNAUTH_TIMEOUT);
      }

#ifndef __MINGW32__
      FD_CLR(c->fd,fds);
#endif /* __MINGW32__ */

      if (remove) removeFreeConnection(c);
      c = next;
    }
  }

  {
    Tty *t,*next;
    for (t = tty->subttys; t; t = next) {
      next = t->next;
      handleTtyFds(fds,currentTime,t);
    }
  }
  if (tty!=&ttys && tty!=&notty
      && tty->connections->next == tty->connections && !tty->subttys) {
    logMessage(LOG_CATEGORY(SERVER_EVENTS), "freeing tty %#010x",tty->number);
    lockMutex(&apiConnectionsMutex);
    removeTty(tty);
    freeTty(tty);
    unlockMutex(&apiConnectionsMutex);
  }
}

#ifndef __MINGW32__
static sigset_t blockedSignalsMask;

static void initializeBlockedSignalsMask(void)
{
  sigemptyset(&blockedSignalsMask);

  sigaddset(&blockedSignalsMask, SIGTERM);
  sigaddset(&blockedSignalsMask, SIGINT);
  sigaddset(&blockedSignalsMask, SIGPIPE);
  sigaddset(&blockedSignalsMask, SIGCHLD);
  sigaddset(&blockedSignalsMask, SIGUSR1);
}
#endif /* __MINGW32__ */

static int prepareThread(void)
{
#ifndef __MINGW32__
  if (pthread_sigmask(SIG_BLOCK, &blockedSignalsMask, NULL) != 0) {
    logSystemError("pthread_sigmask[SIG_BLOCK]");
    return 0;
  }
#endif /* __MINGW32__ */

  return 1;
}

THREAD_FUNCTION(createServerSocket) {
  intptr_t num = (intptr_t) argument;
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "socket creation started: %"PRIdPTR, num);

  if (prepareThread()) {
    createSocket(num);
  }

  lockMutex(&serverSocketsMutex);
    serverSocketsPending -= 1;
  unlockMutex(&serverSocketsMutex);

  logMessage(LOG_CATEGORY(SERVER_EVENTS), "socket creation finished: %"PRIdPTR, num);
  return NULL;
}

/* Function : runServer */
/* The server thread */
/* Returns NULL in any case */
THREAD_FUNCTION(runServer) {
  char *hosts = (char *)argument;
  pthread_attr_t attr;
  int i;
  int res;
  struct sockaddr_storage addr;
  socklen_t addrlen;
  Connection *c;
  time_t currentTime;
  fd_set sockset;
  FileDescriptor resfd;

#ifdef __MINGW32__
  HANDLE *lpHandles;
  int nbAlloc;
  int nbHandles = 0;
#else /* __MINGW32__ */
  int fdmax;
#endif /* __MINGW32__ */

  logMessage(LOG_CATEGORY(SERVER_EVENTS), "server thread started");
  if (!prepareThread()) goto finished;

  if (auth && !isAbsolutePath(auth)) {
    if (!(authDescriptor = authBeginServer(auth))) {
      logMessage(LOG_WARNING, "Unable to start auth server");
      goto finished;
    }
  }

  socketHosts = splitString(hosts,'+',&serverSocketCount);
  if (serverSocketCount > SERVER_SOCKET_LIMIT) {
    logMessage(LOG_ERR, "too many hosts specified: %d > %d)",
               serverSocketCount, SERVER_SOCKET_LIMIT);
    goto finished;
  }
  if (serverSocketCount == 0) {
    logMessage(LOG_INFO,"no hosts specified");
    goto finished;
  }
#ifdef __MINGW32__
  nbAlloc = serverSocketCount;
#endif /* __MINGW32__ */

  pthread_attr_init(&attr);
  /* don't care if it fails */
  pthread_attr_setstacksize(&attr,stackSize);

  for (i=0;i<serverSocketCount;i++)
    socketInfo[i].fd = INVALID_FILE_DESCRIPTOR;

#ifdef __MINGW32__
  if ((getaddrinfoProc && WSAStartup(MAKEWORD(2,0), &wsadata))
	|| (!getaddrinfoProc && WSAStartup(MAKEWORD(1,1), &wsadata))) {
    logWindowsSocketError("Starting socket library");
    goto finished;
  }
#endif /* __MINGW32__ */

#ifdef __MINGW32__
  pthread_cleanup_push(closeSockets,NULL);
#endif /* __MINGW32__ */

  {
    pthread_mutexattr_t attributes;
    pthread_mutexattr_init(&attributes);
    pthread_mutex_init(&serverSocketsMutex, &attributes);
    serverSocketsPending = serverSocketCount;
  }

  for (i=0;i<serverSocketCount;i++) {
    socketInfo[i].addrfamily=brlapiserver_expandHost(socketHosts[i],&socketInfo[i].host,&socketInfo[i].port);

#ifdef __MINGW32__
    if (socketInfo[i].addrfamily != PF_LOCAL) {
#endif /* __MINGW32__ */
      {
        char name[0X100];
        snprintf(name, sizeof(name), "server-socket-create-%d", i);

        res = createThread(name, &socketThreads[i], &attr,
                           createServerSocket, (void *)(intptr_t)i);
      }

      if (res != 0) {
	logMessage(LOG_WARNING,"pthread_create: %s",strerror(res));

	for (i--;i>=0;i--) {
#ifdef __MINGW32__
	  pthread_cancel(socketThreads[i]);
#else /* __MINGW32__ */
	  pthread_kill(socketThreads[i], SIGUSR2);
#endif /* __MINGW32__ */
	  pthread_join(socketThreads[i], NULL);
        }

	goto finished;
      }

#ifdef __MINGW32__
    } else {
      /* Windows doesn't have trouble with local sockets on read-only
       * filesystems, but it has with inter-thread overlapped operations,
       * so call from here
       */
      createSocket(i);
    }
#endif /* __MINGW32__ */
  }

  unauthConnections = 0;
  unauthConnLog = 0;

  while (running) {
#ifdef __MINGW32__
    lpHandles = malloc(nbAlloc * sizeof(*lpHandles));
    nbHandles = 0;

    for (i=0;i<serverSocketCount;i++) {
      if (socketInfo[i].fd != INVALID_HANDLE_VALUE) {
	lpHandles[nbHandles++] = socketInfo[i].overl.hEvent;
      }
    }

    lockMutex(&apiConnectionsMutex);
    addTtyFds(&lpHandles, &nbAlloc, &nbHandles, &notty);
    addTtyFds(&lpHandles, &nbAlloc, &nbHandles, &ttys);
    unlockMutex(&apiConnectionsMutex);

    if (!nbHandles) {
      free(lpHandles);
      approximateDelay(1000);
      continue;
    }

    switch (WaitForMultipleObjects(nbHandles, lpHandles, FALSE, 1000)) {
      case WAIT_TIMEOUT:
        continue;

      case WAIT_FAILED:
        logWindowsSystemError("WaitForMultipleObjects");
        break;
    }

    free(lpHandles);
#else /* __MINGW32__ */
    /* Compute sockets set and fdmax */
    FD_ZERO(&sockset);
    fdmax=0;

    lockMutex(&apiConnectionsMutex);
    addTtyFds(&sockset, &fdmax, &notty);
    addTtyFds(&sockset, &fdmax, &ttys);
    unlockMutex(&apiConnectionsMutex);

    {
      struct timeval tv, *timeout;

      lockMutex(&serverSocketsMutex);
	for (i=0;i<serverSocketCount;i++) {
	  if (socketInfo[i].fd>=0) {
	    FD_SET(socketInfo[i].fd, &sockset);

	    if (socketInfo[i].fd>fdmax) {
	      fdmax = socketInfo[i].fd;
	    }
	  }
	}

        if (unauthConnections || serverSocketsPending) {
          memset(&tv, 0, sizeof(tv));
          tv.tv_sec = SERVER_SELECT_TIMEOUT;
          timeout = &tv;
        } else {
          timeout = NULL;
        }
      unlockMutex(&serverSocketsMutex);

      if (select(fdmax+1, &sockset, NULL, NULL, timeout) < 0) {
        if (fdmax==0) continue; /* still no server socket */
        logMessage(LOG_WARNING,"select: %s",strerror(errno));
        break;
      }
    }
#endif /* __MINGW32__ */

    time(&currentTime);

    for (i=0;i<serverSocketCount;i++) {
      char source[0X100];

#ifdef __MINGW32__
      if (socketInfo[i].fd != INVALID_FILE_DESCRIPTOR &&
          WaitForSingleObject(socketInfo[i].overl.hEvent, 0) == WAIT_OBJECT_0) {
        if (socketInfo[i].addrfamily == PF_LOCAL) {
          DWORD foo;

          if (!(GetOverlappedResult(socketInfo[i].fd, &socketInfo[i].overl, &foo, FALSE))) {
            logWindowsSystemError("GetOverlappedResult");
          }

          resfd = socketInfo[i].fd;
          if ((socketInfo[i].fd = createLocalSocket(&socketInfo[i])) != INVALID_FILE_DESCRIPTOR) {
            logMessage(LOG_CATEGORY(SERVER_EVENTS), "socket %d re-established (fd %"PRIfd", was %"PRIfd")",i,socketInfo[i].fd,resfd);
          }

          snprintf(source, sizeof(source), BRLAPI_SOCKETPATH "%s", socketInfo[i].port);
        } else {
          if (!ResetEvent(socketInfo[i].overl.hEvent)) {
            logWindowsSystemError("ResetEvent in server loop");
          }
#else /* __MINGW32__ */
      if (socketInfo[i].fd>=0 && FD_ISSET(socketInfo[i].fd, &sockset)) {
#endif /* __MINGW32__ */
          addrlen = sizeof(addr);
          resfd = (FileDescriptor)accept((SocketDescriptor)socketInfo[i].fd, (struct sockaddr *) &addr, &addrlen);

          if (resfd == INVALID_FILE_DESCRIPTOR) {
            setSocketErrno();
            logMessage(LOG_WARNING,"accept(%"PRIfd"): %s",socketInfo[i].fd,strerror(errno));
            continue;
          }

          formatAddress(source, sizeof(source), &addr, addrlen);
#ifdef __MINGW32__
        }
#endif /* __MINGW32__ */
        logMessage(LOG_NOTICE, "BrlAPI connection fd=%"PRIfd" accepted: %s", resfd, source);

        if (unauthConnections >= UNAUTH_LIMIT) {
          writeError(resfd, BRLAPI_ERROR_CONNREFUSED);
          closeFileDescriptor(resfd);

          if (unauthConnLog==0) {
            logMessage(LOG_WARNING, "Too many simultaneous unauthorized connections");
          }

          unauthConnLog++;
        } else {
#ifndef __MINGW32__
          if (!setBlockingIo(resfd, 0)) {
            logMessage(LOG_WARNING, "Failed to switch to non-blocking mode: %s",strerror(errno));
            break;
          }
#endif /* __MINGW32__ */

          c = createConnection(resfd, currentTime);
          if (c==NULL) {
            logMessage(LOG_WARNING,"Failed to create connection structure");
            closeFileDescriptor(resfd);
          } else {
	    unauthConnections++;
	    addConnection(c, notty.connections);
	    handleNewConnection(c);
	  }
        }
      }
    }

    handleTtyFds(&sockset,currentTime,&notty);
    handleTtyFds(&sockset,currentTime,&ttys);
  }

  running = 0;
#ifdef __MINGW32__
  pthread_cleanup_pop(1);
#else /* __MINGW32__ */
  closeSockets(NULL);
#endif /* __MINGW32__ */

finished:
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "server thread finished");
  return NULL;
}

/****************************************************************************/
/** MISCELLANEOUS FUNCTIONS                                                **/
/****************************************************************************/

/* Function : initializeAcceptedKeys */
/* Specify which keys should be passed to the client by default, as soon */
/* as it controls the tty */
/* If client asked for commands, one lets it process routing cursor */
/* and screen-related commands */
/* If the client is interested in braille codes, one passes it nothing */
/* to let the user read the screen in case theree is an error */
static int initializeAcceptedKeys(Connection *c, int how)
{
  if (how == BRL_KEYCODES) {
    if (c && addKeyrange(0, BRLAPI_KEY_MAX, &c->acceptedKeys) == -1) return -1;
  } else {
    if (c) {
      typedef struct {
        int (*action) (brlapi_keyCode_t first, brlapi_keyCode_t last, KeyrangeList **list);
        brlapi_rangeType_t type;
        brlapi_keyCode_t code;
      } KeyrangeEntry;

      static const KeyrangeEntry keyrangeTable[] = {
        { .action = addKeyrange,
          .type = brlapi_rangeType_all,
          .code = 0
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_OFFLINE
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_NOOP
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_RESTARTBRL
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_BRL_START
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_BRL_STOP
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_RESTARTSPEECH
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPK_START
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPK_STOP
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SCR_START
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SCR_STOP
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SWITCHVT
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SWITCHVT_PREV
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SWITCHVT_NEXT
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SELECTVT
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SELECTVT_PREV
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SELECTVT_NEXT
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PASSXT
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PASSAT
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PASSPS2
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CONTEXT
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ALERT
        },

        { .action = removeKeyrange,
          .type = brlapi_rangeType_command,
          .code = BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PASSDOTS
        },

        { .action = NULL }
      };

      const KeyrangeEntry *keyrange = keyrangeTable;

      while (keyrange->action) {
        brlapi_keyCode_t first;
        brlapi_keyCode_t mask;
        brlapi_keyCode_t last;

        first = keyrange->code;
        if (brlapiserver_getKeyrangeMask(keyrange->type, first, &mask) == -1) return -1;
        last = first | mask;

        if (keyrange->action(first, last, &c->acceptedKeys) == -1) return -1;
        keyrange += 1;
      }
    }
  }

  return 0;
}

/* Function: ttyTerminationHandler */
/* Recursively removes connections */
static void ttyTerminationHandler(Tty *tty)
{
  while (tty->connections->next != tty->connections) {
    removeFreeConnection(tty->connections->next);
  }
  freeConnection(tty->connections);

  {
    Tty *t = tty->subttys;

    while (t) {
      ttyTerminationHandler(t);
      t = t->next;
    }
  }

  if (flushEvent) {
    asyncDiscardEvent(flushEvent);
    flushEvent = NULL;
  }
}
/* Function : terminationHandler */
/* Terminates driver */
static void terminationHandler(void)
{
  int res;
  running = 0;

#ifdef __MINGW32__
  res = pthread_cancel(serverThread);
#else /* __MINGW32__ */
  res = pthread_kill(serverThread, SIGUSR2);
#endif /* __MINGW32__ */
  pthread_join(serverThread, NULL);

  if (res != 0) {
    logMessage(LOG_WARNING,"pthread_cancel: %s",strerror(res));
  }

  ttyTerminationHandler(&notty);
  ttyTerminationHandler(&ttys);

  if (authDescriptor) {
    authEnd(authDescriptor);
    authDescriptor = NULL;
  }

#ifdef __MINGW32__
  WSACleanup();
#endif /* __MINGW32__ */

  if (socketHosts) {
    deallocateStrings(socketHosts);
    socketHosts = NULL;
  }
}

/* Function: whoFillsTty */
/* Returns the connection which fills the tty */
static Connection *whoFillsTty(Tty *tty) {
  Connection *c;
  Tty *t;
  for (c=tty->connections->next; c!=tty->connections; c = c->next)
    if (c->brlbufstate!=EMPTY) goto found;

  c = NULL;
found:
  for (t = tty->subttys; t; t = t->next)
    if (tty->focus==SCR_NO_VT || t->number == tty->focus) {
      Connection *recur_c = whoFillsTty(t);
      return recur_c ? recur_c : c;
    }
  return c;
}

static inline void setCurrentRootTty(void) {
  ttys.focus = currentVirtualTerminal();
}

/* Function : api_writeWindow */
static int api_writeWindow(BrailleDisplay *brl, const wchar_t *text)
{
  int ok = 1;
  if (text)
    memcpy(coreWindowText, text, displaySize * sizeof(*coreWindowText));
  else
    memset(coreWindowText, 0, displaySize * sizeof(*coreWindowText));
  memcpy(coreWindowDots, brl->buffer, displaySize * sizeof(*coreWindowDots));
  coreWindowCursor = brl->cursor;
  setCurrentRootTty();
  lockMutex(&apiConnectionsMutex);
  lockMutex(&apiRawMutex);
  if (!offline && !suspendConnection && !rawConnection && !whoFillsTty(&ttys)) {
    lockMutex(&apiDriverMutex);
    if (!trueBraille->writeWindow(brl, text)) ok = 0;
    unlockMutex(&apiDriverMutex);
  }
  unlockMutex(&apiRawMutex);
  unlockMutex(&apiConnectionsMutex);
  return ok;
}

/* Function: whoGetsKey */
/* Returns the connection which gets that key */
static Connection *whoGetsKey(Tty *tty, brlapi_keyCode_t code, unsigned int how)
{
  Connection *c;
  Tty *t;
  int passKey;
  for (c=tty->connections->next; c!=tty->connections; c = c->next) {
    lockMutex(&c->acceptedKeysMutex);
    passKey = (c->how==how) && (inKeyrangeList(c->acceptedKeys,code) != NULL);
    unlockMutex(&c->acceptedKeysMutex);
    if (passKey) goto found;
  }
  c = NULL;
found:
  for (t = tty->subttys; t; t = t->next)
    if (tty->focus==SCR_NO_VT || t->number == tty->focus) {
      Connection *recur_c = whoGetsKey(t, code, how);
      return recur_c ? recur_c : c;
    }
  return c;
}

/* Temporary function, until we implement proper generic support for variables.
 */
static void broadcastKey(Tty *tty, brlapi_keyCode_t code, unsigned int how) {
  Connection *c;
  Tty *t;
  for (c=tty->connections->next; c!=tty->connections; c = c->next) {
    lockMutex(&c->acceptedKeysMutex);
    if ((c->how==how) && (inKeyrangeList(c->acceptedKeys,code) != NULL))
      writeKey(c->fd,code);
    unlockMutex(&c->acceptedKeysMutex);
  }
  for (t = tty->subttys; t; t = t->next)
    broadcastKey(t, code, how);
}

/* The core produced a key event, try to send it to a brlapi client. */
static int api__handleKeyEvent(brlapi_keyCode_t clientCode) {
  Connection *c;

  if (offline) {
    broadcastKey(&ttys, BRLAPI_KEY_TYPE_CMD|BRLAPI_KEY_CMD_NOOP, BRL_COMMANDS);
    offline = 0;
  }
  /* somebody gets the raw code */
  if ((c = whoGetsKey(&ttys,clientCode,BRL_KEYCODES))) {
    logMessage(LOG_CATEGORY(SERVER_EVENTS), "transmitting accepted key %016"BRLAPI_PRIxKEYCODE" to fd %"PRIfd,clientCode,c->fd);
    writeKey(c->fd,clientCode);
    return 1;
  }
  return 0;
}

int api_handleKeyEvent(KeyGroup group, KeyNumber number, int press) {
  int ret;
  brlapi_keyCode_t clientCode;
  clientCode = ((brlapi_keyCode_t)group << 8) | number | ((brlapi_keyCode_t)press << 63);
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "API got key %02x %02x (press %d), thus client code %016"BRLAPI_PRIxKEYCODE, group, number, press, clientCode);

  lockMutex(&apiConnectionsMutex);
  ret = api__handleKeyEvent(clientCode);
  unlockMutex(&apiConnectionsMutex);
  return ret;
}

/* The core produced a command, try to send it to a brlapi client.
 * Return true if handled and false otherwise.  */
static int api__handleCommand(int command) {
  if (command == BRL_CMD_OFFLINE) {
    if (!offline) {
      broadcastKey(&ttys, BRLAPI_KEY_TYPE_CMD|BRLAPI_KEY_CMD_OFFLINE, BRL_COMMANDS);
      offline = 1;
    }

    return 0;
  }

  if (offline) {
    broadcastKey(&ttys, BRLAPI_KEY_TYPE_CMD|BRLAPI_KEY_CMD_NOOP, BRL_COMMANDS);
    offline = 0;
  }

  if (command != EOF) {
    Connection *c = NULL;
    brlapi_keyCode_t code;
    if (!cmdBrlttyToBrlapi(&code, command, 1)) {
      logMessage(LOG_CATEGORY(SERVER_EVENTS), "command %08x could not be converted to BrlAPI with retaindots", command);
    } else {
      logMessage(LOG_CATEGORY(SERVER_EVENTS), "command %08x -> client code %016"BRLAPI_PRIxKEYCODE, command, code);
      c = whoGetsKey(&ttys, code, BRL_COMMANDS);
    }

    if (!c) {
      brlapi_keyCode_t alternate;
      if (!cmdBrlttyToBrlapi(&alternate, command, 0)) {
	logMessage(LOG_CATEGORY(SERVER_EVENTS), "command %08x could not be converted to BrlAPI without retaindots", command);
      } else {
	if (alternate != code) {
	  logMessage(LOG_CATEGORY(SERVER_EVENTS), "command %08x -> client code %016"BRLAPI_PRIxKEYCODE, command, alternate);
	  c = whoGetsKey(&ttys, alternate, BRL_COMMANDS);
	  if (c) code = alternate;
	}
      }
    }

    if (c) {
      logMessage(LOG_CATEGORY(SERVER_EVENTS), "transmitting accepted command %lx as client code %016"BRLAPI_PRIxKEYCODE" to fd %"PRIfd,(unsigned long)command,code,c->fd);
      writeKey(c->fd, code);
      return 1;
    }
  }

  return 0;
}

int api_handleCommand(int command) {
  int handled;

  lockMutex(&apiConnectionsMutex);
  handled = api__handleCommand(command);
  unlockMutex(&apiConnectionsMutex);

  return handled;
}

/* Function : api_readCommand
 * Call driver->readCommand unless the driver is suspended.
 */
static int api_readCommand(BrailleDisplay *brl, KeyTableCommandContext context) {
  ssize_t size;
  brlapi_packet_t packet;
  int res;
  int command = EOF;

  lockMutex(&apiConnectionsMutex);
  lockMutex(&apiRawMutex);
  if (suspendConnection || !driverConstructed) {
    unlockMutex(&apiRawMutex);
    goto out;
  }
  if (rawConnection!=NULL) {
    lockMutex(&apiDriverMutex);
    size = trueBraille->readPacket(brl, &packet.data, BRLAPI_MAXPACKETSIZE);
    unlockMutex(&apiDriverMutex);
    if (size<0)
      writeException(rawConnection->fd, BRLAPI_ERROR_DRIVERERROR, BRLAPI_PACKET_PACKET, NULL, 0);
    else if (size)
      brlapiserver_writePacket(rawConnection->fd,BRLAPI_PACKET_PACKET,&packet.data,size);
    unlockMutex(&apiRawMutex);
    goto out;
  }

  lockMutex(&apiDriverMutex);
  res = trueBraille->readCommand(brl,context);
  unlockMutex(&apiDriverMutex);
  if (brl->resizeRequired)
    handleResize(brl);
  command = res;
  /* some client may get raw mode only from now */
  unlockMutex(&apiRawMutex);
out:
  unlockMutex(&apiConnectionsMutex);
  return command;
}

/* Function : api_flush
 * Flush writes to the braille device.
 */
int api_flush(BrailleDisplay *brl) {
  Connection *c;
  static Connection *displayed_last;
  int ok = 1;
  int drain = 0;
  int update = 0;

  lockMutex(&apiConnectionsMutex);
  lockMutex(&apiRawMutex);
  if (suspendConnection) {
    unlockMutex(&apiRawMutex);
    goto out;
  }
  setCurrentRootTty();
  c = whoFillsTty(&ttys);
  if (!offline && c) {
    lockMutex(&c->brailleWindowMutex);
    lockMutex(&apiDriverMutex);
    if (!driverConstructed && !driverConstructing) {
      if (!resumeDriver(brl)) {
	unlockMutex(&apiDriverMutex);
	unlockMutex(&c->brailleWindowMutex);
        unlockMutex(&apiRawMutex);
	goto out;
      }
    }

    if (c->brailleWindow.cursor) {
      unsigned char newCursorOverlay = getCursorOverlay(brl);

      if (newCursorOverlay != cursorOverlay) {
        cursorOverlay = newCursorOverlay;
        update = 1;
      }
    }

    if (c != displayed_last || c->brlbufstate==TODISPLAY || update) {
      unsigned char *oldbuf = disp->buffer, buf[displaySize];
      disp->buffer = buf;
      getDots(&c->brailleWindow, buf);
      brl->cursor = c->brailleWindow.cursor-1;
      ok = trueBraille->writeWindow(brl, c->brailleWindow.text);
      drain = 1;
      disp->buffer = oldbuf;
      displayed_last = c;
    }
    unlockMutex(&apiDriverMutex);
    unlockMutex(&c->brailleWindowMutex);
  } else {
    /* no RAW, no connection filling tty, hence suspend if needed */
    lockMutex(&apiDriverMutex);
    if (!coreActive) {
      if (driverConstructed) {
	/* Put back core output before suspending */
	unsigned char *oldbuf = disp->buffer;
	disp->buffer = coreWindowDots;
	brl->cursor = coreWindowCursor;
	trueBraille->writeWindow(brl, coreWindowText);
	disp->buffer = oldbuf;
	suspendDriver(brl);
      }
      unlockMutex(&apiDriverMutex);
      unlockMutex(&apiRawMutex);
      goto out;
    }
    unlockMutex(&apiDriverMutex);
  }
  if (!ok) {
    unlockMutex(&apiRawMutex);
    goto out;
  }
  if (drain)
    drainBrailleOutput(brl, 0);
  unlockMutex(&apiRawMutex);
out:
  unlockMutex(&apiConnectionsMutex);
  return ok;
}

ASYNC_EVENT_CALLBACK(handleServerFlushEvent) {
  BrailleDisplay *brl = parameters->eventData;
  api_flush(brl);
  resetAllBlinkDescriptors();
}

int api_resume(BrailleDisplay *brl) {
  /* core is resuming or opening the device for the first time, let's try to go
   * to normal state */
  lockMutex(&apiRawMutex);
  lockMutex(&apiDriverMutex);
  if (!suspendConnection && !driverConstructed)
    resumeDriver(brl);
  unlockMutex(&apiDriverMutex);
  unlockMutex(&apiRawMutex);
  return (coreActive = driverConstructed);
}

/* try to get access to device. If suspended, returns 0 */
int api_claimDriver (BrailleDisplay *brl)
{
  int ret;
  lockMutex(&apiSuspendMutex);
  ret = driverConstructed;
  unlockMutex(&apiSuspendMutex);
  return ret;
}

void api_releaseDriver(BrailleDisplay *brl)
{
  unlockMutex(&apiSuspendMutex);
}

void api_suspend(BrailleDisplay *brl) {
  /* core is suspending, going to core suspend state */
  coreActive = 0;
  asyncSignalEvent(flushEvent, NULL);
}

static void brlResize(BrailleDisplay *brl)
{
  /* TODO: handle clients' resize */
  displayDimensions[0] = htonl(brl->textColumns);
  displayDimensions[1] = htonl(brl->textRows);
  displaySize = brl->textColumns * brl->textRows;
  coreWindowText = realloc(coreWindowText, displaySize * sizeof(*coreWindowText));
  coreWindowDots = realloc(coreWindowDots, displaySize * sizeof(*coreWindowDots));
  coreWindowCursor = 0;
  disp = brl;
}

REPORT_LISTENER(brlapi_handleReports)
{
  if (parameters->reportIdentifier == REPORT_BRAILLE_DEVICE_ONLINE) {
    BrailleDisplay *brl = parameters->listenerData;
    api_flush(brl);
    resetAllBlinkDescriptors();
  }
}

static ReportListenerInstance *api_reportListener;

/* Function : api_link */
/* Does all the link stuff to let api get events from the driver and */
/* writes from brltty */
void api_link(BrailleDisplay *brl)
{
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "api link");
  trueBraille=braille;
  memcpy(&ApiBraille,braille,sizeof(BrailleDriver));
  ApiBraille.writeWindow=api_writeWindow;
  ApiBraille.readCommand=api_readCommand;
  ApiBraille.readKey = NULL;
  ApiBraille.keyToCommand = NULL;
  ApiBraille.readPacket = NULL;
  ApiBraille.writePacket = NULL;
  braille=&ApiBraille;
  lockMutex(&apiDriverMutex);
  brlResize(brl);
  driverConstructed=1;
  unlockMutex(&apiDriverMutex);
  lockMutex(&apiConnectionsMutex);
  broadcastKey(&ttys, BRLAPI_KEY_TYPE_CMD|BRLAPI_KEY_CMD_NOOP, BRL_COMMANDS);
  unlockMutex(&apiConnectionsMutex);
  api_reportListener = registerReportListener(REPORT_BRAILLE_DEVICE_ONLINE, brlapi_handleReports, brl);
}

/* Function : api_unlink */
/* Does all the unlink stuff to remove api from the picture */
void api_unlink(BrailleDisplay *brl)
{
  logMessage(LOG_CATEGORY(SERVER_EVENTS), "api unlink");
  unregisterReportListener(api_reportListener);
  lockMutex(&apiConnectionsMutex);
  broadcastKey(&ttys, BRLAPI_KEY_TYPE_CMD|BRLAPI_KEY_CMD_OFFLINE, BRL_COMMANDS);
  unlockMutex(&apiConnectionsMutex);
  free(coreWindowText);
  coreWindowText = NULL;
  free(coreWindowDots);
  coreWindowDots = NULL;
  braille=trueBraille;
  trueBraille=&noBraille;
  lockMutex(&apiDriverMutex);
  if (!coreActive && driverConstructed)
    suspendDriver(disp);
  driverConstructed=0;
  disp = NULL;
  unlockMutex(&apiDriverMutex);
}

/* Function : api_identify */
/* Identifies BrlApi */
void api_identify(int full)
{
  logMessage(LOG_NOTICE, RELEASE);
  if (full) {
    logMessage(LOG_INFO, COPYRIGHT);
  }
}

/* Function : api_start */
/* Initializes BrlApi */
/* One first initialize the driver */
/* Then one creates the communication socket */
int api_start(BrailleDisplay *brl, char **parameters)
{
  int res,i;

  char *hosts =
#if defined(PF_LOCAL)
	":0+127.0.0.1:0"
#else /* PF_LOCAL */
	"127.0.0.1:0"
#endif /* PF_LOCAL */
	;

  {
    char *operand = parameters[PARM_HOST];

    if (*operand) hosts = operand;
  }

  stackSize = MAX(PTHREAD_STACK_MIN, OUR_STACK_MIN);
  {
    const char *operand = parameters[PARM_STACKSIZE];

    if (*operand) {
      int size;
      static const int minSize = PTHREAD_STACK_MIN;

      if (validateInteger(&size, operand, &minSize, NULL)) {
        stackSize = size;
      } else {
        logMessage(LOG_WARNING, "%s: %s", gettext("invalid thread stack size"), operand);
      }
    }
  }

  auth = BRLAPI_DEFAUTH;
  {
    const char *operand = parameters[PARM_AUTH];

    if (*operand) auth = operand;
  }

  pthread_attr_t attr;
  pthread_mutexattr_t mattr;

  coreActive=1;

  if ((notty.connections = createConnection(INVALID_FILE_DESCRIPTOR,0)) == NULL) {
    logMessage(LOG_WARNING, "Unable to create connections list");
    goto noNottyConnections;
  }
  notty.connections->prev = notty.connections->next = notty.connections;
  if ((ttys.connections = createConnection(INVALID_FILE_DESCRIPTOR, 0)) == NULL) {
    logMessage(LOG_WARNING, "Unable to create ttys' connections list");
    goto noTtysConnections;
  }
  ttys.connections->prev = ttys.connections->next = ttys.connections;
  ttys.focus = SCR_NO_VT;

  pthread_mutexattr_init(&mattr);
  pthread_mutexattr_settype(&mattr, PTHREAD_MUTEX_RECURSIVE);

  pthread_mutex_init(&apiConnectionsMutex,&mattr);
  pthread_mutex_init(&apiDriverMutex,&mattr);
  pthread_mutex_init(&apiRawMutex,&mattr);
  pthread_mutex_init(&apiSuspendMutex,&mattr);

  pthread_attr_init(&attr);
  pthread_attr_setstacksize(&attr,stackSize);

  if (!(flushEvent = asyncNewEvent(handleServerFlushEvent, brl))) goto noFlushEvent;

#ifndef __MINGW32__
  initializeBlockedSignalsMask();
  asyncHandleSignal(SIGUSR2, asyncEmptySignalHandler, NULL);
#endif /* __MINGW32__ */

  running = 1;
  trueBraille=&noBraille;

  if ((res = createThread("server-main", &serverThread, &attr,
                          runServer, hosts)) != 0) {
    logMessage(LOG_WARNING,"pthread_create: %s",strerror(res));
    running = 0;

    for (i=0;i<serverSocketCount;i++) {
#ifdef __MINGW32__
      pthread_cancel(socketThreads[i]);
#else /* __MINGW32__ */
      pthread_kill(socketThreads[i], SIGUSR2);
#endif /* __MINGW32__ */
      pthread_join(socketThreads[i], NULL);
    }

    goto noServerThread;
  }

  return 1;

noServerThread:
  asyncDiscardEvent(flushEvent);
noFlushEvent:
  freeConnection(ttys.connections);
noTtysConnections:
  freeConnection(notty.connections);
noNottyConnections:
  authEnd(authDescriptor);
  authDescriptor = NULL;
  return 0;
}

/* Function : api_stop */
/* End of BrlApi session. Closes the listening socket */
/* destroys opened connections and associated resources */
/* Closes the driver */
void api_stop(BrailleDisplay *brl)
{
  terminationHandler();
}

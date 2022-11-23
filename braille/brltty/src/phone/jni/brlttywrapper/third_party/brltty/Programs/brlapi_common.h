/*
 * libbrlapi - A library providing access to braille terminals for applications.
 *
 * Copyright (C) 2002-2019 by
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

/* api_common.h - private definitions shared by both server & client */

#include <stdio.h>
#include <stddef.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>

#ifdef __MINGW32__
#include <io.h>
#else /* __MINGW32__ */
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#endif /* __MINGW32__ */

#include "brlapi_protocol.h"

#if !defined(AF_LOCAL) && defined(AF_UNIX)
#define AF_LOCAL AF_UNIX
#endif /* !defined(AF_LOCAL) && defined(AF_UNIX) */

#if !defined(PF_LOCAL) && defined(PF_UNIX)
#define PF_LOCAL PF_UNIX
#endif /* !defined(PF_LOCAL) && defined(PF_UNIX) */

#ifndef MIN
#define MIN(a, b) (((a) < (b))? (a): (b))
#endif /* MIN */

#ifndef MAX
#define MAX(a, b) (((a) > (b))? (a): (b))
#endif /* MAX */

#ifdef __MINGW32__
#define get_osfhandle(fd) _get_osfhandle(fd)
#endif /* __MINGW32__ */

#define LibcError(function) \
  brlapi_errno=BRLAPI_ERROR_LIBCERR; \
  brlapi_libcerrno = errno; \
  brlapi_errfun = function;

/* brlapi_writeFile */
/* Writes a buffer to a file */
static ssize_t brlapi_writeFile(brlapi_fileDescriptor fd, const void *buffer, size_t size)
{
  const unsigned char *buf = buffer;
  size_t n;
#ifdef __MINGW32__
  DWORD res=0;
#else /* __MINGW32__ */
  ssize_t res=0;
#endif /* __MINGW32__ */
  for (n=0;n<size;n+=res) {
#ifdef __MINGW32__
    OVERLAPPED overl = {0, 0, {{0, 0}}, CreateEvent(NULL, TRUE, FALSE, NULL)};
    if ((!WriteFile(fd,buf+n,size-n,&res,&overl)
      && GetLastError() != ERROR_IO_PENDING) ||
      !GetOverlappedResult(fd, &overl, &res, TRUE)) {
      res = GetLastError();
      CloseHandle(overl.hEvent);
      setErrno(res);
      return -1;
    }
    CloseHandle(overl.hEvent);
#else /* __MINGW32__ */
    res=send(fd,buf+n,size-n,0);
    if ((res<0) &&
        (errno!=EINTR) &&
#ifdef EWOULDBLOCK
        (errno!=EWOULDBLOCK) &&
#endif /* EWOULDBLOCK */
        (errno!=EAGAIN)) { /* EAGAIN shouldn't happen, but who knows... */
      return res;
    }
#endif /* __MINGW32__ */
  }
  return n;
}

/* brlapi_readFile */
/* Reads a buffer from a file */
static ssize_t brlapi_readFile(brlapi_fileDescriptor fd, void *buffer, size_t size, int loop)
{
  unsigned char *buf = buffer;
  size_t n;
#ifdef __MINGW32__
  DWORD res=0;
#else /* __MINGW32__ */
  ssize_t res=0;
#endif /* __MINGW32__ */
  for (n=0;n<size && res>=0;n+=res) {
#ifdef __MINGW32__
    OVERLAPPED overl = {0, 0, {{0, 0}}, CreateEvent(NULL, TRUE, FALSE, NULL)};
    if ((!ReadFile(fd,buf+n,size-n,&res,&overl)
      && GetLastError() != ERROR_IO_PENDING) ||
      !GetOverlappedResult(fd, &overl, &res, TRUE)) {
      res = GetLastError();
      CloseHandle(overl.hEvent);
      if (res == ERROR_HANDLE_EOF) return n;
      setErrno(res);
      return -1;
    }
    CloseHandle(overl.hEvent);
#else /* __MINGW32__ */
    res=read(fd,buf+n,size-n);
    if (res<0) {
      if ((errno!=EINTR) &&
#ifdef EWOULDBLOCK
        (errno!=EWOULDBLOCK) &&
#endif /* EWOULDBLOCK */
        (errno!=EAGAIN)) { /* EAGAIN shouldn't happen, but who knows... */
	return -1;
      }
      if (!loop && !n) return -1; /* Nothing read yet, report EINTR */
      /* else, continue reading */
    }
#endif /* __MINGW32__ */
    if (res==0)
      /* Unexpected end of file ! */
      break;
  }
  return n;
}

typedef enum {
#ifdef __MINGW32__
  READY, /* but no pending ReadFile */
#endif /* __MINGW32__ */
  READING_HEADER,
  READING_CONTENT,
  DISCARDING
} PacketState;

typedef struct {
  brlapi_header_t header;
  uint32_t content[BRLAPI_MAXPACKETSIZE/sizeof(uint32_t)+1]; /* +1 for additional \0 */
  PacketState state;
  int readBytes; /* Already read bytes */
  unsigned char *p; /* Where read() should load datas */
  int n; /* Value to give so read() */
#ifdef __MINGW32__
  OVERLAPPED overl;
#endif /* __MINGW32__ */
} Packet;

/* Function: brlapi_resetPacket */
/* Resets a Packet structure */
static void brlapi_resetPacket(Packet *packet)
{
#ifdef __MINGW32__
  packet->state = READY;
#else /* __MINGW32__ */
  packet->state = READING_HEADER;
#endif /* __MINGW32__ */
  packet->readBytes = 0;
  packet->p = (unsigned char *) &packet->header;
  packet->n = sizeof(packet->header);
#ifdef __MINGW32__
  SetEvent(packet->overl.hEvent);
#endif /* __MINGW32__ */
}

/* Function: brlapi_initializePacket */
/* Prepares a Packet structure */
/* returns 0 on success, -1 on failure */
static int brlapi_initializePacket(Packet *packet)
{
#ifdef __MINGW32__
  memset(&packet->overl,0,sizeof(packet->overl));
  if (!(packet->overl.hEvent = CreateEvent(NULL, TRUE, TRUE, NULL))) {
    setSystemErrno();
    LibcError("CreateEvent for readPacket");
    return -1;
  }
#endif /* __MINGW32__ */
  brlapi_resetPacket(packet);
  return 0;
}

/* Function : readPacket */
/* Reads a packet for the given connection */
/* Returns -2 on EOF, -1 on error, 0 if the reading is not complete, */
/* 1 if the packet has been read. */
static int brlapi__readPacket(Packet *packet, brlapi_fileDescriptor descriptor)
{
#ifdef __MINGW32__
  DWORD res;
  if (packet->state!=READY) {
    /* pending read */
    if (!GetOverlappedResult(descriptor,&packet->overl,&res,FALSE)) {
      switch (GetLastError()) {
        case ERROR_IO_PENDING: return 0;
        case ERROR_HANDLE_EOF:
        case ERROR_BROKEN_PIPE: return -2;
        default: setSystemErrno(); LibcError("GetOverlappedResult"); return -1;
      }
    }
read:
#else /* __MINGW32__ */
  int res;
read:
  res = read(descriptor, packet->p, packet->n);
  if (res==-1) {
    switch (errno) {
      case EINTR: goto read;
      case EAGAIN: return 0;
      default: return -1;
    }
  }
#endif /* __MINGW32__ */
  if (res==0) return -2; /* EOF */
  packet->readBytes += res;
  if ((packet->state==READING_HEADER) && (packet->readBytes==BRLAPI_HEADERSIZE)) {
    packet->header.size = ntohl(packet->header.size);
    packet->header.type = ntohl(packet->header.type);
    if (packet->header.size==0) goto out;
    packet->readBytes = 0;
    if (packet->header.size<=BRLAPI_MAXPACKETSIZE) {
      packet->state = READING_CONTENT;
      packet->n = packet->header.size;
    } else {
      packet->state = DISCARDING;
      packet->n = BRLAPI_MAXPACKETSIZE;
    }
    packet->p = (unsigned char*) packet->content;
  } else if ((packet->state == READING_CONTENT) && (packet->readBytes==packet->header.size)) goto out;
  else if (packet->state==DISCARDING) {
    packet->p = (unsigned char *) packet->content;
    packet->n = MIN(packet->header.size-packet->readBytes, BRLAPI_MAXPACKETSIZE);
  } else {
    packet->n -= res;
    packet->p += res;
  }
#ifdef __MINGW32__
  } else packet->state = READING_HEADER;
  if (!ResetEvent(packet->overl.hEvent))
  {
    setSystemErrno();
    LibcError("ResetEvent in readPacket");
  }
  if (!ReadFile(descriptor, packet->p, packet->n, &res, &packet->overl)) {
    switch (GetLastError()) {
      case ERROR_IO_PENDING: return 0;
      case ERROR_HANDLE_EOF:
      case ERROR_BROKEN_PIPE: return -2;
      default: setSystemErrno(); LibcError("ReadFile"); return -1;
    }
  }
#endif /* __MINGW32__ */
  goto read;

out:
  brlapi_resetPacket(packet);
  return 1;
}

/* brlapi_writePacket */
/* Write a packet on the socket */
ssize_t BRLAPI(writePacket)(brlapi_fileDescriptor fd, brlapi_packetType_t type, const void *buf, size_t size)
{
  uint32_t header[2] = { htonl(size), htonl(type) };
  ssize_t res;

  /* first send packet header (size+type) */
  if ((res=brlapi_writeFile(fd,&header[0],sizeof(header)))<0) {
    LibcError("write in writePacket");
    return res;
  }

  /* eventually data */
  if (size && buf)
    if ((res=brlapi_writeFile(fd,buf,size))<0) {
      LibcError("write in writePacket");
      return res;
    }

  return 0;
}

/* brlapi_readPacketHeader */
/* Read a packet's header and return packet's size */
ssize_t BRLAPI(readPacketHeader)(brlapi_fileDescriptor fd, brlapi_packetType_t *packetType)
{
  uint32_t header[2];
  ssize_t res;
  if ((res=brlapi_readFile(fd,header,sizeof(header),0)) != sizeof(header)) {
    if (res<0) {
      /* reports EINTR too */
      LibcError("read in brlapi_readPacketHeader");
      return -1;
    } else return -2;
  }
  *packetType = ntohl(header[1]);
  return ntohl(header[0]);
}

/* brlapi_readPacketContent */
/* Read a packet's content into the given buffer */
/* If the packet is too large, the buffer is filled with the */
/* beginning of the packet, the rest of the packet being discarded */
/* Returns packet size, -1 on failure, -2 on EOF */
ssize_t BRLAPI(readPacketContent)(brlapi_fileDescriptor fd, size_t packetSize, void *buf, size_t bufSize)
{
  ssize_t res;
  char foo[BRLAPI_MAXPACKETSIZE];
  while (1) {
    res = brlapi_readFile(fd,buf,MIN(bufSize,packetSize),1);
    if (res >= 0) break;
    if (errno != EINTR
#ifdef EWOULDBLOCK
	&& errno != EWOULDBLOCK
#endif /* EWOULDBLOCK */
	&& errno != EAGAIN)
      goto out;
  }
  if (res<MIN(bufSize,packetSize)) return -2; /* pkt smaller than announced => EOF */
  if (packetSize>bufSize) {
    size_t discard = packetSize-bufSize;
    for (res=0; res<discard / sizeof(foo); res++)
      brlapi_readFile(fd,foo,sizeof(foo),1);
    brlapi_readFile(fd,foo,discard % sizeof(foo),1);
  }
  return packetSize;

out:
  LibcError("read in brlapi_readPacket");
  return -1;
}

/* brlapi_readPacket */
/* Read a packet */
/* Returns packet's size, -2 if EOF, -1 on error */
/* If the packet is larger than the supplied buffer, then */
/* the packet is truncated to buffer's size, like in the recv system call */
/* with option MSG_TRUNC (rest of the pcket is read but discarded) */
ssize_t BRLAPI(readPacket)(brlapi_fileDescriptor fd, brlapi_packetType_t *packetType, void *buf, size_t size)
{
  ssize_t res = BRLAPI(readPacketHeader)(fd, packetType);
  if (res<0) return res; /* reports EINTR too */
  return BRLAPI(readPacketContent)(fd, res, buf, size);
}

/* Function : brlapi_loadAuthKey */
/* Loads an authorization key from the given file */
/* It is stored in auth, and its size in authLength */
/* If the file is non-existant or unreadable, returns -1 */
static int BRLAPI(loadAuthKey)(const char *filename, size_t *authlength, void *auth)
{
  int fd;
  off_t stsize;
  struct stat statbuf;
  if (stat(filename, &statbuf)<0) {
    LibcError("stat in loadAuthKey");
    return -1;
  }

  if (statbuf.st_size==0) {
    brlapi_errno = BRLAPI_ERROR_EMPTYKEY;
    brlapi_errfun = "brlapi_laudAuthKey";
    return -1;
  }

  stsize = MIN(statbuf.st_size, BRLAPI_MAXPACKETSIZE-2*sizeof(uint32_t));

  if ((fd = open(filename, O_RDONLY)) <0) {
    LibcError("open in loadAuthKey");
    return -1;
  }

  *authlength = brlapi_readFile(
#ifdef __MINGW32__
		  (HANDLE) get_osfhandle(fd),
#else /* __MINGW32__ */
		  fd,
#endif /* __MINGW32__ */
		  auth, stsize, 1);

  if (*authlength!=(size_t)stsize) {
    LibcError("read in loadAuthKey");
    close(fd);
    return -1;
  }

  close(fd);
  return 0;
}

/* Function: brlapi_expandHost
 * splits host into host & port */
static int BRLAPI(expandHost)(const char *hostAndPort, char **host, char **port) {
  const char *c;
  if (!hostAndPort || !*hostAndPort) {
#if defined(PF_LOCAL)
    *host = NULL;
    *port = strdup("0");
    return PF_LOCAL;
#else /* PF_LOCAL */
    *host = strdup("127.0.0.1");
    *port = strdup(BRLAPI_SOCKETPORT);
    return PF_UNSPEC;
#endif /* PF_LOCAL */
  } else if ((c = strrchr(hostAndPort,':'))) {
    if (c != hostAndPort) {
      int porti = atoi(c+1);
      if (porti>=(1<<16)-BRLAPI_SOCKETPORTNUM) porti=0;
      *host = malloc(c-hostAndPort+1);
      memcpy(*host, hostAndPort, c-hostAndPort);
      (*host)[c-hostAndPort] = 0;
      *port = malloc(6);
      snprintf(*port,6,"%u",BRLAPI_SOCKETPORTNUM+porti);
      return PF_UNSPEC;
    } else {
#if defined(PF_LOCAL)
      *host = NULL;
      *port = strdup(c+1);
      return PF_LOCAL;
#else /* PF_LOCAL */
      int porti = atoi(c+1);
      if (porti>=(1<<16)-BRLAPI_SOCKETPORTNUM) porti=0;
      *host = strdup("127.0.0.1");
      *port = malloc(6);
      snprintf(*port,6,"%u",BRLAPI_SOCKETPORTNUM+porti);
      return PF_UNSPEC;
#endif /* PF_LOCAL */
    }
  } else {
    *host = strdup(hostAndPort);
    *port = strdup(BRLAPI_SOCKETPORT);
    return PF_UNSPEC;
  }
}

typedef struct {
  brlapi_packetType_t type;
  const char *name;
} brlapi_packetTypeEntry_t;

static const brlapi_packetTypeEntry_t brlapi_packetTypeTable[] = {
  { BRLAPI_PACKET_VERSION, "Version" },
  { BRLAPI_PACKET_AUTH, "Auth" },
  { BRLAPI_PACKET_GETDRIVERNAME, "GetDriverName" },
  { BRLAPI_PACKET_GETDISPLAYSIZE, "GetDisplaySize" },
  { BRLAPI_PACKET_ENTERTTYMODE, "EnterTtyMode" },
  { BRLAPI_PACKET_SETFOCUS, "SetFocus" },
  { BRLAPI_PACKET_LEAVETTYMODE, "LeaveTtyMode" },
  { BRLAPI_PACKET_KEY, "Key" },
  { BRLAPI_PACKET_IGNOREKEYRANGES, "IgnoreKeyRanges" },
  { BRLAPI_PACKET_ACCEPTKEYRANGES, "AcceptKeyRanges" },
  { BRLAPI_PACKET_WRITE, "Write" },
  { BRLAPI_PACKET_ENTERRAWMODE, "EnterRawMode" },
  { BRLAPI_PACKET_LEAVERAWMODE, "LeaveRawMode" },
  { BRLAPI_PACKET_PACKET, "Packet" },
  { BRLAPI_PACKET_SUSPENDDRIVER, "SuspendDriver" },
  { BRLAPI_PACKET_RESUMEDRIVER, "ResumeDriver" },
  { BRLAPI_PACKET_ACK, "Ack" },
  { BRLAPI_PACKET_ERROR, "Error" },
  { BRLAPI_PACKET_EXCEPTION, "Exception" },
  { 0, NULL }
};

const char * BRLAPI_STDCALL BRLAPI(getPacketTypeName)(brlapi_packetType_t type)
{
  const brlapi_packetTypeEntry_t *p;
  for (p = brlapi_packetTypeTable; p->type; p++)
    if (type==p->type) return p->name;
  return "Unknown";
}

static int
BRLAPI(getArgumentWidth) (brlapi_keyCode_t keyCode) {
  brlapi_keyCode_t code = keyCode & BRLAPI_KEY_CODE_MASK;

  switch (keyCode & BRLAPI_KEY_TYPE_MASK) {
    default: break;

    case BRLAPI_KEY_TYPE_SYM:
      switch (code & 0XFF000000U) {
        default: break;

        case 0X00000000U:
          switch (code & 0XFF0000U) {
            default: break;
            case 0X000000U: return 8;
          }
          break;

        case 0X01000000U: return 24;
      }
      break;

    case BRLAPI_KEY_TYPE_CMD:
      switch (code & BRLAPI_KEY_CMD_BLK_MASK) {
        default: return 16;
        case 0: return 0;
      }
      break;
  }

  brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
  return -1;
}

/* Function : brlapi_getKeyrangeMask */
/* returns the keyCode mask for a given range type */
static int
BRLAPI(getKeyrangeMask) (brlapi_rangeType_t r, brlapi_keyCode_t code, brlapi_keyCode_t *mask)
{
  switch(r) {
    case brlapi_rangeType_all:
      *mask = BRLAPI_KEY_MAX;
      return 0;
    case brlapi_rangeType_type:
      *mask = BRLAPI_KEY_CODE_MASK|BRLAPI_KEY_FLAGS_MASK;
      return 0;
    case brlapi_rangeType_command: {
      int width = BRLAPI(getArgumentWidth)(code);
      if (width == -1) return -1;
      *mask = ((1 << width) - 1) | BRLAPI_KEY_FLAGS_MASK;
      return 0;
    }
    case brlapi_rangeType_key:
      *mask = BRLAPI_KEY_FLAGS_MASK;
      return 0;
    case brlapi_rangeType_code:
      *mask = 0;
      return 0;
  }
  brlapi_errno = BRLAPI_ERROR_INVALID_PARAMETER;
  return -1;
}

static char *
BRLAPI(getKeyFile)(const char *auth)
{
  const char *path;
  char *ret, *delim;
  if (!strncmp(auth,"keyfile:",8))
    path=auth+8;
  else {
    path=strstr(auth,"+keyfile:");
    if (path) path+=9;
    else path=auth;
  }
  ret=strdup(path);
  delim=strchr(ret,'+');
  if (delim)
    *delim = 0;
  return ret;
}

/*
 *  Copyright (C) 2006-2019 S&S
 *  Samuel Thibault <samuel.thibault@ens-lyon.org>
 *  Sébastien Hinderer <sebastien.hinderer@ens-lyon.org>
 *
 * This program is free software ; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation ; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY ; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the program ; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/*
 * vstp_transfer.c
 *
 * handles file transfers
 */

#include <stdlib.h>
#include <unistd.h>
#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>

#include "brlapi.h"
#include "brlapi_protocol.h"

#include "vstp.h"

#ifdef __MINGW32__
#define alarm(t) (void)0
#endif

/* SEND */
/* tries to send a message */
#define SEND(buf,size) \
do {\
 if (brlapi_sendRaw(buf,size)<0) {\
  perror("while sending");\
  transfer_abort(RET_ECONN);\
 }\
}\
while (0)

/* RECV */
/* tries to get a message */
/* on error, try to clean */
/* also set a timeout */
#define RECV() \
while (1) {\
 alarm(TRY_TIMEOUT);\
 res=brlapi_recvRaw(ibuf,BRLAPI_MAXPACKETSIZE);\
 alarm(0);\
 if (res<0) {\
  perror("while receiving");\
  transfer_abort(RET_ECONN);\
 }\
 if (ibuf[0]==VB_RESET[0]) {\
  fprintf(stderr,"transfer interrupt by user !\n");\
  transfer_abort(RET_INT);\
 }\
 if (res) break;\
}

int transferring = 0;
unsigned char burstmode = VB_AUTOMATIC;
int backup = 0;
char *visiobases_dir = NULL;
char *visiobases_ext = ".vis";
static unsigned char numpacket;
static int sizetransferred;
static char ibuf[BRLAPI_MAXPACKETSIZE];
static int osize;
static int otries;
static char obuf[BRLAPI_MAXPACKETSIZE];
static unsigned char filename[VB_MAXFNLEN+4+1];

/* showPacket */
/* eventually show the packet and abort, if tries number is reached */

static void showPacket(int size) {
 int i;
 fprintf(stderr,"unexpected %c packet, size %d, content :\n", ibuf[0], size);
 for (i=0;i<size;i++) {
  fprintf(stderr,"%2x ", ibuf[i]);
  if (!((i+1)%10)) fprintf(stderr,"\n");
 }
 if (otries++<NUM_TRIES) return;
 fprintf(stderr,"couldn't recover, so aborting...\n");
 transfer_abort(RET_EPROTO);
}
/* WaitForOk */
/* wait for "Y" packet */
static void WaitForOk(void) {
 int res;
 do { SEND(obuf,osize); RECV() }
 while ((res==0 || ibuf[0]!=VB_OK) && (showPacket(res),1));
}

/* renameBackups */
/* renames backups, increasing their number, like logrotate does */
/* returns value returned by the last rename call */
int renameBackups(const char *filename) {
#define SIZEMAX_SUFFIX (1+20+1)
 int n=strlen(filename);
 char to[n+SIZEMAX_SUFFIX],from[n+SIZEMAX_SUFFIX];
 struct stat st;
 int i,max;

 memcpy(to,filename,n);
 for (max=1;max;max++) {
  sprintf(to+n,".%c",max);
  if (stat(to,&st)<0) break;
 }
 if (!max) {
  fprintf(stderr,"too many backups for %s\n",filename);
  errno=ENAMETOOLONG;
  return -1;
 }
 memcpy(from,filename,n);
 strcpy(from+n,".1");
 i=max-1;
 while(i) {
  sprintf(from+n,".%u",i);
  if (rename(from,to)<0) {
   fprintf(stderr,"%s -> %s failed\n",from,to);
   return -1;
  }
  if (--i) strcpy(to+n,from+n);
  else break;
 }
 return(rename(filename,from));
}

/* fileget */
/* get file f on VisioBraille */
void fileget(char *f) {
 int n=strlen(f),lnpath,lnext;
 char *c,*d;
 int res;
 int fd;
 struct stat st;
 char *path,*ext;

 if (n==0) return;

/* find path, if any */
 for (c=f+n-1; c>=f && *c!='/'; c--);
 if (c>=f) {
  path=f;
  lnpath=c-f;
  *c++='\0';
  f=c;
 } else {
  path=NULL;
  lnpath=-1;
 }

/* remove extension */
 for (c=f; *c && *c!='.'; c++);
 if (*c) {
  ext=c;
  lnext=n-(ext-f);
 } else {
  ext=NULL;
  lnext=-1;
 }
 n=c-f;

 if (path) printf("getting %s in %s\n",f,path);
 else printf("getting %s\n",f);

 transfer_init(fileget);

/* sending filename (can contain * and ?) */
 obuf[0]=VB_UNLOAD;
 memcpy(&obuf[1],f,n);
 osize=n+1;
 otries=0;

 while(1) {
  SEND(obuf,osize);
  numpacket='1';
  RECV();
  if (ibuf[0]==VB_FILES_OVER[0]) break; /* end of file list */
  if (res<3) { showPacket(res); continue; }
  if (ibuf[0]!=VB_FILEHERE) { showPacket(res); continue; }
  if (ibuf[1]!=numpacket) { showPacket(res); continue; }

/* ok, VisioBraille proposed a file, let's try to get it */
  if (res-3>VB_MAXFNLEN) {
   fprintf(stderr,"name too long, giving up that file\n");
   obuf[0]=VB_NEXT;
   obuf[1]=ibuf[1];
   osize=2;
   otries=0;
   continue;
  }
/* copy its name */
  {
   char fullpath[(lnpath+1)+(res-3)+(ext?lnext+1:strlen(visiobases_ext))+1];
   if (path) {
    strcpy(fullpath,path);
    strcat(fullpath,"/");
   }
   for(c=ibuf+3,d=fullpath+lnpath+1;c-ibuf<res;*d++=tolower(*c++))
   fullpath[lnpath+1+res-3]='\0';
   strcat(fullpath,ext?ext:visiobases_ext);

   if (backup && stat(fullpath,&st)>=0)
    if (renameBackups(fullpath)==-1)
     perror("couldn't rename backups, overwriting");
   if ((fd=open(fullpath,O_WRONLY|O_CREAT|O_TRUNC,0644))<0) {
    /* openout failed, give up that file */
    perror(fullpath);
    fprintf(stderr,"open failed, giving up that file\n");
    obuf[0]=VB_NEXT;
    obuf[1]=ibuf[1];
    osize=2;
    otries=0;
    continue;
   }

/* start transfer : */
   obuf[0]=VB_ACK_DATA;
   obuf[1]=numpacket;
   osize=2;
   otries=0;
   numpacket=(numpacket+1)&'7';
   sizetransferred=0;
/* ready to transfer ! */
   while (1) {
    SEND(obuf,osize);
    printf("\r%s: %dKo...",fullpath,sizetransferred>>10);
    fflush(stdout);
    RECV();
    if (ibuf[0]==VB_DATA_OVER) break;
    if (res<2) { showPacket(res); continue; }
    if (ibuf[0]!=VB_HERES_DATA) { showPacket(res); continue; }
    if (ibuf[1]!=numpacket) { showPacket(res); continue; }
    if (write(fd,ibuf+2,res-2)<res-2) {
     fprintf(stderr,"writing data on disk for file %s\n"
 		    "So giving up\n",fullpath);
     transfer_abort(RET_EUNIX);
    }
    obuf[0]=VB_ACK_DATA;
    obuf[1]=numpacket;
    osize=2;
    otries=0;
    sizetransferred+=res-2;
    numpacket=(numpacket+1)&'7';
   }
/* transfer finished */
   close(fd);
   printf("ok\n");
   obuf[0]=VB_OK;
   osize=1;
   otries=0;
  }
 }
 transferring=0;
}


static int tryToFind(char *f, int *fd) {
 if ((*fd=open(f,O_RDONLY))<0) {
  strcpy(ibuf,f);
  strcat(ibuf,visiobases_ext);
  if ((*fd=open(ibuf,O_RDONLY))<0) {
   strcpy(ibuf,f);
   strcat(ibuf,".vis");
   if ((*fd=open(ibuf,O_RDONLY))<0) {
    strcpy(ibuf,f);
    strcat(ibuf,".Vis");
    if ((*fd=open(ibuf,O_RDONLY))<0) {
     strcpy(ibuf,f);
     strcat(ibuf,".VIS");
     if ((*fd=open(ibuf,O_RDONLY))<0) {
      return 0;
     }
    }
   }
  }
 }
 return 1;
}

/* fileput */
/* send file f to VisioBraille */
void fileput(char *f) {
 int n;
 char *c;
 int res;
 int fd;

 if (visiobases_dir && (f[0]!='.' || (f[1]!='.' && f[1]!='/') || (f[1]=='.' && f[2]!='/'))) {
  char *f2 = malloc(strlen(visiobases_dir)+1+strlen(f));
  strcpy(f2,visiobases_dir);
  strcat(f2,f);
  if (tryToFind(f2, &fd)) {
   free(f2);
   goto ok;
  }
  fprintf(stderr,"couldn't get it from download directory, trying from current directory.\n");
  free(f2);
 }
 if (!tryToFind(f, &fd)) {
  fprintf(stderr,"open failed, giving up that file\n");
  return;
 }

ok:
 transfer_init(fileput);

 printf("putting %s\n",f);

/* truncate filename : no extension, 8 chars max */
 if ((c=strrchr(f,'/'))) f=c+1;
 for (c=f;*c && *c!='.' && c<f+VB_MAXFNLEN;c++);
 n=c-f;

 obuf[0]=VB_FILEHERE;
 obuf[1]=numpacket='1';
 obuf[2]=VB_FILET_AGENDA;
 memcpy(&obuf[3],f,n);
 memcpy(filename,f,n);
 filename[n]=0;
 osize=n+3;
 otries=0;

 sizetransferred=0;
 while(1) {
  SEND(obuf,osize);
  printf("\r%s: %dKo...",filename,sizetransferred>>10);
  fflush(stdout);
  RECV();
  if (ibuf[0]==VB_NEXT) break;
  if (res<2) { showPacket(res); continue; }
  if (ibuf[0]!=VB_ACK_DATA) { showPacket(res); continue; }
  if (ibuf[1]!=numpacket) { showPacket(res); continue; }
  if ((res=read(fd,&obuf[2],SIZE_PUT))<0) {
   fprintf(stderr,"reading data on disk for file %s failed: %s\n"
		  "So giving up for this file\n",strerror(errno),filename);
   break;
  }
  if (res==0) { /* eof */
   obuf[0]=VB_DATA_OVER;
   osize=1;
   otries=0;
   WaitForOk();
   break;
  }
  obuf[0]=VB_HERES_DATA;
  obuf[1]=numpacket=(numpacket+1)&'7';
  osize=res+2;
  otries=0;
  sizetransferred+=res;
 }
/* transfer finished */
 close(fd);
 printf("ok\n");
}

/* transfer_init */
/* send the correct "I" packet, according to options */
void transfer_init(transferfun *f) {
 if (transferring) return;
 transferring=1;

 obuf[0]=VB_INIT_PARAMS;
 obuf[1]=(f==fileget?VB_UNLOAD:VB_LOAD);
 obuf[2]=burstmode;
 osize=3;
 otries=0;
 WaitForOk();
 numpacket='1';
}

/* transfer_finish */
/* terminate transfer (also end of file list) */
void transfer_finish(transferfun *f) {
 if (!transferring) return;
 transferring=0;
 if (f==fileput)
  SEND(VB_FILES_OVER,strlen(VB_FILES_OVER));
}

/* transfer_abort */
/* if something nasty occured, try to clean */
void transfer_abort(int exitnum) {
 brlapi_sendRaw(VB_RESET,strlen(VB_RESET));
 brlapi_leaveRaw();
 brlapi_closeConnection();
 exit(exitnum);
}

/* transfer_timeout */
/* called when RecvPacket timed out */
void transfer_timeout(int signum) {
 if (otries++>=NUM_TRIES) {
  fprintf(stderr,"No reply from terminal ! Assuming dead, hence aborting\n");
  transfer_abort(RET_EPROTO);
 }
 SEND(obuf,osize);
 alarm(1);
}

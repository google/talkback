/*
 *  Copyright (C) 2006-2023 S&S
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

/* vstp_main.c
 * files transferring with VisioBraille terminals
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include "brlapi.h"
#include "vstp.h"

#define VSTPRC ".vstprc"
#define LINELENGTH 255

char *socketport = NULL;
char *keyname=NULL;

void handleint(int signum) {
 fprintf(stderr,"aborting on signal %d\n",signum);
 transfer_abort(RET_INT);
}

/* every possible option */
#define OPTIONS "ifbnskd"

static void printusage(char *name) {
 printf(VSTP_GET "/" VSTP_PUT " : get files from / put files to a VisioBraille terminal\n");
 printf("Usage: %s [options] [files]\n",name);
 printf("[files] are Unix filenames\n");
 printf(" -i            ask for confirmation\n");
 printf(" -f            don't ask for confirmation (default)\n");
 printf(" -b            create backup (.vis~) file if file already exists\n");
 printf(" -n            do not create backup (.vis~) file (default)\n");
 printf(" -s port       use port as port number instead of default\n");
 printf(" -k filename   use filename as key path instead of default\n");
 printf(" -d            put files into current directory\n");
 printf(" -o filename   also use filename as options file\n");
 exit(RET_EPARSE);
}

static void grr(char *name) {
 printusage(name);
 exit(RET_EPARSE);
}

static transferfun *CheckSendOrRecv(char *name){
 if (strstr(name,VSTP_PUT) != NULL) return fileput;
 if (strstr(name,VSTP_GET) != NULL) return fileget;
 printf("Please call me as " VSTP_PUT " or as " VSTP_GET ".\n");
 grr(name);
 exit(RET_EPARSE);
}

static void Parse(char *filename) {
 FILE *fd;
 char s[LINELENGTH+1];
 char *c,*d;

 if (!(fd=fopen(filename,"r"))) return;

 while (fgets(s,LINELENGTH,fd)) {
  if ((c=strchr(s,'#')))
   *c='\0';
  if ((c=strchr(s,'\n')))
   *c='\0';
  if ((c=strchr(s,'='))) {
   /* '=' option : key name and value */
   for (d=c-1; *d==' ' || *d=='\t'; d--);
   *(d+1)='\0';
   for (c++;   *c==' ' || *c=='\t'; c++);
   *(c-1)='\0';
   if (!strcmp(s,"keyname")) {
    keyname = (char *) malloc(strlen(c)+1);
    strcpy(keyname,c);
   } else if (!strcmp(s,"socketport")) {
    socketport = (char *) malloc(strlen(c)+1);
    strcpy(socketport,c);
   } else if (!strcmp(s,"vbs_ext")) {
    visiobases_ext = (char *) malloc(5);
    strncpy(visiobases_ext,c,4);
    visiobases_ext[4]=0;
   } else if (!strcmp(s,"vbs_dir")) {
    visiobases_dir = (char *) malloc(strlen(c)+1);
    strcpy(visiobases_dir,c);
   }
  } else {
   if (!strcmp(s,"backup")) {
    backup=1;
   } else if (!strcmp(s,"nobackup")) {
    backup=0;
   }
  }
 }
 fclose(fd);
}

static void CheckOptions(int argc, char **argv) {
 int n,m;
 int i;
 for(n=1;n<argc;n++) {
/* an option ? */
  if (argv[n][0]=='-') {
/* is it "--" ? */
   if (argv[n][1]=='-') {
/* --blabla options are not used */
    if (argv[n][2]) {
     printf("long option not recognized : %s\n",argv[n]);
     grr(argv[0]);
    } else return;
   }

   m=n;
/* -blabla, check every letter */
   for (i=1;argv[n][i];i++) {
    if (argv[n][i]=='d')
     if (visiobases_dir) {
      free(visiobases_dir);
      visiobases_dir=NULL;
     }
    if (argv[n][i]=='h') {
     printusage(argv[0]);
     exit(0);
    }
    if (argv[n][i]=='s') {
     if (++m==argc) grr(argv[0]);
     socketport=argv[m];
    }
    if (argv[n][i]=='k') {
     if (++m==argc) grr(argv[0]);
     keyname=argv[m];
    }
    if (argv[n][i]=='o') {
     if (++m==argc) grr(argv[0]);
     Parse(argv[m]);
    }
    if (argv[n][i]=='b')
     backup=1;
    if (argv[n][i]=='n')
     backup=0;
    if (argv[n][i]=='f')
     burstmode=VB_AUTOMATIC;
    if (argv[n][i]=='i')
     burstmode=VB_MANUAL;
    if (strchr(OPTIONS,argv[n][i])==NULL) {
     printf("option not recognized : -%c\n",argv[n][i]);
     grr(argv[0]);
    }
   }
   n=m;
  }
 }
}

int main(int argc, char *argv[]) {
 transferfun *transfer;
 char driverName[13];
 int stilloptions=1;
 char *home;
 brlapi_settings_t brlapi_settings;
 
 transfer=CheckSendOrRecv(argv[0]);

/* first use options file */
 if ((home=getenv("HOME"))) {
  char vstprc[strlen(home)+strlen(VSTPRC)+2];
  strcpy(vstprc,home);
  strcat(vstprc,"/" VSTPRC);
  Parse(vstprc);
 }

/* a first pass to check options and record them, before doing anything */
 CheckOptions(argc--,argv++);

/* ok, one can try to open the socket */
 brlapi_settings.host = socketport;
 brlapi_settings.auth = keyname; 
 if (brlapi_initializeConnection(&brlapi_settings,NULL)<0)
 {
  brlapi_perror("Couldn't initialize connection with BrlAPI");
  exit(RET_ECONN);
 }
 if (brlapi_getDriverName(driverName, sizeof(driverName))<12)
 {
  brlapi_perror("Couldn't get driver name");
  brlapi_closeConnection();
  exit(RET_ECONN);
 }
 if (strcmp(driverName,"VisioBraille"))
 {
  fprintf(stderr,"braille driver is not VisioBraille\n");
  brlapi_closeConnection();
  exit(RET_ECONN);  
 }
 
 if (brlapi_enterRawMode("VisioBraille")<0) {
  fprintf(stderr,"Couldn't get raw mode\n");
  brlapi_closeConnection();
  exit(RET_ECONN);
 }

 signal(SIGINT,handleint);
 signal(SIGTERM,handleint);

#ifdef SIGHUP
 signal(SIGHUP,handleint);
#endif /* SIGHUP */

#ifdef SIGQUIT
 signal(SIGQUIT,handleint);
#endif /* SIGQUIT */

#ifdef SIGPIPE
 signal(SIGPIPE,handleint);
#endif /* SIGPIPE */

#ifdef SIGALRM
 signal(SIGALRM,transfer_timeout);
#endif /* SIGALRM */

 if (visiobases_dir && chdir(visiobases_dir)<0) {
  perror(visiobases_dir);
  fprintf(stderr,"couldn't chdir to download dir, please use -d if you want to store files in .\n");
  exit(RET_EUNIX);
 }

 for(;argc;argc--, argv++) {
/* is it an option ? */
  if (stilloptions)
  if (argv[0][0]=='-') {
   switch (argv[0][1]) {
     case '-': stilloptions=0; continue;
     case 's': /* already parsed */
     case 'k':
     case 'm':
	       argc--;
	       argv++;
     case 'b':
     case 'n':
     case 'f':
     case 'i':
     case 'd':
     default:
	       continue;
   }
  }
  
/* no, a file name, let's try to transfer it */
  transfer(argv[0]);
 }
 printf("transfers finished\n");
 transfer_finish(transfer);
 brlapi_leaveRawMode(); /* can't do much it it fails ! */
 brlapi_closeConnection();
 return 0;
}

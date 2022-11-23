/*
** brl.c for eutp in /home/obert01/work/eutp/src
**
** Made by Olivier BERT
** Login   <obert01@epita.fr>
*/

#ifndef _XOPEN_SOURCE
#define _XOPEN_SOURCE 500
#endif /* _XOPEN_SOURCE */

/* globals */
unsigned char extensions[] = {'K', 'L', 'B', 'T', 'A'};
unsigned char positions[] = {3, 7, 16};

#include <string.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include "brlapi.h"
#include <sys/types.h>
#include <dirent.h>

#include "eutp_brl.h"
#include "eutp_pc.h"
#include "eutp_tools.h"
#include "eutp_transfer.h"

void		eutp_abort(int exitstatus)
{
  brlapi_leaveRawMode();
  brlapi_closeConnection();
  exit(exitstatus);
}

/*
** Asks a Yes/No question.
** If the user says Yes (key # pressed) the function returns 1.
** IF the answer is No (key * pressed) the function return 0.
*/
int		brl_yesno_question(char *prompt)
{
  unsigned char buf[BUFFER_SIZE];

  brl_message(prompt, 0);
  while (1)
    {
      brl_read(buf);
      if (!strncmp((char*)buf, "\003KT#", 4))
	return 1;
      if (!strncmp((char*)buf, "\003KT*", 4))
	return 0;
	}
}
/*
** Read raw data from the terminal
*/
ssize_t		brl_read(unsigned char *buf)
{
  ssize_t res;

  while (1)
    {
      alarm(20);
      res = brlapi_recvRaw(buf, BUFFER_SIZE);
      alarm(0);
      if (res < 0)
	{
	  brlapi_perror("reading on terminal");
	  eutp_abort(E_READ);
	  return 0;
	}
      if (res)
	break;
    }
  return res;
}

ssize_t		brl_write(unsigned char *str, size_t len)
{
  ssize_t res;

  alarm(20);
  res = brlapi_sendRaw(str, len);
  alarm(0);
  if (res < 0)
    {
      brlapi_perror("Error writing to the terminal");
      eutp_abort(E_WRITE);
      return 0;
    }
  return res;
}

/*
** Get model identification
** ident must be a buffer of at least 20 bytes.
*/
void		get_ident(char* ident)
{
  char		buf[256];
  char *p;
  unsigned char ln = 0;

  brl_writeStr("SI");
  brl_read((unsigned char*)buf);
  p = buf;
  while (1)
    {
      ln = *(p++);
      if (ln == 22 && !strncmp(p, "SI", 2))
	{
	  memcpy(ident, p + 2, 20);
	  break;
	}
      else
	p += ln;
    }
}

/*
** Write a string to the terminal
** The string is supposed terminated by a \0 character
*/
void	brl_writeStr(char *str)
{
  brl_write((unsigned char*)str, strlen(str));
}

/*
** Initialize the application and connect to brlapi
** Init brlapi raw mode and print a welcome message on the terminal
*/
int		brl_init(t_env *env)
{
  int res;
  char p[100];
  unsigned int x, y;

  /* Connect to BrlAPI */
  if (brlapi_initializeConnection(NULL, NULL) < 0)
    {
      brlapi_perror("brlapi_initializeConnection");
      return -1;
    }

  /* Get driver id & name */

  res = brlapi_getDriverName(p, sizeof(p));
  if (res == -1)
    brlapi_perror("brlapi_getDriverName");
  else
    printf("Driver name: %s\n",p);

  /* Get display size */
  if (brlapi_getDisplaySize(&x, &y) < 0)
    brlapi_perror("brlapi_getDisplaySize");
  else
    printf("Braille display has %d line%s of %d column%s\n",y,y>1?"s":"",x,x>1?"s":"");

  /* Try entering raw mode, immediately go out from raw mode */
  printf("Trying to enter in raw mode... ");
  if (brlapi_enterRawMode("EuroBraille") < 0)
    brlapi_perror("brlapi_getRaw");
  else {
    printf("Ok\n");
  }
  /* welcome message */
  brl_lasting_message(EUTP_VERSION);
  get_ident(env->ident);
  printf("Identification: %20s\n", env->ident);
  return 0;
}

/*
** Closes the connection to BRLAPI
*/
int		brl_close(void)
{
  brlapi_leaveRawMode();
  brlapi_closeConnection();
  return 0;
}

/*
** Displays a message to the braille terminal
** The cursor can be positionned with the second argument
*/
int		brl_message(char *str, unsigned char cursorpos)
{
  int		ret = 0;
  unsigned int len = strlen((char*)str);
  char*	q = str;
  unsigned char		i = 1;
  unsigned char* buffer = malloc(512);
  unsigned char*	p = buffer;

  *p++ = 'D';
  *p++ = 'M';
  while (*q)
    {
      if (cursorpos >= 1 && i == cursorpos) {
	*p++ = '\x1b';
	*p++ = '\x02';
      }
      *p++ = *q++;
      i++;
    }
  brl_write(buffer, cursorpos == 0 ? len + 2 : len + 4);
  free(buffer);
  return ret;
}


static int		showbrfile(t_env* env)
{
  unsigned char		cursorpos = positions[env->status];
  unsigned char		ext = extensions[env->curext];
  unsigned char* buf = malloc(256);
  unsigned char* str = malloc(256); /* the string to display */

  buf[0] = '\005';
  buf[1] = 'F';
  buf[2] = 'N';
  buf[3] = ext;
  buf[4] = (env->brfilenum & 0xff00) >> 2;
  buf[5] = env->brfilenum & 0x00ff;
  brl_write(buf+1, 5);
  while (1)
    {
      brl_read(buf);
      if (!strncmp((char*)buf, "\003KT", 3))
	continue;
      else
	break;
    }
  if (!strncmp((char*)buf, "\003FE", 3))
    {
      env->brfilenum--;
      return 0;
    }
  strcpy((char*)str, env->brpc ? "BR>PC " : "PC>BR ");
  strncat((char*)str, ((char*)&(buf[6])), buf[0] - 5);
  strcat((char*)str, ".");
  strncat((char*)str, (char*)&ext, 1);
  brl_message((char*)str, cursorpos);
  free(buf);
  free(str);
  return 0;
}



/*
** Show the list of files either of the braille terminal or of the PC
** It is the main loop of the program.
*/
int		brl_listfiles(t_env* env)
{
  unsigned char		end = 0;
  unsigned char*	buf = malloc(256);

  env->curext = 0;
  env->brpc = 1;
  env->brfilenum = 1;
  env->pcfilenum = 0;
  env->status = 1;
  while (!end)
    {
      if (env->brpc == 1)
	showbrfile(env);
      else
	showpcfiles(env);
      brl_read(buf);
      if (!strncmp("\003KT*", (char*)buf, 4))
	end = 1;
      if (!strncmp("\003KT4", (char*)buf, 4) && env->status)
	env->status--;
      if (!strncmp("\003KT6", (char*)buf, 4) &&
	  ((env->status != 2 && env->brpc)
	   || (!env->brpc && env->status != 1)))
	env->status++;
      if (!strncmp("\003KT8", (char*)buf, 4))
	{
	  if (env->status == 0)
	    env->brpc = !env->brpc;
	  if (env->status == 1 && env->brpc)
	    env->brfilenum++;
	  if (env->status == 1 && !env->brpc && env->pcfilenum < env->n - 1)
	    env->pcfilenum++;
	  if (env->status == 2 && env->curext < MAXENT - 1)
	    env->curext++, env->brfilenum = 1;
	}
      if (!strncmp("\003KT2", (char*)buf, 4))
	{
	  if (env->status == 0)
	    env->brpc = !env->brpc;
	  if (env->status == 1 && env->brfilenum > 1 && env->brpc)
	    env->brfilenum--;
	  if (env->status == 1 && env->pcfilenum > 0)
	    env->pcfilenum--;
	  if (env->status == 2 && env->curext > 0)
	    env->curext--, env->brfilenum = 1;
	}
      if (!strncmp("\003KT#", (char*)buf, 4))
	{
	  if (env->brpc) {
	    if (!brtopc(env))
	      end = 1;
	  }
	  else
	    {
	      if (!pctobr(env))
		end = 1;
	    }
	}
    }
  free(buf);
  return 0;
}

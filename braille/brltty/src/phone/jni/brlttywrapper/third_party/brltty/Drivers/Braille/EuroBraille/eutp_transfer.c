/*
** transfer.c for eutp in /home/obert01/work/eutp/src
**
** Made by Olivier BERT
** Login   <obert01@epita.fr>
**
** Started on  Sun Mar 20 16:10:06 2005 Olivier BERT
Last update Fri Jun  1 15:23:17 2007 Olivier BERT
*/

#ifndef _XOPEN_SOURCE
#define _XOPEN_SOURCE 500
#endif /* _XOPEN_SOURCE */

#include <sys/types.h>
#include <dirent.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include "brlapi.h"
#include "eutp_brl.h"
#include "eutp_debug.h"
#include "eutp_tools.h"
#include "eutp_convert.h"

extern unsigned char extensions[];


int		brtopc(t_env* env)
{
  int		fd; /* fd of a temporary file */
  unsigned int		lines = 0; /* number of packets transfered */
  unsigned char destext = 0;  /* conversion of the destination file */
  unsigned char		buf[BUFFER_SIZE]; /* received buffer */
  unsigned char	str[BUFFER_SIZE];  /* sent buffer */
  unsigned char ext = extensions[env->curext];  /* current extension */
  unsigned char filename[15]; /* The transfered file name */
  unsigned char filename1[15]; /* The filename without extension */

  while (1)
    {
      /* Asking name of current selected file */
      strcpy((char*)buf, "\005FN");
      buf[3] = ext;
      buf[4] = (env->brfilenum & 0xff00) >> 2;
      buf[5] = env->brfilenum & 0x00ff;
      brl_write(buf+1, 5);
      brl_read(buf);
      strcpy((char*)str, "Conv ");
      strncat((char*)str, ((char*)&(buf[6])), buf[0] - 5);
      filename[0] = 0;
      strncat((char*)filename, ((char*)&(buf[6])), buf[0] - 5);
      strcat((char*)str, ".");
      remove_blanks(filename);
      strcpy((char*)filename1, (char*)filename);
      strcat((char*)filename, ".");
      if (destext == 0) {
	strncat((char*)str, ((char*)&(extensions[env->curext])), 1);
	strncat((char*)filename, ((char*)&(extensions[env->curext])), 1);
      }
      else {
	strcat((char*)str, "TXT");
	strcat((char*)filename, "TXT");
      }
      brl_message((char*)str, 0);
      brl_read(buf);
      if (!strncmp((char*)buf, "\003KT*", 4))
	return 1;
      if (!strncmp((char*)buf, "\003KT#", 4))
	break;
      if (!strncmp((char*)buf, "\003KT8", 4))
	destext = !destext;
      if (!strncmp((char*)buf, "\003KT2", 4))
	destext = !destext;
    }
  strcpy((char*)buf, "\005FO");
  buf[3] = ext;
  buf[4] = (env->brfilenum & 0xff00) >> 2;
  buf[5] = env->brfilenum & 0x00ff;
  brl_write(buf+1, 5);
  brl_read(buf);
  if (strncmp((char*)buf, "\003FE\x10", 4))
    {
      brl_lasting_message("Erreur ouv br");
      return 1;
    }
  if ((fd = open((char*)filename, O_WRONLY | O_CREAT | O_TRUNC, 0600)) == -1)
    {
      perror("open");
      brl_message("! Err ecriture PC", 0);
      sleep(1);
      return 1;
    }
  while (1)
    {
      brl_writeStr(READ_LINE);
      brl_read(buf);
      if (!strncmp((char*)buf, "\003KT", 3))
	{
	  printf("touche appuyée\n");
	  if (!strncmp((char*)buf, "\003KT*", 4))
	    {
	      printf("touche * appuyée\n");
	      brl_lasting_message("! interrompu ");
	      return 1;
	    }
	}
      if (!strncmp((char*)buf, "\003FE", 3))
	{
	  if (buf[3] == '\x13')
	      break;
	  else
	    {
	      printf("code inattendu\n");
	      brl_lasting_message("! transfert interrompu");
	      return 1;
	    }
	}
      sprintf((char*)str, "... %s %d", filename, lines);
      brl_message((char*)str, 0);
      write(fd, &(buf[3]), buf[0] - 2);
      lines++;
    }
  brl_writeStr(CLOSE_FILE);
  brl_read(buf);
  if (strncmp((char*)buf, "\003FE\x10", 4))
    {
      brl_lasting_message("! erreur");
      return 1;
    }
  printf("fichier fermé\n");
  close(fd);
  if (destext)
    {
      strcat((char*)filename1, ".TXT");
      if (!k2txt(env, (char*)filename, (char*)filename1))
	{
	  brl_lasting_message("! Erreur conversion");
	  return 1;
	}
    }
  brl_lasting_message("! Fin transfert");
  return 1;
}


/*
** When this function returns 4242, it's the end of file
** Otherwise, the length of the trame is returned.
** The trame is formated to be directly sent to the braille terminal
*/
static unsigned int	read_trame_from_file(t_env* env, unsigned char* res,
					     unsigned int *size)
{
  unsigned int retval = 0;
  int ch;
  int oldch;

  *size = 0;
  res[1] = 'F';
  res[2] = 'W';
  oldch = fgetc(env->fs);
  ch = fgetc(env->fs);
  *size = 4;
  res[3] = oldch;
  res[4] = ch;
  while ((!(ch == '$' && oldch == '\x1B')) && (!(ch == '@' && oldch == '\x1B')) )
    {
      oldch = ch;
      ch = fgetc(env->fs);
      (*size)++;
      res[*size] = ch;
    }
  if (ch == '@' && oldch == '\x1B')
    {
      printf("fin du fichier détectée\n");
      retval = 1;
    }
  res[0] = *size;
  return retval;
}


/*
** PC to BR meta-function
*/
int		pctobr(t_env* env)
{
  char end = 0;
  int i = 0;
  unsigned int count = 0;
  unsigned char conv = 0;
  unsigned int lines = 0;
  unsigned char		buf[BUFFER_SIZE]; /* received buffer */
  unsigned char	str[BUFFER_SIZE];  /* sent buffer */
  char* tmpfilename = "/tmp/eutp.tmp";

  for (i = 0; i < BUFFER_SIZE; i++)
    {
      buf[i] = 0;
      str[i] = 0;
    }
  conv = normalize_filename(env);
  if ((env->fd = open(env->list[env->pcfilenum]->d_name, O_RDONLY)) == -1)
    {
      brl_message("!Erreur ouv pc", 0);
      sleep(1);
      return 0;
    }
  env->fs = fdopen(env->fd, "r");
  /* Ouverture fichier en écriture sur le terminal braille */
  str[0] = '\x0C';
  strncpy((char*)&str[1], "Fo\x00", 3);
  str[4] = env->curext;
  strncpy((char*)&str[5], env->filename, 8);
  brl_write(str+1, 12);
  brl_read(buf);
  if (!strncmp((char*)buf, "\003FE\x21", 4))
    {
      if (brl_yesno_question("! Remplacer ?      #"))
	  str[3] = 1;
      else
	return 1;
      brl_write(str+1, 12);
      brl_read(buf);
    }
  if (strncmp((char*)buf, "\002FW", 3))
    {
      brl_lasting_message("! erreur ouv br");
      brl_writeStr(CLOSE_FILE);
      return 1;
    }
  while (!end)
    {
      end = read_trame_from_file(env, str, &count);
      for (i = 0; i < 30; i++)
	printf(",%d,", str[i]);
      printf("\n");
      if (count == 4242) /* fin fichier */
	{
	  printf("fin fichier\n");
	  end = 1;
	}
      brl_write(str+1, count);
      brl_read(buf);
      if (strncmp((char*)buf, (char*)"\002FW", 3))
	{
	  printf("erreur transfert\n");
	  for (i = 0; i < 30; i++)
	    printf(",%d,", buf[i]);
	  printf("\n");
	  brl_lasting_message("! Erreur transfert");
	  brl_writeStr(CLOSE_FILE);
	  return 1;
	}
      lines++;
      sprintf((char*)str, "... %s.%c %d", env->filename, env->curext, lines);
      brl_message((char*)str, 0);
    }
  brl_writeStr(CLOSE_FILE);
  brl_read(buf);
  if (strncmp((char*)buf, "\003FE\x10", 4))
    {
      printf("errer fermeture\n");
      for (i = 0; i < 30; i++)
	printf(",%d,", buf[i]);
      printf("\n");
      brl_lasting_message("! err fermeture");
      fclose(env->fs);
      close(env->fd);
      return 1;
    }
  brl_message("! Fin transfert", 0);
  sleep(1);
  fclose(env->fs);
  close(env->fd);
  if (conv)
    unlink(tmpfilename);
  return 1;
}

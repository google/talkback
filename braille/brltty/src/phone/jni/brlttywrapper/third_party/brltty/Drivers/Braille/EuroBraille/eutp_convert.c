/*
** convert.c for eutp in /home/obert01/work/eutp/src
**
** Made by
** Login   <obert01@epita.fr>
**
** Started on  Thu Mar 31 17:11:59 2005
Last update Wed Jun  6 13:03:41 2007 Olivier BERT
*/

#include <string.h>
#include <unistd.h>
#include <dirent.h>
#include <ctype.h>
#include "brlapi.h"
#include <sys/stat.h>
#include <sys/types.h>
#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <langinfo.h>
#include <locale.h>
#include "eutp_brl.h"


int	       convert_init(t_env* env)
{
  setlocale(LC_ALL, "");
  env->dos2unix = iconv_open(nl_langinfo(CODESET), "CP850");
  env->unix2dos = iconv_open("CP850//translit", nl_langinfo(CODESET));
  return 0;
}

size_t		dos2unix(t_env* env, char** map, size_t size)
{
  char *tmpmap, *tmpmap2;
  char *p, *p2;
  size_t i = size;
  size_t o = 2 *size;
  size_t newsize = 0;

  if ((tmpmap = malloc(size)) == NULL)
    {
      perror("malloc");
      eutp_abort(9);
    }
  if ((tmpmap2 = malloc(2 * size)) == NULL)
    {
      perror("malloc");
      eutp_abort(9);
    }
  for (i = 0; i < size; i++)
    tmpmap[i] = (*map)[i];
  p = tmpmap;
  p2 = tmpmap2;
  iconv(env->dos2unix, &p, &i, &p2, &o);
  newsize = (2 * size) - o;
  *map = realloc(*map, newsize);
  for (i = 0; i < newsize; i++)
    {
      (*map)[i] = tmpmap2[i];
    }
  free(tmpmap);
  free(tmpmap2);
  return newsize;
}

int		k2txt(t_env* env, char* srcfile, char* destfile)
{
  struct stat st;
  int		fd;
  size_t	size;
  char	*map = 0; 
  char	*newmap = 0;
  unsigned int		i = 0, o = 0;
  char		flg_hdr = 1;

  if ((fd = open(srcfile, O_RDONLY)) == -1)
    {
      perror("open");
      return 0;
    }
  fstat(fd, &st);
  size = st.st_size;
  map = malloc(size + 1);
  newmap = malloc(size + 1);
  read(fd, map, size);
  map[size] = 0;
  close(fd);
  if ((fd = open(destfile, O_WRONLY | O_CREAT | O_TRUNC, 0600)) == -1)
    {
      perror("open");
      return 0;
    }
  while (flg_hdr)
    {
      if (map[i] == 'R' && map[ i - 1] == '\x0b')
	flg_hdr = 2;
      if (flg_hdr == 2 && map[i] == '$' && map[i - 1] == '\x1b')
	{
	  i++;
	  flg_hdr = 0;
	  break;
	}
      i++;
    }
  while (i < size -  3) 
    /* the 3 last characters are specific to the K format */
    {
      if (map[i] == 'P' && map[i - 1] == '\x1b')
	newmap[o++] = '\n';
      if (map[i] == 'L' && map[i - 1] == '\x1b')
	newmap[o++] = '\n';
      if (map[i] == '\x1b' && map[i - 1] == '\x1b')
	newmap[o++] = '\x1b';
      if (map[i] != '\x1b' && map[i - 1] != '\x1b')
	newmap[o++] = map[i];
      i++;
    }
  /* Convert text into the current charset */
  size = dos2unix(env, &newmap, o);
  write(fd, newmap, size);
  free(map);
  free(newmap);
  close(fd);
  return 1;
}

/*
Cette fonction prend le nom du fichier PC (.txt ou .k ou autre) et le
met sous la forme xxxxxxxx (8 lettres) et exhibe l'extension.
Elle retourne 1 si une conversion vers .L est nécessaire,
0 sinon. Le type du fichier est détecté uniquement par rapport à l'extension.
*/
int		normalize_filename(t_env* env)
{
  char* name = NULL;
  int i = 0;
  int j = 0;
  int retval = 0;

  env->curext = 0;
  name = env->list[env->pcfilenum]->d_name;
  /* Reconnaissance de l'extension */
  i = strlen((char*)name);
  if (name[i - 2] == '.' && (toupper(name[i - 1] == 'K')
			     || toupper(name[i - 1]) == 'T'
			     || toupper(name[i - 1] == 'A')
			     || toupper(name[i - 1] == 'L')
			     || toupper(name[i - 1]) == 'B'))
    {
      env->curext = toupper(name[i - 1]);
      retval = 0;  /* no conversion neccesary */
    }
  else
    {
      env->curext = 'K';
      retval = 1; /* Converting from Text file to .K needed */
    }
  i = 0;
  j = 0;
  while (name[i] && i <= 7 && name[i] != '.')
    {
      env->filename[j++] = name[i++];
    }
  while (i <= 7)
    {
      env->filename[j++] = ' ';
      i++;
    }
  env->filename[j++] = 0;
  return retval;
}


int		txt2k(char* srcfile, char* destfile)
{
  struct stat st;
  int		fd;
  size_t	size;
  char* map;

  if ((fd = open(srcfile, O_RDONLY)) == -1)
    {
      perror("open");
      return 0;
    }
  fstat(fd, &st);
  size = st.st_size;
  map = malloc(size + 1);
  read(fd, map, size);
  map[size] = 0;
  close(fd);
  if ((fd = open(destfile, O_WRONLY | O_CREAT | O_TRUNC, 0600)) == -1)
    {
      perror("open");
      return 0;
    }
  /* ecriture de l'en-tête */

  return 1;
}

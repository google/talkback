/*
** pc.c for eutp in /home/obert01/work/eutp/src
**
** Made by Olivier BERT
** Login   <obert01@epita.fr>
**
** Started on  Sun Mar 20 01:27:53 2005 Olivier BERT
Last update Wed Jun  6 20:42:40 2007 Olivier BERT
*/

#include <string.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>

#include "eutp_brl.h"
#include "eutp_pc.h"


extern unsigned char extensions[];
extern unsigned char positions[];

/*
** The filter : we don't want to have directories in the list
*/
static int	filter_files(const struct dirent* d)
{
  struct stat st;

  stat(d->d_name, &st);
  if (S_ISDIR(st.st_mode))
    return 0;
  return 1;
}

int		scanfiles(t_env *env)
{
  env->n = scandir(".", &(env->list), filter_files, alphasort);
  if (env->n < 0)
    {
      perror("scandir");
      return -1;
    }
  return env->n;
}

int		pc_init(t_env *env)
{
  return scanfiles(env);
}

/*
** Show PC files
*/
int		showpcfiles(t_env* env)
{
  unsigned char	pos = positions[env->status];
  char		str[BUFFER_SIZE]; /* what we display to the braille terminal */

  strcpy(str, "PC>BR ");
  strcat(str, env->list[env->pcfilenum]->d_name);
  brl_message(str, pos);
  return 0;
}


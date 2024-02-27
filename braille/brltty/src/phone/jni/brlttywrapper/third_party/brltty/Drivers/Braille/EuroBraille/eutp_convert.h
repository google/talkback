/*
** convert.h for eutp in /home/obert01/work/eutp/src
**
** Made by
** Login   <obert01@epita.fr>
**
** Started on  Thu Mar 31 17:12:05 2005
** Last update Tue Mar 14 16:19:49 2006 Olivier BERT
*/
#ifndef __EUTP_CONVERT_H_
#define __EUTP_CONVERT_H_

int		k2txt(t_env* env, char* srcfile, char* destfile);
int	normalize_filename(t_env*);
int	convert_init(t_env *);
#endif

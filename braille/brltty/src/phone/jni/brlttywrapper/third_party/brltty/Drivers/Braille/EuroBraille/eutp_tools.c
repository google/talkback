/*
** tools.c for eutp in /home/obert01/work/eutp/src
**
** Made by
** Login   <obert01@epita.fr>
**
** Started on  Thu Mar 31 12:28:07 2005
** Last update Fri Mar 24 17:31:39 2006 Olivier BERT
*/

#include <unistd.h>
#include "eutp_brl.h"

void			brl_lasting_message(char* msg)
{
  brl_message(msg, 0);
  sleep(1);
  return;
}


/*
** When extracted from the braille terminal, the filenames are padded to
** 8 chars with blank characters. This function removes theese blanks.
*/
void		remove_blanks(unsigned char *str)
{
  int i = 0;

  while (str[i] && str[i] != ' ')
    i++;
  str[i] = 0;
}

void		pad_blanks(unsigned char *str)
{
  int i = 0;

  while (str[i] && i < 8)
    i++;
  while (i < 8)
    {
      str[i++] = ' ';
    }
  str[i] = 0;
}

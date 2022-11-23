/*
** debug.c for eutp in /home/obert01/work/eutp/src
**
** Made by Olivier BERT
** Login   <obert01@epita.fr>
**
** Started on  Wed Mar 30 12:50:37 2005 Olivier BERT
** Last update Wed Mar 30 12:55:24 2005 Olivier BERT
*/


#include <stdio.h>

int		debug_print_buf(unsigned char* buf)
{
  int i = 0;

  printf("printing received buffer:\n");
  for (i = 0; i < 80; i++)
    printf("%x,", buf[i]);
  printf("\n");
  return 0;
}

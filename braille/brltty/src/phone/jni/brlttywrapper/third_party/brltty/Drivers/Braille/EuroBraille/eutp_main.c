/*
** eutp.c for eutp in /home/obert01/work/eutp/src
**
** Made by Olivier BERT
** Login   <obert01@epita.fr>
**
** Started on  Wed Mar 16 18:31:17 2005 Olivier BERT
Last update Wed Jun  6 13:31:16 2007 Olivier BERT
*/

#include <unistd.h>
#include <stdlib.h>
#include "eutp_brl.h"
#include "eutp_convert.h"
#include <stdio.h>

#include "eutp_pc.h"



int		 main(void)
{
  t_env		env;

  if (brl_init(&env) != 0)
    {
      fprintf(stderr, "Error initializing brlapi !\n");
      exit(E_BRLAPI_ERROR);
    }
  if (pc_init(&env) == -1)
    exit(2);
  convert_init(&env);
  brl_listfiles(&env);
  brl_close();
  return 0;
}

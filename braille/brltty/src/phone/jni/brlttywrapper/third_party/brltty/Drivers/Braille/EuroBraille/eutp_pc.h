#ifndef __EUTP_PC_H_
#define __EUTP_PC_H_
#include <dirent.h>
#include "eutp_brl.h"

int		showpcfiles(t_env*);
int		pc_init(t_env*);



/*
** Libc prototypes
*/
int alphasort(const struct dirent **a, 
	      const struct dirent **b);
int scandir(const char *dir, struct dirent ***namelist,
	   int(*filter)(const struct dirent *),
	   int(*compar)(const struct dirent **, const struct dirent **));
#endif

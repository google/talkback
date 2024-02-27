/*
** eutp_brl.h for eutp in /home/obert01/work/eutp/src
**
** Made by Olivier BERT
** Login   <obert01@epita.fr>
**
** Started on  Wed Mar 16 18:41:29 2005 Olivier BERT
Last update Thu Jun  7 18:05:45 2007 Olivier BERT
*/

#ifndef __EUTP_BRL_H_
# define __EUTP_BRL_H_


#include <iconv.h>
#include <stdio.h>
#include <string.h>
#include "brlapi.h"

/* EUTP version number */
# define EUTP_VERSION	"EUTP 0.2.6"

/* Exit codes */
# define E_OK		0
# define E_BRLAPI_ERROR	3
# define E_READ		4
# define E_WRITE	5

/* some defines */
# define HEADER_LINE "\x0cK/CP8 5.08-0C 1 16 FU \x1bi\x1b$"
# define RULE_LINE "\x0bR 25,80,T8,16,24,32,40,48,56,64,72\x1BP\x1B$"
# define READ_LINE	"FR"
# define CLOSE_FILE	"FC"

# define BUFFER_SIZE		500
# define MAXENT		5

/*
** A structure that contains all informations about the file transfer */
typedef struct
{
  /* the braille terminal selected file */
  unsigned short brfilenum;
  unsigned short pcfilenum;
  /* the file extension index we are searching for on the braille terminal
  ** It is a number between 0 and 3. 0 = 'K' 1 = 'L' etc... */
  unsigned char		curextnum;
  /* L'extension du fichier : K, L, B, T, A. Ce champ est utilisé
     que pour le transfert pc->br. Concrètement, on pourrait se passer
     de cette variable ou de celle du dessus */
  unsigned char	curext;
  /* cette variable dit si on est en train de changer le type de transfert (br->pc ou pc->br,
     si on change l'extension ou si on change le nom du fichier. */
  unsigned char		status;
  /* Cette variable dit si on est en train de transférer du terminal vers le pc ou pas */
  unsigned char		brpc;
  /* Le nombre de fichiers sur le répertoire PC courant */
  int		n;
  /* Liste des fichiers PC dans le répertoire courant */
  struct dirent**		list;
  /* Pour le transfert pc->br, un descripteur de fichier pointant sur le fichier à transférer */
  int		fd;
  /* Stream associated with fd */
  FILE*		fs;
  /* nom du fichier à transférer */
  char	filename[9];
  /* convertisseurs pour les caractères */
  iconv_t	dos2unix;
  iconv_t	unix2dos;
  char		ident[20];
} t_env;


int		brl_init(t_env *);
int		brl_close(void);
int             brl_message(char *str, unsigned char cursorpos);
int             brl_yesno_question(char *str);
int		brl_listfiles(t_env*);


void		eutp_abort(int exitstatus);
ssize_t		brl_read(unsigned char *);
ssize_t		brl_write(unsigned char *, size_t len);
void	brl_writeStr(char *str);

#endif

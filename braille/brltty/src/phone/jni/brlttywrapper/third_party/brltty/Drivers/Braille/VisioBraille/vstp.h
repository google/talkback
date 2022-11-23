/*
 *  Copyright (C) 2006-2019 S&S
 *  Samuel Thibault <samuel.thibault@ens-lyon.org>
 *  Sébastien Hinderer <sebastien.hinderer@ens-lyon.org>
 *
 * This program is free software ; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation ; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY ; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with the program ; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

/* vstp.h
 * files transferring with VisioBraille terminals
 */
#ifndef __VBCP_H
#define __VBCP_H

/* different possible names for file */

#define VSTP_PUT	"vstpp"
#define VSTP_GET	"vstpg"

#define SIZE_PUT	250 /* just like xfl */

#define NUM_TRIES	3

#define TRY_TIMEOUT	20 /* seconds to wait between write retries */

/* variables used when transferring */

extern int transferring;
extern unsigned char burstmode;
extern int backup;
extern char *visiobases_dir;
extern char *visiobases_ext;

/* transfer functions */

typedef void transferfun(char *filename);

extern transferfun fileget;
extern transferfun fileput;

extern void transfer_init(transferfun f);
extern void transfer_finish(transferfun f);

extern void transfer_abort(int exitnum);

extern void transfer_timeout(int signum);

/* Packet types */

#define VB_INIT_PARAMS	'I'
#define VB_LOAD		'L'
#define VB_UNLOAD	'U'
#define VB_AUTOMATIC	'A'
#define VB_MANUAL	'M'
#define VB_OK		'Y'
#define VB_FILEHERE	'F'
#define VB_NEXT		'N'
#define VB_ACK_DATA	'K'
#define VB_HERES_DATA	'D'
#define VB_DATA_OVER	'Z'
#define VB_FILES_OVER	"E"

#define VB_RESET	"#"

#define VB_FILET_AGENDA	'A'
#define VB_FILET_TEXTE	'T'

#define VB_MAXFNLEN	8

/* return codes */

#define RET_EPARSE	1	/* syntax error on command line */
#define RET_ECONN	2	/* connection with BrlNet error */
#define RET_EUNIX	3	/* unix file error */
#define RET_EPROTO	4	/* protocol error */
#define RET_INT		16	/* interrupted by user */

#endif /* __VBCP_H */

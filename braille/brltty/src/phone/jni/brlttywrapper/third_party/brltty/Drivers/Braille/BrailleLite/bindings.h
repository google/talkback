/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2019 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.app/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

/* BrailleLite/bindings.h - key bindings for BLazie Engineering's Braille Lite
 * N. Nair, 5 September 1998
 */

#ifndef BRLTTY_INCLUDED_BL_BINDINGS
#define BRLTTY_INCLUDED_BL_BINDINGS

#include "brl_cmds.h"		/* for BRL_CMD_* codes */

/* When the Braille Lite sends braille key information, bits 0-5 represent
 * dots 1-6 and bit 6 represents the space bar.  For now, we mask out bit 6
 * and just use 64-byte tables.
 */

/* The static arrays must only be in braille.c, so just in case ... */
#ifdef BL_NEED_ARRAYS
#undef BL_NEED_ARRAYS

static const unsigned char brltrans[64] =
{
  ' ', 'a', '1', 'b', '\'', 'k', '2', 'l',
  '`', 'c', 'i', 'f', '/', 'm', 's', 'p',
  '"', 'e', '3', 'h', '9', 'o', '6', 'r',
  '~', 'd', 'j', 'g', '>', 'n', 't', 'q',
  ',', '*', '5', '<', '-', 'u', '8', 'v',
  '.', '%', '{', '$', '+', 'x', '!', '&',
  ';', ':', '4', '|', '0', 'z', '7', '(',
  '_', '?', 'w', '}', '#', 'y', ')', '='
};

#ifdef USE_TEXTTRANS
/* Map from key representations (bits 0-5 for dots 1-6) to BRLTTY dot
   pattern representation (dot 1 bit 0, dot 4 bit 1, dot 2 bit 2, etc) */
static const unsigned char keys_to_dots[0100] = {
  /* 000 */ 0,
  /* 001 */ BRL_DOT1,
  /* 002 */ BRL_DOT2,
  /* 003 */ BRL_DOT1 | BRL_DOT2,
  /* 004 */ BRL_DOT3,
  /* 005 */ BRL_DOT1 | BRL_DOT3,
  /* 006 */ BRL_DOT2 | BRL_DOT3,
  /* 007 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT3,
  /* 010 */ BRL_DOT4,
  /* 011 */ BRL_DOT1 | BRL_DOT4,
  /* 012 */ BRL_DOT2 | BRL_DOT4,
  /* 013 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT4,
  /* 014 */ BRL_DOT3 | BRL_DOT4,
  /* 015 */ BRL_DOT1 | BRL_DOT3 | BRL_DOT4,
  /* 016 */ BRL_DOT2 | BRL_DOT3 | BRL_DOT4,
  /* 017 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT3 | BRL_DOT4,
  /* 020 */ BRL_DOT5,
  /* 021 */ BRL_DOT1 | BRL_DOT5,
  /* 022 */ BRL_DOT2 | BRL_DOT5,
  /* 023 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT5,
  /* 024 */ BRL_DOT3 | BRL_DOT5,
  /* 025 */ BRL_DOT1 | BRL_DOT3 | BRL_DOT5,
  /* 026 */ BRL_DOT2 | BRL_DOT3 | BRL_DOT5,
  /* 027 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT3 | BRL_DOT5,
  /* 030 */ BRL_DOT4 | BRL_DOT5,
  /* 031 */ BRL_DOT1 | BRL_DOT4 | BRL_DOT5,
  /* 032 */ BRL_DOT2 | BRL_DOT4 | BRL_DOT5,
  /* 033 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT4 | BRL_DOT5,
  /* 034 */ BRL_DOT3 | BRL_DOT4 | BRL_DOT5,
  /* 035 */ BRL_DOT1 | BRL_DOT3 | BRL_DOT4 | BRL_DOT5,
  /* 036 */ BRL_DOT2 | BRL_DOT3 | BRL_DOT4 | BRL_DOT5,
  /* 037 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT3 | BRL_DOT4 | BRL_DOT5,
  /* 040 */ BRL_DOT6,
  /* 041 */ BRL_DOT1 | BRL_DOT6,
  /* 042 */ BRL_DOT2 | BRL_DOT6,
  /* 043 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT6,
  /* 044 */ BRL_DOT3 | BRL_DOT6,
  /* 045 */ BRL_DOT1 | BRL_DOT3 | BRL_DOT6,
  /* 046 */ BRL_DOT2 | BRL_DOT3 | BRL_DOT6,
  /* 047 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT3 | BRL_DOT6,
  /* 050 */ BRL_DOT4 | BRL_DOT6,
  /* 051 */ BRL_DOT1 | BRL_DOT4 | BRL_DOT6,
  /* 052 */ BRL_DOT2 | BRL_DOT4 | BRL_DOT6,
  /* 053 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT4 | BRL_DOT6,
  /* 054 */ BRL_DOT3 | BRL_DOT4 | BRL_DOT6,
  /* 055 */ BRL_DOT1 | BRL_DOT3 | BRL_DOT4 | BRL_DOT6,
  /* 056 */ BRL_DOT2 | BRL_DOT3 | BRL_DOT4 | BRL_DOT6,
  /* 057 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT3 | BRL_DOT4 | BRL_DOT6,
  /* 060 */ BRL_DOT5 | BRL_DOT6,
  /* 061 */ BRL_DOT1 | BRL_DOT5 | BRL_DOT6,
  /* 062 */ BRL_DOT2 | BRL_DOT5 | BRL_DOT6,
  /* 063 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT5 | BRL_DOT6,
  /* 064 */ BRL_DOT3 | BRL_DOT5 | BRL_DOT6,
  /* 065 */ BRL_DOT1 | BRL_DOT3 | BRL_DOT5 | BRL_DOT6,
  /* 066 */ BRL_DOT2 | BRL_DOT3 | BRL_DOT5 | BRL_DOT6,
  /* 067 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT3 | BRL_DOT5 | BRL_DOT6,
  /* 070 */ BRL_DOT4 | BRL_DOT5 | BRL_DOT6,
  /* 071 */ BRL_DOT1 | BRL_DOT4 | BRL_DOT5 | BRL_DOT6,
  /* 072 */ BRL_DOT2 | BRL_DOT4 | BRL_DOT5 | BRL_DOT6,
  /* 073 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT4 | BRL_DOT5 | BRL_DOT6,
  /* 074 */ BRL_DOT3 | BRL_DOT4 | BRL_DOT5 | BRL_DOT6,
  /* 075 */ BRL_DOT1 | BRL_DOT3 | BRL_DOT4 | BRL_DOT5 | BRL_DOT6,
  /* 076 */ BRL_DOT2 | BRL_DOT3 | BRL_DOT4 | BRL_DOT5 | BRL_DOT6,
  /* 077 */ BRL_DOT1 | BRL_DOT2 | BRL_DOT3 | BRL_DOT4 | BRL_DOT5 | BRL_DOT6
};
#endif /* USE_TEXTTRANS */

/* This table is for global BRLTTY commands, to be passed straight back to
 * the main module.  If keyboard emulation is off, they will work with or
 * without a chord, unless they are marked as dangerous in dangcmd[] below.
 *
 * Note that key combinations used to initiate internal commands should be
 * left as 0 here.
 */

static const int cmdtrans[0100] = {
  /* Oct   Dots      Command */
  /* 000 (      ) */ 0,
  /* 001 (1     ) */ BRL_CMD_LNUP,
  /* 002 ( 2    ) */ BRL_CMD_KEY(CURSOR_LEFT),
  /* 003 (12    ) */ BRL_CMD_KEY(BACKSPACE),
  /* 004 (  3   ) */ BRL_CMD_CHRLT,
  /* 005 (1 3   ) */ 0 /*k: kbemu*/,
  /* 006 ( 23   ) */ BRL_CMD_KEY(CURSOR_UP),
  /* 007 (123   ) */ BRL_CMD_TOP_LEFT,
  /* 010 (   4  ) */ BRL_CMD_LNDN,
  /* 011 (1  4  ) */ BRL_CMD_CSRTRK,
  /* 012 ( 2 4  ) */ BRL_CMD_DISPMD,
  /* 013 (12 4  ) */ BRL_CMD_FREEZE,
  /* 014 (  34  ) */ BRL_CMD_INFO,
  /* 015 (1 34  ) */ BRL_CMD_MUTE,
  /* 016 ( 234  ) */ BRL_CMD_NXSEARCH,
  /* 017 (1234  ) */ BRL_CMD_PASTE,
  /* 020 (    5 ) */ BRL_CMD_KEY(CURSOR_RIGHT),
  /* 021 (1   5 ) */ 0 /*e: endcmd*/,
  /* 022 ( 2  5 ) */ 0 /*25: prefs*/,
  /* 023 (12  5 ) */ BRL_CMD_HOME,
  /* 024 (  3 5 ) */ 0 /*35: meta*/,
  /* 025 (1 3 5 ) */ 0 /*o: number*/,
  /* 026 ( 23 5 ) */ BRL_CMD_LNBEG,
  /* 027 (123 5 ) */ BRL_CMD_RESTARTBRL,
  /* 030 (   45 ) */ BRL_CMD_CSRJMP_VERT,
  /* 031 (1  45 ) */ BRL_CMD_KEY(DELETE),
  /* 032 ( 2 45 ) */ BRL_CMD_BLK(ROUTE),
  /* 033 (12 45 ) */ 0 /*g: internal cursor*/,
  /* 034 (  345 ) */ 0 /*345: blite internal speechbox*/,
  /* 035 (1 345 ) */ BRL_CMD_NXPGRPH,
  /* 036 ( 2345 ) */ BRL_CMD_KEY(TAB),
  /* 037 (12345 ) */ 0 /*q: finish uppercase*/,
  /* 040 (     6) */ BRL_CMD_CHRRT,
  /* 041 (1    6) */ BRL_CMD_BLK(COPY_LINE),
  /* 042 ( 2   6) */ 0 /*26: dot8shift*/,
  /* 043 (12   6) */ BRL_CMD_BLK(CLIP_ADD),
  /* 044 (  3  6) */ BRL_CMD_SAY_LINE,
  /* 045 (1 3  6) */ 0 /*u: uppsercase*/,
  /* 046 ( 23  6) */ BRL_CMD_BLK(CLIP_NEW),
  /* 047 (123  6) */ BRL_CMD_SWITCHVT_NEXT,
  /* 050 (   4 6) */ BRL_CMD_KEY(ENTER),
  /* 051 (1  4 6) */ 0 /*146: free*/,
  /* 052 ( 2 4 6) */ BRL_CMD_KEY(ESCAPE),
  /* 053 (12 4 6) */ BRL_CMD_PRPGRPH,
  /* 054 (  34 6) */ 0 /*346: free*/,
  /* 055 (1 34 6) */ 0 /*x: xtrl*/,
  /* 056 ( 234 6) */ BRL_CMD_SIXDOTS,
  /* 057 (1234 6) */ 0 /*12346: free*/,
  /* 060 (    56) */ BRL_CMD_KEY(CURSOR_DOWN),
  /* 061 (1   56) */ BRL_CMD_PRSEARCH,
  /* 062 ( 2  56) */ BRL_CMD_LNEND,
  /* 063 (12  56) */ BRL_CMD_BACK,
  /* 064 (  3 56) */ BRL_CMD_BLK(COPY_RECT),
  /* 065 (1 3 56) */ 0 /*z: abort*/,
  /* 066 ( 23 56) */ 0 /*2356: rotate*/,
  /* 067 (123 56) */ BRL_CMD_NXPROMPT,
  /* 070 (   456) */ BRL_CMD_BOT_LEFT,
  /* 071 (1  456) */ BRL_CMD_HELP,
  /* 072 ( 2 456) */ 0 /*w: free*/,
  /* 073 (12 456) */ BRL_CMD_LEARN,
  /* 074 (  3456) */ BRL_CMD_SWITCHVT_PREV,
  /* 075 (1 3456) */ 0 /*y: free*/,
  /* 076 ( 23456) */ BRL_CMD_PRPROMPT,
  /* 077 (123456) */ 0 /*123456: noop*/
};

/* Dangerous commands; 1 bit per command, order as cmdtrans[], set if
 * the corresponding command is dangerous.
 */

static const unsigned char dangcmd[8] =
{ 0x00, 0x88, 0x80, 0x05, 0x40, 0x00, 0x10, 0x00 };

typedef int BarCmds[16];
const BarCmds *barcmds;

/* Two advance bar commands. */
static const BarCmds bar2cmds =
{
/* LeftBar\ RightBar> None         Right        Left         Both         */
/*         None    */ 0          , BRL_CMD_FWINRT , BRL_CMD_LNDN   , BRL_CMD_HWINRT ,
/*         Right   */ BRL_CMD_LNUP   , BRL_CMD_ATTRDN , BRL_CMD_ATTRUP , 0          ,
/*         Left    */ BRL_CMD_FWINLT , BRL_CMD_NXDIFLN, BRL_CMD_PRDIFLN, 0          ,
/*         Both    */ BRL_CMD_HWINLT , BRL_CMD_BOT    , BRL_CMD_TOP    , 0
};

/* One advance bar commands. */
static const BarCmds bar1cmds =
{
/*          None         Left         Right        Both         */
/* None  */ 0          , BRL_CMD_FWINLT , BRL_CMD_FWINRT , 0          ,
/* Right */ BRL_CMD_FWINRT , 0          , 0          , 0          ,
/* Left  */ BRL_CMD_FWINLT , 0          , 0          , 0          ,
/* Both  */ 0          , 0          , 0          , 0
};

/* Left whiz wheel commands. */
static const int lwwcmds[] =
/* None         Up           Down         Press        */
  {0          , BRL_CMD_LNUP   , BRL_CMD_LNDN   , BRL_CMD_ATTRVIS};

/* Right whiz wheel commands. */
static const int rwwcmds[] =
/* None         Up           Down         Press        */
  {0          , BRL_CMD_FWINLT , BRL_CMD_FWINRT , BRL_CMD_CSRVIS };

#endif /* BL_NEED_ARRAYS */


/*
 * Functions for the advance bar.  Currently, these are passed straight
 * back to the main module, so have to be global commands.
 */

/* BrailleLite 18 */
#define BLT_BARLT BRL_CMD_FWINLT
#define BLT_BARRT BRL_CMD_FWINRT


/* Internal commands.  The definitions use the ASCII codes from brltrans[]
 * above.  All must be chorded.
 */

#define BLT_KBEMU 'k'
#define BLT_ROTATE '7'
#define BLT_POSITN 'g'
#define BLT_REPEAT 'o'
#define BLT_CONFIG '3'
#define BLT_ENDCMD 'e'
#define BLT_ABORT 'z'
#define SWITCHVT_NEXT 'v'
#define SWITCHVT_PREV '#'
/* These are valid only in repeat input mode. They duplicate existing
   normal commands. */
#define O_SETMARK 's'
#define O_GOTOMARK 'm'

/* For keyboard emulation mode: */
#define BLT_UPCASE 'u'
#define BLT_UPCOFF 'q'
#define BLT_CTRL 'x'
#ifdef USE_TEXTTRANS
#define BLT_DOT8SHIFT '5'
#endif /* USE_TEXTTRANS */
#define BLT_META '9'

#endif /* BRLTTY_INCLUDED_BL_BINDINGS */

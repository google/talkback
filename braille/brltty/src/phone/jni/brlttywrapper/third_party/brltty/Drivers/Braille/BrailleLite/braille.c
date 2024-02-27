/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2023 by The BRLTTY Developers.
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

/* BrailleLite/braille.c - Braille display library
 * For Blazie Engineering's Braille Lite series
 * Author: Nikhil Nair <nn201@cus.cam.ac.uk>
 * Copyright (C) 1998 by Nikhil Nair.
 * Some additions by: Nicolas Pitre <nico@fluxnic.net>
 * Some modifications copyright 2001 by Stéphane Doyon <s.doyon@videotron.ca>.
 * Some additions by: Dave Mielke <dave@mielke.cc>
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>

#include "log.h"
#include "parse.h"
#include "timing.h"
#include "async_wait.h"
#include "message.h"

typedef enum {
  PARM_BAUDRATE,
  PARM_KBEMU
} DriverParameter;
#define BRLPARMS "baudrate", "kbemu"

#include "brl_driver.h"
#include "braille.h"
#include "io_serial.h"

#define BL_NEED_ARRAYS
#include "bindings.h"		/* for keybindings */

#define QSZ 256			/* size of internal input queue in bytes */
#define INT_CSR_SPEED 2		/* on/off time in cycles */
#define ACK_TIMEOUT 1000	/* timeout in ms for an ACK to come back */

SerialDevice *BL_serialDevice = NULL;		/* file descriptor for Braille display */

static unsigned char *prevdata = NULL;	/* previously received data */
static unsigned char *rawdata = NULL;	/* writebrl() buffer for raw Braille data */
static int blitesz;	/* set to 18 or 40 */
static int waiting_ack = 0;	/* waiting acknowledgement flag */
static int reverse_kbd = 0;	/* reverse keyboard flag */
static int intoverride = 0;	/* internal override flag -
				 * highly dubious behaviour ...
				 */
static int int_cursor = 0;	/* position of internal cursor: 0 = none */
static unsigned int kbemu = 1; /* keyboard emulation (whether you can type) */

/* The input queue is only manipulated by the qput() and qget()
 * functions.
 */
static unsigned char *qbase = NULL;	/* start of queue in memory */
static int qoff = 0;		/* offset of first byte */
static int qlen = 0;		/* number of items in the queue */


/* Data type for a Braille Lite key, including translation into command
 * codes:
 */
typedef struct
  {
    unsigned char raw;		/* raw value, after any keyboard reversal */
    int cmd;			/* command code */
    unsigned char asc;		/* ASCII translation of Braille keys */
    unsigned char spcbar;	/* 1 = on, 0 = off */
    unsigned char routing;	/* routing key number */
  }
blkey;


static int
qput (unsigned char c)
{
  if (qlen == QSZ)
    return EOF;
  qbase[(qoff + qlen++) % QSZ] = c;
  return 0;
}


static int
qget (blkey * kp)
{
  unsigned char c;
  int how;
  static const unsigned char counts[] = {1, 3, 3};
  unsigned char count;

  if (qlen == 0)
    return EOF;
  c = qbase[qoff];

  /* extended sequences start with a zero */
  how = (c == 0)? 1:
        (c == 0x80 && blitesz != 18)? 2:
        0;
  count = counts[how];
  if (qlen < count)
    return EOF;

  memset (kp, 0, sizeof (*kp));
  switch (how) {
    case 0: /* non-extended sequences (BL18) */
      /* We must deal with keyboard reversal here: */
      if (reverse_kbd)
	{
	  if (c >= 0x80)	/* advance bar */
	    c ^= 0x03;
	  else
	    c = (c & 0x40) | ((c & 0x38) >> 3) | ((c & 0x07) << 3);
	}

      /* Now we fill in all the info about the keypress: */
      if (c >= 0x80)		/* advance bar */
	{
	  kp->raw = c;
	  switch (c) {
	    case 0x83:		/* left */
	      kp->cmd = BLT_BARLT;
	      break;

	    case 0x80:		/* right */
	      kp->cmd = BLT_BARRT;
	      break;

	    default:		/* unrecognised keypress */
	      kp->cmd = 0;
              break;
	  }
	}
      else
	{
	  kp->spcbar = ((c & 0x40)? 1: 0);
	  c &= 0x3f;		/* leave only dot key info */
	  kp->raw = c;
	  kp->cmd = cmdtrans[c];
	  kp->asc = brltrans[c];
	}
      break;

    case 1: { /* extended sequences (BL40) */
      unsigned char c2 = qbase[((qoff + 1) % QSZ)];
      unsigned char c3 = qbase[((qoff + 2) % QSZ)];

      /* We must deal with keyboard reversal here: */
      if (reverse_kbd)
	{
	  if (c2 == 0)
	    {			/* advance bars or routing keys */
	      if (c3 & 0x80)	/* advance bars */
		c3 = ((c3 & 0xF0) |
		      ((c3 & 0x1) << 3) | ((c3 & 0x2) << 1) |
		      ((c3 & 0x4) >> 1) | ((c3 & 0x8) >> 3));
	      else if (c3 > 0 && c3 <= blitesz)
		c3 = blitesz - c3 + 1;
	    }
	  else
            {
              c2 = (((c2 & 0x38) >> 3) | ((c2 & 0x07) << 3) |
                    ((c2 & 0x40) << 1) | ((c2 & 0x80) >> 1));
              c3 = ((c3 & 0x40) | ((c3 & 0x38) >> 3) | ((c3 & 0x07) << 3));
            }
	}

      /* Now we fill in all the info about the keypress: */
      if (c2 == 0)		/* advance bars or routing keys */
	{
	  kp->raw = c3;
	  if (c3 & 0x80)
            kp->cmd = (*barcmds)[c3 & 0xF];
	  else if (c3 > 0 && c3 <= blitesz)
	    kp->routing = c3;
	}
      else
	{
	  kp->spcbar = ((c3 & 0x40)? 1: 0);
	  c3 &= 0x3f;		/* leave only dot key info */
	  kp->raw = (c2 & 0xC0) | c3; /* combine info for all 8 dots */
	  /* c2&0x3F and c3&0x3F are the same, i.e. dots 1-6. */
	  kp->cmd = cmdtrans[c3];
	  kp->asc = brltrans[c3];
	}
      break;
    }

    case 2: { /* extended sequences (millennium) */
      unsigned char c3 = qbase[((qoff + 2) % QSZ)];

      /* We must deal with keyboard reversal here: */
      if (reverse_kbd)
        c3 = ((c3 & 0x11) << 3) | ((c3 & 0x22) << 1) | ((c3 & 0x44) >> 1) | ((c3 & 0x88) >> 3);
      kp->raw = c3;

      if (c3 & 0x0f)
        kp->cmd = (*barcmds)[((c3 & 0x1) << 3) | ((c3 & 0x2) << 1) | ((c3 & 0x4) >> 1) | ((c3 & 0x8) >> 3)];
      else if (c3 & 0x30)
        kp->cmd = rwwcmds[(c3 >> 4) & 0x3];
      else if (c3 & 0xc0)
        kp->cmd = lwwcmds[(c3 >> 6) & 0x3];
      else
        kp->cmd = 0;
      break;
    }

    default:
      kp->cmd = 0;
      break;
  }

  /* adjust queue variables for next member */
  qoff = (qoff + count) % QSZ;
  qlen -= count;

  return 0;
}


static void
qfill (void)
{
  unsigned char c;		/* character buffer */

  while (serialReadData (BL_serialDevice, &c, 1, 0, 0) == 1)
    {
      if (waiting_ack && c == 5)	/* ^e is the acknowledgement character ... */
	waiting_ack = 0;
      else
	qput (c);
    }
}


static void
qflush (void)
{
  qfill();
  qlen = 0;
}

static int
await_ack (void) {
  TimePeriod period;
  startTimePeriod(&period, ACK_TIMEOUT);
  waiting_ack = 1;
  do {
    asyncWait(10);	/* sleep for 10 ms */
    qfill();
    if (!waiting_ack) return 1;
  } while (!afterTimePeriod(&period, NULL));
  return 0;
}

static void
write_prebrl (void) {
  static const unsigned char request[] = {0X05, 0X44};			/* code to send before Braille */
  serialWriteData(BL_serialDevice, request, sizeof(request));
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device)
{
  static const unsigned int good_baudrates[] =
    {300,600,1200,2400,4800,9600,19200,38400, 0};
  unsigned int baudrate;
  /* Init string for Model detection */

  if (!*parameters[PARM_BAUDRATE] ||
      !serialValidateBaud(&baudrate, "baud rate",
		    parameters[PARM_BAUDRATE], good_baudrates))
    baudrate = BAUDRATE;

  if (*parameters[PARM_KBEMU])
    if (!validateYesNo(&kbemu, parameters[PARM_KBEMU]))
      logMessage(LOG_WARNING, "%s: %s", "invalid keyboard emulation setting", parameters[PARM_KBEMU]);
  kbemu = !!kbemu;

  if (!isSerialDeviceIdentifier(&device)) {
    unsupportedDeviceIdentifier(device);
    return 0;
  }

  logMessage(LOG_DEBUG, "Opening serial port: %s", device);
  if ((BL_serialDevice = serialOpenDevice(device))) {
    if (serialRestartDevice(BL_serialDevice, baudrate)) {
      if (serialSetFlowControl(BL_serialDevice, SERIAL_FLOW_HARDWARE)) {
        if ((qbase = malloc(QSZ))) {
          qflush();
          write_prebrl();

          if (await_ack()) {
            logMessage(LOG_DEBUG, "Got response.");

            /* Next, let's detect the BLT-Model (18, 40, M20, M40). */
            barcmds = &bar2cmds;
            {
              unsigned char cells[18];

              memset(cells, 0, sizeof(cells));
              serialWriteData(BL_serialDevice, cells, sizeof(cells));
              waiting_ack = 1;
              asyncWait(400);
              qfill();

              if (waiting_ack) {
                /* no response, so it must be BLT40 */
                blitesz = 40;
                brl->keyBindings = "40_m20_m40";
              } else {
                blitesz = sizeof(cells);
                brl->keyBindings = "18";
              }
            }

            {
              static const unsigned char request[] = {0X05, 0X57};			/* code to send before Braille */

              asyncWait(200);
              qflush();
              serialWriteData(BL_serialDevice, request, sizeof(request));
              waiting_ack = 0;
              asyncWait(200);
              qfill();

              if (qlen) {
                char response[qlen + 1];
                int length = 0;

                do {
                  unsigned char byte = qbase[qoff % QSZ];

                  qoff = (qoff + 1) % QSZ, --qlen;
                  if (!byte) break;
                  response[length++] = byte;
                } while (qlen);

                response[length] = 0;
                logMessage(LOG_INFO, "Braille Lite identity: %s", response);

                if ((response[0] == 'X') &&
                    (response[1] == ' ') &&
                    (response[2] == 'B')) {
                  blitesz = atoi(&response[3]);
                  if (blitesz <= 20) barcmds = &bar1cmds;
                }
              }
            }

            logMessage(LOG_NOTICE, "Braille Lite %d detected.", blitesz);
            brl->textColumns = blitesz;	/* initialise size of display - */
            brl->textRows = 1;		/* Braille Lites are single line displays */

            makeOutputTable(dotsTable_ISO11548_1);
            makeInputTable();

            /* Allocate space for buffers */
            if ((prevdata = malloc(brl->textColumns))) {
              memset(prevdata, 0, brl->textColumns);

              if ((rawdata = malloc(brl->textColumns))) {
                return 1;

              //free(rawdata);
              //rawdata = NULL;
              } else {
                logMallocError();
              }

              free(prevdata);
              prevdata = NULL;
            } else {
              logMallocError();
            }
          } else {
            logMessage(LOG_DEBUG, "BrailleLite not responding.");
          }

          free(qbase);
          qbase = NULL;
        } else {
          logMallocError();
        }
      }
    }

    serialCloseDevice(BL_serialDevice);
    BL_serialDevice = NULL;
  }

  return 0;
}


static void
brl_destruct (BrailleDisplay * brl)
{
  if (rawdata) {
    free(rawdata);
    rawdata = NULL;
  }

  if (prevdata) {
    free(prevdata);
    prevdata = NULL;
  }

  if (qbase) {
    free(qbase);
    qbase = NULL;
  }

  if (BL_serialDevice) {
    serialCloseDevice(BL_serialDevice);
    BL_serialDevice = NULL;
  }
}


static int
brl_writeWindow (BrailleDisplay * brl, const wchar_t *text)
{
  short i;			/* loop counter */

  /* If the intoverride flag is set, then calls to writebrl() from the main
   * module are ignored, because the display is in internal use.
   * This is highly antisocial behaviour!
   */
  if (intoverride)
    return 1;

  /* First, the internal cursor: */
  if (int_cursor)
    {
      static int timer = 0;		/* for internal cursor */
      timer = (timer + 1) % (INT_CSR_SPEED * 2);
      brl->buffer[int_cursor - 1] = (timer < INT_CSR_SPEED)?
                                      (BRL_DOT1 | BRL_DOT2 | BRL_DOT3 | BRL_DOT7):
                                      (BRL_DOT4 | BRL_DOT5 | BRL_DOT6 | BRL_DOT8);
    }

  /* Next we must handle display reversal: */
  if (reverse_kbd) {
    for (i=0; i<blitesz; i+=1) {
      rawdata[i] = translateInputCell(brl->buffer[blitesz - 1 - i]);
    }
  } else {
    memcpy(rawdata, brl->buffer, blitesz);
  }

  /* Only refresh display if the data has changed: */
  if (cellsHaveChanged(prevdata, rawdata, blitesz, NULL, NULL, NULL))
    {
      /* Dot mapping from standard to BrailleLite: */
      translateOutputCells(rawdata, rawdata, blitesz);

      /* First we process any pending keystrokes, just in case any of them
       * are ^e ...
       */
      waiting_ack = 0;		/* Not really necessary, but ... */
      qfill ();

      /* Next we send the ^eD sequence, and wait for an ACK */
      waiting_ack = 1;
      /* send the ^ED... */
      write_prebrl();
      if (!await_ack()) return 1;

      /* OK, now we'll suppose we're all clear to send Braille data. */
      serialWriteData(BL_serialDevice, rawdata, blitesz);
      await_ack();
    }
  return 1;
}


static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context)
{
  static enum {
    ST_NORMAL,	/* transparent */
    ST_CURSOR,	/* position internal cursor */
    ST_REPEAT,	/* set repeat count */
    ST_CONFIG	/* preferences options */
  } state = ST_NORMAL;
  static int repeat = 0;		/* repeat count for command */
  static int repeatNext = 0; /* flag to indicate  whether 0 we repeat the
				same command or 1 we get the next command
				and repeat that. */
  static int hold, shift, shiftlck, ctrl, meta;
#ifdef USE_TEXTTRANS
  static int dot8shift;
#endif /* USE_TEXTTRANS */
  static blkey key;
  static char outmsg[41];
  int temp = BRL_CMD_NOOP;

 again:
  if(repeatNext || repeat == 0) {
    /* Process any new keystrokes: */
    qfill ();
    if (qget (&key) == EOF)	/* no keys to process */
      return EOF;
    repeatNext = 0;
  }
  if(repeat>0)
    repeat--;

  /* Our overall behaviour depends on the state variable (see above). */
  switch (state)
    {
    case ST_NORMAL:			/* transparent */
      /* First we deal with external commands: */
      do
	{
	  /* if it's not an external command, go on */
	  if (!key.cmd)
	    break;

	  /* if advance bar, return with corresponding command */
	  if (key.asc == 0)
	    return key.cmd;

	  /* I thought I was smart when I suggested to remove CMD_CUT_END.
	     Well now there's this nasty exception: the command offset
	     depends on the display size! */
	  if(key.cmd == BRL_CMD_BLK(COPY_RECT) || key.cmd == BRL_CMD_BLK(COPY_LINE))
	    key.cmd += blitesz-1;

	  if(key.spcbar && (key.cmd &BRL_MSK_BLK) == BRL_CMD_BLK(PASSKEY)) {
          /*
	    if(!kbemu)
	      return EOF;
          */
	    if (!shiftlck)
	      shift = 0;
	    ctrl = meta = 0;
#ifdef USE_TEXTTRANS
	    dot8shift = 0;
#endif /* USE_TEXTTRANS */
	  }

	  /* always OK if chorded */
	  if (key.spcbar)
	    return key.cmd;

	  /* kbemu could be on, then go on */
	  if (kbemu && context == KTB_CTX_DEFAULT)
	    break;

	  /* if it's a dangerous command it should have been chorded */
	  if (dangcmd[(key.raw & 0x38) >> 3] & (1 << (key.raw & 0x07)))
	    break;

	  /* finally we are OK */
	  return key.cmd;
	}
      while (0);

      /* Next, internal commands: */
      if (key.spcbar)
	switch (key.asc)
	  {
	  case BLT_KBEMU:	/* set keyboard emulation */
	    kbemu ^= 1;
	    shift = shiftlck = ctrl = meta = 0;
#ifdef USE_TEXTTRANS
	    dot8shift = 0;
#endif /* USE_TEXTTRANS */
	    if(kbemu)
	      message (NULL, gettext("keyboard emu on"), MSG_SILENT);
	    else message (NULL, gettext("keyboard emu off"), MSG_SILENT);
	    return BRL_CMD_NOOP;
	  case BLT_ROTATE:	/* rotate Braille Lite by 180 degrees */
	    reverse_kbd ^= 1;
	    return BRL_CMD_NOOP;
	  case BLT_POSITN:	/* position internal cursor */
	    int_cursor = blitesz / 2;
	    state = ST_CURSOR;
	    return BRL_CMD_NOOP;
	  case BLT_REPEAT:	/* set repeat count */
	    hold = 0;
	    snprintf (outmsg, sizeof(outmsg), "%s:", gettext("repeat count"));
	    message (NULL, outmsg, MSG_SILENT | MSG_NODELAY);
	    intoverride = 1;
	    state = ST_REPEAT;
	    return BRL_CMD_NOOP;
	  case BLT_CONFIG:	/* configuration menu */
	    snprintf (outmsg, sizeof(outmsg), "%s? [m/s/r/z]", gettext("config"));
	    message (NULL, outmsg, MSG_SILENT | MSG_NODELAY);
	    intoverride = 1;
	    state = ST_CONFIG;
	    return BRL_CMD_NOOP;
	  case ' ':		/* practical exception for */
	    /* If keyboard mode off, space bar == BRL_CMD_HOME */
	    if (!kbemu || context != KTB_CTX_DEFAULT)
	      return BRL_CMD_HOME;
	  }

      /* check for routing keys */
      if (key.routing)
	return (BRL_CMD_BLK(ROUTE) + key.routing - 1);

      if (!kbemu)
	return BRL_CMD_NOOP;

      /* Now kbemu is definitely on. */
      switch (key.raw & 0xC0)
	{
	case 0x40:		/* dot 7 */
	  shift = 1;
	  break;
	case 0xC0:		/* dot 78 */
	  ctrl = 1;
	  break;
	case 0x80:		/* dot 8 */
#ifdef USE_TEXTTRANS
	  dot8shift = 1;
#else /* USE_TEXTTRANS */
	  meta = 1;
#endif /* USE_TEXTTRANS */
	  break;
	}

      if (key.spcbar && key.asc != ' ')
	switch (key.asc)
	  {
	  case BLT_UPCASE:	/* upper case next */
	    if (shift)
	      shiftlck = 1;
	    else
	      shift = 1;
	    return BRL_CMD_NOOP;
	  case BLT_UPCOFF:	/* cancel upper case */
	    shift = shiftlck = 0;
	    return BRL_CMD_NOOP;
	  case BLT_CTRL:	/* control next */
	    ctrl = 1;
	    return BRL_CMD_NOOP;
#ifdef USE_TEXTTRANS
	  case BLT_DOT8SHIFT:	/* add dot 8 to next pattern */
	    dot8shift = 1;
	    return BRL_CMD_NOOP;
#endif /* USE_TEXTTRANS */
	  case BLT_META:	/* meta next */
	    meta = 1;
	    return BRL_CMD_NOOP;
	  case BLT_ABORT:	/* abort - quit keyboard emulation */
	    kbemu = 0;
	    message (NULL, gettext("keyboard emu off"), MSG_SILENT);
	    return BRL_CMD_NOOP;
	  default:		/* unrecognised command */
	    shift = shiftlck = ctrl = meta = 0;
#ifdef USE_TEXTTRANS
	    dot8shift = 0;
#endif /* USE_TEXTTRANS */
	    return BRL_CMD_NOOP;
	  }

      /* OK, it's an ordinary (non-chorded) keystroke, and kbemu is on. */
#ifndef USE_TEXTTRANS
      if (ctrl && key.asc >= 96)
	/* old code was (key.asc & 0x1f) */
	temp = BRL_CMD_BLK(PASSCHAR) | key.asc | BRL_FLG_INPUT_CONTROL;
      else if (meta && key.asc >= 96)
	temp = BRL_CMD_BLK(PASSCHAR) | key.asc | BRL_FLG_INPUT_META;
      else if (shift && (key.asc & 0x40))
	/* old code was (key.asc & 0xdf) */
	temp = BRL_CMD_BLK(PASSCHAR) | key.asc | BRL_FLG_INPUT_SHIFT;
      else
	temp = BRL_CMD_BLK(PASSCHAR) | key.asc;
#else /* USE_TEXTTRANS */
      temp = BRL_CMD_BLK(PASSDOTS) |
	(keys_to_dots[key.raw &0x3F]
	 | ((meta) ? BRL_FLG_INPUT_META : 0)
	 | ((ctrl) ? (BRL_DOT7 | BRL_DOT8) : 
	    (shift) ? BRL_DOT7 : 
	    (dot8shift) ? BRL_DOT8 : 0));
#endif /* USE_TEXTTRANS */
      if (!shiftlck)
	shift = 0;
      ctrl = meta = 0;
#ifdef USE_TEXTTRANS
      dot8shift = 0;
#endif /* USE_TEXTTRANS */
      outmsg[0] = 0;
      return temp;

    case ST_CURSOR:			/* position internal cursor */
      switch (key.cmd)
	{
	case BRL_CMD_HOME:		/* go to middle */
	  int_cursor = blitesz / 2;
	  break;
	case BRL_CMD_LNBEG:	/* beginning of display */
	  int_cursor = 1;
	  break;
	case BRL_CMD_LNEND:	/* end of display */
	  int_cursor = blitesz;
	  break;
	case BRL_CMD_FWINLT:	/* quarter left */
	  int_cursor = MAX (int_cursor - blitesz / 4, 1);
	  break;
	case BRL_CMD_FWINRT:	/* quarter right */
	  int_cursor = MIN (int_cursor + blitesz / 4, blitesz);
	  break;
	case BRL_CMD_CHRLT:	/* one character left */
	  if (int_cursor > 1)
	    int_cursor--;
	  break;
	case BRL_CMD_CHRRT:	/* one character right */
	  if (int_cursor < blitesz)
	    int_cursor++;
	  break;
	case BRL_CMD_BLK(ROUTE):	/* route cursor */
	  if (key.spcbar)
	    {
	      temp = BRL_CMD_BLK(ROUTE) + int_cursor - 1;
	      int_cursor = 0;
	      state = ST_NORMAL;
	    }
	  return temp;
	case BRL_CMD_BLK(CLIP_NEW):	/* begin copy */
	case BRL_CMD_BLK(CLIP_ADD):
	  if (key.spcbar)
	    {
	      temp = key.cmd + int_cursor - 1;
	      int_cursor = 0;
	      state = ST_NORMAL;
	    }
	  return temp;
	case BRL_CMD_BLK(COPY_RECT):	/* end copy */
	case BRL_CMD_BLK(COPY_LINE):
	  if (key.spcbar)
	    {
	      temp = key.cmd + int_cursor - 1;
	      int_cursor = 0;
	      state = ST_NORMAL;
	    }
	  return temp;
	case BRL_CMD_DISPMD: /* attribute info */
	  temp = BRL_CMD_BLK(DESCCHAR) + int_cursor - 1;
	  int_cursor = 0;
	  state = ST_NORMAL;
	  return temp;
	default:
	  if (key.asc == BLT_ABORT) {
            /* cancel cursor positioning */
	    int_cursor = 0;
	    state = ST_NORMAL;
          }
	  break;
	}
      if (key.routing)
	int_cursor = key.routing;
      return BRL_CMD_NOOP;
    case ST_REPEAT:			/* set repeat count */
      if (key.asc >= '0' && key.asc <= '9')
	{
	  hold = (hold * 10 + key.asc - '0') % 100;
	  if (hold) {
	    snprintf (outmsg, sizeof(outmsg), "%s: %d", gettext("repeat count"), hold);
	  } else {
            snprintf (outmsg, sizeof(outmsg), "%s: ", gettext("repeat count"));
          }
	  intoverride = 0;
	  message (NULL, outmsg, MSG_SILENT | MSG_NODELAY);
	  intoverride = 1;
	}
      else if (key.routing)
	{
	  hold = key.routing +1;
	  snprintf (outmsg, sizeof(outmsg), "%s: %d", gettext("repeat count"), hold);
	  intoverride = 0;
	  message (NULL, outmsg, MSG_SILENT | MSG_NODELAY);
	  intoverride = 1;
	}
      else {
	intoverride = 0;
	outmsg[0] = 0;
	state = ST_NORMAL;
	if (hold > 0) {
	  if (key.asc == SWITCHVT_NEXT || key.asc == SWITCHVT_PREV)
	    /* That's chorded or not... */
	    return BRL_CMD_BLK(SWITCHVT) + (hold-1);
	  else if (key.asc == O_SETMARK)
	    return BRL_CMD_BLK(SETMARK) + (hold-1);
	  else if (key.asc == O_GOTOMARK)
	    return BRL_CMD_BLK(GOTOMARK) + (hold-1);
	  else if (key.spcbar)		/* chorded */
	    switch (key.asc)
	      {
	      case BLT_ENDCMD:	/* set repeat count */
		if (hold > 1) {
		  /* repeat next command */
		  repeat = hold;
		  repeatNext = 1;
		}
		/* fall through */
	      case BLT_ABORT:	/* abort or endcmd */
		return BRL_CMD_NOOP;
	      }
	  /* if the key is any other, start repeating it. */
	  repeat = hold;
	  goto again;
	}
      }
      return BRL_CMD_NOOP;
    case ST_CONFIG:			/* preferences options */
      switch (key.asc)
	{
	case 'm':		/* preferences menu */
	  intoverride = 0;
	  state = ST_NORMAL;
	  return BRL_CMD_PREFMENU;
	case 's':		/* save preferences */
	  intoverride = 0;
	  state = ST_NORMAL;
	  return BRL_CMD_PREFSAVE;
	case 'r':		/* restore saved preferences */
	  intoverride = 0;
	  state = ST_NORMAL;
	  return BRL_CMD_PREFLOAD;
	case BLT_ABORT:	/* abort */
	  intoverride = 0;
	  state = ST_NORMAL;
	default:		/* in any case */
	  return BRL_CMD_NOOP;
	}
    }

  /* We should never reach this point ... */
  return EOF;
}

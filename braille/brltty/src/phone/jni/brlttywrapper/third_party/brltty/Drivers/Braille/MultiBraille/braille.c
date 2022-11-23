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

/* MultiBraille/braille.c - Braille display library
 * the following Tieman B.V. braille terminals are supported
 * (infos out of a techn. product description sent to me from tieman by fax):
 *
 * - Brailleline 125 (no explicit description)
 * - Brailleline PICO II or MB145CR (45 braille modules + 1 dummy)
 * - Brailleline MB185CR (85 braille modules + 1 dummy)
 *
 * Wolfgang Astleitner, March/April 2000
 * Email: wolfgang.astleitner@liwest.at
 * braille.c,v 1.0
 *
 * Mostly based on CombiBraille/braille.c by Nikhil Nair
 */


/* Description of the escape-sequences used by these displays:
 - [ESC][0]
   signal sent to the braille display so that we get the init message
 - [ESC][V][braille length][firmware version][CR]
   init message sent back by the braille display
   * braille length:   20 / 25 / 40 / 80 (decimal)
   * firmware version: needs to be divided by 10.0: so if we receive
     21 (decimal) --> version 2.1
 - [ESC][F][braillekey data][CR]
   don't know what this is good for. description: init the PC for reading
   the top keys as braille keys (0: mode off, 1: mode on)
 - [ESC][Z][braille data][CR]
   braille data from PC to braille display
   (braille-encoded characters ([20|25|40|80] * 8 bit)
 - [ESC][B][beep data][CR]
   send a beep to the piezo-beeper:
   1: long beep
   0: short beep
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>

#include "log.h"
#include "timing.h"
#include "async_wait.h"
#include "ascii.h"

#define BRL_STATUS_FIELDS sfCursorAndWindowColumn, sfCursorAndWindowRow, sfStateDots
#define BRL_HAVE_STATUS_CELLS
#include "brl_driver.h"
#include "braille.h"
#include "tables.h"		/* for keybindings */
#include "io_serial.h"

SerialDevice *MB_serialDevice;			/* file descriptor for Braille display */
static int brlcols;		/* length of braille display (auto-detected) */
static unsigned char *prevdata;	/* previously received data */
static unsigned char status[5], oldstatus[5];	/* status cells - always five */
static unsigned char *rawdata;		/* writebrl() buffer for raw Braille data */
static short rawlen;			/* length of rawdata buffer */

/* message event coming from the braille display to the PC */
typedef struct KeyStroke {
	int block;				/* EOF or blocknumber: */
										/* front keys: 84 (~ [ESC][T][keynumber][CR] )
										 *   (MB185CR also block with '0'-'9', '*', '#')
										 * top keys: 83 (~ [ESC][S][keynumber][CR] )
										 * cursorrouting keys: 82 (~ [ESC][R][keynumber][CR] )
                                                                                 */
	int key;					/* => keynumber */
} KeyStroke;


/* Function prototypes: */
static struct KeyStroke getbrlkey (void);		/* get a keystroke from the MultiBraille */

static int brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
	short n, success;		/* loop counters, flags, etc. */
	unsigned char *init_seq = (unsigned char *)"\002\0330";	/* string to send to Braille to initialise: [ESC][0] */
	unsigned char *init_ack = (unsigned char *)"\002\033V";	/* string to expect as acknowledgement: [ESC][V]... */
	unsigned char c;
	TimePeriod period;

	if (!isSerialDeviceIdentifier(&device)) {
		unsupportedDeviceIdentifier(device);
		return 0;
	}

	brlcols = -1;		/* length of braille display (auto-detected) */
	prevdata = rawdata = NULL;		/* clear pointers */

	/* No need to load translation tables, as these are now
	 * defined in tables.h
	 */

	/* Now open the Braille display device for random access */
	if (!(MB_serialDevice = serialOpenDevice(device))) goto failure;
	if (!serialRestartDevice(MB_serialDevice, BAUDRATE)) goto failure;
	if (!serialSetFlowControl(MB_serialDevice, SERIAL_FLOW_HARDWARE)) goto failure;

	/* MultiBraille initialisation procedure:
	 * [ESC][V][Braillelength][Software Version][CR]
	 * I guess, they mean firmware version with software version :*}
	 * firmware version == [Software Version] / 10.0
         */
	success = 0;
	if (init_seq[0])
		if (serialWriteData (MB_serialDevice, init_seq + 1, init_seq[0]) != init_seq[0])
			goto failure;
	startTimePeriod (&period, ACK_TIMEOUT);		/* initialise timeout testing */
	n = 0;
	do {
		asyncWait (20);
		if (serialReadData (MB_serialDevice, &c, 1, 0, 0) == 0)
			continue;
		if (n < init_ack[0] && c != init_ack[1 + n])
			continue;
		if (n == init_ack[0]) {
			brlcols = c, success = 1;

			/* reading version-info */
			/* firmware version == [Software Version] / 10.0 */
			serialReadData (MB_serialDevice, &c, 1, 0, 0);
			logMessage (LOG_INFO, "MultiBraille: Version: %2.1f", c/10.0);

			/* read trailing [CR] */
			serialReadData (MB_serialDevice, &c, 1, 0, 0);
		}
		n++;
	}
	while (!afterTimePeriod (&period, NULL) && n <= init_ack[0]);

	if (success && (brlcols > 0)) {
          if ((prevdata = malloc(brlcols))) {
            if ((rawdata = malloc(20 + (brlcols * 2)))) {
              brl->textColumns = brlcols;
              brl->textRows = 1;

              brl->statusColumns = 5;
              brl->statusRows = 1;

              MAKE_OUTPUT_TABLE(0X01, 0X02, 0X04, 0X80, 0X40, 0X20, 0X08, 0X10);
              return 1;
            } else {
              logMallocError();
            }

            free(prevdata);
          } else {
            logMallocError();
          }
        }

      failure:
	if (MB_serialDevice) {
           serialCloseDevice(MB_serialDevice);
           MB_serialDevice = NULL;
        }
	return 0;
}


static void brl_destruct (BrailleDisplay *brl) {
	unsigned char *pre_data = (unsigned char *)"\002\033Z";	/* string to send to */
	unsigned char *post_data = (unsigned char *)"\001\015";
	unsigned char *close_seq = (unsigned char *)"";

	rawlen = 0;
	if (pre_data[0]) {
		memcpy (rawdata + rawlen, pre_data + 1, pre_data[0]);
		rawlen += pre_data[0];
	}
	/* Clear the five status cells and the main display: */
	memset (rawdata + rawlen, 0, 5 + 1+ brl->textColumns * brl->textRows);
	rawlen += 5 + 1 + brl->textColumns * brl->textRows;  /* +1 is for dummy module */
	if (post_data[0]) {
		memcpy (rawdata + rawlen, post_data + 1, post_data[0]);
		rawlen += post_data[0];
	}

	/* Send closing sequence: */
	if (close_seq[0]) {
		memcpy (rawdata + rawlen, close_seq + 1, close_seq[0]);
		rawlen += close_seq[0];
	}
	serialWriteData (MB_serialDevice, rawdata, rawlen);

	free (prevdata);
	free (rawdata);

	serialCloseDevice (MB_serialDevice);
}



static int brl_writeStatus (BrailleDisplay *brl, const unsigned char *s) {
	/* Dot mapping: */
	translateOutputCells(status, s, 5);
	return 1;
}


static int brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
	int textChanged = cellsHaveChanged(prevdata, brl->buffer, brl->textColumns*brl->textRows, NULL, NULL, NULL);
	int statusChanged = cellsHaveChanged(oldstatus, status, 5, NULL, NULL, NULL);
	short i;			/* loop counter */
	unsigned char *pre_data = (unsigned char *)"\002\033Z";	/* bytewise accessible copies */
	unsigned char *post_data = (unsigned char *)"\001\015";

	/* Only refresh display if the data has changed: */
	if (textChanged || statusChanged) {
		/* Dot mapping from standard to MultiBraille: */
		translateOutputCells(brl->buffer, brl->buffer, brl->textColumns*brl->textRows);

    rawlen = 0;
		if (pre_data[0]) {
			memcpy (rawdata + rawlen, pre_data + 1, pre_data[0]);
			rawlen += pre_data[0];
		}
		
		/* HACK - ALERT ;-)
		 * 6th module is a dummy-modul and not wired!
		 * but I need to but a dummy char at the beginning, else the stati are shifted ...
                 */
		rawdata[rawlen++] = 0;

		/* write stati */
		for (i = 0; i < 5; i++) {
			rawdata[rawlen++] = status[i];
		}
		
		
		/* write braille message itself */
		for (i = 0; i < brl->textColumns * brl->textRows; i++) {
			rawdata[rawlen++] = brl->buffer[i];
		}
      
		if (post_data[0]) {
			memcpy (rawdata + rawlen, post_data + 1, post_data[0]);
			rawlen += post_data[0];
		}
    
		serialWriteData (MB_serialDevice, rawdata, rawlen);
	}
	return 1;
}


static int brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
	static short status = 0;	/* cursor routing keys mode */
	
	KeyStroke keystroke;

	keystroke = getbrlkey ();
	if (keystroke.block == EOF)
		return EOF;
		
	if (keystroke.block != 'R') {
		/* translate only 'T' and 'S' events
		 * I kicked argtrans[] (never ever needed) --> simply return 0x00
                 */
		if (keystroke.block == 'T')
			keystroke.key = cmd_T_trans[keystroke.key];
		else
			keystroke.key = cmd_S_trans[keystroke.key];
		status = 0;
                if ((keystroke.key == BRL_CMD_BLK(COPY_LINE)) || (keystroke.key == BRL_CMD_BLK(COPY_RECT)))
                  keystroke.key += brlcols - 1;
		return keystroke.key;
	} else { /* directly process 'R' events */
	  /* cursor routing key 1 or 2 pressed: begin / end block to copy'n'paste */
		/* (counting starts with 0 !) */
		if (keystroke.key == 1 || keystroke.key == 2) {
			status = keystroke.key;
			return EOF;
		}
		/* cursor routing keys 3, 4 and 5: invidiually defined functions */
		/* (counting starts with 0 !) */
		if (keystroke.key >= 3 && keystroke.key <= 5) {
			return cmd_R_trans[keystroke.key];
		}
		switch (status) {
			case 0:			/* ordinary cursor routing */
				return keystroke.key + BRL_CMD_BLK(ROUTE) - MB_CR_EXTRAKEYS;
			case 1:			/* begin block */
				status = 0;
				return keystroke.key + BRL_CMD_BLK(CLIP_NEW) - MB_CR_EXTRAKEYS;
			case 2:			/* end block */
				status = 0;
				return keystroke.key + BRL_CMD_BLK(COPY_RECT) - MB_CR_EXTRAKEYS;
		}
		status = 0;
	}
	/* should never reach this, just to keep compiler happy ;-) */
	return EOF;
}


/* getbrlkey ()
 * returns a keystroke event
 */

static struct KeyStroke getbrlkey (void) {
	unsigned char c, c_temp;		/* character buffer */
	KeyStroke keystroke;
		
	while (serialReadData (MB_serialDevice, &c, 1, 0, 0) == 1) {
		if (c != ESC) continue;	/* advance to next ESC-sequence */

		serialReadData (MB_serialDevice, &c, 1, 0, 0);		/* read block number */
		switch (c) {
			case 'T':			/* front key message, (MB185CR only: also '0'-'9', '*', '#') */
			case 'S':			/* top key message [1-2-4--8-16-32] */
			case 'R':			/* cursor routing key [0 - maxkeys] */
				keystroke.block = c;
				serialReadData (MB_serialDevice, &c, 1, 0, 0);		/* read keynumber */
				keystroke.key = c;
				serialReadData (MB_serialDevice, &c, 1, 0, 0);		/* read trailing [CR] */
  				/* logMessage(LOG_NOTICE, "MultiBraille.o: Receiving: Key=%d, Block=%c", keystroke.key, keystroke.block); */
				return keystroke;
			default:			/* not supported command --> ignore */
				c_temp = c;
				keystroke.block = EOF;	/* invalid / not supported keystroke */
				serialReadData (MB_serialDevice, &c, 1, 0, 0);		/* read keynumber */
  				keystroke.key = 0;
  				logMessage(LOG_NOTICE, "MultiBraille.o: Ignored: Key=%d, Block=%c", keystroke.key, c_temp);
				return keystroke;
		}
	}
	keystroke.block = EOF;
	keystroke.key = 0;
	return keystroke;
}

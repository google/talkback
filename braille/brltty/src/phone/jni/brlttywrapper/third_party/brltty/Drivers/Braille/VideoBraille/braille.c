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

/* Thanks to the authors of the Vario-HT driver: the implementation of this
 * driver is similar to the Vario-HT one.
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>

#include "log.h"
#include "timing.h"
#include "ports.h"

#include "brl_driver.h"
#include "braille.h"

static unsigned char lastbuff[40];

#define LPTSTATUSPORT LPTPORT+1
#define LPTCONTROLPORT LPTPORT+2
  
static void vbclockpause() {
  int i;
  for (i = 0; i<=VBCLOCK*100; i++) ;
}

static void vbdisplay(unsigned char *vbBuf) {
  int i,j;
  char b;
  for (j = 0; j<VBSIZE; j++) {
    for (i = 7; i>=0; i--) {
      b = (vbBuf[j] << i) & VBLPTDATA;
      writePort1(LPTPORT, b);
      vbclockpause();
      writePort1(LPTPORT, b | VBLPTCLOCK);
      vbclockpause();
    }
  }
  writePort1(LPTPORT, b | VBLPTCLOCK);
  for (i = 0; i<=7; i++) vbclockpause();
  writePort1(LPTPORT, 0);
  for (i = 0; i<=7; i++) vbclockpause();
  writePort1(LPTPORT, VBLPTSTROBE);
  for (i = 0; i<=7; i++) vbclockpause();
  writePort1(LPTPORT, 0);
  vbclockpause();
}

static int vbinit() {
  if (enablePorts(LOG_ERR, LPTPORT, 3)) {
    if (enablePorts(LOG_ERR, 0X80, 1)) {
      makeOutputTable(dotsTable_ISO11548_1);

      {
        unsigned char alldots[40];
        memset(alldots, 0XFF, 40);
        vbdisplay(alldots);
      }

      return 0;
    }
    disablePorts(LPTPORT, 3);
  }

  logMessage(LOG_ERR, "Error: must be superuser");
  return -1;
}

void vbsleep(long x) {
  int i;
  for (i = 0; i<x; i++) writePort1(0x80, 1);
}

static void BrButtons(vbButtons *dest) {
  char i;
  dest->bigbuttons = 0;
  dest->keypressed = 0;
  for (i = 47; i>=40; i--) {
    writePort1(LPTPORT, i);
    vbsleep(VBDELAY);
    if ((readPort1(LPTSTATUSPORT) & 0x08)==0) {
      dest->bigbuttons |= (1 << (i-40));
      dest->keypressed = 1;
    }
  }
  dest->routingkey = 0;
  for (i = 40; i>0; i--) {
    writePort1(LPTPORT, i-1);
    vbsleep(VBDELAY);
    if ((readPort1(LPTSTATUSPORT) & 0x08)==0) {
      dest->routingkey = i;
      dest->keypressed = 1;
      break;
    }
  }
}

static int brl_construct(BrailleDisplay *brl, char **parameters, const char *dev) {
  /*	Seems to signal en error */ 
  if (!vbinit()) {
    /* Theese are pretty static */ 
    brl->textColumns=40;
    brl->textRows=1;
    return 1;
  }
  return 0;
}

static void brl_destruct(BrailleDisplay *brl) {
}

static int brl_writeWindow(BrailleDisplay *brl, const wchar_t *text) {
  const size_t cells = 40;
  unsigned char outbuff[cells];

  /* Only display something if the data actually differs, this 
  *  could most likely cause some problems in redraw situations etc
  *  but since the darn thing wants to redraw quite frequently otherwise 
  *  this still makes a better lookin result */ 
  if (cellsHaveChanged(lastbuff, brl->buffer, cells, NULL, NULL, NULL)) {
    translateOutputCells(outbuff, brl->buffer, cells);
    vbdisplay(outbuff);
    vbdisplay(outbuff);
    brl->writeDelay += VBREFRESHDELAY;
  }
  return 1;
}

static int brl_readCommand(BrailleDisplay *brl, KeyTableCommandContext context) {
  vbButtons buttons;
  BrButtons(&buttons);
  if (!buttons.keypressed) {
    return EOF;
  } else {
    vbButtons b;
    do {
      BrButtons(&b);
      buttons.bigbuttons |= b.bigbuttons;

      {
        const TimeValue duration = {
          .seconds = 0,
          .nanoseconds = 1 * NSECS_PER_USEC
        };

        accurateDelay(&duration);
      }
    } while (b.keypressed);
    /* Test which buttons has been pressed */
    if (buttons.bigbuttons==KEY_UP) return BRL_CMD_LNUP;
    else if (buttons.bigbuttons==KEY_LEFT) return BRL_CMD_FWINLT;
    else if (buttons.bigbuttons==KEY_RIGHT) return BRL_CMD_FWINRT;
    else if (buttons.bigbuttons==KEY_DOWN) return BRL_CMD_LNDN;
    else if (buttons.bigbuttons==KEY_ATTRIBUTES) return BRL_CMD_ATTRVIS;
    else if (buttons.bigbuttons==KEY_CURSOR) return BRL_CMD_CSRVIS;
    else if (buttons.bigbuttons==KEY_HOME) {
      /* If a routing key has been pressed, then mark the beginning of a block;
         go to cursor position otherwise */
      return (buttons.routingkey>0) ? BRL_CMD_BLK(CLIP_NEW)+buttons.routingkey-1 : BRL_CMD_HOME;
    }
    else if (buttons.bigbuttons==KEY_MENU) {
      /* If a routing key has been pressed, then mark the end of a block;
         go to preferences menu otherwise */
      return (buttons.routingkey>0) ? BRL_CMD_BLK(COPY_RECT)+buttons.routingkey-1 : BRL_CMD_PREFMENU;
    }
    else if (buttons.bigbuttons==(KEY_ATTRIBUTES | KEY_MENU)) return BRL_CMD_PASTE;
    else if (buttons.bigbuttons==(KEY_CURSOR | KEY_LEFT)) return BRL_CMD_CHRLT;
    else if (buttons.bigbuttons==(KEY_HOME | KEY_RIGHT)) return BRL_CMD_CHRRT;
    else if (buttons.bigbuttons==(KEY_UP | KEY_LEFT)) return BRL_CMD_TOP_LEFT;
    else if (buttons.bigbuttons==(KEY_RIGHT | KEY_DOWN)) return BRL_CMD_BOT_LEFT;
    else if (buttons.bigbuttons==(KEY_ATTRIBUTES | KEY_DOWN)) return BRL_CMD_HELP;
    else if (buttons.bigbuttons==(KEY_MENU | KEY_CURSOR)) return BRL_CMD_INFO;
    else if (buttons.bigbuttons==0) {
      /* A cursor routing key has been pressed */
      if (buttons.routingkey>0) {
        const TimeValue duration = {
          .seconds = 0,
          .nanoseconds = 5 * NSECS_PER_USEC
        };

        accurateDelay(&duration);
        return BRL_CMD_BLK(ROUTE)+buttons.routingkey-1;
      }
      else return EOF;
    } else
      return EOF;
  }
}

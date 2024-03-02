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

#include "prologue.h"

#include <stdio.h>
#include <string.h>

#include "log.h"
#include "parse.h"
#include "scr.h"
#include "message.h"

typedef enum {
  PARM_DISPSIZE=0,
  PARM_PROMVER=1,
  PARM_BAUD=2
} DriverParameter;
#define BRLPARMS "displaysize", "promversion", "baud"

#define BRL_HAVE_PACKET_IO
#include "brl_driver.h"
#include "braille.h"
#include "brldefs-vs.h"
#include "io_serial.h"

#define MAXPACKETSIZE 512

static SerialDevice *serialDevice;
#ifdef SendIdReq
static struct TermInfo {
  unsigned char code; 
  unsigned char version[3];
  unsigned char f1;
  unsigned char size[2];
  unsigned char dongle;
  unsigned char clock;
  unsigned char routing;
  unsigned char flash;
  unsigned char prog;
  unsigned char lcd;
  unsigned char f2[11];
} terminfo;
#endif /* SendIdReq */

/* Function : brl_writePacket */ 
/* Sends a packet of size bytes, stored at address p to the braille terminal */
/* Returns 0 if everything is right, -1 if an error occured while sending */
static ssize_t brl_writePacket(BrailleDisplay *brl, const void *packet, size_t size)
{
  const unsigned char *p = packet;
  int lgtho = 1;
  unsigned char obuf[MAXPACKETSIZE];
  const unsigned char *x;
  unsigned char *y = obuf;
  unsigned char chksum=0;
  int i,res;

  *y++ = 02;
  for (x=p; (x-p) < size; x++) {
    chksum ^= *x;
    if ((*x) <= 5) {
      *y = 01;
      y++; lgtho++; 
      *y = ( *x ) | 0x40;
    } else *y = *x;
    y++; lgtho++; 
  }
  if (chksum<=5) {
    *y = 1; y++; lgtho++;  
    chksum |= 0x40;
  }
  *y = chksum; y++; lgtho++; 
  *y = 3; y++; lgtho++; 
  for (i=1; i<=5; i++) {
    if (serialWriteData(serialDevice,obuf,lgtho) != lgtho) continue; /* write failed, retry */
    serialAwaitOutput(serialDevice);
    serialAwaitInput(serialDevice, 1000);
    res = serialReadData(serialDevice,&chksum,1,0,0);
    if ((res==1) && (chksum == 0x04)) return 0;
  }
  return (-1);
}

/* Function : brl_readPacket */
/* Reads a packet of at most size bytes from the braille terminal */
/* and puts it at the specified adress */
/* Packets are read into a local buffer until completed and valid */
/* and are then copied to the buffer pointed by p. In this case, */
/* The size of the packet is returned */
/* If a packet is too long, it is discarded and a message sent to the syslog */
/* "+" packets are silently discarded, since they are only disturbing us */
static ssize_t brl_readPacket(BrailleDisplay *brl, void *p, size_t size) 
{
  size_t offset = 0;
  static unsigned char ack = 04;
  static unsigned char nack = 05;
  static int apacket = 0;
  static unsigned char prefix, checksum;
  unsigned char ch;
  static unsigned char buf[MAXPACKETSIZE]; 
  static unsigned char *q;
  if ((p==NULL) || (size<2) || (size>MAXPACKETSIZE)) return 0; 
  while (serialReadChunk(serialDevice,&ch,&offset,1,0,1000)) {
    if (ch==0x02) {
      apacket = 1;
      prefix = 0xff; 
      checksum = 0;
      q = &buf[0];
    } else if (apacket) {
      if (ch==0x01) {
        prefix &= ~(0x40); 
      } else if (ch==0x03) {
        if (checksum==0) {
          serialWriteData(serialDevice,&ack,1); 
          apacket = 0; q--;
          if (buf[0]!='+') {
            memcpy(p,buf,(q-buf));
            return q-&buf[0]; 
          }
        } else {
          serialWriteData(serialDevice,&nack,1);
          apacket = 0;
          return 0;
        }
      } else {
        if ((q-&buf[0])>=size) {
          logMessage(LOG_WARNING,"Packet too long: discarded");
          apacket = 0;
          return 0;
        }
        ch &= prefix; prefix |= 0x40;
        checksum ^= ch;
        (*q) = ch; q++;   
      }
    }
    offset = 0;
  } 
  return 0;
}

/* Function : brl_reset */
/* This routine is called by the brlnet server, when an application that */
/* requested a raw-mode communication with the braille terminal dies before */
/* restoring a normal communication mode */
static int brl_reset(BrailleDisplay *brl)
{
  static unsigned char RescuePacket[] = {'#'}; 
  brl_writePacket(brl,RescuePacket,sizeof(RescuePacket));
  return 1;
}

/* Function : brl_construct */
/* Opens and configures the serial port properly */
/* if brl->textColumns <= 0 when brl_construct is called, then brl->textColumns is initialized */
/* either with the BRAILLEDISPLAYSIZE constant, defined in braille.h */
/* or with the size got through identification request if it succeeds */
/* Else, brl->textColumns is left unmodified by brl_construct, so that */
/* the braille display can be resized without reloading the driver */ 
static int brl_construct(BrailleDisplay *brl, char **parameters, const char *device)
{
#ifdef SendIdReq
  unsigned char ch = '?';
  int i;
#endif /* SendIdReq */
  int ds = BRAILLEDISPLAYSIZE;
  int promVersion = 4;
  unsigned int ttyBaud = 57600;
  if (*parameters[PARM_DISPSIZE]) {
    int dsmin=20, dsmax=40;
    if (!validateInteger(&ds, parameters[PARM_DISPSIZE], &dsmin, &dsmax))
      logMessage(LOG_WARNING, "%s: %s", "invalid braille display size", parameters[PARM_DISPSIZE]);
  }
  if (*parameters[PARM_PROMVER]) {
    int pvmin=3, pvmax=6;
    if (!validateInteger(&promVersion, parameters[PARM_PROMVER], &pvmin, &pvmax))
      logMessage(LOG_WARNING, "%s: %s", "invalid PROM version", parameters[PARM_PROMVER]);
  }
  if (*parameters[PARM_BAUD]) {
    unsigned int baud;
    if (serialValidateBaud(&baud, "TTY baud", parameters[PARM_BAUD], NULL)) {
      ttyBaud = baud;
    }
  }

  if (!isSerialDeviceIdentifier(&device)) {
    unsupportedDeviceIdentifier(device);
    return 0;
  }
  if (!(serialDevice = serialOpenDevice(device))) return 0;
  serialSetParity(serialDevice, SERIAL_PARITY_ODD);
  if (promVersion<4) serialSetFlowControl(serialDevice, SERIAL_FLOW_INPUT_CTS);
  serialRestartDevice(serialDevice,ttyBaud); 
#ifdef SendIdReq
  {
    brl_writePacket(brl,(unsigned char *) &ch,1); 
    i=5; 
    while (i>0) {
      if (brl_readPacket(brl,(unsigned char *) &terminfo,sizeof(terminfo))!=0) {
        if (terminfo.code=='?') {
          terminfo.f2[10] = '\0';
          break;
        }
      }
      i--;
    }
    if (i==0) {
      logMessage(LOG_WARNING,"Unable to identify terminal properly");  
      if (!brl->textColumns) brl->textColumns = BRAILLEDISPLAYSIZE;  
    } else {
      logMessage(LOG_INFO,"Braille terminal description:");
      logMessage(LOG_INFO,"   version=%c%c%c",terminfo.version[0],terminfo.version[1],terminfo.version[2]);
      logMessage(LOG_INFO,"   f1=%c",terminfo.f1);
      logMessage(LOG_INFO,"   size=%c%c",terminfo.size[0],terminfo.size[1]);
      logMessage(LOG_INFO,"   dongle=%c",terminfo.dongle);
      logMessage(LOG_INFO,"   clock=%c",terminfo.clock);
      logMessage(LOG_INFO,"   routing=%c",terminfo.routing);
      logMessage(LOG_INFO,"   flash=%c",terminfo.flash);
      logMessage(LOG_INFO,"   prog=%c",terminfo.prog);
      logMessage(LOG_INFO,"   lcd=%c",terminfo.lcd);
      logMessage(LOG_INFO,"   f2=%s",terminfo.f2);  
      if (brl->textColumns<=0)
        brl->textColumns = (terminfo.size[0]-'0')*10 + (terminfo.size[1]-'0');
    }
  }
#else /* SendIdReq */
  brl->textColumns = ds;
#endif /* SendIdReq */
  brl->textRows=1; 

  {
    /* The following table defines how internal brltty format is converted to */
    /* VisioBraille format. */
    /* The table is declared static so that it is in data segment and not */
    /* in the stack */ 
    static const TranslationTable outputTable = {
#include "brl-out.h"
    };
    setOutputTable(outputTable);
  }

  return 1;
}

/* Function : brl_destruct */
/* Closes the braille device and deallocates dynamic structures */
static void brl_destruct(BrailleDisplay *brl)
{
  if (serialDevice) {
    serialCloseDevice(serialDevice);
  }
}

/* function : brl_writeWindow */
/* Displays a text on the braille window, only if it's different from */
/* the one alreadz displayed */
static int brl_writeWindow(BrailleDisplay *brl, const wchar_t *text)
{
  static unsigned char brailleDisplay[81]= { 0x3e }; /* should be large enough for everyone */
  static unsigned char prevData[80];

  if (cellsHaveChanged(prevData, brl->buffer, brl->textColumns, NULL, NULL, NULL)) {
    translateOutputCells(brailleDisplay+1, brl->buffer, brl->textColumns);
    if (brl_writePacket(brl, (unsigned char *)&brailleDisplay, brl->textColumns+1) == -1) return 0;
  }

  return 1;
}

/* Function : keyToCommand */
/* Converts a key code to a brltty command according to the context */
static int keyToCommand(BrailleDisplay *brl, KeyTableCommandContext context, int code)
{
  static int ctrlpressed = 0; 
  static int altpressed = 0;
  static int cut = 0;
  static int descchar = 0;
  unsigned char ch;
  int type;
  ch = code & 0xff;
  type = code & (~ 0xff);
  if (code==0) return 0;
  if (code==EOF) return EOF;
  if (type==BRL_VSMSK_CHAR) {
    int command = ch | BRL_CMD_BLK(PASSCHAR) | altpressed | ctrlpressed;
    altpressed = ctrlpressed = 0;
    return command;
  }
  if (type==BRL_VSMSK_ROUTING) {
    ctrlpressed = altpressed = 0;
    switch (cut) {
      case 0:
        if (descchar) { descchar = 0; return ((int) ch) | BRL_CMD_BLK(DESCCHAR); }
        else return ((int) ch) | BRL_CMD_BLK(ROUTE);
      case 1: cut++; return ((int) ch) | BRL_CMD_BLK(CLIP_NEW);
      case 2: cut = 0; return ((int) ch) | BRL_CMD_BLK(COPY_LINE);
    }
    return EOF; /* Should not be reached */
  } else if (type==BRL_VSMSK_FUNCTIONKEY) {
    ctrlpressed = altpressed = 0;
    switch (code) {
      case BRL_VSKEY_A1: return BRL_CMD_BLK(SWITCHVT);
      case BRL_VSKEY_A2: return BRL_CMD_BLK(SWITCHVT)+1;
      case BRL_VSKEY_A3: return BRL_CMD_BLK(SWITCHVT)+2;
      case BRL_VSKEY_A6: return BRL_CMD_BLK(SWITCHVT)+3;
      case BRL_VSKEY_A7: return BRL_CMD_BLK(SWITCHVT)+4;
      case BRL_VSKEY_A8: return BRL_CMD_BLK(SWITCHVT)+5;
      case BRL_VSKEY_B5: cut = 1; return EOF;
      case BRL_VSKEY_B6: return BRL_CMD_TOP_LEFT; 
      case BRL_VSKEY_D6: return BRL_CMD_BOT_LEFT;
      case BRL_VSKEY_A4: return BRL_CMD_FWINLTSKIP;
      case BRL_VSKEY_B8: return BRL_CMD_FWINLTSKIP;
      case BRL_VSKEY_A5: return BRL_CMD_FWINRTSKIP;
      case BRL_VSKEY_D8: return BRL_CMD_FWINRTSKIP;
      case BRL_VSKEY_B7: return BRL_CMD_LNUP;
      case BRL_VSKEY_D7: return BRL_CMD_LNDN;
      case BRL_VSKEY_C8: return BRL_CMD_FWINRT;
      case BRL_VSKEY_C6: return BRL_CMD_FWINLT;
      case BRL_VSKEY_C7: return BRL_CMD_HOME;
      case BRL_VSKEY_B2: return BRL_CMD_KEY(CURSOR_UP);
      case BRL_VSKEY_D2: return BRL_CMD_KEY(CURSOR_DOWN);
      case BRL_VSKEY_C3: return BRL_CMD_KEY(CURSOR_RIGHT);
      case BRL_VSKEY_C1: return BRL_CMD_KEY(CURSOR_LEFT);
      case BRL_VSKEY_B3: return BRL_CMD_CSRVIS;
      case BRL_VSKEY_D1: return BRL_CMD_KEY(DELETE);  
      case BRL_VSKEY_D3: return BRL_CMD_KEY(INSERT);
      case BRL_VSKEY_C5: return BRL_CMD_PASTE;
      case BRL_VSKEY_D5: descchar = 1; return EOF;
      default: return EOF;
    }
  } else if (type==BRL_VSMSK_OTHER) {
    /* ctrlpressed = 0; */
    if ((ch>=0xe1) && (ch<=0xea)) {
      int flags = altpressed;
      ch-=0xe1;
      altpressed = 0;
      return flags | BRL_CMD_BLK(PASSKEY) | ( BRL_KEY_FUNCTION + ch); 
    }
    /* altpressed = 0; */
    switch (code) {
      case BRL_VSKEY_PLOC_LT: return BRL_CMD_SIXDOTS;
      case BRL_VSKEY_BACKSPACE: return BRL_CMD_KEY(BACKSPACE);
      case BRL_VSKEY_TAB: return BRL_CMD_KEY(TAB);
      case BRL_VSKEY_RETURN: return BRL_CMD_KEY(ENTER);
      case BRL_VSKEY_PLOC_PLOC_A: return BRL_CMD_HELP;
      case BRL_VSKEY_PLOC_PLOC_B: return BRL_CMD_TUNES; 
      case BRL_VSKEY_PLOC_PLOC_C: return BRL_CMD_PREFMENU;
      case BRL_VSKEY_PLOC_PLOC_D: return BRL_CMD_KEY(PAGE_DOWN);
      case BRL_VSKEY_PLOC_PLOC_E: return BRL_CMD_KEY(END);
      case BRL_VSKEY_PLOC_PLOC_F: return BRL_CMD_FREEZE;
      case BRL_VSKEY_PLOC_PLOC_H: return BRL_CMD_KEY(HOME);
      case BRL_VSKEY_PLOC_PLOC_I: return BRL_CMD_INFO;
      case BRL_VSKEY_PLOC_PLOC_L: return BRL_CMD_LEARN;
      case BRL_VSKEY_PLOC_PLOC_R: return BRL_CMD_PREFLOAD;
      case BRL_VSKEY_PLOC_PLOC_S: return BRL_CMD_PREFSAVE;
      case BRL_VSKEY_PLOC_PLOC_T: return BRL_CMD_CSRTRK;
      case BRL_VSKEY_PLOC_PLOC_U: return BRL_CMD_KEY(PAGE_UP);
      case BRL_VSKEY_CONTROL: ctrlpressed = BRL_FLG_INPUT_CONTROL; return BRL_CMD_NOOP;
      case BRL_VSKEY_ALT: altpressed = BRL_FLG_INPUT_META; return BRL_CMD_NOOP;   
      case BRL_VSKEY_ESCAPE: return BRL_CMD_KEY(ESCAPE);
      default: return EOF;
    }
  }
  return EOF; 
}

/* Function : readKey */
/* Reads a key. The result is context-independent */
/* The intermediate value contains a keycode, masked with the key type */
/* the keytype is one of BRL_NORMALCHAR, BRL_FUNCTIONKEY or BRL_ROUTING */
/* for a normal character, the keycode is the latin1-code of the character */
/* for function-keys, codes 0 to 31 are reserved for A1 to D8 keys */
/* codes after 32 are for ~~* combinations, the order has to be determined */
/* for BRL_ROUTING, the code is the ofset to route, starting from 0 */
static int readKey(BrailleDisplay *brl)
{
  unsigned char ch, packet[MAXPACKETSIZE];
  static int routing = 0;
  ssize_t packetSize;
  packetSize = brl_readPacket(brl,packet,sizeof(packet));
  if (packetSize==0) return EOF;
  if ((packet[0]!=0x3c) && (packet[0]!=0x3d) && (packet[0]!=0x23)) {
    logUnexpectedPacket(packet, packetSize);
    return EOF;
  }
  ch = packet[1];
  if (routing) {
    routing=0;
    if (ch>=0xc0)  return (packet[1]-0xc0) | BRL_VSMSK_ROUTING;
    return EOF;
  }
  if ((ch>=0xc0) && (ch<=0xdf)) return (ch-0xc0) | BRL_VSMSK_FUNCTIONKEY;
  if (ch==0x91) {
    routing = 1;
    return BRL_CMD_NOOP;
  } 
  if ((ch>=0x20) && (ch<=0x9e)) {
    switch (ch) {
      case 0x80: ch = 0xc7; break;
      case 0x81: ch = 0xfc; break;
      case 0x82: ch = 0xe9; break;
      case 0x83: ch = 0xe2; break;
      case 0x84: ch = 0xe4; break;
      case 0x85: ch = 0xe0; break;
      case 0x87: ch = 0xe7; break;
      case 0x88: ch = 0xea; break;
      case 0x89: ch = 0xeb; break;
      case 0x8a: ch = 0xe8; break; 
      case 0x8b: ch = 0xef; break;
      case 0x8c: ch = 0xee; break;
      case 0x8f: ch = 0xc0; break;
      case 0x93: ch = 0xf4; break;
      case 0x94: ch = 0xf6; break;
      case 0x96: ch = 0xfb; break; 
      case 0x97: ch = 0xf9; break;
      case 0x9e: ch = 0x60; break;
    }
    return ch | BRL_VSMSK_CHAR;
  }
  return ch | BRL_VSMSK_OTHER;
}

/* Function : brl_readCommand */
/* Reads a command from the braille keyboard */
static int brl_readCommand(BrailleDisplay *brl, KeyTableCommandContext context)
{
  return keyToCommand(brl,context,readKey(brl));
}

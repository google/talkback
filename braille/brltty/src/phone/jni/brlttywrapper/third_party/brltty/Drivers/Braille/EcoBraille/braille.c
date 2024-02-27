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

/* EcoBraille/braille.c - Braille display library for ECO Braille series
 * Copyright (C) 1999 by Oscar Fernandez <ofa@once.es>
 * See the GNU Lesser General Public license for details in the LICENSE-LGPL file
 *
 * For debuging define DEBUG variable
 */

/* Changes:
 *      mar 1' 2000:
 *              - fix correct size of braille lines.
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>

#include "log.h"

#define BRL_HAVE_STATUS_CELLS
#include "brl_driver.h"
#include "braille.h"
#include "io_serial.h"

/* Braille display parameters */
typedef struct{
    const char *Name;
    int Cols;
    int NbStCells;
} BRLPARAMS;

static const BRLPARAMS Models[NB_MODEL] ={
  {
    /* ID == 0 */
    "ECO20",
    20,
    2
  }
  ,
  {
    /* ID == 1 */
    "ECO40",
    40,
    4
  }
  ,
  {
    /* ID == 2 */
    "ECO80",
    80,
    4
  }
};

#define BRLROWS		1
#define MAX_STCELLS	4	/* hiest number of status cells */

/* Global variables */
static SerialDevice *serialDevice;			/* file descriptor for Braille display */
static unsigned char *rawdata;		/* translated data to send to Braille */
static unsigned char Status[MAX_STCELLS]; /* to hold status */
static const BRLPARAMS *model;		/* points to terminal model config struct */
static int BrailleSize=0;		/* Braille size of braille line */

#ifdef DEBUG
int brl_log;
#endif /* DEBUG */

/* Communication codes */
static char BRL_ID[] = "\x10\x02\xF1";
#define DIM_BRL_ID 3
static unsigned char SYS_READY[] = {0X10, 0X02, 0XF1, 0X57, 0X57, 0X57, 0X10, 0X03};
#define DIM_SYS_READY sizeof(SYS_READY)
static char BRL_READY[] UNUSED = "\x10\x02\x2E";
#define DIM_BRL_READY 3
static char BRL_WRITE_PREFIX[] = "\x61\x10\x02\xBC";
#define DIM_BRL_WRITE_PREFIX 4
static char BRL_WRITE_SUFIX[] = "\x10\x03";
#define DIM_BRL_WRITE_SUFIX 2
static char BRL_KEY[] = "\x10\x02\x88";
#define DIM_BRL_KEY 2


/* Status Sensors */
#define KEY_ST_SENSOR1	0xD5  /* Byte A */
#define KEY_ST_SENSOR2	0xD6
#define KEY_ST_SENSOR3	0xD0
#define KEY_ST_SENSOR4	0xD1

/* Main Sensors */
#define KEY_MAIN_MIN	0x80
#define KEY_MAIN_MAX	0xCF

/* Front Keys */
#define KEY_DOWN	0x01  /* Byte B */
#define KEY_RIGHT	0x02
#define KEY_CLICK	0x04
#define KEY_LEFT	0x08
#define KEY_UP		0x10

/* Function Keys */
#define KEY_F9		0x01  /* byte C */
#define KEY_ALT		0x02
#define KEY_F0		0x04
#define KEY_SHIFT	0x40

#define KEY_F1		0x01  /* Byte D */
#define KEY_F2		0x02
#define KEY_F3		0x04
#define KEY_F4		0x08
#define KEY_F5		0x10
#define KEY_F6		0x20
#define KEY_F7		0x40
#define KEY_F8		0x80


static int WriteToBrlDisplay(unsigned char *Data)
{
  unsigned int size = DIM_BRL_WRITE_PREFIX + BrailleSize + DIM_BRL_WRITE_SUFIX;
  unsigned char buffer[size];
  unsigned char *byte = buffer;
  
  byte = mempcpy(byte, BRL_WRITE_PREFIX, DIM_BRL_WRITE_PREFIX);
  byte = mempcpy(byte, Data, BrailleSize);
  byte = mempcpy(byte, BRL_WRITE_SUFIX, DIM_BRL_WRITE_SUFIX);
 
  serialWriteData(serialDevice, buffer, byte-buffer);
  return 0;
}

static int brl_construct(BrailleDisplay *brl, char **parameters, const char *device)
{
  short ModelID = MODEL;
  unsigned char buffer[DIM_BRL_ID + 6];

  if (!isSerialDeviceIdentifier(&device)) {
    unsupportedDeviceIdentifier(device);
    return 0;
  }

  rawdata = NULL;	/* clear pointers */

  /* Open the Braille display device */
  if (!(serialDevice = serialOpenDevice(device))) goto failure;
  
#ifdef DEBUG
  lockUmask();
  brl_log = open("/tmp/brllog", O_CREAT | O_WRONLY);
  unlockUmask();

  if(brl_log < 0){
    goto failure;
  }
#endif /* DEBUG */

  /* autodetecting ECO model */
  do{
      /* DTR back on */
      serialRestartDevice(serialDevice, BAUDRATE);	/* activate new settings */
      
      /* The 2 next lines can be commented out to try autodetect once anyway */
      if(ModelID != ECO_AUTO){
	break;
      }
      	
      if(serialReadData(serialDevice, &buffer, DIM_BRL_ID + 6, 600, 100) == DIM_BRL_ID + 6){
	  if(memcmp (buffer, BRL_ID, DIM_BRL_ID) == 0){
	  
	    /* Possible values; 0x20, 0x40, 0x80 */
	    int tmpModel=buffer[DIM_BRL_ID] / 0x20;

	    switch(tmpModel){
	     case 1: ModelID=0;
	       break;
	     case 2: ModelID=1;
	       break;
	     case 4: ModelID=2;
	       break;
	    default: ModelID=1;
	    }
	  }
      }
  }while(ModelID == ECO_AUTO);
  
  if(ModelID >= NB_MODEL || ModelID < 0){
    goto failure;		/* unknown model */
  }
    
  /* Need answer to BR */
  /*do{*/
      serialWriteData(serialDevice, SYS_READY, DIM_SYS_READY);
      serialReadData(serialDevice, &buffer, DIM_BRL_READY + 6, 100, 100);
      /*}while(strncmp (buffer, BRL_READY, DIM_BRL_READY));*/
      
      logMessage(LOG_DEBUG, "buffer is: %s",buffer);
  
  /* Set model params */
  model = &Models[ModelID];
  brl->textColumns = model->Cols;		/* initialise size of main display */
  brl->textRows = BRLROWS;		/* ever is 1 in this type of braille lines */
  
  MAKE_OUTPUT_TABLE(0X10, 0X20, 0X40, 0X01, 0X02, 0X04, 0X80, 0X08);

  /* Need to calculate the size; Cols + Status + 1 (space between) */
  BrailleSize = brl->textColumns + model->NbStCells + 1;

  /* Allocate space for buffers */
  rawdata = malloc(BrailleSize); /* Phisical size */
  if(!rawdata){
     goto failure;
  }    

  /* Empty buffers */
  memset(rawdata, 0, BrailleSize);
  memset(Status, 0, MAX_STCELLS);

return 1;

failure:;
  if(rawdata){
     free(rawdata);
  }
       
return 0;
}


static void brl_destruct(BrailleDisplay *brl)
{
  free(rawdata);
  serialCloseDevice(serialDevice);

#ifdef DEBUG  
  close(brl_log);
#endif /* DEBUG */
}


static int brl_writeWindow(BrailleDisplay *brl, const wchar_t *text)
{
  unsigned char *byte = rawdata;
  /* This Braille Line need to display all information, include status */
  
  /* Make status info to rawdata */
  byte = translateOutputCells(byte, Status, model->NbStCells);

  /* step a physical space with main cells */
  *byte++ = 0;
  
  /* Make main info to rawdata */
  byte = translateOutputCells(byte, brl->buffer, brl->textColumns);
     
  /* Write to Braille Display */
  WriteToBrlDisplay(rawdata);
  return 1;
}


static int brl_writeStatus(BrailleDisplay *brl, const unsigned char *st)
{
  /* Update status cells */
  memcpy(Status, st, model->NbStCells);
  return 1;
}


static int brl_readCommand(BrailleDisplay *brl, KeyTableCommandContext context)
{
  int res = EOF;
  long bytes = 0;
  unsigned char *pBuff;
  unsigned char buff[18 + 1];
  
#ifdef DEBUG
  char tmp[80];
#endif /* DEBUG */

  /* Read info from Braille Line */
  if((bytes = serialReadData(serialDevice, buff, 18, 0, 0)) >= 9){

#ifdef DEBUG
     sprintf(tmp, "Type %d, Bytes read: %.2x %.2x %.2x %.2x %.2x %.2x %.2x %.2x %.2x %.2x\n",
        type, buff[0], buff[1], buff[2], buff[3], buff[4], buff[5], buff[6], buff[7], buff[8], buff[9]);
     write(brl_log, tmp, strlen(tmp)); 
#endif /* DEBUG */
  
     /* Is a Key? */
     if((pBuff=(unsigned char *)strstr((char *)buff, BRL_KEY))){  
    
        /* Byte A. Check Status sensors */
	switch(*(pBuff+3)){
	   case KEY_ST_SENSOR1:
	        res = BRL_CMD_HELP;
	        break;

	   case KEY_ST_SENSOR2:
	        res = BRL_CMD_PREFMENU;
	        break;

	   case KEY_ST_SENSOR3:
	        res = BRL_CMD_DISPMD;
	        break;

	   case KEY_ST_SENSOR4:
	        res = BRL_CMD_INFO;
	        break;
        }

	/* Check Main Sensors */
	if(*(pBuff+3) >= KEY_MAIN_MIN && *(pBuff+3) <= KEY_MAIN_MAX){
	
	   /* Nothing */
	}
	
	/* Byte B. Check Front Keys */
	switch(*(pBuff+4)){
	   case KEY_DOWN: /* Down */
	        res = BRL_CMD_LNDN;
	        break;

	   case KEY_RIGHT: /* Right */
	        res = BRL_CMD_FWINRT;
	        break;

	   case KEY_CLICK: /* Eco20 Go to cursor */
	   
	        /* Only for ECO20, haven't function keys */
		if(model->Cols==20){
	           res = BRL_CMD_HOME;
		}
	        break;

	   case KEY_LEFT: /* Left */
	        res = BRL_CMD_FWINLT;
	        break;

	   case KEY_UP: /* Up  */
	        res = BRL_CMD_LNUP;
	        break;

	   case KEY_UP|KEY_CLICK: /* Top of screen  */
	        return(BRL_CMD_TOP);
	        break;

	   case KEY_DOWN|KEY_CLICK: /* Bottom of screen */
	        return(BRL_CMD_BOT);
	        break;

	   case KEY_LEFT|KEY_CLICK: /* Left one half window */
	        return(BRL_CMD_HWINLT);
	        break;

	   case KEY_RIGHT|KEY_CLICK: /* Right one half window */
	        return(BRL_CMD_HWINRT);
	        break;
        }

	/* Byte C. Some Function Keys */
	switch(*(pBuff+5)){
	   case KEY_F9:
	        /* Nothing */
	        break;

           case KEY_ALT:
	        /* Nothing */
	        break;

	   case KEY_F0:
	        /* Nothing */
	        break;

	   case KEY_SHIFT: /* Cursor traking */
		if(*(pBuff+6)==KEY_F8){
		     return(BRL_CMD_CSRTRK);
		}
	        break;
        }
	

	/* Byte D. Rest of Function Keys */
	switch(*(pBuff+6)){
	   case KEY_F1:
	        /* Nothing */
	        break;

	   case KEY_F2:  /* go to cursor */
                res = BRL_CMD_HOME;
	        break;

	   case KEY_F3:
	        /* Nothing */
	        break;

	   case KEY_F4:
	        /* Nothing */
	        break;

	   case KEY_F5: /* togle cursor visibility */
	        res = BRL_CMD_CSRVIS;
	        break;

	   case KEY_F6:
	        /* Nothing */
	        break;

	   case KEY_F7:
	        /* Nothing */
	        break;

	   case KEY_F8: /* Six dot mode */
	        res = BRL_CMD_SIXDOTS;
	        break;
        }
     }
  }
  
return(res);
}

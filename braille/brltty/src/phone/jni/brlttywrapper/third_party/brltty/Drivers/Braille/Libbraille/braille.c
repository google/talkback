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

/* Libbraille/braille.c - Braille display driver using libbraille
 *
 * Written by Sébastien Sablé <sable@users.sourceforge.net>
 *
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>

#include "log.h"
#include "core.h"

#include <braille.h>

typedef enum {
  PARM_DEVICE,
  PARM_DRIVER,
  PARM_TABLE
} DriverParameter;
#define BRLPARMS "device", "driver", "table"

#include "brl_driver.h"

static int
brl_construct(BrailleDisplay *brl, char **parameters, const char *device)
{
  if(*parameters[PARM_DEVICE])
    braille_config(BRL_DEVICE, parameters[PARM_DEVICE]);

  if(*parameters[PARM_DRIVER])
    braille_config(BRL_DRIVER, parameters[PARM_DRIVER]);

  if(*parameters[PARM_TABLE])
    braille_config(BRL_TABLE, parameters[PARM_TABLE]);

  if(braille_init())
    {
      logMessage(LOG_INFO, "Libbraille Version: %s", braille_info(BRL_VERSION));

#ifdef BRL_PATH
      logMessage(LOG_DEBUG, "Libbraille Installation Directory: %s", braille_info(BRL_PATH));
#endif /* BRL_PATH */

#ifdef BRL_PATHCONF
      logMessage(LOG_DEBUG, "Libbraille Configuration Directory: %s", braille_info(BRL_PATHCONF));
#endif /* BRL_PATHCONF */

#ifdef BRL_PATHTBL
      logMessage(LOG_DEBUG, "Libbraille Tables Directory: %s", braille_info(BRL_PATHTBL));
#endif /* BRL_PATHTBL */

#ifdef BRL_PATHDRV
      logMessage(LOG_DEBUG, "Libbraille Drivers Directory: %s", braille_info(BRL_PATHDRV));
#endif /* BRL_PATHDRV */

      logMessage(LOG_INFO, "Libbraille Table: %s", braille_info(BRL_TABLE));
      logMessage(LOG_INFO, "Libbraille Driver: %s", braille_info(BRL_DRIVER));
      logMessage(LOG_INFO, "Libbraille Device: %s", braille_info(BRL_DEVICE));

      logMessage(LOG_INFO, "Display Type: %s", braille_info(BRL_TERMINAL));
      logMessage(LOG_INFO, "Display Size: %d", braille_size());

      brl->textColumns = braille_size();  /* initialise size of display */
      brl->textRows = 1;

      MAKE_OUTPUT_TABLE(
        BRAILLE(1, 0, 0, 0, 0, 0, 0, 0),
        BRAILLE(0, 1, 0, 0, 0, 0, 0, 0),
        BRAILLE(0, 0, 1, 0, 0, 0, 0, 0),
        BRAILLE(0, 0, 0, 1, 0, 0, 0, 0),
        BRAILLE(0, 0, 0, 0, 1, 0, 0, 0),
        BRAILLE(0, 0, 0, 0, 0, 1, 0, 0),
        BRAILLE(0, 0, 0, 0, 0, 0, 1, 0),
        BRAILLE(0, 0, 0, 0, 0, 0, 0, 1)
      );
      makeInputTable();
  
      braille_timeout(100);

      return 1;
    }
  else
    {
      logMessage(LOG_DEBUG, "Libbraille initialization error: %s", braille_geterror());
    }
  
  return 0;
}

static void
brl_destruct(BrailleDisplay *brl)
{
  braille_close();
}

static int
brl_writeWindow(BrailleDisplay *brl, const wchar_t *text)
{
  if(text)
    {
      char bytes[brl->textColumns];
      int i;

      for(i = 0; i < brl->textColumns; ++i)
        {
          wchar_t character = text[i];
          bytes[i] = iswLatin1(character)? character: '?';
        }
      braille_write(bytes, brl->textColumns);

      if(brl->cursor != BRL_NO_CURSOR)
        {
          braille_filter(translateOutputCell(getScreenCursorDots()), brl->cursor);
        }

      braille_render();
    }

  return 1;
}

static int
brl_readCommand(BrailleDisplay *brl, KeyTableCommandContext context)
{
  int res = EOF;
  signed char status;
  brl_key key;

  status = braille_read(&key);
  if(status == -1)
    {
      logMessage(LOG_ERR, "error in braille_read: %s", braille_geterror());
      res = BRL_CMD_RESTARTBRL;
    }
  else if(status)
    {
      switch(key.type)
	{
	case BRL_NONE:
	  break;
	case BRL_CURSOR:
	  res = BRL_CMD_BLK(ROUTE) + key.code;
	  break;
	case BRL_CMD:
	  switch(key.code)
	    {
	    case BRLK_UP:
	      res = BRL_CMD_KEY(CURSOR_UP);
	      break;
	    case BRLK_DOWN:
	      res = BRL_CMD_KEY(CURSOR_DOWN);
	      break;
	    case BRLK_RIGHT:
	      res = BRL_CMD_KEY(CURSOR_RIGHT);
	      break;
	    case BRLK_LEFT:
	      res = BRL_CMD_KEY(CURSOR_LEFT);
	      break;
	    case BRLK_INSERT:
	      res = BRL_CMD_KEY(INSERT);
	      break;
	    case BRLK_HOME:
	      res = BRL_CMD_KEY(HOME);
	      break;
	    case BRLK_END:
	      res = BRL_CMD_KEY(END);
	      break;
	    case BRLK_PAGEUP:
	      res = BRL_CMD_KEY(PAGE_UP);
	      break;
	    case BRLK_PAGEDOWN:
	      res = BRL_CMD_KEY(PAGE_DOWN);
	      break;
	    case BRLK_BACKWARD:
	      res = BRL_CMD_FWINLT;
	      break;
	    case BRLK_FORWARD:
	      res = BRL_CMD_FWINRT;
	      break;
	    case BRLK_ABOVE:
	      res = BRL_CMD_LNUP;
	      break;
	    case BRLK_BELOW:
	      res = BRL_CMD_LNDN;
	      break;
	    default:
	      break;
	    }
	  break;
	case BRL_KEY:
	  res = BRL_CMD_BLK(PASSDOTS) | translateInputCell(key.braille);
	  break;
	default:
          break;
	}
    }

  return res;
}

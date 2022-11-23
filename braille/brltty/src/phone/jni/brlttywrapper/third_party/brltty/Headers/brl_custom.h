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

#ifndef BRLTTY_INCLUDED_BRL_CUSTOM
#define BRLTTY_INCLUDED_BRL_CUSTOM

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  /* must be first - do not modify */
  BRL_CUSTOM_BASIC_COMMAND_BASE = BRL_basicCommandCount - 1,

  /* Define all custom basic commands below this point.
   * Their names should begin with BRL_CMD_CUSTOM_.
   * No values should be assigned.
   */

} BRL_CustomBasicCommand;

typedef enum {
  /* must be first - do not modify */
  BRL_CUSTOM_BLOCK_COMMAND_BASE = BRL_blockCommandCount - 1,

  /* Define all custom block commands below this point.
   * Their names should begin with BRL_BLK_CUSTOM_.
   * No values should be assigned.
   */

} BRL_CustomBlockCommand;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_BRL_CUSTOM */

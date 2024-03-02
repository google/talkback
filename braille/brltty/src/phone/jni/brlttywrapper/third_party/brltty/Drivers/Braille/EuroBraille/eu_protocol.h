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

/** EuroBraille/eu_protocol.h -- Protocol defines, structures and unions
 ** This file contains all definitions about the two protocols.
 ** It is also used to store protocol method headers and the main structure
 ** to access the terminal generically by the High-level part.
 **
 ** See braille.c for the high-level access, and io.h for the low-level access.
**/


#ifndef __EU_PROTOCOL_H__
#define __EU_PROTOCOL_H__

#include "cmd_enqueue.h"
#include "ktb_types.h"
#include "brl_cmds.h"
#include "brl_utils.h"
#include "brl_base.h"

typedef struct {
  const char *protocolName;

  int (*initializeDevice) (BrailleDisplay *brl);
  int (*resetDevice) (BrailleDisplay *brl);

  ssize_t (*readPacket) (BrailleDisplay *brl, void *packet, size_t size);
  ssize_t (*writePacket) (BrailleDisplay *brl, const void *packet, size_t size);

  int (*readCommand) (BrailleDisplay *brl, KeyTableCommandContext c);
  int (*writeWindow) (BrailleDisplay *brl);

  int (*hasVisualDisplay) (BrailleDisplay *brl);
  int (*writeVisual) (BrailleDisplay *brl, const wchar_t *text);
} ProtocolOperations;

typedef struct {
  const ProtocolOperations *protocol;
  int (*awaitInput) (BrailleDisplay *brl, int timeout);
  int (*readByte) (BrailleDisplay *brl, unsigned char *byte, int wait);
  ssize_t (*writeData) (BrailleDisplay *brl, const void *data, size_t size);
} InputOutputOperations;

extern const InputOutputOperations *io;
extern const ProtocolOperations clioProtocolOperations;
extern const ProtocolOperations esysirisProtocolOperations;

#define KEY_ENTRY(s,t,k,n) {.value = {.group=EU_GRP_##s, .number=EU_##t##_##k}, .name=n}
EXTERNAL_KEY_TABLE(clio)
EXTERNAL_KEY_TABLE(iris)
EXTERNAL_KEY_TABLE(esys_small)
EXTERNAL_KEY_TABLE(esys_medium)
EXTERNAL_KEY_TABLE(esys_large)
EXTERNAL_KEY_TABLE(esytime)

#endif /* __EU_PROTOCOL_H__ */

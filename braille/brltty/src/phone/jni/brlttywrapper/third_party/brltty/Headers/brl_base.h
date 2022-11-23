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

#ifndef BRLTTY_INCLUDED_BRL_BASE
#define BRLTTY_INCLUDED_BRL_BASE

#include "brl_types.h"
#include "gio_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define KEY_TABLE_LIST_REFERENCE const KeyTableDefinition *const *
#define KEY_TABLE_LIST_SYMBOL CONCATENATE(brl_ktb_,DRIVER_CODE)
#define KEY_TABLE_LIST_DECLARATION const KeyTableDefinition *const KEY_TABLE_LIST_SYMBOL[]
#define LAST_KEY_TABLE_DEFINITION NULL
#define BEGIN_KEY_TABLE_LIST \
  extern KEY_TABLE_LIST_DECLARATION; \
  KEY_TABLE_LIST_DECLARATION = {
#define END_KEY_TABLE_LIST LAST_KEY_TABLE_DEFINITION};

#define BRL_KEY_GROUP(drv,grp) drv ## _GRP_ ## grp
#define BRL_KEY_NAME(drv,grp,key) drv ## _ ## grp ## _ ## key

#define BRL_KEY_GROUP_ENTRY(drv,grp,nam) KEY_GROUP_ENTRY(BRL_KEY_GROUP(drv, grp), nam)
#define BRL_KEY_NUMBER_ENTRY(drv,grp,num,nam) {.value={.group=BRL_KEY_GROUP(drv, grp), .number=(num)}, .name=nam}
#define BRL_KEY_NAME_ENTRY(drv,grp,key,nam) BRL_KEY_NUMBER_ENTRY(drv, grp, BRL_KEY_NAME(drv, grp, key), nam)

static inline void
setBrailleKeyTable (BrailleDisplay *brl, const KeyTableDefinition *ktd) {
  brl->keyBindings = ktd->bindings;
  brl->keyNames = ktd->names;
}

#define TRANSLATION_TABLE_SIZE 0X100
typedef unsigned char TranslationTable[TRANSLATION_TABLE_SIZE];

#define DOTS_TABLE_SIZE 8
typedef unsigned char DotsTable[DOTS_TABLE_SIZE];

extern const DotsTable dotsTable_ISO11548_1;
extern const DotsTable dotsTable_rotated;

extern void makeTranslationTable (const DotsTable dots, TranslationTable table);
extern void reverseTranslationTable (const TranslationTable from, TranslationTable to);

extern void setOutputTable (const TranslationTable table);
extern void makeOutputTable (const DotsTable dots);
extern void *translateOutputCells (unsigned char *target, const unsigned char *source, size_t count);
extern unsigned char translateOutputCell (unsigned char cell);

extern void makeInputTable (void);
extern void *translateInputCells (unsigned char *target, const unsigned char *source, size_t count);
extern unsigned char translateInputCell (unsigned char cell);

#define MAKE_OUTPUT_TABLE(dot1, dot2, dot3, dot4, dot5, dot6, dot7, dot8) { \
  static const DotsTable dots = { \
    (dot1), (dot2), (dot3), (dot4), (dot5), (dot6), (dot7), (dot8) \
  }; \
  makeOutputTable(dots); \
}

extern void applyBrailleDisplayOrientation (unsigned char *cells, size_t count);

extern int awaitBrailleInput (BrailleDisplay *brl, int timeout);

typedef int BrailleSessionInitializer (BrailleDisplay *brl);

extern int connectBrailleResource (
  BrailleDisplay *brl,
  const char *identifier,
  const GioDescriptor *descriptor,
  BrailleSessionInitializer *initializeSession
);

typedef int BrailleSessionEnder (BrailleDisplay *brl);

extern void disconnectBrailleResource (
  BrailleDisplay *brl,
  BrailleSessionEnder *endSession
);

typedef enum {
  BRL_PVR_INVALID,
  BRL_PVR_INCLUDE,
  BRL_PVR_EXCLUDE
} BraillePacketVerifierResult;

typedef BraillePacketVerifierResult BraillePacketVerifier (
  BrailleDisplay *brl,
  const unsigned char *bytes, size_t size,
  size_t *length, void *data
);

extern size_t readBraillePacket (
  BrailleDisplay *brl,
  GioEndpoint *endpoint,
  void *packet, size_t size,
  BraillePacketVerifier *verifyPacket, void *data
);

extern int writeBraillePacket (
  BrailleDisplay *brl, GioEndpoint *endpoint,
  const void *packet, size_t size
);

extern int writeBrailleMessage (
  BrailleDisplay *brl, GioEndpoint *endpoint,
  int type,
  const void *packet, size_t size
);

extern int acknowledgeBrailleMessage (BrailleDisplay *brl);

typedef int BrailleRequestWriter (BrailleDisplay *brl);

typedef size_t BraillePacketReader (
  BrailleDisplay *brl,
  void *packet, size_t size
);

typedef enum {
  BRL_RSP_CONTINUE,
  BRL_RSP_DONE,
  BRL_RSP_FAIL,
  BRL_RSP_UNEXPECTED
} BrailleResponseResult;

typedef BrailleResponseResult BrailleResponseHandler (
  BrailleDisplay *brl,
  const void *packet, size_t size
);

extern int probeBrailleDisplay (
  BrailleDisplay *brl, unsigned int retryLimit,
  GioEndpoint *endpoint, int inputTimeout,
  BrailleRequestWriter *writeRequest,
  BraillePacketReader *readPacket, void *responsePacket, size_t responseSize,
  BrailleResponseHandler *handleResponse
);

extern void releaseBrailleKeys (BrailleDisplay *brl);

typedef uint32_t KeyNumberSet;
#define KEY_NUMBER_BIT(number) (UINT32_C(1) << (number))
extern KeyNumberSet makeKeyNumberSet (KEY_NAME_TABLES_REFERENCE keys, KeyGroup group);

extern int enqueueKeyEvent (
  BrailleDisplay *brl,
  KeyGroup group, KeyNumber number, int press
);

extern int enqueueKeyEvents (
  BrailleDisplay *brl,
  KeyNumberSet set, KeyGroup group, KeyNumber number, int press
);

extern int enqueueKey (
  BrailleDisplay *brl,
  KeyGroup group, KeyNumber number
);

extern int enqueueKeys (
  BrailleDisplay *brl,
  KeyNumberSet set, KeyGroup group, KeyNumber number
);

extern int enqueueUpdatedKeys (
  BrailleDisplay *brl,
  KeyNumberSet new, KeyNumberSet *old, KeyGroup group, KeyNumber number
);

extern int enqueueUpdatedKeyGroup (
  BrailleDisplay *brl,
  unsigned int count,
  const unsigned char *new,
  unsigned char *old,
  KeyGroup group
);

extern int enqueueXtScanCode (
  BrailleDisplay *brl,
  unsigned char code, unsigned char escape,
  KeyGroup group00, KeyGroup groupE0, KeyGroup groupE1
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_BRL_BASE */

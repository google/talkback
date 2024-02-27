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

#include <fcntl.h>
#include <stdio.h>
#include <errno.h>
#include <stdarg.h>
#include <string.h>

#include "log.h"
#include "parameters.h"
#include "ascii.h"
#include "cmd.h"
#include "parse.h"
#include "async_handle.h"
#include "async_wait.h"
#include "async_alarm.h"
#include "timing.h"
#include "ports.h"
#include "message.h"

#define BRL_HAVE_PACKET_IO

typedef enum {
  PARM_EMBEDDED,
  PARM_LATCH_DELAY,
  PARM_PROTOCOL
} DriverParameter;
#define BRLPARMS "embedded", "latchdelay", "protocol"

#include "brl_driver.h"
#include "brldefs-ir.h"

BEGIN_KEY_NAME_TABLE(common)
  KEY_NAME_ENTRY(IR_KEY_L1, "L1"),
  KEY_NAME_ENTRY(IR_KEY_L2, "L2"),
  KEY_NAME_ENTRY(IR_KEY_L3, "L3"),
  KEY_NAME_ENTRY(IR_KEY_L4, "L4"),
  KEY_NAME_ENTRY(IR_KEY_L5, "L5"),
  KEY_NAME_ENTRY(IR_KEY_L6, "L6"),
  KEY_NAME_ENTRY(IR_KEY_L7, "L7"),
  KEY_NAME_ENTRY(IR_KEY_L8, "L8"),

  KEY_NAME_ENTRY(IR_KEY_Menu, "Menu"),
  KEY_NAME_ENTRY(IR_KEY_Z, "Z"),

  KEY_GROUP_ENTRY(IR_GRP_RoutingKeys, "RoutingKey"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(brl)
  KEY_NAME_ENTRY(IR_KEY_Dot1, "Dot1"),
  KEY_NAME_ENTRY(IR_KEY_Dot2, "Dot2"),
  KEY_NAME_ENTRY(IR_KEY_Dot3, "Dot3"),
  KEY_NAME_ENTRY(IR_KEY_Dot4, "Dot4"),
  KEY_NAME_ENTRY(IR_KEY_Dot5, "Dot5"),
  KEY_NAME_ENTRY(IR_KEY_Dot6, "Dot6"),
  KEY_NAME_ENTRY(IR_KEY_Dot7, "Dot7"),
  KEY_NAME_ENTRY(IR_KEY_Dot8, "Dot8"),
  KEY_NAME_ENTRY(IR_KEY_Backspace, "Backspace"),
  KEY_NAME_ENTRY(IR_KEY_Space, "Space"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(pc)
  KEY_GROUP_ENTRY(IR_GRP_Xt, "Xt"),
  KEY_GROUP_ENTRY(IR_GRP_XtE0, "XtE0"),
  KEY_GROUP_ENTRY(IR_GRP_XtE1, "XtE1"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLES(brl)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(brl),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(pc)
  KEY_NAME_TABLE(common),
  KEY_NAME_TABLE(pc),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(brl)
DEFINE_KEY_TABLE(pc)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(brl),
  &KEY_TABLE_DEFINITION(pc),
END_KEY_TABLE_LIST

#define IR_MAXIMUM_PACKET_SIZE 0X100

#define IR_INTERNAL_SPEED 9600
#define IR_EXTERNAL_SPEED_EUROBRAILLE 9600
#define IR_EXTERNAL_SPEED_NATIVE 57600

/* Input/output ports */
#define IR_PORT_BASE 0X340
#define IR_PORT_INPUT   (IR_PORT_BASE + 0)
#define IR_PORT_OUTPUT  (IR_PORT_BASE + 1)
#define IR_PORT_OUTPUT2 (IR_PORT_BASE + 2)

typedef struct {
  GioEndpoint *gioEndpoint;
  SerialParameters serialParameters;

  const char *name;
  int speed;

  int (*writeNativePacket) (
    BrailleDisplay *brl, GioEndpoint *endpoint,
    const unsigned char *packet, size_t size
  );

  void (*handleNativeAcknowledgement) (BrailleDisplay *brl);

  unsigned int state;
  unsigned int length; /* useful when reading Eurobraille packets */
  unsigned int escape;

  unsigned char *position;
  unsigned char packet[IR_MAXIMUM_PACKET_SIZE];
} Port;

typedef enum {
  IR_PROTOCOL_EUROBRAILLE,
  IR_PROTOCOL_NATIVE
} ProtocolIndex;

typedef struct {
  const char *protocolName;
  int externalSpeed;

  size_t (*readExternalPacket) (BrailleDisplay *brl, Port *port, void *packet, size_t size);
  unsigned forwardAcknowledgements:1;

  int (*forwardInternalPacket) (
    BrailleDisplay *brl,
    const unsigned char *packet, size_t size
  );

  void (*forwardExternalPacket) (
    BrailleDisplay *brl,
    const unsigned char *packet, size_t size,
    int forward
  );

  int (*beginForwarding) (BrailleDisplay *brl);
  int (*endForwarding) (BrailleDisplay *brl);

  ProtocolIndex next;
} ProtocolEntry;

static void setExternalProtocol (BrailleDisplay *brl, ProtocolIndex index);
#define IR_PROTOCOL_DEFAULT IR_PROTOCOL_EUROBRAILLE

typedef struct {
  unsigned char base;
  unsigned char composite;
} CompositeCharacterEntry;

static const CompositeCharacterEntry compositeCharacterTable_circumflex[] = {
  {.base=0X61, .composite=0XE2}, // aâ
  {.base=0X65, .composite=0XEA}, // eê
  {.base=0X69, .composite=0XEE}, // iî
  {.base=0X6F, .composite=0XF4}, // oô
  {.base=0X75, .composite=0XFB}, // uû

  {.base=0X41, .composite=0XC2}, // AÂ
  {.base=0X45, .composite=0XCA}, // EÊ
  {.base=0X49, .composite=0XCE}, // IÎ
  {.base=0X4F, .composite=0XD4}, // OÔ
  {.base=0X55, .composite=0XDB}, // UÛ

  {.base=0X00, .composite=0XA8}
};

static const CompositeCharacterEntry compositeCharacterTable_trema[] = {
  {.base=0X61, .composite=0XE4}, // aä
  {.base=0X65, .composite=0XEB}, // eë
  {.base=0X69, .composite=0XEF}, // iï
  {.base=0X6F, .composite=0XF6}, // oö
  {.base=0X75, .composite=0XFC}, // uü

  {.base=0X41, .composite=0XC4}, // AÄ
  {.base=0X45, .composite=0XCB}, // EË
  {.base=0X49, .composite=0XCF}, // IÏ
  {.base=0X4F, .composite=0XD6}, // OÖ
  {.base=0X55, .composite=0XDC}, // UÜ

  {.base=0X00, .composite=0X5E}
};

static const CompositeCharacterEntry *compositeCharacterTables[] = {
  compositeCharacterTable_circumflex,
  compositeCharacterTable_trema
};

typedef enum {
  xtsLeftShiftPressed,
  xtsRightShiftPressed,
  xtsShiftLocked,

  xtsLeftControlPressed,
  xtsRightControlPressed,

  xtsLeftAltPressed,
  xtsRightAltPressed,

  xtsLeftWindowsPressed,
  xtsRightWindowsPressed,

  xtsInsertPressed,
  xtsFnPressed
} XtState;

#define XTS_BIT(number) (1 << (number))
#define XTS_TEST(bits) (brl->data->xt.state & (bits))
#define XTS_SHIFT XTS_TEST(XTS_BIT(xtsLeftShiftPressed) | XTS_BIT(xtsRightShiftPressed) | XTS_BIT(xtsShiftLocked))
#define XTS_CONTROL XTS_TEST(XTS_BIT(xtsLeftControlPressed) | XTS_BIT(xtsRightControlPressed))
#define XTS_ALT XTS_TEST(XTS_BIT(xtsLeftAltPressed))
#define XTS_ALTGR XTS_TEST(XTS_BIT(xtsRightAltPressed))
#define XTS_WIN XTS_TEST(XTS_BIT(xtsLeftWindowsPressed))
#define XTS_INSERT XTS_TEST(XTS_BIT(xtsInsertPressed))
#define XTS_FN XTS_TEST(XTS_BIT(xtsFnPressed))

typedef enum {
  XtKeyType_ignore = 0, /* required for uninitialized entries */
  XtKeyType_modifier,
  XtKeyType_lock,
  XtKeyType_character,
  XtKeyType_function,
  XtKeyType_complex,
  XtKeyType_composite
} XtKeyType;

typedef struct {
  unsigned char type;
  unsigned char arg1;
  unsigned char arg2;
  unsigned char arg3;
} XtKeyEntry;

typedef enum {
  XT_KEYS_00,
  XT_KEYS_E0,
  XT_KEYS_E1
} XT_KEY_SET;

#define XT_RELEASE 0X80
#define XT_KEY(set,key) ((XT_KEYS_##set << 7) | (key))

static const XtKeyEntry xtKeyTable[] = {
  /* row 1 */
  [XT_KEY(00,0X01)] = { // key 1: escape
    .type = XtKeyType_function,
    .arg1=0X1B
  }
  ,
  [XT_KEY(00,0X3B)] = { // key 2: F1
    .type = XtKeyType_function,
    .arg1=0X70
  }
  ,
  [XT_KEY(00,0X3C)] = { // key 3: F2
    .type = XtKeyType_function,
    .arg1=0X71
  }
  ,
  [XT_KEY(00,0X3D)] = { // key 4: F3
    .type = XtKeyType_function,
    .arg1=0X72
  }
  ,
  [XT_KEY(00,0X3E)] = { // key 5: F4
    .type = XtKeyType_function,
    .arg1=0X73
  }
  ,
  [XT_KEY(00,0X3F)] = { // key 6: F5
    .type = XtKeyType_function,
    .arg1=0X74
  }
  ,
  [XT_KEY(00,0X40)] = { // key 7: F6
    .type = XtKeyType_function,
    .arg1=0X75
  }
  ,
  [XT_KEY(00,0X41)] = { // key 8: F7
    .type = XtKeyType_function,
    .arg1=0X76
  }
  ,
  [XT_KEY(00,0X42)] = { // key 9: F8
    .type = XtKeyType_function,
    .arg1=0X77
  }
  ,
  [XT_KEY(00,0X43)] = { // key 10: F9
    .type = XtKeyType_function,
    .arg1=0X78
  }
  ,
  [XT_KEY(00,0X44)] = { // key 11: F10
    .type = XtKeyType_function,
    .arg1=0X79
  }
  ,
  [XT_KEY(00,0X57)] = { // key 12: F11
    .type = XtKeyType_function,
    .arg1=0X7A
  }
  ,
  [XT_KEY(00,0X58)] = { // key 13: F12
    .type = XtKeyType_function,
    .arg1=0X7B
  }
  ,
  [XT_KEY(00,0X46)] = { // key 14: scroll lock
    .type = XtKeyType_ignore
  }
  ,
  [XT_KEY(E1,0X1D)] = { // key 15: pause break
    .type = XtKeyType_ignore
  }
  ,
  [XT_KEY(E0,0X52)] = { // key 16: insert
    .type = XtKeyType_complex,
    .arg1=0X0F, .arg2=1, .arg3=xtsInsertPressed
  }
  ,
  [XT_KEY(E0,0X53)] = { // key 17: delete
    .type = XtKeyType_function,
    .arg1=0X10, .arg2=1
  }
  ,

  /* row 2 */
  [XT_KEY(00,0X02)] = { // key 1: &1
    .type = XtKeyType_character,
    .arg1=0X26, .arg2=0X31
  }
  ,
  [XT_KEY(00,0X03)] = { // key 2: é2~
    .type = XtKeyType_character,
    .arg1=0XE9, .arg2=0X32, .arg3=0X7E
  }
  ,
  [XT_KEY(00,0X04)] = { // key 3: "3#
    .type = XtKeyType_character,
    .arg1=0X22, .arg2=0X33, .arg3=0X23
  }
  ,
  [XT_KEY(00,0X05)] = { // key 4: '4{
    .type = XtKeyType_character,
    .arg1=0X27, .arg2=0X34, .arg3=0X7B
  }
  ,
  [XT_KEY(00,0X06)] = { // key 5: (5[
    .type = XtKeyType_character,
    .arg1=0X28, .arg2=0X35, .arg3=0X5B
  }
  ,
  [XT_KEY(00,0X07)] = { // key 6: -6|
    .type = XtKeyType_character,
    .arg1=0X2D, .arg2=0X36, .arg3=0X7C
  }
  ,
  [XT_KEY(00,0X08)] = { // key 7: è7`
    .type = XtKeyType_character,
    .arg1=0XE8, .arg2=0X37, .arg3=0X60
  }
  ,
  [XT_KEY(00,0X09)] = { // key 8: _8
    .type = XtKeyType_character,
    .arg1=0X5F, .arg2=0X38, .arg3=0X5C
  }
  ,
  [XT_KEY(00,0X0A)] = { // key 9: ç9^
    .type = XtKeyType_character,
    .arg1=0XE7, .arg2=0X39, .arg3=0X5E
  }
  ,
  [XT_KEY(00,0X0B)] = { // key 10: à0@
    .type = XtKeyType_character,
    .arg1=0XE0, .arg2=0X30, .arg3=0X40
  }
  ,
  [XT_KEY(00,0X0C)] = { // key 11: )°]
    .type = XtKeyType_character,
    .arg1=0X29, .arg2=0XB0, .arg3=0X5D
  }
  ,
  [XT_KEY(00,0X0D)] = { // key 12: =+}
    .type = XtKeyType_character,
    .arg1=0X3D, .arg2=0X2B, .arg3=0X7D
  }
  ,
  [XT_KEY(00,0X29)] = { // key 13: ²
    .type = XtKeyType_character,
    .arg1=0XB2
  }
  ,
  [XT_KEY(00,0X0E)] = { // key 14: backspace
    .type = XtKeyType_function,
    .arg1=0X08
  }
  ,

  /* row 3 */
  [XT_KEY(00,0X0F)] = { // key 1: tab
    .type = XtKeyType_function,
    .arg1=0X09
  }
  ,
  [XT_KEY(00,0X10)] = { // key 2: aA
    .type = XtKeyType_character,
    .arg1=0X61, .arg2=0X41
  }
  ,
  [XT_KEY(00,0X11)] = { // key 3: zZ
    .type = XtKeyType_character,
    .arg1=0X7A, .arg2=0X5A
  }
  ,
  [XT_KEY(00,0X12)] = { // key 4: eE
    .type = XtKeyType_character,
    .arg1=0X65, .arg2=0X45, .arg3=0X80
  }
  ,
  [XT_KEY(00,0X13)] = { // key 5: rR®
    .type = XtKeyType_character,
    .arg1=0X72, .arg2=0X52, .arg3=0XAE
  }
  ,
  [XT_KEY(00,0X14)] = { // key 6: tT
    .type = XtKeyType_character,
    .arg1=0X74, .arg2=0X54, .arg3=0X99
  }
  ,
  [XT_KEY(00,0X15)] = { // key 7: yY
    .type = XtKeyType_character,
    .arg1=0X79, .arg2=0X59
  }
  ,
  [XT_KEY(00,0X16)] = { // key 8: uU
    .type = XtKeyType_character,
    .arg1=0X75, .arg2=0X55
  }
  ,
  [XT_KEY(00,0X17)] = { // key 9: iI
    .type = XtKeyType_character,
    .arg1=0X69, .arg2=0X49
  }
  ,
  [XT_KEY(00,0X18)] = { // key 10: oO
    .type = XtKeyType_character,
    .arg1=0X6F, .arg2=0X4F
  }
  ,
  [XT_KEY(00,0X19)] = { // key 11: pP
    .type = XtKeyType_character,
    .arg1=0X70, .arg2=0X50
  }
  ,
  [XT_KEY(00,0X1A)] = { // key 12: circumflex tréma
    .type = XtKeyType_composite,
    .arg1=1, .arg2=2
  }
  ,
  [XT_KEY(00,0X1B)] = { // key 13: $£¤
    .type = XtKeyType_character,
    .arg1=0X24, .arg2=0XA3, .arg3=0XA4
  }
  ,
  [XT_KEY(00,0X1C)] = { // key 14: return
    .type = XtKeyType_function,
    .arg1=0X0D
  }
  ,

  /* row 4 */
  [XT_KEY(00,0X3A)] = { // key 1: shift lock
    .type = XtKeyType_lock,
    .arg1=xtsShiftLocked
  }
  ,
  [XT_KEY(00,0X1E)] = { // key 2: qQ
    .type = XtKeyType_character,
    .arg1=0X71, .arg2=0X51
  }
  ,
  [XT_KEY(00,0X1F)] = { // key 3: sS
    .type = XtKeyType_character,
    .arg1=0X73, .arg2=0X53
  }
  ,
  [XT_KEY(00,0X20)] = { // key 4: dD
    .type = XtKeyType_character,
    .arg1=0X64, .arg2=0X44
  }
  ,
  [XT_KEY(00,0X21)] = { // key 5: fF
    .type = XtKeyType_character,
    .arg1=0X66, .arg2=0X46
  }
  ,
  [XT_KEY(00,0X22)] = { // key 6: gG
    .type = XtKeyType_character,
    .arg1=0X67, .arg2=0X47
  }
  ,
  [XT_KEY(00,0X23)] = { // key 7: hH
    .type = XtKeyType_character,
    .arg1=0X68, .arg2=0X48
  }
  ,
  [XT_KEY(00,0X24)] = { // key 8: jJ
    .type = XtKeyType_character,
    .arg1=0X6A, .arg2=0X4A
  }
  ,
  [XT_KEY(00,0X25)] = { // key 9: kK
    .type = XtKeyType_character,
    .arg1=0X6B, .arg2=0X4B
  }
  ,
  [XT_KEY(00,0X26)] = { // key 10: lL
    .type = XtKeyType_character,
    .arg1=0X6C, .arg2=0X4C
  }
  ,
  [XT_KEY(00,0X27)] = { // key 11: mM
    .type = XtKeyType_character,
    .arg1=0X6D, .arg2=0X4D
  }
  ,
  [XT_KEY(00,0X28)] = { // key 12: ù%
    .type = XtKeyType_character,
    .arg1=0XF9, .arg2=0X25
  }
  ,
  [XT_KEY(00,0X2B)] = { // key 13: *µ
    .type = XtKeyType_character,
    .arg1=0X2A, .arg2=0XB5
  }
  ,
  [XT_KEY(00,0X1C)] = { // key 14: return
    .type = XtKeyType_function,
    .arg1=0X0D
  }
  ,

  /* row 5 */
  [XT_KEY(00,0X2A)] = { // key 1: left shift
    .type = XtKeyType_modifier,
    .arg1=xtsLeftShiftPressed, .arg2=xtsShiftLocked
  }
  ,
  [XT_KEY(00,0X2C)] = { // key 2: wW
    .type = XtKeyType_character,
    .arg1=0X77, .arg2=0X57
  }
  ,
  [XT_KEY(00,0X2D)] = { // key 3: xX
    .type = XtKeyType_character,
    .arg1=0X78, .arg2=0X58
  }
  ,
  [XT_KEY(00,0X2E)] = { // key 4: cC©
    .type = XtKeyType_character,
    .arg1=0X63, .arg2=0X43, .arg3=0XA9
  }
  ,
  [XT_KEY(00,0X2F)] = { // key 5: vV
    .type = XtKeyType_character,
    .arg1=0X76, .arg2=0X56
  }
  ,
  [XT_KEY(00,0X30)] = { // key 6: bB
    .type = XtKeyType_character,
    .arg1=0X62, .arg2=0X42
  }
  ,
  [XT_KEY(00,0X31)] = { // key 7: nN
    .type = XtKeyType_character,
    .arg1=0X6E, .arg2=0X4E
  }
  ,
  [XT_KEY(00,0X32)] = { // key 8: ,?
    .type = XtKeyType_character,
    .arg1=0X2C, .arg2=0X3F
  }
  ,
  [XT_KEY(00,0X33)] = { // key 9: ;.
    .type = XtKeyType_character,
    .arg1=0X3B, .arg2=0X2E
  }
  ,
  [XT_KEY(00,0X34)] = { // key 10: :/
    .type = XtKeyType_character,
    .arg1=0X3A, .arg2=0X2F
  }
  ,
  [XT_KEY(00,0X35)] = { // key 11: !§
    .type = XtKeyType_character,
    .arg1=0X21, .arg2=0XA7
  }
  ,
  [XT_KEY(00,0X56)] = { // key 12: <>
    .type = XtKeyType_character,
    .arg1=0X3C, .arg2=0X3E
  }
  ,
  [XT_KEY(00,0X36)] = { // key 13: right shift
    .type = XtKeyType_modifier,
    .arg1=xtsRightShiftPressed, .arg2=xtsShiftLocked
  }
  ,

  /* row 6 */
  [XT_KEY(00,0X1D)] = { // key 1: left control
    .type = XtKeyType_modifier,
    .arg1=xtsLeftControlPressed
  }
  ,
  [XT_KEY(E1,0X01)] = { // key 2: fn
    .type = XtKeyType_modifier,
    .arg1=xtsFnPressed
  }
  ,
  [XT_KEY(E0,0X5B)] = { // key 3: left windows
    .type = XtKeyType_complex,
    .arg1=0X5B, .arg3=xtsLeftWindowsPressed
  }
  ,
  [XT_KEY(00,0X38)] = { // key 4: left alt
    .type = XtKeyType_modifier,
    .arg1=xtsLeftAltPressed
  }
  ,
  [XT_KEY(00,0X39)] = { // key 5: space
    .type = XtKeyType_function,
    .arg1=0X20
  }
  ,
  [XT_KEY(E0,0X38)] = { // key 6: right alt
    .type = XtKeyType_modifier,
    .arg1=xtsRightAltPressed
  }
  ,
  [XT_KEY(E0,0X5D)] = { // key 7: right windows
    .type = XtKeyType_function,
    .arg1=0X5D
  }
  ,
  [XT_KEY(E0,0X1D)] = { // key 8: right control
    .type = XtKeyType_modifier,
    .arg1=xtsRightControlPressed
  }
  ,

  /* arrow keys */
  [XT_KEY(E0,0X48)] = { // key 1: up arrow
    .type = XtKeyType_function,
    .arg1=0X0D, .arg2=1
  }
  ,
  [XT_KEY(E0,0X4B)] = { // key 2: left arrow
    .type = XtKeyType_function,
    .arg1=0X0B, .arg2=1
  }
  ,
  [XT_KEY(E0,0X50)] = { // key 3: down arrow
    .type = XtKeyType_function,
    .arg1=0X0E, .arg2=1
  }
  ,
  [XT_KEY(E0,0X4D)] = { // key 4: right arrow
    .type = XtKeyType_function,
    .arg1=0X0C, .arg2=1
  }
  ,
  [XT_KEY(E0,0X49)] = { // fn + key 1: page up
    .type = XtKeyType_function,
    .arg1=0X09, .arg2=1
  }
  ,
  [XT_KEY(E0,0X47)] = { // fn + key 2: home
    .type = XtKeyType_function,
    .arg1=0X07, .arg2=1
  }
  ,
  [XT_KEY(E0,0X51)] = { // fn + key 3: page down
    .type = XtKeyType_function,
    .arg1=0X0A, .arg2=1
  }
  ,
  [XT_KEY(E0,0X4F)] = { // fn + key 4: end
    .type = XtKeyType_function,
    .arg1=0X08, .arg2=1
  }
};

struct BrailleDataStruct {
  unsigned isConnected:1;

  unsigned isEmbedded:1;
  unsigned isSuspended:1;
  unsigned isForwarding:1;

  unsigned haveVisualDisplay:1;

  struct {
    Port port;
    int (*handlePacket) (BrailleDisplay *brl, const void *packet, size_t size);
    int (*isOffline) (BrailleDisplay *brl);
    KeyNumberSet linearKeys;
  } internal;

  struct {
    Port port;
    GioHandleInputObject *hio;
    const ProtocolEntry *protocol;
    unsigned char cells[0XFF];
  } external;

  struct {
    AsyncHandle monitor;

    int delay;
    int interval;

    TimeValue started;
    long int elapsed;
    unsigned pulled:1;
  } latch;

  struct {
    unsigned char refresh;
    unsigned char cells[0XFF];
  } braille;

  struct {
    const CompositeCharacterEntry *composite;
    const XtKeyEntry *key;
    uint16_t state;
  } xt;

  unsigned char *firmwareVersion;
  char serialNumber[5];
};

/* Function readNativePacket */
/* Returns the size of the read packet. */
/* 0 means no packet has been read and there is no error. */
/* -1 means an error occurred */
static size_t
readNativePacket (BrailleDisplay *brl, Port *port, void *packet, size_t size) {
  unsigned char byte;
  int wait = 0;

  while (gioReadByte(port->gioEndpoint, &byte, (port->state && wait))) {
    size_t length = port->position - port->packet;

    wait = 1;

    if (port->state) {
      switch (byte) {
        case ASCII_DLE:
          if (!port->escape) {
            port->escape = 1;
            continue;
          }

        case ASCII_EOT:
          if (!port->escape) {
            port->state = 0;

            if (length <= size) {
              memcpy(packet, port->packet, length);
              logInputPacket(packet, length);
              return length;
            }

            logInputProblem("packet buffer too small", port->packet, length);
            break;
          }

        default:
          if (length < sizeof(port->packet)) {
            *port->position = byte;
          } else {
            if (length == sizeof(port->packet)) logTruncatedPacket(port->packet, length);
            logDiscardedByte(byte);
          }

          port->position += 1;
          port->escape = 0;
          break;
      }
    } else if (byte == ASCII_SOH) {
      port->state = 1;
      port->escape = 0;
      port->position = port->packet;
    } else if (byte == ASCII_ACK) {
      port->handleNativeAcknowledgement(brl);
    } else {
      logIgnoredByte(byte);
    }
  }

  if (errno != EAGAIN) logSystemError("readNativePacket");
  return 0;
}

static size_t
readEurobraillePacket (BrailleDisplay *brl, Port *port, void *packet, size_t size) {
  unsigned char byte;
  int wait = 0;

  while (gioReadByte(port->gioEndpoint, &byte, (port->state && wait))) {
    wait = 1;

    switch (port->state) {
      case 0:
        if (byte == ASCII_STX) {
          port->state = 1;
          port->position = port->packet;
          port->length = 0;
        } else {
          logIgnoredByte(byte);
        }
        break;

      case 1:
        port->length |= byte << 8;
        port->state = 2;
        break;

      case 2:
        port->length |= byte;

        if (port->length < 3) {
          logMessage(LOG_WARNING, "invalid Eurobraille packet declared size: %d", port->length);
          port->state = 0;
        } else {
          port->length -= 2;

          if (port->length > sizeof(port->packet)) {
            logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "readEuroBraillePacket: rejecting packet whose declared size is too large");
            port->state = 0;
          } else {
            port->state = 3;
          }
        }
        break;

      case 3:
        *port->position++ = byte;

        if ((port->position - port->packet) == port->length) port->state = 4;
        break;

      case 4:
        if (byte == ASCII_ETX) {
          size_t length = port->position - port->packet;

          port->state = 0;

          if (length <= size) {
            memcpy(packet, port->packet, length);
            logInputPacket(packet, length);
            return length;
          }

          logInputProblem("packet buffer too small", port->packet, length);
        } else {
          logMessage(LOG_WARNING, "Eurobraille packet with real size exceeding declared size");
          logDiscardedByte(byte);
          port->state = 5;
        }
        break;

      case 5:
        if (byte == ASCII_ETX) {
          port->state = 0;
        } else {
          logDiscardedByte(byte);
        }
        break;

      default:
        logMessage(LOG_WARNING, "readEurobraillePacket: reached unknown state %d", port->state);
        port->state = 0;
        break;
    }
  }

  return 0;
}

static inline int
needsEscape (unsigned char byte) {
  static const unsigned char escapedChars[0X20] = {
    [ASCII_SOH] = 1, [ASCII_EOT] = 1, [ASCII_DLE] = 1,
    [ASCII_ACK] = 1, [ASCII_NAK] = 1,
  };

  if (byte < sizeof(escapedChars)) return escapedChars[byte];
  return 0;
}

static int
writeNativePacket_internal (
  BrailleDisplay *brl, GioEndpoint *endpoint,
  const unsigned char *packet, size_t size
) {
  return writeBrailleMessage(brl, endpoint, 0, packet, size);
}

static int
writeNativePacket_external (
  BrailleDisplay *brl, GioEndpoint *endpoint,
  const unsigned char *packet, size_t size
) {
  return writeBraillePacket(brl, endpoint, packet, size);
}

static void
handleNativeAcknowledgement_internal (BrailleDisplay *brl) {
  acknowledgeBrailleMessage(brl);

  if (brl->data->isForwarding && brl->data->external.protocol->forwardAcknowledgements) {
    static const unsigned char acknowledgement[] = {ASCII_ACK};

    writeBraillePacket(brl, brl->data->external.port.gioEndpoint,
                       acknowledgement, sizeof(acknowledgement));
  }
}

static void
handleNativeAcknowledgement_external (BrailleDisplay *brl) {
}

static size_t
writeNativePacket (
  BrailleDisplay *brl, Port *port,
  const unsigned char *packet, size_t size
) {
  unsigned char	buffer[(size * 2) + 2];
  size_t count;

  {
    const unsigned char *source = packet;
    unsigned char *target = buffer;

    *target++ = ASCII_SOH;

    while (size--) {
      if (needsEscape(*source)) *target++ = ASCII_DLE;
      *target++ = *source++;
    }

    *target++ = ASCII_EOT;
    count = target - buffer;
  }

  if (!port->writeNativePacket(brl, port->gioEndpoint, buffer, count)) return 0;
  return count;
}

/*
static ssize_t
tryWriteNativePacket (BrailleDisplay *brl, Port *port, const void *packet, size_t size) {
  ssize_t res;
  while ( ! (res = writeNativePacket(brl, port, packet, size)) ) {
    if (errno != EAGAIN) return 0;
  }
  return res;
}
*/

static int
writeEurobraillePacket (BrailleDisplay *brl, Port *port, const void *data, size_t size) {
  size_t count;
  size_t packetSize = size + 2;
  unsigned char	packet[packetSize + 2];
  unsigned char *p = packet;

  *p++ = ASCII_STX;
  *p++ = (packetSize >> 8) & 0X00FF;
  *p++ = packetSize & 0X00FF;  
  p = mempcpy(p, data, size);
  *p++ = ASCII_ETX;

  count = p - packet;
  if (!writeBraillePacket(brl, port->gioEndpoint, packet, count)) return 0;
  return count;
}

static int
writeEurobrailleStringPacket (BrailleDisplay *brl, Port *port, const char *string) {
  return writeEurobraillePacket(brl, port, string, strlen(string) + 1);
}

/* Low-level write of dots to the braile display */
/* No check is performed to avoid several consecutive identical writes at this level */
static size_t
writeDots (BrailleDisplay *brl, Port *port, const unsigned char *dots) {
  size_t size = brl->textColumns * brl->textRows;
  unsigned char packet[IR_WINDOW_SIZE_MAXIMUM + 1];
  unsigned char *p = packet;
  int i;

  *p++ = IR_OPT_WriteBraille;
  for (i=0; i<IR_WINDOW_SIZE_MAXIMUM-size; i+=1) *p++ = 0; 
  for (i=0; i<size; i+=1) *p++ = dots[size-i-1];
  return writeNativePacket(brl, port, packet, sizeof(packet));
}

/* Low-level write of text to the braile display */
/* No check is performed to avoid several consecutive identical writes at this level */
static size_t
writeWindow (BrailleDisplay *brl, const unsigned char *text) {
  size_t size = brl->textColumns * brl->textRows;
  unsigned char dots[size];

  translateOutputCells(dots, text, size);
  return writeDots(brl, &brl->data->internal.port, dots);
}

static size_t
clearWindow (BrailleDisplay *brl) {
  size_t size = brl->textColumns * brl->textRows;
  unsigned char window[size];

  memset(window, 0, sizeof(window));
  return writeWindow(brl, window);
}

static void
activateBraille(void) {
  writePort1(IR_PORT_OUTPUT, 0X01);
  asyncWait(9);
  writePort1(IR_PORT_OUTPUT, 0X00);
}

static void
deactivateBraille(void) {
  writePort1(IR_PORT_OUTPUT, 0X02);
  asyncWait(9);
  writePort1(IR_PORT_OUTPUT, 0X00);
}

static ssize_t brl_readPacket (BrailleDisplay *brl, void *packet, size_t size)
{
  if (brl->data->isEmbedded && (brl->data->isSuspended || brl->data->isForwarding)) return 0;
  return readNativePacket(brl, &brl->data->internal.port, packet, size);
}

/* Function brl_writePacket */
/* Returns 1 if the packet is actually written, 0 if the packet is not written */
static ssize_t brl_writePacket (BrailleDisplay *brl, const void *packet, size_t size)
{
  if (brl->data->isSuspended || brl->data->isForwarding) {
    errno = EAGAIN;
    return 0;
  }
  return writeNativePacket(brl, &brl->data->internal.port, packet, size);
}

static int brl_reset (BrailleDisplay *brl)
{
  return 0;
}

static int
sendInteractiveKey (BrailleDisplay *brl, Port *port, unsigned char key) {
  const unsigned char packet[] = {IR_IPT_InteractiveKey, key};

  return writeNativePacket(brl, port, packet, sizeof(packet));
}

static int
sendMenuKey (BrailleDisplay *brl, Port *port) {
  return sendInteractiveKey(brl, port, 'Q');
}

typedef struct {
  int (*handleZKey) (BrailleDisplay *brl, Port *port);
  int (*handleRoutingKey) (BrailleDisplay *brl, Port *port, unsigned char key);
  int (*handlePCKey) (BrailleDisplay *brl, Port *port, int repeat, unsigned char escape, unsigned char key);
  int (*handleFunctionKeys) (BrailleDisplay *brl, Port *port, KeyNumberSet keys);
  int (*handleBrailleKeys) (BrailleDisplay *brl, Port *port, KeyNumberSet keys);
} KeyHandlers;

static int
null_handleZKey(BrailleDisplay *brl, Port *port) {
  logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "ignoring Z key");
  return 1;
}

static int
core_handleZKey(BrailleDisplay *brl, Port *port) {
  logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "Z key pressed");
  setExternalProtocol(brl, brl->data->external.protocol->next);

  {
    Port *port = &brl->data->external.port;

    port->speed = brl->data->external.protocol->externalSpeed;
    port->serialParameters.baud = port->speed;
    if (!gioReconfigureResource(port->gioEndpoint, &port->serialParameters)) return 0;
  }

  return 1;
}

static int
core_handleRoutingKey(BrailleDisplay *brl, Port *port, unsigned char key) {
  return enqueueKey(brl, IR_GRP_RoutingKeys, key-1);
}

static int
core_handlePCKey(BrailleDisplay *brl, Port *port, int repeat, unsigned char escape, unsigned char code) {
  return enqueueXtScanCode(brl, code, escape, IR_GRP_Xt, IR_GRP_XtE0, IR_GRP_XtE1);
}

static int
core_handleFunctionKeys(BrailleDisplay *brl, Port *port, KeyNumberSet keys) {
  return enqueueUpdatedKeys(brl, keys, &brl->data->internal.linearKeys, IR_GRP_NavigationKeys, IR_KEY_L1);
}

static int
core_handleBrailleKeys(BrailleDisplay *brl, Port *port, KeyNumberSet keys) {
  return enqueueKeys(brl, keys, IR_GRP_NavigationKeys, IR_KEY_Dot1);
}

static const KeyHandlers keyHandlers_embedded = {
  .handleZKey = core_handleZKey,
  .handleRoutingKey = core_handleRoutingKey,
  .handlePCKey = core_handlePCKey,
  .handleFunctionKeys = core_handleFunctionKeys,
  .handleBrailleKeys = core_handleBrailleKeys
};

static const KeyHandlers keyHandlers_nonembedded = {
  .handleZKey = null_handleZKey,
  .handleRoutingKey = core_handleRoutingKey,
  .handlePCKey = core_handlePCKey,
  .handleFunctionKeys = core_handleFunctionKeys,
  .handleBrailleKeys = core_handleBrailleKeys
};

static int
eurobrl_handleRoutingKey(BrailleDisplay *brl, Port *port, unsigned char key) {
  unsigned char data[] = {
    0X4B, 0X49, 1, key
  };
  return writeEurobraillePacket(brl, port, data, sizeof(data));
}

static int
eurobrl_handlePCKey(BrailleDisplay *brl, Port *port, int repeat, unsigned char escape, unsigned char key) {
  unsigned char data[] = {0X4B, 0X5A, 0, 0, 0, 0};
  const XtKeyEntry *xke = &xtKeyTable[key & ~XT_RELEASE];

  switch (escape) {
    case 0XE0:
      xke += XT_KEY(E0, 0);
      break;

    case 0XE1:
      xke += XT_KEY(E1, 0);
      break;

    default:
    case 0X00:
      xke += XT_KEY(00, 0);
      break;
  }

  if (xke >= (xtKeyTable + ARRAY_COUNT(xtKeyTable))) {
    static const XtKeyEntry xtKeyEntry = {
      .type = XtKeyType_ignore
    };

    xke = &xtKeyEntry;
  }

  if (key & XT_RELEASE) {
    int current = xke == brl->data->xt.key;
    brl->data->xt.key = NULL;

    switch (xke->type) {
      case XtKeyType_modifier:
        brl->data->xt.state &= ~XTS_BIT(xke->arg1);
        return 1;

      case XtKeyType_complex:
        brl->data->xt.state &= ~XTS_BIT(xke->arg3);
        if (current) goto isFunction;
        return 1;

      default:
        return 1;
    }
  } else {
    brl->data->xt.key = xke;

    switch (xke->type) {
      case XtKeyType_modifier:
        brl->data->xt.state |= XTS_BIT(xke->arg1);
        brl->data->xt.state &= ~XTS_BIT(xke->arg2);
        return 1;

      case XtKeyType_complex:
        brl->data->xt.state |= XTS_BIT(xke->arg3);
        return 1;

      case XtKeyType_lock:
        brl->data->xt.state |= XTS_BIT(xke->arg1);
        return 1;

      case XtKeyType_character:
        if (xke->arg3 && XTS_ALTGR) {
          data[5] = xke->arg3;
        } else if (xke->arg2 && XTS_SHIFT) {
          data[5] = xke->arg2;
        } else {
          data[5] = xke->arg1;
        }
        break;

      case XtKeyType_function:
      isFunction:
        data[3] = xke->arg1;
        data[2] = xke->arg2;
        break;

      case XtKeyType_composite: {
        unsigned char index;

        if (xke->arg2 && XTS_SHIFT) {
          index = xke->arg2;
        } else {
          index = xke->arg1;
        }

        if (index) brl->data->xt.composite = compositeCharacterTables[index - 1];
        return 1;
      }

      default:
        return 1;
    }
  }

  if (XTS_TEST(XTS_BIT(xtsLeftShiftPressed) | XTS_BIT(xtsRightShiftPressed))) data[4] |= 0X01;
  if (XTS_CONTROL) data[4] |= 0X02;
  if (XTS_ALT) data[4] |= 0X04;
  if (XTS_TEST(XTS_BIT(xtsShiftLocked))) data[4] |= 0X08;
  if (XTS_WIN) data[4] |= 0X10;
  if (XTS_ALTGR) data[4] |= 0X20;
  if (XTS_INSERT) data[4] |= 0X80;

  if (brl->data->xt.composite) {
    unsigned char *byte = &data[5];

    if (*byte) {
      const CompositeCharacterEntry *cce = brl->data->xt.composite;

      while (cce->base) {
        if (cce->base == *byte) {
          *byte = cce->composite;
          break;
        }

        cce += 1;
      }

      if (!cce->base && cce->composite) {
        unsigned char original = *byte;
        *byte = cce->composite;
        if (!writeEurobraillePacket(brl, port, data, sizeof(data))) return 0;
        *byte = original;
      }
    }

    brl->data->xt.composite = NULL;
  }

  return writeEurobraillePacket(brl, port, data, sizeof(data));
}

static int
eurobrl_handleFunctionKeys(BrailleDisplay *brl, Port *port, KeyNumberSet keys) {
  if (keys) {
    unsigned char data[] = {
      0X4B, 0X43, 0, (
        (keys & 0XF) |
        ((keys >> 1) & 0XF0)
      )
    };

    if (!writeEurobraillePacket(brl, port, data, sizeof(data))) return 0;
  }

  return 1;
}

static int
eurobrl_handleBrailleKeys(BrailleDisplay *brl, Port *port, KeyNumberSet keys) {
  unsigned char data[] = {
    0X4B, 0X42,
    (keys >> 8) & 0XFF,
    keys & 0XFF
  };

  return writeEurobraillePacket(brl, port, data, sizeof(data));
}

static const KeyHandlers keyHandlers_eurobraille = {
  .handleZKey = null_handleZKey,
  .handleRoutingKey = eurobrl_handleRoutingKey,
  .handlePCKey = eurobrl_handlePCKey,
  .handleFunctionKeys = eurobrl_handleFunctionKeys,
  .handleBrailleKeys = eurobrl_handleBrailleKeys
};

static int
writeExternalCells (BrailleDisplay *brl) {
  return writeDots(brl, &brl->data->internal.port, brl->data->external.cells);
}

static void
saveExternalCells (BrailleDisplay *brl, const unsigned char *cells) {
  memcpy(brl->data->external.cells, cells, brl->textColumns);
}

static int
handleNativePacket (BrailleDisplay *brl, Port *port, const KeyHandlers *keyHandlers, const unsigned char *packet, size_t size) {
  if (size == 2) {
    if (packet[0] == IR_IPT_InteractiveKey) {
      if (packet[1] == 'W') {
        return keyHandlers->handleZKey(brl, port);
      }

      if ((1 <= packet[1]) && (packet[1] <= (brl->textColumns * brl->textRows))) {
        return keyHandlers->handleRoutingKey(brl, port, packet[1]);
      }
    }
  } else if (size == 3) {
    int repeat = (packet[0] == IR_IPT_XtKeyCodeRepeat);

    if ((packet[0] == IR_IPT_XtKeyCode) || repeat) {
      return keyHandlers->handlePCKey(brl, port, repeat, packet[1], packet[2]);
    }

    if (packet[0] == IR_IPT_LinearKeys) {
      KeyNumberSet keys = (packet[1] << 8) | packet[2];

      return keyHandlers->handleFunctionKeys(brl, port, keys);
    }

    if (packet[0] == IR_IPT_BrailleKeys) {
      KeyNumberSet keys = (packet[1] << 8) | packet[2];

      return keyHandlers->handleBrailleKeys(brl, port, keys);
    }
  }

  logUnexpectedPacket(packet, size);
  return 0;
}

static int
forwardInternalPacket_native (
  BrailleDisplay *brl,
  const unsigned char *packet, size_t size
) {
  return writeNativePacket(brl, &brl->data->external.port, packet, size);
}

static int
forwardInternalPacket_eurobraille (
  BrailleDisplay *brl,
  const unsigned char *packet, size_t size
) {
  handleNativePacket(brl, &brl->data->external.port, &keyHandlers_eurobraille, packet, size);
  return 1;
}

static void
forwardExternalPacket_native (
  BrailleDisplay *brl,
  const unsigned char *packet, size_t size,
  int forward
) {
  if (forward) {
    writeNativePacket(brl, &brl->data->internal.port, packet, size);
  }
}

static void
forwardExternalPacket_eurobraille (
  BrailleDisplay *brl,
  const unsigned char *packet, size_t size,
  int forward
) {
  if (size==2 && packet[0]=='S' && packet[1]=='I') {
    /* Send system information */
    Port *port = &brl->data->external.port;
    char str[256];

    writeEurobrailleStringPacket(brl, port, "SNIRIS_KB_40");

    writeEurobrailleStringPacket(brl, port, "SHIR4");

    snprintf(str, sizeof(str), "SS%s", brl->data->serialNumber);
    writeEurobrailleStringPacket(brl, port, str);

    writeEurobrailleStringPacket(brl, port, "SLFR");

    str[0] = 'S';
    str[1] = 'G';
    str[2] = brl->textColumns;
    writeEurobraillePacket(brl, port, str, 3);

    str[0] = 'S';
    str[1] = 'T';
    str[2] = 6;
    writeEurobraillePacket(brl, port, str, 3);

    snprintf(str, sizeof(str), "So%d%da", 0XEF, 0XF8);
    writeEurobrailleStringPacket(brl, port, str);

    writeEurobrailleStringPacket(brl, port, "SW1.92");

    writeEurobrailleStringPacket(brl, port, "SP1.00 30-10-2006");

    snprintf(str, sizeof(str), "SM%d", 0X08);
    writeEurobrailleStringPacket(brl, port, str);

    writeEurobrailleStringPacket(brl, port, "SI");
  } else if (size==brl->textColumns+2 && packet[0]=='B' && packet[1]=='S') {
    /* Write dots to braille display */
    saveExternalCells(brl, packet+2);
    if (forward) writeExternalCells(brl);
  } else {
    logBytes(LOG_WARNING, "forwardEurobraillePacket could not handle this packet: ", packet, size);
  }
}

static int
beginForwarding_native (BrailleDisplay *brl) {
  return sendMenuKey(brl, &brl->data->external.port);
}

static int
endForwarding_native (BrailleDisplay *brl) {
  return sendMenuKey(brl, &brl->data->external.port);
}

static int
beginForwarding_eurobraille (BrailleDisplay *brl) {
  brl->data->xt.composite = NULL;
  brl->data->xt.key = NULL;
  brl->data->xt.state = 0;

  writeExternalCells(brl);
  return 1;
}

static int
endForwarding_eurobraille (BrailleDisplay *brl) {
  return 1;
}

static const ProtocolEntry protocolTable[] = {
  [IR_PROTOCOL_EUROBRAILLE] = {
    .protocolName = strtext("eurobraille"),
    .externalSpeed = IR_EXTERNAL_SPEED_EUROBRAILLE,

    .readExternalPacket = readEurobraillePacket,
    .forwardAcknowledgements = 0,

    .forwardInternalPacket = forwardInternalPacket_eurobraille,
    .forwardExternalPacket = forwardExternalPacket_eurobraille,

    .beginForwarding = beginForwarding_eurobraille,
    .endForwarding = endForwarding_eurobraille,

    .next = IR_PROTOCOL_NATIVE
  },

  [IR_PROTOCOL_NATIVE] = {
    .protocolName = strtext("native"),
    .externalSpeed = IR_EXTERNAL_SPEED_NATIVE,

    .readExternalPacket = readNativePacket,
    .forwardAcknowledgements = 1,

    .forwardInternalPacket = forwardInternalPacket_native,
    .forwardExternalPacket = forwardExternalPacket_native,

    .beginForwarding = beginForwarding_native,
    .endForwarding = endForwarding_native,

    .next = IR_PROTOCOL_EUROBRAILLE
  },
};

static const unsigned char protocolCount = ARRAY_COUNT(protocolTable);

static void
setExternalProtocol (BrailleDisplay *brl, ProtocolIndex index) {
  brl->data->external.protocol = &protocolTable[index];
}

static int
enterPacketForwardMode (BrailleDisplay *brl) {
  logMessage(LOG_INFO,
             "entering packet forward mode (port=%s, protocol=%s, speed=%d)",
             brl->data->external.port.name,
             brl->data->external.protocol->protocolName,
             brl->data->external.port.speed);

  {
    char msg[brl->textColumns+1];

    snprintf(msg, sizeof(msg), "%s (%s)",
             gettext("PC mode"),
             gettext(brl->data->external.protocol->protocolName));
    message(NULL, msg, MSG_NODELAY);
  }

  if (!brl->data->external.protocol->beginForwarding(brl)) return 0;
  brl->data->isForwarding = 1;
  return 1;
}

static int
leavePacketForwardMode (BrailleDisplay *brl) {
  logMessage(LOG_INFO, "leaving packet forward mode");
  if (!brl->data->external.protocol->endForwarding(brl)) return 0;
  brl->data->isForwarding = 0;
  brl->data->braille.refresh = 1;
  return 1;
}

static int
forwardExternalPackets (BrailleDisplay *brl) {
  const ProtocolEntry *protocol = brl->data->external.protocol;
  unsigned char packet[IR_MAXIMUM_PACKET_SIZE];
  size_t size;

  while ((size = protocol->readExternalPacket(brl, &brl->data->external.port, packet, sizeof(packet)))) {
    protocol->forwardExternalPacket(brl, packet, size,
                                    (brl->data->isForwarding && !brl->data->isSuspended));
  }

  return errno == EAGAIN;
}

GIO_INPUT_HANDLER(irHandleExternalInput) {
  BrailleDisplay *brl = parameters->data;

  if (!forwardExternalPackets(brl)) brl->hasFailed = 1;
  return 0;
}

static inline int
isMenuKeyPacket (const unsigned char *packet, size_t size) {
  return (size == 2) && (packet[0] == IR_IPT_InteractiveKey) && (packet[1] == 'Q');
}

static int
handleInternalPacket_embedded (BrailleDisplay *brl, const void *packet, size_t size) {
  if (brl->data->isSuspended) return 1;

  /* The test for Menu key should come first since this key toggles
   * packet forward mode on/off
   */
  if (isMenuKeyPacket(packet, size)) {
    logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "menu key pressed");

    if (brl->data->isForwarding) {
      if (!leavePacketForwardMode(brl)) return 0;
    } else {
      if (!enterPacketForwardMode(brl)) return 0;
    }
  } else if (brl->data->isForwarding) {
    if (!brl->data->external.protocol->forwardInternalPacket(brl, packet, size)) return 0;
  } else {
    handleNativePacket(brl, NULL, &keyHandlers_embedded, packet, size);
  }

  return 1;
}

static int
isOffline_embedded (BrailleDisplay *brl) {
  return brl->data->isForwarding || brl->data->isSuspended;
}

static int
handleInternalPacket_nonembedded (BrailleDisplay *brl, const void *packet, size_t size) {
  int menuKeyPressed = isMenuKeyPacket(packet, size);

  if (menuKeyPressed) {
    logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "menu key pressed");

    if (brl->data->isConnected) {
      logMessage(LOG_INFO, "device disconnected");
      brl->data->isConnected = 0;
      return 1;
    }
  }

  if (!brl->data->isConnected) {
    logMessage(LOG_INFO, "device reconnected");
    brl->data->isConnected = 1;
    brl->data->braille.refresh = 1;
    if (menuKeyPressed) return 1;
  }
  
  handleNativePacket(brl, NULL, &keyHandlers_nonembedded, packet, size);
  return 1;
}

static int
isOffline_nonembedded (BrailleDisplay *brl) {
  return !brl->data->isConnected;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  unsigned char packet[IR_MAXIMUM_PACKET_SIZE];
  size_t size;

  while ((size = readNativePacket(brl, &brl->data->internal.port, packet, sizeof(packet)))) {
    if (!brl->data->internal.handlePacket(brl, packet, size)) goto failure;
  }

  if (errno != EAGAIN) goto failure;
  if (brl->data->internal.isOffline(brl)) return BRL_CMD_OFFLINE;
  return EOF;

failure:
  return BRL_CMD_RESTARTBRL;
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *characters) {
  const size_t size = brl->textColumns * brl->textRows;

  if (brl->data->isForwarding) return 1;

  if (cellsHaveChanged(brl->data->braille.cells, brl->buffer, size, NULL, NULL, &brl->data->braille.refresh)) {
    size_t size = writeWindow(brl, brl->buffer);

    if (!size) return 0;
  }

  return 1;
}

static ssize_t askDevice(BrailleDisplay *brl, IrisOutputPacketType request, unsigned char *response, size_t size)
{
  {
    const unsigned char data[] = {request};
    if (! writeNativePacket(brl, &brl->data->internal.port, data, sizeof(data)) ) return 0;
    drainBrailleOutput(brl, 0);
  }

  while (gioAwaitInput(brl->data->internal.port.gioEndpoint, 1000)) {
    size_t res = readNativePacket(brl, &brl->data->internal.port, response, size);
    if (res) return res;
    if (errno != EAGAIN) break;
  }

  return 0;
}

static int
suspendDevice (BrailleDisplay *brl) {
  if (!brl->data->isEmbedded) return 1;
  logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "suspending device");
  brl->data->isSuspended = 1;

  if (brl->data->isForwarding) {
    if (!sendMenuKey(brl, &brl->data->external.port)) return 0;
  }

  if (!clearWindow(brl)) return 0;
  drainBrailleOutput(brl, 50);
  deactivateBraille();
  setBrailleOffline(brl);
  return 1;
}

static int
resumeDevice (BrailleDisplay *brl) {
  if (!brl->data->isEmbedded) return 1;
  logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "resuming device");
  activateBraille();

  if (brl->data->isForwarding) {
    if (!sendMenuKey(brl, &brl->data->external.port)) return 0;
  } else {
    brl->data->braille.refresh = 1;
    setBrailleOnline(brl);
  }

  brl->data->isSuspended = 0;
  return 1;
}

static void
closePort (Port *port) {
  if (port->gioEndpoint) {
    gioDisconnectResource(port->gioEndpoint);
    port->gioEndpoint = NULL;
  }
}

static int
openPort (Port *port) {
  static const SerialParameters serialParameters = {
    SERIAL_DEFAULT_PARAMETERS,
    .parity = SERIAL_PARITY_EVEN
  };

  GioDescriptor gioDescriptor;
  gioInitializeDescriptor(&gioDescriptor);

  port->serialParameters = serialParameters;
  port->serialParameters.baud = port->speed;
  gioDescriptor.serial.parameters = &port->serialParameters;

  closePort(port);

  if ((port->gioEndpoint = gioConnectResource(port->name, &gioDescriptor))) {
    port->state = 0;
    return 1;
  }

  return 0;
}

static int
openInternalPort (BrailleDisplay *brl) {
  Port *port = &brl->data->internal.port;

  if (openPort(port)) {
    brl->gioEndpoint = port->gioEndpoint;
    return 1;
  }

  return 0;
}

static void
closeInternalPort (BrailleDisplay *brl) {
  brl->gioEndpoint = NULL;
  closePort(&brl->data->internal.port);
}

static void
stopExternalInputHandler (BrailleDisplay *brl) {
  if (brl->data->external.hio) {
    gioDestroyHandleInputObject(brl->data->external.hio);
    brl->data->external.hio = NULL;
  }
}

static int
openExternalPort (BrailleDisplay *brl) {
  stopExternalInputHandler(brl);

  if (openPort(&brl->data->external.port)) {
    brl->data->external.hio =
      gioNewHandleInputObject(brl->data->external.port.gioEndpoint,
                                BRAILLE_DRIVER_INPUT_POLL_INTERVAL,
                                irHandleExternalInput, brl);

    if (brl->data->external.hio) return 1;
  }

  return 0;
}

static void
closeExternalPort (BrailleDisplay *brl) {
  stopExternalInputHandler(brl);
  closePort(&brl->data->external.port);
}

static int
checkLatchState (BrailleDisplay *brl) {
  unsigned char pulled = !(readPort1(IR_PORT_INPUT) & 0X04);

  if (brl->data->latch.pulled) {
    if (pulled) {
      long int elapsed = getMonotonicElapsed(&brl->data->latch.started);
      int result = (brl->data->latch.elapsed <= brl->data->latch.delay) &&
                   (elapsed > brl->data->latch.delay);

      brl->data->latch.elapsed = elapsed;
      return result;
    }

    brl->data->latch.pulled = 0;
    logMessage(LOG_INFO, "latch released");
  } else if (pulled) {
    getMonotonicTime(&brl->data->latch.started);
    brl->data->latch.elapsed = 0;
    brl->data->latch.pulled = 1;
    logMessage(LOG_INFO, "latch pulled");    
  }

  return 0;
}

ASYNC_ALARM_CALLBACK(irMonitorLatch) {
  BrailleDisplay *brl = parameters->data;

  if (checkLatchState(brl)) {
    if (!(brl->data->isSuspended? resumeDevice(brl): suspendDevice(brl))) brl->hasFailed = 1;
  }
}

static int
startLatchMonitor (BrailleDisplay *brl) {
  if (brl->data->latch.monitor) return 1;
  if (!brl->data->latch.delay) return 1;

  if (asyncNewRelativeAlarm(&brl->data->latch.monitor, 0, irMonitorLatch, brl)) {
    if (asyncResetAlarmInterval(brl->data->latch.monitor, brl->data->latch.interval)) {
      brl->data->latch.pulled = 0;
      return 1;
    }

    asyncCancelRequest(brl->data->latch.monitor);
    brl->data->latch.monitor = NULL;
  }

  return 0;
}

static void
stopLatchMonitor (BrailleDisplay *brl) {
  if (brl->data->latch.monitor) {
    asyncCancelRequest(brl->data->latch.monitor);
    brl->data->latch.monitor = NULL;
  }
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    unsigned int embedded;

    memset(brl->data, 0, sizeof(*brl->data));

    brl->data->isConnected = 1;

    brl->data->isSuspended = 0;
    brl->data->isForwarding = 0;

    brl->data->haveVisualDisplay = 0;

    brl->data->internal.port.gioEndpoint = NULL;
    brl->data->internal.port.writeNativePacket = writeNativePacket_internal;
    brl->data->internal.port.handleNativeAcknowledgement = handleNativeAcknowledgement_internal;
    brl->data->internal.linearKeys = 0;

    brl->data->external.port.gioEndpoint = NULL;
    brl->data->external.port.writeNativePacket = writeNativePacket_external;
    brl->data->external.port.handleNativeAcknowledgement = handleNativeAcknowledgement_external;
    brl->data->external.hio = NULL;
    memset(brl->data->external.cells, 0, sizeof(brl->data->external.cells));

    brl->data->latch.monitor = NULL;
    brl->data->latch.delay = IR_DEFAULT_LATCH_DELAY;
    brl->data->latch.interval = IR_DEFAULT_LATCH_INTERVAL;

    brl->data->braille.refresh = 1;

    if (validateYesNo(&embedded, parameters[PARM_EMBEDDED])) {
      int internalPortOpened = 0;

      brl->data->isEmbedded = !!embedded;
      logMessage(LOG_INFO, "Driver Mode: %s",
                 (brl->data->isEmbedded? "embedded": "non-embedded"));

      if (brl->data->isEmbedded) {
        {
          const char *parameter = parameters[PARM_PROTOCOL];
          const char *choices[protocolCount + 1];
          unsigned int choice;

          for (choice=0; choice<protocolCount; choice+=1) {
            choices[choice] = protocolTable[choice].protocolName;
          }
          choices[protocolCount] = NULL;

          if (!validateChoice(&choice, parameter, choices)) {
            choice = IR_PROTOCOL_DEFAULT;
            logMessage(LOG_WARNING, "invalid protocol setting: %s", parameter);
          }

          setExternalProtocol(brl, choice);
          logMessage(LOG_INFO, "External Protocol: %s", brl->data->external.protocol->protocolName);
        }

        {
          const char *parameter = parameters[PARM_LATCH_DELAY];

          if (*parameter) {
            static const int minimum = 0;
            static const int maximum = 100;
            int value;

            if (validateInteger(&value, parameter, &minimum, &maximum)) {
              brl->data->latch.delay = value * 100;
            } else {
              logMessage(LOG_WARNING, "invalid latch delay setting: %s", parameter);
            }
          }
        }

        if (startLatchMonitor(brl)) {
          if (enablePorts(LOG_ERR, IR_PORT_BASE, 3) != -1) {
            brl->data->external.port.name = device;
            brl->data->external.port.speed = brl->data->external.protocol->externalSpeed;

            if (openExternalPort(brl)) {
              brl->data->internal.port.name = "serial:ttyS1";
              brl->data->internal.port.speed = IR_INTERNAL_SPEED;

              if (openInternalPort(brl)) {
                brl->data->internal.handlePacket = handleInternalPacket_embedded;
                brl->data->internal.isOffline = isOffline_embedded;

                activateBraille();
                internalPortOpened = 1;
              }
            }
          } else {
            logSystemError("ioperm");
          }
        }
      } else {
        brl->data->internal.port.name = device;
        brl->data->internal.port.speed = IR_EXTERNAL_SPEED_NATIVE;

        if (openInternalPort(brl)) {
          brl->data->internal.handlePacket = handleInternalPacket_nonembedded;
          brl->data->internal.isOffline = isOffline_nonembedded;

          brl->data->isConnected = 1;
          internalPortOpened = 1;
        }
      }

      if (internalPortOpened) {
        unsigned char deviceResponse[IR_MAXIMUM_PACKET_SIZE];
        ssize_t size;

        if (!(size = askDevice(brl, IR_OPT_VersionRequest, deviceResponse, sizeof(deviceResponse)) )) {
          logMessage(LOG_WARNING, "received no response to version request");
        } else if (size < 3) {
          logBytes(LOG_WARNING, "short firmware version response", deviceResponse, size);
        }  else if (deviceResponse[0] != IR_IPT_VersionResponse) {
          logBytes(LOG_WARNING, "unexpected firmware version response", deviceResponse, size);
        } else {
          const KeyTableDefinition *ktd;

          switch (deviceResponse[1]) {
            case 'a':
            case 'A':
              ktd = &KEY_TABLE_DEFINITION(pc);
              brl->textColumns = IR_WINDOW_SIZE_MAXIMUM;
              break;

            case 'l':
            case 'L':
              ktd = &KEY_TABLE_DEFINITION(brl);
              brl->textColumns = IR_WINDOW_SIZE_MAXIMUM;
              brl->data->haveVisualDisplay = 1;
              break;

            case 's':
            case 'S':
              ktd = &KEY_TABLE_DEFINITION(brl);
              brl->textColumns = IR_WINDOW_SIZE_SMALL;
              break;

            default:
              logBytes(LOG_WARNING, "unrecognized device type in firmware version response", deviceResponse, size);
              ktd = NULL;
              break;
          }

          if (ktd) {
            setBrailleKeyTable(brl, ktd);

            if ((brl->data->firmwareVersion = malloc(size - 1))) {
              memcpy(brl->data->firmwareVersion, deviceResponse+2, size-2);
              brl->data->firmwareVersion[size-2] = 0;
              logMessage(LOG_INFO, "Firmware Version: %s", brl->data->firmwareVersion);

              if (!(size = askDevice(brl, IR_OPT_SerialNumberRequest, deviceResponse, sizeof(deviceResponse)))) {
                logMessage(LOG_WARNING, "Received no response to serial number request.");
              } else if (size != IR_OPT_SERIALNUMBERRESPONSE_LENGTH) {
                logBytes(LOG_WARNING, "short serial number response", deviceResponse, size);
              } else if (deviceResponse[0] != IR_IPT_SerialNumberResponse) {
                logBytes(LOG_WARNING, "unexpected serial number response", deviceResponse, size);
              } else {
                if (deviceResponse[1] != IR_OPT_SERIALNUMBERRESPONSE_NOWINDOWLENGTH) {
                  brl->textColumns = deviceResponse[1];
                }

                {
                  char *byte = brl->data->serialNumber;

                  byte = mempcpy(byte, deviceResponse+2,
                                 (sizeof(brl->data->serialNumber) - 1));
                  *byte = 0;
                  logMessage(LOG_INFO, "Serial Number: %s", brl->data->serialNumber);
                }

                logMessage(LOG_INFO, "Display Size: %u", brl->textColumns);
                logMessage(LOG_INFO, "Visual Display: %s",
                           (brl->data->haveVisualDisplay? "yes": "no"));

                makeOutputTable(dotsTable_ISO11548_1);
                return 1;
              }

              free(brl->data->firmwareVersion);
            } else {
              logMallocError();
            }
          }
        }
      }
    } else {
      logMessage(LOG_WARNING, "invalid embedded setting: %s", parameters[PARM_EMBEDDED]);
    }

    stopLatchMonitor(brl);
    closeExternalPort(brl);
    closeInternalPort(brl);
    free(brl->data);
  } else {
    logMallocError();
  }

  return 0;
}

static void
brl_destruct (BrailleDisplay *brl) {
  if (brl->data->isEmbedded) {
    clearWindow(brl);
    drainBrailleOutput(brl, 50);
    deactivateBraille();
  }

  if (brl->data) {
    stopLatchMonitor(brl);
    closeExternalPort(brl);
    closeInternalPort(brl);
    free(brl->data->firmwareVersion);

    free(brl->data);
    brl->data = NULL;
  }
}

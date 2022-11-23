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

typedef struct {
  unsigned char modelIdentifier;
  unsigned char protocolRevision;
  const char *modelName;
  const KeyTableDefinition *keyTableDefinition;

  uint8_t textColumns;
  uint8_t frontKeys;
  uint8_t hasBar;
  uint8_t leftSwitches;
  uint8_t rightSwitches;
  uint8_t leftKeys;
  uint8_t rightKeys;
  uint8_t statusCount;
} ModelEntry; 

#define PM_MODEL_IDENTITY(identifier, model, name, protocol)	\
  .modelIdentifier = identifier, \
  .modelName = name, \
  .protocolRevision = protocol, \
  .keyTableDefinition = &KEY_TABLE_DEFINITION(model)

#define PM_CELL_COUNTS(columns, status) \
  .textColumns = columns, \
  .statusCount = status

#define PM_FRONT_KEYS(front) \
  .frontKeys = front

#define PM_BAR(ls, rs, lk, rk) \
  .hasBar = 1, \
  .leftSwitches = ls, \
  .rightSwitches = rs, \
  .leftKeys = lk, \
  .rightKeys = rk


BEGIN_KEY_NAME_TABLE(bar)
  BRL_KEY_NAME_ENTRY(PM, BAR, Left1, "BarLeft1"),
  BRL_KEY_NAME_ENTRY(PM, BAR, Left2, "BarLeft2"),
  BRL_KEY_NAME_ENTRY(PM, BAR, Right1, "BarRight1"),
  BRL_KEY_NAME_ENTRY(PM, BAR, Right2, "BarRight2"),
  BRL_KEY_NAME_ENTRY(PM, BAR, Up1, "BarUp1"),
  BRL_KEY_NAME_ENTRY(PM, BAR, Up2, "BarUp2"),
  BRL_KEY_NAME_ENTRY(PM, BAR, Down1, "BarDown1"),
  BRL_KEY_NAME_ENTRY(PM, BAR, Down2, "BarDown2"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(switches)
  BRL_KEY_NAME_ENTRY(PM, SWT, LeftSwitchRear, "LeftSwitchRear"),
  BRL_KEY_NAME_ENTRY(PM, SWT, LeftSwitchFront, "LeftSwitchFront"),
  BRL_KEY_NAME_ENTRY(PM, SWT, RightSwitchRear, "RightSwitchRear"),
  BRL_KEY_NAME_ENTRY(PM, SWT, RightSwitchFront, "RightSwitchFront"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(keys)
  BRL_KEY_NAME_ENTRY(PM, SWT, LeftKeyRear, "LeftKeyRear"),
  BRL_KEY_NAME_ENTRY(PM, SWT, LeftKeyFront, "LeftKeyFront"),
  BRL_KEY_NAME_ENTRY(PM, SWT, RightKeyRear, "RightKeyRear"),
  BRL_KEY_NAME_ENTRY(PM, SWT, RightKeyFront, "RightKeyFront"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(front9)
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 0, "Function"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 1, "Cursor"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 2, "Backward"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 3, "Up"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 4, "Home"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 5, "Down"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 6, "Forward"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 7, "Braille"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 8, "Attribute"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(front13)
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 0, "Dot7"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 1, "Dot3"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 2, "Dot2"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 3, "Dot1"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 4, "Up"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 5, "Home"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 6, "Shift"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 7, "End"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 8, "Down"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 9, "Dot4"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 10, "Dot5"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 11, "Dot6"),
  BRL_KEY_NUMBER_ENTRY(PM, FK1, 12, "Dot8"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(keyboard)
  BRL_KEY_NAME_ENTRY(PM, KBD, Dot1, "Dot1"),
  BRL_KEY_NAME_ENTRY(PM, KBD, Dot2, "Dot2"),
  BRL_KEY_NAME_ENTRY(PM, KBD, Dot3, "Dot3"),
  BRL_KEY_NAME_ENTRY(PM, KBD, Dot4, "Dot4"),
  BRL_KEY_NAME_ENTRY(PM, KBD, Dot5, "Dot5"),
  BRL_KEY_NAME_ENTRY(PM, KBD, Dot6, "Dot6"),
  BRL_KEY_NAME_ENTRY(PM, KBD, Dot7, "Dot7"),
  BRL_KEY_NAME_ENTRY(PM, KBD, Dot8, "Dot8"),

  BRL_KEY_NAME_ENTRY(PM, KBD, Space, "Space"),
  BRL_KEY_NAME_ENTRY(PM, KBD, LeftSpace, "LeftSpace"),
  BRL_KEY_NAME_ENTRY(PM, KBD, RightSpace, "RightSpace"),
  BRL_KEY_NAME_ENTRY(PM, KBD, LeftThumb, "LeftThumb"),
  BRL_KEY_NAME_ENTRY(PM, KBD, RightThumb, "RightThumb"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(routingKeys1)
  BRL_KEY_GROUP_ENTRY(PM, RK1, "RoutingKey1"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(routingKeys2)
  BRL_KEY_GROUP_ENTRY(PM, RK2, "RoutingKey2"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(statusKeys1)
  BRL_KEY_GROUP_ENTRY(PM, SK1, "Status"),
END_KEY_NAME_TABLE

BEGIN_KEY_NAME_TABLE(statusKeys2)
  BRL_KEY_GROUP_ENTRY(PM, SK2, "StatusKey2"),
END_KEY_NAME_TABLE


BEGIN_KEY_NAME_TABLES(c_486)
  KEY_NAME_TABLE(front9),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(2d_l)
  KEY_NAME_TABLE(front9),
  KEY_NAME_TABLE(statusKeys1),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(c)
  KEY_NAME_TABLE(front9),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(2d_s)
  KEY_NAME_TABLE(front13),
  KEY_NAME_TABLE(statusKeys1),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(ib_80)
  KEY_NAME_TABLE(front9),
  KEY_NAME_TABLE(statusKeys1),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el_2d_40)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(switches),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(statusKeys1),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el_2d_66)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(switches),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(statusKeys1),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el_80)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(switches),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(statusKeys1),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el_2d_80)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(switches),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(statusKeys1),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el_40_p)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(switches),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(elba_32)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(switches),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(elba_20)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(switches),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el40s)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el80_ii)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(statusKeys1),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el66s)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
  KEY_NAME_TABLE(routingKeys2),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el80s)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
  KEY_NAME_TABLE(routingKeys2),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(trio)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(keyboard),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el70s)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
  KEY_NAME_TABLE(routingKeys2),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el2d_80s)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(statusKeys1),
  KEY_NAME_TABLE(routingKeys1),
  KEY_NAME_TABLE(routingKeys2),
  KEY_NAME_TABLE(statusKeys2),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(elb_tr_20)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(elb_tr_32)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el40c)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
  KEY_NAME_TABLE(routingKeys2),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el60c)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
  KEY_NAME_TABLE(routingKeys2),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(el80c)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(routingKeys1),
  KEY_NAME_TABLE(routingKeys2),
END_KEY_NAME_TABLES

BEGIN_KEY_NAME_TABLES(live)
  KEY_NAME_TABLE(bar),
  KEY_NAME_TABLE(keys),
  KEY_NAME_TABLE(keyboard),
  KEY_NAME_TABLE(routingKeys1),
END_KEY_NAME_TABLES

DEFINE_KEY_TABLE(c_486)
DEFINE_KEY_TABLE(2d_l)
DEFINE_KEY_TABLE(c)
DEFINE_KEY_TABLE(2d_s)
DEFINE_KEY_TABLE(ib_80)
DEFINE_KEY_TABLE(el_2d_40)
DEFINE_KEY_TABLE(el_2d_66)
DEFINE_KEY_TABLE(el_80)
DEFINE_KEY_TABLE(el_2d_80)
DEFINE_KEY_TABLE(el_40_p)
DEFINE_KEY_TABLE(elba_32)
DEFINE_KEY_TABLE(elba_20)
DEFINE_KEY_TABLE(el40s)
DEFINE_KEY_TABLE(el80_ii)
DEFINE_KEY_TABLE(el66s)
DEFINE_KEY_TABLE(el80s)
DEFINE_KEY_TABLE(trio)
DEFINE_KEY_TABLE(el70s)
DEFINE_KEY_TABLE(el2d_80s)
DEFINE_KEY_TABLE(elb_tr_20)
DEFINE_KEY_TABLE(elb_tr_32)
DEFINE_KEY_TABLE(el40c)
DEFINE_KEY_TABLE(el60c)
DEFINE_KEY_TABLE(el80c)
DEFINE_KEY_TABLE(live)

BEGIN_KEY_TABLE_LIST
  &KEY_TABLE_DEFINITION(c_486),
  &KEY_TABLE_DEFINITION(2d_l),
  &KEY_TABLE_DEFINITION(c),
  &KEY_TABLE_DEFINITION(2d_s),
  &KEY_TABLE_DEFINITION(ib_80),
  &KEY_TABLE_DEFINITION(el_2d_40),
  &KEY_TABLE_DEFINITION(el_2d_66),
  &KEY_TABLE_DEFINITION(el_80),
  &KEY_TABLE_DEFINITION(el_2d_80),
  &KEY_TABLE_DEFINITION(el_40_p),
  &KEY_TABLE_DEFINITION(elba_32),
  &KEY_TABLE_DEFINITION(elba_20),
  &KEY_TABLE_DEFINITION(el40s),
  &KEY_TABLE_DEFINITION(el80_ii),
  &KEY_TABLE_DEFINITION(el66s),
  &KEY_TABLE_DEFINITION(el80s),
  &KEY_TABLE_DEFINITION(trio),
  &KEY_TABLE_DEFINITION(el70s),
  &KEY_TABLE_DEFINITION(el2d_80s),
  &KEY_TABLE_DEFINITION(elb_tr_20),
  &KEY_TABLE_DEFINITION(elb_tr_32),
  &KEY_TABLE_DEFINITION(el40c),
  &KEY_TABLE_DEFINITION(el60c),
  &KEY_TABLE_DEFINITION(el80c),
  &KEY_TABLE_DEFINITION(live),
END_KEY_TABLE_LIST


static const ModelEntry modelTable[] = {
  { PM_MODEL_IDENTITY(0, c_486, "BrailleX Compact 486", 1),
    PM_CELL_COUNTS(40, 0),
    PM_FRONT_KEYS(9)
  },

  { PM_MODEL_IDENTITY(1, 2d_l, "BrailleX 2D Lite (plus)", 1),
    PM_CELL_COUNTS(40, 13),
    PM_FRONT_KEYS(9)
  },

  { PM_MODEL_IDENTITY(2, c, "BrailleX Compact/Tiny", 1),
    PM_CELL_COUNTS(40, 0),
    PM_FRONT_KEYS(9)
  },

  { PM_MODEL_IDENTITY(3, 2d_s, "BrailleX 2D Screen Soft", 1),
    PM_CELL_COUNTS(80, 22),
    PM_FRONT_KEYS(13)
  },

  { PM_MODEL_IDENTITY(6, ib_80, "BrailleX IB 80 CR Soft", 1),
    PM_CELL_COUNTS(80, 4),
    PM_FRONT_KEYS(9)
  },

  { PM_MODEL_IDENTITY(64, el_2d_40, "BrailleX EL 2D-40", 1),
    PM_CELL_COUNTS(40, 13),
    PM_BAR(1, 1, 1, 1)
  },

  { PM_MODEL_IDENTITY(65, el_2d_66, "BrailleX EL 2D-66", 1),
    PM_CELL_COUNTS(66, 13),
    PM_BAR(1, 1, 1, 1)
  },

  { PM_MODEL_IDENTITY(66, el_80, "BrailleX EL 80", 1),
    PM_CELL_COUNTS(80, 2),
    PM_BAR(1, 1, 1, 1)
  },

  { PM_MODEL_IDENTITY(67, el_2d_80, "BrailleX EL 2D-80", 1),
    PM_CELL_COUNTS(80, 20),
    PM_BAR(1, 1, 1, 1)
  },

  { PM_MODEL_IDENTITY(68, el_40_p, "BrailleX EL 40 P", 1),
    PM_CELL_COUNTS(40, 0),
    PM_BAR(1, 1, 1, 0)
  },

  { PM_MODEL_IDENTITY(69, elba_32, "BrailleX Elba 32", 1),
    PM_CELL_COUNTS(32, 0),
    PM_BAR(1, 1, 1, 1)
  },

  { PM_MODEL_IDENTITY(70, elba_20, "BrailleX Elba 20", 1),
    PM_CELL_COUNTS(20, 0),
    PM_BAR(1, 1, 1, 1)
  },

  { PM_MODEL_IDENTITY(85, el40s, "BrailleX EL40s", 1),
    PM_CELL_COUNTS(40, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(86, el80_ii, "BrailleX EL80-II", 1),
    PM_CELL_COUNTS(80, 2),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(87, el66s, "BrailleX EL66s", 1),
    PM_CELL_COUNTS(66, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(88, el80s, "BrailleX EL80s", 1),
    PM_CELL_COUNTS(80, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(89, trio, "BrailleX Trio", 2),
    PM_CELL_COUNTS(40, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(90, el70s, "BrailleX EL70s", 1),
    PM_CELL_COUNTS(70, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(91, el2d_80s, "BrailleX EL2D-80s", 1),
    PM_CELL_COUNTS(80, 20),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(92, elb_tr_20, "BrailleX Elba (Trio 20)", 2),
    PM_CELL_COUNTS(20, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(93, elb_tr_32, "BrailleX Elba (Trio 32)", 2),
    PM_CELL_COUNTS(32, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(95, el40c, "BrailleX EL40c", 1),
    PM_CELL_COUNTS(40, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(96, el60c, "BrailleX EL60c", 1),
    PM_CELL_COUNTS(60, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(97, el80c, "BrailleX EL80c", 1),
    PM_CELL_COUNTS(80, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(98, live, "BrailleX Live 40", 2),
    PM_CELL_COUNTS(40, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(99, live, "BrailleX Live+ 40", 2),
    PM_CELL_COUNTS(40, 0),
    PM_BAR(0, 0, 1, 1)
  },

  { PM_MODEL_IDENTITY(100, live, "BrailleX Live 20", 2),
    PM_CELL_COUNTS(20, 0),
    PM_BAR(0, 0, 1, 1)
  }
};

static const unsigned int modelCount = ARRAY_COUNT(modelTable);

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

#ifndef BRLTTY_INCLUDED_KTB_KEYBOARD
#define BRLTTY_INCLUDED_KTB_KEYBOARD

#include "ktb_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define KBD_GROUP(grp) KBD_GRP_##grp
#define KBD_KEY(grp,key) KBD_KEY_##grp##_##key

#define KBD_KEY_VALUE(grp,num) {.group=KBD_GROUP(grp), .number=num}
#define KBD_KEY_ENTRY(grp,key) KBD_KEY_VALUE(grp, KBD_KEY(grp, key))

#define KBD_GROUP_NAME(grp,nam) {.value=KBD_KEY_VALUE(grp, KTB_KEY_ANY), .name=nam}
#define KBD_KEY_NAME(grp,key,nam) {.value=KBD_KEY_ENTRY(grp, key), .name=nam}

#define KBD_KEY_SPECIAL(name) KBD_KEY_ENTRY(SPECIAL, name)
#define KBD_KEY_UNMAPPED KBD_KEY_SPECIAL(Unmapped)
#define KBD_KEY_IGNORE KBD_KEY_SPECIAL(Ignore)

#define KBD_KEY_LETTER(name) KBD_KEY_ENTRY(LETTER, name)
#define KBD_KEY_NUMBER(name) KBD_KEY_ENTRY(NUMBER, name)
#define KBD_KEY_SYMBOL(name) KBD_KEY_ENTRY(SYMBOL, name)

#define KBD_KEY_ACTION(name) KBD_KEY_ENTRY(ACTION, name)
#define KBD_KEY_MEDIA(name) KBD_KEY_ENTRY(MEDIA, name)
#define KBD_KEY_FUNCTION(name) KBD_KEY_ENTRY(FUNCTION, name)

#define KBD_KEY_MODIFIER(name) KBD_KEY_ENTRY(MODIFIER, name)
#define KBD_KEY_LOCK(name) KBD_KEY_ENTRY(LOCK, name)

#define KBD_KEY_KPNUMBER(name) KBD_KEY_ENTRY(KPNUMBER, name)
#define KBD_KEY_KPSYMBOL(name) KBD_KEY_ENTRY(KPSYMBOL, name)
#define KBD_KEY_KPACTION(name) KBD_KEY_ENTRY(KPACTION, name)

#define KBD_KEY_BRAILLE(name) KBD_KEY_ENTRY(BRAILLE, name)
#define KBD_KEY_ROUTING(number) KBD_KEY_VALUE(ROUTING, (number))

typedef enum {
  KBD_GROUP(SPECIAL) = 0 /* KBD_KEY_UNMAPPED must be all zeros */,

  KBD_GROUP(LETTER),
  KBD_GROUP(NUMBER),
  KBD_GROUP(SYMBOL),

  KBD_GROUP(ACTION),
  KBD_GROUP(MEDIA),
  KBD_GROUP(FUNCTION),

  KBD_GROUP(MODIFIER),
  KBD_GROUP(LOCK),

  KBD_GROUP(KPNUMBER),
  KBD_GROUP(KPSYMBOL),
  KBD_GROUP(KPACTION),

  KBD_GROUP(BRAILLE),
  KBD_GROUP(ROUTING),
} KBD_KeyGroup;

typedef enum {
  KBD_KEY(SPECIAL, Unmapped) = 0 /* KBD_KEY_UNMAPPED must be all zeros */,
  KBD_KEY(SPECIAL, Ignore),
} KBD_SpecialKey;

typedef enum {
  KBD_KEY(LETTER, A),
  KBD_KEY(LETTER, B),
  KBD_KEY(LETTER, C),
  KBD_KEY(LETTER, D),
  KBD_KEY(LETTER, E),
  KBD_KEY(LETTER, F),
  KBD_KEY(LETTER, G),
  KBD_KEY(LETTER, H),
  KBD_KEY(LETTER, I),
  KBD_KEY(LETTER, J),
  KBD_KEY(LETTER, K),
  KBD_KEY(LETTER, L),
  KBD_KEY(LETTER, M),
  KBD_KEY(LETTER, N),
  KBD_KEY(LETTER, O),
  KBD_KEY(LETTER, P),
  KBD_KEY(LETTER, Q),
  KBD_KEY(LETTER, R),
  KBD_KEY(LETTER, S),
  KBD_KEY(LETTER, T),
  KBD_KEY(LETTER, U),
  KBD_KEY(LETTER, V),
  KBD_KEY(LETTER, W),
  KBD_KEY(LETTER, X),
  KBD_KEY(LETTER, Y),
  KBD_KEY(LETTER, Z),
} KBD_LetterKey;

typedef enum {
  KBD_KEY(NUMBER, Zero),
  KBD_KEY(NUMBER, One),
  KBD_KEY(NUMBER, Two),
  KBD_KEY(NUMBER, Three),
  KBD_KEY(NUMBER, Four),
  KBD_KEY(NUMBER, Five),
  KBD_KEY(NUMBER, Six),
  KBD_KEY(NUMBER, Seven),
  KBD_KEY(NUMBER, Eight),
  KBD_KEY(NUMBER, Nine),
} KBD_NumberKey;

typedef enum {
  KBD_KEY(SYMBOL, Grave),
  KBD_KEY(SYMBOL, Minus),
  KBD_KEY(SYMBOL, Equals),
  KBD_KEY(SYMBOL, Backslash),

  KBD_KEY(SYMBOL, LeftBracket),
  KBD_KEY(SYMBOL, RightBracket),

  KBD_KEY(SYMBOL, Semicolon),
  KBD_KEY(SYMBOL, Apostrophe),

  KBD_KEY(SYMBOL, Europe2),
  KBD_KEY(SYMBOL, Comma),
  KBD_KEY(SYMBOL, Period),
  KBD_KEY(SYMBOL, Slash),

  KBD_KEY(SYMBOL, Space),
} KBD_SymbolKey;

typedef enum {
  KBD_KEY(ACTION, Enter),
  KBD_KEY(ACTION, Tab),
  KBD_KEY(ACTION, Escape),

  KBD_KEY(ACTION, Insert),
  KBD_KEY(ACTION, DeleteBackward),
  KBD_KEY(ACTION, DeleteForward),

  KBD_KEY(ACTION, Home),
  KBD_KEY(ACTION, End),

  KBD_KEY(ACTION, PageUp),
  KBD_KEY(ACTION, PageDown),

  KBD_KEY(ACTION, ArrowUp),
  KBD_KEY(ACTION, ArrowDown),
  KBD_KEY(ACTION, ArrowLeft),
  KBD_KEY(ACTION, ArrowRight),

  KBD_KEY(ACTION, PrintScreen),
  KBD_KEY(ACTION, SystemRequest),
  KBD_KEY(ACTION, Pause),

  KBD_KEY(ACTION, GuiLeft),
  KBD_KEY(ACTION, GuiRight),
  KBD_KEY(ACTION, Context),

  KBD_KEY(ACTION, Help),
  KBD_KEY(ACTION, Stop),
  KBD_KEY(ACTION, Props),
  KBD_KEY(ACTION, Front),
  KBD_KEY(ACTION, Open),
  KBD_KEY(ACTION, Find),
  KBD_KEY(ACTION, Again),
  KBD_KEY(ACTION, Undo),
  KBD_KEY(ACTION, Copy),
  KBD_KEY(ACTION, Paste),
  KBD_KEY(ACTION, Cut),

  KBD_KEY(ACTION, Power),
  KBD_KEY(ACTION, Sleep),
  KBD_KEY(ACTION, Wakeup),

  KBD_KEY(ACTION, Menu),
  KBD_KEY(ACTION, Select),

  KBD_KEY(ACTION, Cancel),
  KBD_KEY(ACTION, Clear),
  KBD_KEY(ACTION, Prior),
  KBD_KEY(ACTION, Return),
  KBD_KEY(ACTION, Separator),
  KBD_KEY(ACTION, Out),
  KBD_KEY(ACTION, Oper),
  KBD_KEY(ACTION, Clear_Again),
  KBD_KEY(ACTION, CrSel_Props),
  KBD_KEY(ACTION, ExSel),
} KBD_ActionKey;

typedef enum {
  KBD_KEY(MEDIA, Mute),
  KBD_KEY(MEDIA, VolumeDown),
  KBD_KEY(MEDIA, VolumeUp),

  KBD_KEY(MEDIA, Stop),
  KBD_KEY(MEDIA, Play),
  KBD_KEY(MEDIA, Record),
  KBD_KEY(MEDIA, Pause),
  KBD_KEY(MEDIA, PlayPause),

  KBD_KEY(MEDIA, Previous),
  KBD_KEY(MEDIA, Next),
  KBD_KEY(MEDIA, Backward),
  KBD_KEY(MEDIA, Forward),

  KBD_KEY(MEDIA, Eject),
  KBD_KEY(MEDIA, Close),
  KBD_KEY(MEDIA, EjectClose),
} KBD_MediaKey;

typedef enum {
  KBD_KEY(FUNCTION, F1),
  KBD_KEY(FUNCTION, F2),
  KBD_KEY(FUNCTION, F3),
  KBD_KEY(FUNCTION, F4),
  KBD_KEY(FUNCTION, F5),
  KBD_KEY(FUNCTION, F6),
  KBD_KEY(FUNCTION, F7),
  KBD_KEY(FUNCTION, F8),
  KBD_KEY(FUNCTION, F9),
  KBD_KEY(FUNCTION, F10),
  KBD_KEY(FUNCTION, F11),
  KBD_KEY(FUNCTION, F12),
  KBD_KEY(FUNCTION, F13),
  KBD_KEY(FUNCTION, F14),
  KBD_KEY(FUNCTION, F15),
  KBD_KEY(FUNCTION, F16),
  KBD_KEY(FUNCTION, F17),
  KBD_KEY(FUNCTION, F18),
  KBD_KEY(FUNCTION, F19),
  KBD_KEY(FUNCTION, F20),
  KBD_KEY(FUNCTION, F21),
  KBD_KEY(FUNCTION, F22),
  KBD_KEY(FUNCTION, F23),
  KBD_KEY(FUNCTION, F24),
} KBD_FunctionKey;

typedef enum {
  KBD_KEY(MODIFIER, ShiftLeft),
  KBD_KEY(MODIFIER, ShiftRight),

  KBD_KEY(MODIFIER, ControlLeft),
  KBD_KEY(MODIFIER, ControlRight),

  KBD_KEY(MODIFIER, AltLeft),
  KBD_KEY(MODIFIER, AltRight),
} KBD_ModifierKey;

typedef enum {
  KBD_KEY(LOCK, Capitals),
  KBD_KEY(LOCK, Scroll),
  KBD_KEY(LOCK, Numbers),
} KBD_LockKey;

typedef enum {
  KBD_KEY(KPNUMBER, Zero),
  KBD_KEY(KPNUMBER, One),
  KBD_KEY(KPNUMBER, Two),
  KBD_KEY(KPNUMBER, Three),
  KBD_KEY(KPNUMBER, Four),
  KBD_KEY(KPNUMBER, Five),
  KBD_KEY(KPNUMBER, Six),
  KBD_KEY(KPNUMBER, Seven),
  KBD_KEY(KPNUMBER, Eight),
  KBD_KEY(KPNUMBER, Nine),

  KBD_KEY(KPNUMBER, A),
  KBD_KEY(KPNUMBER, B),
  KBD_KEY(KPNUMBER, C),
  KBD_KEY(KPNUMBER, D),
  KBD_KEY(KPNUMBER, E),
  KBD_KEY(KPNUMBER, F),
} KBD_KPNumberKey;

typedef enum {
  KBD_KEY(KPSYMBOL, DecimalSeparator),
  KBD_KEY(KPSYMBOL, ThousandsSeparator),
  KBD_KEY(KPSYMBOL, 00),
  KBD_KEY(KPSYMBOL, 000),

  KBD_KEY(KPSYMBOL, Plus),
  KBD_KEY(KPSYMBOL, Minus),
  KBD_KEY(KPSYMBOL, Multiply),
  KBD_KEY(KPSYMBOL, Divide),
  KBD_KEY(KPSYMBOL, Modulo),

  KBD_KEY(KPSYMBOL, Equals),
  KBD_KEY(KPSYMBOL, Less),
  KBD_KEY(KPSYMBOL, Greater),
  KBD_KEY(KPSYMBOL, PlusMinus),

  KBD_KEY(KPSYMBOL, LeftParenthesis),
  KBD_KEY(KPSYMBOL, RightParenthesis),
  KBD_KEY(KPSYMBOL, LeftBrace),
  KBD_KEY(KPSYMBOL, RightBrace),

  KBD_KEY(KPSYMBOL, BitwiseAnd),
  KBD_KEY(KPSYMBOL, BitwiseOr),
  KBD_KEY(KPSYMBOL, BitwiseXor),

  KBD_KEY(KPSYMBOL, BooleanNot),
  KBD_KEY(KPSYMBOL, BooleanAnd),
  KBD_KEY(KPSYMBOL, BooleanOr),
  KBD_KEY(KPSYMBOL, BooleanXor),

  KBD_KEY(KPSYMBOL, Space),
  KBD_KEY(KPSYMBOL, Period),
  KBD_KEY(KPSYMBOL, Comma),
  KBD_KEY(KPSYMBOL, Colon),
  KBD_KEY(KPSYMBOL, At),
  KBD_KEY(KPSYMBOL, Number),
  KBD_KEY(KPSYMBOL, CurrencyUnit),
  KBD_KEY(KPSYMBOL, CurrencySubunit),
} KBD_KPSymbolKey;

typedef enum {
  KBD_KEY(KPACTION, Enter),
  KBD_KEY(KPACTION, Backspace),
  KBD_KEY(KPACTION, Tab),

  KBD_KEY(KPACTION, Clear),
  KBD_KEY(KPACTION, ClearEntry),

  KBD_KEY(KPACTION, MemoryClear),
  KBD_KEY(KPACTION, MemoryStore),
  KBD_KEY(KPACTION, MemoryRecall),
  KBD_KEY(KPACTION, MemoryAdd),
  KBD_KEY(KPACTION, MemorySubtract),
  KBD_KEY(KPACTION, MemoryMultiply),
  KBD_KEY(KPACTION, MemoryDivide),

  KBD_KEY(KPACTION, Binary),
  KBD_KEY(KPACTION, Octal),
  KBD_KEY(KPACTION, Decimal),
  KBD_KEY(KPACTION, Hexadecimal),
} KBD_KPActionKey;

typedef enum {
  KBD_KEY(BRAILLE, Space),

  KBD_KEY(BRAILLE, Dot1),
  KBD_KEY(BRAILLE, Dot2),
  KBD_KEY(BRAILLE, Dot3),
  KBD_KEY(BRAILLE, Dot4),
  KBD_KEY(BRAILLE, Dot5),
  KBD_KEY(BRAILLE, Dot6),
  KBD_KEY(BRAILLE, Dot7),
  KBD_KEY(BRAILLE, Dot8),

  KBD_KEY(BRAILLE, Backward),
  KBD_KEY(BRAILLE, Forward),
} KBD_BrailleKey;

extern KEY_NAME_TABLES_DECLARATION(keyboard);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_KTB_KEYBOARD */

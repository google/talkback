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

/* brldefs-vs.h : Useful definitions to handle keys entered at */
/* VisioBraille's keyboard */ 

#ifndef BRLTTY_INCLUDED_VS_BRLDEFS
#define BRLTTY_INCLUDED_VS_BRLDEFS

#define BRL_VSMSK_CHAR        0x100
#define BRL_VSMSK_ROUTING     0x200
#define BRL_VSMSK_FUNCTIONKEY 0x400
#define BRL_VSMSK_OTHER       0x800

/* Symbolic definitions for VisioBraille's function keys */
#define BRL_VSKEY_A1 0x400
#define BRL_VSKEY_A2 0x401
#define BRL_VSKEY_A3 0x402
#define BRL_VSKEY_A4 0x403
#define BRL_VSKEY_A5 0x404
#define BRL_VSKEY_A6 0x405
#define BRL_VSKEY_A7 0x406
#define BRL_VSKEY_A8 0x407
#define BRL_VSKEY_B1 0x408
#define BRL_VSKEY_B2 0x409
#define BRL_VSKEY_B3 0x40a
#define BRL_VSKEY_B4 0x40b
#define BRL_VSKEY_B5 0x40c
#define BRL_VSKEY_B6 0x40d
#define BRL_VSKEY_B7 0x40e
#define BRL_VSKEY_B8 0x40f
#define BRL_VSKEY_C1 0x410
#define BRL_VSKEY_C2 0x411
#define BRL_VSKEY_C3 0x412
#define BRL_VSKEY_C4 0x413
#define BRL_VSKEY_C5 0x414
#define BRL_VSKEY_C6 0x415
#define BRL_VSKEY_C7 0x416
#define BRL_VSKEY_C8 0x417
#define BRL_VSKEY_D1 0x418
#define BRL_VSKEY_D2 0x419
#define BRL_VSKEY_D3 0x41a
#define BRL_VSKEY_D4 0x41b
#define BRL_VSKEY_D5 0x41c
#define BRL_VSKEY_D6 0x41d
#define BRL_VSKEY_D7 0x41e
#define BRL_VSKEY_D8 0x41f

#define BRL_VSKEY_PLOC_LT 0x801
#define BRL_VSKEY_BACKSPACE 0x808
#define BRL_VSKEY_TAB 0x809
#define BRL_VSKEY_RETURN 0x80d

#define BRL_VSKEY_PLOC_PLOC_A 0x8A1
#define BRL_VSKEY_PLOC_PLOC_B 0x8A2
#define BRL_VSKEY_PLOC_PLOC_C 0x8A3
#define BRL_VSKEY_PLOC_PLOC_D 0x8A4
#define BRL_VSKEY_PLOC_PLOC_E 0x8A5
#define BRL_VSKEY_PLOC_PLOC_F 0x8A6
#define BRL_VSKEY_PLOC_PLOC_G 0x8A7
#define BRL_VSKEY_PLOC_PLOC_H 0x8A8
#define BRL_VSKEY_PLOC_PLOC_I 0x8A9
#define BRL_VSKEY_PLOC_PLOC_J 0x8AA
#define BRL_VSKEY_PLOC_PLOC_K 0x8AB
#define BRL_VSKEY_PLOC_PLOC_L 0x8AC
#define BRL_VSKEY_PLOC_PLOC_M 0x8AD
#define BRL_VSKEY_PLOC_PLOC_N 0x8AE
#define BRL_VSKEY_PLOC_PLOC_O 0x8AF
#define BRL_VSKEY_PLOC_PLOC_P 0x8B0
#define BRL_VSKEY_PLOC_PLOC_Q 0x8B1
#define BRL_VSKEY_PLOC_PLOC_R 0x8B2
#define BRL_VSKEY_PLOC_PLOC_S 0x8B3
#define BRL_VSKEY_PLOC_PLOC_T 0x8B4
#define BRL_VSKEY_PLOC_PLOC_U 0x8B5
#define BRL_VSKEY_PLOC_PLOC_V 0x8B6
#define BRL_VSKEY_PLOC_PLOC_W 0x8B7
#define BRL_VSKEY_PLOC_PLOC_X 0x8B8
#define BRL_VSKEY_PLOC_PLOC_Y 0x8B9
#define BRL_VSKEY_PLOC_PLOC_Z 0x8BA

#define BRL_VSKEY_CONTROL 0x8BE
#define BRL_VSKEY_ALT 0x8BF
#define BRL_VSKEY_ESCAPE 0x8e0

#endif /* BRLTTY_INCLUDED_VS_BRLDEFS */ 

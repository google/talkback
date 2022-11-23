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

#ifndef BRLTTY_INCLUDED_PM_BRLDEFS
#define BRLTTY_INCLUDED_PM_BRLDEFS

#define PM_MAXIMUM_TEXT_CELLS 80 
#define PM_MAXIMUM_STATUS_CELLS 22

#define PM_P1_MAXIMUM_PACKET_SIZE 100
#define PM_P1_PKT_SEND     'S'
#define PM_P1_PKT_RECEIVE  'K'
#define PM_P1_PKT_IDENTITY 'I'
#define PM_P1_KEY_PRESSED 1

/* protocol 1 offsets within input data structure */
#define PM_P1_RCV_KEYFUNC  0X0000 /* physical and logical function keys */
#define PM_P1_RCV_KEYROUTE 0X0300 /* routing keys */
#define PM_P1_RCV_SENSOR   0X0600 /* sensors or secondary routing keys */

/* protocol 1 offsets within output data structure */
#define PM_P1_XMT_BRLDATA  0X0000 /* data for braille display */
#define PM_P1_XMT_LCDDATA  0X0100 /* data for LCD */
#define PM_P1_XMT_BRLWRITE 0X0200 /* how to write each braille cell:
                                   * 0 = convert data according to braille table (default)
                                   * 1 = write directly
                                   * 2 = mark end of braille display
                                   */
#define PM_P1_XMT_BRLCELL  0X0300 /* description of each braille cell:
                                   * 0 = has cursor routing key
                                   * 1 = has cursor routing key and sensor
                                   */
#define PM_P1_XMT_ASC2BRL  0X0400 /* ASCII to braille translation table */
#define PM_P1_XMT_LCDUSAGE 0X0500 /* source of LCD data:
                                   * 0 = same as braille display
                                   * 1 = not same as braille display
                                   */
#define PM_P1_XMT_CSRPOSN  0X0501 /* cursor position (0 for no cursor) */
#define PM_P1_XMT_CSRDOTS  0X0502 /* cursor represenation in braille dots */
#define PM_P1_XMT_BRL2ASC  0X0503 /* braille to ASCII translation table */
#define PM_P1_XMT_LENFBSEQ 0X0603 /* length of feedback sequence for speech synthesizer */
#define PM_P1_XMT_LENKPSEQ 0X0604 /* length of keypad sequence */
#define PM_P1_XMT_TIMEK1K2 0X0605 /* key code suppression time for moving from K1 to K2 (left) */
#define PM_P1_XMT_TIMEK3K4 0X0606 /* key code suppression time for moving from K3 to K4 (up) */
#define PM_P1_XMT_TIMEK5K6 0X0607 /* key code suppression time for moving from K5 to K6 (right) */
#define PM_P1_XMT_TIMEK7K8 0X0608 /* key code suppression time for moving from K7 to K8 (down) */
#define PM_P1_XMT_TIMEROUT 0X0609 /* routing time interval */
#define PM_P1_XMT_TIMEOPPO 0X060A /* key code suppression time for opposite movements */

typedef enum {
  PM_BAR_Left1 = 0,
  PM_BAR_Left2,
  PM_BAR_Up1,
  PM_BAR_Up2,
  PM_BAR_Right1,
  PM_BAR_Right2,
  PM_BAR_Down1,
  PM_BAR_Down2,
} PM_BarKey;

typedef enum {
  PM_SWT_LeftSwitchRear = 0,
  PM_SWT_LeftSwitchFront,
  PM_SWT_LeftKeyRear,
  PM_SWT_LeftKeyFront,
  PM_SWT_RightKeyRear,
  PM_SWT_RightKeyFront,
  PM_SWT_RightSwitchRear,
  PM_SWT_RightSwitchFront
} PM_SwitchKey;

typedef enum {
  PM_KBD_Dot1 = 0,
  PM_KBD_Dot2,
  PM_KBD_Dot3,
  PM_KBD_Dot4,
  PM_KBD_Dot5,
  PM_KBD_Dot6,
  PM_KBD_Dot7,
  PM_KBD_Dot8,

  PM_KBD_RightThumb,
  PM_KBD_Space,
  PM_KBD_LeftThumb,
  PM_KBD_RightSpace,
  PM_KBD_LeftSpace
} PM_KeyboardKey;

typedef enum {
  PM_GRP_BAR,
  PM_GRP_SWT,
  PM_GRP_KBD,
  PM_GRP_FK1,
  PM_GRP_RK1,
  PM_GRP_RK2,
  PM_GRP_SK1,
  PM_GRP_SK2
} PM_KeyGroup;

#endif /* BRLTTY_INCLUDED_PM_BRLDEFS */ 

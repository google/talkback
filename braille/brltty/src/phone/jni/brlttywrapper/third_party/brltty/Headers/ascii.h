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

#ifndef BRLTTY_INCLUDED_ASCII
#define BRLTTY_INCLUDED_ASCII

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  NUL = 0X00, /* ^@: Null Character */
  SOH = 0X01, /* ^A: Start of Header */
  STX = 0X02, /* ^B: Start of Text */
  ETX = 0X03, /* ^C: End of Text */
  EOT = 0X04, /* ^D: End of Transmission */
  ENQ = 0X05, /* ^E: Enquiry */
  ACK = 0X06, /* ^F: Acknowledgement */
  BEL = 0X07, /* ^G: Bell */
  BS  = 0X08, /* ^H: BackSpace */
  HT  = 0X09, /* ^I: Horizontal Tab (Character Tabulation) */
  LF  = 0X0A, /* ^J: Line Feed */
  VT  = 0X0B, /* ^K: Vertical Tab (Line Tabulation) */
  FF  = 0X0C, /* ^L: Form Feed */
  CR  = 0X0D, /* ^M: Carriage Return */
  SO  = 0X0E, /* ^N: Shift Out */
  SI  = 0X0F, /* ^O: Shift In */
  DLE = 0X10, /* ^P: Data Link Escape */
  DC1 = 0X11, /* ^Q: Device Control One (XON) */
  DC2 = 0X12, /* ^R: Device Control Two */
  DC3 = 0X13, /* ^S: Device Control Three (XOFF) */
  DC4 = 0X14, /* ^T: Device Control Four */
  NAK = 0X15, /* ^U: Negative Acknowledgement */
  SYN = 0X16, /* ^V: Synchronous Idle */
  ETB = 0X17, /* ^W: End of Transmission Block */
  CAN = 0X18, /* ^X: Cancel */
  EM  = 0X19, /* ^Y: End of Medium */
  SUB = 0X1A, /* ^Z: Substitute */
  ESC = 0X1B, /* ^[: Escape */
  FS  = 0X1C, /* ^\: File Separator (Formation Separator Four) */
  GS  = 0X1D, /* ^]: Group Separator (Formation Separator Three) */
  RS  = 0X1E, /* ^^: Record Separator (Formation Separator Two) */
  US  = 0X1F, /* ^_: Unit Separator (Formation Separator One) */
  DEL = 0X7F  /* ^?: Delete */
} AsciiControlCharacter;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_ASCII */

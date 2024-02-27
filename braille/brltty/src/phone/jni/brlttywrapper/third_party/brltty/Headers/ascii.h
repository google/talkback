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

#ifndef BRLTTY_INCLUDED_ASCII
#define BRLTTY_INCLUDED_ASCII

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  ASCII_NUL = 0X00, /* ^@: Null Character */
  ASCII_SOH = 0X01, /* ^A: Start of Header */
  ASCII_STX = 0X02, /* ^B: Start of Text */
  ASCII_ETX = 0X03, /* ^C: End of Text */
  ASCII_EOT = 0X04, /* ^D: End of Transmission */
  ASCII_ENQ = 0X05, /* ^E: Enquiry */
  ASCII_ACK = 0X06, /* ^F: Acknowledgement */
  ASCII_BEL = 0X07, /* ^G: Bell */
  ASCII_BS = 0X08,  /* ^H: BackSpace */
  ASCII_HT = 0X09,  /* ^I: Horizontal Tab (Character Tabulation) */
  ASCII_LF = 0X0A,  /* ^J: Line Feed */
  ASCII_VT = 0X0B,  /* ^K: Vertical Tab (Line Tabulation) */
  ASCII_FF = 0X0C,  /* ^L: Form Feed */
  ASCII_CR = 0X0D,  /* ^M: Carriage Return */
  ASCII_SO = 0X0E,  /* ^N: Shift Out */
  ASCII_SI = 0X0F,  /* ^O: Shift In */
  ASCII_DLE = 0X10, /* ^P: Data Link Escape */
  ASCII_DC1 = 0X11, /* ^Q: Device Control One (X-ON) */
  ASCII_DC2 = 0X12, /* ^R: Device Control Two */
  ASCII_DC3 = 0X13, /* ^S: Device Control Three (X-OFF) */
  ASCII_DC4 = 0X14, /* ^T: Device Control Four */
  ASCII_NAK = 0X15, /* ^U: Negative Acknowledgement */
  ASCII_SYN = 0X16, /* ^V: Synchronous Idle */
  ASCII_ETB = 0X17, /* ^W: End of Transmission Block */
  ASCII_CAN = 0X18, /* ^X: Cancel */
  ASCII_EM = 0X19,  /* ^Y: End of Medium */
  ASCII_SUB = 0X1A, /* ^Z: Substitute */
  ASCII_ESC = 0X1B, /* ^[: Escape */
  ASCII_FS = 0X1C,  /* ^\: File Separator (Formation Separator Four) */
  ASCII_GS = 0X1D,  /* ^]: Group Separator (Formation Separator Three) */
  ASCII_RS = 0X1E,  /* ^^: Record Separator (Formation Separator Two) */
  ASCII_US = 0X1F,  /* ^_: Unit Separator (Formation Separator One) */
  ASCII_DEL = 0X7F  /* ^?: Delete */
} AsciiControlCharacter;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_ASCII */

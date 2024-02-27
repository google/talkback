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

#ifndef BRLTTY_INCLUDED_USB_CDC_ACM
#define BRLTTY_INCLUDED_USB_CDC_ACM

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  USB_CDC_ACM_CTL_SetCommFeature      = 0X02,
  USB_CDC_ACM_CTL_GetCommFeature      = 0X03,
  USB_CDC_ACM_CTL_ClearCommFeature    = 0X04,
  USB_CDC_ACM_CTL_SetLineCoding       = 0X20,
  USB_CDC_ACM_CTL_GetLineCoding       = 0X21,
  USB_CDC_ACM_CTL_SetControlLineState = 0X22,
  USB_CDC_ACM_CTL_SendBreak           = 0X23
} USB_CDC_ACM_ControlRequest;

typedef enum {
  USB_CDC_ACM_LINE_DTR = 0X01,
  USB_CDC_ACM_LINE_RTS = 0X02
} USB_CDC_ACM_ControlLine;

typedef enum {
  USB_CDC_ACM_STOP_1,
  USB_CDC_ACM_STOP_1_5,
  USB_CDC_ACM_STOP_2
} USB_CDC_ACM_StopBits;

typedef enum {
  USB_CDC_ACM_PARITY_NONE,
  USB_CDC_ACM_PARITY_ODD,
  USB_CDC_ACM_PARITY_EVEN,
  USB_CDC_ACM_PARITY_MARK,
  USB_CDC_ACM_PARITY_SPACE
} USB_CDC_ACM_Parity;

typedef struct {
  uint32_t dwDTERate; /* transmission rate - bits per second */
  uint8_t bCharFormat; /* number of stop bits */
  uint8_t bParityType; /* type of parity */
  uint8_t bDataBits; /* number of data bits - 5,6,7,8,16 */
} PACKED USB_CDC_ACM_LineCoding;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_USB_CDC_ACM */

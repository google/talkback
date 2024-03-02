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

#ifndef BRLTTY_INCLUDED_USB_CH341
#define BRLTTY_INCLUDED_USB_CH341

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define USB_CH341_CONTROL_TYPE UsbControlType_Vendor
#define USB_CH341_CONTROL_RECIPIENT UsbControlRecipient_Device
#define USB_CH341_CONTROL_TIMEOUT 1000

typedef enum {
  USB_CH341_REQ_READ_VERSION      = 0X5F,
  USB_CH341_REQ_READ_REGISTERS    = 0X95,
  USB_CH341_REQ_WRITE_REGISTERS   = 0X9A,
  USB_CH341_REQ_INITIALIZE_SERIAL = 0XA1,
  USB_CH341_REQ_WRITE_MCR         = 0XA4,
} USB_CH341_ControlRequest;

typedef enum {
  USB_CH341_REG_BREAK     = 0X05,
  USB_CH341_REG_MSR       = 0X06,
  USB_CH341_REG_LSR       = 0X07,
  USB_CH341_REG_PRESCALER = 0X12,
  USB_CH341_REG_DIVISOR   = 0X13,
  USB_CH341_REG_BPS_MOD   = 0X14,
  USB_CH341_REG_LCR1      = 0X18,
  USB_CH341_REG_LCR2      = 0X25,
  USB_CH341_REG_FLOW      = 0X27,
} USB_CH341_Register;

#define USB_CH341_FREQUENCY 12000000
#define USB_CH341_BAUD_MINIMUM 46
#define USB_CH341_BAUD_MAXIMUM 2000000

typedef enum {
  USB_CH341_PSF_BYPASS_8x  = 0X01,
  USB_CH341_PSF_BYPASS_64x = 0X02,
  USB_CH341_PSF_BYPASS_2x  = 0X04,
  USB_CH341_PSF_NO_WAIT    = 0X80, // don't wait till there are 32 bytes
} USB_CH341_PrescalerFlags;

#define USB_CH341_DIVISOR_MINIMUM   2
#define USB_CH341_DIVISOR_MAXIMUM 256
#define USB_CH341_DIVISOR_MINUEND 256

typedef enum {
  USB_CH341_LCR1_RECEIVE_ENABLE  = 0X80,
  USB_CH341_LCR1_TRANSMIT_ENABLE = 0X40,

  USB_CH341_LCR1_PAR_BIT_STICK   = 0X20, // mark/space modifier
  USB_CH341_LCR1_PAR_BIT_EVEN    = 0X10,
  USB_CH341_LCR1_PAR_BIT_ENABLE  = 0X08,

  USB_CH341_LCR1_STOP_BITS_1     = 0X00,
  USB_CH341_LCR1_STOP_BITS_2     = 0X04,

  USB_CH341_LCR1_DATA_BITS_5     = 0X00,
  USB_CH341_LCR1_DATA_BITS_6     = 0X01,
  USB_CH341_LCR1_DATA_BITS_7     = 0X02,
  USB_CH341_LCR1_DATA_BITS_8     = 0X03,

  USB_CH341_LCR1_DATA_BITS_MASK  = 0X03,
  USB_CH341_LCR1_STOP_BITS_MASK  = USB_CH341_LCR1_STOP_BITS_1 | USB_CH341_LCR1_STOP_BITS_2,

  USB_CH341_LCR1_PARITY_MASK  = USB_CH341_LCR1_PAR_BIT_ENABLE | USB_CH341_LCR1_PAR_BIT_EVEN | USB_CH341_LCR1_PAR_BIT_STICK,
  USB_CH341_LCR1_PARITY_NONE  = 0X00,
  USB_CH341_LCR1_PARITY_ODD   = USB_CH341_LCR1_PAR_BIT_ENABLE,
  USB_CH341_LCR1_PARITY_EVEN  = USB_CH341_LCR1_PAR_BIT_ENABLE | USB_CH341_LCR1_PAR_BIT_EVEN,
  USB_CH341_LCR1_PARITY_MARK  = USB_CH341_LCR1_PAR_BIT_ENABLE | USB_CH341_LCR1_PAR_BIT_STICK,
  USB_CH341_LCR1_PARITY_SPACE = USB_CH341_LCR1_PAR_BIT_ENABLE | USB_CH341_LCR1_PAR_BIT_STICK | USB_CH341_LCR1_PAR_BIT_EVEN,
} USB_CH341_LineControlFlags1;

typedef enum {
  USB_CH341_MCR_DTR = 0X20, // data terminal ready
  USB_CH341_MCR_RTS = 0X40, // request to send
} USB_CH341_ModemControlFlags;

typedef enum {
  USB_CH341_MSR_CTS = 0X01, // clear to send
  USB_CH341_MSR_DSR = 0X02, // data set ready
  USB_CH341_MSR_RI  = 0X04, // ring indicator
  USB_CH341_MSR_DCD = 0X08, // data carrier detect
} USB_CH341_ModemStatusFlags;

typedef enum {
  USB_CH341_FLOW_RTSCTS = 0X01, // hardware flow control
} USB_CH341_FlowControlFlags;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_USB_CH341 */

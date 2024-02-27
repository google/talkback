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

#ifndef BRLTTY_INCLUDED_USB_CP2101
#define BRLTTY_INCLUDED_USB_CP2101

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  USB_CP2101_CTL_EnableInterface        = 0x00,
  USB_CP2101_CTL_SetBaudDivisor         = 0x01,
  USB_CP2101_CTL_GetBaudDivisor         = 0x02,
  USB_CP2101_CTL_SetLineControl         = 0x03,
  USB_CP2101_CTL_GetLineControl         = 0x04,
  USB_CP2101_CTL_SetBreak               = 0x05,
  USB_CP2101_CTL_SendImmediateCharacter = 0x06,
  USB_CP2101_CTL_SetModemHandShaking    = 0x07,
  USB_CP2101_CTL_GetModemStatus         = 0x08,
  USB_CP2101_CTL_SetXon                 = 0x09,
  USB_CP2101_CTL_SetXoff                = 0x0A,
  USB_CP2101_CTL_SetEventMask           = 0x0B,
  USB_CP2101_CTL_GetEventMask           = 0x0C,
  USB_CP2101_CTL_SetSpecialCharacter    = 0x0D,
  USB_CP2101_CTL_GetSpecialCharacters   = 0x0E,
  USB_CP2101_CTL_GetProperties          = 0x0F,
  USB_CP2101_CTL_GetSerialStatus        = 0x10,
  USB_CP2101_CTL_Reset                  = 0x11,
  USB_CP2101_CTL_Purge                  = 0x12,
  USB_CP2101_CTL_SetFlowControl         = 0x13,
  USB_CP2101_CTL_GetFlowControl         = 0x14,
  USB_CP2101_CTL_EmbedEvents            = 0x15,
  USB_CP2101_CTL_GetEventState          = 0x16,
  USB_CP2101_CTL_SetSpecialCharacters   = 0x19,
  USB_CP2101_CTL_GetBaudRate            = 0x1D,
  USB_CP2101_CTL_SetBaudRate            = 0x1E,
  USB_CP2101_CTL_VendorSpecific         = 0xFF
} USB_CP2101_ControlRequest;

typedef uint32_t USB_CP2101_BaudRate;
typedef uint16_t USB_CP2101_BaudDivisor;
#define USB_CP2101_BAUD_BASE 0X384000

typedef uint16_t USB_CP2101_LineControl;
#define USB_CP2101_STOP_SHIFT 0
#define USB_CP2101_STOP_WIDTH 4
#define USB_CP2101_PARITY_SHIFT 4
#define USB_CP2101_PARITY_WIDTH 4
#define USB_CP2101_DATA_SHIFT 8
#define USB_CP2101_DATA_WIDTH 8
#define USB_CP2101_DATA_MINIMUM 5
#define USB_CP2101_DATA_MAXIMUM 8


typedef enum {
  USB_CP2101_STOP_1,
  USB_CP2101_STOP_1_5,
  USB_CP2101_STOP_2
} USB_CP2101_StopBits;

typedef enum {
  USB_CP2101_PARITY_NONE,
  USB_CP2101_PARITY_ODD,
  USB_CP2101_PARITY_EVEN,
  USB_CP2101_PARITY_MARK,
  USB_CP2101_PARITY_SPACE
} USB_CP2101_Parity;

typedef struct {
  uint32_t handshakeOptions;
  uint32_t dataFlowOptions;
  uint32_t xonThreshold;
  uint32_t xoffThreshold;
} PACKED USB_CP2101_FlowControl;

typedef enum {
  USB_CP2101_FLOW_HSO_DTR_MASK       = 0X00000003, // DTR line usage
  USB_CP2101_FLOW_HSO_DTR_INACTIVE   = 0X00000000, // DTR is held inactive
  USB_CP2101_FLOW_HSO_DTR_ACTIVE     = 0X00000001, // DTR is held active
  USB_CP2101_FLOW_HSO_DTR_CONTROLLED = 0X00000002, // DTR is controlled

  USB_CP2101_FLOW_HSO_CTS_INTERPRET  = 0X00000008, // CTS is interpreted
  USB_CP2101_FLOW_HSO_DSR_INTERPRET  = 0X00000010, // DSR is interpreted
  USB_CP2101_FLOW_HSO_DCD_INTERPRET  = 0X00000020, // DCD is interpreted
  USB_CP2101_FLOW_HSO_DSR_DISCARD    = 0X00000040  // DSR low discards data
} USB_CP2101_ControlHandshake;

typedef enum {
  USB_CP2101_FLOW_DFO_AUTO_TRANSMIT   = 0X00000001, // respond to XON/XOFF from device
  USB_CP2101_FLOW_DFO_AUTO_RECEIVE    = 0X00000002, // send XON/XOFF to device
  USB_CP2101_FLOW_DFO_ERROR_CHARACTER = 0X00000004, // enable insertion of error special-character
  USB_CP2101_FLOW_DFO_STRIP_NULS      = 0X00000008, // discard received NUL characters
  USB_CP2101_FLOW_DFO_BREAK_CHARACTER = 0X00000010, // enable insertion of break special-character

  USB_CP2101_FLOW_DFO_RTS_MASK        = 0X000000C0, // RTS line usage
  USB_CP2101_FLOW_DFO_RTS_INACTIVE    = 0X00000000, // RTS is statically inactive
  USB_CP2101_FLOW_DFO_RTS_ACTIVE      = 0X00000040, // RTS is statically active
  USB_CP2101_FLOW_DFO_RTS_RCV_FLOW    = 0X00000080, // RTS is used for receive flow control
  USB_CP2101_FLOW_DFO_RTS_XMT_ACTIVE  = 0X000000C0, // RTS signals transmit active

  USB_CP2101_FLOW_DFO_AUTO_RCV_ALWAYS = 0X80000000  // send XON/XOFF to device even when suspended
} USB_CP2101_FlowReplace;

typedef struct {
  uint8_t eof;   // indicates end-of-file on input
  uint8_t error; // inserted when an error is detected
  uint8_t brk  ; // inserted when a break is detected
  uint8_t event; // sets bit 2 of the event-occurred mask
  uint8_t xon;   // sent to resume input
  uint8_t xoff;  // sent to suspend input
} PACKED USB_CP2101_SpecialCharacters;

typedef struct {
  uint32_t errors;
  uint32_t holdReasons;
  uint32_t inputCount;
  uint32_t outputCount;
  uint8_t eofReceived;
  uint8_t waitForImmediate;
  uint8_t reserved;
} PACKED USB_CP2101_SerialStatus;

typedef enum {
  USB_CP2101_SS_ERR_BREAK_SIGNAL     = 0X00000001,
  USB_CP2101_SS_ERR_FRAMING_ERROR    = 0X00000002,
  USB_CP2101_SS_ERR_HARDWARE_OVERRUN = 0X00000004,
  USB_CP2101_SS_ERR_QUEUE_OVERRUN    = 0X00000008,
  USB_CP2101_SS_ERR_PARITY_ERROR     = 0X00000010
} USB_CP2101_SerialError;

typedef enum {
  USB_CP2101_SS_HLD_XMT_CTS_WAIT  = 0X00000001,
  USB_CP2101_SS_HLD_XMT_DSR_WAIT  = 0X00000002,
  USB_CP2101_SS_HLD_XMT_DCD_WAIT  = 0X00000004,
  USB_CP2101_SS_HLD_XMT_XON_WAIT  = 0X00000008,
  USB_CP2101_SS_HLD_XMT_XOFF_SENT = 0X00000010,
  USB_CP2101_SS_HLD_XMT_BRK_WAIT  = 0X00000020,
  USB_CP2101_SS_HLD_RCV_DSR_WAIT  = 0X00000040,
} USB_CP2101_HoldReason;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_USB_CP2101 */

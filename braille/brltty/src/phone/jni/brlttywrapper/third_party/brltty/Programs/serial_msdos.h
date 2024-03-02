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

#ifndef BRLTTY_INCLUDED_SERIAL_MSDOS
#define BRLTTY_INCLUDED_SERIAL_MSDOS

#include "serial_uart.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  unsigned short divisor;
  unsigned short bps;
} SerialSpeed;

typedef union {
  unsigned char byte;

  struct {
    unsigned dataBits:2;
    unsigned stopBits:1;
    unsigned parity:2;
    unsigned bps:3;
  } fields;
} SerialBiosConfiguration;

typedef enum {
  SERIAL_BIOS_DATA_7 = 2,
  SERIAL_BIOS_DATA_8 = 3
} serialBiosDataBits;

typedef enum {
  SERIAL_BIOS_STOP_1 = 0,
  SERIAL_BIOS_STOP_2 = 1
} SerialBiosStopBits;

typedef enum {
  SERIAL_BIOS_PARITY_NONE = 0,
  SERIAL_BIOS_PARITY_ODD  = 1,
  SERIAL_BIOS_PARITY_EVEN = 3
} SerialBiosParity;

typedef enum {
  SERIAL_BIOS_BAUD_110 = 0,
  SERIAL_BIOS_BAUD_150,
  SERIAL_BIOS_BAUD_300,
  SERIAL_BIOS_BAUD_600,
  SERIAL_BIOS_BAUD_1200,
  SERIAL_BIOS_BAUD_2400,
  SERIAL_BIOS_BAUD_4800,
  SERIAL_BIOS_BAUD_9600,

  /* Do not reorder, add to, or delete from the preceding set of definitions
   * because their values are significant within the operating system.
   * The set defined below may be extended as needed.
   */

  SERIAL_BIOS_BAUD_19200,
  SERIAL_BIOS_BAUD_38400,
  SERIAL_BIOS_BAUD_57600,
  SERIAL_BIOS_BAUD_115200
} SerialBiosBaud;

typedef enum {
  SERIAL_BIOS_STATUS_CTS_CHANGE    = 0X0001,
  SERIAL_BIOS_STATUS_DSR_CHANGE    = 0X0002,
  SERIAL_BIOS_STATUS_RNG_CHANGE    = 0X0004,
  SERIAL_BIOS_STATUS_CAR_CHANGE    = 0X0008,

  SERIAL_BIOS_STATUS_CTS_PRESENT   = 0X0010,
  SERIAL_BIOS_STATUS_DSR_PRESENT   = 0X0020,
  SERIAL_BIOS_STATUS_RNG_PRESENT   = 0X0040,
  SERIAL_BIOS_STATUS_CAR_PRESENT   = 0X0080,

  SERIAL_BIOS_STATUS_DATA_READY    = 0X0100,
  SERIAL_BIOS_STATUS_OVERRUN_ERROR = 0X0200,
  SERIAL_BIOS_STATUS_PARITY_ERROR  = 0X0400,
  SERIAL_BIOS_STATUS_FRAMING_ERROR = 0X0800,

  SERIAL_BIOS_STATUS_BRK_DETECT    = 0X1000,
  SERIAL_BIOS_STATUS_THR_EMPTY     = 0X2000,
  SERIAL_BIOS_STATUS_TSR_EMPTY     = 0X4000,
  SERIAL_BIOS_STATUS_TIMEOUT       = 0X8000
} SerialBiosStatus;

typedef struct {
  SerialBiosConfiguration bios;
  SerialSpeed speed;
} SerialAttributes;

typedef unsigned char SerialLines;
#define SERIAL_LINE_DTR UART_FLAG_MCR_DTR
#define SERIAL_LINE_RTS UART_FLAG_MCR_RTS
#define SERIAL_LINE_CTS UART_FLAG_MSR_CTS
#define SERIAL_LINE_DSR UART_FLAG_MSR_DSR
#define SERIAL_LINE_RNG UART_FLAG_MSR_RNG
#define SERIAL_LINE_CAR UART_FLAG_MSR_CAR

typedef struct {
  int deviceIndex;
} SerialPackageFields;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SERIAL_MSDOS */

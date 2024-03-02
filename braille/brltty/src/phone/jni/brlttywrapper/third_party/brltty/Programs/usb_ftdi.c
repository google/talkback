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

#include "prologue.h"

#include <string.h>
#include <errno.h>

#include "log.h"
#include "usb_serial.h"
#include "usb_ftdi.h"

static int
usbInputFilter_FTDI (UsbInputFilterData *data) {
  return usbSkipInitialBytes(data, 2);
}

static int
usbSetAttribute_FTDI (UsbDevice *device, unsigned char request, unsigned int value, unsigned int index) {
  logMessage(LOG_CATEGORY(SERIAL_IO), "FTDI request: %02X %04X %04X", request, value, index);
  return usbControlWrite(device, UsbControlRecipient_Device, UsbControlType_Vendor,
                         request, value, index, NULL, 0, 1000) != -1;
}

static int
usbSetBaud_FTDI (UsbDevice *device, unsigned int divisor) {
  return usbSetAttribute_FTDI(device, 3, divisor&0XFFFF, divisor>>0X10);
}

static int
usbSetBaud_FTDI_SIO (UsbDevice *device, unsigned int baud) {
  unsigned int divisor;
  switch (baud) {
    case    300: divisor = 0; break;
    case    600: divisor = 1; break;
    case   1200: divisor = 2; break;
    case   2400: divisor = 3; break;
    case   4800: divisor = 4; break;
    case   9600: divisor = 5; break;
    case  19200: divisor = 6; break;
    case  38400: divisor = 7; break;
    case  57600: divisor = 8; break;
    case 115200: divisor = 9; break;
    default:
      logUnsupportedBaud(baud);
      errno = EINVAL;
      return 0;
  }
  return usbSetBaud_FTDI(device, divisor);
}

static int
usbSetBaud_FTDI_FT8U232AM (UsbDevice *device, unsigned int baud) {
  if (baud > 3000000) {
    logUnsupportedBaud(baud);
    errno = EINVAL;
    return 0;
  }
  {
    const unsigned int base = 48000000;
    unsigned int eighths = base / 2 / baud;
    unsigned int divisor;
    if ((eighths & 07) == 7) eighths++;
    divisor = eighths >> 3;
    divisor |= (eighths & 04)? 0X4000:
               (eighths & 02)? 0X8000:
               (eighths & 01)? 0XC000:
                               0X0000;
    if (divisor == 1) divisor = 0;
    return usbSetBaud_FTDI(device, divisor);
  }
}

static int
usbSetBaud_FTDI_FT232BM (UsbDevice *device, unsigned int baud) {
  if (baud > 3000000) {
    logUnsupportedBaud(baud);
    errno = EINVAL;
    return 0;
  }
  {
    static const unsigned char mask[8] = {00, 03, 02, 04, 01, 05, 06, 07};
    const unsigned int base = 48000000;
    const unsigned int eighths = base / 2 / baud;
    unsigned int divisor = (eighths >> 3) | (mask[eighths & 07] << 14);
    if (divisor == 1) {
      divisor = 0;
    } else if (divisor == 0X4001) {
      divisor = 1;
    }
    return usbSetBaud_FTDI(device, divisor);
  }
}

static int
usbSetFlowControl_FTDI (UsbDevice *device, SerialFlowControl flow) {
  unsigned int index = 0;
#define FTDI_FLOW(from,to) if ((flow & (from)) == (from)) flow &= ~(from), index |= (to)
  FTDI_FLOW(SERIAL_FLOW_OUTPUT_CTS|SERIAL_FLOW_INPUT_RTS, 0X0100);
  FTDI_FLOW(SERIAL_FLOW_OUTPUT_DSR|SERIAL_FLOW_INPUT_DTR, 0X0200);
  FTDI_FLOW(SERIAL_FLOW_OUTPUT_XON|SERIAL_FLOW_INPUT_XON, 0X0400);
#undef FTDI_FLOW
  if (flow) {
    logUnsupportedFlowControl(flow);
  }
  return usbSetAttribute_FTDI(device, 2, ((index & 0X0400)? 0X1311: 0), index);
}

static int
usbSetDataFormat_FTDI (UsbDevice *device, unsigned int dataBits, SerialStopBits stopBits, SerialParity parity) {
  int ok = 1;
  unsigned int value = dataBits & 0XFF;
  if (dataBits != value) {
    logUnsupportedDataBits(dataBits);
    ok = 0;
  }
  switch (parity) {
    case SERIAL_PARITY_NONE:  value |= 0X000; break;
    case SERIAL_PARITY_ODD:   value |= 0X100; break;
    case SERIAL_PARITY_EVEN:  value |= 0X200; break;
    case SERIAL_PARITY_MARK:  value |= 0X300; break;
    case SERIAL_PARITY_SPACE: value |= 0X400; break;
    default:
      logUnsupportedParity(parity);
      ok = 0;
      break;
  }
  switch (stopBits) {
    case SERIAL_STOP_1: value |= 0X0000; break;
    case SERIAL_STOP_2: value |= 0X1000; break;
    default:
      logUnsupportedStopBits(stopBits);
      ok = 0;
      break;
  }
  if (!ok) {
    errno = EINVAL;
    return 0;
  }
  return usbSetAttribute_FTDI(device, 4, value, 0);
}

static int
usbSetModemState_FTDI (UsbDevice *device, int state, int shift, const char *name) {
  if ((state < 0) || (state > 1)) {
    logMessage(LOG_WARNING, "Unsupported FTDI %s state: %d", name, state);
    errno = EINVAL;
    return 0;
  }
  return usbSetAttribute_FTDI(device, 1, ((1 << (shift + 8)) | (state << shift)), 0);
}

static int
usbSetDtrState_FTDI (UsbDevice *device, int state) {
  return usbSetModemState_FTDI(device, state, 0, "DTR");
}

static int
usbSetRtsState_FTDI (UsbDevice *device, int state) {
  return usbSetModemState_FTDI(device, state, 1, "RTS");
}

const UsbSerialOperations usbSerialOperations_FTDI_SIO = {
  .name = "FTDI_SIO",

  .setBaud = usbSetBaud_FTDI_SIO,
  .setDataFormat = usbSetDataFormat_FTDI,
  .setFlowControl = usbSetFlowControl_FTDI,

  .setDtrState = usbSetDtrState_FTDI,
  .setRtsState = usbSetRtsState_FTDI
};

const UsbSerialOperations usbSerialOperations_FTDI_FT8U232AM = {
  .name = "FTDI_FT8U232AM",

  .setBaud = usbSetBaud_FTDI_FT8U232AM,
  .setDataFormat = usbSetDataFormat_FTDI,
  .setFlowControl = usbSetFlowControl_FTDI,

  .setDtrState = usbSetDtrState_FTDI,
  .setRtsState = usbSetRtsState_FTDI,

  .inputFilter = usbInputFilter_FTDI
};

const UsbSerialOperations usbSerialOperations_FTDI_FT232BM = {
  .name = "FTDI_FT232BM",

  .setBaud = usbSetBaud_FTDI_FT232BM,
  .setDataFormat = usbSetDataFormat_FTDI,
  .setFlowControl = usbSetFlowControl_FTDI,

  .setDtrState = usbSetDtrState_FTDI,
  .setRtsState = usbSetRtsState_FTDI,

  .inputFilter = usbInputFilter_FTDI
};

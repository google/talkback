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

#include "prologue.h"

#include <string.h>
#include <errno.h>

#include "log.h"
#include "usb_serial.h"
#include "usb_belkin.h"

static int
usbSetAttribute_Belkin (UsbDevice *device, unsigned char request, unsigned int value, unsigned int index) {
  logMessage(LOG_CATEGORY(USB_IO), "Belkin request: %02X %04X %04X", request, value, index);
  return usbControlWrite(device, UsbControlRecipient_Device, UsbControlType_Vendor,
                         request, value, index, NULL, 0, 1000) != -1;
}

static int
usbSetBaud_Belkin (UsbDevice *device, unsigned int baud) {
  const unsigned int base = 230400;
  if (base % baud) {
    logMessage(LOG_WARNING, "unsupported Belkin baud: %u", baud);
    errno = EINVAL;
    return 0;
  }
  return usbSetAttribute_Belkin(device, 0, base/baud, 0);
}

static int
usbSetFlowControl_Belkin (UsbDevice *device, SerialFlowControl flow) {
  unsigned int value = 0;
#define BELKIN_FLOW(from,to) if ((flow & (from)) == (from)) flow &= ~(from), value |= (to)
  BELKIN_FLOW(SERIAL_FLOW_OUTPUT_CTS, 0X0001);
  BELKIN_FLOW(SERIAL_FLOW_OUTPUT_DSR, 0X0002);
  BELKIN_FLOW(SERIAL_FLOW_INPUT_DSR , 0X0004);
  BELKIN_FLOW(SERIAL_FLOW_INPUT_DTR , 0X0008);
  BELKIN_FLOW(SERIAL_FLOW_INPUT_RTS , 0X0010);
  BELKIN_FLOW(SERIAL_FLOW_OUTPUT_RTS, 0X0020);
  BELKIN_FLOW(SERIAL_FLOW_OUTPUT_XON, 0X0080);
  BELKIN_FLOW(SERIAL_FLOW_INPUT_XON , 0X0100);
#undef BELKIN_FLOW
  if (flow) {
    logMessage(LOG_WARNING, "unsupported Belkin flow control: %02X", flow);
  }
  return usbSetAttribute_Belkin(device, 16, value, 0);
}

static int
usbSetDataBits_Belkin (UsbDevice *device, unsigned int bits) {
  if ((bits < 5) || (bits > 8)) {
    logMessage(LOG_WARNING, "unsupported Belkin data bits: %u", bits);
    errno = EINVAL;
    return 0;
  }
  return usbSetAttribute_Belkin(device, 2, bits-5, 0);
}

static int
usbSetStopBits_Belkin (UsbDevice *device, SerialStopBits bits) {
  unsigned int value;
  switch (bits) {
    case SERIAL_STOP_1: value = 0; break;
    case SERIAL_STOP_2: value = 1; break;
    default:
      logMessage(LOG_WARNING, "unsupported Belkin stop bits: %u", bits);
      errno = EINVAL;
      return 0;
  }
  return usbSetAttribute_Belkin(device, 1, value, 0);
}

static int
usbSetParity_Belkin (UsbDevice *device, SerialParity parity) {
  unsigned int value;
  switch (parity) {
    case SERIAL_PARITY_SPACE: value = 4; break;
    case SERIAL_PARITY_ODD:   value = 2; break;
    case SERIAL_PARITY_EVEN:  value = 1; break;
    case SERIAL_PARITY_MARK:  value = 3; break;
    case SERIAL_PARITY_NONE:  value = 0; break;
    default:
      logMessage(LOG_WARNING, "unsupported Belkin parity: %u", parity);
      errno = EINVAL;
      return 0;
  }
  return usbSetAttribute_Belkin(device, 3, value, 0);
}

static int
usbSetDataFormat_Belkin (UsbDevice *device, unsigned int dataBits, SerialStopBits stopBits, SerialParity parity) {
  if (usbSetDataBits_Belkin(device, dataBits))
    if (usbSetStopBits_Belkin(device, stopBits))
      if (usbSetParity_Belkin(device, parity))
        return 1;
  return 0;
}

static int
usbSetDtrState_Belkin (UsbDevice *device, int state) {
  if ((state < 0) || (state > 1)) {
    logMessage(LOG_WARNING, "Unsupported Belkin DTR state: %d", state);
    errno = EINVAL;
    return 0;
  }
  return usbSetAttribute_Belkin(device, 10, state, 0);
}

static int
usbSetRtsState_Belkin (UsbDevice *device, int state) {
  if ((state < 0) || (state > 1)) {
    logMessage(LOG_WARNING, "Unsupported Belkin RTS state: %d", state);
    errno = EINVAL;
    return 0;
  }
  return usbSetAttribute_Belkin(device, 11, state, 0);
}

const UsbSerialOperations usbSerialOperations_Belkin = {
  .name = "Belkin",
  .setBaud = usbSetBaud_Belkin,
  .setDataFormat = usbSetDataFormat_Belkin,
  .setFlowControl = usbSetFlowControl_Belkin,
  .setDtrState = usbSetDtrState_Belkin,
  .setRtsState = usbSetRtsState_Belkin
};

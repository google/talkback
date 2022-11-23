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
#include "usb_cp2101.h"
#include "bitfield.h"

static size_t
usbGetProperty_CP2101 (UsbDevice *device, uint8_t request, void *data, size_t length) {
  ssize_t result;

  logMessage(LOG_CATEGORY(USB_IO), "getting CP2101 property: %02X", request);
  result = usbControlRead(device, UsbControlRecipient_Interface, UsbControlType_Vendor,
                          request, 0, 0, data, length, 1000);
  if (result == -1) return 0;

  if (result < length) {
    unsigned char *bytes = data;
    memset(&bytes[result], 0, length-result);
  }

  logBytes(LOG_CATEGORY(USB_IO), "CP2101 property input", data, result);
  return result;
}

static int
usbSetComplexProperty_CP2101 (
  UsbDevice *device,
  uint8_t request, uint16_t value,
  const void *data, size_t length
) {
  logMessage(LOG_CATEGORY(USB_IO), "setting CP2101 property: %02X %04X", request, value);
  if (length) logBytes(LOG_CATEGORY(USB_IO), "CP2101 property output", data, length);

  return usbControlWrite(device, UsbControlRecipient_Interface, UsbControlType_Vendor,
                         request, value, 0, data, length, 1000) != -1;
}

static int
usbSetSimpleProperty_CP2101 (UsbDevice *device, uint8_t request, uint16_t value) {
  return usbSetComplexProperty_CP2101(device, request, value, NULL, 0);
}

static int
usbVerifyBaudRate_CP2101 (UsbDevice *device, USB_CP2101_BaudRate expected) {
  USB_CP2101_BaudRate actual;
  ssize_t result;

  logMessage(LOG_CATEGORY(USB_IO), "verifying CP2101 baud rate");
  result = usbGetProperty_CP2101(device, USB_CP2101_CTL_GetBaudRate,
                                 &actual, sizeof(actual));

  if (result == -1) {
    logMessage(LOG_WARNING, "unable to get CP2101 baud rate: %s", strerror(errno));
  } else if (result != sizeof(actual)) {
    logMessage(LOG_WARNING, "unexpected CP2101 baud rate size: %d", (int)result);
  } else if (getLittleEndian32(actual) != expected) {
    logMessage(LOG_WARNING,
               "unexpected CP2101 baud rate value:"
               " Expected:%"PRIu32 " Actual:%"PRIu32,
               expected, getLittleEndian32(actual));
  } else {
    return 1;
  }

  return 0;
}

static int
usbVerifyBaudDivisor_CP2101 (UsbDevice *device, USB_CP2101_BaudDivisor expected) {
  USB_CP2101_BaudDivisor actual;
  ssize_t result;

  logMessage(LOG_CATEGORY(USB_IO), "verifying CP2101 baud divisor");
  result = usbGetProperty_CP2101(device, USB_CP2101_CTL_GetBaudDivisor,
                                 &actual, sizeof(actual));

  if (result == -1) {
    logMessage(LOG_WARNING, "unable to get CP2101 baud divisor: %s", strerror(errno));
  } else if (result != sizeof(actual)) {
    logMessage(LOG_WARNING, "unexpected CP2101 baud divisor size: %d", (int)result);
  } else if (getLittleEndian16(actual) != expected) {
    logMessage(LOG_WARNING,
               "unexpected CP2101 baud divisor value: Expected:%u Actual:%u",
               expected, getLittleEndian16(actual));
  } else {
    return 1;
  }

  return 0;
}

static int
usbVerifyBaud_CP2101 (UsbDevice *device, USB_CP2101_BaudRate rate, USB_CP2101_BaudDivisor divisor) {
  if (!usbVerifyBaudRate_CP2101(device, rate)) return 0;
  if (!usbVerifyBaudDivisor_CP2101(device, divisor)) return 0;
  return 1;
}

static int
usbSetBaud_CP2101 (UsbDevice *device, unsigned int baud) {
  USB_CP2101_BaudDivisor divisor = USB_CP2101_BAUD_BASE / baud;

  if ((baud * divisor) != USB_CP2101_BAUD_BASE) {
    logMessage(LOG_WARNING, "unsupported CP2101 baud: %u", baud);
    errno = EINVAL;
    return 0;
  }

  {
    USB_CP2101_BaudRate rate;

    logMessage(LOG_CATEGORY(USB_IO), "setting CP2101 baud rate: %u", baud);
    putLittleEndian32(&rate, baud);

    if (!usbSetComplexProperty_CP2101(device, USB_CP2101_CTL_SetBaudRate, 0,
                                      &rate, sizeof(rate))) {
      logMessage(LOG_WARNING, "unable to set CP2101 baud rate: %s", strerror(errno));
    } else if (usbVerifyBaud_CP2101(device, baud, divisor)) {
      return 1;
    }
  }

  {
    logMessage(LOG_CATEGORY(USB_IO), "setting CP2101 baud divisor: %u", divisor);

    if (!usbSetSimpleProperty_CP2101(device, USB_CP2101_CTL_SetBaudDivisor, divisor)) {
      logMessage(LOG_WARNING, "unable to set CP2101 baud divisor: %s", strerror(errno));
    } else if (usbVerifyBaud_CP2101(device, baud, divisor)) {
      return 1;
    }
  }

  return 0;
}

static int
usbSetModemState_CP2101 (UsbDevice *device, int state, int shift, const char *name) {
  if ((state < 0) || (state > 1)) {
    logMessage(LOG_WARNING, "unsupported CP2101 %s state: %d", name, state);
    errno = EINVAL;
    return 0;
  }

  logMessage(LOG_CATEGORY(USB_IO),
             "setting CP2101 %s state: %s",
             name, (state? "high": "low"));

  return usbSetSimpleProperty_CP2101(device, USB_CP2101_CTL_SetModemHandShaking, ((1 << (shift + 8)) | (state << shift)));
}

static int
usbSetDtrState_CP2101 (UsbDevice *device, int state) {
  return usbSetModemState_CP2101(device, state, 0, "DTR");
}

static int
usbSetRtsState_CP2101 (UsbDevice *device, int state) {
  return usbSetModemState_CP2101(device, state, 1, "RTS");
}

static int
usbVerifyFlowControl_CP2101 (UsbDevice *device, const USB_CP2101_FlowControl *expected, size_t size) {
  USB_CP2101_FlowControl actual;
  ssize_t result;

  logMessage(LOG_CATEGORY(USB_IO), "verifying CP2101 flow control");
  result = usbGetProperty_CP2101(device, USB_CP2101_CTL_GetFlowControl,
                                 &actual, sizeof(actual));

  if (result == -1) {
    logMessage(LOG_WARNING, "unable to get CP2101 flow control: %s", strerror(errno));
  } else if (result != size) {
    logMessage(LOG_WARNING, "unexpected CP2101 flow control size: %d", (int)result);
  } else if (memcmp(&actual, expected, size) != 0) {
    logMessage(LOG_WARNING, "unexpected CP2101 flow control data");
    logBytes(LOG_WARNING, "expected flow control", expected, size);
    logBytes(LOG_WARNING, "actual flow control", &actual, size);
  } else {
    return 1;
  }

  return 0;
}

static int
usbSetFlowControl_CP2101 (UsbDevice *device, SerialFlowControl flow) {
  USB_CP2101_FlowControl oldSettings;
  USB_CP2101_FlowControl newSettings;
  size_t size;

  logMessage(LOG_CATEGORY(USB_IO), "getting CP2101 flow control");
  size = usbGetProperty_CP2101(device, USB_CP2101_CTL_GetFlowControl,
                               &oldSettings, sizeof(oldSettings));

  if (!size) {
    logMessage(LOG_WARNING, "unable to get CP2101 flow control");
    return 0;
  }

  newSettings = oldSettings;
  newSettings.handshakeOptions = getLittleEndian32(newSettings.handshakeOptions);
  newSettings.dataFlowOptions = getLittleEndian32(newSettings.dataFlowOptions);

  newSettings.handshakeOptions &= ~USB_CP2101_FLOW_HSO_DTR_MASK;
  newSettings.handshakeOptions |= USB_CP2101_FLOW_HSO_DTR_ACTIVE;

  if (flow & SERIAL_FLOW_OUTPUT_CTS) {
    flow &= ~SERIAL_FLOW_OUTPUT_CTS;
    newSettings.handshakeOptions |= USB_CP2101_FLOW_HSO_CTS_INTERPRET;
  } else {
    newSettings.handshakeOptions &= ~USB_CP2101_FLOW_HSO_CTS_INTERPRET;
  }

  if (flow & SERIAL_FLOW_OUTPUT_RTS) {
    flow &= ~SERIAL_FLOW_OUTPUT_RTS;
    newSettings.dataFlowOptions &= ~USB_CP2101_FLOW_DFO_RTS_MASK;
    newSettings.dataFlowOptions |= USB_CP2101_FLOW_DFO_RTS_XMT_ACTIVE;
  } else {
    newSettings.dataFlowOptions &= ~USB_CP2101_FLOW_DFO_RTS_MASK;
    newSettings.dataFlowOptions |= USB_CP2101_FLOW_DFO_RTS_ACTIVE;
  }

  if (flow & SERIAL_FLOW_OUTPUT_XON) {
    flow &= ~SERIAL_FLOW_OUTPUT_XON;
    newSettings.dataFlowOptions |= USB_CP2101_FLOW_DFO_AUTO_TRANSMIT;
  } else {
    newSettings.dataFlowOptions &= ~USB_CP2101_FLOW_DFO_AUTO_TRANSMIT;
  }

  if (flow & SERIAL_FLOW_INPUT_XON) {
    flow &= ~SERIAL_FLOW_INPUT_XON;
    newSettings.dataFlowOptions |= USB_CP2101_FLOW_DFO_AUTO_RECEIVE;
  } else {
    newSettings.dataFlowOptions &= ~USB_CP2101_FLOW_DFO_AUTO_RECEIVE;
  }

  putLittleEndian32(&newSettings.handshakeOptions, newSettings.handshakeOptions);
  putLittleEndian32(&newSettings.dataFlowOptions, newSettings.dataFlowOptions);

  if (flow) {
    logMessage(LOG_WARNING, "unsupported CP2101 flow control: %02X", flow);
    errno = EINVAL;
    return 0;
  }

  if (memcmp(&newSettings, &oldSettings, size) == 0) {
    logMessage(LOG_CATEGORY(USB_IO), "CP2101 flow control unchanged");
  }

  logMessage(LOG_CATEGORY(USB_IO), "setting CP2101 flow control");

  if (!usbSetComplexProperty_CP2101(device, USB_CP2101_CTL_SetFlowControl, 0,
                                    &newSettings, size)) {
    logMessage(LOG_WARNING, "unable to set CP2101 flow control: %s", strerror(errno));
  } else if (usbVerifyFlowControl_CP2101(device, &newSettings, size)) {
    return 1;
  }

  return 0;
}

static int
usbVerifyLineControl_CP2101 (UsbDevice *device, USB_CP2101_LineControl expected) {
  USB_CP2101_LineControl actual;
  ssize_t result;

  logMessage(LOG_CATEGORY(USB_IO), "verifying CP2101 line control");
  result = usbGetProperty_CP2101(device, USB_CP2101_CTL_GetLineControl,
                                 &actual, sizeof(actual));

  if (result == -1) {
    logMessage(LOG_WARNING, "unable to get CP2101 line control: %s", strerror(errno));
  } else if (result != sizeof(actual)) {
    logMessage(LOG_WARNING, "unexpected CP2101 line control size: %d", (int)result);
  } else if (getLittleEndian16(actual) != expected) {
    logMessage(LOG_WARNING,
               "unexpected CP2101 line control value: Expected:0X%04X Actual:0X%04X",
               expected, getLittleEndian16(actual));
  } else {
    return 1;
  }

  return 0;
}

static int
usbSetDataFormat_CP2101 (UsbDevice *device, unsigned int dataBits, SerialStopBits stopBits, SerialParity parity) {
  int ok = 1;
  USB_CP2101_LineControl lineControl = 0;

  {
    USB_CP2101_LineControl value;

    if ((dataBits >= USB_CP2101_DATA_MINIMUM) &&
        (dataBits <= USB_CP2101_DATA_MAXIMUM)) {
      value = dataBits;
    } else {
      logMessage(LOG_WARNING, "unsupported CP2101 data bits: %u", dataBits);
      ok = 0;
      value = 8;
    }

    lineControl |= value << USB_CP2101_DATA_SHIFT;
  }

  {
    USB_CP2101_LineControl value;

    switch (parity) {
      case SERIAL_PARITY_NONE:  value = USB_CP2101_PARITY_NONE;  break;
      case SERIAL_PARITY_ODD:   value = USB_CP2101_PARITY_ODD;   break;
      case SERIAL_PARITY_EVEN:  value = USB_CP2101_PARITY_EVEN;  break;
      case SERIAL_PARITY_MARK:  value = USB_CP2101_PARITY_MARK;  break;
      case SERIAL_PARITY_SPACE: value = USB_CP2101_PARITY_SPACE; break;

      default:
        logMessage(LOG_WARNING, "unsupported CP2101 parity: %u", parity);
        ok = 0;
        value = USB_CP2101_PARITY_NONE;
        break;
    }

    lineControl |= value << USB_CP2101_PARITY_SHIFT;
  }

  {
    USB_CP2101_LineControl value;

    switch (stopBits) {
      case SERIAL_STOP_1:   value = USB_CP2101_STOP_1;   break;
      case SERIAL_STOP_1_5: value = USB_CP2101_STOP_1_5; break;
      case SERIAL_STOP_2:   value = USB_CP2101_STOP_2;   break;

      default:
        logMessage(LOG_WARNING, "unsupported CP2101 stop bits: %u", stopBits);
        ok = 0;
        value = USB_CP2101_STOP_1;
        break;
    }

    lineControl |= value << USB_CP2101_STOP_SHIFT;
  }

  if (ok) {
    logMessage(LOG_CATEGORY(USB_IO), "setting CP2101 line control: 0X%04X", lineControl);

    if (!usbSetSimpleProperty_CP2101(device, USB_CP2101_CTL_SetLineControl, lineControl)) {
      logMessage(LOG_WARNING, "unable to set CP2101 line control: %0X04X", lineControl);
    } else if (usbVerifyLineControl_CP2101(device, lineControl)) {
      return 1;
    }
  }

  errno = EINVAL;
  return 0;
}

static int
usbSetInterfaceState_CP2101 (UsbDevice *device, int state) {
  logMessage(LOG_CATEGORY(USB_IO),
             "setting CP2101 interface state: %s",
             (state? "enabled": "disabled"));

  return usbSetSimpleProperty_CP2101(device, USB_CP2101_CTL_EnableInterface, state);
}

static int
usbEnableAdapter_CP2101 (UsbDevice *device) {
  if (!usbSetInterfaceState_CP2101(device, 0)) return 0;
  if (!usbSetInterfaceState_CP2101(device, 1)) return 0;

  return 1;
}

const UsbSerialOperations usbSerialOperations_CP2101 = {
  .name = "CP2101",     
  .setBaud = usbSetBaud_CP2101,
  .setDataFormat = usbSetDataFormat_CP2101,
  .setFlowControl = usbSetFlowControl_CP2101,
  .setDtrState = usbSetDtrState_CP2101,
  .setRtsState = usbSetRtsState_CP2101,
  .enableAdapter = usbEnableAdapter_CP2101
};

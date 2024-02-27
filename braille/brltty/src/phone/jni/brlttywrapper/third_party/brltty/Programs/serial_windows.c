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

#include <errno.h>
#include <io.h>
#include <fcntl.h>

#include "log.h"
#include "ascii.h"

#include "serial_windows.h"
#include "serial_internal.h"

BEGIN_SERIAL_BAUD_TABLE
#ifdef CBR_110
  {110, CBR_110},
#endif /* CBR_110 */

#ifdef CBR_300
  {300, CBR_300},
#endif /* CBR_300 */

#ifdef CBR_600
  {600, CBR_600},
#endif /* CBR_600 */

#ifdef CBR_1200
  {1200, CBR_1200},
#endif /* CBR_1200 */

#ifdef CBR_2400
  {2400, CBR_2400},
#endif /* CBR_2400 */

#ifdef CBR_4800
  {4800, CBR_4800},
#endif /* CBR_4800 */

#ifdef CBR_9600
  {9600, CBR_9600},
#endif /* CBR_9600 */

#ifdef CBR_14400
  {14400, CBR_14400},
#endif /* CBR_14400 */

#ifdef CBR_19200
  {19200, CBR_19200},
#endif /* CBR_19200 */

#ifdef CBR_38400
  {38400, CBR_38400},
#endif /* CBR_38400 */

#ifdef CBR_56000
  {56000, CBR_56000},
#endif /* CBR_56000 */

#ifdef CBR_57600
  {57600, CBR_57600},
#endif /* CBR_57600 */

#ifdef CBR_115200
  {115200, CBR_115200},
#endif /* CBR_115200 */

#ifdef CBR_128000
  {128000, CBR_128000},
#endif /* CBR_128000 */

#ifdef CBR_256000
  {256000, CBR_256000},
#endif /* CBR_256000 */
END_SERIAL_BAUD_TABLE

void
serialPutInitialAttributes (SerialAttributes *attributes) {
  attributes->DCBlength = sizeof(*attributes);
  attributes->fBinary = TRUE;
  attributes->fTXContinueOnXoff = TRUE;
  attributes->XonChar = ASCII_DC1;
  attributes->XoffChar = ASCII_DC3;
}

int
serialPutSpeed (SerialAttributes *attributes, SerialSpeed speed) {
  attributes->BaudRate = speed;
  return 1;
}

int
serialPutDataBits (SerialAttributes *attributes, unsigned int bits) {
  if ((bits < 5) || (bits > 8)) return 0;
  attributes->ByteSize = bits;
  return 1;
}

int
serialPutStopBits (SerialAttributes *attributes, SerialStopBits bits) {
  if (bits == SERIAL_STOP_1) {
    attributes->StopBits = ONESTOPBIT;
  } else if (bits == SERIAL_STOP_1_5) {
    attributes->StopBits = ONE5STOPBITS;
  } else if (bits == SERIAL_STOP_2) {
    attributes->StopBits = TWOSTOPBITS;
  } else {
    return 0;
  }

  return 1;
}

int
serialPutParity (SerialAttributes *attributes, SerialParity parity) {
  attributes->fParity = FALSE;
  attributes->Parity = NOPARITY;

  if (parity != SERIAL_PARITY_NONE) {
    switch (parity) {
      case SERIAL_PARITY_ODD:
        attributes->Parity = ODDPARITY;
        break;

      case SERIAL_PARITY_EVEN:
        attributes->Parity = EVENPARITY;
        break;

      case SERIAL_PARITY_MARK:
        attributes->Parity = MARKPARITY;
        break;

      case SERIAL_PARITY_SPACE:
        attributes->Parity = SPACEPARITY;
        break;

      default:
        return 0;
    }

    attributes->fParity = TRUE;
  }

  return 1;
}

SerialFlowControl
serialPutFlowControl (SerialAttributes *attributes, SerialFlowControl flow) {
  if (flow & SERIAL_FLOW_OUTPUT_RTS) {
    flow &= ~SERIAL_FLOW_OUTPUT_RTS;
    attributes->fRtsControl = RTS_CONTROL_TOGGLE;
  } else if (flow & SERIAL_FLOW_INPUT_RTS) {
    flow &= ~SERIAL_FLOW_INPUT_RTS;
    attributes->fRtsControl = RTS_CONTROL_HANDSHAKE;
  } else {
    attributes->fRtsControl = RTS_CONTROL_ENABLE;
  }

  if (flow & SERIAL_FLOW_INPUT_XON) {
    flow &= ~SERIAL_FLOW_INPUT_XON;
    attributes->fInX = TRUE;
  } else {
    attributes->fInX = FALSE;
  }

  if (flow & SERIAL_FLOW_OUTPUT_CTS) {
    flow &= ~SERIAL_FLOW_OUTPUT_CTS;
    attributes->fOutxCtsFlow = TRUE;
  } else {
    attributes->fOutxCtsFlow = FALSE;
  }

  if (flow & SERIAL_FLOW_OUTPUT_DSR) {
    flow &= ~SERIAL_FLOW_OUTPUT_DSR;
    attributes->fOutxDsrFlow = TRUE;
  } else {
    attributes->fOutxDsrFlow = FALSE;
  }

  if (flow & SERIAL_FLOW_OUTPUT_XON) {
    flow &= ~SERIAL_FLOW_OUTPUT_XON;
    attributes->fOutX = TRUE;
  } else {
    attributes->fOutX = FALSE;
  }

  return flow;
}

int
serialPutModemState (SerialAttributes *attributes, int enabled) {
  if (enabled) {
    attributes->fDtrControl = DTR_CONTROL_HANDSHAKE;
    attributes->fDsrSensitivity = TRUE;
  } else {
    attributes->fDtrControl = DTR_CONTROL_ENABLE;
    attributes->fDsrSensitivity = FALSE;
  }

  return 1;
}

unsigned int
serialGetDataBits (const SerialAttributes *attributes) {
  return attributes->ByteSize;
}

unsigned int
serialGetStopBits (const SerialAttributes *attributes) {
  if (attributes->StopBits == ONESTOPBIT) return 1;
  if (attributes->StopBits == TWOSTOPBITS) return 2;

  logMessage(LOG_WARNING, "unsupported Windows serial stop bits value: %X", attributes->StopBits);
  return 0;
}

unsigned int
serialGetParityBits (const SerialAttributes *attributes) {
  return (attributes->fParity && (attributes->Parity != NOPARITY))? 1: 0;
}

int
serialGetAttributes (SerialDevice *serial, SerialAttributes *attributes) {
  attributes->DCBlength = sizeof(serial->currentAttributes);
  if (GetCommState(serial->package.fileHandle, attributes)) return 1;
  logWindowsSystemError("GetCommState");
  return 0;
}

int
serialPutAttributes (SerialDevice *serial, const SerialAttributes *attributes) {
  if (SetCommState(serial->package.fileHandle, (SerialAttributes *)attributes)) return 1;
  logWindowsSystemError("SetCommState");
  return 0;
}

int
serialCancelInput (SerialDevice *serial) {
  if (PurgeComm(serial->package.fileHandle, PURGE_RXCLEAR)) return 1;
  logWindowsSystemError("PurgeComm");
  return 0;
}

int
serialCancelOutput (SerialDevice *serial) {
  if (PurgeComm(serial->package.fileHandle, PURGE_TXCLEAR)) return 1;
  logWindowsSystemError("PurgeComm");
  return 0;
}

int
serialMonitorInput (SerialDevice *serial, AsyncMonitorCallback *callback, void *data) {
  return 0;
}

int
serialPollInput (SerialDevice *serial, int timeout) {
  if (serial->package.pendingCharacter != -1) return 1;

  {
    COMMTIMEOUTS timeouts = {MAXDWORD, 0, timeout, 0, 0};
    DWORD bytesRead;
    char c;

    if (!(SetCommTimeouts(serial->package.fileHandle, &timeouts))) {
      logWindowsSystemError("SetCommTimeouts serialAwaitInput");
      setSystemErrno();
      return 0;
    }

    if (!ReadFile(serial->package.fileHandle, &c, 1, &bytesRead, NULL)) {
      logWindowsSystemError("ReadFile");
      setSystemErrno();
      return 0;
    }

    if (bytesRead) {
      serial->package.pendingCharacter = (unsigned char)c;
      return 1;
    }
  }
  errno = EAGAIN;

  return 0;
}

int
serialDrainOutput (SerialDevice *serial) {
  if (FlushFileBuffers(serial->package.fileHandle)) return 1;
  logWindowsSystemError("FlushFileBuffers");
  return 0;
}

ssize_t
serialGetData (
  SerialDevice *serial,
  void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  size_t length = 0;
  COMMTIMEOUTS timeouts = {MAXDWORD, 0, initialTimeout, 0, 0};
  DWORD bytesRead;

  if (serial->package.pendingCharacter != -1) {
    * (unsigned char *) buffer = serial->package.pendingCharacter;
    serial->package.pendingCharacter = -1;
    bytesRead = 1;
  } else {
    if (!(SetCommTimeouts(serial->package.fileHandle, &timeouts))) {
      logWindowsSystemError("SetCommTimeouts serialReadChunk1");
      setSystemErrno();
      return -1;
    }

    if (!ReadFile(serial->package.fileHandle, buffer, size, &bytesRead, NULL)) {
      logWindowsSystemError("ReadFile");
      setSystemErrno();
      return -1;
    }

    if (!bytesRead) return 0;
  }

  size -= bytesRead;
  length += bytesRead;
  timeouts.ReadTotalTimeoutConstant = subsequentTimeout;

  if (!(SetCommTimeouts(serial->package.fileHandle, &timeouts))) {
    logWindowsSystemError("SetCommTimeouts serialReadChunk2");
    setSystemErrno();
    return -1;
  }

  while (size && ReadFile(serial->package.fileHandle, buffer + length, size, &bytesRead, NULL)) {
    if (!bytesRead) return length;
    size -= bytesRead;
    length += bytesRead;
  }

  if (!size) return length;
  logWindowsSystemError("ReadFile");
  setSystemErrno();
  return -1;
}

ssize_t
serialPutData (
  SerialDevice *serial,
  const void *data, size_t size
) {
  COMMTIMEOUTS timeouts = {MAXDWORD, 0, 0, 0, 15000};
  size_t left = size;
  DWORD bytesWritten;

  if (!(SetCommTimeouts(serial->package.fileHandle, &timeouts))) {
    logWindowsSystemError("SetCommTimeouts serialWriteData");
    setSystemErrno();
    return -1;
  }

  while (left && WriteFile(serial->package.fileHandle, data, left, &bytesWritten, NULL)) {
    if (!bytesWritten) break;
    left -= bytesWritten;
    data += bytesWritten;
  }

  if (!left) return size;
  logWindowsSystemError("WriteFile");
  return -1;
}

int
serialGetLines (SerialDevice *serial) {
  if (!GetCommModemStatus(serial->package.fileHandle, &serial->linesState)) {
    logWindowsSystemError("GetCommModemStatus");
    return 0;
  }

  {
    DCB dcb;
    dcb.DCBlength = sizeof(dcb);

    if (!GetCommState(serial->package.fileHandle, &dcb)) {
      logWindowsSystemError("GetCommState");
      return 0;
    }

    if (dcb.fRtsControl == RTS_CONTROL_ENABLE) serial->linesState |= SERIAL_LINE_RTS;
    if (dcb.fDtrControl == DTR_CONTROL_ENABLE) serial->linesState |= SERIAL_LINE_DTR;
  }

  return 1;
}

int
serialPutLines (SerialDevice *serial, SerialLines high, SerialLines low) {
  DCB dcb;
  dcb.DCBlength = sizeof(dcb);

  if (GetCommState(serial->package.fileHandle, &dcb)) {
    if (low & SERIAL_LINE_RTS) {
      dcb.fRtsControl = RTS_CONTROL_DISABLE;
    } else if (high & SERIAL_LINE_RTS) {
      dcb.fRtsControl = RTS_CONTROL_ENABLE;
    }

    if (low & SERIAL_LINE_DTR) {
      dcb.fDtrControl = DTR_CONTROL_DISABLE;
    } else if (high & SERIAL_LINE_DTR) {
      dcb.fDtrControl = DTR_CONTROL_ENABLE;
    }

    if (SetCommState(serial->package.fileHandle, &dcb)) return 1;
    logWindowsSystemError("SetCommState");
  } else {
    logWindowsSystemError("GetCommState");
  }

  return 0;
}

int
serialRegisterWaitLines (SerialDevice *serial, SerialLines lines) {
  DWORD eventMask = 0;

  if (lines & SERIAL_LINE_CTS) eventMask |= EV_CTS;
  if (lines & SERIAL_LINE_DSR) eventMask |= EV_DSR;
  if (lines & SERIAL_LINE_RNG) eventMask |= EV_RING;
  if (lines & SERIAL_LINE_CAR) eventMask |= EV_RLSD;

  if (SetCommMask(serial->package.fileHandle, eventMask)) return 1;
  logWindowsSystemError("SetCommMask");
  return 0;
}

int
serialMonitorWaitLines (SerialDevice *serial) {
  DWORD event;

  if (WaitCommEvent(serial->package.fileHandle, &event, NULL)) return 1;
  logWindowsSystemError("WaitCommEvent");
  return 0;
}

int
serialConnectDevice (SerialDevice *serial, const char *device) {
  if ((serial->package.fileHandle = CreateFile(device, GENERIC_READ|GENERIC_WRITE, 0, NULL, OPEN_EXISTING, 0, NULL)) != INVALID_HANDLE_VALUE) {
    serial->package.pendingCharacter = -1;

    if (serialPrepareDevice(serial)) {
      logMessage(LOG_CATEGORY(SERIAL_IO), "device opened: %s: fh=%" PRIfd,
                 device, serial->package.fileHandle);
      return 1;
    }

    CloseHandle(serial->package.fileHandle);
  } else {
    logWindowsSystemError("CreateFile");
    logMessage(LOG_ERR, "cannot open serial device: %s", device);
  }

  return 0;
}

void
serialDisconnectDevice (SerialDevice *serial) {
  CloseHandle(serial->package.fileHandle);
}

int
serialEnsureFileDescriptor (SerialDevice *serial) {
#ifdef __CYGWIN__
  if ((serial->fileDescriptor = cygwin_attach_handle_to_fd("serialdevice", -1, serial->package.fileHandle, TRUE, GENERIC_READ|GENERIC_WRITE)) >= 0) return 1;
  logSystemError("cygwin_attach_handle_to_fd");
#else /* __CYGWIN__ */
  if ((serial->fileDescriptor = _open_osfhandle((long)serial->package.fileHandle, O_RDWR)) >= 0) return 1;
  logSystemError("open_osfhandle");
#endif /* __CYGWIN__ */

  return 0;
}

void
serialClearError (SerialDevice *serial) {
  ClearCommError(serial->package.fileHandle, NULL, NULL);
}


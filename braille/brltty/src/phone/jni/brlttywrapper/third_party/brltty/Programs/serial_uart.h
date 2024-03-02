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

#ifndef BRLTTY_INCLUDED_SERIAL_UART
#define BRLTTY_INCLUDED_SERIAL_UART

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define UART_PORT_RBR 0 /* receive buffered register */
#define UART_PORT_THR 0 /* transmit holding register */
#define UART_PORT_DLL 0 /* divisor latch low */
#define UART_PORT_IER 1 /* interrupt enable register */
#define UART_PORT_DLH 1 /* divisor latch high */
#define UART_PORT_IIR 2 /* interrupt id register */
#define UART_PORT_LCR 3 /* line control register */
#define UART_PORT_MCR 4 /* modem control register */
#define UART_PORT_MSR 6 /* modem status register */

#define UART_FLAG_LCR_DLAB 0X80 /* divisor latch access bit */

#define UART_FLAG_MCR_DTR 0X01 /* data terminal ready */
#define UART_FLAG_MCR_RTS 0X02 /* ready to send */

#define UART_FLAG_MSR_CTS 0X10 /* clear to send */
#define UART_FLAG_MSR_DSR 0X20 /* data set ready */
#define UART_FLAG_MSR_RNG 0X40 /* ring indicator */
#define UART_FLAG_MSR_CAR 0X80 /* carrier detect */

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SERIAL_UART */

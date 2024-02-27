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

#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/param.h>

#ifdef HAVE_LEGACY_DEV_USB_USB_H
#include <legacy/dev/usb/usb.h>
#else /* HAVE_LEGACY_DEV_USB_USB_H */
#include <dev/usb/usb.h>
#endif /* HAVE_LEGACY_DEV_USB_USB_H */

#include "io_usb.h"
#include "usb_internal.h"

#define USB_CONTROL_PATH_FORMAT "/dev/%s"
#define USB_ENDPOINT_PATH_FORMAT "%.*s.%d"
#include "usb_bsd.h"

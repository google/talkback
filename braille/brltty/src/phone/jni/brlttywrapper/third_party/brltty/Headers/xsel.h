/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 2019 by Samuel Thibault <Samuel.Thibault@ens-lyon.org>
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

#ifndef BRLAPI_INCLUDED_XSEL
#define BRLAPI_INCLUDED_XSEL

#include <X11/X.h>
#include <X11/Xlib.h>

#include "prologue.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  Atom sel, selProp;
  Window selWindow;

  Atom targetsAtom, utf8, incr;

#ifdef HAVE_X11_EXTENSIONS_XFIXES_H
  int xfixesEventBase, xfixesErrorBase;
  Bool haveXfixes;
#endif /* HAVE_X11_EXTENSIONS_XFIXES_H */
} XSelData;

typedef void (*XSelUpdate)(const char *data, unsigned long size);

void XSelInit(Display *dpy, XSelData *data);
void XSelSet(Display *dpy, XSelData *data);
int XSelProcess(Display *dpy, XSelData *data, XEvent *ev, const char *content, XSelUpdate update);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLAPI_INCLUDED_XSEL */

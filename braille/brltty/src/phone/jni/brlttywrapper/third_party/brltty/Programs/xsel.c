/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 2019-2023 by Samuel Thibault <Samuel.Thibault@ens-lyon.org>
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

#include "xsel.h"
#include <X11/Xatom.h>

#ifdef HAVE_X11_EXTENSIONS_XFIXES_H
#include <X11/extensions/Xfixes.h>
#else /* HAVE_X11_EXTENSIONS_XFIXES_H */
#warning clipboard tracking not supported by this build - check that libxfixes has been installed
#endif /* HAVE_X11_EXTENSIONS_XFIXES_H */

/* TODO: get initial clipboard value on startup */

void XSelInit(Display *dpy, XSelData *data) {
  data->sel = XInternAtom(dpy, "CLIPBOARD", False);
  data->selProp = XInternAtom(dpy, "BRLTTY_CLIPBOARD", False);
  data->incr = XInternAtom(dpy, "INCR", False);
  data->utf8 = XInternAtom(dpy,"UTF8_STRING",False);
  data->targetsAtom = XInternAtom(dpy,"TARGETS",False);
  data->selWindow = XCreateSimpleWindow(dpy, RootWindow(dpy, DefaultScreen(dpy)), -10, -10, 1, 1, 0, 0, 0);

#ifdef HAVE_X11_EXTENSIONS_XFIXES_H
  data->haveXfixes = XFixesQueryExtension(dpy, &data->xfixesEventBase, &data->xfixesErrorBase);
  if (data->haveXfixes) {
    XFixesSelectSelectionInput(dpy, data->selWindow, data->sel, XFixesSetSelectionOwnerNotifyMask);
  }
#endif /* HAVE_X11_EXTENSIONS_XFIXES_H */

}

void XSelSet(Display *dpy, XSelData *data) {
  XSetSelectionOwner(dpy, data->sel, data->selWindow, CurrentTime);
  XFlush(dpy);
}

int XSelProcess(Display *dpy, XSelData *data, XEvent *ev, const char *content, XSelUpdate update) {
#ifdef HAVE_X11_EXTENSIONS_XFIXES_H
  if (data->haveXfixes && ev->type == data->xfixesEventBase + XFixesSelectionNotify) {
    XFixesSelectionNotifyEvent *xfEvent = (XFixesSelectionNotifyEvent *) ev;
    if (xfEvent->subtype == XFixesSetSelectionOwnerNotify &&
	xfEvent->selection == data->sel &&
	xfEvent->owner != None &&
	xfEvent->owner != data->selWindow) {
      /* TODO: use TARGETS to support non-utf8 clients */
      XConvertSelection(dpy, data->sel, data->utf8, data->selProp, data->selWindow, xfEvent->selection_timestamp);
    }
    return 1;
  } else
#endif /* HAVE_X11_EXTENSIONS_XFIXES_H */

  switch (ev->type) {
    case SelectionNotify:
      if (ev->xselection.property != None) {
	Atom type;
	int format;
	unsigned long nitems, size, ignore;
	unsigned char *prop_ret;
	XGetWindowProperty(dpy, data->selWindow, data->selProp, 0, 0, False, AnyPropertyType, &type, &format, &nitems, &size, &prop_ret);
	XFree(prop_ret);
	if (type == data->incr) {
	  // large data, but INCR not supported yet
	} else if (size != 0) {
	  XGetWindowProperty(dpy, data->selWindow, data->selProp, 0, size, False, AnyPropertyType, &type, &format, &nitems, &ignore, &prop_ret);

	  update((char*) prop_ret, size);

	  XFree(prop_ret);
	  XDeleteProperty(dpy, data->selWindow, data->selProp);
	}
      }
      return 1;
    case SelectionClear:
      update(NULL, 0);
      return 1;
    case SelectionRequest: {
      XSelectionEvent sev;
      XSelectionRequestEvent *srev = (XSelectionRequestEvent*)&ev->xselectionrequest;
      if (content && srev->target == data->utf8) {
	XChangeProperty(dpy, srev->requestor, srev->property, data->utf8, 8, PropModeReplace, (unsigned char*) content, strlen(content));
	sev.property = srev->property;
      } else if (srev->target == data->targetsAtom) {
	Atom targets[] = { data->targetsAtom, data->utf8 };
	XChangeProperty(dpy, srev->requestor, srev->property, XA_ATOM, 32, PropModeReplace, (unsigned char*) targets, sizeof(targets)/sizeof(*targets));
	sev.property = srev->property;
      } else {
	sev.property = None;
      }
      sev.type = SelectionNotify;
      sev.requestor = srev->requestor;
      sev.selection = srev->selection;
      sev.target = srev->target;
      sev.time = srev->time;
      XSendEvent(dpy, srev->requestor, True, NoEventMask, (XEvent *) &sev);
      return 1;
    }
  }
  return 0;
}

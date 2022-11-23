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

#ifndef BRLTTY_INCLUDED_GET_CURSES
#define BRLTTY_INCLUDED_GET_CURSES

#include "prologue.h"
#undef GOT_CURSES
#undef GOT_CURSES_GET_WCH

#if defined(HAVE_PKG_CURSES)
#define GOT_CURSES
#include <curses.h>

#elif defined(HAVE_PKG_NCURSES)
#define GOT_CURSES
#include <ncurses.h>

#elif defined(HAVE_PKG_NCURSESW)
#define GOT_CURSES
#define GOT_CURSES_GET_WCH
#include <ncursesw/ncurses.h>

#elif defined(HAVE_PKG_PDCURSES)
#define GOT_CURSES
#include <curses.h>

#elif defined(HAVE_PKG_PDCURSESU)
#define GOT_CURSES
#include <curses.h>

#elif defined(HAVE_PKG_PDCURSESW)
#define GOT_CURSES
#define GOT_CURSES_GET_WCH
#define PDC_WIDE
#include <curses.h>

#else /* curses package */
#warning curses package either unspecified or unsupported
#endif /* curses package */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_GET_CURSES */

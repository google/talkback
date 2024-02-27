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

#ifndef BRLTTY_INCLUDED_ROUTING
#define BRLTTY_INCLUDED_ROUTING

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  // don't change the order
  ROUTING_STATUS_NONE,
  ROUTING_STATUS_SUCCEESS,
  ROUTING_STATUS_COLUMN,
  ROUTING_STATUS_ROW,
  ROUTING_STATUS_FAILURE
} RoutingStatus;

extern int startRouting (int column, int row, int screen);
extern int isRouting (void);
extern RoutingStatus getRoutingStatus (int wait);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_ROUTING */

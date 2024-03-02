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

#ifndef BRLTTY_INCLUDED_BLINK
#define BRLTTY_INCLUDED_BLINK

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct BlinkDescriptorStruct BlinkDescriptor;
extern BlinkDescriptor screenCursorBlinkDescriptor;
extern BlinkDescriptor attributesUnderlineBlinkDescriptor;
extern BlinkDescriptor uppercaseLettersBlinkDescriptor;
extern BlinkDescriptor speechCursorBlinkDescriptor;

extern int getBlinkPeriod (BlinkDescriptor *blink);
extern int getBlinkPercentVisible (BlinkDescriptor *blink);
extern int setBlinkProperties (BlinkDescriptor *blink, int period, int percentVisible);

static inline int setBlinkPeriod (BlinkDescriptor *blink, int period) {
  return setBlinkProperties(blink, period, getBlinkPercentVisible(blink));
}

static inline int setBlinkPercentVisible (BlinkDescriptor *blink, int percentVisible) {
  return setBlinkProperties(blink, getBlinkPeriod(blink), percentVisible);
}

extern const char *getBlinkName (BlinkDescriptor *blink);
extern int getBlinkVisibleTime (BlinkDescriptor *blink);
extern int getBlinkInvisibleTime (BlinkDescriptor *blink);
extern int isBlinkEnabled (BlinkDescriptor *blink);

extern int isBlinkVisible (const BlinkDescriptor *blink);
extern void setBlinkState (BlinkDescriptor *blink, int visible);

extern void unrequireAllBlinkDescriptors (void);
extern void requireBlinkDescriptor (BlinkDescriptor *blink);

extern void resetAllBlinkDescriptors (void);
extern void stopAllBlinkDescriptors (void);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_BLINK */

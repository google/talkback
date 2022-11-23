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

#ifndef BRLTTY_INCLUDED_SYSTEM_DARWIN
#define BRLTTY_INCLUDED_SYSTEM_DARWIN

#include <CoreFoundation/CFRunLoop.h>

#import <Foundation/NSThread.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern IOReturn executeRunLoop (int seconds);
extern void addRunLoopSource (CFRunLoopSourceRef source);
extern void removeRunLoopSource (CFRunLoopSourceRef source);

#define MAP_DARWIN_ERROR(from,to) case (from): errno = (to); break;
extern void setDarwinSystemError (IOReturn result);

@interface AsynchronousResult: NSObject
@property (assign, readonly) int isFinished;
@property (assign, readonly) IOReturn finalStatus;

- (int) wait
  : (int) timeout;

- (void) setStatus
  : (IOReturn) status;
@end

@interface AsynchronousTask: AsynchronousResult
@property (assign, readonly) NSThread *taskThread;
@property (assign, readonly) CFRunLoopRef taskRunLoop;

- (IOReturn) run;

- (int) start;

- (void) stop;
@end

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SYSTEM_DARWIN */

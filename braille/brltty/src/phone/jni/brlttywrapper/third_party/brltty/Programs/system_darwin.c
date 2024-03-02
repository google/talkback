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
#include <IOKit/IOKitLib.h>

#import <Foundation/NSLock.h>
#import <Foundation/NSThread.h>
#import <Foundation/NSAutoreleasePool.h>

#include "log.h"
#include "system.h"
#include "system_darwin.h"

static inline CFRunLoopRef
getRunLoop (void) {
  return CFRunLoopGetCurrent();
}

static inline CFStringRef
getRunMode (void) {
  return kCFRunLoopDefaultMode;
}

IOReturn
executeRunLoop (int seconds) {
  return CFRunLoopRunInMode(getRunMode(), seconds, 1);
}

void
addRunLoopSource (CFRunLoopSourceRef source) {
  CFRunLoopAddSource(getRunLoop(), source, getRunMode());
}

void
removeRunLoopSource (CFRunLoopSourceRef source) {
  CFRunLoopRemoveSource(getRunLoop(), source, getRunMode());
}

void
setDarwinSystemError (IOReturn result) {
  switch (result) {
    default: errno = EIO; break;

  //MAP_DARWIN_ERROR(KERN_SUCCESS, )
    MAP_DARWIN_ERROR(KERN_INVALID_ADDRESS, EINVAL)
    MAP_DARWIN_ERROR(KERN_PROTECTION_FAILURE, EFAULT)
    MAP_DARWIN_ERROR(KERN_NO_SPACE, ENOSPC)
    MAP_DARWIN_ERROR(KERN_INVALID_ARGUMENT, EINVAL)
  //MAP_DARWIN_ERROR(KERN_FAILURE, )
    MAP_DARWIN_ERROR(KERN_RESOURCE_SHORTAGE, EAGAIN)
  //MAP_DARWIN_ERROR(KERN_NOT_RECEIVER, )
    MAP_DARWIN_ERROR(KERN_NO_ACCESS, EACCES)
    MAP_DARWIN_ERROR(KERN_MEMORY_FAILURE, EFAULT)
    MAP_DARWIN_ERROR(KERN_MEMORY_ERROR, EFAULT)
  //MAP_DARWIN_ERROR(KERN_ALREADY_IN_SET, )
  //MAP_DARWIN_ERROR(KERN_NOT_IN_SET, )
    MAP_DARWIN_ERROR(KERN_NAME_EXISTS, EEXIST)
    MAP_DARWIN_ERROR(KERN_ABORTED, ECANCELED)
    MAP_DARWIN_ERROR(KERN_INVALID_NAME, EINVAL)
    MAP_DARWIN_ERROR(KERN_INVALID_TASK, EINVAL)
    MAP_DARWIN_ERROR(KERN_INVALID_RIGHT, EINVAL)
    MAP_DARWIN_ERROR(KERN_INVALID_VALUE, EINVAL)
  //MAP_DARWIN_ERROR(KERN_UREFS_OVERFLOW, )
    MAP_DARWIN_ERROR(KERN_INVALID_CAPABILITY, EINVAL)
  //MAP_DARWIN_ERROR(KERN_RIGHT_EXISTS, )
    MAP_DARWIN_ERROR(KERN_INVALID_HOST, EINVAL)
  //MAP_DARWIN_ERROR(KERN_MEMORY_PRESENT, )
  //MAP_DARWIN_ERROR(KERN_MEMORY_DATA_MOVED, )
  //MAP_DARWIN_ERROR(KERN_MEMORY_RESTART_COPY, )
    MAP_DARWIN_ERROR(KERN_INVALID_PROCESSOR_SET, EINVAL)
  //MAP_DARWIN_ERROR(KERN_POLICY_LIMIT, )
    MAP_DARWIN_ERROR(KERN_INVALID_POLICY, EINVAL)
    MAP_DARWIN_ERROR(KERN_INVALID_OBJECT, EINVAL)
  //MAP_DARWIN_ERROR(KERN_ALREADY_WAITING, )
  //MAP_DARWIN_ERROR(KERN_DEFAULT_SET, )
  //MAP_DARWIN_ERROR(KERN_EXCEPTION_PROTECTED, )
    MAP_DARWIN_ERROR(KERN_INVALID_LEDGER, EINVAL)
    MAP_DARWIN_ERROR(KERN_INVALID_MEMORY_CONTROL, EINVAL)
    MAP_DARWIN_ERROR(KERN_INVALID_SECURITY, EINVAL)
  //MAP_DARWIN_ERROR(KERN_NOT_DEPRESSED, )
  //MAP_DARWIN_ERROR(KERN_TERMINATED, )
  //MAP_DARWIN_ERROR(KERN_LOCK_SET_DESTROYED, )
  //MAP_DARWIN_ERROR(KERN_LOCK_UNSTABLE, )
  //MAP_DARWIN_ERROR(KERN_LOCK_OWNED, )
  //MAP_DARWIN_ERROR(KERN_LOCK_OWNED_SELF, )
  //MAP_DARWIN_ERROR(KERN_SEMAPHORE_DESTROYED, )
  //MAP_DARWIN_ERROR(KERN_RPC_SERVER_TERMINATED, )
  //MAP_DARWIN_ERROR(KERN_RPC_TERMINATE_ORPHAN, )
  //MAP_DARWIN_ERROR(KERN_RPC_CONTINUE_ORPHAN, )
    MAP_DARWIN_ERROR(KERN_NOT_SUPPORTED, ENOTSUP)
    MAP_DARWIN_ERROR(KERN_NODE_DOWN, EHOSTDOWN)
  //MAP_DARWIN_ERROR(KERN_NOT_WAITING, )
    MAP_DARWIN_ERROR(KERN_OPERATION_TIMED_OUT, ETIMEDOUT)

    MAP_DARWIN_ERROR(kIOReturnSuccess, 0)
  //MAP_DARWIN_ERROR(kIOReturnError, )
    MAP_DARWIN_ERROR(kIOReturnNoMemory, ENOMEM)
    MAP_DARWIN_ERROR(kIOReturnNoResources, EAGAIN)
  //MAP_DARWIN_ERROR(kIOReturnIPCError, )
    MAP_DARWIN_ERROR(kIOReturnNoDevice, ENODEV)
    MAP_DARWIN_ERROR(kIOReturnNotPrivileged, EACCES)
    MAP_DARWIN_ERROR(kIOReturnBadArgument, EINVAL)
    MAP_DARWIN_ERROR(kIOReturnLockedRead, ENOLCK)
    MAP_DARWIN_ERROR(kIOReturnLockedWrite, ENOLCK)
    MAP_DARWIN_ERROR(kIOReturnExclusiveAccess, EBUSY)
  //MAP_DARWIN_ERROR(kIOReturnBadMessageID, )
    MAP_DARWIN_ERROR(kIOReturnUnsupported, ENOTSUP)
  //MAP_DARWIN_ERROR(kIOReturnVMError, )
  //MAP_DARWIN_ERROR(kIOReturnInternalError, )
    MAP_DARWIN_ERROR(kIOReturnIOError, EIO)
    MAP_DARWIN_ERROR(kIOReturnCannotLock, ENOLCK)
    MAP_DARWIN_ERROR(kIOReturnNotOpen, EBADF)
    MAP_DARWIN_ERROR(kIOReturnNotReadable, EACCES)
    MAP_DARWIN_ERROR(kIOReturnNotWritable, EROFS)
  //MAP_DARWIN_ERROR(kIOReturnNotAligned, )
    MAP_DARWIN_ERROR(kIOReturnBadMedia, ENXIO)
  //MAP_DARWIN_ERROR(kIOReturnStillOpen, )
  //MAP_DARWIN_ERROR(kIOReturnRLDError, )
    MAP_DARWIN_ERROR(kIOReturnDMAError, EDEVERR)
    MAP_DARWIN_ERROR(kIOReturnBusy, EBUSY)
    MAP_DARWIN_ERROR(kIOReturnTimeout, ETIMEDOUT)
    MAP_DARWIN_ERROR(kIOReturnOffline, ENXIO)
    MAP_DARWIN_ERROR(kIOReturnNotReady, ENXIO)
    MAP_DARWIN_ERROR(kIOReturnNotAttached, ENXIO)
    MAP_DARWIN_ERROR(kIOReturnNoChannels, EDEVERR)
    MAP_DARWIN_ERROR(kIOReturnNoSpace, ENOSPC)
    MAP_DARWIN_ERROR(kIOReturnPortExists, EADDRINUSE)
    MAP_DARWIN_ERROR(kIOReturnCannotWire, ENOMEM)
  //MAP_DARWIN_ERROR(kIOReturnNoInterrupt, )
    MAP_DARWIN_ERROR(kIOReturnNoFrames, EDEVERR)
    MAP_DARWIN_ERROR(kIOReturnMessageTooLarge, EMSGSIZE)
    MAP_DARWIN_ERROR(kIOReturnNotPermitted, EPERM)
    MAP_DARWIN_ERROR(kIOReturnNoPower, EPWROFF)
    MAP_DARWIN_ERROR(kIOReturnNoMedia, ENXIO)
    MAP_DARWIN_ERROR(kIOReturnUnformattedMedia, ENXIO)
    MAP_DARWIN_ERROR(kIOReturnUnsupportedMode, ENOSYS)
    MAP_DARWIN_ERROR(kIOReturnUnderrun, EDEVERR)
    MAP_DARWIN_ERROR(kIOReturnOverrun, EDEVERR)
    MAP_DARWIN_ERROR(kIOReturnDeviceError, EDEVERR)
  //MAP_DARWIN_ERROR(kIOReturnNoCompletion, )
    MAP_DARWIN_ERROR(kIOReturnAborted, ECANCELED)
    MAP_DARWIN_ERROR(kIOReturnNoBandwidth, EDEVERR)
    MAP_DARWIN_ERROR(kIOReturnNotResponding, EDEVERR)
    MAP_DARWIN_ERROR(kIOReturnIsoTooOld, EDEVERR)
    MAP_DARWIN_ERROR(kIOReturnIsoTooNew, EDEVERR)
    MAP_DARWIN_ERROR(kIOReturnNotFound, ENOENT)
  //MAP_DARWIN_ERROR(kIOReturnInvalid, )
  }
}

void
initializeSystemObject (void) {
}

@interface AsynchronousResult ()
@property (assign, readwrite) int isFinished;
@property (assign, readwrite) IOReturn finalStatus;
@end

@implementation AsynchronousResult
@synthesize isFinished;
@synthesize finalStatus;

- (int) wait
  : (int) timeout
  {
    if (self.isFinished) return 1;

    while (1) {
      IOReturn result = executeRunLoop(timeout);

      if (self.isFinished) return 1;
      if (result == kCFRunLoopRunHandledSource) continue;
      if (result == kCFRunLoopRunTimedOut) return 0;
    }
  }

- (void) setStatus
  : (IOReturn) status
  {
    self.finalStatus = status;
    self.isFinished = 1;
  }
@end

@interface AsynchronousTask ()
@property (assign, readwrite) NSThread *taskThread;
@property (assign, readwrite) CFRunLoopRef taskRunLoop;

@property (retain) NSCondition *startSynchronizer;
@property (retain) NSThread *resultThread;

- (void) taskFinished;

- (void) main;

- (void) endTask;
@end

@implementation AsynchronousTask
@synthesize taskThread;
@synthesize taskRunLoop;
@synthesize startSynchronizer;
@synthesize resultThread;

- (IOReturn) run
  {
    logMessage(LOG_WARNING, "run method not overridden");
    return kIOReturnSuccess;
  }

- (void) taskFinished
  {
    self.resultThread = nil;
  }

- (void) main
  {
    NSAutoreleasePool *pool = [NSAutoreleasePool new];

    [self.startSynchronizer lock];
    self.taskThread = [NSThread currentThread];
    self.taskRunLoop = CFRunLoopGetCurrent();
    [self.startSynchronizer signal];
    [self.startSynchronizer unlock];

    [self setStatus:[self run]];
    [self performSelector:@selector(taskFinished) onThread:self.resultThread withObject:nil waitUntilDone:0];

    self.taskThread = nil;
    [pool drain];
  }

- (int) start
  {
    if ((self.startSynchronizer = [NSCondition new])) {
      self.resultThread = [NSThread currentThread];

      [self.startSynchronizer lock];
      [NSThread detachNewThreadSelector:@selector(main) toTarget:self withObject:nil];
      [self.startSynchronizer wait];
      [self.startSynchronizer unlock];

      self.startSynchronizer = nil;
      return 1;
    }

    return 0;
  }

- (void) endTask
  {
    CFRunLoopStop(self.taskRunLoop);
  }

- (void) stop
  {
    [self performSelector:@selector(endTask) onThread:self.taskThread withObject:nil waitUntilDone:0];
  }
@end

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
#include <stdarg.h>
#include <setjmp.h>
#include <dpmi.h>
#include <pc.h>
#include <dos.h>
#include <go32.h>
#include <crt0.h>
#include <sys/farptr.h>

#include "log.h"
#include "system.h"
#include "system_msdos.h"

int _crt0_startup_flags = _CRT0_FLAG_LOCK_MEMORY;

/* reduce image */
int _stklen = 0X2000;

void __crt0_load_environment_file(char *_app_name) { return; }
char **__crt0_glob_function(char *_arg) { return 0; }

static void tsrExit (void) NORETURN;

/* disable this bit of magic as it causes a page fault on exit */
#if 0
/* Start undocumented way to make exception handling disappear (v2.03) */
short __djgpp_ds_alias;
void __djgpp_exception_processor(void) { return; }
void __djgpp_exception_setup(void) { return; }
void __djgpp_exception_toggle(void) { return; }
int __djgpp_set_ctrl_c(int enable) { return 0; }
void __maybe_fix_w2k_ntvdm_bug(void) { }
void abort(void) { tsrExit(); }
void _exit(int status) { tsrExit(); }
int raise(int sig) { return 0; }
void *signal(int signum, void*handler) { return NULL; }
/* End undocumented way to make exception handling disappear */
#endif

#define TIMER_INTERRUPT 0X08
#define DOS_INTERRUPT   0X21
#define IDLE_INTERRUPT  0X28

/* For saving interrupt and main contexts */

typedef char FpuState[(7+20)*8];
typedef struct {
  unsigned short segment;
  unsigned short offset;
} DiskTransferAddress;

typedef struct {
  FpuState fpu;
  DiskTransferAddress dta;
  unsigned short psp;
} State;

static State mainState;
static State interruptState;

static jmp_buf mainContext;
static jmp_buf interruptContext;

static int isBackgrounded = 0; /* whether we really TSR */

static unsigned long inDosFlagPointer;
static unsigned long criticalOffset;

/* prevent reentrancy */
static volatile int inTimerInterrupt = 0;
static volatile int inIdleInterrupt = 0;

static inline int
inInterrupt (void) {
  return inTimerInterrupt || inIdleInterrupt;
}

static volatile unsigned long elapsedTickCount;
static volatile unsigned long elapsedTickIncrement;

static __dpmi_regs idleRegisters;

static _go32_dpmi_seginfo origTimerSeginfo, timerSeginfo;
static _go32_dpmi_seginfo origIdleSeginfo,  idleSeginfo;

/* handle Program Segment Prefix switch for proper file descriptor table */
static unsigned short
getProgramSegmentPrefix (void) {
  __dpmi_regs r;

  r.h.ah = 0X51;
  __dpmi_int(DOS_INTERRUPT, &r);
  return r.x.bx;
}

static void
setProgramSegmentPrefix (unsigned short segment) {
  __dpmi_regs r;

  r.h.ah = 0X50;
  r.x.bx = segment;
  __dpmi_int(DOS_INTERRUPT, &r);
}

/* handle Disk Transfer Address switch since djgpp uses it for FindFirst/FindNext */
static void
getDiskTransferAddress (DiskTransferAddress *dta) {
  __dpmi_regs r;

  r.h.ah = 0X2F;
  __dpmi_int(DOS_INTERRUPT, &r);

  dta->segment = r.x.es;
  dta->offset = r.x.bx;
}

static void
setDiskTransferAddress (const DiskTransferAddress *dta) {
  __dpmi_regs r;

  r.h.ah = 0X1A;
  r.x.ds = dta->segment;
  r.x.dx = dta->offset;
  __dpmi_int(DOS_INTERRUPT, &r);
}

unsigned short
msdosGetCodePage (void) {
  __dpmi_regs r;

  r.h.ah = 0X66;
  r.h.al = 0X01;
  __dpmi_int(DOS_INTERRUPT, &r);

  return r.x.bx;
}

/* Handle FPU state switch */
#define saveFpuState(p) asm volatile("fnsave (%0); fwait"::"r"(p):"memory")
#define restoreFpuState(p) asm volatile("frstor (%0)"::"r"(p))

static void
saveState (State *state) {
  saveFpuState(&state->fpu);
  getDiskTransferAddress(&state->dta);
  state->psp = getProgramSegmentPrefix();
}

static void
restoreState (const State *state) {
  restoreFpuState(&state->fpu);
  setDiskTransferAddress(&state->dta);
  setProgramSegmentPrefix(state->psp);
}

static unsigned short
getTicksTillNextTimerInterrupt (void) {
  unsigned char clo, chi;

  outportb(0X43, 0XD2);
  clo = inportb(0X40);
  chi = inportb(0X40);

  return (chi << 8) | clo;
}

/* Timer interrupt handler */
static void
timerInterruptHandler (void) {
  elapsedTickCount += elapsedTickIncrement;
  elapsedTickIncrement = getTicksTillNextTimerInterrupt();

  if (!inInterrupt()) {
    inTimerInterrupt = 1;
    if (!setjmp(interruptContext)) longjmp(mainContext, 1);
    inTimerInterrupt = 0;
  }
}

/* Idle interrupt handler */
static void
idleInterruptHandler (_go32_dpmi_registers *r) {
  if (!inInterrupt()) {
    inIdleInterrupt = 1;
    if (!setjmp(interruptContext)) longjmp(mainContext, 1);
    inIdleInterrupt = 0;
  }

  r->x.cs = origIdleSeginfo.rm_segment;
  r->x.ip = origIdleSeginfo.rm_offset;
  _go32_dpmi_simulate_fcall_iret(r);
}

/* Try to restore interrupt handler */
static int
restore (int vector, _go32_dpmi_seginfo *seginfo, _go32_dpmi_seginfo *orig_seginfo) {
  _go32_dpmi_seginfo cur_seginfo;

  _go32_dpmi_get_protected_mode_interrupt_vector(vector, &cur_seginfo);

  if ((cur_seginfo.pm_selector != seginfo->pm_selector) ||
      (cur_seginfo.pm_offset != seginfo->pm_offset)) {
    return 1;
  }

  _go32_dpmi_set_protected_mode_interrupt_vector(vector, orig_seginfo);
  return 0;
}

/* TSR exit: trying to free as many resources as possible */
static void
tsrExit (void) {
  if (isBackgrounded) {
    unsigned long pspAddress = _go32_info_block.linear_address_of_original_psp;

    if (restore(TIMER_INTERRUPT, &timerSeginfo, &origTimerSeginfo) +
        restore(IDLE_INTERRUPT,  &idleSeginfo,  &origIdleSeginfo)) {
      /* failed, hang */
      setjmp(mainContext);
      longjmp(interruptContext, 1);
    }

    {
      __dpmi_regs r;

      /* free environment */
      r.x.es = _farpeekw(_dos_ds, pspAddress+0X2C);
      r.x.ax = 0X4900;
      __dpmi_int(DOS_INTERRUPT, &r);

      /* free Program Segment Prefix */
      r.x.es = pspAddress / 0X10;
      r.x.ax = 0X4900;
      __dpmi_int(DOS_INTERRUPT, &r);
    }

    /* and return */
    longjmp(interruptContext, 1);

    /* TODO: free protected mode memory */
  }
}

/* go to background: TSR */
void
msdosBackground (void) {
  __djgpp_set_ctrl_c(0);
  saveState(&mainState);

  if (!setjmp(mainContext)) {
    __dpmi_regs regs;

    /* set a chained Protected Mode Timer IRQ handler */
    timerSeginfo.pm_selector = _my_cs();
    timerSeginfo.pm_offset = (unsigned long)&timerInterruptHandler;
    _go32_dpmi_get_protected_mode_interrupt_vector(TIMER_INTERRUPT, &origTimerSeginfo);
    _go32_dpmi_chain_protected_mode_interrupt_vector(TIMER_INTERRUPT, &timerSeginfo);

    /* set a real mode DOS Idle handler which calls back our Idle handler */
    idleSeginfo.pm_selector = _my_cs();
    idleSeginfo.pm_offset = (unsigned long)&idleInterruptHandler;
    memset(&idleRegisters, 0, sizeof(idleRegisters));
    _go32_dpmi_get_real_mode_interrupt_vector(IDLE_INTERRUPT, &origIdleSeginfo);
    _go32_dpmi_allocate_real_mode_callback_iret(&idleSeginfo, &idleRegisters);
    _go32_dpmi_set_real_mode_interrupt_vector(IDLE_INTERRUPT, &idleSeginfo);

    /* Get InDos and Critical flags addresses */
    regs.h.ah = 0X34;
    __dpmi_int(DOS_INTERRUPT, &regs);
    inDosFlagPointer = msdosMakeAddress(regs.x.es, regs.x.bx);

    regs.x.ax = 0X5D06;
    __dpmi_int(DOS_INTERRUPT, &regs);
    criticalOffset = msdosMakeAddress(regs.x.ds, regs.x.si);

    /* We are ready */
    isBackgrounded = 1;

    regs.x.ax = 0X3100;
    msdosBreakAddress(0X100/*psp*/ + _go32_info_block.size_of_transfer_buffer, 0,
                      &regs.x.dx, NULL);
    __dpmi_int(DOS_INTERRUPT, &regs);

    /* shouldn't be reached */
    logMessage(LOG_ERR, "TSR installation failed");
    isBackgrounded = 0;
  }

  saveState(&interruptState);
  restoreState(&mainState);
}

unsigned long
msdosUSleep (unsigned long microseconds) {
  unsigned long ticks;

  if (!isBackgrounded) {
    usleep(microseconds);
    return microseconds;
  }

  saveState(&mainState);
  restoreState(&interruptState);

  /* clock ticks to wait */
  ticks = (microseconds * MSDOS_PIT_FREQUENCY) / UINT64_C(1000000);

  /* we're starting in the middle of a timer period */
  {
    int wasEnabled = disable();

    elapsedTickIncrement = getTicksTillNextTimerInterrupt();
    elapsedTickCount = 0;

    if (wasEnabled) enable();
  }

  while (elapsedTickCount < ticks) {
    /* wait for next interrupt */
    if (!setjmp(mainContext)) longjmp(interruptContext, 1);
    /* interrupt returned */
  }

  /* wait for Dos to be free */
  setjmp(mainContext);

  /* critical sections of DOS are never reentrant */
  if (_farpeekb(_dos_ds, criticalOffset)
  /* DOS is busy but not idle */
   || (!inIdleInterrupt && _farpeekb(_dos_ds, inDosFlagPointer)))
    longjmp(interruptContext, 1);

  saveState(&interruptState);
  restoreState(&mainState);

  return (elapsedTickCount * UINT64_C(1000000)) / MSDOS_PIT_FREQUENCY;
}

int
vsnprintf (char *str, size_t size, const char *format, va_list ap) {
  size_t alloc = 1024;
  char *buf;
  int ret;

  if (alloc < size) alloc = size;
  buf = alloca(alloc);
  ret = vsprintf(buf, format, ap);
  if (size > (ret + 1)) size = ret + 1;
  memcpy(str, buf, size);
  return ret;
}

int
snprintf (char *str, size_t size, const char *format, ...) {
  va_list argp;
  int ret;

  va_start(argp, format);
  ret = vsnprintf(str, size, format, argp);
  va_end(argp);

  return ret;
}

void
initializeSystemObject (void) {
}

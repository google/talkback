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

#ifndef BRLTTY_INCLUDED_SYSTEM_WINDOWS
#define BRLTTY_INCLUDED_SYSTEM_WINDOWS

#include "prologue.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define WIN_PROC_STUB(name) typeof(name) (*name##Proc)


/* ntdll.dll */
#include <ntdef.h>

#ifndef STATUS_SUCCESS
#include <ntstatus.h>
#endif /* STATUS_SUCCESS */

#ifndef HAVE_PROCESS_INFORMATION_CLASS
typedef enum _PROCESSINFOCLASS {
  ProcessUserModeIOPL = 16,
} PROCESSINFOCLASS, PROCESS_INFORMATION_CLASS;
#else /* HAVE_PROCESS_INFORMATION_CLASS */
#  ifndef HAVE_ProcessUserModeIOPL
#    define ProcessUserModeIOPL 16
#  endif /* ProcessUserModeIOPL */
#endif /* HAVE_PROCESS_INFORMATION_CLASS */

extern NTSTATUS WINAPI NtSetInformationProcess (HANDLE, PROCESS_INFORMATION_CLASS, PVOID, ULONG);
extern WIN_PROC_STUB(NtSetInformationProcess);


/* kernel32.dll: console */
extern WIN_PROC_STUB(AttachConsole);

extern WINBASEAPI int WINAPI GetLocaleInfoEx (LPCWSTR, LCTYPE, LPWSTR, int);
extern WIN_PROC_STUB(GetLocaleInfoEx);

#ifndef LOCALE_NAME_USER_DEFAULT
#define LOCALE_NAME_USER_DEFAULT NULL
#endif /* LOCALE_NAME_USER_DEFAULT */

#ifndef LOCALE_SNAME
#define LOCALE_SNAME 0X5CL
#endif /* LOCALE_SNAME */


/* user32.dll */
extern WIN_PROC_STUB(GetAltTabInfoA);
extern WIN_PROC_STUB(SendInput);


/* ws2_32.dll */
#ifdef __MINGW32__
#include <ws2tcpip.h>

extern WIN_PROC_STUB(getaddrinfo);
extern WIN_PROC_STUB(freeaddrinfo);

#define getaddrinfo(host,port,hints,res) getaddrinfoProc(host,port,hints,res)
#define freeaddrinfo(res) freeaddrinfoProc(res)

extern char *getWindowsLocaleName (void);
#endif /* __MINGW32__ */


extern char *makeWindowsCommandLine (const char *const *arguments);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SYSTEM_WINDOWS */

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

#include "log.h"
#include "pgmpath.h"
#include "service.h"
#include "system_windows.h"

int
installService (const char *name, const char *description, const char *configurationFile) {
  const char *const arguments[] = {
    getProgramPath(), "-n",
    "-f", configurationFile,
    NULL
  };

  int installed = 0;
  char *command = makeWindowsCommandLine(arguments);

  if (command) {
    SC_HANDLE scm = OpenSCManager(
      NULL, // machine name
      NULL, // database name
      SC_MANAGER_CREATE_SERVICE // desired access
    );

    if (scm) {
      SC_HANDLE service = CreateService(
        scm, // service control manager database
        name, // service name
        description, // display name
        SERVICE_ALL_ACCESS, // desired access
        (SERVICE_WIN32_OWN_PROCESS | SERVICE_INTERACTIVE_PROCESS), // service type
        SERVICE_AUTO_START, // start type
        SERVICE_ERROR_NORMAL, // error control
        command, // binary path name
        NULL, // load order group
        NULL, // tag id
        NULL, // dependencies
        NULL, // service start name
        NULL // password
      );

      if (service) {
        logMessage(LOG_NOTICE, "service installed: %s", name);
        installed = 1;

        CloseServiceHandle(service);
      } else if (GetLastError() == ERROR_SERVICE_EXISTS) {
        logMessage(LOG_WARNING, "service already installed: %s", name);
        installed = 1;
      } else {
        logWindowsSystemError("CreateService");
      }

      CloseServiceHandle(scm);
    } else {
      logWindowsSystemError("OpenSCManager");
    }

    free(command);
  }

  return installed;
}

int
removeService (const char *name) {
  int removed = 0;
  SC_HANDLE scm = OpenSCManager(NULL, NULL, SC_MANAGER_ALL_ACCESS);

  if (scm) {
    SC_HANDLE service = OpenService(scm, name, DELETE);

    if (service) {
      if (DeleteService(service)) {
        logMessage(LOG_NOTICE, "service removed: %s", name);
        removed = 1;
      } else if (GetLastError() == ERROR_SERVICE_MARKED_FOR_DELETE) {
        logMessage(LOG_WARNING, "service already being removed: %s", name);
        removed = 1;
      } else {
        logWindowsSystemError("DeleteService");
      }

      CloseServiceHandle(service);
    } else if (GetLastError() == ERROR_SERVICE_DOES_NOT_EXIST) {
      logMessage(LOG_WARNING, "service not installed: %s", name);
      removed = 1;
    } else {
      logWindowsSystemError("OpenService");
    }

    CloseServiceHandle(scm);
  } else {
    logWindowsSystemError("OpenSCManager");
  }

  return removed;
}

int
notifyServiceReady (void) {
  logUnsupportedFeature("service ready notification");
  return 0;
}

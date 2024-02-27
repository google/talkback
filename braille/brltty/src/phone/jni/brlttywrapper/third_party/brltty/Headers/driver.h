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

#ifndef BRLTTY_INCLUDED_DRIVER
#define BRLTTY_INCLUDED_DRIVER

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  const char *name;
  const char *code;
  const char *comment;
  const char *version;
  const char *developers;
  const char *date;
  const char *time;
} DriverDefinition;

#define DRIVER_DEFINITION_DECLARATION const DriverDefinition definition

#define DRIVER_DEFINITION_INITIALIZER .definition = { \
  .name = STRINGIFY(DRIVER_NAME), \
  .code = STRINGIFY(DRIVER_CODE), \
  .comment = DRIVER_COMMENT, \
  .version = DRIVER_VERSION, \
  .developers = DRIVER_DEVELOPERS, \
  .date = __DATE__, \
  .time = __TIME__ \
}

#define DRIVER_VERSION_STRING PACKAGE_VERSION
#define DRIVER_VERSION_DECLARATION(type) const char CONCATENATE(type##_version_,DRIVER_CODE)[] = DRIVER_VERSION_STRING

extern void unsupportedDeviceIdentifier (const char *identifier);

extern void logOutputPacket (const void *packet, size_t size);
extern void logInputPacket (const void *packet, size_t size);
extern void logInputProblem (const char *problem, const unsigned char *bytes, size_t count);
extern void logIgnoredByte (unsigned char byte);
extern void logDiscardedByte (unsigned char byte);
extern void logUnknownPacket (unsigned char byte);
extern void logPartialPacket (const void *packet, size_t size);
extern void logTruncatedPacket (const void *packet, size_t size);
extern void logShortPacket (const void *packet, size_t size);
extern void logUnexpectedPacket (const void *packet, size_t size);
extern void logCorruptPacket (const void *packet, size_t size);
extern void logDiscardedBytes (const unsigned char *bytes, size_t count);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_DRIVER */

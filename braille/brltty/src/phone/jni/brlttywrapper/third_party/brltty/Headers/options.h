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

#ifndef BRLTTY_INCLUDED_OPTIONS
#define BRLTTY_INCLUDED_OPTIONS

#include "strfmth.h"
#include "program.h"
#include "datafile.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  OPT_Hidden	= 0X01,
  OPT_Extend	= 0X02,
  OPT_Config	= 0X04,
  OPT_Environ	= 0X08,
  OPT_Format  	= 0X10
} OptionFlag;

#define FLAG_TRUE_WORD "on"
#define FLAG_FALSE_WORD "off"

typedef struct {
  const char *word;
  const char *argument;
  const char *description;

  struct {
    const char *setting;
    int (*adjust) (char **setting);
  } internal;

  unsigned char letter;
  unsigned char bootParameter;
  unsigned char flags;

  union {
    int *flag;
    char **string;
  } setting;

  union {
    const char *const *array;
    STR_DECLARE_FORMATTER((*format), unsigned int index);
  } strings;
} OptionEntry;

#define BEGIN_OPTION_TABLE(name) static const OptionEntry name[] = {
#define END_OPTION_TABLE \
  { .letter = 'h', \
    .word = "help", \
    .description = strtext("Print a usage summary (commonly used options only), and then exit.") \
  } \
  , \
  { .letter = 'H', \
    .word = "full-help", \
    .description = strtext("Print a usage summary (all options), and then exit.") \
  } \
};

typedef struct {
  const OptionEntry *optionTable;
  unsigned int optionCount;
  int *doBootParameters;
  int *doEnvironmentVariables;
  char **configurationFile;
  const char *applicationName;
  const char *argumentsSummary;
} OptionsDescriptor;

#define OPTION_TABLE(name) .optionTable = name, .optionCount = ARRAY_COUNT(name)

extern ProgramExitStatus processOptions (
  const OptionsDescriptor *descriptor,
  int *argumentCount, char ***argumentVector
);

#define PROCESS_OPTIONS(descriptorVariable, argcVariable, argvVariable) { \
  ProgramExitStatus exitStatus = processOptions(&descriptorVariable, &argcVariable, &argvVariable); \
  if (exitStatus == PROG_EXIT_FORCE) return PROG_EXIT_SUCCESS; \
  if (exitStatus != PROG_EXIT_SUCCESS) return exitStatus; \
}

extern void resetOptions (const OptionsDescriptor *descriptor);

typedef struct {
  void (*beginStream) (const char *name, void *data);
  void (*endStream) (int incomplete, void *data);
  DataFileParameters dataFileParameters;
} InputFilesProcessingParameters;

extern ProgramExitStatus processInputFiles (
  char **paths, int count,
  const InputFilesProcessingParameters *parameters
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_OPTIONS */

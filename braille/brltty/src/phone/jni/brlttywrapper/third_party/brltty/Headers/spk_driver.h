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

#ifndef BRLTTY_INCLUDED_SPK_DRIVER
#define BRLTTY_INCLUDED_SPK_DRIVER

#include "spk_types.h"
#include "spk_base.h"
#include "spk.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#ifdef SPKPARMS
static const char *const spk_parameters[] = {SPKPARMS, NULL};
#else /* SPKPARMS */
#define spk_parameters NULL
#endif /* SPKPARMS */

static int spk_construct (SpeechSynthesizer *spk, char **parameters);
static void spk_destruct (SpeechSynthesizer *spk);

static void spk_say (SpeechSynthesizer *spk, const unsigned char *text, size_t length, size_t count, const unsigned char *attributes);
static void spk_mute (SpeechSynthesizer *spk);

#ifndef SPKSYMBOL
#define SPKSYMBOL CONCATENATE(spk_driver_,DRIVER_CODE)
#endif /* SPKSYMBOL */

#ifndef SPKCONST
#define SPKCONST const
#endif /* SPKCONST */

extern SPKCONST SpeechDriver SPKSYMBOL;
SPKCONST SpeechDriver SPKSYMBOL = {
  DRIVER_DEFINITION_INITIALIZER,

  spk_parameters,

  spk_construct,
  spk_destruct,

  spk_say,
  spk_mute
};

DRIVER_VERSION_DECLARATION(spk);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SPK_DRIVER */

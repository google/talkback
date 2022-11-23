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

#include "prologue.h"

#include <string.h>

#include "log.h"
#include "pcm.h"
#include "system_java.h"

struct PcmDeviceStruct {
  JNIEnv *env;
  jobject device;
};

static jclass pcmDeviceClass = NULL;

static int
findPcmDeviceClass (JNIEnv *env) {
  return findJavaClass(env, &pcmDeviceClass, JAVA_OBJ_BRLTTY("PcmDevice"));
}

PcmDevice *
openPcmDevice (int errorLevel, const char *device) {
  PcmDevice *pcm = malloc(sizeof(*pcm));

  if (pcm) {
    memset(pcm, 0, sizeof(*pcm));
    pcm->env = getJavaNativeInterface();
    pcm->device = NULL;

    if (findPcmDeviceClass(pcm->env)) {
      static jmethodID constructor = 0;

      if (findJavaConstructor(pcm->env, &constructor, pcmDeviceClass,
                              JAVA_SIG_CONSTRUCTOR())) {
        jobject localReference = (*pcm->env)->NewObject(pcm->env, pcmDeviceClass, constructor);

        if (!clearJavaException(pcm->env, 1)) {
          jobject globalReference = (*pcm->env)->NewGlobalRef(pcm->env, localReference);

          (*pcm->env)->DeleteLocalRef(pcm->env, localReference);
          localReference = NULL;

          if (globalReference) {
            pcm->device = globalReference;
            return pcm;
          } else {
            logMallocError();
            clearJavaException(pcm->env, 0);
          }
        }
      }
    }

    free(pcm);
  } else {
    logMallocError();
  }

  return NULL;
}

void
closePcmDevice (PcmDevice *pcm) {
  if (pcm) {
    if (pcm->device) {
      if (findPcmDeviceClass(pcm->env)) {
        static jmethodID method = 0;

        if (findJavaInstanceMethod(pcm->env, &method, pcmDeviceClass, "close",
                                   JAVA_SIG_METHOD(JAVA_SIG_VOID,
                                                  ))) {
          (*pcm->env)->CallVoidMethod(pcm->env, pcm->device, method);
          clearJavaException(pcm->env, 1);
        }
      }

      (*pcm->env)->DeleteGlobalRef(pcm->env, pcm->device);
    }

    free(pcm);
  }
}

int
writePcmData (PcmDevice *pcm, const unsigned char *buffer, int count) {
  if (findPcmDeviceClass(pcm->env)) {
    static jmethodID method = 0;

    if (findJavaInstanceMethod(pcm->env, &method, pcmDeviceClass, "write",
                               JAVA_SIG_METHOD(JAVA_SIG_BOOLEAN,
                                               JAVA_SIG_ARRAY(JAVA_SIG_SHORT) // samples
                                              ))) {
      jint size = count / 2;
      jshortArray jSamples = (*pcm->env)->NewShortArray(pcm->env, size);
      if (jSamples) {
        typedef union {
          const unsigned char *bytes;
          const int16_t *actual;
        } Samples;

        Samples samples = {
          .bytes = buffer
        };

        jboolean result;

        (*pcm->env)->SetShortArrayRegion(pcm->env, jSamples, 0, size, samples.actual);
        result = (*pcm->env)->CallBooleanMethod(pcm->env, pcm->device, method, jSamples);
        (*pcm->env)->DeleteLocalRef(pcm->env, jSamples);

        if (!clearJavaException(pcm->env, 1)) {
          if (result == JNI_TRUE) {
            return 1;
          }
        }
      } else {
        logMallocError();
        clearJavaException(pcm->env, 0);
      }
    }
  }

  return 0;
}

int
getPcmBlockSize (PcmDevice *pcm) {
  if (findPcmDeviceClass(pcm->env)) {
    static jmethodID method = 0;

    if (findJavaInstanceMethod(pcm->env, &method, pcmDeviceClass, "getBufferSize",
                               JAVA_SIG_METHOD(JAVA_SIG_INT,
                                              ))) {
      jint result = (*pcm->env)->CallIntMethod(pcm->env, pcm->device, method);

      if (!clearJavaException(pcm->env, 1)) return result;
    }
  }

  return 0X100;
}

int
getPcmSampleRate (PcmDevice *pcm) {
  if (findPcmDeviceClass(pcm->env)) {
    static jmethodID method = 0;

    if (findJavaInstanceMethod(pcm->env, &method, pcmDeviceClass, "getSampleRate",
                               JAVA_SIG_METHOD(JAVA_SIG_INT,
                                              ))) {
      jint result = (*pcm->env)->CallIntMethod(pcm->env, pcm->device, method);

      if (!clearJavaException(pcm->env, 1)) return result;
    }
  }

  return 8000;
}

int
setPcmSampleRate (PcmDevice *pcm, int rate) {
  if (findPcmDeviceClass(pcm->env)) {
    static jmethodID method = 0;

    if (findJavaInstanceMethod(pcm->env, &method, pcmDeviceClass, "setSampleRate",
                               JAVA_SIG_METHOD(JAVA_SIG_VOID,
                                               JAVA_SIG_INT // rate
                                              ))) {
      (*pcm->env)->CallVoidMethod(pcm->env, pcm->device, method, rate);
      clearJavaException(pcm->env, 1);
    }
  }

  return getPcmSampleRate(pcm);
}

int
getPcmChannelCount (PcmDevice *pcm) {
  if (findPcmDeviceClass(pcm->env)) {
    static jmethodID method = 0;

    if (findJavaInstanceMethod(pcm->env, &method, pcmDeviceClass, "getChannelCount",
                               JAVA_SIG_METHOD(JAVA_SIG_INT,
                                              ))) {
      jint result = (*pcm->env)->CallIntMethod(pcm->env, pcm->device, method);

      if (!clearJavaException(pcm->env, 1)) return result;
    }
  }

  return 1;
}

int
setPcmChannelCount (PcmDevice *pcm, int channels) {
  if (findPcmDeviceClass(pcm->env)) {
    static jmethodID method = 0;

    if (findJavaInstanceMethod(pcm->env, &method, pcmDeviceClass, "setChannelCount",
                               JAVA_SIG_METHOD(JAVA_SIG_VOID,
                                               JAVA_SIG_INT // count
                                              ))) {
      (*pcm->env)->CallVoidMethod(pcm->env, pcm->device, method, channels);
      clearJavaException(pcm->env, 1);
    }
  }

  return getPcmChannelCount(pcm);
}

PcmAmplitudeFormat
getPcmAmplitudeFormat (PcmDevice *pcm) {
  return PCM_FMT_S16N;
}

PcmAmplitudeFormat
setPcmAmplitudeFormat (PcmDevice *pcm, PcmAmplitudeFormat format) {
  return getPcmAmplitudeFormat(pcm);
}

void
pushPcmOutput (PcmDevice *pcm) {
  if (findPcmDeviceClass(pcm->env)) {
    static jmethodID method = 0;

    if (findJavaInstanceMethod(pcm->env, &method, pcmDeviceClass, "push",
                               JAVA_SIG_METHOD(JAVA_SIG_VOID,
                                              ))) {
      (*pcm->env)->CallVoidMethod(pcm->env, pcm->device, method);
      clearJavaException(pcm->env, 1);
    }
  }
}

void
awaitPcmOutput (PcmDevice *pcm) {
}

void
cancelPcmOutput (PcmDevice *pcm) {
  if (findPcmDeviceClass(pcm->env)) {
    static jmethodID method = 0;

    if (findJavaInstanceMethod(pcm->env, &method, pcmDeviceClass, "cancel",
                               JAVA_SIG_METHOD(JAVA_SIG_VOID,
                                              ))) {
      (*pcm->env)->CallVoidMethod(pcm->env, pcm->device, method);
      clearJavaException(pcm->env, 1);
    }
  }
}

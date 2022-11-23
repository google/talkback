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

#include <asoundlib.h>

#include "log.h"
#include "parse.h"
#include "timing.h"
#include "midi.h"

struct MidiDeviceStruct {
  snd_seq_t *sequencer;
  int port;
  int queue;
  snd_seq_queue_status_t *status;
  snd_seq_real_time_t time;
  unsigned char note;
};

#define snd_seq_control_queue snd_seq_control_queue

static int
findMidiDevice (MidiDevice *midi, int errorLevel, int *client, int *port) {
  snd_seq_client_info_t *clientInformation = malloc(snd_seq_client_info_sizeof());

  if (clientInformation) {
    memset(clientInformation, 0, snd_seq_client_info_sizeof());
    snd_seq_client_info_set_client(clientInformation, -1);

    while (snd_seq_query_next_client(midi->sequencer, clientInformation) >= 0) {
      int clientIdentifier = snd_seq_client_info_get_client(clientInformation);
      snd_seq_port_info_t *portInformation = malloc(snd_seq_port_info_sizeof());

      if (portInformation) {
        memset(portInformation, 0, snd_seq_port_info_sizeof());
        snd_seq_port_info_set_client(portInformation, clientIdentifier);
        snd_seq_port_info_set_port(portInformation, -1);

        while (snd_seq_query_next_port(midi->sequencer, portInformation) >= 0) {
          int portIdentifier = snd_seq_port_info_get_port(portInformation);
          int actualCapabilities = snd_seq_port_info_get_capability(portInformation);
          const int neededCapabilties = SND_SEQ_PORT_CAP_WRITE | SND_SEQ_PORT_CAP_SUBS_WRITE;

          if (((actualCapabilities & neededCapabilties) == neededCapabilties) &&
              !(actualCapabilities & SND_SEQ_PORT_CAP_NO_EXPORT)) {
            *client = clientIdentifier;
            *port = portIdentifier;
            logMessage(LOG_DEBUG, "Using ALSA MIDI device: %d[%s] %d[%s]",
                       clientIdentifier, snd_seq_client_info_get_name(clientInformation),
                       portIdentifier, snd_seq_port_info_get_name(portInformation));

            free(portInformation);
            free(clientInformation);
            return 1;
          }
        }

        free(portInformation);
      } else {
        logMallocError();
      }
    }

    free(clientInformation);
  } else {
    logMallocError();
  }

  logMessage(errorLevel, "No MDII devices.");
  return 0;
}

static int
parseMidiDevice (MidiDevice *midi, int errorLevel, const char *device, int *client, int *port) {
  char **components = splitString(device, ':', NULL);

  if (components) {
    char **component = components;

    if (*component && **component) {
      const char *clientSpecifier = *component++;

      if (*component && **component) {
        const char *portSpecifier = *component++;

        if (!*component) {
          int clientIdentifier;
          int clientOk = 0;

          if (isInteger(&clientIdentifier, clientSpecifier)) {
            if ((clientIdentifier >= 0) && (clientIdentifier <= 0XFFFF)) clientOk = 1;
          } else {
            snd_seq_client_info_t *info = malloc(snd_seq_client_info_sizeof());

            if (info) {
              memset(info, 0, snd_seq_client_info_sizeof());
              snd_seq_client_info_set_client(info, -1);

              while (snd_seq_query_next_client(midi->sequencer, info) >= 0) {
                const char *name = snd_seq_client_info_get_name(info);

                if (strstr(name, clientSpecifier)) {
                  clientIdentifier = snd_seq_client_info_get_client(info);
                  clientOk = 1;
                  logMessage(LOG_INFO, "Using ALSA MIDI client: %d[%s]",
                             clientIdentifier, name);
                  break;
                }
              }

              free(info);
            } else {
              logMallocError();
            }
          }

          if (clientOk) {
            int portIdentifier;
            int portOk = 0;

            if (isInteger(&portIdentifier, portSpecifier)) {
              if ((portIdentifier >= 0) && (portIdentifier <= 0XFFFF)) portOk = 1;
            } else {
              snd_seq_port_info_t *info = malloc(snd_seq_port_info_sizeof());

              if (info) {
                memset(info, 0, snd_seq_port_info_sizeof());
                snd_seq_port_info_set_client(info, clientIdentifier);
                snd_seq_port_info_set_port(info, -1);

                while (snd_seq_query_next_port(midi->sequencer, info) >= 0) {
                  const char *name = snd_seq_port_info_get_name(info);

                  if (strstr(name, portSpecifier)) {
                    portIdentifier = snd_seq_port_info_get_port(info);
                    portOk = 1;
                    logMessage(LOG_INFO, "Using ALSA MIDI port: %d[%s]",
                               portIdentifier, name);
                    break;
                  }
                }

                free(info);
              } else {
                logMallocError();
              }
            }

            if (portOk) {
              *client = clientIdentifier;
              *port = portIdentifier;

              deallocateStrings(components);
              return 1;
            } else {
              logMessage(errorLevel, "Invalid ALSA MIDI port: %s", device);
            }
          } else {
            logMessage(errorLevel, "Invalid ALSA MIDI client: %s", device);
          }
        } else {
          logMessage(errorLevel, "Too many ALSA MIDI device components: %s", device);
        }
      } else {
        logMessage(errorLevel, "Missing ALSA MIDI port specifier: %s", device);
      }
    } else {
      logMessage(errorLevel, "Missing ALSA MIDI client specifier: %s", device);
    }

    deallocateStrings(components);
  } else {
    logMallocError();
  }

  return 0;
}

static void
updateMidiStatus (MidiDevice *midi) {
  snd_seq_get_queue_status(midi->sequencer, midi->queue, midi->status);
}

static void
startMidiTimer (MidiDevice *midi) {
  if (!midi->time.tv_sec && !midi->time.tv_nsec) {
    updateMidiStatus(midi);
    midi->time = *snd_seq_queue_status_get_real_time(midi->status);
  }
}

static void
stopMidiTimer (MidiDevice *midi) {
  midi->time.tv_sec = 0;
  midi->time.tv_nsec = 0;
}

MidiDevice *
openMidiDevice (int errorLevel, const char *device) {
  MidiDevice *midi;

  if ((midi = malloc(sizeof(*midi)))) {
    const char *sequencerName = "default";
    int result;

    if ((result = snd_seq_open(&midi->sequencer, sequencerName, SND_SEQ_OPEN_OUTPUT, 0)) >= 0) {
      snd_seq_set_client_name(midi->sequencer, PACKAGE_NAME);

      if ((midi->port = snd_seq_create_simple_port(midi->sequencer, "out0",
                                                        SND_SEQ_PORT_CAP_READ|SND_SEQ_PORT_CAP_SUBS_READ,
                                                        SND_SEQ_PORT_TYPE_APPLICATION)) >= 0) {
        if ((midi->queue = snd_seq_alloc_queue(midi->sequencer)) >= 0) {
          if ((result = snd_seq_queue_status_malloc(&midi->status)) >= 0) {
            int client = 0;
            int port = 0;
            int deviceOk;

            if (*device) {
              deviceOk = parseMidiDevice(midi, errorLevel, device, &client, &port);
            } else {
              deviceOk = findMidiDevice(midi, errorLevel, &client, &port);
            }

            if (deviceOk) {
              logMessage(LOG_DEBUG, "Connecting to ALSA MIDI device: %d:%d", client, port);

              if ((result = snd_seq_connect_to(midi->sequencer, midi->port, client, port)) >= 0) {
                if ((result = snd_seq_start_queue(midi->sequencer, midi->queue, NULL)) >= 0) {
                  stopMidiTimer(midi);
                  return midi;
                } else {
                  logMessage(errorLevel, "Cannot start ALSA MIDI queue: %d:%d: %s",
                             client, port, snd_strerror(result));
                }
              } else {
                logMessage(errorLevel, "Cannot connect to ALSA MIDI device: %d:%d: %s",
                           client, port, snd_strerror(result));
              }
            }

            snd_seq_queue_status_free(midi->status);
          } else {
            logMessage(errorLevel, "Cannot allocate ALSA MIDI queue status container: %s",
                       snd_strerror(result));
          }
        } else {
          logMessage(errorLevel, "Cannot allocate ALSA MIDI queue: %s",
                     snd_strerror(result));
        }
      } else {
        logMessage(errorLevel, "Cannot create ALSA MIDI output port: %s",
                   snd_strerror(midi->port));
      }

      snd_seq_close(midi->sequencer);
    } else {
      logMessage(errorLevel, "Cannot open ALSA sequencer: %s: %s",
                 sequencerName, snd_strerror(result));
    }

    free(midi);
  } else {
    logSystemError("MIDI device allocation");
  }
  return NULL;
}

void
closeMidiDevice (MidiDevice *midi) {
  snd_seq_queue_status_free(midi->status);
  snd_seq_close(midi->sequencer);
  free(midi);
}

int
flushMidiDevice (MidiDevice *midi) {
  while (1) {
    int duration;

    updateMidiStatus(midi);
    {
      const snd_seq_real_time_t *time = snd_seq_queue_status_get_real_time(midi->status);
      int seconds = midi->time.tv_sec - time->tv_sec;
      int nanoseconds = midi->time.tv_nsec - time->tv_nsec;
      duration = (seconds * 1000) + (nanoseconds / 1000000);
    }

    if (duration <= 0) break;
    approximateDelay(duration);
  }

  stopMidiTimer(midi);
  return 1;
}

static void
prepareMidiEvent (MidiDevice *midi, snd_seq_event_t *event) {
  snd_seq_ev_clear(event);
  snd_seq_ev_set_source(event, midi->port);
  snd_seq_ev_set_subs(event);
}

static void
scheduleMidiEvent (MidiDevice *midi, snd_seq_event_t *event) {
  snd_seq_ev_schedule_real(event, midi->queue, 1, &midi->time);
}

static int
sendMidiEvent (MidiDevice *midi, snd_seq_event_t *event) {
  int result;

  if ((result = snd_seq_event_output(midi->sequencer, event)) >= 0) {
    snd_seq_drain_output(midi->sequencer);
    return 1;
  } else {
    logMessage(LOG_ERR, "ALSA MIDI write error: %s", snd_strerror(result));
  }
  return 0;
}

int
setMidiInstrument (MidiDevice *midi, unsigned char channel, unsigned char instrument) {
  snd_seq_event_t event;

  prepareMidiEvent(midi, &event);
  snd_seq_ev_set_pgmchange(&event, channel, instrument);
  return sendMidiEvent(midi, &event);
}

int
beginMidiBlock (MidiDevice *midi) {
  startMidiTimer(midi);
  return 1;
}

int
endMidiBlock (MidiDevice *midi) {
  return 1;
}

int
startMidiNote (MidiDevice *midi, unsigned char channel, unsigned char note, unsigned char volume) {
  snd_seq_event_t event;

  prepareMidiEvent(midi, &event);
  snd_seq_ev_set_noteon(&event, channel, note, volume);
  midi->note = note;
  scheduleMidiEvent(midi, &event);
  return sendMidiEvent(midi, &event);
}

int
stopMidiNote (MidiDevice *midi, unsigned char channel) {
  snd_seq_event_t event;

  prepareMidiEvent(midi, &event);
  snd_seq_ev_set_noteoff(&event, channel, midi->note, 0);
  midi->note = 0;
  scheduleMidiEvent(midi, &event);
  return sendMidiEvent(midi, &event);
}

int
insertMidiWait (MidiDevice *midi, int duration) {
  midi->time.tv_sec += duration / 1000;
  midi->time.tv_nsec += (duration % 1000) * 1000000;

  while (midi->time.tv_nsec >= 1000000000) {
    midi->time.tv_nsec -= 1000000000;
    midi->time.tv_sec++;
  }

  return 1;
}

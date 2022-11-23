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

#include <CoreServices/CoreServices.h>
#include <AudioUnit/AudioUnit.h>
#include <AudioToolbox/AudioToolbox.h>

#include "log.h"
#include "midi.h"

struct MidiDeviceStruct {
  AUGraph graph;
  AudioUnit synth;
  /* Note that is currently playing. */
  int note;
};
  
MidiDevice *
openMidiDevice (int errorLevel, const char *device) {
  MidiDevice *midi;
  int result;
  AUNode synthNode, outNode;
  ComponentDescription cd;
  UInt32 propVal;

  if (!(midi = malloc(sizeof(*midi)))) {
    logMallocError();
    return NULL;
  }

  /* Create a graph with a software synth and a default output unit. */

  cd.componentManufacturer = kAudioUnitManufacturer_Apple;
  cd.componentFlags = 0;
  cd.componentFlagsMask = 0;

  if ((result = NewAUGraph(&midi->graph)) != noErr) {
    logMessage(errorLevel, "Can't create audio graph component: %d", result);
    goto err;
  }

  cd.componentType = kAudioUnitType_MusicDevice;
  cd.componentSubType = kAudioUnitSubType_DLSSynth;
  if ((result = AUGraphNewNode(midi->graph, &cd, 0, NULL, &synthNode))
      != noErr) {
    logMessage(errorLevel, "Can't create software synthersizer component: %d",
	       result);
    goto err;
  }

  cd.componentType = kAudioUnitType_Output;
  cd.componentSubType = kAudioUnitSubType_DefaultOutput;
  if ((result = AUGraphNewNode(midi->graph, &cd, 0, NULL, &outNode))
      != noErr) {
    logMessage(errorLevel, "Can't create default output audio component: %d",
	       result);
    goto err;
  }

  if ((result = AUGraphOpen(midi->graph)) != noErr) {
    logMessage(errorLevel, "Can't open audio graph component: %d", result);
    goto err;
  }

  if ((result = AUGraphConnectNodeInput(midi->graph, synthNode, 0, outNode, 0))
      != noErr) {
    logMessage(errorLevel, "Can't connect synth audio component to output: %d",
	       result);
    goto err;
  }

  if ((result = AUGraphGetNodeInfo(midi->graph, synthNode, 0, 0, 0,
				   &midi->synth)) != noErr) {
    logMessage(errorLevel, "Can't get audio component for software synth: %d",
	       result);
    goto err;
  }

  if ((result = AUGraphInitialize(midi->graph)) != noErr) {
    logMessage(errorLevel, "Can't initialize audio graph: %d", result);
    goto err;
  }

  /* Turn off the reverb.  The value range is -120 to 40 dB. */
  propVal = false;
  if ((result = AudioUnitSetProperty(midi->synth,
				     kMusicDeviceProperty_UsesInternalReverb,
				     kAudioUnitScope_Global, 0,
				     &propVal, sizeof(propVal)))
      != noErr) {
    /* So, having reverb isn't that critical, is it? */
    logMessage(LOG_DEBUG, "Can't turn of software synth reverb: %d",
	       result);
  }

  /* TODO: Maybe just start the graph when we are going to use it? */
  if ((result = AUGraphStart(midi->graph)) != noErr) {
    logMessage(errorLevel, "Can't start audio graph component: %d", result);
    goto err;
  }

  return midi;

 err:
  if (midi->graph)
    DisposeAUGraph(midi->graph);
  free(midi);
  return NULL;
}

void
closeMidiDevice (MidiDevice *midi) {
  int result;
  if (midi) {
    if ((result = DisposeAUGraph(midi->graph)) != noErr)
      logMessage(LOG_ERR, "Can't dispose audio graph component: %d", result);
    free(midi);
  }
}

int
flushMidiDevice (MidiDevice *midi) {
  return 1;
}

int
setMidiInstrument (MidiDevice *midi, unsigned char channel, unsigned char instrument) {
  int result;
  if ((result = MusicDeviceMIDIEvent(midi->synth, 0xC0 | channel, instrument,
				     0, 0)) != noErr)
    logMessage(LOG_ERR, "Can't set MIDI instrument: %d", result);

  return result == noErr;
}

int
beginMidiBlock (MidiDevice *midi) {
  return 1;
}

int
endMidiBlock (MidiDevice *midi) {
  return 1;
}

int
startMidiNote (MidiDevice *midi, unsigned char channel, unsigned char note, unsigned char volume) {
  int result;

  if ((result = MusicDeviceMIDIEvent(midi->synth, 0x90 | channel, note,
				     volume, 0)) != noErr) {
    logMessage(LOG_ERR, "Can't start MIDI note: %d", result);
    return 0;
  }
  midi->note = note;

  return 1;
}

int
stopMidiNote (MidiDevice *midi, unsigned char channel) {
  int result;

  if ((result = MusicDeviceMIDIEvent(midi->synth, 0x90 | channel, midi->note,
				     0, 0)) != noErr) {
    logMessage(LOG_ERR, "Can't stop MIDI note: %d", result);
    return 0;
  }
  midi->note = 0;

  return 1;
}

int
insertMidiWait (MidiDevice *midi, int duration) {
  usleep(duration * 1000);
  return 1;
}

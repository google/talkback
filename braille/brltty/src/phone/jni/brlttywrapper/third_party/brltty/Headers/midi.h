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

#ifndef BRLTTY_INCLUDED_MIDI
#define BRLTTY_INCLUDED_MIDI

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern const char *const midiInstrumentTable[];
extern const unsigned int midiInstrumentCount;
extern const char *midiGetInstrumentType (unsigned char instrument);

typedef struct MidiDeviceStruct MidiDevice;
typedef void (*MidiBufferFlusher) (unsigned char *buffer, int count);

extern MidiDevice *openMidiDevice (int errorLevel, const char *device);
extern void closeMidiDevice (MidiDevice *midi);
extern int flushMidiDevice (MidiDevice *midi);
extern int setMidiInstrument (MidiDevice *midi, unsigned char channel, unsigned char instrument);
extern int beginMidiBlock (MidiDevice *midi);
extern int endMidiBlock (MidiDevice *midi);
extern int startMidiNote (MidiDevice *midi, unsigned char channel, unsigned char note, unsigned char volume);
extern int stopMidiNote (MidiDevice *midi, unsigned char channel);
extern int insertMidiWait (MidiDevice *midi, int duration);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_MIDI */

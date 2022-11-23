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

#include "midi.h"

static const char *const midiInstrumentTypes[] = {
  [0X0] = strtext("Piano"),
  [0X1] = strtext("Chromatic Percussion"),
  [0X2] = strtext("Organ"),
  [0X3] = strtext("Guitar"),
  [0X4] = strtext("Bass"),
  [0X5] = strtext("Strings"),
  [0X6] = strtext("Ensemble"),
  [0X7] = strtext("Brass"),
  [0X8] = strtext("Reed"),
  [0X9] = strtext("Pipe"),
  [0XA] = strtext("Synth Lead"),
  [0XB] = strtext("Synth Pad"),
  [0XC] = strtext("Synth FM"),
  [0XD] = strtext("Ethnic Instruments"),
  [0XE] = strtext("Percussive Instruments"),
  [0XF] = strtext("Sound Effects")
};

const char *const midiInstrumentTable[] = {
/* Piano */
  [0X00] = strtext("Acoustic Grand Piano"),
  [0X01] = strtext("Bright Acoustic Piano"),
  [0X02] = strtext("Electric Grand Piano"),
  [0X03] = strtext("Honkytonk Piano"),
  [0X04] = strtext("Electric Piano 1"),
  [0X05] = strtext("Electric Piano 2"),
  [0X06] = strtext("Harpsichord"),
  [0X07] = strtext("Clavi"),
/* Chromatic Percussion */
  [0X08] = strtext("Celesta"),
  [0X09] = strtext("Glockenspiel"),
  [0X0A] = strtext("Music Box"),
  [0X0B] = strtext("Vibraphone"),
  [0X0C] = strtext("Marimba"),
  [0X0D] = strtext("Xylophone"),
  [0X0E] = strtext("Tubular Bells"),
  [0X0F] = strtext("Dulcimer"),
/* Organ */
  [0X10] = strtext("Drawbar Organ"),
  [0X11] = strtext("Percussive Organ"),
  [0X12] = strtext("Rock Organ"),
  [0X13] = strtext("Church Organ"),
  [0X14] = strtext("Reed Organ"),
  [0X15] = strtext("Accordion"),
  [0X16] = strtext("Harmonica"),
  [0X17] = strtext("Tango Accordion"),
/* Guitar */
  [0X18] = strtext("Acoustic Guitar (nylon)"),
  [0X19] = strtext("Acoustic Guitar (steel)"),
  [0X1A] = strtext("Electric Guitar (jazz)"),
  [0X1B] = strtext("Electric Guitar (clean)"),
  [0X1C] = strtext("Electric Guitar (muted)"),
  [0X1D] = strtext("Overdriven Guitar"),
  [0X1E] = strtext("Distortion Guitar"),
  [0X1F] = strtext("Guitar Harmonics"),
/* Bass */
  [0X20] = strtext("Acoustic Bass"),
  [0X21] = strtext("Electric Bass (finger)"),
  [0X22] = strtext("Electric Bass (pick)"),
  [0X23] = strtext("Fretless Bass"),
  [0X24] = strtext("Slap Bass 1"),
  [0X25] = strtext("Slap Bass 2"),
  [0X26] = strtext("Synth Bass 1"),
  [0X27] = strtext("Synth Bass 2"),
/* Strings */
  [0X28] = strtext("Violin"),
  [0X29] = strtext("Viola"),
  [0X2A] = strtext("Cello"),
  [0X2B] = strtext("Contrabass"),
  [0X2C] = strtext("Tremolo Strings"),
  [0X2D] = strtext("Pizzicato Strings"),
  [0X2E] = strtext("Orchestral Harp"),
  [0X2F] = strtext("Timpani"),
/* Ensemble */
  [0X30] = strtext("String Ensemble 1"),
  [0X31] = strtext("String Ensemble 2"),
  [0X32] = strtext("SynthStrings 1"),
  [0X33] = strtext("SynthStrings 2"),
  [0X34] = strtext("Choir Aahs"),
  [0X35] = strtext("Voice Oohs"),
  [0X36] = strtext("Synth Voice"),
  [0X37] = strtext("Orchestra Hit"),
/* Brass */
  [0X38] = strtext("Trumpet"),
  [0X39] = strtext("Trombone"),
  [0X3A] = strtext("Tuba"),
  [0X3B] = strtext("Muted Trumpet"),
  [0X3C] = strtext("French Horn"),
  [0X3D] = strtext("Brass Section"),
  [0X3E] = strtext("SynthBrass 1"),
  [0X3F] = strtext("SynthBrass 2"),
/* Reed */
  [0X40] = strtext("Soprano Sax"),
  [0X41] = strtext("Alto Sax"),
  [0X42] = strtext("Tenor Sax"),
  [0X43] = strtext("Baritone Sax"),
  [0X44] = strtext("Oboe"),
  [0X45] = strtext("English Horn"),
  [0X46] = strtext("Bassoon"),
  [0X47] = strtext("Clarinet"),
/* Pipe */
  [0X48] = strtext("Piccolo"),
  [0X49] = strtext("Flute"),
  [0X4A] = strtext("Recorder"),
  [0X4B] = strtext("Pan Flute"),
  [0X4C] = strtext("Blown Bottle"),
  [0X4D] = strtext("Shakuhachi"),
  [0X4E] = strtext("Whistle"),
  [0X4F] = strtext("Ocarina"),
/* Synth Lead */
  [0X50] = strtext("Lead 1 (square)"),
  [0X51] = strtext("Lead 2 (sawtooth)"),
  [0X52] = strtext("Lead 3 (calliope)"),
  [0X53] = strtext("Lead 4 (chiff)"),
  [0X54] = strtext("Lead 5 (charang)"),
  [0X55] = strtext("Lead 6 (voice)"),
  [0X56] = strtext("Lead 7 (fifths)"),
  [0X57] = strtext("Lead 8 (bass + lead)"),
/* Synth Pad */
  [0X58] = strtext("Pad 1 (new age)"),
  [0X59] = strtext("Pad 2 (warm)"),
  [0X5A] = strtext("Pad 3 (polysynth)"),
  [0X5B] = strtext("Pad 4 (choir)"),
  [0X5C] = strtext("Pad 5 (bowed)"),
  [0X5D] = strtext("Pad 6 (metallic)"),
  [0X5E] = strtext("Pad 7 (halo)"),
  [0X5F] = strtext("Pad 8 (sweep)"),
/* Synth FM */
  [0X60] = strtext("FX 1 (rain)"),
  [0X61] = strtext("FX 2 (soundtrack)"),
  [0X62] = strtext("FX 3 (crystal)"),
  [0X63] = strtext("FX 4 (atmosphere)"),
  [0X64] = strtext("FX 5 (brightness)"),
  [0X65] = strtext("FX 6 (goblins)"),
  [0X66] = strtext("FX 7 (echoes)"),
  [0X67] = strtext("FX 8 (sci-fi)"),
/* Ethnic Instruments */
  [0X68] = strtext("Sitar"),
  [0X69] = strtext("Banjo"),
  [0X6A] = strtext("Shamisen"),
  [0X6B] = strtext("Koto"),
  [0X6C] = strtext("Kalimba"),
  [0X6D] = strtext("Bag Pipe"),
  [0X6E] = strtext("Fiddle"),
  [0X6F] = strtext("Shanai"),
/* Percussive Instruments */
  [0X70] = strtext("Tinkle Bell"),
  [0X71] = strtext("Agogo"),
  [0X72] = strtext("Steel Drums"),
  [0X73] = strtext("Woodblock"),
  [0X74] = strtext("Taiko Drum"),
  [0X75] = strtext("Melodic Tom"),
  [0X76] = strtext("Synth Drum"),
  [0X77] = strtext("Reverse Cymbal"),
/* Sound Effects */
  [0X78] = strtext("Guitar Fret Noise"),
  [0X79] = strtext("Breath Noise"),
  [0X7A] = strtext("Seashore"),
  [0X7B] = strtext("Bird Tweet"),
  [0X7C] = strtext("Telephone Ring"),
  [0X7D] = strtext("Helicopter"),
  [0X7E] = strtext("Applause"),
  [0X7F] = strtext("Gunshot")
};
const unsigned int midiInstrumentCount = ARRAY_COUNT(midiInstrumentTable);

const char *
midiGetInstrumentType (unsigned char instrument) {
  return midiInstrumentTypes[instrument >> 3];
}

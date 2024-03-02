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

#include "midi.h"

static const char *const midiInstrumentGroups[] = {
  // xgettext: This is the name of MIDI musical instrument group #1.
  [0X0] = strtext("Piano"),

  // xgettext: This is the name of MIDI musical instrument group #2.
  [0X1] = strtext("Chromatic Percussion"),

  // xgettext: This is the name of MIDI musical instrument group #3.
  [0X2] = strtext("Organ"),

  // xgettext: This is the name of MIDI musical instrument group #4.
  [0X3] = strtext("Guitar"),

  // xgettext: This is the name of MIDI musical instrument group #5.
  [0X4] = strtext("Bass"),

  // xgettext: This is the name of MIDI musical instrument group #6.
  [0X5] = strtext("Strings"),

  // xgettext: This is the name of MIDI musical instrument group #7.
  [0X6] = strtext("Ensemble"),

  // xgettext: This is the name of MIDI musical instrument group #8.
  [0X7] = strtext("Brass"),

  // xgettext: This is the name of MIDI musical instrument group #9.
  [0X8] = strtext("Reed"),

  // xgettext: This is the name of MIDI musical instrument group #10.
  [0X9] = strtext("Pipe"),

  // xgettext: This is the name of MIDI musical instrument group #11.
  // xgettext: (synth is a common short form for synthesizer)
  [0XA] = strtext("Synth Lead"),

  // xgettext: This is the name of MIDI musical instrument group #12.
  // xgettext: (synth is a common short form for synthesizer)
  [0XB] = strtext("Synth Pad"),

  // xgettext: This is the name of MIDI musical instrument group #13.
  // xgettext: (synth is a common short form for synthesizer)
  // xgettext: (FM is the acronym for Frequency Modulation)
  [0XC] = strtext("Synth FM"),

  // xgettext: This is the name of MIDI musical instrument group #14.
  [0XD] = strtext("Ethnic Instruments"),

  // xgettext: This is the name of MIDI musical instrument group #15.
  [0XE] = strtext("Percussive Instruments"),

  // xgettext: This is the name of MIDI musical instrument group #16.
  [0XF] = strtext("Sound Effects")
};

const char *const midiInstrumentTable[] = {
/* Piano */
  // xgettext: This is the name of MIDI musical instrument #1 (in the Piano group).
  [0X00] = strtext("Acoustic Grand Piano"),

  // xgettext: This is the name of MIDI musical instrument #2 (in the Piano group).
  [0X01] = strtext("Bright Acoustic Piano"),

  // xgettext: This is the name of MIDI musical instrument #3 (in the Piano group).
  [0X02] = strtext("Electric Grand Piano"),

  // xgettext: This is the name of MIDI musical instrument #4 (in the Piano group).
  [0X03] = strtext("Honkytonk Piano"),

  // xgettext: This is the name of MIDI musical instrument #5 (in the Piano group).
  [0X04] = strtext("Electric Piano 1"),

  // xgettext: This is the name of MIDI musical instrument #6 (in the Piano group).
  [0X05] = strtext("Electric Piano 2"),

  // xgettext: This is the name of MIDI musical instrument #7 (in the Piano group).
  [0X06] = strtext("Harpsichord"),

  // xgettext: This is the name of MIDI musical instrument #8 (in the Piano group).
  [0X07] = strtext("Clavinet"),

/* Chromatic Percussion */
  // xgettext: This is the name of MIDI musical instrument #9 (in the Chromatic Percussion group).
  [0X08] = strtext("Celesta"),

  // xgettext: This is the name of MIDI musical instrument #10 (in the Chromatic Percussion group).
  [0X09] = strtext("Glockenspiel"),

  // xgettext: This is the name of MIDI musical instrument #11 (in the Chromatic Percussion group).
  [0X0A] = strtext("Music Box"),

  // xgettext: This is the name of MIDI musical instrument #12 (in the Chromatic Percussion group).
  [0X0B] = strtext("Vibraphone"),

  // xgettext: This is the name of MIDI musical instrument #13 (in the Chromatic Percussion group).
  [0X0C] = strtext("Marimba"),

  // xgettext: This is the name of MIDI musical instrument #14 (in the Chromatic Percussion group).
  [0X0D] = strtext("Xylophone"),

  // xgettext: This is the name of MIDI musical instrument #15 (in the Chromatic Percussion group).
  [0X0E] = strtext("Tubular Bells"),

  // xgettext: This is the name of MIDI musical instrument #16 (in the Chromatic Percussion group).
  [0X0F] = strtext("Dulcimer"),

/* Organ */
  // xgettext: This is the name of MIDI musical instrument #17 (in the Organ group).
  [0X10] = strtext("Drawbar Organ"),

  // xgettext: This is the name of MIDI musical instrument #18 (in the Organ group).
  [0X11] = strtext("Percussive Organ"),

  // xgettext: This is the name of MIDI musical instrument #19 (in the Organ group).
  [0X12] = strtext("Rock Organ"),

  // xgettext: This is the name of MIDI musical instrument #20 (in the Organ group).
  [0X13] = strtext("Church Organ"),

  // xgettext: This is the name of MIDI musical instrument #21 (in the Organ group).
  [0X14] = strtext("Reed Organ"),

  // xgettext: This is the name of MIDI musical instrument #22 (in the Organ group).
  [0X15] = strtext("Accordion"),

  // xgettext: This is the name of MIDI musical instrument #23 (in the Organ group).
  [0X16] = strtext("Harmonica"),

  // xgettext: This is the name of MIDI musical instrument #24 (in the Organ group).
  [0X17] = strtext("Tango Accordion"),

/* Guitar */
  // xgettext: This is the name of MIDI musical instrument #25 (in the Guitar group).
  [0X18] = strtext("Acoustic Guitar (nylon)"),

  // xgettext: This is the name of MIDI musical instrument #26 (in the Guitar group).
  [0X19] = strtext("Acoustic Guitar (steel)"),

  // xgettext: This is the name of MIDI musical instrument #27 (in the Guitar group).
  [0X1A] = strtext("Electric Guitar (jazz)"),

  // xgettext: This is the name of MIDI musical instrument #28 (in the Guitar group).
  [0X1B] = strtext("Electric Guitar (clean)"),

  // xgettext: This is the name of MIDI musical instrument #29 (in the Guitar group).
  [0X1C] = strtext("Electric Guitar (muted)"),

  // xgettext: This is the name of MIDI musical instrument #30 (in the Guitar group).
  [0X1D] = strtext("Overdriven Guitar"),

  // xgettext: This is the name of MIDI musical instrument #31 (in the Guitar group).
  [0X1E] = strtext("Distortion Guitar"),

  // xgettext: This is the name of MIDI musical instrument #32 (in the Guitar group).
  [0X1F] = strtext("Guitar Harmonics"),

/* Bass */
  // xgettext: This is the name of MIDI musical instrument #33 (in the Bass group).
  [0X20] = strtext("Acoustic Bass"),

  // xgettext: This is the name of MIDI musical instrument #34 (in the Bass group).
  [0X21] = strtext("Electric Bass (finger)"),

  // xgettext: This is the name of MIDI musical instrument #35 (in the Bass group).
  [0X22] = strtext("Electric Bass (pick)"),

  // xgettext: This is the name of MIDI musical instrument #36 (in the Bass group).
  [0X23] = strtext("Fretless Bass"),

  // xgettext: This is the name of MIDI musical instrument #37 (in the Bass group).
  [0X24] = strtext("Slap Bass 1"),

  // xgettext: This is the name of MIDI musical instrument #38 (in the Bass group).
  [0X25] = strtext("Slap Bass 2"),

  // xgettext: This is the name of MIDI musical instrument #39 (in the Bass group).
  [0X26] = strtext("Synth Bass 1"),

  // xgettext: This is the name of MIDI musical instrument #40 (in the Bass group).
  [0X27] = strtext("Synth Bass 2"),

/* Strings */
  // xgettext: This is the name of MIDI musical instrument #41 (in the Strings group).
  [0X28] = strtext("Violin"),

  // xgettext: This is the name of MIDI musical instrument #42 (in the Strings group).
  [0X29] = strtext("Viola"),

  // xgettext: This is the name of MIDI musical instrument #43 (in the Strings group).
  [0X2A] = strtext("Cello"),

  // xgettext: This is the name of MIDI musical instrument #44 (in the Strings group).
  [0X2B] = strtext("Contrabass"),

  // xgettext: This is the name of MIDI musical instrument #45 (in the Strings group).
  [0X2C] = strtext("Tremolo Strings"),

  // xgettext: This is the name of MIDI musical instrument #46 (in the Strings group).
  [0X2D] = strtext("Pizzicato Strings"),

  // xgettext: This is the name of MIDI musical instrument #47 (in the Strings group).
  [0X2E] = strtext("Orchestral Harp"),

  // xgettext: This is the name of MIDI musical instrument #48 (in the Strings group).
  [0X2F] = strtext("Timpani"),

/* Ensemble */
  // xgettext: This is the name of MIDI musical instrument #49 (in the Ensemble group).
  [0X30] = strtext("String Ensemble 1"),

  // xgettext: This is the name of MIDI musical instrument #50 (in the Ensemble group).
  [0X31] = strtext("String Ensemble 2"),

  // xgettext: This is the name of MIDI musical instrument #51 (in the Ensemble group).
  [0X32] = strtext("SynthStrings 1"),

  // xgettext: This is the name of MIDI musical instrument #52 (in the Ensemble group).
  [0X33] = strtext("SynthStrings 2"),

  // xgettext: This is the name of MIDI musical instrument #53 (in the Ensemble group).
  [0X34] = strtext("Choir Aahs"),

  // xgettext: This is the name of MIDI musical instrument #54 (in the Ensemble group).
  [0X35] = strtext("Voice Oohs"),

  // xgettext: This is the name of MIDI musical instrument #55 (in the Ensemble group).
  [0X36] = strtext("Synth Voice"),

  // xgettext: This is the name of MIDI musical instrument #56 (in the Ensemble group).
  [0X37] = strtext("Orchestra Hit"),

/* Brass */
  // xgettext: This is the name of MIDI musical instrument #57 (in the Brass group).
  [0X38] = strtext("Trumpet"),

  // xgettext: This is the name of MIDI musical instrument #58 (in the Brass group).
  [0X39] = strtext("Trombone"),

  // xgettext: This is the name of MIDI musical instrument #59 (in the Brass group).
  [0X3A] = strtext("Tuba"),

  // xgettext: This is the name of MIDI musical instrument #60 (in the Brass group).
  [0X3B] = strtext("Muted Trumpet"),

  // xgettext: This is the name of MIDI musical instrument #61 (in the Brass group).
  [0X3C] = strtext("French Horn"),

  // xgettext: This is the name of MIDI musical instrument #62 (in the Brass group).
  [0X3D] = strtext("Brass Section"),

  // xgettext: This is the name of MIDI musical instrument #63 (in the Brass group).
  [0X3E] = strtext("SynthBrass 1"),

  // xgettext: This is the name of MIDI musical instrument #64 (in the Brass group).
  [0X3F] = strtext("SynthBrass 2"),

/* Reed */
  // xgettext: This is the name of MIDI musical instrument #65 (in the Reed group).
  [0X40] = strtext("Soprano Saxophone"),

  // xgettext: This is the name of MIDI musical instrument #66 (in the Reed group).
  [0X41] = strtext("Alto Saxophone"),

  // xgettext: This is the name of MIDI musical instrument #67 (in the Reed group).
  [0X42] = strtext("Tenor Saxophone"),

  // xgettext: This is the name of MIDI musical instrument #68 (in the Reed group).
  [0X43] = strtext("Baritone Saxophone"),

  // xgettext: This is the name of MIDI musical instrument #69 (in the Reed group).
  [0X44] = strtext("Oboe"),

  // xgettext: This is the name of MIDI musical instrument #70 (in the Reed group).
  [0X45] = strtext("English Horn"),

  // xgettext: This is the name of MIDI musical instrument #71 (in the Reed group).
  [0X46] = strtext("Bassoon"),

  // xgettext: This is the name of MIDI musical instrument #72 (in the Reed group).
  [0X47] = strtext("Clarinet"),

/* Pipe */
  // xgettext: This is the name of MIDI musical instrument #73 (in the Pipe group).
  [0X48] = strtext("Piccolo"),

  // xgettext: This is the name of MIDI musical instrument #74 (in the Pipe group).
  [0X49] = strtext("Flute"),

  // xgettext: This is the name of MIDI musical instrument #75 (in the Pipe group).
  [0X4A] = strtext("Recorder"),

  // xgettext: This is the name of MIDI musical instrument #76 (in the Pipe group).
  [0X4B] = strtext("Pan Flute"),

  // xgettext: This is the name of MIDI musical instrument #77 (in the Pipe group).
  [0X4C] = strtext("Blown Bottle"),

  // xgettext: This is the name of MIDI musical instrument #78 (in the Pipe group).
  [0X4D] = strtext("Shakuhachi"),

  // xgettext: This is the name of MIDI musical instrument #79 (in the Pipe group).
  [0X4E] = strtext("Whistle"),

  // xgettext: This is the name of MIDI musical instrument #80 (in the Pipe group).
  [0X4F] = strtext("Ocarina"),

/* Synth Lead */
  // xgettext: This is the name of MIDI musical instrument #81 (in the Synth Lead group).
  [0X50] = strtext("Lead 1 (square)"),

  // xgettext: This is the name of MIDI musical instrument #82 (in the Synth Lead group).
  [0X51] = strtext("Lead 2 (sawtooth)"),

  // xgettext: This is the name of MIDI musical instrument #83 (in the Synth Lead group).
  [0X52] = strtext("Lead 3 (calliope)"),

  // xgettext: This is the name of MIDI musical instrument #84 (in the Synth Lead group).
  [0X53] = strtext("Lead 4 (chiff)"),

  // xgettext: This is the name of MIDI musical instrument #85 (in the Synth Lead group).
  [0X54] = strtext("Lead 5 (charang)"),

  // xgettext: This is the name of MIDI musical instrument #86 (in the Synth Lead group).
  [0X55] = strtext("Lead 6 (voice)"),

  // xgettext: This is the name of MIDI musical instrument #87 (in the Synth Lead group).
  [0X56] = strtext("Lead 7 (fifths)"),

  // xgettext: This is the name of MIDI musical instrument #88 (in the Synth Lead group).
  [0X57] = strtext("Lead 8 (bass + lead)"),

/* Synth Pad */
  // xgettext: This is the name of MIDI musical instrument #89 (in the Synth Pad group).
  [0X58] = strtext("Pad 1 (new age)"),

  // xgettext: This is the name of MIDI musical instrument #90 (in the Synth Pad group).
  [0X59] = strtext("Pad 2 (warm)"),

  // xgettext: This is the name of MIDI musical instrument #91 (in the Synth Pad group).
  [0X5A] = strtext("Pad 3 (polysynth)"),

  // xgettext: This is the name of MIDI musical instrument #92 (in the Synth Pad group).
  [0X5B] = strtext("Pad 4 (choir)"),

  // xgettext: This is the name of MIDI musical instrument #93 (in the Synth Pad group).
  [0X5C] = strtext("Pad 5 (bowed)"),

  // xgettext: This is the name of MIDI musical instrument #94 (in the Synth Pad group).
  [0X5D] = strtext("Pad 6 (metallic)"),

  // xgettext: This is the name of MIDI musical instrument #95 (in the Synth Pad group).
  [0X5E] = strtext("Pad 7 (halo)"),

  // xgettext: This is the name of MIDI musical instrument #96 (in the Synth Pad group).
  [0X5F] = strtext("Pad 8 (sweep)"),

/* Synth FM */
  // xgettext: This is the name of MIDI musical instrument #97 (in the Synth FM group).
  [0X60] = strtext("FX 1 (rain)"),

  // xgettext: This is the name of MIDI musical instrument #98 (in the Synth FM group).
  [0X61] = strtext("FX 2 (soundtrack)"),

  // xgettext: This is the name of MIDI musical instrument #99 (in the Synth FM group).
  [0X62] = strtext("FX 3 (crystal)"),

  // xgettext: This is the name of MIDI musical instrument #100 (in the Synth FM group).
  [0X63] = strtext("FX 4 (atmosphere)"),

  // xgettext: This is the name of MIDI musical instrument #101 (in the Synth FM group).
  [0X64] = strtext("FX 5 (brightness)"),

  // xgettext: This is the name of MIDI musical instrument #102 (in the Synth FM group).
  [0X65] = strtext("FX 6 (goblins)"),

  // xgettext: This is the name of MIDI musical instrument #103 (in the Synth FM group).
  [0X66] = strtext("FX 7 (echoes)"),

  // xgettext: This is the name of MIDI musical instrument #104 (in the Synth FM group).
  // xgettext: (sci-fi is a common short form for science fiction)
  [0X67] = strtext("FX 8 (sci-fi)"),

/* Ethnic Instruments */
  // xgettext: This is the name of MIDI musical instrument #105 (in the Ethnic group).
  [0X68] = strtext("Sitar"),

  // xgettext: This is the name of MIDI musical instrument #106 (in the Ethnic group).
  [0X69] = strtext("Banjo"),

  // xgettext: This is the name of MIDI musical instrument #107 (in the Ethnic group).
  [0X6A] = strtext("Shamisen"),

  // xgettext: This is the name of MIDI musical instrument #108 (in the Ethnic group).
  [0X6B] = strtext("Koto"),

  // xgettext: This is the name of MIDI musical instrument #109 (in the Ethnic group).
  [0X6C] = strtext("Kalimba"),

  // xgettext: This is the name of MIDI musical instrument #110 (in the Ethnic group).
  [0X6D] = strtext("Bag Pipe"),

  // xgettext: This is the name of MIDI musical instrument #111 (in the Ethnic group).
  [0X6E] = strtext("Fiddle"),

  // xgettext: This is the name of MIDI musical instrument #112 (in the Ethnic group).
  [0X6F] = strtext("Shanai"),

/* Percussive Instruments */
  // xgettext: This is the name of MIDI musical instrument #113 (in the Percussive group).
  [0X70] = strtext("Tinkle Bell"),

  // xgettext: This is the name of MIDI musical instrument #114 (in the Percussive group).
  [0X71] = strtext("Agogo"),

  // xgettext: This is the name of MIDI musical instrument #115 (in the Percussive group).
  [0X72] = strtext("Steel Drums"),

  // xgettext: This is the name of MIDI musical instrument #116 (in the Percussive group).
  [0X73] = strtext("Woodblock"),

  // xgettext: This is the name of MIDI musical instrument #117 (in the Percussive group).
  [0X74] = strtext("Taiko Drum"),

  // xgettext: This is the name of MIDI musical instrument #118 (in the Percussive group).
  [0X75] = strtext("Melodic Tom"),

  // xgettext: This is the name of MIDI musical instrument #119 (in the Percussive group).
  [0X76] = strtext("Synth Drum"),

  // xgettext: This is the name of MIDI musical instrument #120 (in the Percussive group).
  [0X77] = strtext("Reverse Cymbal"),

/* Sound Effects */
  // xgettext: This is the name of MIDI musical instrument #121 (in the Sound Effects group).
  [0X78] = strtext("Guitar Fret Noise"),

  // xgettext: This is the name of MIDI musical instrument #122 (in the Sound Effects group).
  [0X79] = strtext("Breath Noise"),

  // xgettext: This is the name of MIDI musical instrument #123 (in the Sound Effects group).
  [0X7A] = strtext("Seashore"),

  // xgettext: This is the name of MIDI musical instrument #124 (in the Sound Effects group).
  [0X7B] = strtext("Bird Tweet"),

  // xgettext: This is the name of MIDI musical instrument #125 (in the Sound Effects group).
  [0X7C] = strtext("Telephone Ring"),

  // xgettext: This is the name of MIDI musical instrument #126 (in the Sound Effects group).
  [0X7D] = strtext("Helicopter"),

  // xgettext: This is the name of MIDI musical instrument #127 (in the Sound Effects group).
  [0X7E] = strtext("Applause"),

  // xgettext: This is the name of MIDI musical instrument #128 (in the Sound Effects group).
  [0X7F] = strtext("Gunshot")
};
const unsigned int midiInstrumentCount = ARRAY_COUNT(midiInstrumentTable);

const char *
midiGetInstrumentGroup (unsigned char instrument) {
  return midiInstrumentGroups[instrument >> 3];
}

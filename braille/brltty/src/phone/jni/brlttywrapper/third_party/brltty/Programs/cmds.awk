###############################################################################
# BRLTTY - A background process providing access to the console screen (when in
#          text mode) for a blind person using a refreshable braille display.
#
# Copyright (C) 1995-2019 by The BRLTTY Developers.
#
# BRLTTY comes with ABSOLUTELY NO WARRANTY.
#
# This is free software, placed under the terms of the
# GNU Lesser General Public License, as published by the Free Software
# Foundation; either version 2.1 of the License, or (at your option) any
# later version. Please see the file LICENSE-LGPL for details.
#
# Web Page: http://brltty.app/
#
# This software is maintained by Dave Mielke <dave@mielke.cc>.
###############################################################################

function writeCommandField(name, value) {
  print "  ." name " = " value ","
}

function writeCommandAttribute(attribute) {
  writeCommandField("is" attribute, "1")
}

function writeCommandEntry(name, symbol, value, help) {
  if (help ~ /^deprecated /) return

  print "{ // " symbol
  writeCommandField("name", "\"" name "\"")
  writeCommandField("code", value)

  if (help ~ /^set .*\//) writeCommandAttribute("Toggle")
  if (help ~ /^bring /) writeCommandAttribute("Routing")

  if (help ~ /^go /) {
    writeCommandAttribute("Motion")
    if (help ~ / (up|down|top|bottom) /) writeCommandAttribute("Vertical")
    if (help ~ / (left|right|beginning|end) /) writeCommandAttribute("Horizontal")
    if (help ~ / (backward|forward) /) writeCommandAttribute("Panning")
  }

  if (symbol ~ /^BRL_BLK_/) {
    if (symbol ~ /^BRL_BLK_PASS/) {
      if (symbol ~ /PASS(CHAR|DOTS|KEY)/) {
        writeCommandAttribute("Input")
      }

      if (symbol ~ /PASSCHAR/) {
        writeCommandAttribute("Character")
      }

      if (symbol ~ /PASSDOTS/) {
        writeCommandAttribute("Braille")
      }

      if (symbol ~ /PASS(XT|AT|PS2)/) {
        writeCommandAttribute("Keyboard")
      }
    } else if (help ~ / character$/) {
      writeCommandAttribute("Column")
    } else if (help ~ / characters /) {
      writeCommandAttribute("Range")
    } else if (help ~ / line$/) {
      writeCommandAttribute("Row")
      writeCommandAttribute("Vertical")
    } else {
      writeCommandAttribute("Offset")
    }
  } else if (symbol ~ /^BRL_KEY_/) {
    writeCommandAttribute("Input")

    if (symbol ~ /_FUNCTION/) {
      writeCommandAttribute("Offset")
    }
  }

  writeCommandField("description", "strtext(\"" help "\")")
  print "},"
  print ""
}

function brlCommand(name, symbol, value, help) {
  writeCommandEntry(name, symbol, symbol, help)
}

function brlBlock(name, symbol, value, help) {
  writeCommandEntry(name, symbol, "BRL_CMD_BLK(" name ")", help)
}

function brlKey(name, symbol, value, help) {
  writeCommandEntry("KEY_" name, symbol, "BRL_CMD_KEY(" name ")", help)
}

function brlFlag(name, symbol, value, help) {
}

function brlDot(number, symbol, value, help) {
}

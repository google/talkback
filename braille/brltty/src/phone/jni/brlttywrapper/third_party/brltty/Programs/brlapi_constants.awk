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

BEGIN {
  writeHeaderPrologue("BRLAPI_INCLUDED_CONSTANTS", "brlapi.h")
  beginDoxygenFile()
  print "/** \\ingroup brlapi_keycodes"
  print " * @{ */"
  print ""
}

END {
  writeBrlapiDots()
  print "/** @} */"
  print ""
  writeHeaderEpilogue()
}

function brlCommand(name, symbol, value, help) {
  writeMacroDefinition("BRLAPI_KEY_CMD_" name, "(BRLAPI_KEY_CMD(0) + " value ")", help)
}

function brlBlock(name, symbol, value, help) {
  if (name == "PASSCHAR") return
  if (name == "PASSKEY") return

  writeMacroDefinition("BRLAPI_KEY_CMD_" name, "BRLAPI_KEY_CMD(" value ")", help)
}

function brlKey(name, symbol, value, help) {
}

function brlFlag(name, symbol, value, help) {
  if (value ~ /^0[xX][0-9a-fA-F]+0000$/) {
    value = substr(value, 1, length(value)-4)
    if (name ~ /^INPUT_/) {
      name = substr(name, 7)
    } else {
      value = value "00"
    }
    value = "BRLAPI_KEY_FLG(" value ")"
  } else if (value ~ /^\(/) {
    gsub("BRL_FLG_", "BRLAPI_KEY_FLG_", value)
  } else {
    return
  }
  writeMacroDefinition("BRLAPI_KEY_FLG_" name, value, help)
}

function brlDot(number, symbol, value, help) {
  writeMacroDefinition("BRLAPI_DOT" number, value, help)
}

function writeBrlapiDots() {
  print ""
  print "/** Helper macro to easily produce braille patterns */"

  arguments = ""
  argumentDelimiter = ""
  expression = ""
  subexpressionDelimiter = ""

  for (dotNumber=1; dotNumber<=8; ++dotNumber) {
    argumentName = "dot" dotNumber

    arguments = arguments argumentDelimiter argumentName
    argumentDelimiter = ", "

    subexpression = "((" argumentName ")? BRLAPI_DOT" dotNumber ": 0)"
    expression = expression subexpressionDelimiter "  " subexpression
    subexpressionDelimiter = " | \\\n"
  }

  print "#define BRLAPI_DOTS(" arguments ") (\\\n" expression " \\\n)"

  print ""
  writeMacroDefinition("BRLAPI_DOT_CHORD", 0X100, "space key")
}

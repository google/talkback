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
  writeHeaderPrologue("BRLAPI_INCLUDED_KEYTAB", "api.h")
}

END {
  writeHeaderEpilogue()
}

function brlCommand(name, symbol, value, help) {
  writeSimpleKey("CMD", name)
}

function brlBlock(name, symbol, value, help) {
  if (name == "PASSCHAR") return
  if (name == "PASSKEY") return
  writeSimpleKey("CMD", name)
}

function brlKey(name, symbol, value, help) {
  if (name == "FUNCTION") {
    for (functionNumber=1; functionNumber<=35; ++functionNumber) {
      writeComplexKey("F" functionNumber, "BRLAPI_KEY_SYM_FUNCTION+" (functionNumber - 1))
    }
  } else {
    writeSimpleKey("SYM", getBrlapiKeyName(name))
  }
}

function brlFlag(name, symbol, value, help) {
}

function brlDot(number, symbol, value, help) {
}

function writeSimpleKey(type, name) {
  writeComplexKey(name, "(BRLAPI_KEY_TYPE_" type " | BRLAPI_KEY_" type "_" name ")")
}

function writeComplexKey(name, code) {
  print "{"
  print "  .name = \"" name "\","
  print "  .code = " code
  print "},"
}

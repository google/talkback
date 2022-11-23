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
  writeHeaderPrologue("BRLAPI_INCLUDED_BRLDEFS", "api.h")
}

END {
  writeMacroDefinition("BRL_MSK_BLK", "(BRLAPI_KEY_TYPE_MASK | BRLAPI_KEY_CMD_BLK_MASK)", "mask for command type")
  writeMaskDefinition("ARG", "mask for command value/argument")
  writeMacroDefinition("BRL_MSK_FLG", "BRLAPI_KEY_FLAGS_MASK", "mask for command flags")
  writeMacroDefinition("BRL_MSK_CMD", "(BRL_MSK_BLK | BRL_MSK_ARG)", "mask for command")

  writeHeaderEpilogue()
}

function writeMaskDefinition(name, help) {
  writeSymbolDefinition(name, "BRL_MSK_", "", "BRLAPI_KEY_CMD_", "_MASK", help)
}

function writeSymbolDefinition(name, brlPrefix, brlSuffix, apiPrefix, apiSuffix, help) {
  writeMacroDefinition(brlPrefix name brlSuffix, apiPrefix name apiSuffix, help)
}

function brlCommand(name, symbol, value, help) {
  writeSymbolDefinition(name, "BRL_CMD_", "", "(BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_", ")", help)
}

function brlBlock(name, symbol, value, help) {
  if (name == "PASSCHAR") {
    writeMacroDefinition("BRL_KEY_" name, "(BRLAPI_KEY_TYPE_SYM | 0X0000)", help)
  } else if (name == "PASSKEY") {
    writeMacroDefinition("BRL_KEY_" name, "(BRLAPI_KEY_TYPE_SYM | 0XFF00)", help)
  } else {
    writeSymbolDefinition(name, "BRL_BLK_", "", "(BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_", ")", help)
  }
}

function brlKey(name, symbol, value, help) {
  writeMacroDefinition("BRL_KEY_" name, "(BRLAPI_KEY_SYM_" getBrlapiKeyName(name) " & 0XFF)", help)
}

function brlFlag(name, symbol, value, help) {
  if (match(name, "^[^_]*_")) {
    type = substr(name, 1, RLENGTH-1)
    if (type == "CHAR") {
      name = substr(name, RLENGTH+1)
      writeSymbolDefinition(name, "BRL_FLG_" type "_", "", "BRLAPI_KEY_FLG_", "", help)
      return
    }
  }
  writeSymbolDefinition(name, "BRL_FLG_", "", "BRLAPI_KEY_FLG_", "", help)
}

function brlDot(number, symbol, value, help) {
  writeSymbolDefinition(number, "BRL_DOT", "", "BRLAPI_DOT", "", help)
}

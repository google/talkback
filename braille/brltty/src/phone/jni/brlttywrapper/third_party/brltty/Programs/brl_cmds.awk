###############################################################################
# BRLTTY - A background process providing access to the console screen (when in
#          text mode) for a blind person using a refreshable braille display.
#
# Copyright (C) 1995-2023 by The BRLTTY Developers.
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
  brlCommandValue = 0
  brlBlockValue = 0
  brlKeyValue = 0

  brlBlockDeprecations["CLIP_NEW"] = "CUTBEGIN"
  brlBlockDeprecations["CLIP_ADD"] = "CUTAPPEND"
  brlBlockDeprecations["COPY_LINE"] = "CUTLINE"
  brlBlockDeprecations["COPY_RECT"] = "CUTRECT"
  brlBlockDeprecations["CLIP_COPY"] = "COPYCHARS"
  brlBlockDeprecations["CLIP_APPEND"] = "APNDCHARS"
}

/^[[:space:]]*BRL_CMD_/ {
  brlCommand(substr($1, 9), $1, brlCommandValue++, getComment($0))
  next
}

/^[[:space:]]*BRL_BLK_/ {
  prefix = substr($1, 1, 8)
  name = substr($1, 9)
  value = brlBlockValue++
  help = getComment($0)

  if (help !~ /^\(/) {
    brlBlock(name, $1, value, help)

    if (name in brlBlockDeprecations) {
      alias = brlBlockDeprecations[name]
      brlBlock(alias, prefix alias, value, "deprecated definition of " name " - " help)
    }
  }

  next
}

/^[[:space:]]*BRL_KEY_/ {
  gsub(",", "", $1)
  key = tolower(substr($1, 9))
  gsub("_", "-", key)
  brlKey(substr($1, 9), $1, brlKeyValue++, key " key")
  next
}

/#define[[:space:]]+BRL_FLG_/ {
  brlFlag(substr($2, 9), $2, getDefineValue(), getComment($0))
  next
}

/#define[[:space:]]+BRL_DOT/ {
  value = getDefineValue()
  sub("^.*\\(", "", value)
  sub("\\).*$", "", value)
  value = 2 ^ (value - 1)
  brlDot(substr($2, 8), $2, value, getComment($0))
  next
}

function writeHeaderPrologue(symbol, copyrightFile) {
  writeCopyright(copyrightFile)

  headerSymbol = symbol
  print "#ifndef " headerSymbol
  writeMacroDefinition(headerSymbol)
  print ""

  print "#ifdef __cplusplus"
  print "extern \"C\" {"
  print "#endif /* __cplusplus */"
  print ""
}

function writeCopyright(file) {
  if (length(file) > 0) {
    while (getline line <file == 1) {
      if (match(line, "^ */\\* *$")) {
        print line

        while (getline line <file == 1) {
          print line
          if (match(line, "^ *\\*/ *$")) break
        }

        print ""
        break
      }
    }
  }
}

function writeHeaderEpilogue() {
  print ""
  print "#ifdef __cplusplus"
  print "}"
  print "#endif /* __cplusplus */"

  print ""
  print "#endif /* " headerSymbol " */"
}

function writeMacroDefinition(name, definition, help) {
  statement = "#define " name
  if (length(definition) > 0) statement = statement " " definition

  if (length(help) > 0) print makeDoxygenComment(help)
  print statement
}

function getComment(line,     comment, last) {
  comment = ""

  if (match(line, "/\\*")) {
    line = substr(line, RSTART+2)
    gsub("^\\**<? *", "", line)

    while (1) {
      last = gsub("\\*/.*$", "", line)
      gsub(" *$", "", line)

      if (length(comment) > 0) comment = comment " "
      comment = comment line
      if (last) break

      getline line
      gsub("^[[:space:]]*\\**[[:space:]]*", "", line)
    }
  } else if (match(line, "//")) {
    comment = substr(line, RSTART+2);
    gsub("^ *", "", comment)

    gsub("\\*/.*$", "", comment)
    gsub(" *$", "", comment)
  }

  return comment
}

function getDefineValue() {
  if ($3 !~ "^\\(") return getNormalizedConstant($3)
  if (match($0, "\\([^)]*\\)")) return substr($0, RSTART, RLENGTH)
  return ""
}

function getNormalizedConstant(value) {
   if (value ~ "^UINT64_C\\(.+\\)$") value = substr(value, 10, length(value)-10)
   return value
}

function makeDoxygenComment(text) {
  return "/** " text " */"
}

function makeTranslatorNote(text) {
  return "// xgettext: " text
}

function beginDoxygenFile() {
  print "/** \\file"
  print " */"
  print ""
}

function getBrlapiKeyName(name) {
  if (name == "ENTER") return "LINEFEED"
  if (name ~ /^CURSOR_/) return substr(name, 8)
  return name
}

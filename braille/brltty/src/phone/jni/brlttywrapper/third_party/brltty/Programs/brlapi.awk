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
  apiRangeTypeCount = 0
  apiParameterTypeCount = 0
}

/#define[[:space:]]+BRLAPI_(CURSOR|DISPLAY|TTY)_/ {
  apiConstant(substr($2, 8), $2, getDefineValue(), "")
  next
}

/^[[:space:]]*BRLAPI_(ERROR)_[^[:space:]]+[[:space:]]+=[[:space:]]/ {
  gsub(",", "", $3)
  apiConstant(substr($1, 8), $1, $3, getComment($0))
  next
}

/#define[[:space:]]+BRLAPI_KEY_MAX/ {
  apiMask(substr($2, 12), $2, getDefineValue(), "")
  next
}

/#define[[:space:]]+BRLAPI_KEY_[A-Z_]+_MASK/ {
  apiMask(substr($2, 12), $2, getDefineValue(), "")
  next
}

/#define[[:space:]]+BRLAPI_KEY_[A-Z_]+_SHIFT/ {
  apiShift(substr($2, 12), $2, getDefineValue(), "")
  next
}

/#define[[:space:]]+BRLAPI_KEY_TYPE_/ {
  apiType(substr($2, 17), $2, getDefineValue(), "")
  next
}

/#define[[:space:]]+BRLAPI_KEY_SYM_/ {
  apiKey(substr($2, 16), $2, getDefineValue(), "")
  next
}

/^[[:space:]]*brlapi_rangeType_/ {
  gsub(",", "", $1)
  apiRangeType(substr($1, 18), $1, apiRangeTypeCount++, "")
  next
}

/#define[[:space:]]+BRLAPI_PARAMF_/ {
  apiConstant(substr($2, 8), $2, getDefineValue(), "")
  next
}

/^[[:space:]]*BRLAPI_PARAM_TYPE_[^[:space:],]+,?/ {
  gsub(",", "", $1)
  name = substr($1, 8)

  if ($2 == "=" ) {
    gsub("^BRLAPI_", "", $3)
    gsub(",", "", $3)
    apiConstant(name, $1, apiParameterTypeValues[$3], getComment($0))
  } else {
    apiParameterTypeValues[name] = apiParameterTypeCount
    apiConstant(name, $1, apiParameterTypeCount++, getComment($0))
  }

  next
}

/^[[:space:]]*BRLAPI_PARAM_[^[:space:]]+[[:space:]]+=[[:space:]]+[0-9]+,?/ {
  gsub(",", "", $3)
  apiConstant(substr($1, 8), $1, $3, getComment($0))
  next
}

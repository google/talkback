{ // BRL_CMD_NOOP
  .name = "NOOP",
  .code = BRL_CMD_NOOP,
  .description = strtext("do nothing"),
},

{ // BRL_CMD_LNUP
  .name = "LNUP",
  .code = BRL_CMD_LNUP,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go up one line"),
},

{ // BRL_CMD_LNDN
  .name = "LNDN",
  .code = BRL_CMD_LNDN,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go down one line"),
},

{ // BRL_CMD_WINUP
  .name = "WINUP",
  .code = BRL_CMD_WINUP,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go up several lines"),
},

{ // BRL_CMD_WINDN
  .name = "WINDN",
  .code = BRL_CMD_WINDN,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go down several lines"),
},

{ // BRL_CMD_PRDIFLN
  .name = "PRDIFLN",
  .code = BRL_CMD_PRDIFLN,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go up to nearest line with different content"),
},

{ // BRL_CMD_NXDIFLN
  .name = "NXDIFLN",
  .code = BRL_CMD_NXDIFLN,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go down to nearest line with different content"),
},

{ // BRL_CMD_ATTRUP
  .name = "ATTRUP",
  .code = BRL_CMD_ATTRUP,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go up to nearest line with different highlighting"),
},

{ // BRL_CMD_ATTRDN
  .name = "ATTRDN",
  .code = BRL_CMD_ATTRDN,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go down to nearest line with different highlighting"),
},

{ // BRL_CMD_TOP
  .name = "TOP",
  .code = BRL_CMD_TOP,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go to top line"),
},

{ // BRL_CMD_BOT
  .name = "BOT",
  .code = BRL_CMD_BOT,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go to bottom line"),
},

{ // BRL_CMD_TOP_LEFT
  .name = "TOP_LEFT",
  .code = BRL_CMD_TOP_LEFT,
  .isMotion = 1,
  .isVertical = 1,
  .isHorizontal = 1,
  .description = strtext("go to beginning of top line"),
},

{ // BRL_CMD_BOT_LEFT
  .name = "BOT_LEFT",
  .code = BRL_CMD_BOT_LEFT,
  .isMotion = 1,
  .isVertical = 1,
  .isHorizontal = 1,
  .description = strtext("go to beginning of bottom line"),
},

{ // BRL_CMD_PRPGRPH
  .name = "PRPGRPH",
  .code = BRL_CMD_PRPGRPH,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go up to first line of paragraph"),
},

{ // BRL_CMD_NXPGRPH
  .name = "NXPGRPH",
  .code = BRL_CMD_NXPGRPH,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go down to first line of next paragraph"),
},

{ // BRL_CMD_PRPROMPT
  .name = "PRPROMPT",
  .code = BRL_CMD_PRPROMPT,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go up to previous command prompt"),
},

{ // BRL_CMD_NXPROMPT
  .name = "NXPROMPT",
  .code = BRL_CMD_NXPROMPT,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go down to next command prompt"),
},

{ // BRL_CMD_PRSEARCH
  .name = "PRSEARCH",
  .code = BRL_CMD_PRSEARCH,
  .description = strtext("search backward for clipboard text"),
},

{ // BRL_CMD_NXSEARCH
  .name = "NXSEARCH",
  .code = BRL_CMD_NXSEARCH,
  .description = strtext("search forward for clipboard text"),
},

{ // BRL_CMD_CHRLT
  .name = "CHRLT",
  .code = BRL_CMD_CHRLT,
  .isMotion = 1,
  .isHorizontal = 1,
  .description = strtext("go left one character"),
},

{ // BRL_CMD_CHRRT
  .name = "CHRRT",
  .code = BRL_CMD_CHRRT,
  .isMotion = 1,
  .isHorizontal = 1,
  .description = strtext("go right one character"),
},

{ // BRL_CMD_HWINLT
  .name = "HWINLT",
  .code = BRL_CMD_HWINLT,
  .isMotion = 1,
  .isHorizontal = 1,
  .description = strtext("go left half a braille window"),
},

{ // BRL_CMD_HWINRT
  .name = "HWINRT",
  .code = BRL_CMD_HWINRT,
  .isMotion = 1,
  .isHorizontal = 1,
  .description = strtext("go right half a braille window"),
},

{ // BRL_CMD_FWINLT
  .name = "FWINLT",
  .code = BRL_CMD_FWINLT,
  .isMotion = 1,
  .isPanning = 1,
  .description = strtext("go backward one braille window"),
},

{ // BRL_CMD_FWINRT
  .name = "FWINRT",
  .code = BRL_CMD_FWINRT,
  .isMotion = 1,
  .isPanning = 1,
  .description = strtext("go forward one braille window"),
},

{ // BRL_CMD_FWINLTSKIP
  .name = "FWINLTSKIP",
  .code = BRL_CMD_FWINLTSKIP,
  .isMotion = 1,
  .isPanning = 1,
  .description = strtext("go backward skipping blank braille windows"),
},

{ // BRL_CMD_FWINRTSKIP
  .name = "FWINRTSKIP",
  .code = BRL_CMD_FWINRTSKIP,
  .isMotion = 1,
  .isPanning = 1,
  .description = strtext("go forward skipping blank braille windows"),
},

{ // BRL_CMD_LNBEG
  .name = "LNBEG",
  .code = BRL_CMD_LNBEG,
  .isMotion = 1,
  .isHorizontal = 1,
  .description = strtext("go to beginning of line"),
},

{ // BRL_CMD_LNEND
  .name = "LNEND",
  .code = BRL_CMD_LNEND,
  .isMotion = 1,
  .isHorizontal = 1,
  .description = strtext("go to end of line"),
},

{ // BRL_CMD_HOME
  .name = "HOME",
  .code = BRL_CMD_HOME,
  .isMotion = 1,
  .description = strtext("go to screen cursor"),
},

{ // BRL_CMD_BACK
  .name = "BACK",
  .code = BRL_CMD_BACK,
  .isMotion = 1,
  .description = strtext("go back after cursor tracking"),
},

{ // BRL_CMD_RETURN
  .name = "RETURN",
  .code = BRL_CMD_RETURN,
  .isMotion = 1,
  .description = strtext("go to screen cursor or go back after cursor tracking"),
},

{ // BRL_CMD_FREEZE
  .name = "FREEZE",
  .code = BRL_CMD_FREEZE,
  .isToggle = 1,
  .description = strtext("set screen image frozen/unfrozen"),
},

{ // BRL_CMD_DISPMD
  .name = "DISPMD",
  .code = BRL_CMD_DISPMD,
  .isToggle = 1,
  .description = strtext("set display mode attributes/text"),
},

{ // BRL_CMD_SIXDOTS
  .name = "SIXDOTS",
  .code = BRL_CMD_SIXDOTS,
  .isToggle = 1,
  .description = strtext("set text style 6-dot/8-dot"),
},

{ // BRL_CMD_SLIDEWIN
  .name = "SLIDEWIN",
  .code = BRL_CMD_SLIDEWIN,
  .isToggle = 1,
  .description = strtext("set sliding braille window on/off"),
},

{ // BRL_CMD_SKPIDLNS
  .name = "SKPIDLNS",
  .code = BRL_CMD_SKPIDLNS,
  .isToggle = 1,
  .description = strtext("set skipping of lines with identical content on/off"),
},

{ // BRL_CMD_SKPBLNKWINS
  .name = "SKPBLNKWINS",
  .code = BRL_CMD_SKPBLNKWINS,
  .isToggle = 1,
  .description = strtext("set skipping of blank braille windows on/off"),
},

{ // BRL_CMD_CSRVIS
  .name = "CSRVIS",
  .code = BRL_CMD_CSRVIS,
  .isToggle = 1,
  .description = strtext("set screen cursor visibility on/off"),
},

{ // BRL_CMD_CSRHIDE
  .name = "CSRHIDE",
  .code = BRL_CMD_CSRHIDE,
  .isToggle = 1,
  .description = strtext("set hidden screen cursor on/off"),
},

{ // BRL_CMD_CSRTRK
  .name = "CSRTRK",
  .code = BRL_CMD_CSRTRK,
  .isToggle = 1,
  .description = strtext("set track screen cursor on/off"),
},

{ // BRL_CMD_CSRSIZE
  .name = "CSRSIZE",
  .code = BRL_CMD_CSRSIZE,
  .isToggle = 1,
  .description = strtext("set screen cursor style block/underline"),
},

{ // BRL_CMD_CSRBLINK
  .name = "CSRBLINK",
  .code = BRL_CMD_CSRBLINK,
  .isToggle = 1,
  .description = strtext("set screen cursor blinking on/off"),
},

{ // BRL_CMD_ATTRVIS
  .name = "ATTRVIS",
  .code = BRL_CMD_ATTRVIS,
  .isToggle = 1,
  .description = strtext("set attribute underlining on/off"),
},

{ // BRL_CMD_ATTRBLINK
  .name = "ATTRBLINK",
  .code = BRL_CMD_ATTRBLINK,
  .isToggle = 1,
  .description = strtext("set attribute blinking on/off"),
},

{ // BRL_CMD_CAPBLINK
  .name = "CAPBLINK",
  .code = BRL_CMD_CAPBLINK,
  .isToggle = 1,
  .description = strtext("set capital letter blinking on/off"),
},

{ // BRL_CMD_TUNES
  .name = "TUNES",
  .code = BRL_CMD_TUNES,
  .isToggle = 1,
  .description = strtext("set alert tunes on/off"),
},

{ // BRL_CMD_AUTOREPEAT
  .name = "AUTOREPEAT",
  .code = BRL_CMD_AUTOREPEAT,
  .isToggle = 1,
  .description = strtext("set autorepeat on/off"),
},

{ // BRL_CMD_AUTOSPEAK
  .name = "AUTOSPEAK",
  .code = BRL_CMD_AUTOSPEAK,
  .isToggle = 1,
  .description = strtext("set autospeak on/off"),
},

{ // BRL_CMD_HELP
  .name = "HELP",
  .code = BRL_CMD_HELP,
  .description = strtext("enter/leave help display"),
},

{ // BRL_CMD_INFO
  .name = "INFO",
  .code = BRL_CMD_INFO,
  .description = strtext("enter/leave status display"),
},

{ // BRL_CMD_LEARN
  .name = "LEARN",
  .code = BRL_CMD_LEARN,
  .description = strtext("enter/leave command learn mode"),
},

{ // BRL_CMD_PREFMENU
  .name = "PREFMENU",
  .code = BRL_CMD_PREFMENU,
  .description = strtext("enter/leave preferences menu"),
},

{ // BRL_CMD_PREFSAVE
  .name = "PREFSAVE",
  .code = BRL_CMD_PREFSAVE,
  .description = strtext("save preferences to disk"),
},

{ // BRL_CMD_PREFLOAD
  .name = "PREFLOAD",
  .code = BRL_CMD_PREFLOAD,
  .description = strtext("restore preferences from disk"),
},

{ // BRL_CMD_MENU_FIRST_ITEM
  .name = "MENU_FIRST_ITEM",
  .code = BRL_CMD_MENU_FIRST_ITEM,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go up to first item"),
},

{ // BRL_CMD_MENU_LAST_ITEM
  .name = "MENU_LAST_ITEM",
  .code = BRL_CMD_MENU_LAST_ITEM,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go down to last item"),
},

{ // BRL_CMD_MENU_PREV_ITEM
  .name = "MENU_PREV_ITEM",
  .code = BRL_CMD_MENU_PREV_ITEM,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go up to previous item"),
},

{ // BRL_CMD_MENU_NEXT_ITEM
  .name = "MENU_NEXT_ITEM",
  .code = BRL_CMD_MENU_NEXT_ITEM,
  .isMotion = 1,
  .isVertical = 1,
  .description = strtext("go down to next item"),
},

{ // BRL_CMD_MENU_PREV_SETTING
  .name = "MENU_PREV_SETTING",
  .code = BRL_CMD_MENU_PREV_SETTING,
  .description = strtext("select previous choice"),
},

{ // BRL_CMD_MENU_NEXT_SETTING
  .name = "MENU_NEXT_SETTING",
  .code = BRL_CMD_MENU_NEXT_SETTING,
  .description = strtext("select next choice"),
},

{ // BRL_CMD_MUTE
  .name = "MUTE",
  .code = BRL_CMD_MUTE,
  .description = strtext("stop speaking"),
},

{ // BRL_CMD_SPKHOME
  .name = "SPKHOME",
  .code = BRL_CMD_SPKHOME,
  .isMotion = 1,
  .description = strtext("go to current speaking position"),
},

{ // BRL_CMD_SAY_LINE
  .name = "SAY_LINE",
  .code = BRL_CMD_SAY_LINE,
  .description = strtext("speak current line"),
},

{ // BRL_CMD_SAY_ABOVE
  .name = "SAY_ABOVE",
  .code = BRL_CMD_SAY_ABOVE,
  .description = strtext("speak from top of screen through current line"),
},

{ // BRL_CMD_SAY_BELOW
  .name = "SAY_BELOW",
  .code = BRL_CMD_SAY_BELOW,
  .description = strtext("speak from current line through bottom of screen"),
},

{ // BRL_CMD_SAY_SLOWER
  .name = "SAY_SLOWER",
  .code = BRL_CMD_SAY_SLOWER,
  .description = strtext("decrease speaking rate"),
},

{ // BRL_CMD_SAY_FASTER
  .name = "SAY_FASTER",
  .code = BRL_CMD_SAY_FASTER,
  .description = strtext("increase speaking rate"),
},

{ // BRL_CMD_SAY_SOFTER
  .name = "SAY_SOFTER",
  .code = BRL_CMD_SAY_SOFTER,
  .description = strtext("decrease speaking volume"),
},

{ // BRL_CMD_SAY_LOUDER
  .name = "SAY_LOUDER",
  .code = BRL_CMD_SAY_LOUDER,
  .description = strtext("increase speaking volume"),
},

{ // BRL_CMD_SWITCHVT_PREV
  .name = "SWITCHVT_PREV",
  .code = BRL_CMD_SWITCHVT_PREV,
  .description = strtext("switch to the previous virtual terminal"),
},

{ // BRL_CMD_SWITCHVT_NEXT
  .name = "SWITCHVT_NEXT",
  .code = BRL_CMD_SWITCHVT_NEXT,
  .description = strtext("switch to the next virtual terminal"),
},

{ // BRL_CMD_CSRJMP_VERT
  .name = "CSRJMP_VERT",
  .code = BRL_CMD_CSRJMP_VERT,
  .isRouting = 1,
  .description = strtext("bring screen cursor to current line"),
},

{ // BRL_CMD_PASTE
  .name = "PASTE",
  .code = BRL_CMD_PASTE,
  .description = strtext("insert clipboard text after screen cursor"),
},

{ // BRL_CMD_RESTARTBRL
  .name = "RESTARTBRL",
  .code = BRL_CMD_RESTARTBRL,
  .description = strtext("restart braille driver"),
},

{ // BRL_CMD_RESTARTSPEECH
  .name = "RESTARTSPEECH",
  .code = BRL_CMD_RESTARTSPEECH,
  .description = strtext("restart speech driver"),
},

{ // BRL_CMD_OFFLINE
  .name = "OFFLINE",
  .code = BRL_CMD_OFFLINE,
  .description = strtext("braille display temporarily unavailable"),
},

{ // BRL_CMD_SHIFT
  .name = "SHIFT",
  .code = BRL_CMD_SHIFT,
  .description = strtext("cycle the Shift sticky input modifier (next, on, off)"),
},

{ // BRL_CMD_UPPER
  .name = "UPPER",
  .code = BRL_CMD_UPPER,
  .description = strtext("cycle the Upper sticky input modifier (next, on, off)"),
},

{ // BRL_CMD_CONTROL
  .name = "CONTROL",
  .code = BRL_CMD_CONTROL,
  .description = strtext("cycle the Control sticky input modifier (next, on, off)"),
},

{ // BRL_CMD_META
  .name = "META",
  .code = BRL_CMD_META,
  .description = strtext("cycle the Meta (Left Alt) sticky input modifier (next, on, off)"),
},

{ // BRL_CMD_TIME
  .name = "TIME",
  .code = BRL_CMD_TIME,
  .description = strtext("show current date and time"),
},

{ // BRL_CMD_MENU_PREV_LEVEL
  .name = "MENU_PREV_LEVEL",
  .code = BRL_CMD_MENU_PREV_LEVEL,
  .isMotion = 1,
  .description = strtext("go to previous menu level"),
},

{ // BRL_CMD_ASPK_SEL_LINE
  .name = "ASPK_SEL_LINE",
  .code = BRL_CMD_ASPK_SEL_LINE,
  .isToggle = 1,
  .description = strtext("set autospeak selected line on/off"),
},

{ // BRL_CMD_ASPK_SEL_CHAR
  .name = "ASPK_SEL_CHAR",
  .code = BRL_CMD_ASPK_SEL_CHAR,
  .isToggle = 1,
  .description = strtext("set autospeak selected character on/off"),
},

{ // BRL_CMD_ASPK_INS_CHARS
  .name = "ASPK_INS_CHARS",
  .code = BRL_CMD_ASPK_INS_CHARS,
  .isToggle = 1,
  .description = strtext("set autospeak inserted characters on/off"),
},

{ // BRL_CMD_ASPK_DEL_CHARS
  .name = "ASPK_DEL_CHARS",
  .code = BRL_CMD_ASPK_DEL_CHARS,
  .isToggle = 1,
  .description = strtext("set autospeak deleted characters on/off"),
},

{ // BRL_CMD_ASPK_REP_CHARS
  .name = "ASPK_REP_CHARS",
  .code = BRL_CMD_ASPK_REP_CHARS,
  .isToggle = 1,
  .description = strtext("set autospeak replaced characters on/off"),
},

{ // BRL_CMD_ASPK_CMP_WORDS
  .name = "ASPK_CMP_WORDS",
  .code = BRL_CMD_ASPK_CMP_WORDS,
  .isToggle = 1,
  .description = strtext("set autospeak completed words on/off"),
},

{ // BRL_CMD_SPEAK_CURR_CHAR
  .name = "SPEAK_CURR_CHAR",
  .code = BRL_CMD_SPEAK_CURR_CHAR,
  .description = strtext("speak current character"),
},

{ // BRL_CMD_SPEAK_PREV_CHAR
  .name = "SPEAK_PREV_CHAR",
  .code = BRL_CMD_SPEAK_PREV_CHAR,
  .isMotion = 1,
  .description = strtext("go to and speak previous character"),
},

{ // BRL_CMD_SPEAK_NEXT_CHAR
  .name = "SPEAK_NEXT_CHAR",
  .code = BRL_CMD_SPEAK_NEXT_CHAR,
  .isMotion = 1,
  .description = strtext("go to and speak next character"),
},

{ // BRL_CMD_SPEAK_CURR_WORD
  .name = "SPEAK_CURR_WORD",
  .code = BRL_CMD_SPEAK_CURR_WORD,
  .description = strtext("speak current word"),
},

{ // BRL_CMD_SPEAK_PREV_WORD
  .name = "SPEAK_PREV_WORD",
  .code = BRL_CMD_SPEAK_PREV_WORD,
  .isMotion = 1,
  .description = strtext("go to and speak previous word"),
},

{ // BRL_CMD_SPEAK_NEXT_WORD
  .name = "SPEAK_NEXT_WORD",
  .code = BRL_CMD_SPEAK_NEXT_WORD,
  .isMotion = 1,
  .description = strtext("go to and speak next word"),
},

{ // BRL_CMD_SPEAK_CURR_LINE
  .name = "SPEAK_CURR_LINE",
  .code = BRL_CMD_SPEAK_CURR_LINE,
  .description = strtext("speak current line"),
},

{ // BRL_CMD_SPEAK_PREV_LINE
  .name = "SPEAK_PREV_LINE",
  .code = BRL_CMD_SPEAK_PREV_LINE,
  .isMotion = 1,
  .description = strtext("go to and speak previous line"),
},

{ // BRL_CMD_SPEAK_NEXT_LINE
  .name = "SPEAK_NEXT_LINE",
  .code = BRL_CMD_SPEAK_NEXT_LINE,
  .isMotion = 1,
  .description = strtext("go to and speak next line"),
},

{ // BRL_CMD_SPEAK_FRST_CHAR
  .name = "SPEAK_FRST_CHAR",
  .code = BRL_CMD_SPEAK_FRST_CHAR,
  .isMotion = 1,
  .description = strtext("go to and speak first non-blank character on line"),
},

{ // BRL_CMD_SPEAK_LAST_CHAR
  .name = "SPEAK_LAST_CHAR",
  .code = BRL_CMD_SPEAK_LAST_CHAR,
  .isMotion = 1,
  .description = strtext("go to and speak last non-blank character on line"),
},

{ // BRL_CMD_SPEAK_FRST_LINE
  .name = "SPEAK_FRST_LINE",
  .code = BRL_CMD_SPEAK_FRST_LINE,
  .isMotion = 1,
  .description = strtext("go to and speak first non-blank line on screen"),
},

{ // BRL_CMD_SPEAK_LAST_LINE
  .name = "SPEAK_LAST_LINE",
  .code = BRL_CMD_SPEAK_LAST_LINE,
  .isMotion = 1,
  .description = strtext("go to and speak last non-blank line on screen"),
},

{ // BRL_CMD_DESC_CURR_CHAR
  .name = "DESC_CURR_CHAR",
  .code = BRL_CMD_DESC_CURR_CHAR,
  .description = strtext("describe current character"),
},

{ // BRL_CMD_SPELL_CURR_WORD
  .name = "SPELL_CURR_WORD",
  .code = BRL_CMD_SPELL_CURR_WORD,
  .description = strtext("spell current word"),
},

{ // BRL_CMD_ROUTE_CURR_LOCN
  .name = "ROUTE_CURR_LOCN",
  .code = BRL_CMD_ROUTE_CURR_LOCN,
  .isRouting = 1,
  .description = strtext("bring screen cursor to speech cursor"),
},

{ // BRL_CMD_SPEAK_CURR_LOCN
  .name = "SPEAK_CURR_LOCN",
  .code = BRL_CMD_SPEAK_CURR_LOCN,
  .description = strtext("speak speech cursor location"),
},

{ // BRL_CMD_SHOW_CURR_LOCN
  .name = "SHOW_CURR_LOCN",
  .code = BRL_CMD_SHOW_CURR_LOCN,
  .isToggle = 1,
  .description = strtext("set speech cursor visibility on/off"),
},

{ // BRL_CMD_CLIP_SAVE
  .name = "CLIP_SAVE",
  .code = BRL_CMD_CLIP_SAVE,
  .description = strtext("save clipboard to disk"),
},

{ // BRL_CMD_CLIP_RESTORE
  .name = "CLIP_RESTORE",
  .code = BRL_CMD_CLIP_RESTORE,
  .description = strtext("restore clipboard from disk"),
},

{ // BRL_CMD_BRLUCDOTS
  .name = "BRLUCDOTS",
  .code = BRL_CMD_BRLUCDOTS,
  .isToggle = 1,
  .description = strtext("set braille input mode dots/text"),
},

{ // BRL_CMD_BRLKBD
  .name = "BRLKBD",
  .code = BRL_CMD_BRLKBD,
  .isToggle = 1,
  .description = strtext("set braille keyboard enabled/disabled"),
},

{ // BRL_CMD_UNSTICK
  .name = "UNSTICK",
  .code = BRL_CMD_UNSTICK,
  .description = strtext("clear all sticky input modifiers"),
},

{ // BRL_CMD_ALTGR
  .name = "ALTGR",
  .code = BRL_CMD_ALTGR,
  .description = strtext("cycle the AltGr (Right Alt) sticky input modifier (next, on, off)"),
},

{ // BRL_CMD_GUI
  .name = "GUI",
  .code = BRL_CMD_GUI,
  .description = strtext("cycle the GUI (Windows) sticky input modifier (next, on, off)"),
},

{ // BRL_CMD_BRL_STOP
  .name = "BRL_STOP",
  .code = BRL_CMD_BRL_STOP,
  .description = strtext("stop the braille driver"),
},

{ // BRL_CMD_BRL_START
  .name = "BRL_START",
  .code = BRL_CMD_BRL_START,
  .description = strtext("start the braille driver"),
},

{ // BRL_CMD_SPK_STOP
  .name = "SPK_STOP",
  .code = BRL_CMD_SPK_STOP,
  .description = strtext("stop the speech driver"),
},

{ // BRL_CMD_SPK_START
  .name = "SPK_START",
  .code = BRL_CMD_SPK_START,
  .description = strtext("start the speech driver"),
},

{ // BRL_CMD_SCR_STOP
  .name = "SCR_STOP",
  .code = BRL_CMD_SCR_STOP,
  .description = strtext("stop the screen driver"),
},

{ // BRL_CMD_SCR_START
  .name = "SCR_START",
  .code = BRL_CMD_SCR_START,
  .description = strtext("start the screen driver"),
},

{ // BRL_CMD_SELECTVT_PREV
  .name = "SELECTVT_PREV",
  .code = BRL_CMD_SELECTVT_PREV,
  .description = strtext("bind to the previous virtual terminal"),
},

{ // BRL_CMD_SELECTVT_NEXT
  .name = "SELECTVT_NEXT",
  .code = BRL_CMD_SELECTVT_NEXT,
  .description = strtext("bind to the next virtual terminal"),
},

{ // BRL_CMD_PRNBWIN
  .name = "PRNBWIN",
  .code = BRL_CMD_PRNBWIN,
  .isMotion = 1,
  .isPanning = 1,
  .description = strtext("go backward to nearest non-blank braille window"),
},

{ // BRL_CMD_NXNBWIN
  .name = "NXNBWIN",
  .code = BRL_CMD_NXNBWIN,
  .isMotion = 1,
  .isPanning = 1,
  .description = strtext("go forward to nearest non-blank braille window"),
},

{ // BRL_CMD_TOUCH_NAV
  .name = "TOUCH_NAV",
  .code = BRL_CMD_TOUCH_NAV,
  .isToggle = 1,
  .description = strtext("set touch navigation on/off"),
},

{ // BRL_CMD_SPEAK_INDENT
  .name = "SPEAK_INDENT",
  .code = BRL_CMD_SPEAK_INDENT,
  .description = strtext("speak indent of current line"),
},

{ // BRL_CMD_ASPK_INDENT
  .name = "ASPK_INDENT",
  .code = BRL_CMD_ASPK_INDENT,
  .isToggle = 1,
  .description = strtext("set autospeak indent of current line on/off"),
},

{ // BRL_BLK_ROUTE
  .name = "ROUTE",
  .code = BRL_CMD_BLK(ROUTE),
  .isRouting = 1,
  .isColumn = 1,
  .description = strtext("bring screen cursor to character"),
},

{ // BRL_BLK_CLIP_NEW
  .name = "CLIP_NEW",
  .code = BRL_CMD_BLK(CLIP_NEW),
  .isColumn = 1,
  .description = strtext("start new clipboard at character"),
},

{ // BRL_BLK_CLIP_ADD
  .name = "CLIP_ADD",
  .code = BRL_CMD_BLK(CLIP_ADD),
  .isColumn = 1,
  .description = strtext("append to clipboard from character"),
},

{ // BRL_BLK_COPY_RECT
  .name = "COPY_RECT",
  .code = BRL_CMD_BLK(COPY_RECT),
  .isColumn = 1,
  .description = strtext("rectangular copy to character"),
},

{ // BRL_BLK_COPY_LINE
  .name = "COPY_LINE",
  .code = BRL_CMD_BLK(COPY_LINE),
  .isColumn = 1,
  .description = strtext("linear copy to character"),
},

{ // BRL_BLK_SWITCHVT
  .name = "SWITCHVT",
  .code = BRL_CMD_BLK(SWITCHVT),
  .isOffset = 1,
  .description = strtext("switch to specific virtual terminal"),
},

{ // BRL_BLK_PRINDENT
  .name = "PRINDENT",
  .code = BRL_CMD_BLK(PRINDENT),
  .isMotion = 1,
  .isVertical = 1,
  .isColumn = 1,
  .description = strtext("go up to nearest line with less indent than character"),
},

{ // BRL_BLK_NXINDENT
  .name = "NXINDENT",
  .code = BRL_CMD_BLK(NXINDENT),
  .isMotion = 1,
  .isVertical = 1,
  .isColumn = 1,
  .description = strtext("go down to nearest line with less indent than character"),
},

{ // BRL_BLK_DESCCHAR
  .name = "DESCCHAR",
  .code = BRL_CMD_BLK(DESCCHAR),
  .isColumn = 1,
  .description = strtext("describe character"),
},

{ // BRL_BLK_SETLEFT
  .name = "SETLEFT",
  .code = BRL_CMD_BLK(SETLEFT),
  .isColumn = 1,
  .description = strtext("place left end of braille window at character"),
},

{ // BRL_BLK_SETMARK
  .name = "SETMARK",
  .code = BRL_CMD_BLK(SETMARK),
  .isOffset = 1,
  .description = strtext("remember current braille window position"),
},

{ // BRL_BLK_GOTOMARK
  .name = "GOTOMARK",
  .code = BRL_CMD_BLK(GOTOMARK),
  .isMotion = 1,
  .isOffset = 1,
  .description = strtext("go to remembered braille window position"),
},

{ // BRL_BLK_GOTOLINE
  .name = "GOTOLINE",
  .code = BRL_CMD_BLK(GOTOLINE),
  .isMotion = 1,
  .isRow = 1,
  .isVertical = 1,
  .description = strtext("go to selected line"),
},

{ // BRL_BLK_PRDIFCHAR
  .name = "PRDIFCHAR",
  .code = BRL_CMD_BLK(PRDIFCHAR),
  .isMotion = 1,
  .isVertical = 1,
  .isColumn = 1,
  .description = strtext("go up to nearest line with different character"),
},

{ // BRL_BLK_NXDIFCHAR
  .name = "NXDIFCHAR",
  .code = BRL_CMD_BLK(NXDIFCHAR),
  .isMotion = 1,
  .isVertical = 1,
  .isColumn = 1,
  .description = strtext("go down to nearest line with different character"),
},

{ // BRL_BLK_CLIP_COPY
  .name = "CLIP_COPY",
  .code = BRL_CMD_BLK(CLIP_COPY),
  .isRange = 1,
  .description = strtext("copy characters to clipboard"),
},

{ // BRL_BLK_CLIP_APPEND
  .name = "CLIP_APPEND",
  .code = BRL_CMD_BLK(CLIP_APPEND),
  .isRange = 1,
  .description = strtext("append characters to clipboard"),
},

{ // BRL_BLK_PASTE_HISTORY
  .name = "PASTE_HISTORY",
  .code = BRL_CMD_BLK(PASTE_HISTORY),
  .isOffset = 1,
  .description = strtext("insert clipboard history entry after screen cursor"),
},

{ // BRL_BLK_SET_TEXT_TABLE
  .name = "SET_TEXT_TABLE",
  .code = BRL_CMD_BLK(SET_TEXT_TABLE),
  .isOffset = 1,
  .description = strtext("set text table"),
},

{ // BRL_BLK_SET_ATTRIBUTES_TABLE
  .name = "SET_ATTRIBUTES_TABLE",
  .code = BRL_CMD_BLK(SET_ATTRIBUTES_TABLE),
  .isOffset = 1,
  .description = strtext("set attributes table"),
},

{ // BRL_BLK_SET_CONTRACTION_TABLE
  .name = "SET_CONTRACTION_TABLE",
  .code = BRL_CMD_BLK(SET_CONTRACTION_TABLE),
  .isOffset = 1,
  .description = strtext("set contraction table"),
},

{ // BRL_BLK_SET_KEYBOARD_TABLE
  .name = "SET_KEYBOARD_TABLE",
  .code = BRL_CMD_BLK(SET_KEYBOARD_TABLE),
  .isOffset = 1,
  .description = strtext("set keyboard table"),
},

{ // BRL_BLK_SET_LANGUAGE_PROFILE
  .name = "SET_LANGUAGE_PROFILE",
  .code = BRL_CMD_BLK(SET_LANGUAGE_PROFILE),
  .isOffset = 1,
  .description = strtext("set language profile"),
},

{ // BRL_BLK_SELECTVT
  .name = "SELECTVT",
  .code = BRL_CMD_BLK(SELECTVT),
  .isOffset = 1,
  .description = strtext("bind to specific virtual terminal"),
},

{ // BRL_BLK_ALERT
  .name = "ALERT",
  .code = BRL_CMD_BLK(ALERT),
  .isOffset = 1,
  .description = strtext("render an alert"),
},

{ // BRL_BLK_PASSCHAR
  .name = "PASSCHAR",
  .code = BRL_CMD_BLK(PASSCHAR),
  .isInput = 1,
  .isCharacter = 1,
  .description = strtext("type unicode character"),
},

{ // BRL_BLK_PASSDOTS
  .name = "PASSDOTS",
  .code = BRL_CMD_BLK(PASSDOTS),
  .isInput = 1,
  .isBraille = 1,
  .description = strtext("type braille dots"),
},

{ // BRL_BLK_PASSAT
  .name = "PASSAT",
  .code = BRL_CMD_BLK(PASSAT),
  .isKeyboard = 1,
  .description = strtext("AT (set 2) keyboard scan code"),
},

{ // BRL_BLK_PASSXT
  .name = "PASSXT",
  .code = BRL_CMD_BLK(PASSXT),
  .isKeyboard = 1,
  .description = strtext("XT (set 1) keyboard scan code"),
},

{ // BRL_BLK_PASSPS2
  .name = "PASSPS2",
  .code = BRL_CMD_BLK(PASSPS2),
  .isKeyboard = 1,
  .description = strtext("PS/2 (set 3) keyboard scan code"),
},

{ // BRL_BLK_CONTEXT
  .name = "CONTEXT",
  .code = BRL_CMD_BLK(CONTEXT),
  .isOffset = 1,
  .description = strtext("switch to command context"),
},

{ // BRL_BLK_TOUCH_AT
  .name = "TOUCH_AT",
  .code = BRL_CMD_BLK(TOUCH_AT),
  .isOffset = 1,
  .description = strtext("current reading location"),
},

{ // BRL_KEY_ENTER
  .name = "KEY_ENTER",
  .code = BRL_CMD_KEY(ENTER),
  .isInput = 1,
  .description = strtext("enter key"),
},

{ // BRL_KEY_TAB
  .name = "KEY_TAB",
  .code = BRL_CMD_KEY(TAB),
  .isInput = 1,
  .description = strtext("tab key"),
},

{ // BRL_KEY_BACKSPACE
  .name = "KEY_BACKSPACE",
  .code = BRL_CMD_KEY(BACKSPACE),
  .isInput = 1,
  .description = strtext("backspace key"),
},

{ // BRL_KEY_ESCAPE
  .name = "KEY_ESCAPE",
  .code = BRL_CMD_KEY(ESCAPE),
  .isInput = 1,
  .description = strtext("escape key"),
},

{ // BRL_KEY_CURSOR_LEFT
  .name = "KEY_CURSOR_LEFT",
  .code = BRL_CMD_KEY(CURSOR_LEFT),
  .isInput = 1,
  .description = strtext("cursor-left key"),
},

{ // BRL_KEY_CURSOR_RIGHT
  .name = "KEY_CURSOR_RIGHT",
  .code = BRL_CMD_KEY(CURSOR_RIGHT),
  .isInput = 1,
  .description = strtext("cursor-right key"),
},

{ // BRL_KEY_CURSOR_UP
  .name = "KEY_CURSOR_UP",
  .code = BRL_CMD_KEY(CURSOR_UP),
  .isInput = 1,
  .description = strtext("cursor-up key"),
},

{ // BRL_KEY_CURSOR_DOWN
  .name = "KEY_CURSOR_DOWN",
  .code = BRL_CMD_KEY(CURSOR_DOWN),
  .isInput = 1,
  .description = strtext("cursor-down key"),
},

{ // BRL_KEY_PAGE_UP
  .name = "KEY_PAGE_UP",
  .code = BRL_CMD_KEY(PAGE_UP),
  .isInput = 1,
  .description = strtext("page-up key"),
},

{ // BRL_KEY_PAGE_DOWN
  .name = "KEY_PAGE_DOWN",
  .code = BRL_CMD_KEY(PAGE_DOWN),
  .isInput = 1,
  .description = strtext("page-down key"),
},

{ // BRL_KEY_HOME
  .name = "KEY_HOME",
  .code = BRL_CMD_KEY(HOME),
  .isInput = 1,
  .description = strtext("home key"),
},

{ // BRL_KEY_END
  .name = "KEY_END",
  .code = BRL_CMD_KEY(END),
  .isInput = 1,
  .description = strtext("end key"),
},

{ // BRL_KEY_INSERT
  .name = "KEY_INSERT",
  .code = BRL_CMD_KEY(INSERT),
  .isInput = 1,
  .description = strtext("insert key"),
},

{ // BRL_KEY_DELETE
  .name = "KEY_DELETE",
  .code = BRL_CMD_KEY(DELETE),
  .isInput = 1,
  .description = strtext("delete key"),
},

{ // BRL_KEY_FUNCTION
  .name = "KEY_FUNCTION",
  .code = BRL_CMD_KEY(FUNCTION),
  .isInput = 1,
  .isOffset = 1,
  .description = strtext("function key"),
},


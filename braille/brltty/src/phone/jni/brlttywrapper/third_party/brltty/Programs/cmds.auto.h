{
    // BRL_CMD_NOOP
    .name = "NOOP",
    .code = BRL_CMD_NOOP,
    // xgettext: This is the description of the NOOP command.
    .description = strtext("do nothing"),
},

    {
        // BRL_CMD_LNUP
        .name = "LNUP",
        .code = BRL_CMD_LNUP,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the LNUP command.
        .description = strtext("go up one line"),
    },

    {
        // BRL_CMD_LNDN
        .name = "LNDN",
        .code = BRL_CMD_LNDN,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the LNDN command.
        .description = strtext("go down one line"),
    },

    {
        // BRL_CMD_WINUP
        .name = "WINUP",
        .code = BRL_CMD_WINUP,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the WINUP command.
        .description = strtext("go up several lines"),
    },

    {
        // BRL_CMD_WINDN
        .name = "WINDN",
        .code = BRL_CMD_WINDN,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the WINDN command.
        .description = strtext("go down several lines"),
    },

    {
        // BRL_CMD_PRDIFLN
        .name = "PRDIFLN",
        .code = BRL_CMD_PRDIFLN,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the PRDIFLN command.
        .description = strtext("go up to nearest line with different content"),
    },

    {
        // BRL_CMD_NXDIFLN
        .name = "NXDIFLN",
        .code = BRL_CMD_NXDIFLN,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the NXDIFLN command.
        .description =
            strtext("go down to nearest line with different content"),
    },

    {
        // BRL_CMD_ATTRUP
        .name = "ATTRUP",
        .code = BRL_CMD_ATTRUP,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the ATTRUP command.
        .description =
            strtext("go up to nearest line with different highlighting"),
    },

    {
        // BRL_CMD_ATTRDN
        .name = "ATTRDN",
        .code = BRL_CMD_ATTRDN,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the ATTRDN command.
        .description =
            strtext("go down to nearest line with different highlighting"),
    },

    {
        // BRL_CMD_TOP
        .name = "TOP",
        .code = BRL_CMD_TOP,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the TOP command.
        .description = strtext("go to top line"),
    },

    {
        // BRL_CMD_BOT
        .name = "BOT",
        .code = BRL_CMD_BOT,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the BOT command.
        .description = strtext("go to bottom line"),
    },

    {
        // BRL_CMD_TOP_LEFT
        .name = "TOP_LEFT",
        .code = BRL_CMD_TOP_LEFT,
        .isMotion = 1,
        .isVertical = 1,
        .isHorizontal = 1,
        // xgettext: This is the description of the TOP_LEFT command.
        .description = strtext("go to beginning of top line"),
    },

    {
        // BRL_CMD_BOT_LEFT
        .name = "BOT_LEFT",
        .code = BRL_CMD_BOT_LEFT,
        .isMotion = 1,
        .isVertical = 1,
        .isHorizontal = 1,
        // xgettext: This is the description of the BOT_LEFT command.
        .description = strtext("go to beginning of bottom line"),
    },

    {
        // BRL_CMD_PRPGRPH
        .name = "PRPGRPH",
        .code = BRL_CMD_PRPGRPH,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the PRPGRPH command.
        .description = strtext("go up to first line of paragraph"),
    },

    {
        // BRL_CMD_NXPGRPH
        .name = "NXPGRPH",
        .code = BRL_CMD_NXPGRPH,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the NXPGRPH command.
        .description = strtext("go down to first line of next paragraph"),
    },

    {
        // BRL_CMD_PRPROMPT
        .name = "PRPROMPT",
        .code = BRL_CMD_PRPROMPT,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the PRPROMPT command.
        .description = strtext("go up to previous command prompt"),
    },

    {
        // BRL_CMD_NXPROMPT
        .name = "NXPROMPT",
        .code = BRL_CMD_NXPROMPT,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the NXPROMPT command.
        .description = strtext("go down to next command prompt"),
    },

    {
        // BRL_CMD_PRSEARCH
        .name = "PRSEARCH",
        .code = BRL_CMD_PRSEARCH,
        // xgettext: This is the description of the PRSEARCH command.
        .description = strtext("search backward for clipboard text"),
    },

    {
        // BRL_CMD_NXSEARCH
        .name = "NXSEARCH",
        .code = BRL_CMD_NXSEARCH,
        // xgettext: This is the description of the NXSEARCH command.
        .description = strtext("search forward for clipboard text"),
    },

    {
        // BRL_CMD_CHRLT
        .name = "CHRLT",
        .code = BRL_CMD_CHRLT,
        .isMotion = 1,
        .isHorizontal = 1,
        // xgettext: This is the description of the CHRLT command.
        .description = strtext("go left one character"),
    },

    {
        // BRL_CMD_CHRRT
        .name = "CHRRT",
        .code = BRL_CMD_CHRRT,
        .isMotion = 1,
        .isHorizontal = 1,
        // xgettext: This is the description of the CHRRT command.
        .description = strtext("go right one character"),
    },

    {
        // BRL_CMD_HWINLT
        .name = "HWINLT",
        .code = BRL_CMD_HWINLT,
        .isMotion = 1,
        .isHorizontal = 1,
        // xgettext: This is the description of the HWINLT command.
        .description = strtext("go left half a braille window"),
    },

    {
        // BRL_CMD_HWINRT
        .name = "HWINRT",
        .code = BRL_CMD_HWINRT,
        .isMotion = 1,
        .isHorizontal = 1,
        // xgettext: This is the description of the HWINRT command.
        .description = strtext("go right half a braille window"),
    },

    {
        // BRL_CMD_FWINLT
        .name = "FWINLT",
        .code = BRL_CMD_FWINLT,
        .isMotion = 1,
        .isPanning = 1,
        // xgettext: This is the description of the FWINLT command.
        .description = strtext("go backward one braille window"),
    },

    {
        // BRL_CMD_FWINRT
        .name = "FWINRT",
        .code = BRL_CMD_FWINRT,
        .isMotion = 1,
        .isPanning = 1,
        // xgettext: This is the description of the FWINRT command.
        .description = strtext("go forward one braille window"),
    },

    {
        // BRL_CMD_FWINLTSKIP
        .name = "FWINLTSKIP",
        .code = BRL_CMD_FWINLTSKIP,
        .isMotion = 1,
        .isPanning = 1,
        // xgettext: This is the description of the FWINLTSKIP command.
        .description = strtext("go backward skipping blank braille windows"),
    },

    {
        // BRL_CMD_FWINRTSKIP
        .name = "FWINRTSKIP",
        .code = BRL_CMD_FWINRTSKIP,
        .isMotion = 1,
        .isPanning = 1,
        // xgettext: This is the description of the FWINRTSKIP command.
        .description = strtext("go forward skipping blank braille windows"),
    },

    {
        // BRL_CMD_LNBEG
        .name = "LNBEG",
        .code = BRL_CMD_LNBEG,
        .isMotion = 1,
        .isHorizontal = 1,
        // xgettext: This is the description of the LNBEG command.
        .description = strtext("go to beginning of line"),
    },

    {
        // BRL_CMD_LNEND
        .name = "LNEND",
        .code = BRL_CMD_LNEND,
        .isMotion = 1,
        .isHorizontal = 1,
        // xgettext: This is the description of the LNEND command.
        .description = strtext("go to end of line"),
    },

    {
        // BRL_CMD_HOME
        .name = "HOME",
        .code = BRL_CMD_HOME,
        .isMotion = 1,
        // xgettext: This is the description of the HOME command.
        .description = strtext("go to screen cursor"),
    },

    {
        // BRL_CMD_BACK
        .name = "BACK",
        .code = BRL_CMD_BACK,
        .isMotion = 1,
        // xgettext: This is the description of the BACK command.
        .description = strtext("go back after cursor tracking"),
    },

    {
        // BRL_CMD_RETURN
        .name = "RETURN",
        .code = BRL_CMD_RETURN,
        .isMotion = 1,
        // xgettext: This is the description of the RETURN command.
        .description =
            strtext("go to screen cursor or go back after cursor tracking"),
    },

    {
        // BRL_CMD_FREEZE
        .name = "FREEZE",
        .code = BRL_CMD_FREEZE,
        .isToggle = 1,
        // xgettext: This is the description of the FREEZE command.
        .description = strtext("set screen image frozen/unfrozen"),
    },

    {
        // BRL_CMD_DISPMD
        .name = "DISPMD",
        .code = BRL_CMD_DISPMD,
        .isToggle = 1,
        // xgettext: This is the description of the DISPMD command.
        .description = strtext("set display mode attributes/text"),
    },

    {
        // BRL_CMD_SIXDOTS
        .name = "SIXDOTS",
        .code = BRL_CMD_SIXDOTS,
        .isToggle = 1,
        // xgettext: This is the description of the SIXDOTS command.
        .description = strtext("set text style 6-dot/8-dot"),
    },

    {
        // BRL_CMD_SLIDEWIN
        .name = "SLIDEWIN",
        .code = BRL_CMD_SLIDEWIN,
        .isToggle = 1,
        // xgettext: This is the description of the SLIDEWIN command.
        .description = strtext("set sliding braille window on/off"),
    },

    {
        // BRL_CMD_SKPIDLNS
        .name = "SKPIDLNS",
        .code = BRL_CMD_SKPIDLNS,
        .isToggle = 1,
        // xgettext: This is the description of the SKPIDLNS command.
        .description =
            strtext("set skipping of lines with identical content on/off"),
    },

    {
        // BRL_CMD_SKPBLNKWINS
        .name = "SKPBLNKWINS",
        .code = BRL_CMD_SKPBLNKWINS,
        .isToggle = 1,
        // xgettext: This is the description of the SKPBLNKWINS command.
        .description = strtext("set skipping of blank braille windows on/off"),
    },

    {
        // BRL_CMD_CSRVIS
        .name = "CSRVIS",
        .code = BRL_CMD_CSRVIS,
        .isToggle = 1,
        // xgettext: This is the description of the CSRVIS command.
        .description = strtext("set screen cursor visibility on/off"),
    },

    {
        // BRL_CMD_CSRHIDE
        .name = "CSRHIDE",
        .code = BRL_CMD_CSRHIDE,
        .isToggle = 1,
        // xgettext: This is the description of the CSRHIDE command.
        .description = strtext("set hidden screen cursor on/off"),
    },

    {
        // BRL_CMD_CSRTRK
        .name = "CSRTRK",
        .code = BRL_CMD_CSRTRK,
        .isToggle = 1,
        // xgettext: This is the description of the CSRTRK command.
        .description = strtext("set track screen cursor on/off"),
    },

    {
        // BRL_CMD_CSRSIZE
        .name = "CSRSIZE",
        .code = BRL_CMD_CSRSIZE,
        .isToggle = 1,
        // xgettext: This is the description of the CSRSIZE command.
        .description = strtext("set screen cursor style block/underline"),
    },

    {
        // BRL_CMD_CSRBLINK
        .name = "CSRBLINK",
        .code = BRL_CMD_CSRBLINK,
        .isToggle = 1,
        // xgettext: This is the description of the CSRBLINK command.
        .description = strtext("set screen cursor blinking on/off"),
    },

    {
        // BRL_CMD_ATTRVIS
        .name = "ATTRVIS",
        .code = BRL_CMD_ATTRVIS,
        .isToggle = 1,
        // xgettext: This is the description of the ATTRVIS command.
        .description = strtext("set attribute underlining on/off"),
    },

    {
        // BRL_CMD_ATTRBLINK
        .name = "ATTRBLINK",
        .code = BRL_CMD_ATTRBLINK,
        .isToggle = 1,
        // xgettext: This is the description of the ATTRBLINK command.
        .description = strtext("set attribute blinking on/off"),
    },

    {
        // BRL_CMD_CAPBLINK
        .name = "CAPBLINK",
        .code = BRL_CMD_CAPBLINK,
        .isToggle = 1,
        // xgettext: This is the description of the CAPBLINK command.
        .description = strtext("set capital letter blinking on/off"),
    },

    {
        // BRL_CMD_TUNES
        .name = "TUNES",
        .code = BRL_CMD_TUNES,
        .isToggle = 1,
        // xgettext: This is the description of the TUNES command.
        .description = strtext("set alert tunes on/off"),
    },

    {
        // BRL_CMD_AUTOREPEAT
        .name = "AUTOREPEAT",
        .code = BRL_CMD_AUTOREPEAT,
        .isToggle = 1,
        // xgettext: This is the description of the AUTOREPEAT command.
        .description = strtext("set autorepeat on/off"),
    },

    {
        // BRL_CMD_AUTOSPEAK
        .name = "AUTOSPEAK",
        .code = BRL_CMD_AUTOSPEAK,
        .isToggle = 1,
        // xgettext: This is the description of the AUTOSPEAK command.
        .description = strtext("set autospeak on/off"),
    },

    {
        // BRL_CMD_HELP
        .name = "HELP",
        .code = BRL_CMD_HELP,
        // xgettext: This is the description of the HELP command.
        .description = strtext("enter/leave help display"),
    },

    {
        // BRL_CMD_INFO
        .name = "INFO",
        .code = BRL_CMD_INFO,
        // xgettext: This is the description of the INFO command.
        .description = strtext("enter/leave status display"),
    },

    {
        // BRL_CMD_LEARN
        .name = "LEARN",
        .code = BRL_CMD_LEARN,
        // xgettext: This is the description of the LEARN command.
        .description = strtext("enter/leave command learn mode"),
    },

    {
        // BRL_CMD_PREFMENU
        .name = "PREFMENU",
        .code = BRL_CMD_PREFMENU,
        // xgettext: This is the description of the PREFMENU command.
        .description = strtext("enter/leave preferences menu"),
    },

    {
        // BRL_CMD_PREFSAVE
        .name = "PREFSAVE",
        .code = BRL_CMD_PREFSAVE,
        // xgettext: This is the description of the PREFSAVE command.
        .description = strtext("save preferences to disk"),
    },

    {
        // BRL_CMD_PREFLOAD
        .name = "PREFLOAD",
        .code = BRL_CMD_PREFLOAD,
        // xgettext: This is the description of the PREFLOAD command.
        .description = strtext("restore preferences from disk"),
    },

    {
        // BRL_CMD_MENU_FIRST_ITEM
        .name = "MENU_FIRST_ITEM",
        .code = BRL_CMD_MENU_FIRST_ITEM,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the MENU_FIRST_ITEM command.
        .description = strtext("go up to first item"),
    },

    {
        // BRL_CMD_MENU_LAST_ITEM
        .name = "MENU_LAST_ITEM",
        .code = BRL_CMD_MENU_LAST_ITEM,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the MENU_LAST_ITEM command.
        .description = strtext("go down to last item"),
    },

    {
        // BRL_CMD_MENU_PREV_ITEM
        .name = "MENU_PREV_ITEM",
        .code = BRL_CMD_MENU_PREV_ITEM,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the MENU_PREV_ITEM command.
        .description = strtext("go up to previous item"),
    },

    {
        // BRL_CMD_MENU_NEXT_ITEM
        .name = "MENU_NEXT_ITEM",
        .code = BRL_CMD_MENU_NEXT_ITEM,
        .isMotion = 1,
        .isVertical = 1,
        // xgettext: This is the description of the MENU_NEXT_ITEM command.
        .description = strtext("go down to next item"),
    },

    {
        // BRL_CMD_MENU_PREV_SETTING
        .name = "MENU_PREV_SETTING",
        .code = BRL_CMD_MENU_PREV_SETTING,
        // xgettext: This is the description of the MENU_PREV_SETTING command.
        .description = strtext("select previous choice"),
    },

    {
        // BRL_CMD_MENU_NEXT_SETTING
        .name = "MENU_NEXT_SETTING",
        .code = BRL_CMD_MENU_NEXT_SETTING,
        // xgettext: This is the description of the MENU_NEXT_SETTING command.
        .description = strtext("select next choice"),
    },

    {
        // BRL_CMD_MUTE
        .name = "MUTE",
        .code = BRL_CMD_MUTE,
        // xgettext: This is the description of the MUTE command.
        .description = strtext("stop speaking"),
    },

    {
        // BRL_CMD_SPKHOME
        .name = "SPKHOME",
        .code = BRL_CMD_SPKHOME,
        .isMotion = 1,
        // xgettext: This is the description of the SPKHOME command.
        .description = strtext("go to current speaking position"),
    },

    {
        // BRL_CMD_SAY_LINE
        .name = "SAY_LINE",
        .code = BRL_CMD_SAY_LINE,
        // xgettext: This is the description of the SAY_LINE command.
        .description = strtext("speak current line"),
    },

    {
        // BRL_CMD_SAY_ABOVE
        .name = "SAY_ABOVE",
        .code = BRL_CMD_SAY_ABOVE,
        // xgettext: This is the description of the SAY_ABOVE command.
        .description = strtext("speak from top of screen through current line"),
    },

    {
        // BRL_CMD_SAY_BELOW
        .name = "SAY_BELOW",
        .code = BRL_CMD_SAY_BELOW,
        // xgettext: This is the description of the SAY_BELOW command.
        .description =
            strtext("speak from current line through bottom of screen"),
    },

    {
        // BRL_CMD_SAY_SLOWER
        .name = "SAY_SLOWER",
        .code = BRL_CMD_SAY_SLOWER,
        // xgettext: This is the description of the SAY_SLOWER command.
        .description = strtext("decrease speaking rate"),
    },

    {
        // BRL_CMD_SAY_FASTER
        .name = "SAY_FASTER",
        .code = BRL_CMD_SAY_FASTER,
        // xgettext: This is the description of the SAY_FASTER command.
        .description = strtext("increase speaking rate"),
    },

    {
        // BRL_CMD_SAY_SOFTER
        .name = "SAY_SOFTER",
        .code = BRL_CMD_SAY_SOFTER,
        // xgettext: This is the description of the SAY_SOFTER command.
        .description = strtext("decrease speaking volume"),
    },

    {
        // BRL_CMD_SAY_LOUDER
        .name = "SAY_LOUDER",
        .code = BRL_CMD_SAY_LOUDER,
        // xgettext: This is the description of the SAY_LOUDER command.
        .description = strtext("increase speaking volume"),
    },

    {
        // BRL_CMD_SWITCHVT_PREV
        .name = "SWITCHVT_PREV",
        .code = BRL_CMD_SWITCHVT_PREV,
        // xgettext: This is the description of the SWITCHVT_PREV command.
        .description = strtext("switch to the previous virtual terminal"),
    },

    {
        // BRL_CMD_SWITCHVT_NEXT
        .name = "SWITCHVT_NEXT",
        .code = BRL_CMD_SWITCHVT_NEXT,
        // xgettext: This is the description of the SWITCHVT_NEXT command.
        .description = strtext("switch to the next virtual terminal"),
    },

    {
        // BRL_CMD_CSRJMP_VERT
        .name = "CSRJMP_VERT",
        .code = BRL_CMD_CSRJMP_VERT,
        .isRouting = 1,
        // xgettext: This is the description of the CSRJMP_VERT command.
        .description = strtext("bring screen cursor to current line"),
    },

    {
        // BRL_CMD_PASTE
        .name = "PASTE",
        .code = BRL_CMD_PASTE,
        // xgettext: This is the description of the PASTE command.
        .description = strtext("insert clipboard text after screen cursor"),
    },

    {
        // BRL_CMD_RESTARTBRL
        .name = "RESTARTBRL",
        .code = BRL_CMD_RESTARTBRL,
        // xgettext: This is the description of the RESTARTBRL command.
        .description = strtext("restart braille driver"),
    },

    {
        // BRL_CMD_RESTARTSPEECH
        .name = "RESTARTSPEECH",
        .code = BRL_CMD_RESTARTSPEECH,
        // xgettext: This is the description of the RESTARTSPEECH command.
        .description = strtext("restart speech driver"),
    },

    {
        // BRL_CMD_OFFLINE
        .name = "OFFLINE",
        .code = BRL_CMD_OFFLINE,
        // xgettext: This is the description of the OFFLINE command.
        .description = strtext("braille display temporarily unavailable"),
    },

    {
        // BRL_CMD_SHIFT
        .name = "SHIFT",
        .code = BRL_CMD_SHIFT,
        // xgettext: This is the description of the SHIFT command.
        .description =
            strtext("cycle the Shift sticky input modifier (next, on, off)"),
    },

    {
        // BRL_CMD_UPPER
        .name = "UPPER",
        .code = BRL_CMD_UPPER,
        // xgettext: This is the description of the UPPER command.
        .description =
            strtext("cycle the Upper sticky input modifier (next, on, off)"),
    },

    {
        // BRL_CMD_CONTROL
        .name = "CONTROL",
        .code = BRL_CMD_CONTROL,
        // xgettext: This is the description of the CONTROL command.
        .description =
            strtext("cycle the Control sticky input modifier (next, on, off)"),
    },

    {
        // BRL_CMD_META
        .name = "META",
        .code = BRL_CMD_META,
        // xgettext: This is the description of the META command.
        .description = strtext(
            "cycle the Meta (Left Alt) sticky input modifier (next, on, off)"),
    },

    {
        // BRL_CMD_TIME
        .name = "TIME",
        .code = BRL_CMD_TIME,
        // xgettext: This is the description of the TIME command.
        .description = strtext("show current date and time"),
    },

    {
        // BRL_CMD_MENU_PREV_LEVEL
        .name = "MENU_PREV_LEVEL",
        .code = BRL_CMD_MENU_PREV_LEVEL,
        .isMotion = 1,
        // xgettext: This is the description of the MENU_PREV_LEVEL command.
        .description = strtext("go to previous menu level"),
    },

    {
        // BRL_CMD_ASPK_SEL_LINE
        .name = "ASPK_SEL_LINE",
        .code = BRL_CMD_ASPK_SEL_LINE,
        .isToggle = 1,
        // xgettext: This is the description of the ASPK_SEL_LINE command.
        .description = strtext("set autospeak selected line on/off"),
    },

    {
        // BRL_CMD_ASPK_SEL_CHAR
        .name = "ASPK_SEL_CHAR",
        .code = BRL_CMD_ASPK_SEL_CHAR,
        .isToggle = 1,
        // xgettext: This is the description of the ASPK_SEL_CHAR command.
        .description = strtext("set autospeak selected character on/off"),
    },

    {
        // BRL_CMD_ASPK_INS_CHARS
        .name = "ASPK_INS_CHARS",
        .code = BRL_CMD_ASPK_INS_CHARS,
        .isToggle = 1,
        // xgettext: This is the description of the ASPK_INS_CHARS command.
        .description = strtext("set autospeak inserted characters on/off"),
    },

    {
        // BRL_CMD_ASPK_DEL_CHARS
        .name = "ASPK_DEL_CHARS",
        .code = BRL_CMD_ASPK_DEL_CHARS,
        .isToggle = 1,
        // xgettext: This is the description of the ASPK_DEL_CHARS command.
        .description = strtext("set autospeak deleted characters on/off"),
    },

    {
        // BRL_CMD_ASPK_REP_CHARS
        .name = "ASPK_REP_CHARS",
        .code = BRL_CMD_ASPK_REP_CHARS,
        .isToggle = 1,
        // xgettext: This is the description of the ASPK_REP_CHARS command.
        .description = strtext("set autospeak replaced characters on/off"),
    },

    {
        // BRL_CMD_ASPK_CMP_WORDS
        .name = "ASPK_CMP_WORDS",
        .code = BRL_CMD_ASPK_CMP_WORDS,
        .isToggle = 1,
        // xgettext: This is the description of the ASPK_CMP_WORDS command.
        .description = strtext("set autospeak completed words on/off"),
    },

    {
        // BRL_CMD_SPEAK_CURR_CHAR
        .name = "SPEAK_CURR_CHAR",
        .code = BRL_CMD_SPEAK_CURR_CHAR,
        // xgettext: This is the description of the SPEAK_CURR_CHAR command.
        .description = strtext("speak current character"),
    },

    {
        // BRL_CMD_SPEAK_PREV_CHAR
        .name = "SPEAK_PREV_CHAR",
        .code = BRL_CMD_SPEAK_PREV_CHAR,
        .isMotion = 1,
        // xgettext: This is the description of the SPEAK_PREV_CHAR command.
        .description = strtext("go to and speak previous character"),
    },

    {
        // BRL_CMD_SPEAK_NEXT_CHAR
        .name = "SPEAK_NEXT_CHAR",
        .code = BRL_CMD_SPEAK_NEXT_CHAR,
        .isMotion = 1,
        // xgettext: This is the description of the SPEAK_NEXT_CHAR command.
        .description = strtext("go to and speak next character"),
    },

    {
        // BRL_CMD_SPEAK_CURR_WORD
        .name = "SPEAK_CURR_WORD",
        .code = BRL_CMD_SPEAK_CURR_WORD,
        // xgettext: This is the description of the SPEAK_CURR_WORD command.
        .description = strtext("speak current word"),
    },

    {
        // BRL_CMD_SPEAK_PREV_WORD
        .name = "SPEAK_PREV_WORD",
        .code = BRL_CMD_SPEAK_PREV_WORD,
        .isMotion = 1,
        // xgettext: This is the description of the SPEAK_PREV_WORD command.
        .description = strtext("go to and speak previous word"),
    },

    {
        // BRL_CMD_SPEAK_NEXT_WORD
        .name = "SPEAK_NEXT_WORD",
        .code = BRL_CMD_SPEAK_NEXT_WORD,
        .isMotion = 1,
        // xgettext: This is the description of the SPEAK_NEXT_WORD command.
        .description = strtext("go to and speak next word"),
    },

    {
        // BRL_CMD_SPEAK_CURR_LINE
        .name = "SPEAK_CURR_LINE",
        .code = BRL_CMD_SPEAK_CURR_LINE,
        // xgettext: This is the description of the SPEAK_CURR_LINE command.
        .description = strtext("speak current line"),
    },

    {
        // BRL_CMD_SPEAK_PREV_LINE
        .name = "SPEAK_PREV_LINE",
        .code = BRL_CMD_SPEAK_PREV_LINE,
        .isMotion = 1,
        // xgettext: This is the description of the SPEAK_PREV_LINE command.
        .description = strtext("go to and speak previous line"),
    },

    {
        // BRL_CMD_SPEAK_NEXT_LINE
        .name = "SPEAK_NEXT_LINE",
        .code = BRL_CMD_SPEAK_NEXT_LINE,
        .isMotion = 1,
        // xgettext: This is the description of the SPEAK_NEXT_LINE command.
        .description = strtext("go to and speak next line"),
    },

    {
        // BRL_CMD_SPEAK_FRST_CHAR
        .name = "SPEAK_FRST_CHAR",
        .code = BRL_CMD_SPEAK_FRST_CHAR,
        .isMotion = 1,
        // xgettext: This is the description of the SPEAK_FRST_CHAR command.
        .description =
            strtext("go to and speak first non-blank character on line"),
    },

    {
        // BRL_CMD_SPEAK_LAST_CHAR
        .name = "SPEAK_LAST_CHAR",
        .code = BRL_CMD_SPEAK_LAST_CHAR,
        .isMotion = 1,
        // xgettext: This is the description of the SPEAK_LAST_CHAR command.
        .description =
            strtext("go to and speak last non-blank character on line"),
    },

    {
        // BRL_CMD_SPEAK_FRST_LINE
        .name = "SPEAK_FRST_LINE",
        .code = BRL_CMD_SPEAK_FRST_LINE,
        .isMotion = 1,
        // xgettext: This is the description of the SPEAK_FRST_LINE command.
        .description =
            strtext("go to and speak first non-blank line on screen"),
    },

    {
        // BRL_CMD_SPEAK_LAST_LINE
        .name = "SPEAK_LAST_LINE",
        .code = BRL_CMD_SPEAK_LAST_LINE,
        .isMotion = 1,
        // xgettext: This is the description of the SPEAK_LAST_LINE command.
        .description = strtext("go to and speak last non-blank line on screen"),
    },

    {
        // BRL_CMD_DESC_CURR_CHAR
        .name = "DESC_CURR_CHAR",
        .code = BRL_CMD_DESC_CURR_CHAR,
        // xgettext: This is the description of the DESC_CURR_CHAR command.
        .description = strtext("describe current character"),
    },

    {
        // BRL_CMD_SPELL_CURR_WORD
        .name = "SPELL_CURR_WORD",
        .code = BRL_CMD_SPELL_CURR_WORD,
        // xgettext: This is the description of the SPELL_CURR_WORD command.
        .description = strtext("spell current word"),
    },

    {
        // BRL_CMD_ROUTE_CURR_LOCN
        .name = "ROUTE_CURR_LOCN",
        .code = BRL_CMD_ROUTE_CURR_LOCN,
        .isRouting = 1,
        // xgettext: This is the description of the ROUTE_CURR_LOCN command.
        .description = strtext("bring screen cursor to speech cursor"),
    },

    {
        // BRL_CMD_SPEAK_CURR_LOCN
        .name = "SPEAK_CURR_LOCN",
        .code = BRL_CMD_SPEAK_CURR_LOCN,
        // xgettext: This is the description of the SPEAK_CURR_LOCN command.
        .description = strtext("speak speech cursor location"),
    },

    {
        // BRL_CMD_SHOW_CURR_LOCN
        .name = "SHOW_CURR_LOCN",
        .code = BRL_CMD_SHOW_CURR_LOCN,
        .isToggle = 1,
        // xgettext: This is the description of the SHOW_CURR_LOCN command.
        .description = strtext("set speech cursor visibility on/off"),
    },

    {
        // BRL_CMD_CLIP_SAVE
        .name = "CLIP_SAVE",
        .code = BRL_CMD_CLIP_SAVE,
        // xgettext: This is the description of the CLIP_SAVE command.
        .description = strtext("save clipboard to disk"),
    },

    {
        // BRL_CMD_CLIP_RESTORE
        .name = "CLIP_RESTORE",
        .code = BRL_CMD_CLIP_RESTORE,
        // xgettext: This is the description of the CLIP_RESTORE command.
        .description = strtext("restore clipboard from disk"),
    },

    {
        // BRL_CMD_BRLUCDOTS
        .name = "BRLUCDOTS",
        .code = BRL_CMD_BRLUCDOTS,
        .isToggle = 1,
        // xgettext: This is the description of the BRLUCDOTS command.
        .description = strtext("set braille typing mode dots/text"),
    },

    {
        // BRL_CMD_BRLKBD
        .name = "BRLKBD",
        .code = BRL_CMD_BRLKBD,
        .isToggle = 1,
        // xgettext: This is the description of the BRLKBD command.
        .description = strtext("set braille keyboard enabled/disabled"),
    },

    {
        // BRL_CMD_UNSTICK
        .name = "UNSTICK",
        .code = BRL_CMD_UNSTICK,
        // xgettext: This is the description of the UNSTICK command.
        .description = strtext("clear all sticky input modifiers"),
    },

    {
        // BRL_CMD_ALTGR
        .name = "ALTGR",
        .code = BRL_CMD_ALTGR,
        // xgettext: This is the description of the ALTGR command.
        .description = strtext("cycle the AltGr (Right Alt) sticky input "
                               "modifier (next, on, off)"),
    },

    {
        // BRL_CMD_GUI
        .name = "GUI",
        .code = BRL_CMD_GUI,
        // xgettext: This is the description of the GUI command.
        .description = strtext(
            "cycle the GUI (Windows) sticky input modifier (next, on, off)"),
    },

    {
        // BRL_CMD_BRL_STOP
        .name = "BRL_STOP",
        .code = BRL_CMD_BRL_STOP,
        // xgettext: This is the description of the BRL_STOP command.
        .description = strtext("stop the braille driver"),
    },

    {
        // BRL_CMD_BRL_START
        .name = "BRL_START",
        .code = BRL_CMD_BRL_START,
        // xgettext: This is the description of the BRL_START command.
        .description = strtext("start the braille driver"),
    },

    {
        // BRL_CMD_SPK_STOP
        .name = "SPK_STOP",
        .code = BRL_CMD_SPK_STOP,
        // xgettext: This is the description of the SPK_STOP command.
        .description = strtext("stop the speech driver"),
    },

    {
        // BRL_CMD_SPK_START
        .name = "SPK_START",
        .code = BRL_CMD_SPK_START,
        // xgettext: This is the description of the SPK_START command.
        .description = strtext("start the speech driver"),
    },

    {
        // BRL_CMD_SCR_STOP
        .name = "SCR_STOP",
        .code = BRL_CMD_SCR_STOP,
        // xgettext: This is the description of the SCR_STOP command.
        .description = strtext("stop the screen driver"),
    },

    {
        // BRL_CMD_SCR_START
        .name = "SCR_START",
        .code = BRL_CMD_SCR_START,
        // xgettext: This is the description of the SCR_START command.
        .description = strtext("start the screen driver"),
    },

    {
        // BRL_CMD_SELECTVT_PREV
        .name = "SELECTVT_PREV",
        .code = BRL_CMD_SELECTVT_PREV,
        // xgettext: This is the description of the SELECTVT_PREV command.
        .description = strtext("bind to the previous virtual terminal"),
    },

    {
        // BRL_CMD_SELECTVT_NEXT
        .name = "SELECTVT_NEXT",
        .code = BRL_CMD_SELECTVT_NEXT,
        // xgettext: This is the description of the SELECTVT_NEXT command.
        .description = strtext("bind to the next virtual terminal"),
    },

    {
        // BRL_CMD_PRNBWIN
        .name = "PRNBWIN",
        .code = BRL_CMD_PRNBWIN,
        .isMotion = 1,
        .isPanning = 1,
        // xgettext: This is the description of the PRNBWIN command.
        .description =
            strtext("go backward to nearest non-blank braille window"),
    },

    {
        // BRL_CMD_NXNBWIN
        .name = "NXNBWIN",
        .code = BRL_CMD_NXNBWIN,
        .isMotion = 1,
        .isPanning = 1,
        // xgettext: This is the description of the NXNBWIN command.
        .description =
            strtext("go forward to nearest non-blank braille window"),
    },

    {
        // BRL_CMD_TOUCH_NAV
        .name = "TOUCH_NAV",
        .code = BRL_CMD_TOUCH_NAV,
        .isToggle = 1,
        // xgettext: This is the description of the TOUCH_NAV command.
        .description = strtext("set touch navigation on/off"),
    },

    {
        // BRL_CMD_SPEAK_INDENT
        .name = "SPEAK_INDENT",
        .code = BRL_CMD_SPEAK_INDENT,
        // xgettext: This is the description of the SPEAK_INDENT command.
        .description = strtext("speak indent of current line"),
    },

    {
        // BRL_CMD_ASPK_INDENT
        .name = "ASPK_INDENT",
        .code = BRL_CMD_ASPK_INDENT,
        .isToggle = 1,
        // xgettext: This is the description of the ASPK_INDENT command.
        .description = strtext("set autospeak indent of current line on/off"),
    },

    {
        // BRL_CMD_REFRESH
        .name = "REFRESH",
        .code = BRL_CMD_REFRESH,
        // xgettext: This is the description of the REFRESH command.
        .description = strtext("refresh braille display"),
    },

    {
        // BRL_CMD_INDICATORS
        .name = "INDICATORS",
        .code = BRL_CMD_INDICATORS,
        // xgettext: This is the description of the INDICATORS command.
        .description = strtext("show various device status indicators"),
    },

    {
        // BRL_CMD_TXTSEL_CLEAR
        .name = "TXTSEL_CLEAR",
        .code = BRL_CMD_TXTSEL_CLEAR,
        // xgettext: This is the description of the TXTSEL_CLEAR command.
        .description = strtext("clear the text selection"),
    },

    {
        // BRL_CMD_TXTSEL_ALL
        .name = "TXTSEL_ALL",
        .code = BRL_CMD_TXTSEL_ALL,
        // xgettext: This is the description of the TXTSEL_ALL command.
        .description = strtext("select all of the text"),
    },

    {
        // BRL_CMD_HOST_COPY
        .name = "HOST_COPY",
        .code = BRL_CMD_HOST_COPY,
        // xgettext: This is the description of the HOST_COPY command.
        .description = strtext("copy selected text to host clipboard"),
    },

    {
        // BRL_CMD_HOST_CUT
        .name = "HOST_CUT",
        .code = BRL_CMD_HOST_CUT,
        // xgettext: This is the description of the HOST_CUT command.
        .description = strtext("cut selected text to host clipboard"),
    },

    {
        // BRL_CMD_HOST_PASTE
        .name = "HOST_PASTE",
        .code = BRL_CMD_HOST_PASTE,
        // xgettext: This is the description of the HOST_PASTE command.
        .description =
            strtext("insert host clipboard text after screen cursor"),
    },

    {
        // BRL_CMD_GUI_TITLE
        .name = "GUI_TITLE",
        .code = BRL_CMD_GUI_TITLE,
        // xgettext: This is the description of the GUI_TITLE command.
        .description = strtext("show the window title"),
    },

    {
        // BRL_CMD_GUI_BRL_ACTIONS
        .name = "GUI_BRL_ACTIONS",
        .code = BRL_CMD_GUI_BRL_ACTIONS,
        // xgettext: This is the description of the GUI_BRL_ACTIONS command.
        .description = strtext("open the braille actions window"),
    },

    {
        // BRL_CMD_GUI_HOME
        .name = "GUI_HOME",
        .code = BRL_CMD_GUI_HOME,
        .isMotion = 1,
        // xgettext: This is the description of the GUI_HOME command.
        .description = strtext("go to the home screen"),
    },

    {
        // BRL_CMD_GUI_BACK
        .name = "GUI_BACK",
        .code = BRL_CMD_GUI_BACK,
        .isMotion = 1,
        // xgettext: This is the description of the GUI_BACK command.
        .description = strtext("go back to the previous screen"),
    },

    {
        // BRL_CMD_GUI_DEV_SETTINGS
        .name = "GUI_DEV_SETTINGS",
        .code = BRL_CMD_GUI_DEV_SETTINGS,
        // xgettext: This is the description of the GUI_DEV_SETTINGS command.
        .description = strtext("open the device settings window"),
    },

    {
        // BRL_CMD_GUI_DEV_OPTIONS
        .name = "GUI_DEV_OPTIONS",
        .code = BRL_CMD_GUI_DEV_OPTIONS,
        // xgettext: This is the description of the GUI_DEV_OPTIONS command.
        .description = strtext("open the device options window"),
    },

    {
        // BRL_CMD_GUI_APP_LIST
        .name = "GUI_APP_LIST",
        .code = BRL_CMD_GUI_APP_LIST,
        // xgettext: This is the description of the GUI_APP_LIST command.
        .description = strtext("open the application list window"),
    },

    {
        // BRL_CMD_GUI_APP_MENU
        .name = "GUI_APP_MENU",
        .code = BRL_CMD_GUI_APP_MENU,
        // xgettext: This is the description of the GUI_APP_MENU command.
        .description = strtext("open the application-specific menu"),
    },

    {
        // BRL_CMD_GUI_APP_ALERTS
        .name = "GUI_APP_ALERTS",
        .code = BRL_CMD_GUI_APP_ALERTS,
        // xgettext: This is the description of the GUI_APP_ALERTS command.
        .description = strtext("open the application alerts window"),
    },

    {
        // BRL_CMD_GUI_AREA_ACTV
        .name = "GUI_AREA_ACTV",
        .code = BRL_CMD_GUI_AREA_ACTV,
        // xgettext: This is the description of the GUI_AREA_ACTV command.
        .description = strtext("return to the active screen area"),
    },

    {
        // BRL_CMD_GUI_AREA_PREV
        .name = "GUI_AREA_PREV",
        .code = BRL_CMD_GUI_AREA_PREV,
        // xgettext: This is the description of the GUI_AREA_PREV command.
        .description = strtext("switch to the previous screen area"),
    },

    {
        // BRL_CMD_GUI_AREA_NEXT
        .name = "GUI_AREA_NEXT",
        .code = BRL_CMD_GUI_AREA_NEXT,
        // xgettext: This is the description of the GUI_AREA_NEXT command.
        .description = strtext("switch to the next screen area"),
    },

    {
        // BRL_CMD_GUI_ITEM_FRST
        .name = "GUI_ITEM_FRST",
        .code = BRL_CMD_GUI_ITEM_FRST,
        // xgettext: This is the description of the GUI_ITEM_FRST command.
        .description = strtext("move to the first item in the screen area"),
    },

    {
        // BRL_CMD_GUI_ITEM_PREV
        .name = "GUI_ITEM_PREV",
        .code = BRL_CMD_GUI_ITEM_PREV,
        // xgettext: This is the description of the GUI_ITEM_PREV command.
        .description = strtext("move to the previous item in the screen area"),
    },

    {
        // BRL_CMD_GUI_ITEM_NEXT
        .name = "GUI_ITEM_NEXT",
        .code = BRL_CMD_GUI_ITEM_NEXT,
        // xgettext: This is the description of the GUI_ITEM_NEXT command.
        .description = strtext("move to the next item in the screen area"),
    },

    {
        // BRL_CMD_GUI_ITEM_LAST
        .name = "GUI_ITEM_LAST",
        .code = BRL_CMD_GUI_ITEM_LAST,
        // xgettext: This is the description of the GUI_ITEM_LAST command.
        .description = strtext("move to the last item in the screen area"),
    },

    {
        // BRL_CMD_SAY_LOWER
        .name = "SAY_LOWER",
        .code = BRL_CMD_SAY_LOWER,
        // xgettext: This is the description of the SAY_LOWER command.
        .description = strtext("decrease speaking pitch"),
    },

    {
        // BRL_CMD_SAY_HIGHER
        .name = "SAY_HIGHER",
        .code = BRL_CMD_SAY_HIGHER,
        // xgettext: This is the description of the SAY_HIGHER command.
        .description = strtext("increase speaking pitch"),
    },

    {
        // BRL_CMD_SAY_ALL
        .name = "SAY_ALL",
        .code = BRL_CMD_SAY_ALL,
        // xgettext: This is the description of the SAY_ALL command.
        .description =
            strtext("speak from top of screen through bottom of screen"),
    },

    {
        // BRL_CMD_CONTRACTED
        .name = "CONTRACTED",
        .code = BRL_CMD_CONTRACTED,
        .isToggle = 1,
        // xgettext: This is the description of the CONTRACTED command.
        .description = strtext("set contracted/computer braille"),
    },

    {
        // BRL_CMD_COMPBRL6
        .name = "COMPBRL6",
        .code = BRL_CMD_COMPBRL6,
        .isToggle = 1,
        // xgettext: This is the description of the COMPBRL6 command.
        .description = strtext("set six/eight dot computer braille"),
    },

    {
        // BRL_CMD_PREFRESET
        .name = "PREFRESET",
        .code = BRL_CMD_PREFRESET,
        // xgettext: This is the description of the PREFRESET command.
        .description = strtext("reset preferences to defaults"),
    },

    {
        // BRL_BLK_ROUTE
        .name = "ROUTE",
        .code = BRL_CMD_BLK(ROUTE),
        .isRouting = 1,
        .isColumn = 1,
        // xgettext: This is the description of the ROUTE command.
        .description = strtext("bring screen cursor to character"),
    },

    {
        // BRL_BLK_CLIP_NEW
        .name = "CLIP_NEW",
        .code = BRL_CMD_BLK(CLIP_NEW),
        .isColumn = 1,
        // xgettext: This is the description of the CLIP_NEW command.
        .description = strtext("start new clipboard at character"),
    },

    {
        // BRL_BLK_CLIP_ADD
        .name = "CLIP_ADD",
        .code = BRL_CMD_BLK(CLIP_ADD),
        .isColumn = 1,
        // xgettext: This is the description of the CLIP_ADD command.
        .description = strtext("append to clipboard from character"),
    },

    {
        // BRL_BLK_COPY_RECT
        .name = "COPY_RECT",
        .code = BRL_CMD_BLK(COPY_RECT),
        .isColumn = 1,
        // xgettext: This is the description of the COPY_RECT command.
        .description = strtext("rectangular copy to character"),
    },

    {
        // BRL_BLK_COPY_LINE
        .name = "COPY_LINE",
        .code = BRL_CMD_BLK(COPY_LINE),
        .isColumn = 1,
        // xgettext: This is the description of the COPY_LINE command.
        .description = strtext("linear copy to character"),
    },

    {
        // BRL_BLK_SWITCHVT
        .name = "SWITCHVT",
        .code = BRL_CMD_BLK(SWITCHVT),
        .isOffset = 1,
        // xgettext: This is the description of the SWITCHVT command.
        .description = strtext("switch to specific virtual terminal"),
    },

    {
        // BRL_BLK_PRINDENT
        .name = "PRINDENT",
        .code = BRL_CMD_BLK(PRINDENT),
        .isMotion = 1,
        .isVertical = 1,
        .isColumn = 1,
        // xgettext: This is the description of the PRINDENT command.
        .description =
            strtext("go up to nearest line with less indent than character"),
    },

    {
        // BRL_BLK_NXINDENT
        .name = "NXINDENT",
        .code = BRL_CMD_BLK(NXINDENT),
        .isMotion = 1,
        .isVertical = 1,
        .isColumn = 1,
        // xgettext: This is the description of the NXINDENT command.
        .description =
            strtext("go down to nearest line with less indent than character"),
    },

    {
        // BRL_BLK_DESCCHAR
        .name = "DESCCHAR",
        .code = BRL_CMD_BLK(DESCCHAR),
        .isColumn = 1,
        // xgettext: This is the description of the DESCCHAR command.
        .description = strtext("describe character"),
    },

    {
        // BRL_BLK_SETLEFT
        .name = "SETLEFT",
        .code = BRL_CMD_BLK(SETLEFT),
        .isColumn = 1,
        // xgettext: This is the description of the SETLEFT command.
        .description = strtext("place left end of braille window at character"),
    },

    {
        // BRL_BLK_SETMARK
        .name = "SETMARK",
        .code = BRL_CMD_BLK(SETMARK),
        .isOffset = 1,
        // xgettext: This is the description of the SETMARK command.
        .description = strtext("remember current braille window position"),
    },

    {
        // BRL_BLK_GOTOMARK
        .name = "GOTOMARK",
        .code = BRL_CMD_BLK(GOTOMARK),
        .isMotion = 1,
        .isOffset = 1,
        // xgettext: This is the description of the GOTOMARK command.
        .description = strtext("go to remembered braille window position"),
    },

    {
        // BRL_BLK_GOTOLINE
        .name = "GOTOLINE",
        .code = BRL_CMD_BLK(GOTOLINE),
        .isMotion = 1,
        .isRow = 1,
        .isVertical = 1,
        // xgettext: This is the description of the GOTOLINE command.
        .description = strtext("go to selected line"),
    },

    {
        // BRL_BLK_PRDIFCHAR
        .name = "PRDIFCHAR",
        .code = BRL_CMD_BLK(PRDIFCHAR),
        .isMotion = 1,
        .isVertical = 1,
        .isColumn = 1,
        // xgettext: This is the description of the PRDIFCHAR command.
        .description =
            strtext("go up to nearest line with different character"),
    },

    {
        // BRL_BLK_NXDIFCHAR
        .name = "NXDIFCHAR",
        .code = BRL_CMD_BLK(NXDIFCHAR),
        .isMotion = 1,
        .isVertical = 1,
        .isColumn = 1,
        // xgettext: This is the description of the NXDIFCHAR command.
        .description =
            strtext("go down to nearest line with different character"),
    },

    {
        // BRL_BLK_CLIP_COPY
        .name = "CLIP_COPY",
        .code = BRL_CMD_BLK(CLIP_COPY),
        .isRange = 1,
        // xgettext: This is the description of the CLIP_COPY command.
        .description = strtext("copy characters to clipboard"),
    },

    {
        // BRL_BLK_CLIP_APPEND
        .name = "CLIP_APPEND",
        .code = BRL_CMD_BLK(CLIP_APPEND),
        .isRange = 1,
        // xgettext: This is the description of the CLIP_APPEND command.
        .description = strtext("append characters to clipboard"),
    },

    {
        // BRL_BLK_PASTE_HISTORY
        .name = "PASTE_HISTORY",
        .code = BRL_CMD_BLK(PASTE_HISTORY),
        .isOffset = 1,
        // xgettext: This is the description of the PASTE_HISTORY command.
        .description =
            strtext("insert clipboard history entry after screen cursor"),
    },

    {
        // BRL_BLK_SET_TEXT_TABLE
        .name = "SET_TEXT_TABLE",
        .code = BRL_CMD_BLK(SET_TEXT_TABLE),
        .isOffset = 1,
        // xgettext: This is the description of the SET_TEXT_TABLE command.
        .description = strtext("set text table"),
    },

    {
        // BRL_BLK_SET_ATTRIBUTES_TABLE
        .name = "SET_ATTRIBUTES_TABLE",
        .code = BRL_CMD_BLK(SET_ATTRIBUTES_TABLE),
        .isOffset = 1,
        // xgettext: This is the description of the SET_ATTRIBUTES_TABLE
        // command.
        .description = strtext("set attributes table"),
    },

    {
        // BRL_BLK_SET_CONTRACTION_TABLE
        .name = "SET_CONTRACTION_TABLE",
        .code = BRL_CMD_BLK(SET_CONTRACTION_TABLE),
        .isOffset = 1,
        // xgettext: This is the description of the SET_CONTRACTION_TABLE
        // command.
        .description = strtext("set contraction table"),
    },

    {
        // BRL_BLK_SET_KEYBOARD_TABLE
        .name = "SET_KEYBOARD_TABLE",
        .code = BRL_CMD_BLK(SET_KEYBOARD_TABLE),
        .isOffset = 1,
        // xgettext: This is the description of the SET_KEYBOARD_TABLE command.
        .description = strtext("set keyboard table"),
    },

    {
        // BRL_BLK_SET_LANGUAGE_PROFILE
        .name = "SET_LANGUAGE_PROFILE",
        .code = BRL_CMD_BLK(SET_LANGUAGE_PROFILE),
        .isOffset = 1,
        // xgettext: This is the description of the SET_LANGUAGE_PROFILE
        // command.
        .description = strtext("set language profile"),
    },

    {
        // BRL_BLK_ROUTE_LINE
        .name = "ROUTE_LINE",
        .code = BRL_CMD_BLK(ROUTE_LINE),
        .isRouting = 1,
        .isRow = 1,
        .isVertical = 1,
        // xgettext: This is the description of the ROUTE_LINE command.
        .description = strtext("bring screen cursor to line"),
    },

    {
        // BRL_BLK_REFRESH_LINE
        .name = "REFRESH_LINE",
        .code = BRL_CMD_BLK(REFRESH_LINE),
        .isRow = 1,
        .isVertical = 1,
        // xgettext: This is the description of the REFRESH_LINE command.
        .description = strtext("refresh braille line"),
    },

    {
        // BRL_BLK_TXTSEL_START
        .name = "TXTSEL_START",
        .code = BRL_CMD_BLK(TXTSEL_START),
        .isOffset = 1,
        // xgettext: This is the description of the TXTSEL_START command.
        .description = strtext("start text selection"),
    },

    {
        // BRL_BLK_TXTSEL_SET
        .name = "TXTSEL_SET",
        .code = BRL_CMD_BLK(TXTSEL_SET),
        .isOffset = 1,
        // xgettext: This is the description of the TXTSEL_SET command.
        .description = strtext("set text selection"),
    },

    {
        // BRL_BLK_ROUTE_SPEECH
        .name = "ROUTE_SPEECH",
        .code = BRL_CMD_BLK(ROUTE_SPEECH),
        .isRouting = 1,
        .isColumn = 1,
        // xgettext: This is the description of the ROUTE_SPEECH command.
        .description = strtext("bring speech cursor to character"),
    },

    {
        // BRL_BLK_SELECTVT
        .name = "SELECTVT",
        .code = BRL_CMD_BLK(SELECTVT),
        .isOffset = 1,
        // xgettext: This is the description of the SELECTVT command.
        .description = strtext("bind to specific virtual terminal"),
    },

    {
        // BRL_BLK_ALERT
        .name = "ALERT",
        .code = BRL_CMD_BLK(ALERT),
        .isOffset = 1,
        // xgettext: This is the description of the ALERT command.
        .description = strtext("render an alert"),
    },

    {
        // BRL_BLK_PASSCHAR
        .name = "PASSCHAR",
        .code = BRL_CMD_BLK(PASSCHAR),
        .isInput = 1,
        .isCharacter = 1,
        // xgettext: This is the description of the PASSCHAR command.
        .description = strtext("type unicode character"),
    },

    {
        // BRL_BLK_PASSDOTS
        .name = "PASSDOTS",
        .code = BRL_CMD_BLK(PASSDOTS),
        .isInput = 1,
        .isBraille = 1,
        // xgettext: This is the description of the PASSDOTS command.
        .description = strtext("type braille dots"),
    },

    {
        // BRL_BLK_PASSAT
        .name = "PASSAT",
        .code = BRL_CMD_BLK(PASSAT),
        .isKeyboard = 1,
        // xgettext: This is the description of the PASSAT command.
        .description = strtext("AT (set 2) keyboard scan code"),
    },

    {
        // BRL_BLK_PASSXT
        .name = "PASSXT",
        .code = BRL_CMD_BLK(PASSXT),
        .isKeyboard = 1,
        // xgettext: This is the description of the PASSXT command.
        .description = strtext("XT (set 1) keyboard scan code"),
    },

    {
        // BRL_BLK_PASSPS2
        .name = "PASSPS2",
        .code = BRL_CMD_BLK(PASSPS2),
        .isKeyboard = 1,
        // xgettext: This is the description of the PASSPS2 command.
        .description = strtext("PS/2 (set 3) keyboard scan code"),
    },

    {
        // BRL_BLK_CONTEXT
        .name = "CONTEXT",
        .code = BRL_CMD_BLK(CONTEXT),
        .isOffset = 1,
        // xgettext: This is the description of the CONTEXT command.
        .description = strtext("switch to command context"),
    },

    {
        // BRL_BLK_TOUCH_AT
        .name = "TOUCH_AT",
        .code = BRL_CMD_BLK(TOUCH_AT),
        .isOffset = 1,
        // xgettext: This is the description of the TOUCH_AT command.
        .description = strtext("current reading location"),
    },

    {
        // BRL_BLK_MACRO
        .name = "MACRO",
        .code = BRL_CMD_BLK(MACRO),
        .isOffset = 1,
        // xgettext: This is the description of the MACRO command.
        .description = strtext("execute command macro"),
    },

    {
        // BRL_BLK_HOSTCMD
        .name = "HOSTCMD",
        .code = BRL_CMD_BLK(HOSTCMD),
        .isOffset = 1,
        // xgettext: This is the description of the HOSTCMD command.
        .description = strtext("run host command"),
    },

    {
        // BRL_KEY_ENTER
        .name = "KEY_ENTER",
        .code = BRL_CMD_KEY(ENTER),
        .isInput = 1,
        // xgettext: This is the description of the KEY_ENTER command.
        .description = strtext("enter key"),
    },

    {
        // BRL_KEY_TAB
        .name = "KEY_TAB",
        .code = BRL_CMD_KEY(TAB),
        .isInput = 1,
        // xgettext: This is the description of the KEY_TAB command.
        .description = strtext("tab key"),
    },

    {
        // BRL_KEY_BACKSPACE
        .name = "KEY_BACKSPACE",
        .code = BRL_CMD_KEY(BACKSPACE),
        .isInput = 1,
        // xgettext: This is the description of the KEY_BACKSPACE command.
        .description = strtext("backspace key"),
    },

    {
        // BRL_KEY_ESCAPE
        .name = "KEY_ESCAPE",
        .code = BRL_CMD_KEY(ESCAPE),
        .isInput = 1,
        // xgettext: This is the description of the KEY_ESCAPE command.
        .description = strtext("escape key"),
    },

    {
        // BRL_KEY_CURSOR_LEFT
        .name = "KEY_CURSOR_LEFT",
        .code = BRL_CMD_KEY(CURSOR_LEFT),
        .isInput = 1,
        // xgettext: This is the description of the KEY_CURSOR_LEFT command.
        .description = strtext("cursor-left key"),
    },

    {
        // BRL_KEY_CURSOR_RIGHT
        .name = "KEY_CURSOR_RIGHT",
        .code = BRL_CMD_KEY(CURSOR_RIGHT),
        .isInput = 1,
        // xgettext: This is the description of the KEY_CURSOR_RIGHT command.
        .description = strtext("cursor-right key"),
    },

    {
        // BRL_KEY_CURSOR_UP
        .name = "KEY_CURSOR_UP",
        .code = BRL_CMD_KEY(CURSOR_UP),
        .isInput = 1,
        // xgettext: This is the description of the KEY_CURSOR_UP command.
        .description = strtext("cursor-up key"),
    },

    {
        // BRL_KEY_CURSOR_DOWN
        .name = "KEY_CURSOR_DOWN",
        .code = BRL_CMD_KEY(CURSOR_DOWN),
        .isInput = 1,
        // xgettext: This is the description of the KEY_CURSOR_DOWN command.
        .description = strtext("cursor-down key"),
    },

    {
        // BRL_KEY_PAGE_UP
        .name = "KEY_PAGE_UP",
        .code = BRL_CMD_KEY(PAGE_UP),
        .isInput = 1,
        // xgettext: This is the description of the KEY_PAGE_UP command.
        .description = strtext("page-up key"),
    },

    {
        // BRL_KEY_PAGE_DOWN
        .name = "KEY_PAGE_DOWN",
        .code = BRL_CMD_KEY(PAGE_DOWN),
        .isInput = 1,
        // xgettext: This is the description of the KEY_PAGE_DOWN command.
        .description = strtext("page-down key"),
    },

    {
        // BRL_KEY_HOME
        .name = "KEY_HOME",
        .code = BRL_CMD_KEY(HOME),
        .isInput = 1,
        // xgettext: This is the description of the KEY_HOME command.
        .description = strtext("home key"),
    },

    {
        // BRL_KEY_END
        .name = "KEY_END",
        .code = BRL_CMD_KEY(END),
        .isInput = 1,
        // xgettext: This is the description of the KEY_END command.
        .description = strtext("end key"),
    },

    {
        // BRL_KEY_INSERT
        .name = "KEY_INSERT",
        .code = BRL_CMD_KEY(INSERT),
        .isInput = 1,
        // xgettext: This is the description of the KEY_INSERT command.
        .description = strtext("insert key"),
    },

    {
        // BRL_KEY_DELETE
        .name = "KEY_DELETE",
        .code = BRL_CMD_KEY(DELETE),
        .isInput = 1,
        // xgettext: This is the description of the KEY_DELETE command.
        .description = strtext("delete key"),
    },

    {
        // BRL_KEY_FUNCTION
        .name = "KEY_FUNCTION",
        .code = BRL_CMD_KEY(FUNCTION),
        .isInput = 1,
        .isOffset = 1,
        // xgettext: This is the description of the KEY_FUNCTION command.
        .description = strtext("function key"),
    },

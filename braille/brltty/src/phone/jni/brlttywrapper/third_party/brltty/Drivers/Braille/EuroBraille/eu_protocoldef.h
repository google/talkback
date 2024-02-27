
/////////////////////////////////////////////////////////////////
// LINK PROTOCOL - KEY
    // this key is operational only if subkey is 'B','D' or 'B','S' (LP_BRAILLE_DISPLAY,LP_BRAILLE_DISPLAY_STATIC/LP_BRAILLE_DISPLAY_DYNAMIC
#define LP_SPECIFIC_PROTOCOL '1'

#define LP_SYSTEM           'S'
#define LP_MODE             'R'
#define LP_KEY              'K'
#define LP_PARAMETER        'P'		//specific esytime2
#define LP_TRANSFERT        't'		// Specific Esytime.
#define LP_ENCRYPTION_KEY   'Z'
#define LP_BRAILLE_DISPLAY  'B'
#define LP_LCD_DISPLAY      'L'
//#define LP_TEST             'T'
//#define LP_SOUND            'M'
//#define LP_DEBUG            'D'      // Trame de Débug
#define LP_VISU             'V'      // Trame de Visualisation

// LINK PROTOCOL LP_ENCRYPTION_KEY
#define LP_ENCRYPTION_KEY_1 '1'
#define LP_ENCRYPTION_KEY_2 '2'
#define LP_ENCRYPTION_KEY_3 '3'
#define LP_EXT_KEY          'X'
#define LP_END_KEY          'E'

// LINK PROTOCOL KEYBOARD - SUBKey
#define LP_KEY_INTERACTIVE   'I'
#define LP_KEY_COMMAND       'C'
#define LP_KEY_OPTICAL       'O'
#define LP_KEY_BRAILLE       'B'
#define LP_KEY_PC            'Z'
#define LP_KEY_FINGER        'F'
#define LP_KEY_USB_HID_MODE  'U'
#define LP_KEY_USB			 'u'		// Trame touche à générer via l'USB (Spécific Esytouch)


// LINK PROTOCOL BRAILLEDISPLAY - SUBKEY
#define LP_BRAILLE_DISPLAY_STATIC       'S'
#define LP_BRAILLE_DISPLAY_DYNAMIC      'C'
#define LP_BRAILLE_DISPLAY_BLINK        'B'
//#define LP_BRAILLE_DISPLAY_DEBUG_TEXT     'X'

// LINK PROTOCOL BRAILLEDISPLAY - SUBKEY
#define LP_LCD_DISPLAY_TEXT         'T'
#define LP_LCD_DISPLAY_CARET        'C'

// LINK PROTOCOL SYSTEM - SUBKEY
#define LP_SYSTEM_IDENTITY          'I'
#define LP_SYSTEM_NAME              'N'
#define LP_SYSTEM_SHORTNAME         'H'
#define LP_SYSTEM_SERIAL            'S'
#define LP_SYSTEM_LANGUAGE          'L'
#define LP_SYSTEM_LANGUAGE_OPTION   'l'
#define LP_SYSTEM_BATTERY           'B'
#define LP_SYSTEM_DISPLAY_LENGTH    'G'
#define LP_SYSTEM_TYPE              'T'
#define LP_SYSTEM_OPTION            'O'
#define LP_SYSTEM_SOFTWARE          'W'
#define LP_SYSTEM_PROTOCOL          'P'
#define LP_SYSTEM_FRAME_LENGTH      'M'
#define LP_SYSTEM_DATE_AND_TIME		'D'
#define LP_SYSTEM_OPTICAL_VALUE     'o'

// LINK PROTOCOL PARAMETERS - SUBKEY
#define LP_PARAMETER_NAME			'N'
#define LP_PARAMETER_SHORT_NAME		'S'
#define LP_PARAMETER_SERIAL			'R'
#define LP_PARAMETER_OPTION			'O'


// LINK PROTOCOL TEST - SUBKEY
#define LP_TEST_LINK        'L'

// LINK PROTOCOL MODE - SUBKEY
#define LP_MODE_PILOT       'P'
#define LP_MODE_INTERNAL    'I'
#define LP_MODE_MENU        'M'
#define LP_MODE_SPECIFIC_PROTOCOL 'S'

// LINK PROTOCOL SOUND - SUBKEY
#define LP_SOUND_PLAY       'P'
#define LP_SOUND_STOP       'A'

// LINK PROTOCOL DEBUG - SUBKey
#define LP_DEBUG_SIMU       'S'     // FIX ME à supprimer dans quelques jours...
#define LP_DEBUG_TRACE      'T'     // FIX ME à supprimer dans quelques jours...
#define LP_DEBUG_LOG        'L'     
#define LP_DEBUG_WARN       'W'     
#define LP_DEBUG_ERROR      'E'     

// LINK PROTOCOL VISU - SUBKey
#define LP_VISU_TEXT        'T'     
#define LP_VISU_DOT         'D'     

// LINK PROTOCOL TRANSFERT - SUBKey
#define LP_TRANSFERT_PARAMETERS		'p' // Request to receive or send the assignment table.
//#define LP_TRANSFERT_TABLE          't' // Request to receive or send the assignment table.
#define LP_TRANSFERT_FIRMWARE       'f' // Request to send the assignment table.
#define LP_TRANSFERT_RECORD         'r'	// A record in Intel Hexa format.
#define LP_TRANSFERT_NEXT_RECORD    'n' // Request to receive the next record.
#define LP_TRANSFERT_ASK_CHECKSUM   'c' // Ask the checksum on complete hex file.
#define LP_TRANSFERT_END            'e' // Notify the end of transfert.
#define LP_TRANSFERT_ERROR          'o' // Error

/////////////////////////////////////////////////////////////////
// TYPE OF INTERACTIVE KEYS
#define INTERACTIVE_SINGLE_CLIC	0x01
#define INTERACTIVE_REPETITION	0x02
#define INTERACTIVE_DOUBLE_CLIC 0x03

/////////////////////////////////////////////////////////////////
// IRIS COMMANDS KEYBOARD
typedef enum {
    IRIS_L1_KEY        = 0x00000001,
    IRIS_L2_KEY        = 0x00000002,        
    IRIS_L3_KEY        = 0x00000004,
    IRIS_L4_KEY        = 0x00000008,
    IRIS_L5_KEY        = 0x00000010,
    IRIS_L6_KEY        = 0x00000020,
    IRIS_L7_KEY        = 0x00000040,
    IRIS_L8_KEY        = 0x00000080,
    IRIS_UP_KEY        = 0x00000100,
    IRIS_LEFT_KEY      = 0x00000800,
    IRIS_RIGHT_KEY     = 0x00000400,
    IRIS_DOWN_KEY      = 0x00000200,
} IRIS_COMMAND, * PIRIS_COMMAND;

/////////////////////////////////////////////////////////////////
// ESYS COMMANDS KEYBOARD
typedef enum {
    ESYS_SCROLLER1_RIGHT_KEY        = 0x00000001,
    ESYS_SCROLLER1_LEFT_KEY         = 0x00000002,        
    ESYS_SCROLLER2_RIGHT_KEY        = 0x00000004,
    ESYS_SCROLLER2_LEFT_KEY         = 0x00000008,
    ESYS_SCROLLER3_RIGHT_KEY        = 0x00000010,
    ESYS_SCROLLER3_LEFT_KEY         = 0x00000020,
    ESYS_SCROLLER4_RIGHT_KEY        = 0x00000040,
    ESYS_SCROLLER4_LEFT_KEY         = 0x00000080,
    ESYS_SCROLLER5_RIGHT_KEY        = 0x00000100,
    ESYS_SCROLLER5_LEFT_KEY         = 0x00000200,
    ESYS_SCROLLER6_RIGHT_KEY        = 0x00000400,
    ESYS_SCROLLER6_LEFT_KEY         = 0x00000800,
    ESYS_JOYSTICK1_UP_KEY           = 0x00010000,
    ESYS_JOYSTICK1_DOWN_KEY         = 0x00020000,
    ESYS_JOYSTICK1_RIGHT_KEY        = 0x00040000,
    ESYS_JOYSTICK1_LEFT_KEY         = 0x00080000,
    ESYS_JOYSTICK1_MIDDLE_KEY       = 0x00100000,    
    ESYS_JOYSTICK2_UP_KEY           = 0x01000000,
    ESYS_JOYSTICK2_DOWN_KEY         = 0x02000000,
    ESYS_JOYSTICK2_RIGHT_KEY        = 0x04000000,
    ESYS_JOYSTICK2_LEFT_KEY         = 0x08000000,
    ESYS_JOYSTICK2_MIDDLE_KEY       = 0x10000000
} ESYS_COMMAND, * PESYS_COMMAND;

/////////////////////////////////////////////////////////////////
// ESYTIME COMMANDS KEYBOARD
typedef enum {
    ESYTIME_L1_KEY                     = 0x00000001,
    ESYTIME_L2_KEY                     = 0x00000002,        
    ESYTIME_L3_KEY                     = 0x00000004,
    ESYTIME_L4_KEY                     = 0x00000008,
    ESYTIME_L5_KEY                     = 0x00000010,
    ESYTIME_L6_KEY                     = 0x00000020,
    ESYTIME_L7_KEY                     = 0x00000040,
    ESYTIME_L8_KEY                     = 0x00000080,
    ESYTIME_JOYSTICK1_UP_KEY           = 0x00010000,
    ESYTIME_JOYSTICK1_DOWN_KEY         = 0x00020000,
    ESYTIME_JOYSTICK1_RIGHT_KEY        = 0x00040000,
    ESYTIME_JOYSTICK1_LEFT_KEY         = 0x00080000,
    ESYTIME_JOYSTICK1_MIDDLE_KEY       = 0x00100000,    
    ESYTIME_JOYSTICK2_UP_KEY           = 0x01000000,
    ESYTIME_JOYSTICK2_DOWN_KEY         = 0x02000000,
    ESYTIME_JOYSTICK2_RIGHT_KEY        = 0x04000000,
    ESYTIME_JOYSTICK2_LEFT_KEY         = 0x08000000,
    ESYTIME_JOYSTICK2_MIDDLE_KEY       = 0x10000000
} ESYTIME_COMMAND, * PESYSTIME_COMMAND;


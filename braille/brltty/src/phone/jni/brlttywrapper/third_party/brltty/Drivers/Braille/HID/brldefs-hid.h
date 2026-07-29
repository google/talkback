#ifndef BRLTTY_INCLUDED_HID_BRLDEFS
#define BRLTTY_INCLUDED_HID_BRLDEFS

#include "third_party/brltty/Headers/hid_defs.h"
#include "third_party/brltty/Headers/ktb_types.h"

#define MAX_INPUT_SIZE 0XFF
#define MAX_OUTPUT_SIZE 0XFF
#define MAX_USAGE_COUNT 0xFF
#define MAXIMUM_KEY_VALUE 0XFF
#define KEYS_BITMASK(name) BITMASK(name, (MAXIMUM_KEY_VALUE + 1), int)

// Enum of Key values that are used by the KEYS_BITMASK in order to track
// which keys are currently pressed. The specific values are meaningless and
// only serve as a temporary identifier in the input parsing logic.
typedef enum {
  HID_KEY_Dot1 = 1,
  HID_KEY_Dot2,
  HID_KEY_Dot3,
  HID_KEY_Dot4,
  HID_KEY_Dot5,
  HID_KEY_Dot6,
  HID_KEY_Dot7,
  HID_KEY_Dot8,
  HID_KEY_Space,
  HID_KEY_SpaceLeft,
  HID_KEY_SpaceRight,

  HID_KEY_PanLeft,
  HID_KEY_PanRight,

  HID_KEY_DPadUp,
  HID_KEY_DPadDown,
  HID_KEY_DPadLeft,
  HID_KEY_DPadRight,
  HID_KEY_DPadCenter,

  HID_KEY_RockerUp,
  HID_KEY_RockerDown,
  HID_KEY_RockerPress,

  HID_KEY_JoystickCenter,
  HID_KEY_JoystickUp,
  HID_KEY_JoystickDown,
  HID_KEY_JoystickLeft,
  HID_KEY_JoystickRight,

  HID_KEY_ROUTING,
} HID_Keys;

// Maps from official Braille Display HID usages to the custom Key enum.
int KEY_MAP[][2] = {
    {HID_USG_BRL_KeyboardDot1, HID_KEY_Dot1},
    {HID_USG_BRL_KeyboardDot2, HID_KEY_Dot2},
    {HID_USG_BRL_KeyboardDot3, HID_KEY_Dot3},
    {HID_USG_BRL_KeyboardDot4, HID_KEY_Dot4},
    {HID_USG_BRL_KeyboardDot5, HID_KEY_Dot5},
    {HID_USG_BRL_KeyboardDot6, HID_KEY_Dot6},
    {HID_USG_BRL_KeyboardDot7, HID_KEY_Dot7},
    {HID_USG_BRL_KeyboardDot8, HID_KEY_Dot8},
    {HID_USG_BRL_KeyboardSpace, HID_KEY_Space},
    {HID_USG_BRL_KeyboardLeftSpace, HID_KEY_SpaceLeft},
    {HID_USG_BRL_KeyboardRightSpace, HID_KEY_SpaceRight},
    {HID_USG_BRL_PanLeft, HID_KEY_PanLeft},
    {HID_USG_BRL_PanRight, HID_KEY_PanRight},
    {HID_USG_BRL_DPadUp, HID_KEY_DPadUp},
    {HID_USG_BRL_DPadDown, HID_KEY_DPadDown},
    {HID_USG_BRL_DPadLeft, HID_KEY_DPadLeft},
    {HID_USG_BRL_DPadRight, HID_KEY_DPadRight},
    // BP24 internal pan keys.
    // BP24 exposes them as Button usages 0x14 and 0x15.
    {0x14, HID_KEY_DPadLeft},
    {0x15, HID_KEY_DPadRight},
    {HID_USG_BRL_DPadCenter, HID_KEY_DPadCenter},
    {HID_USG_BRL_RockerUp, HID_KEY_RockerUp},
    {HID_USG_BRL_RockerDown, HID_KEY_RockerDown},
    {HID_USG_BRL_RockerPress, HID_KEY_RockerPress},
    {HID_USG_BRL_JoystickCenter, HID_KEY_JoystickCenter},
    {HID_USG_BRL_JoystickUp, HID_KEY_JoystickUp},
    {HID_USG_BRL_JoystickDown, HID_KEY_JoystickDown},
    {HID_USG_BRL_JoystickLeft, HID_KEY_JoystickLeft},
    {HID_USG_BRL_JoystickRight, HID_KEY_JoystickRight},
    // Router keys are handled separately.
};
int KEY_MAP_COUNT = sizeof(KEY_MAP) / sizeof(KEY_MAP[0]);

typedef enum { HID_GRP_NavigationKeys = 0, HID_GRP_RoutingKeys } HID_KEYGroup;

// Maps from the Key enum to a textual Key name used by the HID.ktb keytable.
static const KeyNameEntry keyNameTable[] = {
    {.value.number = HID_KEY_Dot1, .name = "Dot1"},
    {.value.number = HID_KEY_Dot2, .name = "Dot2"},
    {.value.number = HID_KEY_Dot3, .name = "Dot3"},
    {.value.number = HID_KEY_Dot4, .name = "Dot4"},
    {.value.number = HID_KEY_Dot5, .name = "Dot5"},
    {.value.number = HID_KEY_Dot6, .name = "Dot6"},
    {.value.number = HID_KEY_Dot7, .name = "Dot7"},
    {.value.number = HID_KEY_Dot8, .name = "Dot8"},
    {.value.number = HID_KEY_Space, .name = "Space"},
    {.value.number = HID_KEY_SpaceLeft, .name = "SpaceLeft"},
    {.value.number = HID_KEY_SpaceRight, .name = "SpaceRight"},
    {.value.number = HID_KEY_PanLeft, .name = "PanLeft"},
    {.value.number = HID_KEY_PanRight, .name = "PanRight"},
    {.value.number = HID_KEY_DPadUp, .name = "DPadUp"},
    {.value.number = HID_KEY_DPadDown, .name = "DPadDown"},
    {.value.number = HID_KEY_DPadLeft, .name = "DPadLeft"},
    {.value.number = HID_KEY_DPadRight, .name = "DPadRight"},
    {.value.number = HID_KEY_DPadCenter, .name = "DPadCenter"},
    {.value.number = HID_KEY_RockerUp, .name = "RockerUp"},
    {.value.number = HID_KEY_RockerDown, .name = "RockerDown"},
    {.value.number = HID_KEY_RockerPress, .name = "RockerPress"},
    {.value.number = HID_KEY_JoystickCenter, .name = "JoystickCenter"},
    {.value.number = HID_KEY_JoystickUp, .name = "JoystickUp"},
    {.value.number = HID_KEY_JoystickDown, .name = "JoystickDown"},
    {.value.number = HID_KEY_JoystickLeft, .name = "JoystickLeft"},
    {.value.number = HID_KEY_JoystickRight, .name = "JoystickRight"},
    {.value = {.group = HID_GRP_RoutingKeys, .number = KTB_KEY_ANY},
     .name = "RoutingKey"},
    {.name = NULL}};

static const KeyNameEntry *const keyNameTables_HID[] = {keyNameTable, NULL};

static const KeyTableDefinition keyTableDefinition_HID = {
    .bindings = "HID", .names = keyNameTables_HID};

#endif /* BRLTTY_INCLUDED_HID_BRLDEFS */

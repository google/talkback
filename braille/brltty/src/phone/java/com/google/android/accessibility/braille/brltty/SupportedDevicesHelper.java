package com.google.android.accessibility.braille.brltty;

import androidx.annotation.Nullable;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Helper class maps device name patterns to device-related data. */
public class SupportedDevicesHelper {
  public static final List<SupportedDevice> supportedDevices;

  private SupportedDevicesHelper() {}

  @Nullable
  public static DeviceInfo getDeviceInfo(String deviceName) {
    for (SupportedDevice supportedDevice : supportedDevices) {
      DeviceInfo deviceInfo = supportedDevice.match(deviceName);
      if (deviceInfo != null) {
        return deviceInfo;
      }
    }
    return null;
  }

  private interface SupportedDevice {
    DeviceInfo match(String deviceName);
  }

  private static class NameRegexSupportedDevice implements SupportedDevice {
    private final String driverCode;
    private final boolean connectSecurely;
    private final Map<String, Integer> friendlyKeyNames;
    private final Pattern[] nameRegexes;

    public NameRegexSupportedDevice(
        String driverCode,
        boolean connectSecurely,
        Map<String, Integer> friendlyKeyNames,
        Pattern... nameRegexes) {
      this.driverCode = driverCode;
      this.connectSecurely = connectSecurely;
      this.friendlyKeyNames = friendlyKeyNames;
      this.nameRegexes = nameRegexes;
    }

    @Nullable
    @Override
    public DeviceInfo match(String deviceName) {
      for (Pattern nameRegex : nameRegexes) {
        if (nameRegex.matcher(deviceName).lookingAt()) {
          return DeviceInfo.builder()
              .setDriverCode(driverCode)
              .setFriendlyKeyNames(friendlyKeyNames)
              .setConnectSecurely(connectSecurely)
              .build();
        }
      }
      return null;
    }

    @Override
    public String toString() {
      StringBuilder s = new StringBuilder();
      s.append(driverCode);
      for (Pattern p : nameRegexes) {
        s.append(" ").append(p);
      }
      return s.toString();
    }
  }

  private static class KeyNameMapBuilder {
    private final Map<String, Integer> nameMap = new HashMap<>();

    /**
     * Adds a mapping from the internal {@code name} to a friendly name with resource id {@code
     * resId}.
     */
    @CanIgnoreReturnValue
    public KeyNameMapBuilder add(String name, int resId) {
      nameMap.put(name, resId);
      return this;
    }

    @CanIgnoreReturnValue
    public KeyNameMapBuilder dots6() {
      add("Dot1", R.string.key_Dot1);
      add("Dot2", R.string.key_Dot2);
      add("Dot3", R.string.key_Dot3);
      add("Dot4", R.string.key_Dot4);
      add("Dot5", R.string.key_Dot5);
      add("Dot6", R.string.key_Dot6);
      return this;
    }

    @CanIgnoreReturnValue
    public KeyNameMapBuilder dots8() {
      dots6();
      add("Dot7", R.string.key_Dot7);
      add("Dot8", R.string.key_Dot8);
      return this;
    }

    @CanIgnoreReturnValue
    public KeyNameMapBuilder routing() {
      return add("RoutingKey", R.string.key_Routing);
    }

    @CanIgnoreReturnValue
    public KeyNameMapBuilder dualJoysticks() {
      add("LeftJoystickLeft", R.string.key_LeftJoystickLeft);
      add("LeftJoystickRight", R.string.key_LeftJoystickRight);
      add("LeftJoystickUp", R.string.key_LeftJoystickUp);
      add("LeftJoystickDown", R.string.key_LeftJoystickDown);
      add("LeftJoystickPress", R.string.key_LeftJoystickCenter);
      add("RightJoystickLeft", R.string.key_RightJoystickLeft);
      add("RightJoystickRight", R.string.key_RightJoystickRight);
      add("RightJoystickUp", R.string.key_RightJoystickUp);
      add("RightJoystickDown", R.string.key_RightJoystickDown);
      add("RightJoystickPress", R.string.key_RightJoystickCenter);
      return this;
    }

    public Map<String, Integer> build() {
      return Collections.unmodifiableMap(nameMap);
    }
  }

  static {
    ArrayList<SupportedDevice> l = new ArrayList<>();

    // BraillePen
    l.add(
        new NameRegexSupportedDevice(
            "vo",
            true,
            new KeyNameMapBuilder()
                .dots6()
                .add("Shift", R.string.key_BP_Shift)
                .add("Space", R.string.key_Space)
                .add("Control", R.string.key_BP_Control)
                .add("JoystickLeft", R.string.key_JoystickLeft)
                .add("JoystickRight", R.string.key_JoystickRight)
                .add("JoystickUp", R.string.key_JoystickUp)
                .add("JoystickDown", R.string.key_JoystickDown)
                .add("JoystickEnter", R.string.key_JoystickCenter)
                .add("ScrollLeft", R.string.key_BP_ScrollLeft)
                .add("ScrollRight", R.string.key_BP_ScrollRight)
                .build(),
            Pattern.compile("EL12-")));

    // Esys
    l.add(
        new NameRegexSupportedDevice(
            "eu",
            true,
            new KeyNameMapBuilder()
                .dots8()
                .add("Switch1Left", R.string.key_esys_SwitchLeft)
                .add("Switch1Right", R.string.key_esys_SwitchRight)
                .dualJoysticks()
                .add("Backspace", R.string.key_Backspace)
                .add("Space", R.string.key_Space)
                .add("RoutingKey1", R.string.key_Routing)
                .build(),
            Pattern.compile("Esys-")));

    // Freedom Scientific Focus blue displays.
    l.add(
        new NameRegexSupportedDevice(
            "fs",
            true,
            new KeyNameMapBuilder()
                .dots8()
                .add("Space", R.string.key_Space)
                .add("PanLeft", R.string.key_pan_left)
                .add("PanRight", R.string.key_pan_right)
                .add("LeftWheelPress", R.string.key_focus_LeftWheelPress)
                .add("LeftWheelDown", R.string.key_focus_LeftWheelDown)
                .add("LeftWheelUp", R.string.key_focus_LeftWheelUp)
                .add("RightWheelPress", R.string.key_focus_RightWheelPress)
                .add("RightWheelDown", R.string.key_focus_RightWheelDown)
                .add("RightWheelUp", R.string.key_focus_RightWheelUp)
                .routing()
                .add("LeftShift", R.string.key_focus_LeftShift)
                .add("RightShift", R.string.key_focus_RightShift)
                .add("LeftGdf", R.string.key_focus_LeftGdf)
                .add("RightGdf", R.string.key_focus_RightGdf)
                .add("LeftRockerUp", R.string.key_focus_LeftRockerUp)
                .add("LeftRockerDown", R.string.key_focus_LeftRockerDown)
                .add("RightRockerUp", R.string.key_focus_RightRockerUp)
                .add("RightRockerDown", R.string.key_focus_RightRockerDown)
                .build(),
            Pattern.compile("Focus (40|14|80) BT"),
            Pattern.compile("FOCUS")));

    // Brailliant
    // Secure connections currently fail on Android devices for the
    // Brailliant.
    l.add(
        new NameRegexSupportedDevice(
            "hw",
            false,
            new KeyNameMapBuilder()
                .dots8()
                .add("Left", R.string.key_JoystickLeft)
                .add("Right", R.string.key_JoystickRight)
                .add("Up", R.string.key_JoystickUp)
                .add("Down", R.string.key_JoystickDown)
                .add("Press", R.string.key_JoystickCenter)
                .add("ThumbLeft", R.string.key_pan_left)
                .add("ThumbRight", R.string.key_pan_right)
                .routing()
                .add("Space", R.string.key_Space)
                .add("Power", R.string.key_brailliant_Power)
                .add("Display1", R.string.key_brailliant_Display1)
                .add("Display2", R.string.key_brailliant_Display2)
                .add("Display3", R.string.key_brailliant_Display3)
                .add("Display4", R.string.key_brailliant_Display4)
                .add("Display5", R.string.key_brailliant_Display5)
                .add("Display6", R.string.key_brailliant_Display6)
                .add("Thumb1", R.string.key_brailliant_Thumb1)
                .add("Thumb2", R.string.key_brailliant_Thumb2)
                .add("Thumb3", R.string.key_brailliant_Thumb3)
                .add("Thumb4", R.string.key_brailliant_Thumb4)
                .build(),
            Pattern.compile("Brailliant BI"),
            Pattern.compile("APH Mantis"),
            Pattern.compile("APH Chameleon"),
            Pattern.compile("NLS eReader H")));

    // HIMS
    l.add(
        new NameRegexSupportedDevice(
            "hm",
            false,
            new KeyNameMapBuilder()
                .dots8()
                .routing()
                .add("Space", R.string.key_Space)
                .add("F1", R.string.key_F1)
                .add("F2", R.string.key_F2)
                .add("F3", R.string.key_F3)
                .add("F4", R.string.key_F4)
                .add("Backward", R.string.key_Backward)
                .add("Forward", R.string.key_Forward)
                .build(),
            Pattern.compile("Hansone|HansoneXL|SmartBeetle")));
    l.add(
        new NameRegexSupportedDevice(
            "hm",
            false,
            new KeyNameMapBuilder()
                .dots8()
                .routing()
                .add("Space", R.string.key_Space)
                .add("F1", R.string.key_F1)
                .add("F2", R.string.key_F2)
                .add("F3", R.string.key_F3)
                .add("F4", R.string.key_F4)
                .add("LeftScrollUp", R.string.key_LeftScrollUp)
                .add("LeftScrollDown", R.string.key_LeftScrollDown)
                .add("RightScrollUp", R.string.key_RightScrollUp)
                .add("RightScrollDown", R.string.key_RightScrollDown)
                .build(),
            Pattern.compile("BrailleSense|BrailleEDGE")));

    // APH Refreshabraille.
    // Secure connections get prematurely closed 50% of the time
    // by the Refreshabraille.
    l.add(
        new NameRegexSupportedDevice(
            "bm",
            false,
            new KeyNameMapBuilder()
                .dots8()
                .add("Left", R.string.key_JoystickLeft)
                .add("Right", R.string.key_JoystickRight)
                .add("Up", R.string.key_JoystickUp)
                .add("Down", R.string.key_JoystickDown)
                .add("Press", R.string.key_JoystickCenter)
                .routing()
                .add("Display2", R.string.key_APH_AdvanceLeft)
                .add("Display5", R.string.key_APH_AdvanceRight)
                .add("B9", R.string.key_Space)
                .add("B10", R.string.key_Space)
                .build(),
            Pattern.compile("Refreshabraille")));

    // APH Orbit Reader.
    // Secure connections get prematurely closed 50% of the time
    // by the Orbit Reader.
    l.add(
        new NameRegexSupportedDevice(
            "bm",
            false,
            new KeyNameMapBuilder()
                .dots8()
                .add("Left", R.string.key_JoystickLeft)
                .add("Right", R.string.key_JoystickRight)
                .add("Up", R.string.key_JoystickUp)
                .add("Down", R.string.key_JoystickDown)
                .add("Press", R.string.key_JoystickCenter)
                .add("Display2", R.string.key_APH_AdvanceLeft)
                .add("Display5", R.string.key_APH_AdvanceRight)
                .add("Space", R.string.key_Space)
                .build(),
            Pattern.compile("Orbit")));

    // Baum VarioConnect
    l.add(
        new NameRegexSupportedDevice(
            "bm",
            false,
            new KeyNameMapBuilder()
                .dots8()
                .add("Left", R.string.key_JoystickLeft)
                .add("Right", R.string.key_JoystickRight)
                .add("Up", R.string.key_JoystickUp)
                .add("Down", R.string.key_JoystickDown)
                .add("Press", R.string.key_JoystickCenter)
                .routing()
                .add("Display2", R.string.key_APH_AdvanceLeft)
                .add("Display5", R.string.key_APH_AdvanceRight)
                .add("B9", R.string.key_Space)
                .add("B10", R.string.key_Space)
                .build(),
            Pattern.compile("VarioConnect")));
    // Baum VarioUltra
    l.add(
        new NameRegexSupportedDevice(
            "bm",
            false,
            new KeyNameMapBuilder()
                .dots8()
                .add("Left", R.string.key_JoystickLeft)
                .add("Right", R.string.key_JoystickRight)
                .add("Up", R.string.key_JoystickUp)
                .add("Down", R.string.key_JoystickDown)
                .add("Press", R.string.key_JoystickCenter)
                .routing()
                .add("Display2", R.string.key_APH_AdvanceLeft)
                .add("Display5", R.string.key_APH_AdvanceRight)
                .add("B9", R.string.key_Space)
                .add("B10", R.string.key_Space)
                .build(),
            Pattern.compile("VarioUltra")));

    // Older Brailliant, from Humanware group. Uses Baum
    // protocol. No Braille keyboard on this one. Secure
    // connections currently fail on Android devices with this
    // display.
    l.add(
        new NameRegexSupportedDevice(
            "bm",
            false,
            new KeyNameMapBuilder()
                .add("Display1", R.string.key_hwg_brailliant_Display1)
                .add("Display2", R.string.key_hwg_brailliant_Display2)
                .add("Display3", R.string.key_hwg_brailliant_Display3)
                .add("Display4", R.string.key_hwg_brailliant_Display4)
                .add("Display5", R.string.key_hwg_brailliant_Display5)
                .add("Display6", R.string.key_hwg_brailliant_Display6)
                .routing()
                .build(),
            Pattern.compile("HWG Brailliant"),
            Pattern.compile("NLS eReader Z")));

    // Braillex Trio
    l.add(
        new NameRegexSupportedDevice(
            "pm",
            true,
            new KeyNameMapBuilder()
                .dots8()
                .add("LeftSpace", R.string.key_Space)
                .add("RightSpace", R.string.key_Space)
                .add("Space", R.string.key_Space)
                .add("LeftThumb", R.string.key_braillex_LeftThumb)
                .add("RightThumb", R.string.key_braillex_RightThumb)
                .add("RoutingKey1", R.string.key_Routing)
                .add("BarLeft1", R.string.key_braillex_BarLeft1)
                .add("BarLeft2", R.string.key_braillex_BarLeft2)
                .add("BarRight1", R.string.key_braillex_BarRight1)
                .add("BarRight2", R.string.key_braillex_BarRight2)
                .add("BarUp1", R.string.key_braillex_BarUp1)
                .add("BarUp2", R.string.key_braillex_BarUp2)
                .add("BarDown1", R.string.key_braillex_BarDown1)
                .add("BarDown2", R.string.key_braillex_BarDown2)
                .add("LeftKeyRear", R.string.key_braillex_LeftKeyRear)
                .add("LeftKeyFront", R.string.key_braillex_LeftKeyFront)
                .add("RightKeyRear", R.string.key_braillex_RightKeyRear)
                .add("RightKeyFront", R.string.key_braillex_RightKeyFront)
                .build(),
            Pattern.compile("braillex trio")));

    // Alva BC640/BC680
    l.add(
        new NameRegexSupportedDevice(
            "al",
            false,
            new KeyNameMapBuilder()
                // No braille dot keys.
                .add("ETouchLeftRear", R.string.key_albc_ETouchLeftRear)
                .add("ETouchRightRear", R.string.key_albc_ETouchRightRear)
                .add("ETouchLeftFront", R.string.key_albc_ETouchLeftFront)
                .add("ETouchRightFront", R.string.key_albc_ETouchRightFront)
                .add("SmartpadF1", R.string.key_albc_SmartpadF1)
                .add("SmartpadF2", R.string.key_albc_SmartpadF2)
                .add("SmartpadF3", R.string.key_albc_SmartpadF3)
                .add("SmartpadF4", R.string.key_albc_SmartpadF4)
                .add("SmartpadUp", R.string.key_albc_SmartpadUp)
                .add("SmartpadDown", R.string.key_albc_SmartpadDown)
                .add("SmartpadLeft", R.string.key_albc_SmartpadLeft)
                .add("SmartpadRight", R.string.key_albc_SmartpadRight)
                .add("SmartpadEnter", R.string.key_albc_SmartpadEnter)
                .add("ThumbLeft", R.string.key_albc_ThumbLeft)
                .add("ThumbRight", R.string.key_albc_ThumbRight)
                .add("ThumbUp", R.string.key_albc_ThumbUp)
                .add("ThumbDown", R.string.key_albc_ThumbDown)
                .add("ThumbHome", R.string.key_albc_ThumbHome)
                .add("RoutingKey1", R.string.key_Routing)
                .build(),
            Pattern.compile("Alva BC", Pattern.CASE_INSENSITIVE)));

    // HandyTech displays
    l.add(
        new NameRegexSupportedDevice(
            "ht",
            true,
            new KeyNameMapBuilder()
                .add("B4", R.string.key_Dot1)
                .add("B3", R.string.key_Dot2)
                .add("B2", R.string.key_Dot3)
                .add("B1", R.string.key_Dot7)
                .add("B5", R.string.key_Dot4)
                .add("B6", R.string.key_Dot5)
                .add("B7", R.string.key_Dot6)
                .add("B8", R.string.key_Dot8)
                .routing()
                .add("LeftRockerTop", R.string.key_handytech_LeftTrippleActionTop)
                .add("LeftRockerBottom", R.string.key_handytech_LeftTrippleActionBottom)
                .add(
                    "LeftRockerTop+LeftRockerBottom",
                    R.string.key_handytech_LeftTrippleActionMiddle)
                .add("RightRockerTop", R.string.key_handytech_RightTrippleActionTop)
                .add("RightRockerBottom", R.string.key_handytech_RightTrippleActionBottom)
                .add(
                    "RightRockerTop+RightRockerBottom",
                    R.string.key_handytech_RightTrippleActionMiddle)
                .add("SpaceLeft", R.string.key_handytech_LeftSpace)
                .add("SpaceRight", R.string.key_handytech_RightSpace)
                .add("Display1", R.string.key_hwg_brailliant_Display1)
                .add("Display2", R.string.key_hwg_brailliant_Display2)
                .add("Display3", R.string.key_hwg_brailliant_Display3)
                .add("Display4", R.string.key_hwg_brailliant_Display4)
                .add("Display5", R.string.key_hwg_brailliant_Display5)
                .add("Display6", R.string.key_hwg_brailliant_Display6)
                .build(),
            Pattern.compile(
                "(Braille Wave( BRW)?|Braillino( BL2)?|Braille Star 40( BS4)?|Easy Braille("
                    + " EBR)?|Active Braille( AB4)?|Basic Braille"
                    + " BB[3,4,6]?)\\/[a-zA-Z][0-9]-[0-9]{5}|Actilino"),
            Pattern.compile("(BRW|BL2|BS4|EBR|AB4|BB(3|4|6)?)\\/[a-zA-Z][0-9]-[0-9]{5}")));

    // Seika Mini Note Taker. Secure connections fail to connect reliably.
    l.add(
        new NameRegexSupportedDevice(
            "sk",
            false,
            new KeyNameMapBuilder()
                .dots8()
                .routing()
                .dualJoysticks()
                .add("Backspace", R.string.key_Backspace)
                .add("Space", R.string.key_Space)
                .add("LeftButton", R.string.key_skntk_PanLeft)
                .add("RightButton", R.string.key_skntk_PanRight)
                .build(),
            Pattern.compile("TSM")));

    // Seika Braille Display. No Braille keys on this display.
    l.add(
        new NameRegexSupportedDevice(
            "sk",
            true,
            new KeyNameMapBuilder()
                .add("K1", R.string.key_skbdp_PanLeft)
                .add("K8", R.string.key_skbdp_PanRight)
                .add("K2", R.string.key_skbdp_LeftRockerLeft)
                .add("K3", R.string.key_skbdp_LeftRockerRight)
                .add("K4", R.string.key_skbdp_LeftLongKey)
                .add("K5", R.string.key_skbdp_RightLongKey)
                .add("K6", R.string.key_skbdp_RightRockerLeft)
                .add("K7", R.string.key_skbdp_RightRockerRight)
                .add("RoutingKey2", R.string.key_Routing)
                .routing()
                .build(),
            Pattern.compile("TS5")));

    supportedDevices = Collections.unmodifiableList(l);
  }
}

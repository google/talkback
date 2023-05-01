# Introduction

![TalkBack For Developers Logo][100]

This repository contains forked source code for Google's TalkBack, which is a screen
reader for blind and visually-impaired users of Android. For usage instructions,
read
[TalkBack User Guide](https://support.google.com/accessibility/android/answer/6283677?hl=en).

### How to Build

To build TalkBack, run `./build.sh`, which will produce an apk file. You can also specify a serial number by running `./build.sh -s [SERIAL]` to automatically install to your device.

Ensure that:
- jenv local 1.8
- NDK 24.0.8215888 is installed

### How to Install

Install the apk onto your Android device in the usual manner using adb.

### How to Run

With the apk now installed on the device, the TalkBack service should now be
present under Settings -> Accessibility, and will be off by default. To turn it
on, toggle the switch preference to the on position.

### How to use with ADB

```shell
# Activate
adb shell settings put secure enabled_accessibility_services com.android.talkback4d/com.developer.talkback.TalkBackDevService

# Deactivate
adb shell settings put secure enabled_accessibility_services null

# General format
# All commands take the format of a broadcast
adb shell am broadcast  -a com.a11y.adb.[ACTION] [OPTIONS]

BROADCAST () { adb shell am broadcast "$@"; }

# Perform actions
BROADCAST -a com.a11y.adb.previous       # default granularity
BROADCAST -a com.a11y.adb.next           # default granularity

BROADCAST -a com.a11y.adb.previous -e mode headings # move tp previous heading
BROADCAST -a com.a11y.adb.next -e mode headings     # move to next heading

# Toggle settings 
BROADCAST -a com.a11y.adb.toggle_speech_output # show special toasts for spoken text
BROADCAST -a com.a11y.adb.perform_click_action
BROADCAST -a com.a11y.adb.volume_toggle # special case that toggles between 5% and 50%
BROADCAST -a com.a11y.adb.debug_log_overlay # special case that toggles between 5% and 50%

# Custom settings
BROADCAST -a com.a11y.adb.block_out # toggle blocking out everything except the focused element
```

## All parameters
- [Action list][0]
- [Action parameter list in the SelectorController enum][1]
- [Developer settings][2]
- [Volume specific controls][3]

## TODO
- Add curtain
    - Activate via ADB
- Dev tools:
    - Colour contrast check
    - Touch target size check
    - Developer-friendly details on curtain (add to announcements)
    - [NAF control checker][4]
    - [x] Hide all screen except highlighted node
    - Show labels

## FIXED
- Menus lacking dark mode / styling
- Back button in menus
- Colour focus preference crash
- Colour focus preference not applying

[0]: https://github.com/qbalsdon/talkback/blob/main/talkback/src/main/java/com/google/android/accessibility/talkback/adb/A11yAction.java
[1]: https://github.com/qbalsdon/talkback/blob/main/talkback/src/main/java/com/google/android/accessibility/talkback/selector/SelectorController.java#L116
[2]: https://github.com/qbalsdon/talkback/blob/main/talkback/src/main/java/com/google/android/accessibility/talkback/adb/ToggleDeveloperSetting.java
[3]: https://github.com/qbalsdon/talkback/blob/main/talkback/src/main/java/com/google/android/accessibility/talkback/adb/VolumeSetting.java
[4]: https://android.googlesource.com/platform/frameworks/uiautomator/+/android-support-test/src/main/java/android/support/test/uiautomator/AccessibilityNodeInfoDumper.java#125
[100]: ./talkback/src/main/res/drawable-xxxhdpi/icon_tb4d_round.png "TalkBack for developers"

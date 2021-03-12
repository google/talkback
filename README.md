# Introduction
This repository contains source code for Google's TalkBack, which is a screen reader for blind and visually-impaired users of Android.
For usage instructions, see [TalkBack User Guide](https://support.google.com/accessibility/android/answer/6283677?hl=en).

### How to Build
To build TalkBack, you will need Android Studio (tested with version 3.5.1).

1. Create an Android Studio project from the talkback source code.
2. If you receive warnings about "Multiple dex files define INotificationSideChannel", ensure you have an updated Gradle plugin.
3. From the "Build" menu, "Make project".
4. Use the APK file from the "build/" directory.

### How to Install
Install the apk onto your Android device in the usual manner using adb.

### How to Run
With the apk now installed on the device, the TalkBack service should now be present under Settings -> Accessibility, and will be off by default. To turn it on, toggle the switch preference to the on position.

# Introduction
This repository contains sources for two Android accessibility services from Google:

* TalkBack -- a screen reader for blind and visually impaired users. For usage instructions, see [TalkBack User Guide](https://support.google.com/accessibility/android/answer/6283677?hl=en).
* Switch Access -- screen navigation tool for users with mobility limitations. For usage instructions, see [Switch Access User Guide](https://support.google.com/accessibility/android/answer/6122836?hl=en).

When you build and install TalkBack package, both services will be installed under Settings -> Accessibility and will be turned off by default. It is *not recommended* running both services at the same time as they may conflict with each other.


### Building
To build TalkBack, you will need Android Studio (tested with version 3.5.1).

1. Create an Android Studio project from the talkback source code.
2. If you receive warnings about "Multiple dex files define INotificationSideChannel", ensure you have an updated Gradle plugin.
3. From the "Build" menu, "Make project".
4. Use the APK file from the "build/" directory.



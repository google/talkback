# Introduction
This repository contains sources for two Android accessibility services:

* TalkBack -- a screen reader for blind and visually impaired users. For usage instructions, see [TalkBack User Guide](https://support.google.com/accessibility/android/answer/6283677?hl=en).
* Switch Access -- screen navigation tool for users with mobility limitations. For usage instructions, see [Switch Access User Guide](https://support.google.com/accessibility/android/answer/6122836?hl=en).

When you build and install TalkBack package, both services will be installed under Settings -> Accessibility and will be turned off by default. It is *not recommended* running both services at the same time as they may conflict with each other.


## Dependencies
To build TalkBack sources you will need:

1. Download android sdk from <https://developer.android.com/sdk/installing/index.html?pkg=tools>
2. Set ANDROID_HOME to the path of Android sdk folder
3. Open Android SDK manager and install
  - Tools/Android SDK Build-tools 22.0.1
  - Android 7.0 (API 24)
  - Extras/Android Support Repository
  - Extras/Google Repository


## Building, Installing and Testing
TalkBack uses gradle as build system.
Here are commands to build, install and test the project from command line:

1. Assemble debug and release apks: ./gradlew assemble
2. Assemble only debug apk: ./gradlew assembleDebug
3. Install debug apk on connected device: ./gradlew installDebug
4. Run robolectric tests: ./gradlew test


## Test App
This repository also includes a test app that you can use to:

1. Test TalkBack's behavior against various standard widgets and interaction patterns.
2. Explore source code for test cases to see the implementation of various TalkBack features.

To build and install the app, do the following:

1. Switch to the root directory of this repository.
2. Change to the "tests" directory.
3. To assemble debug and release apk: ./gradlew assemble
4. To build debug apk: ./gradlew assembleDebug
5. The apks will be located in the app/build/outputs/apk directory.

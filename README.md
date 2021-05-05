# Introduction

This repository contains source code for Google's TalkBack, which is a screen
reader for blind and visually-impaired users of Android. For usage instructions,
see
[TalkBack User Guide](https://support.google.com/accessibility/android/answer/6283677?hl=en).

### How to Build

To build TalkBack, run ./build.sh, which will produce an apk file.

### How to Install

Install the apk onto your Android device in the usual manner using adb.

### How to Run

With the apk now installed on the device, the TalkBack service should now be
present under Settings -> Accessibility, and will be off by default. To turn it
on, toggle the switch preference to the on position.

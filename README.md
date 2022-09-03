# Introduction

This repository contains **forked** source code for Google's TalkBack, which is a screen
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

### Personal Notes

```jenv local 1.8``` <-- Need to do this if build fails
```./build.sh && adb install ./build/outputs/apk/phone/debug/talkback-phone-debug.apk```

My computer is using jenv to manage the JAVA_HOME etc so be ready with that

Java 11 is not a problem, just use it everywhere. Running `java -version` was insightful along with `jenv versions`

'com.android.talkback/com.google.android.marvin.talkback.TalkBackService', 'com.google.android.apps.accessibility.voiceaccess/com.google.android.apps.accessibility.voiceaccess.JustSpeakService:com.android.talkback/com.google.android.marvin.talkback.TalkBackService'

## TODO
- Hide screen via ADB? (Customise reading controls)
- Organise gestures by action
- Add curtain
- Dev tools: Colour contrast check
- Dev tools: Touch target size check
- Dev tools: Developer-friendly details on curtain (add to announcements)

## FIXED
- Menus lacking dark mode / styling
- Back button in menus?


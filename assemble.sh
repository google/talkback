#!/bin/bash

function exitOnFailure() {
    rc=$?
    if [[ $rc != 0 ]] ; then
    exit 1
    fi
}

if [ $1 == "assembleDebugTest" ]
then
    ./vendor/unbundled_google/packages/TalkBack/gradlew clean :robolectricTargetZip:assembleTargetZip :robolectricTest:assembleTestJar :robolectricTestSuite:assembleSuiteJar -PoutDir=$OUT_DIR/build
    exitOnFailure
    cp -r $OUT_DIR/build/robolectric/* $DIST_DIR
    exitOnFailure
    ./vendor/unbundled_google/packages/TalkBack/gradlew clean assembleGoogleDebug assembleGoogleDebugAndroidTest -PoutDir=$OUT_DIR/build
elif [ $1 == "assembleDebug" ]
then
    ./vendor/unbundled_google/packages/TalkBack/gradlew clean assembleGoogleDebug assembleGoogleRelease -PoutDir=$OUT_DIR/build
elif [ $1 == "assembleRelease" ]
then
    ./vendor/unbundled_google/packages/TalkBack/gradlew clean assembleGoogleRelease -PoutDir=$OUT_DIR/build
else
    ./vendor/unbundled_google/packages/TalkBack/gradlew clean $1 -PoutDir=$OUT_DIR/build
fi

exitOnFailure
cp -r $OUT_DIR/build/outputs/apk/* $DIST_DIR
exitOnFailure

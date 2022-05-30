### Bash script for building Talkback-for-Partners Android apk
###
### The following environment variables must be set before executing this script
###   ANDROID_SDK           # path to local copy of Android SDK
###   ANDROID_NDK           # path to local copy of Android NDK
###   JAVA_HOME             # path to local copy of Java SDK. Should be Java 8.
# On gLinux, use 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64'


GRADLE_DOWNLOAD_VERSION=5.4.1
GRADLE_TRACE=false   # change to true to enable verbose logging of gradlew


function log {
  if [[ -n $1 ]]; then
    echo "##### ${1}"
  else echo
  fi
}

function fail_with_message  {
  echo
  echo "Error: ${1}"
  exit 1
}


log "pwd: $(pwd)"


if [[ -z "${ANDROID_SDK}" ]]; then
  fail_with_message "ANDROID_SDK environment variable is unset"
fi
log "\${ANDROID_SDK}: ${ANDROID_SDK}"
log "ls ${ANDROID_SDK}"; ls "${ANDROID_SDK}"
if [[ -z "${ANDROID_NDK}" ]]; then
  fail_with_message "ANDROID_NDK environment variable is unset"
fi
log "\${ANDROID_NDK}: ${ANDROID_NDK}"
log "ls \${ANDROID_NDK}:"; ls "${ANDROID_NDK}"
log


log "Write local.properties file"
echo "sdk.dir=${ANDROID_SDK}" > local.properties
echo "ndk.dir=${ANDROID_NDK}" >> local.properties
log "cat local.properties"; cat local.properties
log


if [[ -z "${JAVA_HOME}" ]]; then
  fail_with_message "JAVA_HOME environment variable is unset. It should be set to a Java 8 SDK (in order for the license acceptance to work)"
fi
log "\${JAVA_HOME}: ${JAVA_HOME}"
log "ls \${JAVA_HOME}:"; ls "${JAVA_HOME}"
log "java -version:"; java -version
log "javac -version:"; javac -version
log


log "Accept SDK licenses"
log "${ANDROID_SDK}"/tools/bin/sdkmanager --licenses; yes | "${ANDROID_SDK}"/tools/bin/sdkmanager --licenses
ACCEPT_SDK_LICENSES_EXIT_CODE=$?
log
if [[ $ACCEPT_SDK_LICENSES_EXIT_CODE -ne 0 ]]; then
  fail_with_message "Build Error: SDK license acceptance failed. This can happen if your JAVA_HOME is not set to Java 8"
fi


# Having compileSdkVersion=31 leads to javac error "unrecognized Attribute name MODULE (class com.sun.tools.javac.util.UnsharedNameTable$NameImpl)"; switching to Java 11 fixes this problem.
sudo update-java-alternatives --set java-1.11.0-openjdk-amd64
export JAVA_HOME=/usr/lib/jvm/java-1.11.0-openjdk-amd64
log "\${JAVA_HOME}: ${JAVA_HOME}"
log "ls \${JAVA_HOME}:"; ls "${JAVA_HOME}"
log "java -version:"; java -version
log "javac -version:"; javac -version
log


GRADLE_ZIP_REMOTE_FILE=gradle-${GRADLE_DOWNLOAD_VERSION}-bin.zip
GRADLE_ZIP_DEST_PATH=~/Desktop/${GRADLE_DOWNLOAD_VERSION}.zip
log "Download gradle binary from the web ${GRADLE_ZIP_REMOTE_FILE} to ${GRADLE_ZIP_DEST_PATH} using wget"
wget -O ${GRADLE_ZIP_DEST_PATH} https://services.gradle.org/distributions/${GRADLE_ZIP_REMOTE_FILE}
log


GRADLE_UNZIP_HOSTING_FOLDER=/opt/gradle-${GRADLE_DOWNLOAD_VERSION}
log "Unzip gradle zipfile ${GRADLE_ZIP_DEST_PATH} to ${GRADLE_UNZIP_HOSTING_FOLDER}"
sudo unzip -n -d ${GRADLE_UNZIP_HOSTING_FOLDER} ${GRADLE_ZIP_DEST_PATH}
log


GRADLE_BINARY=${GRADLE_UNZIP_HOSTING_FOLDER}/gradle-${GRADLE_DOWNLOAD_VERSION}/bin/gradle
log "\${GRADLE_BINARY} = ${GRADLE_BINARY}"
log "\${GRADLE_BINARY} -version"
${GRADLE_BINARY} -version
log "Obtain gradle/wrapper/ with gradle wrapper --gradle-version ${GRADLE_DOWNLOAD_VERSION}"
${GRADLE_BINARY} wrapper --gradle-version ${GRADLE_DOWNLOAD_VERSION}
log


log "find gradle"
find gradle
log "gradlew --version"
./gradlew --version
log


GRADLEW_DEBUG=
GRADLEW_STACKTRACE=
if [[ "$GRADLE_TRACE" = true ]]; then
  GRADLEW_DEBUG=--debug
  GRADLEW_STACKTRACE=--stacktrace
fi
log "./gradlew assembleDebug"
chmod 777 gradlew
./gradlew ${GRADLEW_DEBUG} ${GRADLEW_STACKTRACE} assemble
BUILD_EXIT_CODE=$?
log

if [[ $BUILD_EXIT_CODE -eq 0 ]]; then
  log "find . -name *.apk"
  find . -name "*.apk"
  log
fi


exit $BUILD_EXIT_CODE   ### This should be the last line in this file

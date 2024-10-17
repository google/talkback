### Bash script for building Talkback-for-Partners Android apk
###
### The following environment variables must be set before executing this script
###   ANDROID_SDK           # path to local copy of Android SDK
###   JAVA_HOME             # path to local copy of Java SDK.

### Environment variables:
#   ANDROID_SDK=~/Android/Sdk
#   ANDROID_NDK=~/Android/Sdk/ndk
# The latest JDK,
#   JAVA_HOME=/usr/local/buildtools/java/jdk
# JDK 17, work for current build,
#   JAVA_HOME=/google/data/ro/projects/java-platform/linux-amd64/jdk-17-latest/bin

# For help in getting the correct version numbers of gradle, the gradle plugin,
# and Java, see the following:
# https://developer.android.com/build/releases/gradle-plugin#updating-gradle
# https://docs.gradle.org/current/userguide/compatibility.html
GRADLE_DOWNLOAD_VERSION=7.6.4
GRADLE_TRACE=false   # change to true to enable verbose logging of gradle


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

function require_environment_variable() {
  if [[ -z ${!1+set} ]]; then
    fail_with_message "the environment variable $1 is not set"
  else
    log "${1}: ${!1}"
  fi
}

function require_folder_exists() {
  if [[ ! -d "${1}" ]]; then
    fail_with_message "the folder at ${1} does not exist"
  else
    log "ls ${1}"; ls "${1}"
  fi
}


log "pwd: $(pwd)"
log "ls"; ls
log


require_environment_variable ANDROID_SDK
require_folder_exists "${ANDROID_SDK}"
require_environment_variable JAVA_HOME
require_folder_exists "${JAVA_HOME}"
log


log "Write local.properties file"
echo "sdk.dir=${ANDROID_SDK}" > local.properties
log "cat local.properties"; cat local.properties
log


# Download the gradle binary at the version set atop this file
GRADLE_ZIP_REMOTE_FILE=gradle-${GRADLE_DOWNLOAD_VERSION}-bin.zip
GRADLE_ZIP_DEST_PATH=~/${GRADLE_DOWNLOAD_VERSION}.zip
log "Download gradle binary from the web ${GRADLE_ZIP_REMOTE_FILE} to ${GRADLE_ZIP_DEST_PATH} using wget"
time wget -O ${GRADLE_ZIP_DEST_PATH} https://services.gradle.org/distributions/${GRADLE_ZIP_REMOTE_FILE}
log


# Unzip the gradle binary
GRADLE_UNZIP_HOSTING_FOLDER=/opt/gradle-${GRADLE_DOWNLOAD_VERSION}
log "Unzip gradle zipfile ${GRADLE_ZIP_DEST_PATH} to ${GRADLE_UNZIP_HOSTING_FOLDER}"
sudo unzip -n -d ${GRADLE_UNZIP_HOSTING_FOLDER} ${GRADLE_ZIP_DEST_PATH}
GRADLE_BINARY=${GRADLE_UNZIP_HOSTING_FOLDER}/gradle-${GRADLE_DOWNLOAD_VERSION}/bin/gradle
log "\${GRADLE_BINARY} = ${GRADLE_BINARY}"
log "\${GRADLE_BINARY} -version"
${GRADLE_BINARY} -version
log


if [[ "$GRADLE_TRACE" = true ]]; then
  log "${GRADLE_BINARY} dependencies"
  ${GRADLE_BINARY} dependencies
  log
fi


GRADLE_DEBUG=
GRADLE_STACKTRACE=
if [[ "$GRADLE_TRACE" = true ]]; then
  GRADLE_DEBUG=--debug
  GRADLE_STACKTRACE=--stacktrace
fi
log "${GRADLE_BINARY} assembleDebug"
${GRADLE_BINARY} ${GRADLE_DEBUG} ${GRADLE_STACKTRACE} assembleDebug
BUILD_EXIT_CODE=$?
log


if [[ $BUILD_EXIT_CODE -eq 0 ]]; then
  log "find . -name *.apk"
  find . -name "*.apk"
  log
fi


exit $BUILD_EXIT_CODE   ### This should be the last line in this file

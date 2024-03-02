### Bash script for building Talkback-for-Partners Android apk
###
### The following environment variables must be set before executing this script
###   ANDROID_SDK           # path to local copy of Android SDK
###   ANDROID_NDK           # path to local copy of Android NDK
###   JAVA_HOME             # path to local copy of Java SDK.

#-----------------------------------------------------------------------------
DEVICE=""
PIPELINE=false
USAGE="./build.sh [-s | --device] SERIAL_NUMBER]"
while [[ "$#" -gt 0 ]]; do
    case $1 in
        -s|--device) DEVICE="$2"; shift ;;
        -p) PIPELINE=true; shift ;;
        -h|--help) echo $USAGE; exit 0 ;;
        *) echo "Unknown parameter passed: $1"; exit 1 ;;
    esac
    shift
done
#-----------------------------------------------------------------------------

GRADLE_DOWNLOAD_VERSION=7.3.3
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

function require_environment_variable() {
  if [[ -z ${!1+set} ]]; then
    fail_with_message "the environment variable $1 is not set"
    exit 1
  else
    log "${1}: ${!1}"
  fi
}

function require_folder_exists() {
  if [[ ! -d "${1}" ]]; then
    fail_with_message "the folder at ${1} does not exist"
    exit 1
  else
    log "ls ${1}"; ls "${1}"
  fi
}

log "pwd: $(pwd)"
log "ls"; ls
log

#-----------------------------------------------------------------------------
TEMP_DIR=~/tmp
if [[ "$PIPELINE" = false ]]; then
  log "!! CURRENT JAVA_HOME: [$JAVA_HOME] !!"
  unset JAVA_HOME;
  export JAVA_HOME=$(/usr/libexec/java_home -v"11.0.21");
  log "!!     NEW JAVA_HOME: [$JAVA_HOME] !!"
else
  TEMP_DIR=$(mktemp -d)
  mkdir $TEMP_DIR/opt
fi
#-----------------------------------------------------------------------------

require_environment_variable ANDROID_SDK
require_folder_exists "${ANDROID_SDK}"
require_environment_variable ANDROID_NDK
require_folder_exists "${ANDROID_NDK}"
require_environment_variable JAVA_HOME
require_folder_exists "${JAVA_HOME}"
log


log "Write local.properties file"
echo "sdk.dir=${ANDROID_SDK}" > local.properties
echo "ndk.dir=${ANDROID_NDK}" >> local.properties
log "cat local.properties"; cat local.properties
log

log "\${JAVA_HOME}: ${JAVA_HOME}"
log "ls \${JAVA_HOME}:"; ls "${JAVA_HOME}"
log "java -version:"; java -version
log "javac -version:"; javac -version
log

GRADLE_ZIP_REMOTE_FILE=gradle-${GRADLE_DOWNLOAD_VERSION}-bin.zip
GRADLE_ZIP_DEST_PATH=${TEMP_DIR}/${GRADLE_DOWNLOAD_VERSION}.zip
GRADLE_UNZIP_HOSTING_FOLDER=${TEMP_DIR}/opt/gradle-${GRADLE_DOWNLOAD_VERSION}

if [[ ! -f "$GRADLE_ZIP_DEST_PATH" ]]; then
  log "--> Downloading GRADLE"
  log "Download gradle binary from the web ${GRADLE_ZIP_REMOTE_FILE} to ${GRADLE_ZIP_DEST_PATH} using curl"
  sudo curl -L -o ${GRADLE_ZIP_DEST_PATH} https://services.gradle.org/distributions/${GRADLE_ZIP_REMOTE_FILE}
else
  log "--> Using downloaded file: [$GRADLE_ZIP_DEST_PATH]"
fi


log "Unzip gradle zipfile ${GRADLE_ZIP_DEST_PATH} to ${GRADLE_UNZIP_HOSTING_FOLDER}"
unzip -n -d ${GRADLE_UNZIP_HOSTING_FOLDER} ${GRADLE_ZIP_DEST_PATH}
if [ $? -eq 0 ]; then
    log "unzip succeeded"
else
    log "!! UNZIP FAILED !!"
    log "unzip -n -d ${GRADLE_UNZIP_HOSTING_FOLDER} ${GRADLE_ZIP_DEST_PATH}"
    log
    exit 1
fi
log


GRADLE_BINARY=${GRADLE_UNZIP_HOSTING_FOLDER}/gradle-${GRADLE_DOWNLOAD_VERSION}/bin/gradle
log "\${GRADLE_BINARY} = ${GRADLE_BINARY}"
log "${GRADLE_BINARY} -version"
${GRADLE_BINARY} -version
log "Obtain gradle wrapper"
log "$GRADLE_BINARY wrapper --gradle-version ${GRADLE_DOWNLOAD_VERSION}"
${GRADLE_BINARY} wrapper --gradle-version ${GRADLE_DOWNLOAD_VERSION}
log


if [[ "$GRADLE_TRACE" = true ]]; then
  log "./gradlew dependencies"
  ${GRADLE_BINARY} dependencies
  log
fi


GRADLEW_DEBUG=
GRADLEW_STACKTRACE=
if [[ "$GRADLE_TRACE" = true ]]; then
  GRADLEW_DEBUG=--debug
  GRADLEW_STACKTRACE=--stacktrace
fi
log "./${GRADLE_BINARY} assemble"
${GRADLE_BINARY} ${GRADLEW_DEBUG} ${GRADLEW_STACKTRACE} assemble
BUILD_EXIT_CODE=$?
log

if [[ $BUILD_EXIT_CODE -eq 0 ]]; then
  log "find . -name *.apk"
  find . -name "*.apk"
  log

  if [[ ! -z $DEVICE ]]; then
    log "precaution: uninstalling from $DEVICE"
    adb -s "$DEVICE" uninstall com.android.talkback4d
    log "installing on $DEVICE"
    adb -s "$DEVICE" install ./build/outputs/apk/phone/debug/talkback-phone-debug.apk
  fi
fi

exit $BUILD_EXIT_CODE   ### This should be the last line in this file

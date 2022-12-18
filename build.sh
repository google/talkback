### Bash script for building Talkback-for-Partners Android apk
###
### The following environment variables must be set before executing this script
###   ANDROID_SDK           # path to local copy of Android SDK
###   ANDROID_NDK           # path to local copy of Android NDK
###   JAVA_HOME             # path to local copy of Java SDK. Should be Java 8.
# On gLinux, use 'export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64'

#-----------------------------------------------------------------------------
DEVICE=""
PIPELINE=false
USAGE="./build.sh [[-s | --device] SERIAL_NUMBER]"
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

GRADLE_DOWNLOAD_VERSION=6.7.1
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

function print_sdk_info {
  log "\${ANDROID_SDK}: ${ANDROID_SDK}"
  log "ls ${ANDROID_SDK}"; ls "${ANDROID_SDK}"
  if [[ -d "${ANDROID_SDK}/platforms" ]]; then
    log "\${ANDROID_SDK}/platforms: ${ANDROID_SDK}/platforms"
    log "ls ${ANDROID_SDK}/platforms"; ls "${ANDROID_SDK}/platforms"
  fi
  if [[ -d "${ANDROID_SDK}/build-tools" ]]; then
    log "\${ANDROID_SDK}/build-tools: ${ANDROID_SDK}/build-tools"
    log "ls ${ANDROID_SDK}/build-tools"; ls "${ANDROID_SDK}/build-tools"
  fi
  log "\${ANDROID_NDK}: ${ANDROID_NDK}"
  log "ls \${ANDROID_NDK}:"; ls "${ANDROID_NDK}"
}


log "pwd: $(pwd)"


if [[ -z "${ANDROID_SDK}" ]]; then
  fail_with_message "ANDROID_SDK environment variable is unset"
fi
if [[ -z "${ANDROID_NDK}" ]]; then
  fail_with_message "ANDROID_NDK environment variable is unset"
fi
print_sdk_info
log


log "Write local.properties file"
echo "sdk.dir=${ANDROID_SDK}" > local.properties
echo "ndk.dir=${ANDROID_NDK}" >> local.properties
log "cat local.properties"; cat local.properties
log


#-----------------------------------------------------------------------------
if [[ "$PIPELINE" = false ]]; then
  unset JAVA_HOME;
  export JAVA_HOME=$(/usr/libexec/java_home -v"1.8");
fi
#-----------------------------------------------------------------------------


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


if [[ "$PIPELINE" = false ]]; then
  unset JAVA_HOME;
  export JAVA_HOME=$(/usr/libexec/java_home -v"11");
fi
log "\${JAVA_HOME}: ${JAVA_HOME}"
log "ls \${JAVA_HOME}:"; ls "${JAVA_HOME}"
log "java -version:"; java -version
log "javac -version:"; javac -version
log


GRADLE_ZIP_REMOTE_FILE=gradle-${GRADLE_DOWNLOAD_VERSION}-bin.zip
GRADLE_ZIP_DEST_PATH=~/Desktop/${GRADLE_DOWNLOAD_VERSION}.zip
GRADLE_UNZIP_HOSTING_FOLDER=/opt/gradle-${GRADLE_DOWNLOAD_VERSION}


if [[ ! -f "$GRADLE_ZIP_DEST_PATH" ]]; then
  log "--> Downloading GRADLE"
  if [[ "$PIPELINE" = true ]]; then
    mkdir ~/tmp
    mkdir ~/tmp/opt
    GRADLE_ZIP_DEST_PATH=~/tmp/${GRADLE_DOWNLOAD_VERSION}.zip
    GRADLE_UNZIP_HOSTING_FOLDER=~/tmp/opt/gradle-${GRADLE_DOWNLOAD_VERSION}
    log "Download gradle binary from the web ${GRADLE_ZIP_REMOTE_FILE} to ${GRADLE_ZIP_DEST_PATH} using wget"
    wget -O ${GRADLE_ZIP_DEST_PATH} https://services.gradle.org/distributions/${GRADLE_ZIP_REMOTE_FILE}
    log
  else
    log "Download gradle binary from the web ${GRADLE_ZIP_REMOTE_FILE} to ${GRADLE_ZIP_DEST_PATH} using curl"
    COMMAND="curl -L -o ${GRADLE_ZIP_DEST_PATH} https://services.gradle.org/distributions/${GRADLE_ZIP_REMOTE_FILE}"
    sudo curl -L -o ${GRADLE_ZIP_DEST_PATH} https://services.gradle.org/distributions/${GRADLE_ZIP_REMOTE_FILE}
  fi
fi


log "Unzip gradle zipfile ${GRADLE_ZIP_DEST_PATH} to ${GRADLE_UNZIP_HOSTING_FOLDER}"
unzip -n -d ${GRADLE_UNZIP_HOSTING_FOLDER} ${GRADLE_ZIP_DEST_PATH}
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
chmod 777 gradlew
log "./gradlew --version"
./gradlew --version
log


if [[ "$GRADLE_TRACE" = true ]]; then
  log "./gradlew dependencies"
  ./gradlew dependencies
  log
fi


GRADLEW_DEBUG=
GRADLEW_STACKTRACE=
if [[ "$GRADLE_TRACE" = true ]]; then
  GRADLEW_DEBUG=--debug
  GRADLEW_STACKTRACE=--stacktrace
fi
log "./gradlew assembleDebug"
./gradlew ${GRADLEW_DEBUG} ${GRADLEW_STACKTRACE} assemble
BUILD_EXIT_CODE=$?
log

if [[ $BUILD_EXIT_CODE -eq 0 ]]; then
  if [[ ! -z $DEVICE ]]; then
    log "installing on $DEVICE"
    adb -s $DEVICE install ./build/outputs/apk/phone/debug/talkback-phone-debug.apk
  fi
  print_sdk_info
  log

  log "find . -name *.apk"
  find . -name "*.apk"
  log
fi

exit $BUILD_EXIT_CODE   ### This should be the last line in this file

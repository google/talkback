#!/usr/bin/env bash
### Bash script for building Talkback-for-Partners Android apk
WORK_DIR="$(dirname -- "${BASH_SOURCE[0]}")"
GRADLE_TRACE=false   # change to true to enable verbose logging of gradle
GRADLE_WRAPPER="$WORK_DIR/gradlew"

function log {
  if [[ -n $1 ]]; then
    echo "##### ${1}"
  else echo
  fi
}

log "pwd: $(pwd)"
log "ls"; ls
log

if [[ "$GRADLE_TRACE" = true ]]; then
  log "${GRADLE_WRAPPER} dependencies"
  ${GRADLE_WRAPPER} dependencies
  log
fi


GRADLE_DEBUG=
GRADLE_STACKTRACE=
if [[ "$GRADLE_TRACE" = true ]]; then
  GRADLE_DEBUG=--debug
  GRADLE_STACKTRACE=--stacktrace
fi
log "${GRADLE_WRAPPER} assembleDebug"
${GRADLE_WRAPPER} ${GRADLE_DEBUG} ${GRADLE_STACKTRACE} assembleDebug
BUILD_EXIT_CODE=$?
log


if [[ $BUILD_EXIT_CODE -eq 0 ]]; then
  log "find $WORK_DIR -name *.apk"
  find $WORK_DIR -name "*.apk"
  log
fi


exit $BUILD_EXIT_CODE   ### This should be the last line in this file

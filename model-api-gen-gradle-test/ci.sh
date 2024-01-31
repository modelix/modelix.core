#!/bin/sh

set -e
(
  TEST_DIR="$(dirname "$(readlink -f "$0")")"
  cd "$TEST_DIR/../"
  if [ "${CI}" != "true" ]; then
    trap cleanup INT TERM EXIT
    cleanup () {
      kill "${MODEL_SERVER_PID}"
      exit
    }
  fi
  ./gradlew :model-server:run --console=plain --args="-inmemory -port 28102" &
  MODEL_SERVER_PID=$!

  curl -X GET --retry 30 --retry-connrefused --retry-delay 1 http://localhost:28102/health
  cd "$TEST_DIR"
  ./gradlew build --console=plain --stacktrace
)

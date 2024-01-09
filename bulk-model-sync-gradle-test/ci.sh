#!/bin/sh

set -e

cd "$(dirname "$0")"

(
  cd graph-lang-api
  ./gradlew publishToMavenLocal --console=plain
)

./gradlew assemble --console=plain

if [ "${CI}" != "true" ]; then
  trap cleanup INT TERM EXIT
  cleanup () {
    kill "${MODEL_SERVER_PID}"
    exit
  }
fi

./gradlew runModelServer --console=plain &
MODEL_SERVER_PID=$!

curl -X GET --retry 30 --retry-connrefused --retry-delay 1 http://localhost:28309/health

./gradlew runSyncTestPush --console=plain --stacktrace
./gradlew test --tests 'PushTest'
./gradlew runSyncTestPull --console=plain --stacktrace
./gradlew test --tests 'PullTest'

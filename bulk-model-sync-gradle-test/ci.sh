#!/bin/sh

set -e
set -x

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

cd ..
./gradlew :model-server:run --console=plain --args="-inmemory -port 28309" &
MODEL_SERVER_PID=$!

cd "$(dirname "$0")"

curl -X GET --retry 30 --retry-connrefused --retry-delay 1 http://localhost:28309/health

./gradlew runSyncTestPush --console=plain --stacktrace
./gradlew test --tests 'PushTest'
./gradlew runSyncTestPull --console=plain --stacktrace
./gradlew test --tests 'PullTest'

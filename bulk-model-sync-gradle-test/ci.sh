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
sleep 5

#CI needs more time
if [ "${CI}" = "true" ]; then
  sleep 20
fi

curl -X POST http://127.0.0.1:28309/v2/repositories/ci-test/init

./gradlew runSyncTestPush --console=plain --stacktrace
./gradlew test --tests 'PushTest'
./gradlew runSyncTestPull --console=plain --stacktrace
./gradlew test --tests 'PullTest'

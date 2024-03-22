#!/bin/sh

set -e
set -x

cd "$(dirname "$(readlink -f "$0")")"

./gradlew assemble --console=plain

if [ "${CI}" != "true" ]; then
  trap cleanup INT TERM EXIT
  cleanup () {
    kill "${MODEL_SERVER_PID}"
    exit
  }
fi

./gradlew :modelix.core:model-server:run --console=plain --args="-inmemory -port 28309" &
MODEL_SERVER_PID=$!

curl -X GET --retry 30 --retry-connrefused --retry-delay 1 http://localhost:28309/health

./gradlew runSyncTestPush --console=plain --stacktrace
./gradlew test --tests 'PushTest'
./gradlew test --tests 'ChangeApplier'
./gradlew runSyncTestPull --console=plain --stacktrace
./gradlew test --tests 'PullTest'

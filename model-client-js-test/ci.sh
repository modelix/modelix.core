#!/bin/sh

set -e
(
  TEST_DIR="$(dirname "$(readlink -f "$0")")"
  cd "$TEST_DIR/../"
  ./gradlew :model-server:jibDockerBuild

  cd "$TEST_DIR"
  ./gradlew build --console=plain --stacktrace
)

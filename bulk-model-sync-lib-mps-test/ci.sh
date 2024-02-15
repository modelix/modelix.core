#!/bin/sh

set -e
(
  cd "$(dirname "$0")"
  ./gradlew test --console=plain --stacktrace
)

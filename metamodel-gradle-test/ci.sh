#!/bin/sh

set -e
cd "$(dirname "$0")"

(
  cd ..
  ./modelix-version.sh # ensure the version.txt exists
  ./gradlew publishToMavenLocal
)

./gradlew build

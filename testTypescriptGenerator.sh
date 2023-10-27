#!/bin/sh

set -e

./gradlew :model-api-gen-runtime:packJsPackage \
          :model-api-gen:publishToMavenLocal \
          :model-api-gen-runtime:publishToMavenLocal \
          :model-api-gen-gradle:publishToMavenLocal \
          :model-api:publishToMavenLocal \
          :model-client:publishToMavenLocal

(
  cd model-api-gen-gradle-test
  rm -rf typescript-generation/node_modules/@modelix
  rm -f typescript-generation/package-lock.json
  ./gradlew :typescript-generation:npm_run_build
)

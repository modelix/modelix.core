#!/bin/sh

set -e

TAG=$( ./modelix-version.sh )
MODELIX_TARGET_PLATFORM="${MODELIX_TARGET_PLATFORM:=linux/amd64}"

(
  cd model-server
  if [[ -z "${MODELIX_CI}" ]]; then
    docker build --platform ${MODELIX_TARGET_PLATFORM} --no-cache \
    -t modelix/modelix-model:latest -t "modelix/modelix-model:${TAG}" .
  else
    docker buildx build --platform linux/amd64,linux/arm64 --push --no-cache \
    -t modelix/modelix-model:latest -t "modelix/modelix-model:${TAG}" .
  fi
)

if [ -f "../modelix/helm" ]; then
  sed -i.bak -E "s/  model: \".*\"/  model: \"${TAG}\"/" ../modelix/helm/dev.yaml
  rm ../modelix/helm/dev.yaml.bak
fi

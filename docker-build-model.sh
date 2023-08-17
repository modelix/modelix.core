#!/bin/sh

set -e

TAG=$( ./modelix-version.sh )

(
  cd model-server
  if [ "${CI}" = "true" ]; then
    docker buildx build --platform linux/amd64,linux/arm64 --push \
    -t modelix/model-server:latest -t "modelix/model-server:${TAG}" \
    -t modelix/modelix-model:latest -t "modelix/modelix-model:${TAG}" .
  else
    docker build \
    -t modelix/model-server:latest -t "modelix/model-server:${TAG}" \
    -t modelix/modelix-model:latest -t "modelix/modelix-model:${TAG}" .
  fi
)

if [ -f "../modelix/helm" ]; then
  sed -i.bak -E "s/  model: \".*\"/  model: \"${TAG}\"/" ../modelix/helm/dev.yaml
  rm ../modelix/helm/dev.yaml.bak
fi

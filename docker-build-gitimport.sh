#!/bin/sh

set -e

TAG=$( ./modelix-version.sh )

(
  cd mps-git-import-cli
  if [ "${CI}" = "true" ]; then
    docker buildx build --platform linux/amd64,linux/arm64 --push \
    -t modelix/mps-git-import:latest -t "modelix/mps-git-import:${TAG}" .
  else
    docker build \
    -t modelix/mps-git-import:latest -t "modelix/mps-git-import:${TAG}" .
  fi
)

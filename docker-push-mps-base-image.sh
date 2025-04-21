#!/bin/sh

set -e

cd mps-base-image

MPS_MAJOR_VERSION=`echo "$MPS_VERSION" | grep -oE '20[0-9]{2}\.[0-9]+'`
docker buildx build --platform linux/amd64,linux/arm64 --push \
  --build-arg MPS_VERSION="${MPS_VERSION}" \
  -t "modelix/mps-base-image:${MPS_VERSION}" \
  -t "modelix/mps-base-image:${MPS_MAJOR_VERSION}" \
  .

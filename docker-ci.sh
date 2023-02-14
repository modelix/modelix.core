#!/bin/bash

set -e

docker login -u "$DOCKER_HUB_USER" -p "$DOCKER_HUB_KEY"

./docker-build-model.sh

#MODELIX_VERSION=$( ./modelix-version.sh )
#TAGS="$MODELIX_VERSION" # list separated by space
#IMAGE_NAMES="model" # list separated by space
#for TAG in $TAGS ; do
#  echo "Pushing Tag $TAG"
#
#  for IMAGE_NAME in $IMAGE_NAMES ; do
#    if [ "$TAG" != "latest" ]; then
#      docker tag "modelix/modelix-$IMAGE_NAME:latest" "modelix/modelix-$IMAGE_NAME:${TAG}"
#    fi
#    docker push "modelix/modelix-$IMAGE_NAME:${TAG}"
#  done
#done

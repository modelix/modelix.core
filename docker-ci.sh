#!/bin/bash

set -e

echo "$DOCKER_HUB_KEY" | docker login -u "$DOCKER_HUB_USER" --password-stdin

./docker-build-model.sh
./docker-build-gitimport.sh

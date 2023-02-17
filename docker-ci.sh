#!/bin/bash

set -e

docker login -u "$DOCKER_HUB_USER" -p "$DOCKER_HUB_KEY"

./docker-build-model.sh

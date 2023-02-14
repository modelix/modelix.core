#!/bin/sh

set -e
cd "$(dirname "$0")"

FILE=./version.txt
if [ -f "$FILE" ]; then
    MODELIX_VERSION="`cat $FILE`"
else
    MODELIX_VERSION="$(date +"%Y%m%d%H%M")-SNAPSHOT"
    echo "$MODELIX_VERSION" > $FILE
fi

echo "${MODELIX_VERSION}-ci-test"

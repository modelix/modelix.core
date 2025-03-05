#!/bin/sh

jvmArgs="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5071 -XX:MaxRAMPercentage=75"

if [ -n "$jdbc_url" ]; then
  jvmArgs="$jvmArgs -Djdbc.url=$jdbc_url"
fi

export MODEL_SERVER_OPTS="$jvmArgs"
/model-server/bin/model-server "$@"

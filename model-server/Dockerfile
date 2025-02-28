FROM registry.access.redhat.com/ubi8/openjdk-11:1.21-1.1736337912
USER root
WORKDIR /model-server/
EXPOSE 28101
HEALTHCHECK CMD curl --fail http://localhost:28101/health || exit 1

COPY build/install/model-server/ /model-server/

ENV MODEL_SERVER_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5071 -XX:MaxRAMPercentage=75 -Djdbc.url=$jdbc_url"
ENTRYPOINT ["/model-server/bin/model-server"]

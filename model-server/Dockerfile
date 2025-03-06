FROM registry.access.redhat.com/ubi8/openjdk-11:1.21-1.1736337912
USER root
WORKDIR /model-server/
EXPOSE 28101
HEALTHCHECK CMD curl --fail http://localhost:28101/health || exit 1

COPY run-model-server.sh /model-server/run-model-server.sh
COPY build/install/model-server/ /model-server/

ENTRYPOINT ["/model-server/run-model-server.sh"]

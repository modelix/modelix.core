FROM registry.access.redhat.com/ubi8/openjdk-11:1.20-2.1731461725
WORKDIR /usr/modelix-model
EXPOSE 28101
HEALTHCHECK CMD curl --fail http://localhost:28101/health || exit 1
COPY run-model-server.sh /usr/modelix-model/
COPY build/dependency-libs/ /usr/modelix-model/model-server/build/libs/
COPY build/libs/ /usr/modelix-model/model-server/build/libs/
ENTRYPOINT ["./run-model-server.sh"]

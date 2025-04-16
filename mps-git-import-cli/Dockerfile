FROM eclipse-temurin:21
ARG MPS_VERSION="2024.1.3"

RUN apt update && apt install wget ant -y

# download MPS and extract into /mps
RUN set -e \
    && mkdir /mpstmp \
    && cd /mpstmp \
    && MPS_MAJOR_VERSION=`echo "$MPS_VERSION" | grep -oE '20[0-9]{2}\.[0-9]+'` \
    && wget "https://download.jetbrains.com/mps/${MPS_MAJOR_VERSION}/MPS-${MPS_VERSION}.tar.gz" \
    && tar -xf $(ls | head -n 1) \
    && mv "MPS $MPS_MAJOR_VERSION" "/mps" \
    && cd .. \
    && rm -rf /mpstmp \
    && rm -rf /mps/jbr

COPY build/install/mps-git-import-cli /mps-git-import-cli

#RUN /mps-git-import-cli/bin/mps-git-import-cli self-test

ENTRYPOINT ["/mps-git-import-cli/bin/mps-git-import-cli"]
CMD ["self-test"]

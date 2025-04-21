FROM debian:bookworm-slim AS base

FROM base AS downloader
ARG MPS_VERSION="2024.1.3"

RUN apt update && apt install wget ca-certificates -y --no-install-recommends

# download MPS and extract into /mps
RUN set -e \
    && mkdir /mpstmp \
    && cd /mpstmp \
    && MPS_MAJOR_VERSION=`echo "$MPS_VERSION" | grep -oE '20[0-9]{2}\.[0-9]+'` \
    && wget --progress=dot:giga "https://download.jetbrains.com/mps/${MPS_MAJOR_VERSION}/MPS-${MPS_VERSION}.tar.gz" \
    && tar -xf $(ls | head -n 1) \
    && mv "MPS $MPS_MAJOR_VERSION" "/mps" \
    && cd .. \
    && rm -rf /mpstmp \
    && rm -rf /mps/jbr

FROM base

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates wget gnupg \
    && wget --progress=dot:giga -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor -o /usr/share/keyrings/adoptium.gpg \
    # Add Adoptium repository
    && echo "deb [signed-by=/usr/share/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends temurin-21-jdk ant git \
    # Clean up APT caches \
    && rm -rf /var/lib/apt/lists/* \
    && rm -rf /var/cache/apt

# JAVA_HOME should be set correctly by the temurin package installation scripts
# Verify (optional)
RUN echo "Java Version:" && java -version && \
    echo "Ant Version:" && ant -version && \
    echo "Git Version:" && git --version && \
    echo "JAVA_HOME:" && echo $JAVA_HOME && \
    echo "Installed JDKs:" && ls -l /usr/lib/jvm/

COPY --from=downloader /mps /mps

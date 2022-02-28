ARG ARCH
ARG BUILD_DATE
ARG BUILD_VERSION
ARG BASE_IMAGE=eclipse-temurin:11-jre

# Base Image
FROM $BASE_IMAGE AS build

# Labels
LABEL version="1.0"
LABEL maintaner="jorge.almela@bavenir.eu"
LABEL release-date=$BUILD_DATE
LABEL org.opencontainers.image.source https://github.com/AuroralH2020/auroral-gateway

# Variables
ARG UID=1001
ARG GID=1001
ENV UID=${UID}
ENV GID=${GID}

# Create group and user that will run the gateway
RUN groupadd -r --gid ${GID} app && useradd -r --uid ${UID} --gid ${GID} -s /sbin/nologin --home /gateway app

# Create working directory
RUN mkdir gateway
RUN mkdir gateway/persistance
WORKDIR /gateway/persistance

# Copy sources
COPY --chown=app:app target/ogwapi-jar-with-dependencies.jar /gateway/
COPY --chown=app:app pom.xml /gateway/persistance/
COPY --chown=app:app config/** /gateway/persistance/config/
COPY --chown=app:app keystore/** /gateway/persistance/keystore/

# Create directories
RUN mkdir /gateway/persistance/data \
    && mkdir /gateway/persistance/log

# Change rights and user
RUN chmod +x ./config/fillAgid.sh \
    && chown -R app:app /gateway \
    && chmod +x ./keystore/genkeys.sh

# Use non-root user    
USER app

# Select port
EXPOSE  8181

# Start the gateway-api in docker container
CMD ["java", "-jar", "../ogwapi-jar-with-dependencies.jar"]

# Base image
ARG ARCH
FROM ${ARCH}eclipse-temurin:8-jre

# Labels
LABEL version="1.0"
LABEL maintaner="jorge.almela@bavenir.eu"
LABEL release-date="26-10-2021"
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
WORKDIR /gateway

# Copy sources
COPY --chown=app:app pom.xml /gateway/
COPY  --chown=app:app target/ogwapi-jar-with-dependencies.jar /gateway/target/
COPY --chown=app:app config/** /gateway/config/
COPY --chown=app:app keystore/** /gateway/keystore/

# Create directories
RUN mkdir data \
    && mkdir log

# Change rights and user
RUN chmod 764 ./target/ogwapi-jar-with-dependencies.jar \
    && chmod -R 777  ./log/ \
    && chmod -R 777  ./data/ \
    && chown -R app:app /gateway \
    && chmod -R 764 ./config \
    && chmod +x ./keystore/genkeys.sh

# Use non-root user    
USER app

# Select port
EXPOSE  8181

# Start the gateway-api in docker container
CMD ["java", "-jar", "./target/ogwapi-jar-with-dependencies.jar"]

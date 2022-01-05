#!/bin/bash
USAGE="$(basename "$0") [ -h ] [ -e env ]
-- Build and publish image to docker registry
-- Flags:
      -h  shows help
      -e  environment [ dev (default), prod, ... ]"

# Default configuration
ENV=dev
REGISTRY=registry.bavenir.eu
IMAGE_NAME=auroral_gateway

PLATFORMS=linux/amd64,linux/arm64,linux/arm/v7
# PLATFORMS=linux/arm/v7
# Github configuration
GIT_ENV=beta
GIT_REGISTRY=ghcr.io
GIT_IMAGE_NAME=auroralh2020/auroral-gateway

# Get configuration
while getopts 'hd:e:' OPTION; do
case "$OPTION" in
    h)
    echo "$USAGE"
    exit 0
    ;;
    e)
    ENV="$OPTARG"
    ;;
esac
done

# Build Sources
echo Maven build
# create volume for maven dependencies
docker volume create --name maven-repo
# build using maven
docker run -it --rm \
       -v maven-repo:/root/.m2 \
       -v "$(pwd)":/opt/maven \
       -w /opt/maven \
       maven:3.5.4-jdk-8-slim \
       mvn clean package

echo Build and push image ${IMAGE_NAME} with tag ${ENV}

# Do login
docker login ${REGISTRY}

# Multiarch builder
docker buildx use multiplatform

# Build for AMD64/ARM64 & push to private registry
docker buildx build --platform ${PLATFORMS} \
                    --tag ${REGISTRY}/${IMAGE_NAME}:${ENV} \
                    --build-arg UID=1001 --build-arg GID=1001 \
                    --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
                    --build-arg BUILD_VERSION="1.0" \
                    -f Dockerfile . --push
docker pull ${REGISTRY}/${IMAGE_NAME}:${ENV}


# # Push to GitHub
docker buildx build --platform ${PLATFORMS} \
                    --tag ${GIT_REGISTRY}/${GIT_IMAGE_NAME}:${GIT_ENV} \
                    --build-arg UID=1001 --build-arg GID=1001 \
                    --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
                    --build-arg BUILD_VERSION="1.0" \
                    -f Dockerfile . --push
docker pull ${GIT_REGISTRY}/${GIT_IMAGE_NAME}:${GIT_ENV}
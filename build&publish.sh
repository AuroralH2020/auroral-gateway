#!/bin/bash
USAGE="$(basename "$0") [ -h ] [ -v version]
-- Build and publish image to docker registry
-- Flags:
      -h  shows help
      -v  version [ i.e. 1.0, 2.2,... ]"

# BUILD PLATFORMS 
PLATFORMS=linux/amd64,linux/arm64,linux/arm/v7
VERSION=0
LATEST=0 # If 1 build image with latest tag
# Maven using docker / local --> Default local
MAVEN_DOCKER=0

# Default configuration
ENV=dev
REGISTRY=registry.bavenir.eu
IMAGE_NAME=auroral_gateway

# Github configuration
GIT_ENV=beta
GIT_REGISTRY=ghcr.io
GIT_IMAGE_NAME=auroralh2020/auroral-gateway

# Get configuration
while getopts 'hd:v:' OPTION; do
case "$OPTION" in
    h)
    echo "$USAGE"
    exit 0
    ;;
    v)
    VERSION="$OPTARG"
    ;;
esac
done

# Update to VERSION if any
if [ ${VERSION} != 0 ]
then
    ENV=${VERSION}
    GIT_ENV=${VERSION}
    LATEST=1
    echo Do you wish to continue pushing version ${VERSION} ?
    select yn in "Yes" "No"; do
    case $yn in
        Yes ) echo Updating image version!; break;;
        No ) echo Aborting...; exit;;
    esac
    done
fi

# Build Sources
echo Maven build

if [ $MAVEN_DOCKER == 1 ]; then 
    # create volume for maven dependencies
    docker volume create --name maven-repo
    # build using maven
    docker run -it --rm \
        -v maven-repo:/root/.m2 \
        -v "$(pwd)":/opt/maven \
        -w /opt/maven \
        maven:3.5.4-jdk-8-slim \
        mvn clean package
else
    mvn clean package
fi

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
                    --build-arg BUILD_VERSION=${VERSION} \
                    -f Dockerfile . --push
docker pull ${REGISTRY}/${IMAGE_NAME}:${ENV}


# Push to GitHub
docker buildx build --platform ${PLATFORMS} \
                    --tag ${GIT_REGISTRY}/${GIT_IMAGE_NAME}:${GIT_ENV} \
                    --build-arg UID=1001 --build-arg GID=1001 \
                    --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
                    --build-arg BUILD_VERSION=${VERSION} \
                    -f Dockerfile . --push
docker pull ${GIT_REGISTRY}/${GIT_IMAGE_NAME}:${GIT_ENV}

# Build latest when new version

if [ ${LATEST} == 1 ]
then
    docker buildx build --platform ${PLATFORMS} \
                        --tag ${GIT_REGISTRY}/${GIT_IMAGE_NAME}:latest \
                        --build-arg BUILD_DATE=$(date -u +'%Y-%m-%dT%H:%M:%SZ') \
                        --build-arg BUILD_VERSION=${VERSION} \
                        -f Dockerfile . --push
fi

# Pull local arch version 
docker pull ${GIT_REGISTRY}/${GIT_IMAGE_NAME}:${GIT_ENV}

# END
echo Build and publish ended successfully!
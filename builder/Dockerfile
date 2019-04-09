# This Dockerfile builds an image that can be used to build the nomulus app.
# We need the following programs during Gradle build:
# 1. Java 8 for compilation.
# 2. Node.js/NPM for JavaScript compilation.
# 3. Google Cloud SDK for generating the WARs.
# TODO: We should probably combine multile RUN statments into one so that only
# one layer is produced, but separating them makes it easier to experiment, as
# previous layers are cached. When we settle on the content, consider merging
# the RUNs.
FROM marketplace.gcr.io/google/ubuntu1804
ENV DEBIAN_FRONTEND noninteractive
RUN apt-get update && apt-get install apt-utils -y && apt-get upgrade -y
RUN apt-get install -y locales lsb-release
# Cribbed from https://hub.docker.com/_/ubuntu
RUN localedef -i en_US -c -f UTF-8 -A /usr/share/locale/locale.alias en_US.UTF-8
ENV LANG en_US.utf8
RUN apt-get install openjdk-8-jdk-headless -y
RUN apt-get install npm -y
# Cribbed from https://cloud.google.com/sdk/docs/quickstart-debian-ubuntu
RUN export CLOUD_SDK_REPO="cloud-sdk-$(lsb_release -c -s)" && \
    echo "deb http://packages.cloud.google.com/apt $CLOUD_SDK_REPO main" \
    | tee -a /etc/apt/sources.list.d/google-cloud-sdk.list && \
    curl https://packages.cloud.google.com/apt/doc/apt-key.gpg \
    | apt-key add - && apt-get update -y
RUN apt-get install google-cloud-sdk-app-engine-java -y
RUN apt-get remove apt-utils locales lsb-release -y && \
    apt-get autoclean -y && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

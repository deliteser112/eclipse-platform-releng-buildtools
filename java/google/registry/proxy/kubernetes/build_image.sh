#!/bin/bash
# Copyright 2018 The Nomulus Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This script builds the proxy jar file with all of its dependencies included,
# then puts it in an image with a name compatible with GCR. If a "push"
# argument is given, it also uploads the image to GCR.

function cleanup() {
  rm ${WORKDIR}/${TARGET} -f
}

trap cleanup EXIT

PROJECT=`gcloud config list 2>&1 | grep project | awk -F'= ' '{print $2}'`;

echo "PROJECT: ${PROJECT}"

PACKAGE_PREFIX=""

PACKAGE=${PACKAGE_PREFIX}"java/google/registry/proxy"

TARGET=proxy_server_deploy.jar

BUILD_TOOL=bazel

WORKSPACE=`$BUILD_TOOL info workspace`

WORKDIR=${WORKSPACE}/${PACKAGE}/kubernetes

BINDIR=${WORKSPACE}/${BUILD_TOOL}-bin/${PACKAGE}

$BUILD_TOOL build "//"${PACKAGE}:${TARGET}

cp ${BINDIR}/${TARGET} ${WORKDIR}/

docker build -t gcr.io/${PROJECT}/proxy:latest $WORKDIR

# Publish the image to GCR if "push" argument is given.
if [ -z $1 ]
then
  exit
fi

if [ $1 = "push" ]
then
  gcloud docker -- push gcr.io/${PROJECT}/proxy:latest
else
  echo "usage: $0 [push]"
fi

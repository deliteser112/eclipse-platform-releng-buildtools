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
#
# This script runs a workflow to generate dependency lock file, run a build against
# the generated lock file, save the lock file and upload dependency JARs to a private
# Maven repository if the build succeeds.

set -e

ALL_SUBPROJECTS="core proxy util"
SUBPROJECTS=
REPOSITORY_URL=

while [[ $# -gt 0 ]]; do
  KEY="$1"
  case ${KEY} in
    --repositoryUrl)
      shift
      REPOSITORY_URL="$1"
      ;;
    *)
      SUBPROJECTS="${SUBPROJECTS} ${KEY}"
      ;;
  esac
  shift
done

if [[ -z ${SUBPROJECTS} ]]; then
  SUBPROJECTS="${ALL_SUBPROJECTS}"
fi

if [[ -z ${REPOSITORY_URL} ]]; then
  echo "--repositoryUrl must be specified"
  exit 1
fi

WORKING_DIR=$(dirname $0)

for PROJECT in ${SUBPROJECTS}; do
  ${WORKING_DIR}/gradlew ":${PROJECT}:generateLock"
  ${WORKING_DIR}/gradlew -PdependencyLock.useGeneratedLock=true \
    ":${PROJECT}:build"
  ${WORKING_DIR}/gradlew ":${PROJECT}:saveLock"
  ${WORKING_DIR}/gradlew -PrepositoryUrl="${REPOSITORY_URL}" \
    ":${PROJECT}:publish"
done

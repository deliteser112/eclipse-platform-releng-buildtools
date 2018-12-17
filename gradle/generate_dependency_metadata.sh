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
# This script runs a workflow to clone a git repository to local, generate
# a metadata file for each dependency artifact and check in the file to remote
# repository.

set -e

ALL_SUBPROJECTS="core proxy util"

USAGE="Usage: ${0} REPO_URL"
REPO_URL=${1:?${USAGE}}

REPO_DIR="$(mktemp -d)"

git clone ${REPO_URL} ${REPO_DIR}
for PROJECT in ${ALL_SUBPROJECTS}; do
  $(dirname $0)/gradlew -PprivateRepository="${REPO_DIR}" \
    ":${PROJECT}:generateDependencyMetadata"
done
cd "${REPO_DIR}"
git add -A
git diff-index --quiet HEAD \
  || git commit -m "Update dependency metadata file" && git push
rm -rf "${REPO_DIR}"

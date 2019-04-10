# Copyright 2019 The Nomulus Authors. All Rights Reserved.
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

# This Dockerfile builds an image that can be used to build the nomulus app.
# We need the following programs during Gradle build:
# 1. Java 8 for compilation.
# 2. Node.js/NPM for JavaScript compilation.
# 3. Google Cloud SDK for generating the WARs.
FROM marketplace.gcr.io/google/ubuntu1804
ENV DEBIAN_FRONTEND=noninteractive LANG=en_US.UTF-8
ADD ./build.sh .
RUN ["bash", "./build.sh"]

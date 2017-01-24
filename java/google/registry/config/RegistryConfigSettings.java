// Copyright 2016 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.config;

/** The POJO that YAML config files are deserialized into. */
public class RegistryConfigSettings {

  public General general;

  public Datastore datastore;

  public Monitoring monitoring;

  /** General configuration options that apply to the entire App Engine project. */
  public static class General {

    public String appEngineProjectId;
  }

  /** Configuration for Cloud Datastore. */
  public static class Datastore {

    public int commitLogBucketsNum;

    public int eppResourceIndexBucketsNum;
  }

  /** Configuration for monitoring. */
  public static class Monitoring {

    public int stackdriverMaxQps;

    public int stackdriverMaxPointsPerRequest;

    public int writeIntervalSeconds;
  }
}

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

import java.util.List;

/** The POJO that YAML config files are deserialized into. */
public class RegistryConfigSettings {

  public AppEngine appEngine;
  public GSuite gSuite;
  public RegistryPolicy registryPolicy;
  public Datastore datastore;
  public RegistrarConsole registrarConsole;
  public Monitoring monitoring;

  /** Configuration options that apply to the entire App Engine project. */
  public static class AppEngine {
    public String projectId;
  }

  /** Configuration options for the G Suite account used by Nomulus. */
  public static class GSuite {
    public String domainName;
    public String outgoingEmailAddress;
    public String outgoingEmailDisplayName;
    public String adminAccountEmailAddress;
  }

  /** Configuration options for registry policy. */
  public static class RegistryPolicy {
    public String contactAndHostRoidSuffix;
    public String productName;
    public List<String> registrarChangesNotificationEmailAddresses;
    public String defaultRegistrarWhoisServer;
    public String defaultRegistrarReferralUrl;
  }

  /** Configuration for Cloud Datastore. */
  public static class Datastore {
    public int commitLogBucketsNum;
    public int eppResourceIndexBucketsNum;
  }

  /** Configuration for the web-based registrar console. */
  public static class RegistrarConsole {
    public String logoFilename;
    public String supportPhoneNumber;
    public String supportEmailAddress;
    public String announcementsEmailAddress;
    public String integrationEmailAddress;
    public String technicalDocsUrl;
  }

  /** Configuration for monitoring. */
  public static class Monitoring {
    public int stackdriverMaxQps;
    public int stackdriverMaxPointsPerRequest;
    public int writeIntervalSeconds;
  }
}

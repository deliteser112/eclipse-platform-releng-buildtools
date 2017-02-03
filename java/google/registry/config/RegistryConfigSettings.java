// Copyright 2017 The Nomulus Authors. All Rights Reserved.
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
import java.util.Map;

/** The POJO that YAML config files are deserialized into. */
public class RegistryConfigSettings {

  public AppEngine appEngine;
  public GSuite gSuite;
  public RegistryPolicy registryPolicy;
  public Datastore datastore;
  public Caching caching;
  public Rde rde;
  public RegistrarConsole registrarConsole;
  public Monitoring monitoring;
  public Misc misc;
  public Rdap rdap;
  public Braintree braintree;

  /** Configuration options that apply to the entire App Engine project. */
  public static class AppEngine {
    public String projectId;
    public ToolsServiceUrl toolsServiceUrl;

    /** Configuration options for the tools service URL. */
    public static class ToolsServiceUrl {
      public String hostName;
      public int port;
    }
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
    public String customLogicFactoryClass;
    public String whoisCommandFactoryClass;
    public int contactAutomaticTransferDays;
    public String greetingServerId;
    public List<String> registrarChangesNotificationEmailAddresses;
    public String defaultRegistrarWhoisServer;
    public String defaultRegistrarReferralUrl;
    public String tmchCaMode;
    public String tmchCrlUrl;
    public String tmchMarksDbUrl;
    public String checkApiServletClientId;
    public String reservedTermsExportDisclaimer;
    public String whoisDisclaimer;
  }

  /** Configuration for Cloud Datastore. */
  public static class Datastore {
    public int commitLogBucketsNum;
    public int eppResourceIndexBucketsNum;
    public int baseOfyRetryMillis;
  }

  /** Configuration for caching. */
  public static class Caching {
    public int singletonCacheRefreshSeconds;
    public int domainLabelCachingSeconds;
    public int singletonCachePersistSeconds;
  }

  /** Configuration for Registry Data Escrow (RDE). */
  public static class Rde {
    public String reportUrlPrefix;
    public String uploadUrl;
    public String sshIdentityEmailAddress;
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

  /** Miscellaneous configuration that doesn't quite fit in anywhere else. */
  public static class Misc {
    public String sheetExportId;
  }

  /** Configuration for RDAP. */
  public static class Rdap {
    public String baseUrl;
  }

  /** Configuration for Braintree credit card payment processing. */
  public static class Braintree {
    public String merchantId;
    public String publicKey;
    public Map<String, String> merchantAccountIdsMap;
  }
}

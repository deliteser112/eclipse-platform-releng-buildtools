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

import static google.registry.config.ConfigUtils.makeUrl;

import com.google.common.base.Ascii;
import com.google.common.net.HostAndPort;
import java.net.URL;
import org.joda.time.Duration;

/**
 * Registry configuration for global constants that can't be injected.
 */
public final class RegistryConfig {

  /**
   * Returns the App Engine project ID, which is based off the environment name.
   */
  public static String getProjectId() {
    String prodProjectId = "domain-registry";
    RegistryEnvironment environment = RegistryEnvironment.get();
    switch (environment) {
      case PRODUCTION:
      case UNITTEST:
      case LOCAL:
        return prodProjectId;
      default:
        return prodProjectId + "-" + Ascii.toLowerCase(environment.name());
    }
  }

  /**
   * Returns the Google Cloud Storage bucket for storing backup snapshots.
   *
   * @see google.registry.export.ExportSnapshotServlet
   */
  public static String getSnapshotsBucket() {
    return getProjectId() + "-snapshots";
  }

  /**
   * Number of sharded commit log buckets.
   *
   * <p>This number is crucial for determining how much transactional throughput the system can
   * allow, because it determines how many entity groups are available for writing commit logs.
   * Since entity groups have a one transaction per second SLA (which is actually like ten in
   * practice), a registry that wants to be able to handle one hundred transactions per second
   * should have one hundred buckets.
   *
   * <p><b>Warning:</b> This can be raised but never lowered.
   *
   * @see google.registry.model.ofy.CommitLogBucket
   */
  public static int getCommitLogBucketCount() {
    switch (RegistryEnvironment.get()) {
      case UNITTEST:
        return 3;
      default:
        return 100;
    }
  }

  /**
   * Returns the length of time before commit logs should be deleted from datastore.
   *
   * <p>The only reason you'll want to retain this commit logs in datastore is for performing
   * point-in-time restoration queries for subsystems like RDE.
   *
   * @see google.registry.backup.DeleteOldCommitLogsAction
   * @see google.registry.model.translators.CommitLogRevisionsTranslatorFactory
   */
  public static Duration getCommitLogDatastoreRetention() {
    return Duration.standardDays(30);
  }

  /**
   * Returns {@code true} if TMCH certificate authority should be in testing mode.
   *
   * @see google.registry.tmch.TmchCertificateAuthority
   */
  public static boolean getTmchCaTestingMode() {
    switch (RegistryEnvironment.get()) {
      case PRODUCTION:
        return false;
      default:
        return true;
    }
  }

  /**
   * Returns the address of the Nomulus app HTTP server.
   *
   * <p>This is used by the {@code nomulus} tool to connect to the App Engine remote API.
   */
  public static HostAndPort getServer() {
    switch (RegistryEnvironment.get()) {
      case LOCAL:
        return HostAndPort.fromParts("localhost", 8080);
      case UNITTEST:
        throw new UnsupportedOperationException("Unit tests can't spin up a full server");
      default:
        return HostAndPort.fromParts(
            String.format("tools-dot-%s.appspot.com", getProjectId()), 443);
    }
  }

  /** Returns the amount of time a singleton should be cached, before expiring. */
  public static Duration getSingletonCacheRefreshDuration() {
    switch (RegistryEnvironment.get()) {
      case UNITTEST:
        // All cache durations are set to zero so that unit tests can update and then retrieve data
        // immediately without failure.
        return Duration.ZERO;
      default:
        return Duration.standardMinutes(10);
    }
  }

  /**
   * Returns the amount of time a domain label list should be cached in memory before expiring.
   *
   * @see google.registry.model.registry.label.ReservedList
   * @see google.registry.model.registry.label.PremiumList
   */
  public static Duration getDomainLabelListCacheDuration() {
    switch (RegistryEnvironment.get()) {
      case UNITTEST:
        return Duration.ZERO;
      default:
        return Duration.standardHours(1);
    }
  }

  /** Returns the amount of time a singleton should be cached in persist mode, before expiring. */
  public static Duration getSingletonCachePersistDuration() {
    switch (RegistryEnvironment.get()) {
      case UNITTEST:
        return Duration.ZERO;
      default:
        return Duration.standardDays(365);
    }
  }

  /**
   * Returns default WHOIS server to use when {@code Registrar#getWhoisServer()} is {@code null}.
   *
   * @see "google.registry.whois.DomainWhoisResponse"
   * @see "google.registry.whois.RegistrarWhoisResponse"
   */
  public static String getRegistrarDefaultWhoisServer() {
    switch (RegistryEnvironment.get()) {
      case UNITTEST:
        return "whois.nic.fakewhois.example";
      default:
        return "whois.nic.registry.example";
    }
  }

  /**
   * Returns the default referral URL that is used unless registrars have specified otherwise.
   */
  public static URL getRegistrarDefaultReferralUrl() {
    switch (RegistryEnvironment.get()) {
      case UNITTEST:
        return makeUrl("http://www.referral.example/path");
      default:
        return makeUrl("https://www.registry.example");
    }
  }

  /**
   * Returns the number of {@code EppResourceIndex} buckets to be used.
   */
  public static int getEppResourceIndexBucketCount() {
    switch (RegistryEnvironment.get()) {
      case UNITTEST:
        return 3;
      default:
        return 997;
    }
  }

  /**
   * Returns the base retry duration that gets doubled after each failure within {@code Ofy}.
   */
  public static Duration getBaseOfyRetryDuration() {
    switch (RegistryEnvironment.get()) {
      case UNITTEST:
        return Duration.ZERO;
      default:
        return Duration.millis(100);
    }
  }

  private RegistryConfig() {}
}

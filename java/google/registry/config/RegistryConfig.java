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

import com.google.common.base.Ascii;
import com.google.common.base.Optional;
import com.google.common.net.HostAndPort;
import java.net.URL;
import org.joda.time.Duration;

/**
 * Registry configuration for global constants that can't be injected.
 *
 * <p>The goal of this custom configuration system is to have our project environments configured
 * in type-safe Java code that can be refactored, rather than XML files and system properties.
 */
public abstract class RegistryConfig {

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

  public abstract Optional<String> getECatcherAddress();

  /**
   * Returns the address of the Nomulus app HTTP server.
   *
   * <p>This is used by the {@code nomulus} tool to connect to the App Engine remote API.
   */
  public abstract HostAndPort getServer();

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
   * Returns the header text at the top of the reserved terms exported list.
   *
   * @see google.registry.export.ExportUtils#exportReservedTerms
   */
  public abstract String getReservedTermsExportDisclaimer();

  /**
   * Returns default WHOIS server to use when {@code Registrar#getWhoisServer()} is {@code null}.
   *
   * @see "google.registry.whois.DomainWhoisResponse"
   * @see "google.registry.whois.RegistrarWhoisResponse"
   */
  public abstract String getRegistrarDefaultWhoisServer();

  /**
   * Returns the default referral URL that is used unless registrars have specified otherwise.
   */
  public abstract URL getRegistrarDefaultReferralUrl();

  /**
   * Returns the number of EppResourceIndex buckets to be used.
   */
  public abstract int getEppResourceIndexBucketCount();

  /**
   * Returns the base duration that gets doubled on each retry within {@code Ofy}.
   */
  public abstract Duration getBaseOfyRetryDuration();

  /**
   * Returns the global automatic transfer length for contacts.  After this amount of time has
   * elapsed, the transfer is automatically approved.
   */
  public abstract Duration getContactAutomaticTransferLength();

  /**
   * Returns the clientId of the registrar used by the {@code CheckApiServlet}.
   */
  public abstract String getCheckApiServletRegistrarClientId();

  // XXX: Please consider using ConfigModule instead of adding new methods to this file.
}

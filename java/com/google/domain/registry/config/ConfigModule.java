// Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.domain.registry.config;

import static com.google.domain.registry.config.ConfigUtils.makeUrl;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;

import dagger.Module;
import dagger.Provides;

import org.joda.money.CurrencyUnit;
import org.joda.time.DateTimeConstants;
import org.joda.time.Duration;

import java.lang.annotation.Documented;
import java.net.URI;
import java.net.URL;

import javax.inject.Qualifier;

/** Dagger module for injecting configuration settings. */
@Module
public final class ConfigModule {

  /** Dagger qualifier for configuration settings. */
  @Qualifier
  @Documented
  public static @interface Config {
    String value() default "";
  }

  private static final RegistryEnvironment registryEnvironment = RegistryEnvironment.get();

  @Provides
  public static RegistryEnvironment provideRegistryEnvironment() {
    return registryEnvironment;
  }

  @Provides
  public static RegistryConfig provideConfig(RegistryEnvironment environment) {
    return environment.config();
  }

  @Provides
  @Config("projectId")
  public static String provideProjectId(RegistryConfig config) {
    return config.getProjectId();
  }

  /** @see RegistryConfig#getZoneFilesBucket() */
  @Provides
  @Config("zoneFilesBucket")
  public static String provideZoneFilesBucket(RegistryConfig config) {
    return config.getZoneFilesBucket();
  }

  /** @see RegistryConfig#getCommitsBucket() */
  @Provides
  @Config("commitLogGcsBucket")
  public static String provideCommitLogGcsBucket(RegistryConfig config) {
    return config.getCommitsBucket();
  }

  /** @see RegistryConfig#getCommitLogDatastoreRetention() */
  @Provides
  @Config("commitLogDatastoreRetention")
  public static Duration provideCommitLogDatastoreRetention(RegistryConfig config) {
    return config.getCommitLogDatastoreRetention();
  }

  /**
   * Maximum number of commit logs to delete per transaction.
   *
   * <p>If we assume that the average key size is 256 bytes and that each manifest has six
   * mutations, we can do about 5,000 deletes in a single transaction before hitting the 10mB limit.
   * Therefore 500 should be a safe number, since it's an order of a magnitude less space than we
   * need.
   *
   * <p>Transactions also have a four minute time limit. Since we have to perform N subqueries to
   * fetch mutation keys, 500 would be a safe number if those queries were performed in serial,
   * since each query would have about 500ms to complete, which is an order a magnitude more time
   * than we need. However this does not apply, since the subqueries are performed asynchronously.
   *
   * @see com.google.domain.registry.backup.DeleteOldCommitLogsAction
   */
  @Provides
  @Config("commitLogMaxDeletes")
  public static int provideCommitLogMaxDeletes() {
    return 500;
  }

  /**
   * Batch size for the number of transactions' worth of commit log data to process at once when
   * exporting a commit log diff.
   *
   * @see com.google.domain.registry.backup.ExportCommitLogDiffAction
   */
  @Provides
  @Config("commitLogDiffExportBatchSize")
  public static int provideCommitLogDiffExportBatchSize() {
    return 100;
  }

  /**
   * Returns the Google Cloud Storage bucket for staging BRDA escrow deposits.
   *
   * @see com.google.domain.registry.rde.PendingDepositChecker
   */
  @Provides
  @Config("brdaBucket")
  public static String provideBrdaBucket(@Config("projectId") String projectId) {
    return projectId + "-icann-brda";
  }

  /** @see com.google.domain.registry.rde.BrdaCopyTask */
  @Provides
  @Config("brdaDayOfWeek")
  public static int provideBrdaDayOfWeek() {
    return DateTimeConstants.TUESDAY;
  }

  /** Amount of time between BRDA deposits. */
  @Provides
  @Config("brdaInterval")
  public static Duration provideBrdaInterval() {
    return Duration.standardDays(7);
  }

  /** Maximum amount of time generating an BRDA deposit for a TLD could take, before killing. */
  @Provides
  @Config("brdaLockTimeout")
  public static Duration provideBrdaLockTimeout() {
    return Duration.standardHours(5);
  }

  /** Returns {@code true} if the target zone should be created in DNS if it does not exist. */
  @Provides
  @Config("dnsCreateZone")
  public static boolean provideDnsCreateZone(RegistryEnvironment environment) {
    switch (environment) {
      case PRODUCTION:
        return false;
      default:
        return true;
    }
  }

  /**
   * The maximum number of domain and host updates to batch together to send to
   * PublishDnsUpdatesAction, to avoid exceeding AppEngine's limits.
   * */
  @Provides
  @Config("dnsTldUpdateBatchSize")
  public static int provideDnsTldUpdateBatchSize() {
    return 100;
  }

  /** The maximum interval (seconds) to lease tasks from the dns-pull queue. */
  @Provides
  @Config("dnsWriteLockTimeout")
  public static Duration provideDnsWriteLockTimeout() {
    // Optimally, we would set this to a little less than the length of the DNS refresh cycle, since
    // otherwise, a new PublishDnsUpdatesAction could get kicked off before the current one has
    // finished, which will try and fail to acquire the lock. However, it is more important that it
    // be greater than the DNS write timeout, so that if that timeout occurs, it will be cleaned up
    // gracefully, rather than having the lock time out. So we have to live with the possible lock
    // failures.
    return Duration.standardSeconds(75);
  }

  /** Returns the default time to live for DNS records. */
  @Provides
  @Config("dnsDefaultTtl")
  public static Duration provideDnsDefaultTtl() {
    return Duration.standardSeconds(180);
  }

  /**
   * Number of sharded entity group roots used for performing strongly consistent scans.
   *
   * <p><b>Warning:</b> This number may increase but never decrease.
   *
   * @see com.google.domain.registry.model.index.EppResourceIndex
   */
  @Provides
  @Config("eppResourceIndexBucketCount")
  public static int provideEppResourceIndexBucketCount(RegistryConfig config) {
    return config.getEppResourceIndexBucketCount();
  }

  /**
   * Returns size of Google Cloud Storage client connection buffer in bytes.
   *
   * @see com.google.domain.registry.gcs.GcsUtils
   */
  @Provides
  @Config("gcsBufferSize")
  public static int provideGcsBufferSize() {
    return 1024 * 1024;
  }

  /**
   * Gets the email address of the admin account for the Google App.
   *
   * @see com.google.domain.registry.groups.DirectoryGroupsConnection
   */
  @Provides
  @Config("googleAppsAdminEmailAddress")
  public static String provideGoogleAppsAdminEmailAddress(RegistryEnvironment environment) {
    switch (environment) {
      case PRODUCTION:
        return "admin@registry.google";
      default:
        return "admin@domainregistry-sandbox.co";
    }
  }

  /**
   * Returns the publicly accessible domain name for the running Google Apps instance.
   *
   * @see com.google.domain.registry.export.SyncGroupMembersTask
   * @see com.google.domain.registry.tools.server.CreateGroupsTask
   */
  @Provides
  @Config("publicDomainName")
  public static String providePublicDomainName(RegistryEnvironment environment) {
    switch (environment) {
      case PRODUCTION:
        return "registry.google";
      default:
        return "domainregistry-sandbox.co";
    }
  }

  @Provides
  @Config("tmchCaTestingMode")
  public static boolean provideTmchCaTestingMode(RegistryConfig config) {
    return config.getTmchCaTestingMode();
  }

  /**
   * ICANN TMCH Certificate Revocation List URL.
   *
   * <p>This file needs to be downloaded at least once a day and verified to make sure it was
   * signed by {@code icann-tmch.crt}.
   *
   * @see com.google.domain.registry.tmch.TmchCrlTask
   * @see "http://tools.ietf.org/html/draft-lozano-tmch-func-spec-08#section-5.2.3.2"
   */
  @Provides
  @Config("tmchCrlUrl")
  public static URL provideTmchCrlUrl(RegistryEnvironment environment) {
    switch (environment) {
      case PRODUCTION:
        return makeUrl("http://crl.icann.org/tmch.crl");
      default:
        return makeUrl("http://crl.icann.org/tmch_pilot.crl");
    }
  }

  @Provides
  @Config("tmchMarksdbUrl")
  public static String provideTmchMarksdbUrl(RegistryConfig config) {
    return config.getTmchMarksdbUrl();
  }

  /**
   * Returns the Google Cloud Storage bucket for staging escrow deposits pending upload.
   *
   * @see com.google.domain.registry.rde.RdeStagingAction
   */
  @Provides
  @Config("rdeBucket")
  public static String provideRdeBucket(@Config("projectId") String projectId) {
    return projectId + "-rde";
  }

  /**
   * Size of Ghostryde buffer in bytes for each layer in the pipeline.
   *
   * @see com.google.domain.registry.rde.Ghostryde
   */
  @Provides
  @Config("rdeGhostrydeBufferSize")
  public static Integer provideRdeGhostrydeBufferSize() {
    return 64 * 1024;
  }

  /** Amount of time between RDE deposits. */
  @Provides
  @Config("rdeInterval")
  public static Duration provideRdeInterval() {
    return Duration.standardDays(1);
  }

  /** Maximum amount of time for sending a small XML file to ICANN via HTTP, before killing. */
  @Provides
  @Config("rdeReportLockTimeout")
  public static Duration provideRdeReportLockTimeout() {
    return Duration.standardSeconds(60);
  }

  /**
   * URL of ICANN's HTTPS server to which the RDE report should be {@code PUT}.
   *
   * <p>You must append {@code "/TLD/ID"} to this URL.
   *
   * @see com.google.domain.registry.rde.RdeReportTask
   */
  @Provides
  @Config("rdeReportUrlPrefix")
  public static String provideRdeReportUrlPrefix(RegistryEnvironment environment) {
    switch (environment) {
      case PRODUCTION:
        return "https://ry-api.icann.org/report/registry-escrow-report";
      default:
        return "https://test-ry-api.icann.org:8543/report/registry-escrow-report";
    }
  }

  /**
   * Size of RYDE generator buffer in bytes for each of the five layers.
   *
   * @see com.google.domain.registry.rde.RydePgpCompressionOutputStream
   * @see com.google.domain.registry.rde.RydePgpFileOutputStream
   * @see com.google.domain.registry.rde.RydePgpSigningOutputStream
   * @see com.google.domain.registry.rde.RydeTarOutputStream
   */
  @Provides
  @Config("rdeRydeBufferSize")
  public static Integer provideRdeRydeBufferSize() {
    return 64 * 1024;
  }

  /** Maximum amount of time generating an escrow deposit for a TLD could take, before killing. */
  @Provides
  @Config("rdeStagingLockTimeout")
  public static Duration provideRdeStagingLockTimeout() {
    return Duration.standardHours(5);
  }

  /** Maximum amount of time it should ever take to upload an escrow deposit, before killing. */
  @Provides
  @Config("rdeUploadLockTimeout")
  public static Duration provideRdeUploadLockTimeout() {
    return Duration.standardMinutes(30);
  }

  /**
   * Minimum amount of time to wait between consecutive SFTP uploads on a single TLD.
   *
   * <p>This value was communicated to us by the escrow provider.
   */
  @Provides
  @Config("rdeUploadSftpCooldown")
  public static Duration provideRdeUploadSftpCooldown() {
    return Duration.standardHours(2);
  }

  /**
   * Returns SFTP URL containing a username, hostname, port (optional), and directory (optional) to
   * which cloud storage files are uploaded. The password should not be included, as it's better to
   * use public key authentication.
   *
   * @see com.google.domain.registry.rde.RdeUploadTask
   */
  @Provides
  @Config("rdeUploadUrl")
  public static URI provideRdeUploadUrl(RegistryEnvironment environment) {
    switch (environment) {
      case PRODUCTION:
        return URI.create("sftp://GoogleTLD@sftpipm2.ironmountain.com/Outbox");
      default:
        return URI.create("sftp://google@ppftpipm.ironmountain.com/Outbox");
    }
  }

  /** Maximum amount of time for syncing a spreadsheet, before killing. */
  @Provides
  @Config("sheetLockTimeout")
  public static Duration provideSheetLockTimeout() {
    return Duration.standardHours(1);
  }

  /**
   * Returns ID of Google Spreadsheet to which Registrar entities should be synced.
   *
   * <p>This ID, as you'd expect, comes from the URL of the spreadsheet.
   *
   * @see com.google.domain.registry.export.sheet.SyncRegistrarsSheetTask
   */
  @Provides
  @Config("sheetRegistrarId")
  public static Optional<String> provideSheetRegistrarId(RegistryEnvironment environment) {
    switch (environment) {
      case PRODUCTION:
        return Optional.of("1n2Gflqsgo9iDXcdt9VEskOVySZ8qIhQHJgjqsleCKdE");
      case ALPHA:
      case CRASH:
        return Optional.of("16BwRt6v11Iw-HujCbAkmMxqw3sUG13B8lmXLo-uJTsE");
      case SANDBOX:
        return Optional.of("1TlR_UMCtfpkxT9oUEoF5JEbIvdWNkLRuURltFkJ_7_8");
      case QA:
        return Optional.of("1RoY1XZhLLwqBkrz0WbEtaT9CU6c8nUAXfId5BtM837o");
      default:
        return Optional.absent();
    }
  }

  /** Amount of time between synchronizations of the Registrar spreadsheet. */
  @Provides
  @Config("sheetRegistrarInterval")
  public static Duration provideSheetRegistrarInterval() {
    return Duration.standardHours(1);
  }

  /**
   * Returns SSH client connection and read timeout.
   *
   * @see com.google.domain.registry.rde.RdeUploadTask
   */
  @Provides
  @Config("sshTimeout")
  public static Duration provideSshTimeout() {
    return Duration.standardSeconds(30);
  }

  /** Duration after watermark where we shouldn't deposit, because transactions might be pending. */
  @Provides
  @Config("transactionCooldown")
  public static Duration provideTransactionCooldown() {
    return Duration.standardMinutes(5);
  }

  /**
   * Number of times to retry a GAE operation when {@code TransientFailureException} is thrown.
   *
   * <p>The number of milliseconds it'll sleep before giving up is {@code 2^n - 2}.
   *
   * @see com.google.domain.registry.util.TaskEnqueuer
   */
  @Provides
  @Config("transientFailureRetries")
  public static int provideTransientFailureRetries() {
    return 12;  // Four seconds.
  }

  /**
   * Amount of time public HTTP proxies are permitted to cache our WHOIS responses.
   *
   * @see com.google.domain.registry.whois.WhoisHttpServer
   */
  @Provides
  @Config("whoisHttpExpires")
  public static Duration provideWhoisHttpExpires() {
    return Duration.standardDays(1);
  }

  /**
   * Maximum number of results to return for an RDAP search query
   *
   * @see com.google.domain.registry.rdap.RdapActionBase
   */
  @Provides
  @Config("rdapResultSetMaxSize")
  public static int provideRdapResultSetMaxSize() {
    return 100;
  }

  /**
   * Base for RDAP link paths.
   *
   * @see com.google.domain.registry.rdap.RdapActionBase
   */
  @Provides
  @Config("rdapLinkBase")
  public static String provideRdapLinkBase() {
    return "https://nic.google/rdap/";
  }

  /**
   * WHOIS server displayed in RDAP query responses.
   *
   * @see com.google.domain.registry.rdap.RdapActionBase
   */
  @Provides
  @Config("rdapWhoisServer")
  public static String provideRdapWhoisServer() {
    return "whois.nic.google";
  }

  /** Returns Braintree Merchant Account IDs for each supported currency. */
  @Provides
  @Config("braintreeMerchantAccountIds")
  public static ImmutableMap<CurrencyUnit, String> provideBraintreeMerchantAccountId(
      RegistryEnvironment environment) {
    switch (environment) {
      case PRODUCTION:
        return ImmutableMap.of(
            CurrencyUnit.USD, "charlestonregistryUSD",
            CurrencyUnit.JPY, "charlestonregistryJPY");
      default:
        return ImmutableMap.of(
            CurrencyUnit.USD, "google",
            CurrencyUnit.JPY, "google-jpy");
    }
  }

  /**
   * Returns Braintree Merchant ID of Registry, used for accessing Braintree API.
   *
   * <p>This is a base32 value copied from the Braintree website.
   */
  @Provides
  @Config("braintreeMerchantId")
  public static String provideBraintreeMerchantId(RegistryEnvironment environment) {
    switch (environment) {
      case PRODUCTION:
        return "TODO(b/25619518): Add production Braintree API credentials";
      default:
        // Valentine: Domain Registry Braintree Sandbox
        return "vqgn8khkq2cs6y9s";
    }
  }

  /**
   * Returns Braintree Public Key of Registry, used for accessing Braintree API.
   *
   * <p>This is a base32 value copied from the Braintree website.
   *
   * @see com.google.domain.registry.keyring.api.Keyring#getBraintreePrivateKey()
   */
  @Provides
  @Config("braintreePublicKey")
  public static String provideBraintreePublicKey(RegistryEnvironment environment) {
    switch (environment) {
      case PRODUCTION:
        return "tzcfxggzgbh2jg5x";
      default:
        // Valentine: Domain Registry Braintree Sandbox
        return "tzcyzvm3mn7zkdnx";
    }
  }
}

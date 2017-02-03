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

import static com.google.common.base.Suppliers.memoize;
import static google.registry.config.ConfigUtils.makeUrl;
import static google.registry.config.YamlUtils.getConfigSettings;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.google.common.base.Ascii;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import dagger.Module;
import dagger.Provides;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.joda.money.CurrencyUnit;
import org.joda.time.DateTimeConstants;
import org.joda.time.Duration;

/**
 * Central clearing-house for all configuration.
 */
public final class RegistryConfig {

  /** Dagger qualifier for configuration settings. */
  @Qualifier
  @Retention(RUNTIME)
  @Documented
  public static @interface Config {
    String value() default "";
  }

  /**
   * Configuration example for the Nomulus codebase.
   *
   * <p>The Nomulus codebase contains many classes that inject configurable settings. This is
   * the centralized class that is used by default to configure them all, in hard-coded type-safe
   * Java code.
   *
   * <p>This class does not represent the total configuration of the Nomulus service. It's
   * <b>only meant for settings that need to be configured <i>once</i></b>. Settings which may
   * be subject to change in the future, should instead be retrieved from Datastore. The
   * {@link google.registry.model.registry.Registry Registry} class is one such example of this.
   *
   * <h3>Customization</h3>
   *
   * <p>It is recommended that users do not modify this file within a forked repository. It is
   * preferable to modify these settings by swapping out this module with a separate copied version
   * in the user's repository. For this to work, other files need to be copied too, such as the
   * {@code @Component} instances under {@code google.registry.module}.  This allows modules to be
   * substituted at the {@code @Component} level.
   */
  @Module
  public static final class ConfigModule {

    private static final RegistryEnvironment REGISTRY_ENVIRONMENT = RegistryEnvironment.get();

    @Provides
    public static RegistryEnvironment provideRegistryEnvironment() {
      return REGISTRY_ENVIRONMENT;
    }

    @Provides
    @Config("projectId")
    public static String provideProjectId(RegistryConfigSettings config) {
      return config.appEngine.projectId;
    }

    /**
     * The filename of the logo to be displayed in the header of the registrar console.
     *
     * @see google.registry.ui.server.registrar.ConsoleUiAction
     */
    @Provides
    @Config("logoFilename")
    public static String provideLogoFilename(RegistryConfigSettings config) {
      return config.registrarConsole.logoFilename;
    }

    /**
     * The product name of this specific registry.  Used throughout the registrar console.
     *
     * @see google.registry.ui.server.registrar.ConsoleUiAction
     */
    @Provides
    @Config("productName")
    public static String provideProductName(RegistryConfigSettings config) {
      return config.registryPolicy.productName;
    }

    /**
     * Returns the roid suffix to be used for the roids of all contacts and hosts.  E.g. a value of
     * "ROID" would end up creating roids that look like "ABC123-ROID".
     *
     * @see <a href="http://www.iana.org/assignments/epp-repository-ids/epp-repository-ids.xhtml">
     *      Extensible Provisioning Protocol (EPP) Repository Identifiers</a>
     */
    @Provides
    @Config("contactAndHostRoidSuffix")
    public static String provideContactAndHostRoidSuffix(RegistryConfigSettings config) {
      return config.registryPolicy.contactAndHostRoidSuffix;
    }

    /**
     * The e-mail address for questions about integrating with the registry.  Used in the
     * "contact-us" section of the registrar console.
     *
     * @see google.registry.ui.server.registrar.ConsoleUiAction
     */
    @Provides
    @Config("integrationEmail")
    public static String provideIntegrationEmail(RegistryConfigSettings config) {
      return config.registrarConsole.integrationEmailAddress;
    }

    /**
     * The e-mail address for general support.  Used in the "contact-us" section of the registrar
     * console.
     *
     * @see google.registry.ui.server.registrar.ConsoleUiAction
     */
    @Provides
    @Config("supportEmail")
    public static String provideSupportEmail(RegistryConfigSettings config) {
      return config.registrarConsole.supportEmailAddress;
    }

    /**
     * The "From" e-mail address for announcements.  Used in the "contact-us" section of the
     * registrar console.
     *
     * @see google.registry.ui.server.registrar.ConsoleUiAction
     */
    @Provides
    @Config("announcementsEmail")
    public static String provideAnnouncementsEmail(RegistryConfigSettings config) {
      return config.registrarConsole.announcementsEmailAddress;
    }

    /**
     * The contact phone number.  Used in the "contact-us" section of the registrar console.
     *
     * @see google.registry.ui.server.registrar.ConsoleUiAction
     */
    @Provides
    @Config("supportPhoneNumber")
    public static String provideSupportPhoneNumber(RegistryConfigSettings config) {
      return config.registrarConsole.supportPhoneNumber;
    }

    /**
     * The URL for technical support docs. Used in the "contact-us" section of the registrar
     * console.
     *
     * @see google.registry.ui.server.registrar.ConsoleUiAction
     */
    @Provides
    @Config("technicalDocsUrl")
    public static String provideTechnicalDocsUrl(RegistryConfigSettings config) {
      return config.registrarConsole.technicalDocsUrl;
    }

    /**
     * Returns the Google Cloud Storage bucket for storing zone files.
     *
     * @see google.registry.backup.ExportCommitLogDiffAction
     */
    @Provides
    @Config("zoneFilesBucket")
    public static String provideZoneFilesBucket(@Config("projectId") String projectId) {
      return projectId + "-zonefiles";
    }

    /**
     * Returns the Google Cloud Storage bucket for storing commit logs.
     *
     * @see google.registry.backup.ExportCommitLogDiffAction
     */
    @Provides
    @Config("commitLogGcsBucket")
    public static String provideCommitLogGcsBucket(@Config("projectId") String projectId) {
      return projectId + "-commits";
    }

    /** @see RegistryConfig#getCommitLogDatastoreRetention() */
    @Provides
    @Config("commitLogDatastoreRetention")
    public static Duration provideCommitLogDatastoreRetention() {
      return RegistryConfig.getCommitLogDatastoreRetention();
    }

    /**
     * The GCS bucket for exporting domain lists.
     *
     * @see google.registry.export.ExportDomainListsAction
     */
    @Provides
    @Config("domainListsGcsBucket")
    public static String provideDomainListsGcsBucket(@Config("projectId") String projectId) {
      return projectId + "-domain-lists";
    }

    /**
     * Maximum number of commit logs to delete per transaction.
     *
     * <p>If we assume that the average key size is 256 bytes and that each manifest has six
     * mutations, we can do about 5,000 deletes in a single transaction before hitting the 10mB
     * limit. Therefore 500 should be a safe number, since it's an order of a magnitude less space
     * than we need.
     *
     * <p>Transactions also have a four minute time limit. Since we have to perform N subqueries to
     * fetch mutation keys, 500 would be a safe number if those queries were performed in serial,
     * since each query would have about 500ms to complete, which is an order a magnitude more time
     * than we need. However this does not apply, since the subqueries are performed asynchronously.
     *
     * @see google.registry.backup.DeleteOldCommitLogsAction
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
     * @see google.registry.backup.ExportCommitLogDiffAction
     */
    @Provides
    @Config("commitLogDiffExportBatchSize")
    public static int provideCommitLogDiffExportBatchSize() {
      return 100;
    }

    /**
     * Returns the Google Cloud Storage bucket for staging BRDA escrow deposits.
     *
     * @see google.registry.rde.PendingDepositChecker
     */
    @Provides
    @Config("brdaBucket")
    public static String provideBrdaBucket(@Config("projectId") String projectId) {
      return projectId + "-icann-brda";
    }

    /** @see google.registry.rde.BrdaCopyAction */
    @Provides
    @Config("brdaDayOfWeek")
    public static int provideBrdaDayOfWeek() {
      return DateTimeConstants.TUESDAY;
    }

    /**
     * Amount of time between BRDA deposits.
     *
     * @see google.registry.rde.PendingDepositChecker
     */
    @Provides
    @Config("brdaInterval")
    public static Duration provideBrdaInterval() {
      return Duration.standardDays(7);
    }

    /**
     * The maximum number of domain and host updates to batch together to send to
     * PublishDnsUpdatesAction, to avoid exceeding AppEngine's limits.
     *
     * @see google.registry.dns.ReadDnsQueueAction
     */
    @Provides
    @Config("dnsTldUpdateBatchSize")
    public static int provideDnsTldUpdateBatchSize() {
      return 100;
    }

    /**
     * The maximum interval (seconds) to lease tasks from the dns-pull queue.
     *
     * @see google.registry.dns.ReadDnsQueueAction
     * @see google.registry.dns.PublishDnsUpdatesAction
     */
    @Provides
    @Config("dnsWriteLockTimeout")
    public static Duration provideDnsWriteLockTimeout() {
      /*
       * Optimally, we would set this to a little less than the length of the DNS refresh cycle,
       * since otherwise, a new PublishDnsUpdatesAction could get kicked off before the current one
       * has finished, which will try and fail to acquire the lock. However, it is more important
       * that it be greater than the DNS write timeout, so that if that timeout occurs, it will be
       * cleaned up gracefully, rather than having the lock time out. So we have to live with the
       * possible lock failures.
       */
      return Duration.standardSeconds(75);
    }

    /**
     * Returns the default time to live for DNS records.
     *
     * @see google.registry.dns.writer.clouddns.CloudDnsWriter
     */
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
     * @see google.registry.model.index.EppResourceIndex
     */
    @Provides
    @Config("eppResourceIndexBucketCount")
    public static int provideEppResourceIndexBucketCount(RegistryConfigSettings config) {
      return config.datastore.eppResourceIndexBucketsNum;
    }

    /**
     * Returns size of Google Cloud Storage client connection buffer in bytes.
     *
     * @see google.registry.gcs.GcsUtils
     */
    @Provides
    @Config("gcsBufferSize")
    public static int provideGcsBufferSize() {
      return 1024 * 1024;
    }

    /**
     * Returns the email address of the admin account on the G Suite app used to perform
     * administrative actions.
     *
     * @see google.registry.groups.DirectoryGroupsConnection
     */
    @Provides
    @Config("gSuiteAdminAccountEmailAddress")
    public static String provideGSuiteAdminAccountEmailAddress(RegistryConfigSettings config) {
      return config.gSuite.adminAccountEmailAddress;
    }

    /**
     * Returns the email address(es) that notifications of registrar and/or registrar contact
     * updates should be sent to, or the empty list if updates should not be sent.
     *
     * @see google.registry.ui.server.registrar.RegistrarSettingsAction
     */
    @Provides
    @Config("registrarChangesNotificationEmailAddresses")
    public static ImmutableList<String> provideRegistrarChangesNotificationEmailAddresses(
        RegistryConfigSettings config) {
      return ImmutableList.copyOf(config.registryPolicy.registrarChangesNotificationEmailAddresses);
    }

    /**
     * Returns the publicly accessible domain name for the running G Suite instance.
     *
     * @see google.registry.export.SyncGroupMembersAction
     * @see google.registry.tools.server.CreateGroupsAction
     */
    @Provides
    @Config("gSuiteDomainName")
    public static String provideGSuiteDomainName(RegistryConfigSettings config) {
      return config.gSuite.domainName;
    }

    /**
     * Returns the mode that TMCH certificate authority should run in.
     *
     * @see google.registry.tmch.TmchCertificateAuthority
     */
    @Provides
    @Config("tmchCaMode")
    public static TmchCaMode provideTmchCaMode(RegistryConfigSettings config) {
      return TmchCaMode.valueOf(config.registryPolicy.tmchCaMode);
    }

    /** The mode that the {@code TmchCertificateAuthority} operates in. */
    public enum TmchCaMode {

      /** Production mode, suitable for live environments hosting TLDs. */
      PRODUCTION,

      /** Pilot mode, for everything else (e.g. sandbox). */
      PILOT;
    }

    /**
     * ICANN TMCH Certificate Revocation List URL.
     *
     * <p>This file needs to be downloaded at least once a day and verified to make sure it was
     * signed by {@code icann-tmch.crt} or {@code icann-tmch-pilot.crt} depending on TMCH CA mode.
     *
     * @see google.registry.tmch.TmchCrlAction
     * @see <a href="http://tools.ietf.org/html/draft-lozano-tmch-func-spec-08#section-5.2.3.2">TMCH
     *     RFC</a>
     */
    @Provides
    @Config("tmchCrlUrl")
    public static URL provideTmchCrlUrl(RegistryConfigSettings config) {
      return makeUrl(config.registryPolicy.tmchCrlUrl);
    }

    /**
     * URL prefix for communicating with MarksDB ry interface.
     *
     * <p>This URL is used for DNL, SMDRL, and LORDN.
     *
     * @see google.registry.tmch.Marksdb
     * @see google.registry.tmch.NordnUploadAction
     */
    @Provides
    @Config("tmchMarksdbUrl")
    public static String provideTmchMarksdbUrl(RegistryConfigSettings config) {
      return config.registryPolicy.tmchMarksDbUrl;
    }

    /**
     * The email address that outgoing emails from the app are sent from.
     *
     * @see google.registry.ui.server.registrar.SendEmailUtils
     */
    @Provides
    @Config("gSuiteOutgoingEmailAddress")
    public static String provideGSuiteOutgoingEmailAddress(RegistryConfigSettings config) {
      return config.gSuite.outgoingEmailAddress;
    }

    /**
     * The display name that is used on outgoing emails sent by Nomulus.
     *
     * @see google.registry.ui.server.registrar.SendEmailUtils
     */
    @Provides
    @Config("gSuiteOutoingEmailDisplayName")
    public static String provideGSuiteOutgoingEmailDisplayName(RegistryConfigSettings config) {
      return config.gSuite.outgoingEmailDisplayName;
    }

    /**
     * Returns the Google Cloud Storage bucket for staging escrow deposits pending upload.
     *
     * @see google.registry.rde.RdeStagingAction
     */
    @Provides
    @Config("rdeBucket")
    public static String provideRdeBucket(@Config("projectId") String projectId) {
      return projectId + "-rde";
    }

    /**
     * Returns the Google Cloud Storage bucket for importing escrow files.
     *
     * @see google.registry.rde.imports.RdeContactImportAction
     * @see google.registry.rde.imports.RdeHostImportAction
     */
    @Provides
    @Config("rdeImportBucket")
    public static String provideRdeImportBucket(@Config("projectId") String projectId) {
      return projectId + "-rde-import";
    }

    /**
     * Size of Ghostryde buffer in bytes for each layer in the pipeline.
     *
     * @see google.registry.rde.Ghostryde
     */
    @Provides
    @Config("rdeGhostrydeBufferSize")
    public static Integer provideRdeGhostrydeBufferSize() {
      return 64 * 1024;
    }

    /**
     * Amount of time between RDE deposits.
     *
     * @see google.registry.rde.PendingDepositChecker
     * @see google.registry.rde.RdeReportAction
     * @see google.registry.rde.RdeUploadAction
     */
    @Provides
    @Config("rdeInterval")
    public static Duration provideRdeInterval() {
      return Duration.standardDays(1);
    }

    /**
     * Maximum amount of time for sending a small XML file to ICANN via HTTP, before killing.
     *
     * @see google.registry.rde.RdeReportAction
     */
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
     * @see google.registry.rde.RdeReportAction
     */
    @Provides
    @Config("rdeReportUrlPrefix")
    public static String provideRdeReportUrlPrefix(RegistryConfigSettings config) {
      return config.rde.reportUrlPrefix;
    }

    /**
     * Size of RYDE generator buffer in bytes for each of the five layers.
     *
     * @see google.registry.rde.RydePgpCompressionOutputStream
     * @see google.registry.rde.RydePgpFileOutputStream
     * @see google.registry.rde.RydePgpSigningOutputStream
     * @see google.registry.rde.RydeTarOutputStream
     */
    @Provides
    @Config("rdeRydeBufferSize")
    public static Integer provideRdeRydeBufferSize() {
      return 64 * 1024;
    }

    /**
     * Maximum amount of time generating an escrow deposit for a TLD could take, before killing.
     *
     * @see google.registry.rde.RdeStagingReducer
     */
    @Provides
    @Config("rdeStagingLockTimeout")
    public static Duration provideRdeStagingLockTimeout() {
      return Duration.standardHours(5);
    }

    /**
     * Maximum amount of time it should ever take to upload an escrow deposit, before killing.
     *
     * @see google.registry.rde.RdeUploadAction
     */
    @Provides
    @Config("rdeUploadLockTimeout")
    public static Duration provideRdeUploadLockTimeout() {
      return Duration.standardMinutes(30);
    }

    /**
     * Minimum amount of time to wait between consecutive SFTP uploads on a single TLD.
     *
     * <p>This value was communicated to us by the escrow provider.
     *
     * @see google.registry.rde.RdeStagingReducer
     */
    @Provides
    @Config("rdeUploadSftpCooldown")
    public static Duration provideRdeUploadSftpCooldown() {
      return Duration.standardHours(2);
    }

    /**
     * Returns the identity (an email address) used for the SSH keys used in RDE SFTP uploads.
     *
     * @see google.registry.keyring.api.Keyring#getRdeSshClientPublicKey()
     * @see google.registry.keyring.api.Keyring#getRdeSshClientPrivateKey()
     */
    @Provides
    @Config("rdeSshIdentity")
    public static String provideSshIdentity(RegistryConfigSettings config) {
      return config.rde.sshIdentityEmailAddress;
    }

    /**
     * Returns SFTP URL containing a username, hostname, port (optional), and directory (optional)
     * to which cloud storage files are uploaded. The password should not be included, as it's
     * better to use public key authentication.
     *
     * @see google.registry.rde.RdeUploadAction
     */
    @Provides
    @Config("rdeUploadUrl")
    public static URI provideRdeUploadUrl(RegistryConfigSettings config) {
      return URI.create(config.rde.uploadUrl);
    }

    /**
     * Whether or not the registrar console is enabled.
     *
     * @see google.registry.ui.server.registrar.ConsoleUiAction
     */
    @Provides
    @Config("registrarConsoleEnabled")
    public static boolean provideRegistrarConsoleEnabled() {
      return true;
    }

    /**
     * Maximum amount of time for syncing a spreadsheet, before killing.
     *
     * @see google.registry.export.sheet.SyncRegistrarsSheetAction
     */
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
     * @see google.registry.export.sheet.SyncRegistrarsSheetAction
     */
    @Provides
    @Config("sheetRegistrarId")
    public static Optional<String> provideSheetRegistrarId(RegistryConfigSettings config) {
      return Optional.fromNullable(config.misc.sheetExportId);
    }

    /**
     * Returns SSH client connection and read timeout.
     *
     * @see google.registry.rde.RdeUploadAction
     */
    @Provides
    @Config("sshTimeout")
    public static Duration provideSshTimeout() {
      return Duration.standardSeconds(30);
    }

    /**
     * Duration after watermark where we shouldn't deposit, because transactions might be pending.
     *
     * @see google.registry.rde.RdeStagingAction
     */
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
     * <p>Note that this uses {@code @Named} instead of {@code @Config} so that it can be used from
     * the low-level util package, which cannot have a dependency on the config package.
     *
     * @see google.registry.util.TaskEnqueuer
     */
    @Provides
    @Named("transientFailureRetries")
    public static int provideTransientFailureRetries() {
      return 12;  // Four seconds.
    }

    /**
     * Amount of time public HTTP proxies are permitted to cache our WHOIS responses.
     *
     * @see google.registry.whois.WhoisHttpServer
     */
    @Provides
    @Config("whoisHttpExpires")
    public static Duration provideWhoisHttpExpires() {
      return Duration.standardDays(1);
    }

    /**
     * Maximum number of results to return for an RDAP search query
     *
     * @see google.registry.rdap.RdapActionBase
     */
    @Provides
    @Config("rdapResultSetMaxSize")
    public static int provideRdapResultSetMaxSize() {
      return 100;
    }

    /**
     * Base for RDAP link paths.
     *
     * @see google.registry.rdap.RdapActionBase
     */
    @Provides
    @Config("rdapLinkBase")
    public static String provideRdapLinkBase(RegistryConfigSettings config) {
      return config.rdap.baseUrl;
    }

    /**
     * WHOIS server displayed in RDAP query responses. As per Gustavo Lozano of ICANN, this should
     * be omitted, but the ICANN operational profile doesn't actually say that, so it's good to have
     * the ability to reinstate this field if necessary.
     *
     * @see google.registry.rdap.RdapActionBase
     */
    @Nullable
    @Provides
    @Config("rdapWhoisServer")
    public static String provideRdapWhoisServer() {
      return null;
    }

    /**
     * Returns Braintree Merchant Account IDs for each supported currency.
     *
     * @see google.registry.ui.server.registrar.RegistrarPaymentAction
     * @see google.registry.ui.server.registrar.RegistrarPaymentSetupAction
     */
    @Provides
    @Config("braintreeMerchantAccountIds")
    public static ImmutableMap<CurrencyUnit, String> provideBraintreeMerchantAccountId(
        RegistryConfigSettings config) {
      Map<String, String> merchantAccountIds = config.braintree.merchantAccountIdsMap;
      ImmutableMap.Builder<CurrencyUnit, String> builder = new ImmutableMap.Builder<>();
      for (Entry<String, String> entry : merchantAccountIds.entrySet()) {
        builder.put(CurrencyUnit.of(entry.getKey()), entry.getValue());
      }
      return builder.build();
    }

    /**
     * Returns Braintree Merchant ID of Registry, used for accessing Braintree API.
     *
     * <p>This is a base32 value copied from the Braintree website.
     *
     * @see google.registry.braintree.BraintreeModule
     */
    @Provides
    @Config("braintreeMerchantId")
    public static String provideBraintreeMerchantId(RegistryConfigSettings config) {
      return config.braintree.merchantId;
    }

    /**
     * Returns Braintree Public Key of Registry, used for accessing Braintree API.
     *
     * <p>This is a base32 value copied from the Braintree website.
     *
     * @see google.registry.braintree.BraintreeModule
     * @see google.registry.keyring.api.Keyring#getBraintreePrivateKey()
     */
    @Provides
    @Config("braintreePublicKey")
    public static String provideBraintreePublicKey(RegistryConfigSettings config) {
      return config.braintree.publicKey;
    }

    /**
     * Disclaimer displayed at the end of WHOIS query results.
     *
     * @see google.registry.whois.WhoisResponse
     */
    @Provides
    @Config("whoisDisclaimer")
    public static String provideWhoisDisclaimer(RegistryConfigSettings config) {
      return config.registryPolicy.whoisDisclaimer;
    }

    /**
     * Maximum QPS for the Google Cloud Monitoring V3 (aka Stackdriver) API. The QPS limit can be
     * adjusted by contacting Cloud Support.
     *
     * @see google.registry.monitoring.metrics.stackdriver.StackdriverWriter
     */
    @Provides
    @Config("stackdriverMaxQps")
    public static int provideStackdriverMaxQps(RegistryConfigSettings config) {
      return config.monitoring.stackdriverMaxQps;
    }

    /**
     * Maximum number of points that can be sent to Stackdriver in a single {@code
     * TimeSeries.Create} API call.
     *
     * @see google.registry.monitoring.metrics.stackdriver.StackdriverWriter
     */
    @Provides
    @Config("stackdriverMaxPointsPerRequest")
    public static int provideStackdriverMaxPointsPerRequest(RegistryConfigSettings config) {
      return config.monitoring.stackdriverMaxPointsPerRequest;
    }

    /**
     * The reporting interval for {@link google.registry.monitoring.metrics.Metric} instances to be
     * sent to a {@link google.registry.monitoring.metrics.MetricWriter}.
     *
     * @see google.registry.monitoring.metrics.MetricReporter
     */
    @Provides
    @Config("metricsWriteInterval")
    public static Duration provideMetricsWriteInterval(RegistryConfigSettings config) {
      return Duration.standardSeconds(config.monitoring.writeIntervalSeconds);
    }

    /**
     * The global automatic transfer length for contacts.  After this amount of time has
     * elapsed, the transfer is automatically approved.
     *
     * @see google.registry.flows.contact.ContactTransferRequestFlow
     */
    @Provides
    @Config("contactAutomaticTransferLength")
    public static Duration provideContactAutomaticTransferLength(RegistryConfigSettings config) {
      return Duration.standardDays(config.registryPolicy.contactAutomaticTransferDays);
    }

    /**
     * Returns the maximum number of entities that can be checked at one time in an EPP check flow.
     */
    @Provides
    @Config("maxChecks")
    public static int provideMaxChecks() {
      return 50;
    }

    /**
     * Returns the delay before executing async delete flow mapreduces.
     *
     * <p>This delay should be sufficiently longer than a transaction, to solve the following
     * problem:
     *
     * <ul>
     *   <li>a domain mutation flow starts a transaction
     *   <li>the domain flow non-transactionally reads a resource and sees that it's not in
     *       PENDING_DELETE
     *   <li>the domain flow creates a new reference to this resource
     *   <li>a contact/host delete flow runs and marks the resource PENDING_DELETE and commits
     *   <li>the domain flow commits
     * </ul>
     *
     * <p>Although we try not to add references to a PENDING_DELETE resource, strictly speaking that
     * is ok as long as the mapreduce eventually sees the new reference (and therefore
     * asynchronously fails the delete). Without this delay, the mapreduce might have started before
     * the domain flow committed, and could potentially miss the reference.
     *
     * @see google.registry.flows.async.AsyncFlowEnqueuer
     */
    @Provides
    @Config("asyncDeleteFlowMapreduceDelay")
    public static Duration provideAsyncDeleteFlowMapreduceDelay() {
      return Duration.standardSeconds(90);
    }

    /**
     * The server ID used in the 'svID' element of an EPP 'greeting'.
     *
     * @see <a href="https://tools.ietf.org/html/rfc5730">RFC 7530</a>
     */
    @Provides
    @Config("greetingServerId")
    public static String provideGreetingServerId(RegistryConfigSettings config) {
      return config.registryPolicy.greetingServerId;
    }

    @Provides
    @Config("customLogicFactoryClass")
    public static String provideCustomLogicFactoryClass(RegistryConfigSettings config) {
      return config.registryPolicy.customLogicFactoryClass;
    }

    @Provides
    @Config("whoisCommandFactoryClass")
    public static String provideWhoisCommandFactoryClass(RegistryConfigSettings config) {
      return config.registryPolicy.whoisCommandFactoryClass;
    }

    /**
     * Returns the header text at the top of the reserved terms exported list.
     *
     * @see google.registry.export.ExportUtils#exportReservedTerms
     */
    @Provides
    @Config("reservedTermsExportDisclaimer")
    public static String provideReservedTermsExportDisclaimer(RegistryConfigSettings config) {
      return config.registryPolicy.reservedTermsExportDisclaimer;
    }

    /**
     * Returns the clientId of the registrar used by the {@code CheckApiServlet}.
     */
    @Provides
    @Config("checkApiServletRegistrarClientId")
    public static String provideCheckApiServletRegistrarClientId(RegistryConfigSettings config) {
      return config.registryPolicy.checkApiServletClientId;
    }

    @Singleton
    @Provides
    static RegistryConfigSettings provideRegistryConfigSettings() {
      return CONFIG_SETTINGS.get();
    }

    /**
     * Returns the help path for the RDAP terms of service.
     *
     * <p>Make sure that this path is equal to the key of the entry in the RDAP help map containing
     * the terms of service. The ICANN operational profile requires that the TOS be included in all
     * responses, and this string is used to find the TOS in the help map.
     */
    @Provides
    @Config("rdapTosPath")
    public static String provideRdapTosPath() {
      return "/tos";
    }

    /**
     * Returns the help text to be used by RDAP.
     *
     * <p>Make sure that the map entry for the terms of service use the same key as specified in
     * rdapTosPath above.
     */
    @Singleton
    @Provides
    @Config("rdapHelpMap")
    public static ImmutableMap<String, RdapNoticeDescriptor> provideRdapHelpMap() {
      return new ImmutableMap.Builder<String, RdapNoticeDescriptor>()
          .put("/", RdapNoticeDescriptor.builder()
              .setTitle("RDAP Help")
              .setDescription(ImmutableList.of(
                  "RDAP Help Topics (use /help/topic for information)",
                  "syntax",
                  "tos (Terms of Service)"))
              .setLinkValueSuffix("help/")
              .build())
          .put("/index", RdapNoticeDescriptor.builder()
              .setTitle("RDAP Help")
              .setDescription(ImmutableList.of(
                  "RDAP Help Topics (use /help/topic for information)",
                  "syntax",
                  "tos (Terms of Service)"))
              .setLinkValueSuffix("help/index")
              .build())
          .put("/syntax", RdapNoticeDescriptor.builder()
              .setTitle("RDAP Command Syntax")
              .setDescription(ImmutableList.of(
                  "domain/XXXX",
                  "nameserver/XXXX",
                  "entity/XXXX",
                  "domains?name=XXXX",
                  "domains?nsLdhName=XXXX",
                  "domains?nsIp=XXXX",
                  "nameservers?name=XXXX",
                  "nameservers?ip=XXXX",
                  "entities?fn=XXXX",
                  "entities?handle=XXXX",
                  "help/XXXX"))
              .setLinkValueSuffix("help/syntax")
              .build())
          .put("/tos", RdapNoticeDescriptor.builder()
              .setTitle("RDAP Terms of Service")
              .setDescription(ImmutableList.of(
                  "By querying our Domain Database, you are agreeing to comply with these terms so"
                      + " please read them carefully.",
                  "Any information provided is 'as is' without any guarantee of accuracy.",
                  "Please do not misuse the Domain Database. It is intended solely for"
                      + " query-based access.",
                  "Don't use the Domain Database to allow, enable, or otherwise support the"
                      + " transmission of mass unsolicited, commercial advertising or"
                      + " solicitations.",
                  "Don't access our Domain Database through the use of high volume, automated"
                      + " electronic processes that send queries or data to the systems of any"
                      + " ICANN-accredited registrar.",
                  "You may only use the information contained in the Domain Database for lawful"
                      + " purposes.",
                  "Do not compile, repackage, disseminate, or otherwise use the information"
                      + " contained in the Domain Database in its entirety, or in any substantial"
                      + " portion, without our prior written permission.",
                  "We may retain certain details about queries to our Domain Database for the"
                      + " purposes of detecting and preventing misuse.",
                  "We reserve the right to restrict or deny your access to the database if we"
                      + " suspect that you have failed to comply with these terms.",
                  "We reserve the right to modify this agreement at any time."))
              .setLinkValueSuffix("help/tos")
              .build())
          .build();
    }
  }

  /**
   * Returns the App Engine project ID, which is based off the environment name.
   */
  public static String getProjectId() {
    return CONFIG_SETTINGS.get().appEngine.projectId;
  }

  /**
   * Returns the Google Cloud Storage bucket for storing backup snapshots.
   *
   * @see google.registry.export.ExportSnapshotAction
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
    return CONFIG_SETTINGS.get().datastore.commitLogBucketsNum;
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
   * Returns the address of the Nomulus app HTTP server.
   *
   * <p>This is used by the {@code nomulus} tool to connect to the App Engine remote API.
   */
  public static HostAndPort getServer() {
    // TODO(b/33386530): Make this configurable in a way that is accessible from the nomulus
    // command-line tool.
    switch (RegistryEnvironment.get()) {
      case PRODUCTION:
        return HostAndPort.fromParts("tools-dot-domain-registry.appspot.com", 443);
      case LOCAL:
        return HostAndPort.fromParts("localhost", 8080);
      case UNITTEST:
        throw new UnsupportedOperationException("Unit tests can't spin up a full server");
      default:
        return HostAndPort.fromParts(
            String.format(
                "tools-dot-domain-registry-%s.appspot.com",
                Ascii.toLowerCase(RegistryEnvironment.get().name())),
            443);
    }
  }

  /** Returns the amount of time a singleton should be cached, before expiring. */
  public static Duration getSingletonCacheRefreshDuration() {
    return Duration.standardSeconds(CONFIG_SETTINGS.get().caching.singletonCacheRefreshSeconds);
  }

  /**
   * Returns the amount of time a domain label list should be cached in memory before expiring.
   *
   * @see google.registry.model.registry.label.ReservedList
   * @see google.registry.model.registry.label.PremiumList
   */
  public static Duration getDomainLabelListCacheDuration() {
    return Duration.standardSeconds(CONFIG_SETTINGS.get().caching.domainLabelCachingSeconds);
  }

  /** Returns the amount of time a singleton should be cached in persist mode, before expiring. */
  public static Duration getSingletonCachePersistDuration() {
    return Duration.standardSeconds(CONFIG_SETTINGS.get().caching.singletonCachePersistSeconds);
  }

  /** Returns the email address that outgoing emails from the app are sent from. */
  public static String getGSuiteOutgoingEmailAddress() {
    return CONFIG_SETTINGS.get().gSuite.outgoingEmailAddress;
  }

  /** Returns the display name that outgoing emails from the app are sent from. */
  public static String getGSuiteOutgoingEmailDisplayName() {
    return CONFIG_SETTINGS.get().gSuite.outgoingEmailDisplayName;
  }

  /**
   * Returns default WHOIS server to use when {@code Registrar#getWhoisServer()} is {@code null}.
   *
   * @see "google.registry.whois.DomainWhoisResponse"
   * @see "google.registry.whois.RegistrarWhoisResponse"
   */
  public static String getDefaultRegistrarWhoisServer() {
    return CONFIG_SETTINGS.get().registryPolicy.defaultRegistrarWhoisServer;
  }

  /**
   * Returns the default referral URL that is used unless registrars have specified otherwise.
   */
  public static String getDefaultRegistrarReferralUrl() {
    return CONFIG_SETTINGS.get().registryPolicy.defaultRegistrarReferralUrl;
  }

  /**
   * Returns the number of {@code EppResourceIndex} buckets to be used.
   */
  public static int getEppResourceIndexBucketCount() {
    return CONFIG_SETTINGS.get().datastore.eppResourceIndexBucketsNum;
  }

  /**
   * Returns the base retry duration that gets doubled after each failure within {@code Ofy}.
   */
  public static Duration getBaseOfyRetryDuration() {
    return Duration.millis(CONFIG_SETTINGS.get().datastore.baseOfyRetryMillis);
  }

  /** Returns the roid suffix to be used for the roids of all contacts and hosts. */
  public static String getContactAndHostRoidSuffix() {
    return CONFIG_SETTINGS.get().registryPolicy.contactAndHostRoidSuffix;
  }

  /** Returns the global automatic transfer length for contacts. */
  public static Duration getContactAutomaticTransferLength() {
    return Duration.standardDays(CONFIG_SETTINGS.get().registryPolicy.contactAutomaticTransferDays);
  }

  /**
   * Memoizes loading of the {@link RegistryConfigSettings} POJO.
   *
   * <p>Memoizing without cache expiration is used because the app must be re-deployed in order to
   * change the contents of the YAML config files.
   */
  private static final Supplier<RegistryConfigSettings> CONFIG_SETTINGS =
      memoize(new Supplier<RegistryConfigSettings>() {
        @Override
        public RegistryConfigSettings get() {
          return getConfigSettings();
        }});

  private RegistryConfig() {}
}

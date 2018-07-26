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

package google.registry.tools;

import dagger.Component;
import google.registry.config.RegistryConfig.ConfigModule;
import google.registry.dns.writer.VoidDnsWriterModule;
import google.registry.dns.writer.clouddns.CloudDnsWriterModule;
import google.registry.dns.writer.dnsupdate.DnsUpdateWriterModule;
import google.registry.keyring.api.KeyModule;
import google.registry.keyring.kms.KmsModule;
import google.registry.rde.RdeModule;
import google.registry.request.Modules.AppIdentityCredentialModule;
import google.registry.request.Modules.DatastoreServiceModule;
import google.registry.request.Modules.GoogleCredentialModule;
import google.registry.request.Modules.Jackson2Module;
import google.registry.request.Modules.URLFetchServiceModule;
import google.registry.request.Modules.UrlFetchTransportModule;
import google.registry.request.Modules.UseAppIdentityCredentialForGoogleApisModule;
import google.registry.request.Modules.UserServiceModule;
import google.registry.util.AppEngineServiceUtilsImpl.AppEngineServiceUtilsModule;
import google.registry.util.SystemClock.SystemClockModule;
import google.registry.util.SystemSleeper.SystemSleeperModule;
import google.registry.whois.WhoisModule;
import javax.inject.Singleton;

/**
 * Dagger component for Registry Tool.
 *
 * <p>Any command class with {@code @Inject} fields <i>must</i> be listed as a method here.
 * Otherwise {@link RegistryCli} will not be able to populate those fields after its instantiation.
 */
@Singleton
@Component(
    modules = {
      AppEngineConnectionFlags.FlagsModule.class,
      AppEngineServiceUtilsModule.class,
      // TODO(b/36866706): Find a way to replace this with a command-line friendly version
      AppIdentityCredentialModule.class,
      AuthModule.class,
      ConfigModule.class,
      DatastoreServiceModule.class,
      google.registry.keyring.api.DummyKeyringModule.class,
      CloudDnsWriterModule.class,
      DefaultRequestFactoryModule.class,
      DefaultRequestFactoryModule.RequestFactoryModule.class,
      DnsUpdateWriterModule.class,
      GoogleCredentialModule.class,
      Jackson2Module.class,
      KeyModule.class,
      KmsModule.class,
      RdeModule.class,
      RegistryToolModule.class,
      SystemClockModule.class,
      SystemSleeperModule.class,
      URLFetchServiceModule.class,
      UrlFetchTransportModule.class,
      // TODO(b/36866706): Find a way to replace this with a command-line friendly version
      UseAppIdentityCredentialForGoogleApisModule.class,
      UserServiceModule.class,
      VoidDnsWriterModule.class,
      WhoisModule.class,
    })
interface RegistryToolComponent {
  void inject(CheckSnapshotCommand command);
  void inject(CountDomainsCommand command);
  void inject(CreateAnchorTenantCommand command);
  void inject(CreateCdnsTld command);
  void inject(CreateContactCommand command);
  void inject(CreateDomainCommand command);
  void inject(CreateLrpTokensCommand command);
  void inject(CreateTldCommand command);
  void inject(DeployInvoicingPipelineCommand command);
  void inject(DeploySpec11PipelineCommand command);
  void inject(EncryptEscrowDepositCommand command);
  void inject(GenerateAllocationTokensCommand command);
  void inject(GenerateApplicationsReportCommand command);
  void inject(GenerateDnsReportCommand command);
  void inject(GenerateEscrowDepositCommand command);
  void inject(GetKeyringSecretCommand command);
  void inject(GhostrydeCommand command);
  void inject(ListCursorsCommand command);
  void inject(LockDomainCommand command);
  void inject(LoginCommand command);
  void inject(LogoutCommand command);
  void inject(PendingEscrowCommand command);
  void inject(RenewDomainCommand command);
  void inject(SendEscrowReportToIcannCommand command);
  void inject(SetupOteCommand command);
  void inject(UnlockDomainCommand command);
  void inject(UpdateCursorsCommand command);
  void inject(UpdateDomainCommand command);
  void inject(UpdateKmsKeyringCommand command);
  void inject(UpdateTldCommand command);
  void inject(ValidateEscrowDepositCommand command);
  void inject(WhoisQueryCommand command);

  AppEngineConnection appEngineConnection();
}

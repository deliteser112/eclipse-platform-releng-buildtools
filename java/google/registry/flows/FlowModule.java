// Copyright 2016 The Domain Registry Authors. All Rights Reserved.
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

package google.registry.flows;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.ImmutableMap;

import dagger.Module;
import dagger.Provides;

import google.registry.flows.contact.ContactCheckFlow;
import google.registry.flows.contact.ContactCreateFlow;
import google.registry.flows.contact.ContactDeleteFlow;
import google.registry.flows.contact.ContactInfoFlow;
import google.registry.flows.contact.ContactTransferApproveFlow;
import google.registry.flows.contact.ContactTransferCancelFlow;
import google.registry.flows.contact.ContactTransferQueryFlow;
import google.registry.flows.contact.ContactTransferRejectFlow;
import google.registry.flows.contact.ContactTransferRequestFlow;
import google.registry.flows.contact.ContactUpdateFlow;
import google.registry.flows.domain.ClaimsCheckFlow;
import google.registry.flows.domain.DomainAllocateFlow;
import google.registry.flows.domain.DomainApplicationCreateFlow;
import google.registry.flows.domain.DomainApplicationDeleteFlow;
import google.registry.flows.domain.DomainApplicationInfoFlow;
import google.registry.flows.domain.DomainApplicationUpdateFlow;
import google.registry.flows.domain.DomainCheckFlow;
import google.registry.flows.domain.DomainCreateFlow;
import google.registry.flows.domain.DomainDeleteFlow;
import google.registry.flows.domain.DomainInfoFlow;
import google.registry.flows.domain.DomainRenewFlow;
import google.registry.flows.domain.DomainRestoreRequestFlow;
import google.registry.flows.domain.DomainTransferApproveFlow;
import google.registry.flows.domain.DomainTransferCancelFlow;
import google.registry.flows.domain.DomainTransferQueryFlow;
import google.registry.flows.domain.DomainTransferRejectFlow;
import google.registry.flows.domain.DomainTransferRequestFlow;
import google.registry.flows.domain.DomainUpdateFlow;
import google.registry.flows.host.HostCheckFlow;
import google.registry.flows.host.HostCreateFlow;
import google.registry.flows.host.HostDeleteFlow;
import google.registry.flows.host.HostInfoFlow;
import google.registry.flows.host.HostUpdateFlow;
import google.registry.flows.picker.FlowPicker;
import google.registry.flows.poll.PollAckFlow;
import google.registry.flows.poll.PollRequestFlow;
import google.registry.flows.session.HelloFlow;
import google.registry.flows.session.LoginFlow;
import google.registry.flows.session.LogoutFlow;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppinput.EppInput;

import java.lang.annotation.Documented;
import java.util.Map;

import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.inject.Qualifier;

/** Module to choose and instantiate an EPP flow. */
@Module
public class FlowModule {

  private EppInput eppInput;
  private byte[] inputXmlBytes;
  private SessionMetadata sessionMetadata;
  private TransportCredentials credentials;
  private boolean isDryRun;
  private EppRequestSource eppRequestSource;
  private boolean isSuperuser;

  private FlowModule() {}

  /** Builder for {@link FlowModule}. */
  static class Builder {
    FlowModule module = new FlowModule();

    Builder setEppInput(EppInput eppInput) {
      module.eppInput = eppInput;
      return this;
    }

    Builder setInputXmlBytes(byte[] inputXmlBytes) {
      module.inputXmlBytes = inputXmlBytes;
      return this;
    }

    Builder setSessionMetadata(SessionMetadata sessionMetadata) {
      module.sessionMetadata = sessionMetadata;
      return this;
    }

    Builder setIsDryRun(boolean isDryRun) {
      module.isDryRun = isDryRun;
      return this;
    }

    Builder setIsSuperuser(boolean isSuperuser) {
      module.isSuperuser = isSuperuser;
      return this;
    }

    Builder setEppRequestSource(EppRequestSource eppRequestSource) {
      module.eppRequestSource = eppRequestSource;
      return this;
    }

    Builder setCredentials(TransportCredentials credentials) {
      module.credentials = credentials;
      return this;
    }

    FlowModule build() {
      try {
        checkState(module != null, "Already built");
        return module;
      } finally {
        module = null;
      }
    }
  }

  @Provides
  @FlowScope
  @InputXml
  byte[] provideInputXml() {
    return inputXmlBytes;
  }

  @Provides
  @FlowScope
  EppInput provideEppInput() {
    return eppInput;
  }

  @Provides
  @FlowScope
  SessionMetadata provideSessionMetadata() {
    return sessionMetadata;
  }

  @Provides
  @FlowScope
  @DryRun
  boolean provideIsDryRun() {
    return isDryRun;
  }

  @Provides
  @FlowScope
  @Transactional
  boolean provideIsTransactional(Class<? extends Flow> flowClass) {
    return TransactionalFlow.class.isAssignableFrom(flowClass);
  }

  @Provides
  @FlowScope
  @Superuser
  boolean provideIsSuperuser() {
    return isSuperuser;
  }

  @Provides
  @FlowScope
  EppRequestSource provideEppRequestSource() {
    return eppRequestSource;
  }

  @Provides
  @FlowScope
  TransportCredentials provideTransportCredentials() {
    return credentials;
  }

  @Provides
  @FlowScope
  @Nullable
  @ClientId
  static String provideClientId(SessionMetadata sessionMetadata) {
    return sessionMetadata.getClientId();
  }

  @Provides
  @FlowScope
  static Trid provideTrid(EppInput eppInput) {
    return Trid.create(eppInput.getCommandWrapper().getClTrid());
  }

  /** Provides a mapping between flow classes and injected providers. */
  @Provides
  @FlowScope
  static Map<Class<? extends Flow>, Provider<? extends Flow>> provideFlowClassMap(
      Provider<ContactCheckFlow> contactCheckFlowProvider,
      Provider<ContactCreateFlow> contactCreateFlowProvider,
      Provider<ContactDeleteFlow> contactDeleteFlowProvider,
      Provider<ContactInfoFlow> contactInfoFlowProvider,
      Provider<ContactTransferApproveFlow> contactTransferApproveFlowProvider,
      Provider<ContactTransferCancelFlow> contactTransferCancelFlowProvider,
      Provider<ContactTransferQueryFlow> contactTransferQueryFlowProvider,
      Provider<ContactTransferRejectFlow> contactTransferRejectFlowProvider,
      Provider<ContactTransferRequestFlow> contactTransferRequestFlowProvider,
      Provider<ContactUpdateFlow> contactUpdateFlowProvider,
      Provider<ClaimsCheckFlow> claimsCheckFlowProvider,
      Provider<DomainAllocateFlow> domainAllocateFlowProvider,
      Provider<DomainApplicationCreateFlow> domainApplicationCreateFlowProvider,
      Provider<DomainApplicationDeleteFlow> domainApplicationDeleteFlowProvider,
      Provider<DomainApplicationInfoFlow> domainApplicationInfoFlowProvider,
      Provider<DomainApplicationUpdateFlow> domainApplicationUpdateFlowProvider,
      Provider<DomainCheckFlow> domainCheckFlowProvider,
      Provider<DomainCreateFlow> domainCreateFlowProvider,
      Provider<DomainDeleteFlow> domainDeleteFlowProvider,
      Provider<DomainInfoFlow> domainInfoFlowProvider,
      Provider<DomainRenewFlow> domainRenewFlowProvider,
      Provider<DomainRestoreRequestFlow> domainRestoreRequestFlowProvider,
      Provider<DomainTransferApproveFlow> domainTransferApproveFlowProvider,
      Provider<DomainTransferCancelFlow> domainTransferCancelFlowProvider,
      Provider<DomainTransferQueryFlow> domainTransferQueryFlowProvider,
      Provider<DomainTransferRejectFlow> domainTransferRejectFlowProvider,
      Provider<DomainTransferRequestFlow> domainTransferRequestFlowProvider,
      Provider<DomainUpdateFlow> domainUpdateFlowProvider,
      Provider<HostCheckFlow> hostCheckFlowProvider,
      Provider<HostCreateFlow> hostCreateFlowProvider,
      Provider<HostDeleteFlow> hostDeleteFlowProvider,
      Provider<HostInfoFlow> hostInfoFlowProvider,
      Provider<HostUpdateFlow> hostUpdateFlowProvider,
      Provider<PollAckFlow> pollAckFlowProvider,
      Provider<PollRequestFlow> pollRequestFlowProvider,
      Provider<HelloFlow> helloFlowProvider,
      Provider<LoginFlow> loginFlowProvider,
      Provider<LogoutFlow> logoutFlowProvider) {
    return new ImmutableMap.Builder<Class<? extends Flow>, Provider<? extends Flow>>()
        .put(ContactCheckFlow.class, contactCheckFlowProvider)
        .put(ContactCreateFlow.class, contactCreateFlowProvider)
        .put(ContactDeleteFlow.class, contactDeleteFlowProvider)
        .put(ContactInfoFlow.class, contactInfoFlowProvider)
        .put(ContactTransferApproveFlow.class, contactTransferApproveFlowProvider)
        .put(ContactTransferCancelFlow.class, contactTransferCancelFlowProvider)
        .put(ContactTransferQueryFlow.class, contactTransferQueryFlowProvider)
        .put(ContactTransferRejectFlow.class, contactTransferRejectFlowProvider)
        .put(ContactTransferRequestFlow.class, contactTransferRequestFlowProvider)
        .put(ContactUpdateFlow.class, contactUpdateFlowProvider)
        .put(ClaimsCheckFlow.class, claimsCheckFlowProvider)
        .put(DomainAllocateFlow.class, domainAllocateFlowProvider)
        .put(DomainApplicationCreateFlow.class, domainApplicationCreateFlowProvider)
        .put(DomainApplicationDeleteFlow.class, domainApplicationDeleteFlowProvider)
        .put(DomainApplicationInfoFlow.class, domainApplicationInfoFlowProvider)
        .put(DomainApplicationUpdateFlow.class, domainApplicationUpdateFlowProvider)
        .put(DomainCheckFlow.class, domainCheckFlowProvider)
        .put(DomainCreateFlow.class, domainCreateFlowProvider)
        .put(DomainDeleteFlow.class, domainDeleteFlowProvider)
        .put(DomainInfoFlow.class, domainInfoFlowProvider)
        .put(DomainRenewFlow.class, domainRenewFlowProvider)
        .put(DomainRestoreRequestFlow.class, domainRestoreRequestFlowProvider)
        .put(DomainTransferApproveFlow.class, domainTransferApproveFlowProvider)
        .put(DomainTransferCancelFlow.class, domainTransferCancelFlowProvider)
        .put(DomainTransferQueryFlow.class, domainTransferQueryFlowProvider)
        .put(DomainTransferRejectFlow.class, domainTransferRejectFlowProvider)
        .put(DomainTransferRequestFlow.class, domainTransferRequestFlowProvider)
        .put(DomainUpdateFlow.class, domainUpdateFlowProvider)
        .put(HostCheckFlow.class, hostCheckFlowProvider)
        .put(HostCreateFlow.class, hostCreateFlowProvider)
        .put(HostDeleteFlow.class, hostDeleteFlowProvider)
        .put(HostInfoFlow.class, hostInfoFlowProvider)
        .put(HostUpdateFlow.class, hostUpdateFlowProvider)
        .put(PollAckFlow.class, pollAckFlowProvider)
        .put(PollRequestFlow.class, pollRequestFlowProvider)
        .put(HelloFlow.class, helloFlowProvider)
        .put(LoginFlow.class, loginFlowProvider)
        .put(LogoutFlow.class, logoutFlowProvider)
        .build();
  }

  @Provides
  @FlowScope
  static Class<? extends Flow> provideFlowClass(EppInput eppInput) {
    try {
      return FlowPicker.getFlowClass(eppInput);
    } catch (EppException e) {
      throw new EppExceptionInProviderException(e);
    }
  }

  @Provides
  @FlowScope
  static Flow provideFlow(
      Map<Class<? extends Flow>, Provider<? extends Flow>> flowProviders,
      Class<? extends Flow> flowClass) {
    return flowProviders.get(flowClass).get();
  }

  /** Wrapper class to carry an {@link EppException} to the calling code. */
  static class EppExceptionInProviderException extends RuntimeException {
    EppExceptionInProviderException(EppException exception) {
      super(exception);
    }
  }

  /** Dagger qualifier for inputXml. */
  @Qualifier
  @Documented
  public @interface InputXml {}

  /** Dagger qualifier for registrar client id. */
  @Qualifier
  @Documented
  public @interface ClientId {}

  /** Dagger qualifier for whether a flow is in dry run mode. */
  @Qualifier
  @Documented
  public @interface DryRun {}

  /** Dagger qualifier for whether a flow is in superuser mode. */
  @Qualifier
  @Documented
  public @interface Superuser {}

  /** Dagger qualifier for whether a flow is transactional. */
  @Qualifier
  @Documented
  public @interface Transactional {}
}

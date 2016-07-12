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

import dagger.Module;
import dagger.Provides;
import google.registry.flows.picker.FlowPicker;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppinput.EppInput;
import java.lang.annotation.Documented;
import javax.annotation.Nullable;
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

  @Provides
  @FlowScope
  static Class<? extends Flow> provideFlowClass(EppInput eppInput) {
    try {
      return FlowPicker.getFlowClass(eppInput);
    } catch (EppException e) {
      throw new EppExceptionInProviderException(e);
    }
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

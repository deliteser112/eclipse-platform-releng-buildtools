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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.model.eppcommon.Trid;
import google.registry.model.eppinput.EppInput;
import google.registry.model.eppinput.EppInput.CommandExtension;
import google.registry.model.eppoutput.EppOutput;
import google.registry.model.eppoutput.EppResponse;
import google.registry.model.eppoutput.EppResponse.ResponseData;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;
import google.registry.model.eppoutput.Result;
import google.registry.model.poll.MessageQueueInfo;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.joda.time.DateTime;

/**
 * An abstract EPP flow.
 *
 * <p>This class also contains static methods for loading an appropriate flow based on model
 * classes.
 */
public abstract class Flow {

  protected EppInput eppInput;
  protected SessionMetadata sessionMetadata;
  protected TransportCredentials credentials;
  protected Trid trid;
  protected DateTime now;

  /** Whether this flow is being run in a superuser mode that can skip some checks. */
  protected boolean isSuperuser;

  /** The collection of allowed extensions for the flow. */
  private Set<Class<? extends CommandExtension>> validExtensions = new HashSet<>();

  /** Flows can override this for custom initialization. */
  @SuppressWarnings("unused")
  protected void initFlow() throws EppException {}

  /** Execute the business logic for this flow. */
  protected abstract EppOutput run() throws EppException;

  /**
   * Subclasses that create a resource should override this to return the repoId of the new
   * resource.
   */
  protected String getCreatedRepoId() {
    return null;
  }

  protected String getClientId() {
    return sessionMetadata.getClientId();
  }

  protected EppOutput createOutput(Result.Code code) {
    return createOutput(code, null);
  }

  protected EppOutput createOutput(Result.Code code, ResponseData responseData) {
    return createOutput(code, responseData, null);
  }

  protected EppOutput createOutput(
      Result.Code code,
      @Nullable ResponseData responseData,
      @Nullable ImmutableList<? extends ResponseExtension> extensions) {
    return createOutput(
        code, responseData == null ? null : ImmutableList.of(responseData), extensions, null);
  }

  protected EppOutput createOutput(
      Result.Code code,
      @Nullable ImmutableList<ResponseData> responseData,
      @Nullable ImmutableList<? extends ResponseExtension> responseExtensions,
      @Nullable MessageQueueInfo messageQueueInfo) {
    return EppOutput.create(new EppResponse.Builder()
        .setTrid(trid)
        .setResult(Result.create(code))
        .setMessageQueueInfo(messageQueueInfo)
        .setResData(responseData)
        .setExtensions(responseExtensions)
        .setExecutionTime(now)
        .build());
  }

  /**
   * Using an init function instead of a constructor avoids duplicating constructors across the
   * entire hierarchy of flow classes
   */
  public final Flow init(
      EppInput eppInput,
      Trid trid,
      SessionMetadata sessionMetadata,
      TransportCredentials credentials,
      boolean isSuperuser,
      DateTime now) throws EppException {
    this.eppInput = eppInput;
    this.trid = trid;
    this.sessionMetadata = sessionMetadata;
    this.credentials = credentials;
    this.now = now;
    this.isSuperuser = isSuperuser;
    initFlow();
    validExtensions = ImmutableSet.copyOf(validExtensions);
    return this;
  }

  /**
   * Add an extension class as a valid extension for a flow.
   * Must be called in the init series of methods, as the validExtensions
   * becomes immutable once init is complete.
   */
  @SafeVarargs
  protected final void registerExtensions(Class<? extends CommandExtension>... extensions) {
    Collections.addAll(validExtensions, extensions);
  }

  protected final <E extends CommandExtension>
      void registerExtensions(List<Class<? extends E>> extensions) {
    validExtensions.addAll(extensions);
  }

  /** Get the legal command extension types for this flow. */
  protected final Set<Class<? extends CommandExtension>> getValidRequestExtensions() {
    return ImmutableSet.copyOf(validExtensions);
  }
}

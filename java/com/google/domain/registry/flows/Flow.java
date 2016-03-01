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

package com.google.domain.registry.flows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.domain.registry.model.eppcommon.Trid;
import com.google.domain.registry.model.eppinput.EppInput;
import com.google.domain.registry.model.eppinput.EppInput.CommandExtension;
import com.google.domain.registry.model.eppoutput.EppOutput;
import com.google.domain.registry.model.eppoutput.Response;
import com.google.domain.registry.model.eppoutput.Response.ResponseData;
import com.google.domain.registry.model.eppoutput.Response.ResponseExtension;
import com.google.domain.registry.model.eppoutput.Result;

import org.joda.time.DateTime;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * An abstract EPP flow.
 * <p>
 * This class also contains static methods for loading an appropriate flow based on model classes.
 */
public abstract class Flow {

  protected EppInput eppInput;
  protected SessionMetadata sessionMetadata;
  protected Trid trid;
  protected DateTime now;
  protected byte[] inputXmlBytes;

  /** Whether this flow is being run in a superuser mode that can skip some checks. */
  protected boolean superuser;

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
      ResponseData responseData,
      ImmutableList<? extends ResponseExtension> extensions) {
    return EppOutput.create(new Response.Builder()
        .setTrid(trid)
        .setResult(Result.create(code))
        .setExecutionTime(now)
        .setCreatedRepoId(getCreatedRepoId())
        .setResData(responseData == null ? null : ImmutableList.of(responseData))
        .setExtensions(extensions)
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
      boolean superuser,
      DateTime now,
      byte[] inputXmlBytes) throws EppException {
    this.eppInput = eppInput;
    this.trid = trid;
    this.sessionMetadata = sessionMetadata;
    this.now = now;
    this.superuser = superuser;
    this.inputXmlBytes = inputXmlBytes;
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

  /** Get the legal command extension types for this flow. */
  protected final Set<Class<? extends CommandExtension>> getValidRequestExtensions() {
    return ImmutableSet.copyOf(validExtensions);
  }
}

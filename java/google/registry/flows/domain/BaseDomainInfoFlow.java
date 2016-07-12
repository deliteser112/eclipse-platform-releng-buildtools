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

package google.registry.flows.domain;

import static google.registry.util.CollectionUtils.forceEmptyToNull;

import com.google.common.collect.ImmutableList;
import google.registry.flows.EppException;
import google.registry.flows.ResourceInfoFlow;
import google.registry.model.domain.DomainBase;
import google.registry.model.domain.DomainBase.Builder;
import google.registry.model.domain.DomainCommand;
import google.registry.model.domain.secdns.SecDnsInfoExtension;
import google.registry.model.eppoutput.EppResponse.ResponseExtension;

/**
 * An EPP flow that reads a domain resource or application.
 *
 * @param <R> the resource type being manipulated
 * @param <B> a builder for the resource
 */
public abstract class BaseDomainInfoFlow<R extends DomainBase, B extends Builder<R, B>>
    extends ResourceInfoFlow<R, DomainCommand.Info> {
  @Override
  protected final ImmutableList<ResponseExtension> getResponseExtensions() throws EppException {
    ImmutableList.Builder<ResponseExtension> builder = new ImmutableList.Builder<>();
    // According to RFC 5910 section 2, we should only return this if the client specified the
    // "urn:ietf:params:xml:ns:secDNS-1.1" when logging in. However, this is a "SHOULD" not a "MUST"
    // and we are going to ignore it; clients who don't care about secDNS can just ignore it.
    if (!existingResource.getDsData().isEmpty()) {
      builder.add(SecDnsInfoExtension.create(existingResource.getDsData()));
    }
    return forceEmptyToNull(builder.addAll(getDomainResponseExtensions()).build());
  }

  /** Subclasses should override this to add their extensions. */
  protected abstract ImmutableList<? extends ResponseExtension> getDomainResponseExtensions()
      throws EppException;
}

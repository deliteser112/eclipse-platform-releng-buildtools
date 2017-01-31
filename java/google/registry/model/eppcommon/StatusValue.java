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

package google.registry.model.eppcommon;

import static com.google.common.base.CaseFormat.LOWER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.collect.ImmutableSet;
import google.registry.model.EppResource;
import google.registry.model.contact.ContactResource;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.DomainResource;
import google.registry.model.host.HostResource;
import google.registry.model.translators.EnumToAttributeAdapter.EppEnum;
import google.registry.model.translators.StatusValueAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Represents an EPP status value for hosts, contacts, and domains, as defined in RFC 5731, 5732,
 * and 5733. The values here are the union of all 3 sets of status values.
 *
 * <p>The RFCs define extra optional metadata (language and message) that we don't use and therefore
 * don't model.
 *
 * <p>Note that {@code StatusValue.LINKED} should never be stored. Rather, it should be calculated
 * on the fly whenever needed using an eventually consistent query (i.e. in info flows).
 *
 * @see <a href="https://www.icann.org/resources/pages/epp-status-codes-2014-06-16-en">EPP Status
 *     Codes</a>
 */
@XmlJavaTypeAdapter(StatusValueAdapter.class)
public enum StatusValue implements EppEnum {

  CLIENT_DELETE_PROHIBITED,
  CLIENT_HOLD,
  CLIENT_RENEW_PROHIBITED,
  CLIENT_TRANSFER_PROHIBITED,
  CLIENT_UPDATE_PROHIBITED,

  /**
   * A status for a domain with no nameservers that has all the other requirements for {@link #OK}.
   *
   * <p>Only domains can have this status, and it supersedes OK.
   */
  INACTIVE(ContactResource.class, HostResource.class, DomainApplication.class),

  /**
   * A status for a resource has an incoming reference from an active domain.
   *
   * <p>LINKED is a "virtual" status value that should never be persisted to Datastore on any
   * resource. It must be computed on the fly when we need it, as the set of domains using a
   * resource can change at any time.
   */
  LINKED(ContactResource.class, DomainApplication.class, DomainResource.class, HostResource.class),

  /**
   * A status for a resource that has no other statuses.
   *
   * <p>Domains that have no other statuses but also have no nameservers get {@link #INACTIVE}
   * instead. The spec also allows a resource to have {@link #LINKED} along with OK, but we
   * implement LINKED as a virtual status that gets appended to outputs (such as info commands) on
   * the fly, so we can ignore LINKED when dealing with persisted resources.
   */
  OK,

  /**
   * A status for a resource undergoing asynchronous creation.
   *
   * <p>We only use this for unallocated applications.
   */
  PENDING_CREATE(ContactResource.class, DomainResource.class, HostResource.class),

  /**
   * A status for a resource undergoing asynchronous deletion or for a recently deleted domain.
   *
   * <p>Contacts and hosts are deleted asynchronously because we need to check their incoming
   * references with strong consistency, requiring a mapreduce.
   *
   * <p>Domains that are deleted after the add grace period ends go into a redemption grace period,
   * and when that ends they go into pending delete for 5 days.
   *
   * <p>Applications are deleted synchronously and can never have this status.
   */
  PENDING_DELETE(DomainApplication.class),

  /**
   * A status for a resource with an unresolved transfer request.
   *
   * <p>Applications can't be transferred. Hosts transfer indirectly via superordinate domain.
   */
  // TODO(b/34844887): Remove PENDING_TRANSFER from all host resources and forbid it here.
  PENDING_TRANSFER(DomainApplication.class),

  /**
   * A status for a resource undergoing an asynchronous update.
   *
   * <p>This status is here for completeness, but it is not used by our system.
   */
  PENDING_UPDATE(
      ContactResource.class, DomainApplication.class, DomainResource.class, HostResource.class),

  SERVER_DELETE_PROHIBITED,
  SERVER_HOLD,
  SERVER_RENEW_PROHIBITED,
  SERVER_TRANSFER_PROHIBITED,
  SERVER_UPDATE_PROHIBITED;

  private final String xmlName = UPPER_UNDERSCORE.to(LOWER_CAMEL, name());
  private final ImmutableSet<Class<? extends EppResource>> forbiddenOn;

  @SafeVarargs
  private StatusValue(Class<? extends EppResource>... forbiddenOn) {
    this.forbiddenOn = ImmutableSet.copyOf(forbiddenOn);
  }

  @Override
  public String getXmlName() {
    return xmlName;
  }

  public boolean isClientSettable() {
    // This is the actual definition of client-settable statuses; see RFC5730 section 2.3.
    return xmlName.startsWith("client");
  }

  public boolean isChargedStatus() {
    return xmlName.startsWith("server") && xmlName.endsWith("Prohibited");
  }

  public boolean isForbiddenOn(Class<? extends EppResource> resource) {
    return forbiddenOn.contains(resource);
  }

  public static StatusValue fromXmlName(String xmlName) {
    return StatusValue.valueOf(LOWER_CAMEL.to(UPPER_UNDERSCORE, nullToEmpty(xmlName)));
  }
}

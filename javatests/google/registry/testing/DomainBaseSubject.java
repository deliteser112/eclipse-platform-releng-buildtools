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

package google.registry.testing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.SimpleSubjectBuilder;
import google.registry.model.domain.DomainBase;
import google.registry.testing.TruthChainer.And;
import java.util.Objects;
import org.joda.time.DateTime;

/** Truth subject for asserting things about {@link DomainBase} instances. */
public final class DomainBaseSubject
    extends AbstractDomainBaseSubject<DomainBase, DomainBaseSubject> {

  public And<DomainBaseSubject> hasRegistrationExpirationTime(DateTime expiration) {
    if (!Objects.equals(actual().getRegistrationExpirationTime(), expiration)) {
      failWithBadResults(
          "has registrationExpirationTime",
          expiration,
          actual().getRegistrationExpirationTime());
    }
    return andChainer();
  }

  public And<DomainBaseSubject> hasLastTransferTime(DateTime lastTransferTime) {
    return hasValue(
        lastTransferTime,
        actual().getLastTransferTime(),
        "has lastTransferTime");
  }

  public And<DomainBaseSubject> hasLastTransferTimeNotEqualTo(DateTime lastTransferTime) {
    return doesNotHaveValue(
        lastTransferTime,
        actual().getLastTransferTime(),
        "lastTransferTime");
  }

  public And<DomainBaseSubject> hasDeletePollMessage() {
    if (actual().getDeletePollMessage() == null) {
      failWithActual(simpleFact("expected to have a delete poll message"));
    }
    return andChainer();
  }

  public And<DomainBaseSubject> hasNoDeletePollMessage() {
    if (actual().getDeletePollMessage() != null) {
      failWithActual(simpleFact("expected to have no delete poll message"));
    }
    return andChainer();
  }

  public And<DomainBaseSubject> hasSmdId(String smdId) {
    return hasValue(smdId, actual().getSmdId(), "has smdId");
  }

  public DomainBaseSubject(FailureMetadata failureMetadata, DomainBase subject) {
    super(failureMetadata, checkNotNull(subject));
  }

  public static SimpleSubjectBuilder<DomainBaseSubject, DomainBase> assertAboutDomains() {
    return assertAbout(DomainBaseSubject::new);
  }
}

// Copyright 2016 The Nomulus Authors. All Rights Reserved.
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
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.common.truth.AbstractVerb.DelegatedVerb;
import com.google.common.truth.FailureStrategy;
import google.registry.model.domain.DomainApplication;
import google.registry.model.domain.launch.ApplicationStatus;
import google.registry.model.smd.EncodedSignedMark;
import google.registry.testing.TruthChainer.And;
import java.util.Objects;

/** Truth subject for asserting things about {@link DomainApplication} instances. */
public final class DomainApplicationSubject
    extends AbstractDomainBaseSubject<DomainApplication, DomainApplicationSubject> {

  public And<DomainApplicationSubject> hasApplicationStatus(
      ApplicationStatus applicationStatus) {
    if (!Objects.equals(actual().getApplicationStatus(), applicationStatus)) {
      failWithBadResults(
          "has application status", applicationStatus, actual().getApplicationStatus());
    }
    return andChainer();
  }

  public And<DomainApplicationSubject> doesNotHaveApplicationStatus(
      ApplicationStatus applicationStatus) {
    return doesNotHaveValue(
        applicationStatus,
        actual().getApplicationStatus(),
        "application status");
  }

  public And<DomainApplicationSubject> hasExactlyEncodedSignedMarks(
      EncodedSignedMark... encodedSignedMarks) {
    if (!Objects.equals(
        ImmutableSet.copyOf(actual().getEncodedSignedMarks()),
        ImmutableSet.of(encodedSignedMarks))) {
      assertThat(actual().getEncodedSignedMarks())
          .named("the encoded signed marks of " + actualAsString())
          .containsExactly((Object[]) encodedSignedMarks);
    }
    return andChainer();
  }

  public And<DomainApplicationSubject> hasNumEncodedSignedMarks(int num) {
    if (actual().getEncodedSignedMarks().size() != num) {
      failWithBadResults(
          "has this many encoded signed marks: ", num, actual().getEncodedSignedMarks().size());
    }
    return andChainer();
  }

  /** A factory for instances of this subject. */
  private static class SubjectFactory
      extends ReflectiveSubjectFactory<DomainApplication, DomainApplicationSubject>{}

  public DomainApplicationSubject(FailureStrategy strategy, DomainApplication subject) {
    super(strategy, checkNotNull(subject));
  }

  public static DelegatedVerb<DomainApplicationSubject, DomainApplication>
      assertAboutApplications() {
    return assertAbout(new SubjectFactory());
  }
}

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

import static com.google.common.truth.Truth.assertAbout;
import static google.registry.util.PreconditionsUtils.checkArgumentNotNull;

import com.google.common.base.Optional;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import google.registry.model.eppoutput.Result.Code;
import google.registry.monitoring.whitebox.EppMetric;
import google.registry.testing.TruthChainer.And;
import java.util.Objects;
import javax.annotation.Nullable;

/** Utility methods for asserting things about {@link EppMetric} instances. */
public class EppMetricSubject extends Subject<EppMetricSubject, EppMetric> {

  public EppMetricSubject(FailureStrategy strategy, EppMetric subject) {
    super(strategy, subject);
  }

  public static EppMetricSubject assertThat(EppMetric subject) {
    return assertAbout(SUBJECT_FACTORY).that(subject);
  }

  public And<EppMetricSubject> hasClientId(String clientId) {
    return hasValue(clientId, actual().getClientId(), "has clientId");
  }

  public And<EppMetricSubject> hasCommandName(String commandName) {
    return hasValue(commandName, actual().getCommandName(), "has commandName");
  }

  public And<EppMetricSubject> hasEppTarget(String eppTarget) {
    return hasValue(eppTarget, actual().getEppTarget(), "has eppTarget");
  }

  public And<EppMetricSubject> hasStatus(Code status) {
    return hasValue(status, actual().getStatus(), "has status");
  }

  private <E> And<EppMetricSubject> hasValue(E expected, Optional<E> actual, String verb) {
    checkArgumentNotNull(expected, "Expected value cannot be null");
    if (actual == null) {
      fail("is non-null", expected);
    } else if (!actual.isPresent()) {
      fail("has value", expected);
    } else if (!Objects.equals(expected, actual.get())) {
      failWithBadResults(verb, expected, verb, actual);
    }
    return new And<>(this);
  }

  /** {@link SubjectFactory} for assertions about {@link EppMetric} objects. */
  private static final SubjectFactory<EppMetricSubject, EppMetric> SUBJECT_FACTORY =
      new SubjectFactory<EppMetricSubject, EppMetric>() {
        @Override
        public EppMetricSubject getSubject(
            FailureStrategy failureStrategy, @Nullable EppMetric target) {
          return new EppMetricSubject(failureStrategy, target);
        }
      };
}
